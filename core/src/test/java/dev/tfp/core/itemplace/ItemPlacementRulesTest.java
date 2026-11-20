/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.itemplace;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.gather.GroundcoverType;
import dev.tfp.core.itemplace.ItemPlacementRules.Spot;
import org.junit.jupiter.api.Test;

class ItemPlacementRulesTest {
    private static final Spot AIR = new Spot(true, false);

    @Test
    void sixItemsMapToTheirGroundcover() {
        assertThat(ItemPlacementRules.typeOf("minecraft:stick")).contains(GroundcoverType.STICK);
        assertThat(ItemPlacementRules.typeOf("minecraft:bone")).contains(GroundcoverType.BONE);
        assertThat(ItemPlacementRules.typeOf("minecraft:feather")).contains(GroundcoverType.FEATHER);
        assertThat(ItemPlacementRules.typeOf("minecraft:flint")).contains(GroundcoverType.FLINT);
        assertThat(ItemPlacementRules.typeOf("minecraft:rotten_flesh")).contains(GroundcoverType.ROTTEN_FLESH);
        assertThat(ItemPlacementRules.typeOf("tfp:powder/salt")).contains(GroundcoverType.SALT_LICK);
        assertThat(ItemPlacementRules.typeOf("tfc:powder/salt")).contains(GroundcoverType.SALT_LICK);
    }

    @Test
    void itemsWithTheirOwnBlockItemOrNoPlacementAreNotMapped() {
        assertThat(ItemPlacementRules.typeOf("tfp:groundcover/clam")).isEmpty();
        assertThat(ItemPlacementRules.typeOf("minecraft:dirt")).isEmpty();
    }

    @Test
    void groundcoverNeedsReplaceableTargetAndSturdyFloor() {
        assertThat(ItemPlacementRules.placeGroundcover("minecraft:stick", AIR, true))
                .hasValueSatisfying(g -> {
                    assertThat(g.blockId()).isEqualTo("tfp:groundcover/stick");
                    assertThat(g.waterlogged()).isFalse();
                });
        assertThat(ItemPlacementRules.placeGroundcover("minecraft:stick", AIR, false)).isEmpty();
        assertThat(ItemPlacementRules.placeGroundcover("minecraft:stick", new Spot(false, false), true)).isEmpty();
        assertThat(ItemPlacementRules.placeGroundcover("minecraft:dirt", AIR, true)).isEmpty();
    }

    @Test
    void waterSourceWaterlogs() {
        assertThat(ItemPlacementRules.placeGroundcover("minecraft:bone", new Spot(true, true), true))
                .hasValueSatisfying(g -> assertThat(g.waterlogged()).isTrue());
    }

    @Test
    void snowOnPileableBlockPlacesAPile() {
        assertThat(ItemPlacementRules.snow(true, true, 0)).isInstanceOf(ItemPlacementRules.SnowAction.Pile.class);
    }

    @Test
    void snowOnAFullBlockOrUnsupportedIsVanilla() {
        assertThat(ItemPlacementRules.snow(false, true, 0)).isInstanceOf(ItemPlacementRules.SnowAction.Defer.class);
        assertThat(ItemPlacementRules.snow(true, false, 0)).isInstanceOf(ItemPlacementRules.SnowAction.Defer.class);
    }

    @Test
    void snowOnAPileAddsALayerUpToEight() {
        assertThat(ItemPlacementRules.snow(false, true, 3)).isEqualTo(new ItemPlacementRules.SnowAction.AddLayer(4));
        assertThat(ItemPlacementRules.snow(false, true, 8)).isInstanceOf(ItemPlacementRules.SnowAction.Defer.class);
    }
}
