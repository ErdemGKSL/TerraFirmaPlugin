/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.rendered;

import org.bukkit.entity.Player;

/**
 * PAR-INFRA-32: the client plugin's server-side mining loop for blocks the client cannot break itself (renderer cubes
 * on a barrier carrier and every other custom block whose break the server runs). Implemented by the CraftEngine
 * adapter, installed in {@link MiningDrivers}; feature code never calls CraftEngine directly.
 *
 * <p>Both methods run on the player's thread (Paper main thread, Folia: the player's region thread).
 */
public interface MiningDriver {
    /** False for the absent driver: nothing to drive. */
    default boolean available() {
        return true;
    }

    /** Whether the plugin is mining a block for {@code player} whose progress the server computes. */
    boolean isServerMining(Player player);

    /** Counts this server tick as a mining tick (CraftEngine: one arm-swing acknowledgement, counted at most once a tick). */
    void advance(Player player);

    /** No client plugin: no server-side mining loop. */
    MiningDriver NONE = new MiningDriver() {
        @Override
        public boolean available() {
            return false;
        }

        @Override
        public boolean isServerMining(Player player) {
            return false;
        }

        @Override
        public void advance(Player player) {
        }
    };
}
