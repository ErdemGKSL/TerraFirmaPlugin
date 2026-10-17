/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.worldgen;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.worldgen.chunk.PlaceholderRocks;
import dev.tfp.core.worldgen.volcano.Gem;
import dev.tfp.core.worldgen.volcano.VolcanoBlock;
import dev.tfp.core.worldgen.volcano.VolcanoKind;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

/** M2-GEN-14: the generator's volcano and hot spring placeholders, and the rule that writes them into one chunk. */
class VolcanoWiringTest {
    private static final int BASALT = PlaceholderRocks.names().indexOf("basalt");

    @Test
    void everyKindHasAPlaceholderOrAPaletteRock() {
        for (VolcanoKind kind : VolcanoKind.values()) {
            final Material m = VolcanoBlocks.placeholder(kind);
            assertThat(m == null).as("%s", kind).isEqualTo(kind.hasRock() && kind != VolcanoKind.GRAVEL && kind != VolcanoKind.MAGMA);
        }
        assertThat(VolcanoBlocks.placeholder(VolcanoKind.LAVA)).isEqualTo(Material.LAVA);
        assertThat(VolcanoBlocks.placeholder(VolcanoKind.SPRING_WATER)).isEqualTo(Material.WATER);
        assertThat(VolcanoBlocks.placeholder(VolcanoKind.MAGMA)).isEqualTo(Material.MAGMA_BLOCK);
        final VolcanoBlock hardened = new VolcanoBlock(0, 70, 0, VolcanoKind.HARDENED_ROCK, BASALT, null);
        assertThat(VolcanoBlocks.rockId(hardened)).isEqualTo("tfp:rock/raw/basalt");
        assertThat(TfcTerrain.ROCK_IDS.indexOf(VolcanoBlocks.rockId(hardened))).isEqualTo(BASALT);
        final VolcanoBlock gem = new VolcanoBlock(0, 70, 0, VolcanoKind.GEM_ORE, BASALT, Gem.TOPAZ);
        assertThat(VolcanoBlocks.gemOreId(gem)).isEqualTo("tfp:ore/topaz/basalt");
        assertThat(VolcanoBlocks.gemOreId(hardened)).isNull();
    }

    @Test
    void writesOnlyItsChunkInsideTheHeightsAndKeepsTrees() {
        final Map<String, Material> world = new HashMap<>();
        world.put("2,66,0", Material.OAK_LOG);
        final Map<String, VolcanoKind> placed = new HashMap<>();
        final VolcanoBlocks.Target target = new VolcanoBlocks.Target() {
            @Override
            public Material type(int localX, int y, int localZ) {
                return world.getOrDefault(localX + "," + y + "," + localZ, Material.STONE);
            }

            @Override
            public void place(int localX, int y, int localZ, VolcanoBlock block) {
                assertThat(localX).isBetween(0, 15);
                assertThat(localZ).isBetween(0, 15);
                placed.put(localX + "," + y + "," + localZ, block.kind());
            }
        };
        // chunk (1, 0)
        final List<VolcanoBlock> blocks = List.of(
            new VolcanoBlock(17, 60, 0, VolcanoKind.LAVA, -1, null),
            new VolcanoBlock(18, 66, 0, VolcanoKind.SPRING_WATER, -1, null), // a log is there
            new VolcanoBlock(15, 60, 0, VolcanoKind.LAVA, -1, null), // chunk (0, 0)
            new VolcanoBlock(20, -64, 3, VolcanoKind.RAW_ROCK, BASALT, null), // the bedrock floor
            new VolcanoBlock(20, 320, 3, VolcanoKind.AIR, -1, null), // above the world
            new VolcanoBlock(31, 61, 15, VolcanoKind.MAGMA, BASALT, null));
        assertThat(VolcanoBlocks.write(blocks, 1, 0, -64, 320, target)).isEqualTo(2);
        assertThat(placed).containsOnlyKeys("1,60,0", "15,61,15");
        assertThat(placed.get("15,61,15")).isEqualTo(VolcanoKind.MAGMA);
    }
}
