/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.integration;

/**
 * Reloads the HUD plugin after we edited its config (M1-INST-02: BetterHud's {@code config.yml} only exists
 * after BetterHud's first start, so the edits are applied then and need a reload, not a restart).
 * Implemented by {@code integration/betterhud}.
 *
 * <p>BetterHud also reloads inside every CraftEngine pack build (M0-BH-01): never call {@link #reload()} while
 * {@link #isReloading()} or while CraftEngine builds ({@link PackRebuilder#busy()}); rebuild the CraftEngine pack
 * afterwards through {@code PackRebuildTrigger}, so the pack is built after BetterHud's merge.
 */
public interface HudReloader {
    /** True if a HUD plugin is installed and its adapter is running. */
    boolean available();

    /** True while the HUD plugin is reloading. Any thread. */
    boolean isReloading();

    /** Reloads the HUD plugin on the calling thread (blocking: io executor). True on success. */
    boolean reload();
}
