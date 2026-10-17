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
import dev.tfp.paper.integration.HudReloader;
import dev.tfp.paper.integration.PackRebuilder;
import dev.tfp.paper.integration.VisualPools;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
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
 * Every rebuild starts only once BetterHud is not reloading ({@link #rebuildAfterHud}): BetterHud merges into the
 * pack from a reload it runs inside CraftEngine's build and skips the merge ("still on reload") when CraftEngine's
 * startup asset cache already started one, so a build started then is served without BetterHud's fonts.
 * BetterHud can also start that reload after our wait but before the build's merge point (m2 date-paper-1.21.11:
 * "Successfully merged with CraftEngine" 3 s after "CraftEngine resource pack rebuilt."), so if BetterHud is still
 * reloading when a build has finished, that build missed the merge: once the reload (and with it the merge) ends,
 * the pack is built <b>one</b> more time ({@link #rebuildWithHudMerge}, at most once per run).
 * Every check's figures are also written to {@code <stateDir>/}{@value #POOL_CHECK_FILE} (io executor), so a
 * restart can be compared with the previous run (self-test {@code inst.profile_restart}).
 */
public final class PackRebuildTrigger {
    /** The last pool check's figures, {@link java.util.Properties} text ({@link #poolCheckRecord}). */
    public static final String POOL_CHECK_FILE = "pool-check.properties";
    private static final long HUD_POLL_MILLIS = 500;
    private static final int HUD_MAX_POLLS = 1200; // 10 minutes
    private final Installer.Result install;
    private final Path stateDir;
    private final PackRebuilder rebuilder;
    private final HudReloader hud;
    private final VisualPools pools;
    private final VisualIndex index;
    private final VisualSelection selection;
    private final int reserve;
    private final Executor io;
    private final Logger log;
    private final AtomicBoolean requested = new AtomicBoolean();
    /** Completes when this run's rebuild finished (successfully or not). */
    private final CompletableFuture<Void> rebuildDone = new CompletableFuture<>();
    private final AtomicBoolean extraRequested = new AtomicBoolean();
    /** Set once this run rebuilt again because BetterHud merged after a build ({@link #rebuildWithHudMerge}). */
    private final AtomicBoolean mergeRetried = new AtomicBoolean();
    /** Completes when the {@link #requestRebuild} rebuild finished. */
    private final CompletableFuture<Void> extraDone = new CompletableFuture<>();
    private volatile ProfileCheck.Result lastCheck;

    /**
     * @param index     the shipped {@code visuals/index.json} (null: no capacity check)
     * @param selection the installed allocation (null: no capacity check)
     */
    public PackRebuildTrigger(Installer.Result install, Path stateDir, PackRebuilder rebuilder, VisualPools pools,
            VisualIndex index, VisualSelection selection, int reserve, Executor io, Logger log) {
        this(install, stateDir, rebuilder, null, pools, index, selection, reserve, io, log);
    }

    /** @param hud BetterHud; every rebuild waits until it is not reloading (null: no wait) */
    public PackRebuildTrigger(Installer.Result install, Path stateDir, PackRebuilder rebuilder, HudReloader hud,
            VisualPools pools, VisualIndex index, VisualSelection selection, int reserve, Executor io, Logger log) {
        this.install = install;
        this.stateDir = stateDir;
        this.rebuilder = rebuilder;
        this.hud = hud;
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
        CompletableFuture<Void> main = planned || requested.get() ? rebuildDone.copy() : CompletableFuture.completedFuture(null);
        return extraRequested.get() ? CompletableFuture.allOf(main, extraDone) : main;
    }

    /**
     * Asks for one more rebuild in this run because a merged pack changed after startup (BetterHud's first-start
     * config edits, ARCHITECTURE.md §12 step 4). If the planned rebuild has not been requested yet it covers this
     * and nothing more is done; otherwise one extra rebuild is requested (at most once per run). The CraftEngine
     * adapter waits until CraftEngine is idle, so the build runs after BetterHud's merge. Any thread; never throws.
     */
    public void requestRebuild(String reason) {
        if (!rebuilder.available()) {
            return;
        }
        if (install != null && install.status().packRebuild() && !requested.get()) {
            log.info("The planned CraftEngine pack rebuild also covers: " + reason + ".");
            return;
        }
        if (!extraRequested.compareAndSet(false, true)) {
            return;
        }
        log.info("Rebuilding the CraftEngine resource pack (" + reason + ").");
        rebuildWithHudMerge().whenComplete((ok, err) -> {
            extraDone.complete(null);
            if (err != null || !ok) {
                log.log(Level.WARNING, "The CraftEngine pack rebuild failed; players keep the old pack. Run /ce reload all", err);
            } else {
                log.info("CraftEngine resource pack rebuilt.");
            }
        });
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
        rebuildWithHudMerge().whenComplete((ok, err) -> {
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

    /**
     * {@link #rebuildAfterHud}, then, if BetterHud is still reloading after that build finished, one more
     * {@link #rebuildAfterHud} (which waits for that reload to end). BetterHud merges only from
     * CraftEngine's pack-cache event, synchronously: our build's own merge reload has ended before the build
     * completes, so a reload still running then is one that made the build skip the merge ("still on reload!"),
     * and its end is the merge that missed the pack. At most once per run (with {@link #requestRebuild});
     * the returned future follows the last build. Never throws.
     */
    private CompletableFuture<Boolean> rebuildWithHudMerge() {
        return rebuildAfterHud().thenCompose(ok -> {
            if (!ok || !hudReloading() || !mergeRetried.compareAndSet(false, true)) {
                return CompletableFuture.completedFuture(ok);
            }
            log.info("CraftEngine resource pack rebuilt, but BetterHud was still reloading, so its merge missed the pack; "
                    + "rebuilding once more after BetterHud's merge.");
            return rebuildAfterHud();
        });
    }

    /**
     * {@link PackRebuilder#rebuild()} once BetterHud is not reloading (polled on the io executor; after
     * {@value #HUD_MAX_POLLS} polls the build starts anyway). Never throws: failures complete the future.
     */
    private CompletableFuture<Boolean> rebuildAfterHud() {
        CompletableFuture<Boolean> out = new CompletableFuture<>();
        awaitHud(out, 0);
        return out;
    }

    private void awaitHud(CompletableFuture<Boolean> out, int polls) {
        if (hudReloading() && polls < HUD_MAX_POLLS) {
            if (polls == 0) {
                log.info("Waiting for BetterHud's reload to finish before the CraftEngine pack build, so its merge lands in the pack.");
            }
            CompletableFuture.delayedExecutor(HUD_POLL_MILLIS, TimeUnit.MILLISECONDS, io).execute(() -> awaitHud(out, polls + 1));
            return;
        }
        try {
            rebuilder.rebuild().whenComplete((ok, err) -> {
                if (err != null) {
                    out.completeExceptionally(err);
                } else {
                    out.complete(ok);
                }
            });
        } catch (RuntimeException e) {
            out.completeExceptionally(e);
        }
    }

    private boolean hudReloading() {
        try {
            return hud != null && hud.available() && hud.isReloading();
        } catch (RuntimeException | LinkageError e) {
            return false;
        }
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
