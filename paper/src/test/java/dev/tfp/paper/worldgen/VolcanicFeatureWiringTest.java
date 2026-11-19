/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.worldgen;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.worldgen.chunk.PlaceholderRocks;
import dev.tfp.core.worldgen.volcano.Gem;
import dev.tfp.core.worldgen.volcano.VolcanicBlock;
import dev.tfp.core.worldgen.volcano.VolcanicKind;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

/** PAR-WORLD-27: every volcanic feature kind's block in the Paper terrain, and the rule that writes them into one chunk. */
class VolcanicFeatureWiringTest {
    private static final int BASALT = PlaceholderRocks.names().indexOf("basalt");

    @Test
    void everyKindHasAPlaceholderOrAPaletteRock() {
        for (VolcanicKind kind : VolcanicKind.values()) {
            final Material m = VolcanicFeatureBlocks.placeholder(kind);
            assertThat(m == null).as("%s", kind).isEqualTo(kind.hasRock() && kind != VolcanicKind.GRAVEL && kind != VolcanicKind.MAGMA);
            assertThat(VolcanicFeatureBlocks.groundcoverId(kind) != null).as("%s", kind).isEqualTo(kind.isGroundcover());
            if (kind.isFluid()) {
                assertThat(m).isIn(Material.WATER, Material.LAVA);
            }
        }
        assertThat(VolcanicFeatureBlocks.placeholder(VolcanicKind.SALT_WATER)).isEqualTo(Material.WATER);
        assertThat(VolcanicFeatureBlocks.groundcoverId(VolcanicKind.PUMICE)).isEqualTo("tfp:groundcover/pumice");
        assertThat(VolcanicFeatureBlocks.groundcoverId(VolcanicKind.OBSIDIAN_SHARD)).isEqualTo("tfp:groundcover/obsidian_shard");
        final VolcanicBlock hardened = new VolcanicBlock(0, 70, 0, VolcanicKind.HARDENED_ROCK, BASALT, null);
        assertThat(TfcTerrain.ROCK_IDS.indexOf(VolcanicFeatureBlocks.rockId(hardened))).isEqualTo(BASALT);
        assertThat(VolcanicFeatureBlocks.oreId(hardened)).isNull();
        assertThat(VolcanicFeatureBlocks.oreId(new VolcanicBlock(0, 70, 0, VolcanicKind.SULFUR_ORE, BASALT, null)))
            .isEqualTo("tfp:ore/sulfur/basalt");
        assertThat(VolcanicFeatureBlocks.oreId(new VolcanicBlock(0, 70, 0, VolcanicKind.GEM_ORE, BASALT, Gem.TOPAZ)))
            .isEqualTo("tfp:ore/topaz/basalt");
    }

    @Test
    void writesOnlyItsChunkInsideTheHeightsAndKeepsTrees() {
        final Map<String, Material> world = new HashMap<>();
        world.put("2,66,0", Material.OAK_LOG);
        final Map<String, VolcanicKind> placed = new HashMap<>();
        final VolcanicFeatureBlocks.Target target = new VolcanicFeatureBlocks.Target() {
            @Override
            public Material type(int localX, int y, int localZ) {
                return world.getOrDefault(localX + "," + y + "," + localZ, Material.STONE);
            }

            @Override
            public void place(int localX, int y, int localZ, VolcanicBlock block) {
                assertThat(localX).isBetween(0, 15);
                assertThat(localZ).isBetween(0, 15);
                placed.put(localX + "," + y + "," + localZ, block.kind());
            }
        };
        // chunk (1, 0)
        final List<VolcanicBlock> blocks = List.of(
            new VolcanicBlock(17, 60, 0, VolcanicKind.SPRING_WATER, -1, null),
            new VolcanicBlock(18, 66, 0, VolcanicKind.PUMICE, -1, null), // a log is there
            new VolcanicBlock(15, 60, 0, VolcanicKind.LAVA, -1, null), // chunk (0, 0)
            new VolcanicBlock(20, -64, 3, VolcanicKind.RAW_ROCK, BASALT, null), // the bedrock floor
            new VolcanicBlock(20, 320, 3, VolcanicKind.AIR, -1, null), // above the world
            new VolcanicBlock(31, 61, 15, VolcanicKind.SULFUR_ORE, BASALT, null),
            new VolcanicBlock(31, 62, 15, VolcanicKind.OBSIDIAN_SHARD, -1, null));
        assertThat(VolcanicFeatureBlocks.write(blocks, 1, 0, -64, 320, target)).isEqualTo(3);
        assertThat(placed).containsOnlyKeys("1,60,0", "15,61,15", "15,62,15");
        assertThat(placed.get("15,61,15")).isEqualTo(VolcanicKind.SULFUR_ORE);
    }
}
