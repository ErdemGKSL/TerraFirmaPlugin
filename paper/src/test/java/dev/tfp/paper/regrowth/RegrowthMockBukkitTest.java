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
}
