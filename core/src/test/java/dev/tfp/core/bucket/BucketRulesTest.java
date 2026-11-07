/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/fluids/FluidHelpers.java @ c3b4d9536 (emptyFluidFrom, pickupFluidInto)
 */
package dev.tfp.core.bucket;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.bucket.BucketRules.HandUpdate;
import dev.tfp.core.bucket.BucketRules.Outcome;
import dev.tfp.core.bucket.BucketRules.Target;
import dev.tfp.core.fluid.FluidContainerKind;
import dev.tfp.core.vessel.FluidAmount;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BucketRulesTest {
    private static final Target STONE = new Target(false, false, false, null, false, false);
    private static final Target GRASS = new Target(false, true, false, null, false, false);
    private static final Target WATER_SOURCE = new Target(false, true, true, WorldFluid.WATER, true, false);
    private static final Target WATER_FLOWING = new Target(false, true, true, WorldFluid.WATER, false, false);
    private static final Target LAVA_SOURCE = new Target(false, true, true, WorldFluid.LAVA, true, false);
    private static final Target STAIRS = new Target(false, false, false, null, false, true);

    @Test
    void pickupFillsAFullBucketAndRemovesTheSource() {
        BucketRules.Pickup p = BucketRules.pickup("minecraft:water", 1000, true);
        assertThat(p.content()).isEqualTo(new FluidAmount("minecraft:water", 1000));
        assertThat(p.filled()).isEqualTo(1000);
        assertThat(p.removesSource()).isTrue();
    }

    @Test
    void pickupOfAFluidTheBucketMayNotHoldDoesNothing() {
        BucketRules.Pickup p = BucketRules.pickup("minecraft:lava", 1000, false);
        assertThat(p.content()).isEqualTo(FluidAmount.EMPTY);
        assertThat(p.removesSource()).isFalse();
    }

    @Test
    void pickupIntoASmallTankStillRemovesTheWholeSource() {
        // TFC: "Some fluid was filled, so we need to be aggressive about picking up the original fluid"
        BucketRules.Pickup p = BucketRules.pickup("minecraft:water", 250, true);
        assertThat(p.filled()).isEqualTo(250);
        assertThat(p.removesSource()).isTrue();
    }

    @Test
    void defaultConfigPlacesATransientFlowingBlock() {
        var p = BucketRules.place(WorldFluid.WATER, 1000, false, Target.AIR, false, true);
        assertThat(p.outcome()).isEqualTo(Outcome.PLACE_FLOWING);
        assertThat(p.breakFirst()).isFalse();
        assertThat(p.drains()).isTrue();
    }

    @Test
    void sourcePlacementNeedsTheConfigAndAFullBucket() {
        assertThat(BucketRules.place(WorldFluid.WATER, 1000, true, Target.AIR, false, true).outcome())
                .isEqualTo(Outcome.PLACE_SOURCE);
        assertThat(BucketRules.place(WorldFluid.WATER, 999, true, Target.AIR, false, true).outcome())
                .isEqualTo(Outcome.PLACE_FLOWING);
    }

    @Test
    void aSolidTargetTriesTheAdjacentBlockOnce() {
        assertThat(BucketRules.place(WorldFluid.WATER, 1000, false, STONE, false, true).outcome())
                .isEqualTo(Outcome.TRY_ADJACENT);
        var second = BucketRules.place(WorldFluid.WATER, 1000, false, STONE, false, false);
        assertThat(second.outcome()).isEqualTo(Outcome.REFUSE);
        assertThat(second.drains()).isFalse();
    }

    @Test
    void replaceablePlantsBreakFirst() {
        var p = BucketRules.place(WorldFluid.LAVA, 1000, false, GRASS, false, true);
        assertThat(p.outcome()).isEqualTo(Outcome.PLACE_FLOWING);
        assertThat(p.breakFirst()).isTrue();
    }

    @Test
    void theSameFluidAbsorbsTheBucket() {
        assertThat(BucketRules.place(WorldFluid.WATER, 1000, false, WATER_SOURCE, false, true).outcome())
                .isEqualTo(Outcome.ABSORB);
        assertThat(BucketRules.place(WorldFluid.WATER, 500, false, WATER_FLOWING, false, true).outcome())
                .isEqualTo(Outcome.ABSORB);
        assertThat(BucketRules.place(WorldFluid.WATER, 1000, true, WATER_SOURCE, false, true).outcome())
                .isEqualTo(Outcome.ABSORB);
        // with sources allowed, a flowing block of the fluid becomes a source
        assertThat(BucketRules.place(WorldFluid.WATER, 1000, true, WATER_FLOWING, false, true).outcome())
                .isEqualTo(Outcome.PLACE_SOURCE);
        // water over lava is set (the vanilla fluid interaction follows)
        var overLava = BucketRules.place(WorldFluid.WATER, 1000, false, LAVA_SOURCE, false, true);
        assertThat(overLava.outcome()).isEqualTo(Outcome.PLACE_FLOWING);
        assertThat(overLava.breakFirst()).isFalse();
    }

    @Test
    void waterVaporizesInUltrawarmDimensionsButLavaDoesNot() {
        assertThat(BucketRules.place(WorldFluid.WATER, 1000, false, Target.AIR, true, true).outcome())
                .isEqualTo(Outcome.VAPORIZE);
        assertThat(BucketRules.place(WorldFluid.LAVA, 1000, false, Target.AIR, true, true).outcome())
                .isEqualTo(Outcome.PLACE_FLOWING);
    }

    @Test
    void waterloggingNeedsSourcePlacement() {
        assertThat(BucketRules.place(WorldFluid.WATER, 1000, true, STAIRS, false, true).outcome())
                .isEqualTo(Outcome.WATERLOG);
        // not allowed to place sources: the stairs are not replaceable, so the adjacent block is tried
        assertThat(BucketRules.place(WorldFluid.WATER, 1000, false, STAIRS, false, true).outcome())
                .isEqualTo(Outcome.TRY_ADJACENT);
        // lava never waterlogs
        assertThat(BucketRules.place(WorldFluid.LAVA, 1000, true, STAIRS, false, true).outcome())
                .isEqualTo(Outcome.TRY_ADJACENT);
        // a replaceable waterloggable block without source placement denies the behaviour
        Target replaceableLoggable = new Target(false, true, false, null, false, true);
        assertThat(BucketRules.place(WorldFluid.WATER, 1000, false, replaceableLoggable, false, true).outcome())
                .isEqualTo(Outcome.REFUSE);
    }

    @Test
    void handUpdateFollowsFluidHelpersWith() {
        assertThat(BucketRules.handUpdate(1, false)).isEqualTo(HandUpdate.REPLACE);
        assertThat(BucketRules.handUpdate(1, true)).isEqualTo(HandUpdate.KEEP);
        assertThat(BucketRules.handUpdate(3, false)).isEqualTo(HandUpdate.SPLIT);
        assertThat(BucketRules.handUpdate(3, true)).isEqualTo(HandUpdate.SPLIT);
    }

    @Test
    void worldFluidMapping() {
        assertThat(WorldFluid.WATER.pickedUp(false)).isEqualTo("minecraft:water");
        assertThat(WorldFluid.WATER.pickedUp(true)).isEqualTo("tfp:salt_water");
        assertThat(WorldFluid.LAVA.pickedUp(true)).isEqualTo("minecraft:lava");
        assertThat(WorldFluid.placedAs("tfp:spring_water")).contains(WorldFluid.WATER);
        assertThat(WorldFluid.placedAs("tfp:salt_water")).contains(WorldFluid.WATER);
        assertThat(WorldFluid.placedAs("tfp:river_water")).contains(WorldFluid.WATER);
        assertThat(WorldFluid.placedAs("minecraft:lava")).contains(WorldFluid.LAVA);
        assertThat(WorldFluid.placedAs("tfp:beer")).isEmpty();
        assertThat(WorldFluid.placedAs("tfp:metal/copper")).isEmpty();
        assertThat(WorldFluid.placedAs("minecraft:milk")).isEmpty();
    }

    @Test
    void bucketKindsAndConfig() {
        assertThat(BucketKind.byItem("tfp:wooden_bucket")).contains(BucketKind.WOODEN);
        assertThat(BucketKind.byItem("tfp:metal/bucket/red_steel")).contains(BucketKind.RED_STEEL);
        assertThat(BucketKind.byItem("minecraft:bucket")).isEmpty();
        assertThat(BucketKind.BLUE_STEEL.container()).isEqualTo(FluidContainerKind.BLUE_STEEL_BUCKET);
        assertThat(BucketConfig.DEFAULTS.canPlaceSources(BucketKind.WOODEN)).isFalse();
        BucketConfig c = BucketConfig.fromTree(Map.of("buckets", Map.of("metal-bucket-can-place-sources", true)));
        assertThat(c.canPlaceSources(BucketKind.RED_STEEL)).isTrue();
        assertThat(c.canPlaceSources(BucketKind.WOODEN)).isFalse();
        assertThat(BucketConfig.fromTree(Map.of("buckets", "nonsense"))).isEqualTo(BucketConfig.DEFAULTS);
        assertThat(BucketConfig.fromTree(null)).isEqualTo(BucketConfig.DEFAULTS);
    }
}
