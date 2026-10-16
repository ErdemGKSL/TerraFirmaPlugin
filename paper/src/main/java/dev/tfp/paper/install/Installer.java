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
import dev.tfp.core.install.ThirdPartyEdits;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The {@code onLoad} install step ({@code dependency-bootstrap} §2, §5, §6; ARCHITECTURE.md §12 step 2):
 * our content into {@code plugins/CraftEngine/resources/tfp/} and {@code plugins/BetterHud/packs/tfp/},
 * then the targeted config edits, then the restart/degraded decision ({@link InstallStatus#decide}).
 * BetterModel gets no files: its models are registered from our jar.
 *
 * <p>Blocking file I/O: {@code onLoad} or the io executor only. Not thread-safe; run one at a time.
 */
public final class Installer {
    public static final String CRAFTENGINE = "CraftEngine";
    public static final String BETTERHUD = "BetterHud";
    /** Our pack's folder name in both plugins. */
    public static final String PACK = "tfp";

    /**
     * @param pluginsDir     the server's {@code plugins/} folder
     * @param stateDir       our installer state folder ({@code plugins/TerraFirmaPlugin/install})
     * @param contentRoot    the jar's {@code /content} (or a directory with the same layout); null if unavailable
     * @param contentVersion recorded in the manifests (our plugin version)
     * @param installed      names of the client plugins present
     * @param ceStates       CraftEngine block states our content defines (sizes {@code serverside-blocks})
     */
    public record Inputs(TfpConfig.AutoInstall settings, Path pluginsDir, Path stateDir, Path contentRoot,
            String contentVersion, Set<String> installed, int ceStates) {
        public Inputs {
            installed = Set.copyOf(installed);
        }
    }

    /** Everything one run did. A null report means the target was skipped ({@code skipped} says why). */
    public record Result(InstallStatus status, ContentInstaller.Report craftEngine, ContentInstaller.Report betterHud,
            List<String> skipped, List<EditResult> edits) {
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
            ce = installTree(in, "craftengine/" + PACK, craftEngineTarget(in.pluginsDir()), "CraftEngine pack", skipped, failures, log);
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

        InstallStatus status = InstallStatus.decide(edits, ceInstalled, ce != null && ce.changed(), failures);
        Result result = new Result(status, ce, bh, skipped, edits);
        logResult(result, log);
        return result;
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

    private static ContentInstaller.Report installTree(Inputs in, String sourceRel, Path target, String label,
            List<String> skipped, List<String> failures, Logger log) {
        Optional<Path> source = Optional.ofNullable(in.contentRoot()).map(r -> r.resolve(sourceRel)).filter(Files::isDirectory);
        if (source.isEmpty()) {
            skipped.add(label + ": the plugin jar ships no /content/" + sourceRel);
            return null;
        }
        try {
            return ContentInstaller.install(source.get(), target, in.contentVersion(), in.settings().restoreDeleted());
        } catch (IOException | RuntimeException e) {
            log.log(Level.SEVERE, "Cannot install the " + label + " into " + target, e);
            failures.add(label + " install failed: " + e);
            return null;
        }
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
        for (EditResult e : r.edits()) {
            String what = e.edit().target().path() + " " + e.edit().key();
            switch (e.outcome()) {
                case APPLIED -> log.info("Config edit: " + what + ": " + e.before() + " -> " + e.after() + " (" + e.edit().why() + ")");
                case OWNER_KEPT -> log.warning("Config edit skipped: " + what + " = " + e.before() + " (" + e.detail()
                        + "); we recommend changing it because " + e.edit().why());
                case FAILED -> log.warning("Config edit failed: " + what + ": " + e.detail());
                case FILE_MISSING, UNCHANGED -> {
                }
            }
        }
        if (r.status().packRebuild()) {
            log.info("The CraftEngine resource pack must be rebuilt for players to see the changes.");
        }
        for (String line : r.status().banner()) {
            log.severe(line);
        }
    }
}
