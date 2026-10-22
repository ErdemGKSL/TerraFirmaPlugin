/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.install;

import dev.tfp.core.install.ConfigEdit;
import dev.tfp.core.install.InstallStatus.EditResult;
import dev.tfp.paper.integration.HudReloader;
import dev.tfp.paper.integration.PackRebuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * M1-INST-02 on a fresh server (ARCHITECTURE.md §12 step 4): BetterHud writes its {@code config.yml} only on its
 * first start, after our {@code onLoad}, so its edits (e.g. {@code default-hud} without {@code test_hud}, with {@code tfp_date_hud}) end as
 * {@code FILE_MISSING} (and so does the demo compass edit, {@code compasses/default_compass.yml}). Once BetterHud has enabled ({@code PluginEnableEvent}, or {@code ServerLoadEvent} if it
 * already was), {@link #betterHudEnabled} applies them on the io executor, then asks {@link PackRebuildTrigger} for the
 * CraftEngine pack build, which reloads BetterHud inside it (M0-BH-01). Only without CraftEngine is BetterHud
 * reloaded directly (skipped while it is already reloading).
 *
 * <p>Runs once per server run; tried again only if the file was still missing.
 */
public final class PendingHudEdit {
    private static final ConfigEdit.Target TARGET = ConfigEdit.Target.BETTERHUD_CONFIG;
    private final Installer.Inputs inputs;
    private final Installer.Result install;
    private final HudReloader hud;
    private final PackRebuilder craftEngine;
    private final PackRebuildTrigger trigger;
    private final Executor io;
    private final Logger log;
    private final AtomicBoolean started = new AtomicBoolean();

    public PendingHudEdit(Installer.Inputs inputs, Installer.Result install, HudReloader hud, PackRebuilder craftEngine,
            PackRebuildTrigger trigger, Executor io, Logger log) {
        this.inputs = inputs;
        this.install = install;
        this.hud = hud;
        this.craftEngine = craftEngine;
        this.trigger = trigger;
        this.io = io;
        this.log = log;
    }

    /** From the {@code PluginEnableEvent} of {@code plugin} (and the {@code ServerLoadEvent} fallback). Any thread. */
    public CompletableFuture<List<EditResult>> betterHudEnabled(String plugin) {
        if (!Installer.BETTERHUD.equals(plugin) || inputs == null || !Installer.pending(install, TARGET)
                || !started.compareAndSet(false, true)) {
            return CompletableFuture.completedFuture(List.of());
        }
        return CompletableFuture.supplyAsync(() -> {
            List<EditResult> config = Installer.applyPending(inputs, TARGET, log);
            List<EditResult> edits = new ArrayList<>(config);
            // BetterHud writes its demo compass file in the same first start.
            edits.addAll(Installer.applyPending(inputs, ConfigEdit.Target.BETTERHUD_DEMO_COMPASS, log));
            if (!config.isEmpty() && config.stream().allMatch(e -> e.outcome() == ConfigEdit.Outcome.FILE_MISSING)) {
                started.set(false); // not written yet: the ServerLoadEvent fallback tries again
            } else if (edits.stream().anyMatch(e -> e.outcome() == ConfigEdit.Outcome.APPLIED)) {
                reload();
            }
            return edits;
        }, io).exceptionally(e -> {
            log.log(Level.WARNING, "Cannot apply the pending " + TARGET.path() + " edits", e);
            return List.of();
        });
    }

    /**
     * On the io executor, after the edits were written. With CraftEngine present we do not reload BetterHud
     * ourselves: BetterHud reloads inside every CraftEngine pack build (M0-BH-01), and that build is deferred until
     * CraftEngine is idle. A direct reload here ran while BetterHud and BetterModel were still starting and failed
     * ("Unable to make a resource pack", {@code GraphicsEnvironment$LocalGE}), and the failed AWT class init then
     * broke BetterHud's merge in the following CraftEngine build too (spikes/run/m1/hud-paper-*).
     */
    private void reload() {
        if (craftEngine.available()) {
            log.info("BetterHud config edited on its first start; the CraftEngine pack build reloads BetterHud");
        } else if (hud.isReloading()) {
            log.info("BetterHud config edited while BetterHud is reloading; not reloading BetterHud again");
        } else if (hud.available()) {
            log.info("BetterHud config edited on its first start; reloading BetterHud");
            try {
                hud.reload();
            } catch (RuntimeException | LinkageError e) {
                log.log(Level.WARNING, "BetterHud reload failed", e);
            }
        }
        trigger.requestRebuild("BetterHud config edited on its first start");
    }
}
