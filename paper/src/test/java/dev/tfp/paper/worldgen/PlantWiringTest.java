/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.worldgen;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.worldgen.plant.PlantBlock;
import dev.tfp.core.worldgen.plant.PlantState;
import dev.tfp.core.worldgen.plant.PlantType;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

/** M2-GEN-13: the generator's plant and coral placeholders, and the rule that writes them into one chunk. */
class PlantWiringTest {
    private static PlantState plant(String name, PlantType type, PlantState.Part part, PlantState.Fluid fluid) {
        return new PlantState("tfp:plant/" + name, PlantState.Kind.PLANT, type, part, type.hasAge() ? 1 : -1, fluid, -1);
    }

    private static PlantState other(String id, PlantState.Kind kind, PlantState.Fluid fluid, int extra) {
        return new PlantState(id, kind, null, PlantState.Part.SINGLE, -1, fluid, extra);
    }

    @Test
    void everySupportedTypeAndKindHasAPlaceholder() {
        for (PlantType type : PlantType.values()) {
            if (!type.survivable()) {
                continue;
            }
            final PlantState.Fluid fluid = type.fluidProperty() == null ? PlantState.Fluid.EMPTY : type.fluidProperty();
            final PlantState.Part part = type.isTall() ? PlantState.Part.LOWER : PlantState.Part.SINGLE;
            final Material m = PlantBlocks.placeholder(plant("x", type, part, fluid));
            assertThat(m).as("%s", type).isNotNull();
            assertThat(m.isBlock()).isTrue();
        }
        assertThat(PlantBlocks.placeholder(plant("allium", PlantType.STANDARD, PlantState.Part.SINGLE, PlantState.Fluid.EMPTY)))
            .isEqualTo(Material.ALLIUM);
        assertThat(PlantBlocks.placeholder(plant("bluegrass", PlantType.SHORT_GRASS, PlantState.Part.SINGLE, PlantState.Fluid.EMPTY)))
            .isEqualTo(Material.SHORT_GRASS);
        assertThat(PlantBlocks.placeholder(other("tfp:wild_crop/wheat", PlantState.Kind.WILD_CROP, PlantState.Fluid.EMPTY, -1)))
            .isEqualTo(Material.FERN);
        assertThat(PlantBlocks.placeholder(other("tfp:wild_crop/maize", PlantState.Kind.WILD_CROP, PlantState.Fluid.EMPTY, -1)))
            .isEqualTo(Material.LARGE_FERN);
        assertThat(PlantBlocks.placeholder(other("tfp:pumpkin", PlantState.Kind.WILD_CROP_FRUIT, PlantState.Fluid.EMPTY, -1)))
            .isEqualTo(Material.PUMPKIN);
        assertThat(PlantBlocks.placeholder(other("minecraft:fire_coral_block", PlantState.Kind.CORAL_BLOCK, PlantState.Fluid.EMPTY, -1)))
            .isEqualTo(Material.FIRE_CORAL_BLOCK);
        assertThat(PlantBlocks.placeholder(other("tfp:coral/horn_coral_wall_fan", PlantState.Kind.CORAL_WALL_FAN, PlantState.Fluid.SALT_WATER, 2)))
            .isEqualTo(Material.HORN_CORAL_WALL_FAN);
        assertThat(PlantBlocks.placeholder(other("tfp:coral/tube_coral", PlantState.Kind.CORAL, PlantState.Fluid.SALT_WATER, -1)))
            .isEqualTo(Material.TUBE_CORAL);
        assertThat(PlantBlocks.placeholder(other("tfp:sea_pickle", PlantState.Kind.SEA_PICKLE, PlantState.Fluid.SALT_WATER, 3)))
            .isEqualTo(Material.SEA_PICKLE);
    }

