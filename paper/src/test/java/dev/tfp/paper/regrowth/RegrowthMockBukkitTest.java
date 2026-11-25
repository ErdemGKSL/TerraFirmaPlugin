/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.regrowth;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.calendar.CalendarTicks;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.testing.MockServerTest;
import java.util.Optional;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

/** PAR-FARM-11: the cutting's stored plant, plant names, new plant states and the winter rule. */
class RegrowthMockBukkitTest extends MockServerTest {
    @Test
    void aCuttingKeepsItsPlantInTheTfpPlantRecord() {
        ItemStack stack = new ItemStack(Material.PAPER);
        assertThat(CuttingPdc.read(stack)).isEmpty();
        assertThat(CuttingPdc.write(stack, "tfp:plant/rose")).isTrue();
        assertThat(CuttingPdc.read(stack)).contains("tfp:plant/rose");
        assertThat(CuttingPdc.read(stack.clone())).contains("tfp:plant/rose");
        assertThat(CuttingPdc.read(new ItemStack(Material.PAPER))).isEmpty();
    }

    @Test
    void plantNamesComeFromTheBlockId() {
        assertThat(PlantSweeper.plantName("tfp:plant/ryegrass")).isEqualTo(Optional.of("ryegrass"));
        assertThat(PlantSweeper.plantName("tfp:crop/wheat")).isEmpty();
    }

    @Test
    void aNewPlantIsTheParentAtAgeZeroAndATallOneIsALowerHalf() {
        BlockStateRef parent = BlockStateRef.of("tfp:plant/rose").with("age", "3").with("half", "lower");
        assertThat(PlantSweeper.newState(parent, true).properties()).containsEntry("age", "0").containsEntry("half", "lower");
        BlockStateRef grass = BlockStateRef.of("tfp:plant/ryegrass").with("age", "2");
        assertThat(PlantSweeper.newState(grass, false).properties()).containsOnlyKeys("age").containsEntry("age", "0");
        BlockStateRef noAge = BlockStateRef.of("tfp:plant/beachgrass");
        assertThat(PlantSweeper.newState(noAge, false)).isEqualTo(noAge);
    }

    @Test
    void winterFollowsTheHemisphere() {
        long day = CalendarTicks.CALENDAR_TICKS_IN_DAY;
        long januaryNorth = 0;
        // month index 0 is January: winter in the north, summer in the south
        assertThat(PlantSweeper.winter(januaryNorth, 8, true)).isTrue();
        assertThat(PlantSweeper.winter(januaryNorth, 8, false)).isFalse();
        // six months on it flips
        long july = 6L * 8 * day;
        assertThat(PlantSweeper.winter(july, 8, true)).isFalse();
        assertThat(PlantSweeper.winter(july, 8, false)).isTrue();
    }

    @Test
    void aCreepingPlantCopiesTheParentAndSetsEachSide() {
        BlockStateRef parent = BlockStateRef.of("tfp:plant/moss").with("age", "2").with("down", "true");
        // bit i is side i: down, up, north, south, west, east
        BlockStateRef child = PlantSweeper.creepState(parent, 0b100010);
        assertThat(child.properties()).containsEntry("age", "2").containsEntry("down", "false").containsEntry("up", "true")
            .containsEntry("north", "false").containsEntry("south", "false").containsEntry("west", "false").containsEntry("east", "true");
    }

    @Test
    void creepFacesAskTheOppositeFaceAndTheKindsTag() {
        java.util.Set<String> sturdy = new java.util.HashSet<>();
        java.util.Set<String> tags = new java.util.HashSet<>();
        PlantSweeper.GrowthLive live = new PlantSweeper.GrowthLive() {
            @Override
            public boolean sturdy(int x, int y, int z, dev.tfp.paper.plant.growth.Face face) {
                return sturdy.contains(x + "," + y + "," + z + "," + face);
            }

            @Override
            public boolean tagged(int x, int y, int z, String tag) {
                return tags.contains(x + "," + y + "," + z + "," + tag);
            }
        };
        // the block below (0, -1, 0) holds the plant by its UP face; the block to the east holds it by its WEST face
        sturdy.add("0,-1,0,UP");
        sturdy.add("1,0,0,WEST");
        sturdy.add("-1,0,0,UP"); // the wrong face of the west neighbour: no ground
        assertThat(PlantSweeper.creepFaces(live, 0, 0, 0, dev.tfp.core.regrowth.CreepKind.NORMAL, 0, 0)).isEqualTo(0b100001);
        // ice, snow: the not-plantable tag takes a side out
        tags.add("1,0,0," + PlantSweeper.CREEPING_NOT_ON);
        assertThat(PlantSweeper.creepFaces(live, 0, 0, 0, dev.tfp.core.regrowth.CreepKind.NORMAL, 0, 0)).isEqualTo(0b000001);
        // the stone kind needs the stone tag
        assertThat(PlantSweeper.creepFaces(live, 0, 0, 0, dev.tfp.core.regrowth.CreepKind.STONE, 0, 0)).isZero();
        tags.add("0,-1,0," + PlantSweeper.CREEPING_STONE_ON);
        assertThat(PlantSweeper.creepFaces(live, 0, 0, 0, dev.tfp.core.regrowth.CreepKind.STONE, 0, 0)).isEqualTo(0b000001);
        // a neighbour outside the chunk (z -1 is chunk -1) is not read, though its face is sturdy
        sturdy.add("0,0,-1,SOUTH");
        assertThat(PlantSweeper.creepFaces(live, 0, 0, 0, dev.tfp.core.regrowth.CreepKind.NORMAL, 0, 0) & 0b000100).isZero();
        assertThat(PlantSweeper.creepFaces(live, 0, 0, 0, dev.tfp.core.regrowth.CreepKind.NORMAL, 0, -1) & 0b000100).isNotZero();
    }
}
