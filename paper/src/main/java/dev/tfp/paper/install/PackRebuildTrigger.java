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
import java.nio.file.Path;
import java.util.Optional;
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
 */
public final class PackRebuildTrigger {
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
        rebuilder.rebuild().whenComplete((ok, err) -> {
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
        Level level = switch (r.verdict()) {
            case OK -> Level.INFO;
            case WARN -> Level.WARNING;
            case FAIL -> Level.SEVERE;
        };
        for (String line : r.lines()) {
            log.log(level, line);
        }
    }
}
