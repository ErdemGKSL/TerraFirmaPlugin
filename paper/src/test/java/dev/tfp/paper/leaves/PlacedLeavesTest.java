/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.leaves;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.FakeCustomBlocks;
import dev.tfp.paper.testing.MockServerTest;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/** PAR-GATHER-15: a leaf block a player places is persistent; one that was never placed by a player is not touched. */
class PlacedLeavesTest extends MockServerTest {
    private static final String LEAVES = "tfp:wood/leaves/oak";

    private final FakeCustomBlocks blocks = new FakeCustomBlocks().known(LEAVES, "tfp:wood/log/oak");
    private World world;
    private PlayerMock player;
    private PlacedLeaves listener;

    @BeforeEach
    void setUp() {
        world = server.addSimpleWorld("placed");
        world.getChunkAt(0, 0);
        player = server.addPlayer();
        listener = new PlacedLeaves(blocks);
    }

    private Block put(int x, String id, String persistent) {
        Block b = world.getBlockAt(x, 64, 0);
        b.setType(Material.OAK_LEAVES);
        BlockStateRef state = BlockStateRef.of(id);
        blocks.place(b, persistent == null ? state : state.with("persistent", persistent), false);
        return b;
    }

    private void placeEvent(Block b) {
        listener.onPlace(new BlockPlaceEvent(b, b.getState(), world.getBlockAt(b.getX(), 63, 0), new ItemStack(Material.OAK_LEAVES),
            player, true, org.bukkit.inventory.EquipmentSlot.HAND));
    }

    @Test
    void aPlayerPlacedLeafBecomesPersistent() {
        Block b = put(0, LEAVES, "false");
        placeEvent(b);
        assertThat(blocks.stateAt(b).orElseThrow().properties()).containsEntry("persistent", "true");
    }

    @Test
    void aLeafNobodyPlacedKeepsItsDefault() {
        Block b = put(1, LEAVES, "false");
        assertThat(blocks.stateAt(b).orElseThrow().properties()).containsEntry("persistent", "false");
    }

    @Test
    void otherBlocksAndLeavesWithoutThePropertyAreLeftAlone() {
        Block log = put(2, "tfp:wood/log/oak", null);
        Block bare = put(3, LEAVES, null);
        placeEvent(log);
        placeEvent(bare);
        assertThat(blocks.stateAt(log).orElseThrow()).isEqualTo(BlockStateRef.of("tfp:wood/log/oak"));
        assertThat(blocks.stateAt(bare).orElseThrow().properties()).doesNotContainKey("persistent");
    }

    @Test
    void aPlacedLeafIsNotOrphanDecayed() {
        Block b = put(4, LEAVES, "false");
        placeEvent(b);
        assertThat(PlacedLeaves.persistent(blocks.stateAt(b).orElseThrow())).isEmpty();
    }
}
