/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/entities/misc/TFCFallingBlockEntity.java @ c3b4d9536
 */
package dev.tfp.core.falling;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.OptionalDouble;
import org.junit.jupiter.api.Test;

/**
 * PAR-PHYS-01 golden values against TFC's {@code TFCFallingBlockEntity} (toughness, canFallThrough, the on-ground
 * branch in its order) and vanilla {@code FallingBlockEntity} (fall damage, motion).
 */
class FallRulesTest {
    /** A torch or a flower: not sturdy, not replaceable, toughness 0. */
    private static final FallCell TORCH = new FallCell(false, false, 0, false, false);
    /** A bottom slab: up face not sturdy. */
    private static final FallCell SLAB = new FallCell(false, false, 0, false, false);
    /** Raw stone, toughness 2 from #c:stones. */
    private static final FallCell STONE = new FallCell(false, true, 2, false, false);
    /** A stone wall top: not sturdy but toughness 2. */
    private static final FallCell STONE_WALL = new FallCell(false, false, 2, false, false);
    private static final FallCell BEDROCK = new FallCell(false, true, 4, true, false);
    /** An end portal frame: not sturdy, unbreakable. */
    private static final FallCell PORTAL_FRAME = new FallCell(false, false, 0, true, false);
    private static final FallCell WATER = new FallCell(true, false, 0, false, true);
    /** Tall grass: replaceable, not sturdy. */
    private static final FallCell GRASS = new FallCell(false, false, 0, false, true);
    /** Soul sand: full-block sturdy faces? its up face is sturdy in vanilla but its collision is 14/16. */
    private static final FallCell SOUL_SAND = new FallCell(false, true, 0, false, false);

    @Test
    void toughnessOrder() {
        assertThat(FallRules.toughness(true, true, true, true)).isEqualTo(4);
        assertThat(FallRules.toughness(false, true, true, true)).isEqualTo(3);
        assertThat(FallRules.toughness(false, false, true, true)).isEqualTo(2);
        assertThat(FallRules.toughness(false, false, false, true)).isEqualTo(1);
        assertThat(FallRules.toughness(false, false, false, false)).isZero();
    }

    @Test
    void canFallThroughNeedsNonSturdyEqualToughnessAndBreakable() {
        assertThat(FallRules.canFallThrough(TORCH, 0)).isTrue();
        assertThat(FallRules.canFallThrough(FallCell.AIR, 0)).isTrue();
        assertThat(FallRules.canFallThrough(STONE, 4)).as("sturdy").isFalse();
        assertThat(FallRules.canFallThrough(STONE_WALL, 0)).as("tougher").isFalse();
        assertThat(FallRules.canFallThrough(STONE_WALL, 2)).as("equal toughness").isTrue();
        assertThat(FallRules.canFallThrough(PORTAL_FRAME, 4)).as("unbreakable").isFalse();
        assertThat(FallRules.canFallThrough(BEDROCK, 4)).isFalse();
    }

    private static FallRules.Contact contact(FallCell at, FallCell below, FallCell above) {
        return new FallRules.Contact(at, below, above, true, true, true, false, false);
    }

    @Test
    void landsOnStoneAsItself() {
        assertThat(FallRules.decide(contact(FallCell.AIR, STONE, FallCell.AIR), 0)).isEqualTo(Landing.PLACE_AT);
    }

    @Test
    void breaksATorchItStandsInFirstThenTheSlabBelow() {
        assertThat(FallRules.decide(contact(TORCH, SLAB, FallCell.AIR), 0)).isEqualTo(Landing.DESTROY_AT);
        assertThat(FallRules.decide(contact(FallCell.AIR, SLAB, FallCell.AIR), 0)).isEqualTo(Landing.DESTROY_BELOW);
    }

    @Test
    void noBreakRightAfterABreakOrWhenNotOnABlock() {
        FallRules.Contact afterBreak = new FallRules.Contact(TORCH, STONE, FallCell.AIR, true, true, true, true, false);
        // the torch is not replaceable and, with bedrock toughness, the torch under the above cell can be fallen
        // through, so TFC breaks the (air) block above and places there
        assertThat(FallRules.decide(afterBreak, 0)).isEqualTo(Landing.DESTROY_ABOVE_AND_PLACE_ABOVE);
        FallRules.Contact onEntity = new FallRules.Contact(TORCH, STONE, FallCell.AIR, true, true, false, false, false);
        assertThat(FallRules.decide(onEntity, 0)).as("on a boat: no break, cannot place, drop").isEqualTo(Landing.DROP);
    }

    @Test
    void airAndFluidAreNotBrokenButReplaced() {
        assertThat(FallRules.decide(contact(WATER, STONE, FallCell.AIR), 0)).isEqualTo(Landing.PLACE_AT);
    }

    @Test
    void replaceableGrassIsBrokenFirstAsTfcDoes() {
        // grass is not air: canFallThrough holds, so TFC destroys it (with drops) before placing
        assertThat(FallRules.decide(contact(GRASS, STONE, FallCell.AIR), 0)).isEqualTo(Landing.DESTROY_AT);
        FallRules.Contact again = new FallRules.Contact(GRASS, STONE, FallCell.AIR, true, true, true, true, false);
        assertThat(FallRules.decide(again, 0)).isEqualTo(Landing.PLACE_AT);
    }

