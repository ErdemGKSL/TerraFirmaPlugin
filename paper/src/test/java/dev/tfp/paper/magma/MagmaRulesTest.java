/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.magma;

import static org.assertj.core.api.Assertions.assertThat;

import org.bukkit.GameMode;
import org.junit.jupiter.api.Test;

/** PAR-WORLD-45: the hot floor of the CraftEngine magma blocks. */
class MagmaRulesTest {
    @Test
    void recognisesThePerRockMagmaIds() {
        assertThat(MagmaRules.isMagma("tfp:rock/magma/basalt")).isTrue();
        assertThat(MagmaRules.isMagma("tfp:rock/magma/")).isFalse();
        assertThat(MagmaRules.isMagma("tfp:rock/hardened/basalt")).isFalse();
        assertThat(MagmaRules.isMagma(null)).isFalse();
    }

    @Test
    void hurtsLikeVanillaMagma() {
        assertThat(MagmaRules.hurts(GameMode.SURVIVAL, true, false, false, false)).isTrue();
        assertThat(MagmaRules.hurts(GameMode.ADVENTURE, true, false, false, false)).isTrue();
        assertThat(MagmaRules.hurts(GameMode.SURVIVAL, false, false, false, false)).as("airborne").isFalse();
        assertThat(MagmaRules.hurts(GameMode.SURVIVAL, true, true, false, false)).as("sneaking").isFalse();
        assertThat(MagmaRules.hurts(GameMode.SURVIVAL, true, false, true, false)).as("frost walker").isFalse();
        assertThat(MagmaRules.hurts(GameMode.SURVIVAL, true, false, false, true)).as("fire immune").isFalse();
        assertThat(MagmaRules.hurts(GameMode.CREATIVE, true, false, false, false)).isFalse();
        assertThat(MagmaRules.hurts(GameMode.SPECTATOR, true, false, false, false)).isFalse();
    }
}
