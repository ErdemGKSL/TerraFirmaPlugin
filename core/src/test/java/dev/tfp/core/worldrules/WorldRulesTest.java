/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.worldrules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.tfp.core.worldrules.WorldRules.Liquid;
import dev.tfp.core.worldrules.WorldRules.Neighbour;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** PAR-DATA-14: golden values against TFC's ForgeEventHandler and mixins @ c3b4d9536. */
class WorldRulesTest {
    private static final Neighbour SOURCE = new Neighbour(true, 8, true, false, false);
    private static final Neighbour AQUEDUCT = new Neighbour(true, 8, true, true, false);
    private static final Neighbour SLUICE_FED = new Neighbour(true, 8, true, false, true);
    private static final Neighbour FLOW_5 = new Neighbour(true, 5, false, false, false);

    @Test
    void lavaAndWaterMakeTfcHardenedRock() {
        // onFluidPlaceBlock: STONE -> gabbro, COBBLESTONE -> rhyolite, BASALT -> basalt
        assertThat(WorldRules.hardenedRock("minecraft:stone")).contains("tfp:rock/hardened/gabbro");
        assertThat(WorldRules.hardenedRock("minecraft:cobblestone")).contains("tfp:rock/hardened/rhyolite");
        assertThat(WorldRules.hardenedRock("minecraft:basalt")).contains("tfp:rock/hardened/basalt");
        assertThat(WorldRules.hardenedRock("minecraft:obsidian")).isEmpty();
        assertThat(WorldRules.hardenedRock("minecraft:ice")).isEmpty();
    }

    @Test
    void breakingSnowTakesOneLayerOutOfCreative() {
        assertThat(WorldRules.snowLayersAfterBreak(4, false)).isEqualTo(3);
        assertThat(WorldRules.snowLayersAfterBreak(8, false)).isEqualTo(7);
        assertThat(WorldRules.snowLayersAfterBreak(1, false)).isZero();
        assertThat(WorldRules.snowLayersAfterBreak(4, true)).isZero();
        assertThatThrownBy(() -> WorldRules.snowLayersAfterBreak(9, false)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void leavesHoldOneSnowLayer() {
        assertThat(WorldRules.snowPlacementAllowed(1, true)).isTrue();
        assertThat(WorldRules.snowPlacementAllowed(2, true)).isFalse();
        assertThat(WorldRules.snowPlacementAllowed(5, false)).isTrue();
    }

    @Test
    void predatorsStayOutOfBoatsAndTadpolesEatNothing() {
        assertThat(WorldRules.mayMount(true, true)).isFalse();
        assertThat(WorldRules.mayMount(true, false)).isTrue();
        assertThat(WorldRules.mayMount(false, true)).isTrue();
        assertThat(WorldRules.tadpoleFood()).isFalse();
    }

    @Test
    void bambooGrowsWithLongGrowthChance() {
        assertThat(WorldRules.PLANT_LONG_GROWTH_CHANCE).isEqualTo(0.03);
        assertThat(WorldRules.bambooGrows(0.03f, 0.03)).isTrue(); // TFC's float roll: 0.03f widens to 0.0299999993, not above 0.03
        assertThat(WorldRules.bambooGrows(0.0300001f, 0.03)).isFalse();
        assertThat(WorldRules.bambooGrows(0.02f, 0.03)).isTrue();
        assertThat(WorldRules.bambooGrows(0.5f, 0.03)).isFalse();
        assertThat(WorldRules.bambooGrows(0.0f, 0.0)).isTrue();
    }

    @Test
    void vanillaNewLiquid() {
        assertThat(WorldRules.newLiquid(List.of(SOURCE, SOURCE, Neighbour.NONE, Neighbour.NONE), true, false, false))
                .isEqualTo(Liquid.SOURCE);
        // two sources but nothing to hold it: flows at 7, or falls when water is above
        assertThat(WorldRules.newLiquid(List.of(SOURCE, SOURCE, Neighbour.NONE, Neighbour.NONE), false, false, false))
                .isEqualTo(new Liquid(Liquid.Kind.FLOWING, 7));
        assertThat(WorldRules.newLiquid(List.of(SOURCE, SOURCE, Neighbour.NONE, Neighbour.NONE), false, true, false))
                .isEqualTo(Liquid.FALLING);
        assertThat(WorldRules.newLiquid(List.of(FLOW_5, Neighbour.NONE, Neighbour.NONE, Neighbour.NONE), true, false, false))
                .isEqualTo(new Liquid(Liquid.Kind.FLOWING, 4));
        assertThat(WorldRules.newLiquid(List.of(new Neighbour(true, 1, false, false, false)), true, false, false))
                .isEqualTo(Liquid.EMPTY);
        assertThat(new Liquid(Liquid.Kind.FLOWING, 7).level()).isEqualTo(1);
        assertThat(Liquid.SOURCE.level()).isZero();
        assertThat(Liquid.FALLING.level()).isEqualTo(8);
    }

    @Test
    void aqueductsAndSluiceFedWaterMakeNoSource() {
        assertThat(WorldRules.deniedSource(List.of(SOURCE, AQUEDUCT, Neighbour.NONE, Neighbour.NONE), true, false))
                .contains(new Liquid(Liquid.Kind.FLOWING, 7));
        assertThat(WorldRules.deniedSource(List.of(SLUICE_FED, SOURCE, Neighbour.NONE, Neighbour.NONE), true, true))
                .contains(Liquid.FALLING);
        // a third plain source still makes one
        assertThat(WorldRules.deniedSource(List.of(SOURCE, AQUEDUCT, SOURCE, Neighbour.NONE), true, false)).isEmpty();
        // vanilla makes no source: nothing to deny
        assertThat(WorldRules.deniedSource(List.of(SOURCE, Neighbour.NONE, Neighbour.NONE, Neighbour.NONE), true, false))
                .isEqualTo(Optional.empty());
    }
}
