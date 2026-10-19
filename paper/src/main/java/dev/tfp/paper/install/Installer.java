/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.install;

import dev.tfp.core.config.TfpConfig;
import dev.tfp.core.install.ConfigEdit;
import dev.tfp.core.install.InstallStatus;
import dev.tfp.core.install.InstallStatus.EditResult;
import dev.tfp.core.install.InstallManifest;
import dev.tfp.core.install.ThirdPartyEdits;
import dev.tfp.core.visuals.PackBuild;
import dev.tfp.core.visuals.VisualSelection;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The {@code onLoad} install step ({@code dependency-bootstrap} §2, §5, §6; ARCHITECTURE.md §12 step 2):
 * our content into {@code plugins/CraftEngine/resources/tfp/} and {@code plugins/BetterHud/packs/tfp/},
 * then the targeted config edits, then the restart/degraded decision ({@link InstallStatus#decide}).
 * BetterModel gets no files: its models are registered from our jar.
 *
 * <p>M1-INST-03: the selected visual allocation ({@code /content/visuals/<family>/<profile>/nether-end-*})
 * is installed with the CraftEngine pack as {@code configuration/visuals/}; the pack's fingerprint is compared
 * with the one the last successful rebuild delivered ({@link PackBuild}); a difference asks for one rebuild
 * ({@code status().packRebuild()}), which {@link PackRebuildTrigger} starts after CraftEngine has loaded.
 *
 * <p>Blocking file I/O: {@code onLoad} or the io executor only. Not thread-safe; run one at a time.
 */
public final class Installer {
    public static final String CRAFTENGINE = "CraftEngine";
    public static final String BETTERHUD = "BetterHud";
    /** Our pack's folder name in both plugins. */
    public static final String PACK = "tfp";
    /** Where the selected allocation's files go inside our CraftEngine pack. */
    public static final String VISUALS_DIR = "configuration/visuals/";
    /** The generated BetterHud glue folder, relative to {@code betterhud/tfp} (never copied into packs/tfp). */
    static final String GLUE_DIR = "glue/";
    /** The allocation files installed into the CraftEngine pack (the suppression list stays in the jar). */
    public static final List<String> VISUAL_FILES = List.of("visual_templates.yml", "visual_mappings.yml");

    /**
     * @param pluginsDir     the server's {@code plugins/} folder
     * @param stateDir       our installer state folder ({@code plugins/TerraFirmaPlugin/install})
     * @param contentRoot    the jar's {@code /content} (or a directory with the same layout); null if unavailable
     * @param contentVersion recorded in the manifests (our plugin version)
     * @param installed      names of the client plugins present
     * @param ceStates       CraftEngine block states our content defines (sizes {@code serverside-blocks})
     * @param visuals        the visual allocation to install (null: none, e.g. no content)
     */
    public record Inputs(TfpConfig.AutoInstall settings, Path pluginsDir, Path stateDir, Path contentRoot,
            String contentVersion, Set<String> installed, int ceStates, VisualSelection visuals) {
        public Inputs {
            installed = Set.copyOf(installed);
        }

        public Inputs(TfpConfig.AutoInstall settings, Path pluginsDir, Path stateDir, Path contentRoot,
                String contentVersion, Set<String> installed, int ceStates) {
            this(settings, pluginsDir, stateDir, contentRoot, contentVersion, installed, ceStates, null);
        }
    }

    /**
     * Everything one run did. A null report means the target was skipped ({@code skipped} says why).
     *
     * @param packPlan        whether our CE pack content/profile differs from the last delivered build
     * @param packFingerprint what our CE pack holds now; record it with {@link #recordPackBuilt} once a
     *                        rebuild succeeded (null if our CE pack was not installed)
     */
    public record Result(InstallStatus status, ContentInstaller.Report craftEngine, ContentInstaller.Report betterHud,
            List<String> skipped, List<EditResult> edits, PackBuild.Plan packPlan, PackBuild.Fingerprint packFingerprint) {
        public Result {
            skipped = List.copyOf(skipped);
            edits = List.copyOf(edits);
        }
    }

    private Installer() {
    }

    public static Path craftEngineTarget(Path pluginsDir) {
        return pluginsDir.resolve(CRAFTENGINE).resolve("resources").resolve(PACK);
    }

    public static Path betterHudTarget(Path pluginsDir) {
        return pluginsDir.resolve(BETTERHUD).resolve("packs").resolve(PACK);
    }

    public static Result run(Inputs in, Logger log) {
        TfpConfig.AutoInstall s = in.settings();
        List<String> skipped = new ArrayList<>();
        List<String> failures = new ArrayList<>();

        ContentInstaller.Report ce = null;
        boolean ceInstalled = in.installed().contains(CRAFTENGINE);
        if (!ceInstalled) {
            skipped.add("CraftEngine pack: CraftEngine is not installed");
        } else if (!s.craftengine()) {
            skipped.add("CraftEngine pack: auto-install.craftengine is false");
        } else if (!Files.isDirectory(in.pluginsDir().resolve(CRAFTENGINE).resolve("resources"))) {
            // CraftEngine extracts its default packs only while resources/ does not exist (M0-VIS-01): never create it.
            skipped.add("CraftEngine pack: plugins/CraftEngine/resources does not exist yet (start CraftEngine once)");
            failures.add("our CraftEngine pack is not installed: plugins/CraftEngine/resources does not exist yet");
        } else {
            ce = installCraftEngine(in, skipped, failures, log);
        }
        PackBuild.Plan plan = PackBuild.Plan.NOTHING;
        PackBuild.Fingerprint fingerprint = null;
        if (ce != null) {
            try {
                fingerprint = fingerprint(craftEngineTarget(in.pluginsDir()), in.visuals());
                plan = PackBuild.decide(readPackRecord(in.stateDir()), fingerprint);
            } catch (IOException | RuntimeException e) {
                log.log(Level.WARNING, "Cannot compare our CraftEngine pack with the last build; rebuilding it", e);
                plan = new PackBuild.Plan(true, false, "pack fingerprint unavailable (" + e + ")");
            }
        }

        ContentInstaller.Report bh = null;
        if (!in.installed().contains(BETTERHUD)) {
            skipped.add("BetterHud pack: BetterHud is not installed");
        } else if (!s.betterhud()) {
            skipped.add("BetterHud pack: auto-install.betterhud is false");
        } else if (Files.isDirectory(in.pluginsDir().resolve(BETTERHUD).resolve("packs").resolve("-" + PACK))) {
            skipped.add("BetterHud pack: disabled by the owner (packs/-" + PACK + " exists)");
        } else {
            bh = installTree(in, "betterhud/" + PACK, betterHudTarget(in.pluginsDir()), "BetterHud pack", skipped, failures, log);
        }

        List<EditResult> edits = List.of();
        try {
            ThirdPartyConfigEditor editor = new ThirdPartyConfigEditor(in.pluginsDir(), in.stateDir());
            List<ConfigEdit> all = ThirdPartyEdits.all(in.ceStates());
            edits = s.configEdits() ? editor.apply(all, in.installed()) : editor.check(all, in.installed());
        } catch (IOException | RuntimeException e) {
            log.log(Level.SEVERE, "Cannot edit the CraftEngine/BetterHud/BetterModel configs", e);
            failures.add("config edits failed: " + e);
        }

        // A changed CE file always changes the fingerprint, so the plan covers ce.changed() too.
        InstallStatus status = InstallStatus.decide(edits, ceInstalled, plan.rebuild(), failures);
        Result result = new Result(status, ce, bh, skipped, edits, plan, fingerprint);
        logResult(result, log);
        return result;
    }

    /** True if every edit of {@code target} in {@code r} is still {@link ConfigEdit.Outcome#FILE_MISSING} (pending). */
    public static boolean pending(Result r, ConfigEdit.Target target) {
        List<EditResult> of = r == null ? List.of() : r.edits().stream().filter(e -> e.edit().target() == target).toList();
        return !of.isEmpty() && of.stream().allMatch(e -> e.outcome() == ConfigEdit.Outcome.FILE_MISSING);
    }

    /**
     * Applies the edits of {@code target} once more, e.g. BetterHud's after its first start wrote the file
     * (ARCHITECTURE.md §12 step 4); records them in {@code edits.json} like {@link #run}. Nothing when
     * {@code auto-install.config-edits} is off. Blocking file I/O: io executor.
     */
    public static List<EditResult> applyPending(Inputs in, ConfigEdit.Target target, Logger log) {
        if (!in.settings().configEdits()) {
            return List.of();
        }
        List<ConfigEdit> of = ThirdPartyEdits.all(in.ceStates()).stream().filter(e -> e.target() == target).toList();
        try {
            List<EditResult> edits = new ThirdPartyConfigEditor(in.pluginsDir(), in.stateDir()).apply(of, in.installed());
            logEdits(edits, log);
            return edits;
        } catch (IOException | RuntimeException e) {
            log.log(Level.SEVERE, "Cannot edit " + target.path(), e);
            return List.of();
        }
    }

    /**
     * CraftEngine block states our content defines, from the datagen report ({@code counts.ce_states}, else
     * {@code counts.ce_blocks} as a lower bound); 0 if the report is missing.
     */
    public static int ceStates(Path contentRoot) {
        if (contentRoot == null) {
            return 0;
        }
        Path report = contentRoot.resolve("reports").resolve("datagen-summary.json");
        try {
            com.google.gson.JsonObject counts = com.google.gson.JsonParser.parseString(
                    Files.readString(report, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("counts");
            if (counts == null) {
                return 0;
            }
            for (String key : new String[] {"ce_states", "ce_blocks"}) {
                if (counts.has(key)) {
                    return counts.get(key).getAsInt();
                }
            }
            return 0;
        } catch (IOException | RuntimeException e) {
            return 0;
        }
    }

    /** Our CE pack plus the selected visual allocation under {@link #VISUALS_DIR}. */
    private static ContentInstaller.Report installCraftEngine(Inputs in, List<String> skipped, List<String> failures,
            Logger log) {
        String label = "CraftEngine pack";
        String sourceRel = "craftengine/" + PACK;
        Path target = craftEngineTarget(in.pluginsDir());
        Optional<Path> source = Optional.ofNullable(in.contentRoot()).map(r -> r.resolve(sourceRel)).filter(Files::isDirectory);
        if (source.isEmpty()) {
            skipped.add(label + ": the plugin jar ships no /content/" + sourceRel);
            return null;
        }
        try {
            Map<String, Path> files = new TreeMap<>(ContentInstaller.list(source.get()));
            VisualSelection v = in.visuals();
            if (v != null) {
                Path dir = in.contentRoot().resolve(v.dir());
                List<String> missing = VISUAL_FILES.stream().filter(f -> !Files.isRegularFile(dir.resolve(f))).toList();
                if (missing.isEmpty()) {
                    for (String f : VISUAL_FILES) {
                        files.put(VISUALS_DIR + f, dir.resolve(f));
                    }
                } else {
                    failures.add("visual allocation " + v.id() + " is not shipped in the plugin jar (missing " + missing
                            + "): our blocks have no visuals");
                }
            }
            return ContentInstaller.install(files, target, in.contentVersion(), in.settings().restoreDeleted());
        } catch (IOException | RuntimeException e) {
            log.log(Level.SEVERE, "Cannot install the " + label + " into " + target, e);
            failures.add(label + " install failed: " + e);
            return null;
        }
    }

    /** What our installed CE pack holds (from its manifest) with the selection installed. */
    static PackBuild.Fingerprint fingerprint(Path ceTarget, VisualSelection visuals) throws IOException {
        InstallManifest m = ContentInstaller.readManifest(ceTarget.resolve(InstallManifest.FILE_NAME));
        Map<String, String> files = new TreeMap<>();
        m.files().forEach((k, e) -> {
            if (!e.deleted()) {
                files.put(k, e.sha256());
            }
        });
        return PackBuild.Fingerprint.of(files, visuals);
    }

    /** The fingerprint of the last successful CraftEngine pack rebuild (empty: none or unreadable). */
    public static Optional<PackBuild.Fingerprint> readPackRecord(Path stateDir) {
        Path f = stateDir.resolve(PackBuild.RECORD_FILE);
        try {
            return Files.isRegularFile(f) ? PackBuild.Fingerprint.fromJson(Files.readString(f, StandardCharsets.UTF_8))
                    : Optional.empty();
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    /** Records that a CraftEngine rebuild delivered {@code fingerprint}. Blocking I/O: io executor. */
    public static void recordPackBuilt(Path stateDir, PackBuild.Fingerprint fingerprint) throws IOException {
        AtomicFiles.write(stateDir.resolve(PackBuild.RECORD_FILE), fingerprint.toJson().getBytes(StandardCharsets.UTF_8));
    }

    private static ContentInstaller.Report installTree(Inputs in, String sourceRel, Path target, String label,
            List<String> skipped, List<String> failures, Logger log) {
        Optional<Path> source = Optional.ofNullable(in.contentRoot()).map(r -> r.resolve(sourceRel)).filter(Files::isDirectory);
        if (source.isEmpty()) {
            skipped.add(label + ": the plugin jar ships no /content/" + sourceRel);
            return null;
        }
        try {
            return installBetterHudPack(source.get(), target, in.contentVersion(), in.settings().restoreDeleted());
        } catch (IOException | RuntimeException e) {
            log.log(Level.SEVERE, "Cannot install the " + label + " into " + target, e);
            failures.add(label + " install failed: " + e);
            return null;
        }
    }

    /**
     * Copies the generated BetterHud pack ({@code source} = {@code content/betterhud/tfp}) into {@code target}.
     * The {@code glue/} folder (placeholders.yml, hidden-vanilla.yml) is read by our plugin's glue, not by
     * BetterHud, so it is never copied into the packs folder. Any other file is copied as before.
     *
     * @param source the shipped {@code betterhud/tfp} folder
     * @param target {@code plugins/BetterHud/packs/tfp}
     */
    static ContentInstaller.Report installBetterHudPack(Path source, Path target, String contentVersion,
            boolean restoreDeleted) throws IOException {
        Map<String, Path> shipped = new TreeMap<>(ContentInstaller.list(source));
        // Single explicit check on the path relative to betterhud/tfp: the glue folder is plugin-only data.
        shipped.keySet().removeIf(rel -> rel.startsWith(GLUE_DIR));
        return ContentInstaller.install(shipped, target, contentVersion, restoreDeleted);
    }

    private static void logResult(Result r, Logger log) {
        for (ContentInstaller.Report rep : new ContentInstaller.Report[] {r.craftEngine(), r.betterHud()}) {
            if (rep == null) {
                continue;
            }
            log.info("Installed " + rep.summary());
            for (String side : rep.sideFiles()) {
                log.fine("  edited by you, our version saved as " + side);
            }
        }
        for (String s : r.skipped()) {
            log.info("Skipped " + s);
        }
        logEdits(r.edits(), log);
        if (r.status().packRebuild()) {
            log.info("The CraftEngine resource pack will be rebuilt once CraftEngine has loaded"
                    + (r.packPlan().reason().isEmpty() ? "" : " (" + r.packPlan().reason() + ")") + ".");
        }
        // A pending restart alone is the expected first start of a fresh server: WARNING. Setup the owner must
        // fix (SETUP INCOMPLETE): SEVERE.
        Level level = r.status().setupIncomplete() ? Level.SEVERE : Level.WARNING;
        for (String line : r.status().banner()) {
            log.log(level, line);
        }
    }

    private static void logEdits(List<EditResult> edits, Logger log) {
        for (EditResult e : edits) {
            String what = e.edit().target().path() + " " + e.edit().key();
            switch (e.outcome()) {
                case APPLIED -> log.info("Config edit: " + what + ": " + e.before() + " -> " + e.after() + " (" + e.edit().why() + ")");
                case OWNER_KEPT -> log.warning("Config edit skipped: " + what + " = " + e.before() + " (" + e.detail()
                        + "); we recommend changing it because " + e.edit().why());
                case FAILED -> log.warning("Config edit failed: " + what + ": " + e.detail());
                case FILE_MISSING -> {
                    ConfigEdit.Target t = e.edit().target();
                    // BetterHud/BetterModel enable after us and write their config.yml on their first start.
                    // BetterHud's edits are applied again once it has enabled (PendingHudEdit).
                    if (t == ConfigEdit.Target.BETTERHUD_CONFIG) {
                        log.info("Config edit pending: " + what + " (" + t.path()
                                + " does not exist yet; applied once BetterHud has enabled)");
                    } else if (t == ConfigEdit.Target.BETTERMODEL_CONFIG) {
                        log.info("Config edit pending: " + what + " (" + t.path()
                                + " does not exist yet; applied at the next start)");
                    }
                }
                case UNCHANGED -> {
                }
            }
        }
    }
}
