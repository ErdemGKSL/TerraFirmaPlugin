/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.worldwater.hot;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.worldgen.biome.TfcBiomes;
import dev.tfp.core.worldgen.river.Flow;
import dev.tfp.core.worldwater.ChunkWater;
import dev.tfp.paper.testing.MockServerTest;
import java.util.Optional;
import org.bukkit.Material;
import org.bukkit.World;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** PAR-WORLD-27: a stratovolcano's crater lake of spring water (rock bed, no gravel) and its spring fissures are spring water. */
class SpringWaterStratovolcanoTest extends MockServerTest {
    private World world;

    @BeforeEach
    void setUp() {
        world = server.addSimpleWorld("crater");
    }

    private static SpringWater springs(int biome) {
        return new SpringWater((w, cx, cz) -> Optional.of(ChunkWater.uniform(cx, cz, biome, Flow.NONE, 100)));
    }

    /** A 5x5 crater lake 3 deep with its top at {@code top}, on stone, and a fissure of water 6 deep under its middle. */
    private void craterLake(int top) {
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                world.getBlockAt(x, top - 3, z).setType(Material.STONE);
                for (int y = top - 2; y <= top; y++) {
                    world.getBlockAt(x, y, z).setType(Material.WATER);
                }
                world.getBlockAt(x, top + 1, z).setType(Material.AIR);
            }
        }
        for (int y = top - 9; y <= top - 3; y++) {
            world.getBlockAt(0, y, 0).setType(Material.WATER);
        }
    }

    @Test
    void craterLakeInAStratovolcanoIsSpringWater() {
        craterLake(100);
        final SpringWater springs = springs(TfcBiomes.VOLCANIC_OCEANIC_MOUNTAINS);
        assertThat(springs.isSpringWater(world.getBlockAt(1, 100, 1))).isTrue();
        assertThat(springs.isSpringWater(world.getBlockAt(-2, 98, 2))).isTrue();
        assertThat(springs.isSpringWater(world.getBlockAt(0, 92, 0))).as("the fissure").isTrue();
        assertThat(springs.isSpringWater(world.getBlockAt(3, 100, 0))).as("air").isFalse();
    }

    @Test
    void elsewhereTheGravelPoolRuleStays() {
        craterLake(100);
        assertThat(springs(TfcBiomes.OLD_MOUNTAINS).isSpringWater(world.getBlockAt(1, 100, 1))).isFalse();
        assertThat(springs(TfcBiomes.PLAINS).isSpringWater(world.getBlockAt(1, 100, 1))).isFalse();
    }

    @Test
    void waterAtOrBelowSeaLevelIsNotSpringWater() {
        craterLake(63);
        assertThat(springs(TfcBiomes.VOLCANIC_OCEANIC_MOUNTAINS).isSpringWater(world.getBlockAt(0, 63, 0))).isFalse();
    }

    @Test
    void unknownChunkIsNotSpringWater() {
        craterLake(100);
        assertThat(new SpringWater((w, cx, cz) -> Optional.empty()).isSpringWater(world.getBlockAt(0, 100, 0))).isFalse();
    }
}