    @Test
    void tougherBlockBelowIsLandedOn() {
        assertThat(FallRules.decide(contact(FallCell.AIR, STONE_WALL, FallCell.AIR), 0)).isEqualTo(Landing.PLACE_AT);
        assertThat(FallRules.decide(contact(FallCell.AIR, STONE_WALL, FallCell.AIR), 2))
                .isEqualTo(Landing.DESTROY_BELOW);
    }

    @Test
    void movingPistonBouncesAfterTheBreakChecks() {
        FallRules.Contact piston = new FallRules.Contact(FallCell.AIR, STONE, FallCell.AIR, true, true, true, false,
                true);
        assertThat(FallRules.decide(piston, 0)).isEqualTo(Landing.BOUNCE);
    }

    @Test
    void cannotSurviveTriesAboveThenDrops() {
        FallRules.Contact noSurvive = new FallRules.Contact(FallCell.AIR, STONE, FallCell.AIR, false, true, true, false,
                false);
        // above: canPlaceAt(above, survivesAbove, below = at (air)) with bedrock toughness: air can be fallen through
        assertThat(FallRules.decide(noSurvive, 0)).as("air under the above cell: falls through, so break above")
                .isEqualTo(Landing.DESTROY_ABOVE_AND_PLACE_ABOVE);
        FallRules.Contact blockedAbove = new FallRules.Contact(FallCell.AIR, STONE, BEDROCK, false, false, true, false,
                false);
        assertThat(FallRules.decide(blockedAbove, 0)).isEqualTo(Landing.DROP);
    }

    @Test
    void soulSandStylePartialBlockPlacesAbove() {
        // standing inside the soul sand cell (collision 14/16): at = soul sand, not replaceable, sturdy, so no break
        FallRules.Contact c = new FallRules.Contact(SOUL_SAND, STONE, FallCell.AIR, true, true, true, false, false);
        assertThat(FallRules.decide(c, 0)).isEqualTo(Landing.PLACE_ABOVE);
    }

    @Test
    void finishingOutcomes() {
        assertThat(Landing.PLACE_AT.finishes()).isTrue();
        assertThat(Landing.DROP.finishes()).isTrue();
        assertThat(Landing.DESTROY_AT.finishes()).isFalse();
        assertThat(Landing.BOUNCE.finishes()).isFalse();
    }

    @Test
    void expiryAsTfc() {
        assertThat(FallRules.expires(600, 64, -64, 320)).isFalse();
        assertThat(FallRules.expires(601, 64, -64, 320)).isTrue();
        assertThat(FallRules.expires(100, -70, -64, 320)).isFalse();
        assertThat(FallRules.expires(101, -70, -64, 320)).isTrue();
        assertThat(FallRules.expires(101, 321, -64, 320)).isTrue();
    }

    @Test
    void vanillaFallDamageWithTfcCollapseAndLandslideValues() {
        // collapse: 2.0 per block, max 20
        assertThat(FallRules.damage(0.5, 2.0f, 20)).isZero();
        assertThat(FallRules.damage(1.0, 2.0f, 20)).isZero(); // ceil(0) = 0 -> floor(0) = 0
        assertThat(FallRules.damage(1.5, 2.0f, 20)).isEqualTo(2f);
        assertThat(FallRules.damage(4.0, 2.0f, 20)).isEqualTo(6f);
        assertThat(FallRules.damage(30.0, 2.0f, 20)).isEqualTo(20f);
        // landslide: 0.8 per block, max 10
        assertThat(FallRules.damage(4.0, 0.8f, 10)).isEqualTo(2f); // floor(3 * 0.8)
        assertThat(FallRules.damage(2.0, 0.8f, 10)).isZero(); // floor(0.8)
        assertThat(FallRules.damage(100.0, 0.8f, 10)).isEqualTo(10f);
    }

    @Test
    void restingTopIsTheHighestTopCrossedThisTick() {
        double[] tops = {64.0, 63.0, 64.5};
        double[] bottoms = {63.0, 62.0, 64.0};
        // from 65.2 down by 1.0: crosses 64.5 (a slab in cell 64)
        assertThat(FallRules.restingTop(65.2, -1.0, tops, bottoms)).isEqualTo(OptionalDouble.of(64.5));
        // from 66 down by 0.5: nothing
        assertThat(FallRules.restingTop(66.0, -0.5, tops, bottoms)).isEmpty();
        // already resting on 64.5 with the next tick's gravity step
        assertThat(FallRules.restingTop(64.5, FallRules.moveVelocity(0.0), tops, bottoms))
                .isEqualTo(OptionalDouble.of(64.5));
        // a box whose bottom is not below the block (beside or above it) is ignored
        assertThat(FallRules.restingTop(64.0, -0.5, new double[] {64.0}, new double[] {64.0})).isEmpty();
    }

    @Test
    void motionIsVanilla() {
        assertThat(FallRules.moveVelocity(0.0)).isEqualTo(-0.04);
        // after 20 ticks from rest a falling block moves about 0.65 blocks per tick (vanilla terminal ~1.96)
        double vy = 0;
        for (int t = 0; t < 20; t++) {
            vy = FallRules.moveVelocity(vy) * FallRules.DRAG;
        }
        assertThat(vy).isBetween(-0.67, -0.65);
    }
}
