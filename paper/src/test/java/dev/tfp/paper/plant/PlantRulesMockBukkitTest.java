/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.plant;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.worldgen.plant.PlantType;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.FakeCustomBlocks;
import dev.tfp.paper.testing.MockServerTest;
import java.util.Set;
import org.bukkit.World;
import org.bukkit.util.BoundingBox;
import org.junit.jupiter.api.Test;

/** PAR-FARM-12: the survival decision of a snapshot, the plant types the module covers, and the cactus contact box test. */
class PlantRulesMockBukkitTest extends MockServerTest {
    @Test
    void aPlantWithoutItsSupportTagDoesNotSurvive() {
        BlockStateRef rose = BlockStateRef.of("tfp:plant/allium");
        PlantSupport.Snapshot floating = new PlantSupport.Snapshot(rose, PlantType.STANDARD, false, Set.of(), false, false);
        assertThat(PlantSupport.decide(floating)).isFalse();
        PlantSupport.Snapshot supported = new PlantSupport.Snapshot(rose, PlantType.STANDARD, false, Set.of("tfc:bush_plantable_on"), false,
            false);
        assertThat(PlantSupport.decide(supported)).isTrue();
    }

    @Test
    void anUpperHalfNeedsItsLowerHalf() {
        BlockStateRef upper = BlockStateRef.of("tfp:plant/rose").with("half", "upper");
        assertThat(PlantSupport.decide(new PlantSupport.Snapshot(upper, PlantType.TALL_GRASS, true, Set.of("tfc:grass_plantable_on"), false,
            false))).isFalse();
        assertThat(PlantSupport.decide(new PlantSupport.Snapshot(upper, PlantType.TALL_GRASS, true, Set.of(), true, false))).isTrue();
    }

    @Test
    void theModuleCoversTheLandPlantsAndLeavesFloatingOnesToCraftEngine() {
        assertThat(PlantSupport.typeOf("tfp:plant/allium")).contains(PlantType.STANDARD);
        assertThat(PlantSupport.typeOf("tfp:plant/ryegrass")).contains(PlantType.SHORT_GRASS);
        assertThat(PlantSupport.typeOf("tfp:plant/lotus")).isEmpty();
        assertThat(PlantSupport.typeOf("tfp:plant/not_a_plant")).isEmpty();
        assertThat(PlantSupport.typeOf("tfp:crop/wheat")).isEmpty();
    }

    @Test
    void aBoxInsideACactusCellIsHurt() {
        FakeCustomBlocks blocks = new FakeCustomBlocks().known("tfp:plant/prickly_pear", "tfp:plant/allium");
        World world = server.addSimpleWorld("cactus");
        blocks.place(world.getBlockAt(5, 64, 5), BlockStateRef.of("tfp:plant/prickly_pear").with("half", "lower"), false);
        blocks.place(world.getBlockAt(9, 64, 9), BlockStateRef.of("tfp:plant/allium"), false);
        CactusContactTask task = new CactusContactTask(blocks, null);
        assertThat(task.touches(world, BoundingBox.of(new org.bukkit.util.Vector(5.5, 64.5, 5.5), 0.3, 0.9, 0.3))).isTrue();
        // standing on the cell next to it (boxes ending exactly at the border do not touch)
        assertThat(task.touches(world, new BoundingBox(6.0, 64, 5.2, 6.6, 65.8, 5.8))).isFalse();
        assertThat(task.touches(world, BoundingBox.of(new org.bukkit.util.Vector(9.5, 64.5, 9.5), 0.3, 0.9, 0.3))).isFalse();
    }
}
