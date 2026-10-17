/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.weather;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.world.BlockPos;
import dev.tfp.paper.testing.MockServerTest;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.junit.jupiter.api.Test;

/** M2-WTH-03: the pile memory of {@link WorldBlocks} on a MockBukkit chunk (snow piles remember the block they cover). */
class WorldBlocksMockBukkitTest extends MockServerTest {
    @Test
    void aPileRemembersItsBlocksAndGivesThemBack() {
        World world = server.addSimpleWorld("tfp_snow");
        world.getChunkAt(0, 0);
        WorldBlocks blocks = new WorldBlocks(world);
        BlockPos pos = new BlockPos(3, 70, 5);
        BlockPos above = pos.offset(0, 1, 0);
        BlockData fern = Material.FERN.createBlockData();
        BlockData grass = Material.SHORT_GRASS.createBlockData();
        world.getBlockAt(pos.x(), pos.y(), pos.z()).setBlockData(fern, false);
        world.getBlockAt(above.x(), above.y(), above.z()).setBlockData(grass, false);
        BlockData snow = Material.SNOW.createBlockData();

        // A pile that absorbs the block above: the block above goes, the pile remembers both
        blocks.write(pos, new SnowBlock(snow, new SnowBlock.Pile(fern, grass), null));
        assertThat(blocks.read(pos)).isEqualTo(new SnowBlock(snow, new SnowBlock.Pile(fern, grass), null));
        assertThat(world.getBlockAt(above.x(), above.y(), above.z()).getType()).isEqualTo(Material.AIR);

        // The melt gives them back
        blocks.write(pos, new SnowBlock(fern, null, grass));
        assertThat(blocks.read(pos)).isEqualTo(SnowBlock.of(fern));
        assertThat(world.getBlockAt(above.x(), above.y(), above.z()).getBlockData()).isEqualTo(grass);

        // Plain snow written over a position forgets the old pile
        blocks.write(pos, new SnowBlock(snow, new SnowBlock.Pile(fern, null), null));
        blocks.write(pos, SnowBlock.of(snow));
        assertThat(blocks.read(pos).pile()).isNull();
        assertThat(world.getChunkAt(0, 0).getPersistentDataContainer().getKeys()).isEmpty();
    }

    @Test
    void anUnloadedChunkIsUnknown() {
        World world = server.addSimpleWorld("tfp_snow2");
        WorldBlocks blocks = new WorldBlocks(world);
        world.getChunkAt(40, 40).unload();
        assertThat(blocks.read(new BlockPos(40 * 16, 70, 40 * 16))).isNull();
    }
}
