/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.install;

import dev.tfp.core.visuals.PackBuild;
import dev.tfp.core.visuals.ProfileCheck;
import dev.tfp.core.visuals.VisualIndex;
import dev.tfp.core.visuals.VisualSelection;
import dev.tfp.paper.integration.PackRebuilder;
import dev.tfp.paper.integration.VisualPools;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * M1-INST-03 after CraftEngine has loaded ({@code dependency-bootstrap} §6b; ARCHITECTURE.md §9.1). Run
 * {@link #onCraftEngineReload} from {@code BlockVisuals.onReload} (every CraftEngine reload):
 * <ol>
 *   <li>checks the real {@code solid} pool against the selected profile ({@link ProfileCheck}): FAIL is
 *       logged as an error naming the next higher profile, WARN below {@code visuals.solid-reserve-warning};</li>
 *   <li>if the installer asked for it ({@code status().packRebuild()}), requests <b>one</b> CraftEngine pack
 *       rebuild per server run; after it succeeded, records the pack fingerprint (io executor) so the next
 *       start with the same content and profile does not rebuild again.</li>
 * </ol>
 * The rebuild's own CraftEngine reload calls this again: the check runs again, the rebuild is not repeated.
 * Every check's figures are also written to {@code <stateDir>/}{@value #POOL_CHECK_FILE} (io executor), so a
 * restart can be compared with the previous run (self-test {@code inst.profile_restart}).
 */
public final class PackRebuildTrigger {
    /** The last pool check's figures, {@link java.util.Properties} text ({@link #poolCheckRecord}). */
    public static final String POOL_CHECK_FILE = "pool-check.properties";
    private final Installer.Result install;
    private final Path stateDir;
    private final PackRebuilder rebuilder;
    private final VisualPools pools;
    private final VisualIndex index;
    private final VisualSelection selection;
    private final int reserve;
    private final Executor io;
    private final Logger log;
    private final AtomicBoolean requested = new AtomicBoolean();
    /** Completes when this run's rebuild finished (successfully or not). */
    private final CompletableFuture<Void> rebuildDone = new CompletableFuture<>();
    private volatile ProfileCheck.Result lastCheck;

    /**
     * @param index     the shipped {@code visuals/index.json} (null: no capacity check)
     * @param selection the installed allocation (null: no capacity check)
     */
    public PackRebuildTrigger(Installer.Result install, Path stateDir, PackRebuilder rebuilder, VisualPools pools,
            VisualIndex index, VisualSelection selection, int reserve, Executor io, Logger log) {
        this.install = install;
        this.stateDir = stateDir;
        this.rebuilder = rebuilder;
        this.pools = pools;
        this.index = index;
        this.selection = selection;
        this.reserve = reserve;
        this.io = io;
        this.log = log;
    }

    /** The latest pool check (null before the first CraftEngine reload or without index/selection). */
    public ProfileCheck.Result lastCheck() {
        return lastCheck;
    }

    /** True once this run asked CraftEngine for its rebuild. */
    public boolean rebuildRequested() {
        return requested.get();
    }

    /**
     * Completes once this run's pack rebuild has finished, or at once when none is planned. The rebuild is a full
     * CraftEngine reload: while it runs, CraftEngine's block and item registries are empty (M1 self-test matrix).
     * The returned future is a copy; cancelling it does not affect the rebuild.
     */
    public CompletableFuture<Void> settled() {
        boolean planned = install != null && install.status().packRebuild() && rebuilder.available();
        return planned || requested.get() ? rebuildDone.copy() : CompletableFuture.completedFuture(null);
    }

    /** On the thread that delivered the CraftEngine reload. Never throws. */
    public void onCraftEngineReload() {
        try {
            check();
        } catch (RuntimeException e) {
            log.log(Level.WARNING, "Cannot check the CraftEngine visual pools", e);
        }
        if (install == null || !install.status().packRebuild() || !rebuilder.available()
                || !requested.compareAndSet(false, true)) {
            return;
        }
        PackBuild.Fingerprint built = install.packFingerprint();
        String reason = install.packPlan().reason();
        log.info("Rebuilding the CraftEngine resource pack" + (reason.isEmpty() ? "" : " (" + reason + ")") + ".");
        CompletableFuture<Boolean> rebuild;
        try {
            rebuild = rebuilder.rebuild();
        } catch (RuntimeException e) {
            rebuildDone.complete(null);
            log.log(Level.WARNING, "The CraftEngine pack rebuild failed; players keep the old pack. Run /ce reload all", e);
            return;
        }
        rebuild.whenComplete((ok, err) -> {
            rebuildDone.complete(null);
            if (err != null) {
                log.log(Level.WARNING, "The CraftEngine pack rebuild failed; players keep the old pack. Run /ce reload all", err);
            } else if (!ok) {
                log.warning("CraftEngine reported a failed reload; players keep the old pack. Fix the errors and run /ce reload all");
            } else {
                log.info("CraftEngine resource pack rebuilt.");
                if (built != null) {
                    io.execute(() -> {
                        try {
                            Installer.recordPackBuilt(stateDir, built);
                        } catch (IOException e) {
                            log.log(Level.WARNING, "Cannot record the pack build; the next start rebuilds again", e);
                        }
                    });
                }
            }
        });
    }

    private void check() {
        if (index == null || selection == null) {
            return;
        }
        Optional<ProfileCheck.PoolUsage> usage = pools.solid();
        if (usage.isEmpty()) {
            return;
        }
        ProfileCheck.Result r = ProfileCheck.check(selection, index.solidDemand(selection.family(), selection.netherEnd()),
                usage.get(), reserve);
        lastCheck = r;
        String record = poolCheckRecord(selection, usage.get(), reserve, r);
        io.execute(() -> {
            try {
                AtomicFiles.write(stateDir.resolve(POOL_CHECK_FILE), record.getBytes(StandardCharsets.UTF_8));
            } catch (IOException | RuntimeException e) {
                log.log(Level.FINE, "Cannot record the pool check", e);
            }
        });
        Level level = switch (r.verdict()) {
            case OK -> Level.INFO;
            case WARN -> Level.WARNING;
            case FAIL -> Level.SEVERE;
        };
        for (String line : r.lines()) {
            log.log(level, line);
        }
    }

    /**
     * The persisted figures of one pool check. Pure; keys are read by the self-test, keep them stable:
     * {@code selection, profile, verdict, capacity, used, ours, stale, needed, others, free, reserve, line}
     * ({@code line}: the capacity message, the one with "fits", "Only ... free" or "does not fit").
     */
    public static String poolCheckRecord(VisualSelection selection, ProfileCheck.PoolUsage usage, int reserve,
            ProfileCheck.Result r) {
        String line = r.lines().stream()
                .filter(l -> l.contains(" fits: ") || l.startsWith("Only ") || l.contains("does not fit"))
                .findFirst().orElse(r.lines().isEmpty() ? "" : r.lines().getLast());
        java.util.Properties p = new java.util.Properties();
        p.setProperty("selection", selection.id());
        p.setProperty("profile", selection.profile().id());
        p.setProperty("verdict", r.verdict().name());
        p.setProperty("capacity", Integer.toString(usage.capacity()));
        p.setProperty("used", Integer.toString(usage.used()));
        p.setProperty("ours", Integer.toString(usage.ours()));
        p.setProperty("stale", Integer.toString(usage.stale()));
        p.setProperty("needed", Integer.toString(r.needed()));
        p.setProperty("others", Integer.toString(r.othersUsed()));
        p.setProperty("free", Integer.toString(r.free()));
        p.setProperty("reserve", Integer.toString(reserve));
        p.setProperty("line", line);
        java.io.StringWriter w = new java.io.StringWriter();
        try {
            p.store(w, "TerraFirmaPlugin: last CraftEngine solid pool check (M1-INST-03)");
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
        return w.toString();
    }
}