    @Test
    void writesOnlyFreePositionsOfItsChunkAndBothHalvesOrNone() {
        final Map<String, Material> world = new HashMap<>();
        // chunk (1, 0): water at y 60 and 61 of column (17, 0), air elsewhere; a log at (18, 66, 0)
        world.put("1,60,0", Material.WATER);
        world.put("1,61,0", Material.WATER);
        world.put("2,66,0", Material.OAK_LOG);
        world.put("4,60,0", Material.WATER);
        final Map<String, Boolean> halves = new HashMap<>();
        final PlantBlocks.Target target = new PlantBlocks.Target() {
            @Override
            public Material type(int localX, int y, int localZ) {
                return world.getOrDefault(localX + "," + y + "," + localZ, Material.AIR);
            }

            @Override
            public void place(int localX, int y, int localZ, PlantBlock block, Material material, boolean upperHalf) {
                assertThat(localX).isBetween(0, 15);
                assertThat(localZ).isBetween(0, 15);
                world.put(localX + "," + y + "," + localZ, material);
                halves.put(localX + "," + y + "," + localZ, upperHalf);
            }
        };
        final PlantState.Fluid empty = PlantState.Fluid.EMPTY;
        final PlantState.Fluid water = PlantState.Fluid.WATER;
        final List<PlantBlock> blocks = List.of(
            // a flower on air: written
            new PlantBlock(16, 65, 0, plant("poppy", PlantType.STANDARD, PlantState.Part.SINGLE, empty)),
            // the same position again (a neighbouring chunk's decision): the first claim stays
            new PlantBlock(16, 65, 0, plant("dandelion", PlantType.STANDARD, PlantState.Part.SINGLE, empty)),
            // a submerged tall water plant, both halves in water: tall seagrass
            new PlantBlock(17, 60, 0, plant("badderlocks", PlantType.TALL_WATER, PlantState.Part.LOWER, water)),
            new PlantBlock(17, 61, 0, plant("badderlocks", PlantType.TALL_WATER, PlantState.Part.UPPER, water)),
            // a tall grass whose upper half is taken by a log: neither half
            new PlantBlock(18, 65, 0, plant("foxglove", PlantType.TALL_GRASS, PlantState.Part.LOWER, empty)),
            new PlantBlock(18, 66, 0, plant("foxglove", PlantType.TALL_GRASS, PlantState.Part.UPPER, empty)),
            // an emergent water plant (upper half in air): seagrass alone
            new PlantBlock(20, 60, 0, plant("cattail", PlantType.TALL_WATER_FRESH, PlantState.Part.LOWER, water)),
            new PlantBlock(20, 61, 0, plant("cattail", PlantType.TALL_WATER_FRESH, PlantState.Part.UPPER, empty)),
            // a water plant where the chunk has air now: skipped
            new PlantBlock(19, 60, 0, plant("eel_grass", PlantType.GRASS_WATER_FRESH, PlantState.Part.SINGLE, water)),
            // another chunk and above the world: skipped
            new PlantBlock(15, 65, 0, plant("poppy", PlantType.STANDARD, PlantState.Part.SINGLE, empty)),
            new PlantBlock(16, 400, 0, plant("poppy", PlantType.STANDARD, PlantState.Part.SINGLE, empty)));
        world.put("4,60,0", Material.WATER);
        final int written = PlantBlocks.write(blocks, 1, 0, -64, 320, target);
        assertThat(written).isEqualTo(4);
        assertThat(world.get("0,65,0")).isEqualTo(Material.POPPY);
        assertThat(world.get("1,60,0")).isEqualTo(Material.TALL_SEAGRASS);
        assertThat(world.get("1,61,0")).isEqualTo(Material.TALL_SEAGRASS);
        assertThat(halves.get("1,60,0")).isFalse();
        assertThat(halves.get("1,61,0")).isTrue();
        assertThat(world.get("2,65,0")).isNull();
        assertThat(world.get("2,66,0")).isEqualTo(Material.OAK_LOG);
        assertThat(world.get("4,60,0")).isEqualTo(Material.SEAGRASS);
        assertThat(world.get("4,61,0")).isNull();
        assertThat(world.get("3,60,0")).isNull();
    }

    @Test
    void coralBlocksGoIntoWaterOrOntoCorals() {
        final PlantState block = other("minecraft:tube_coral_block", PlantState.Kind.CORAL_BLOCK, PlantState.Fluid.EMPTY, -1);
        final Map<String, Material> world = new HashMap<>(Map.of("0,50,0", Material.WATER, "0,51,0", Material.BRAIN_CORAL,
            "0,52,0", Material.STONE));
        final PlantBlocks.Target target = new PlantBlocks.Target() {
            @Override
            public Material type(int localX, int y, int localZ) {
                return world.getOrDefault(localX + "," + y + "," + localZ, Material.AIR);
            }

            @Override
            public void place(int localX, int y, int localZ, PlantBlock b, Material material, boolean upperHalf) {
                world.put(localX + "," + y + "," + localZ, material);
            }
        };
        final int written = PlantBlocks.write(List.of(new PlantBlock(0, 50, 0, block), new PlantBlock(0, 51, 0, block),
            new PlantBlock(0, 52, 0, block)), 0, 0, -64, 320, target);
        assertThat(written).isEqualTo(2);
        assertThat(world.get("0,50,0")).isEqualTo(Material.TUBE_CORAL_BLOCK);
        assertThat(world.get("0,51,0")).isEqualTo(Material.TUBE_CORAL_BLOCK);
        assertThat(world.get("0,52,0")).isEqualTo(Material.STONE);
    }
}
