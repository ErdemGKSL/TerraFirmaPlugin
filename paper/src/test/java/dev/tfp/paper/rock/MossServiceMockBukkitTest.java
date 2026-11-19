/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.rock;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.world.BlockPos;
import dev.tfp.paper.blockdata.InMemoryChunkDataStore;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.testing.MockServerTest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** PAR-GATHER-06: the lazy moss catch-up converts cobble and brick blocks that touch water, and only those. */
class MossServiceMockBukkitTest extends MockServerTest {
    /** A map-backed custom block layer: positions to states. */
    static final class FakeBlocks implements CustomBlocks {
        final Map<BlockPos, BlockStateRef> states = new HashMap<>();

        private static BlockPos key(Block b) {
            return new BlockPos(b.getX(), b.getY(), b.getZ());
        }

        @Override
        public boolean exists(String id) {
            return true;
        }

        @Override
        public Optional<BlockStateRef> stateAt(Block block) {
            return Optional.ofNullable(states.get(key(block)));
        }

        @Override
        public boolean place(Block block, BlockStateRef state, boolean applyPhysics) {
            states.put(key(block), state);
            return true;
        }

        @Override
        public boolean remove(Block block) {
            return states.remove(key(block)) != null;
        }

        @Override
        public boolean isCorrectTool(Block block, ItemStack tool) {
            return true;
        }

        @Override
        public void onChunkLoad(Consumer<Chunk> hook) {
        }

        @Override
        public void onChunkUnload(Consumer<Chunk> hook) {
        }
    }

    private final FakeBlocks blocks = new FakeBlocks();
    private final AtomicLong now = new AtomicLong();
    private final List<BlockPos> tracked = new ArrayList<>();
    private World world;

    private MossService service(boolean enabled) {
        return new MossService(blocks, (b, kind) -> tracked.add(FakeBlocks.key(b)), new InMemoryChunkDataStore(),
                now::get, enabled, 20);
    }

    @BeforeEach
    void setUp() {
        world = server.addSimpleWorld("moss");
    }

    private void put(int x, int y, int z, String id, boolean water) {
        blocks.states.put(new BlockPos(x, y, z), BlockStateRef.of(id));
        if (water) {
            world.getBlockAt(x, y + 1, z).setType(Material.WATER);
        }
    }

    private String idAt(int x, int y, int z) {
        BlockStateRef r = blocks.states.get(new BlockPos(x, y, z));
        return r == null ? null : r.id();
    }

    @Test
    void longCatchUpConvertsWetCobbleAndLeavesDryAndOtherBlocks() {
        BlockPos src = new BlockPos(8, 64, 8);
        blocks.states.put(src, BlockStateRef.of("tfp:rock/mossy_cobble/granite"));
        // every other cell of the 7x7x7 neighbourhood: wet cobble on y=64, dry stairs on y=70, raw rock on y=62
        for (int x = 5; x <= 11; x++) {
            for (int z = 5; z <= 11; z++) {
                if (x == 8 && z == 8) {
                    continue;
                }
                put(x, 64, z, "tfp:rock/cobble/granite", true);
                put(x, 70, z, "tfp:rock/bricks/granite_stairs", false);
                put(x, 62, z, "tfp:rock/raw/granite", true);
            }
        }
        MossService moss = service(true);
        assertThat(moss.catchUp(world, src)).isTrue(); // first sight of the chunk: nothing to replay
        assertThat(idAt(5, 64, 5)).isEqualTo("tfp:rock/cobble/granite");

        now.set(200_000_000L); // far past the 64-attempt cap
        assertThat(moss.catchUp(world, src)).isTrue();
        assertThat(blocks.states.entrySet().stream()
                .filter(e -> e.getKey().y() == 64 && e.getValue().id().startsWith("tfp:rock/mossy_cobble/")).count())
                .as("wet cobble converted").isGreaterThan(5);
        blocks.states.forEach((p, s) -> {
            if (p.y() == 70) {
                assertThat(s.id()).as("dry blocks stay").doesNotContain("mossy");
            }
            if (p.y() == 62) {
                assertThat(s.id()).as("raw rock is not mossable").isEqualTo("tfp:rock/raw/granite");
            }
        });
        assertThat(tracked).isNotEmpty();
    }

    @Test
    void conversionKeepsTheStateProperties() {
        BlockPos src = new BlockPos(8, 64, 8);
        blocks.states.put(src, BlockStateRef.of("tfp:rock/mossy_bricks/basalt"));
        for (int x = 5; x <= 11; x++) {
            for (int z = 5; z <= 11; z++) {
                if (x != 8 || z != 8) {
                    blocks.states.put(new BlockPos(x, 64, z), BlockStateRef.of("tfp:rock/bricks/basalt_stairs",
                            Map.of("facing", "north", "half", "top")));
                    world.getBlockAt(x, 65, z).setType(Material.WATER);
                }
            }
        }
        MossService moss = service(true);
        moss.catchUp(world, src);
        now.set(200_000_000L);
        moss.catchUp(world, src);
        BlockStateRef converted = blocks.states.values().stream()
                .filter(s -> s.id().equals("tfp:rock/mossy_bricks/basalt_stairs")).findFirst().orElseThrow();
        assertThat(converted.properties()).containsEntry("facing", "north").containsEntry("half", "top");
    }

    @Test
    void disabledSpreadingAndBrokenSourcesAreHandled() {
        BlockPos src = new BlockPos(8, 64, 8);
        blocks.states.put(src, BlockStateRef.of("tfp:rock/mossy_cobble/granite"));
        put(9, 64, 8, "tfp:rock/cobble/granite", true);
        MossService off = service(false);
        off.catchUp(world, src);
        now.set(200_000_000L);
        assertThat(off.catchUp(world, src)).isTrue();
        assertThat(idAt(9, 64, 8)).isEqualTo("tfp:rock/cobble/granite");

        blocks.states.remove(src);
        assertThat(service(true).catchUp(world, src)).as("no longer mossy: index entry dropped").isFalse();
    }
}
