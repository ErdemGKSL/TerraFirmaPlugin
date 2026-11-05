/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.blocksound;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.FakeCustomBlocks;
import dev.tfp.paper.integration.craftengine.CeBlockSoundTable;
import dev.tfp.paper.testing.MockServerTest;
import java.util.concurrent.atomic.AtomicLong;
import org.bukkit.block.Block;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

/** PAR-INFRA-33: the region-thread side records our blocks when mined or broken, and each player's world. */
class BlockSoundTrackerMockBukkitTest extends MockServerTest {
    final FakeCustomBlocks custom = new FakeCustomBlocks().known("tfp:bellows", "tfp:unknown_sounds");
    final AtomicLong clock = new AtomicLong(1_000L);
    BlockSoundRewrite rewrite;
    WorldMock world;
    PlayerMock player;

    @BeforeEach
    void setUp() {
        world = server.addSimpleWorld("sounds");
        rewrite = new BlockSoundRewrite(new TrackedBlocks());
        rewrite.table(CeBlockSoundTable.parse("""
            blocks:
              tfp:bellows:
                settings:
                  sounds:
                    break: minecraft:block.wood.break
                    hit: minecraft:block.wood.hit
            """));
        server.getPluginManager().registerEvents(new BlockSoundTracker(rewrite, custom, clock::get), plugin);
        player = server.addPlayer();
        player.teleport(world.getSpawnLocation());
    }

    @Test
    void joinAndWorldChangeRecordThePlayersWorld() {
        assertThat(rewrite.tracked().worldOf(player.getUniqueId())).isEqualTo(world.getUID());
        player.disconnect();
        assertThat(rewrite.tracked().worldOf(player.getUniqueId())).isNull();
    }

    @Test
    void miningAndBreakingTrackOnlyBlocksInTheTable() {
        Block bellows = world.getBlockAt(1, 64, 1);
        custom.place(bellows, BlockStateRef.of("tfp:bellows"), false);
        Block other = world.getBlockAt(2, 64, 1);
        custom.place(other, BlockStateRef.of("tfp:unknown_sounds"), false);
        Block vanilla = world.getBlockAt(3, 64, 1);

        for (Block b : new Block[] {bellows, other, vanilla}) {
            server.getPluginManager().callEvent(new BlockDamageEvent(player, b, org.bukkit.block.BlockFace.UP, new ItemStack(org.bukkit.Material.AIR), false));
        }
        assertThat(rewrite.tracked().size()).isEqualTo(1);
        assertThat(rewrite.tracked().blockAt(new TrackedBlocks.Pos(world.getUID(), 1, 64, 1), clock.get())).isEqualTo("tfp:bellows");
        BlockSoundRewrite.Decision hit = rewrite.decide(player.getUniqueId(), "minecraft:block.wood.hit", 1, 64, 1, 0.5f, 0.5f, clock.get());
        assertThat(hit).isEqualTo(new BlockSoundRewrite.Decision(BlockSoundRewrite.Kind.SET, 0.25f, 0.5f));

        server.getPluginManager().callEvent(new BlockBreakEvent(bellows, player));
        long afterBreakWindow = clock.get() + TrackedBlocks.BROKEN_NANOS + 1;
        assertThat(rewrite.tracked().blockAt(new TrackedBlocks.Pos(world.getUID(), 1, 64, 1), afterBreakWindow))
            .as("a broken block is forgotten soon after").isNull();
    }
}
