/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.leaves;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.FakeCustomBlocks;
import dev.tfp.paper.testing.MockServerTest;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/** PAR-GATHER-04 on MockBukkit with a fake CraftEngine and a queue standing in for the region scheduler. */
class LeavesMockBukkitTest extends MockServerTest {
    private static final String LOG = "tfp:wood/log/oak";
    private static final String LEAVES = "tfp:wood/leaves/oak";

    private final FakeCustomBlocks blocks = new FakeCustomBlocks().known(LOG, LEAVES, "tfp:wood/fallen_leaves/oak",
            "tfp:wood/twig/oak");
    private final ArrayDeque<Runnable> queue = new ArrayDeque<>();
    private final List<Long> delays = new ArrayList<>();
    private final ArrayDeque<Float> floats = new ArrayDeque<>();
    private World world;
    private PlayerMock player;
    private LeafDecay decay;

    @BeforeEach
    void setUp() {
        world = server.addSimpleWorld("leaves");
        for (int cx = -1; cx <= 1; cx++) {
            for (int cz = -1; cz <= 1; cz++) {
                world.getChunkAt(cx, cz);
            }
        }
        player = server.addPlayer();
        decay = new LeafDecay(blocks, new LeafDecay.Env() {
            @Override
            public boolean isLog(BlockStateRef state) {
                return state.id().equals(LOG);
            }

            @Override
            public void regionDelayed(World w, int chunkX, int chunkZ, long delayTicks, Runnable task) {
                delays.add(delayTicks);
                queue.add(task);
            }

            @Override
            public float nextFloat() {
                return floats.isEmpty() ? 0.5f : floats.removeFirst();
            }
        });
    }

    private Block log(int x, int y, int z) {
        Block b = world.getBlockAt(x, y, z);
        b.setType(Material.OAK_LOG);
        blocks.place(b, BlockStateRef.of(LOG), false);
        return b;
    }

    private Block leaf(int x, int y, int z, String persistent) {
        Block b = world.getBlockAt(x, y, z);
        b.setType(Material.OAK_LEAVES);
        BlockStateRef state = BlockStateRef.of(LEAVES);
        blocks.place(b, persistent == null ? state : state.with("persistent", persistent), false);
        return b;
    }

    private void breakLog(Block log) {
        BlockBreakEvent e = new BlockBreakEvent(log, player);
        decay.onBreak(e);
        blocks.remove(log);
        log.setType(Material.AIR);
    }

    private void drain() {
        while (!queue.isEmpty()) {
            queue.poll().run();
        }
    }

    @Test
    void orphanedNonPersistentLeavesDecayAfterARandomDelay() {
        Block trunk = log(0, 64, 0);
        for (int x = 1; x <= 3; x++) {
            leaf(x, 64, 0, "false");
        }
        breakLog(trunk);
        assertThat(queue).hasSize(3);
        assertThat(delays).allMatch(d -> d >= 1 && d <= 24_000);
        drain();
        for (int x = 1; x <= 3; x++) {
            assertThat(blocks.stateAt(world.getBlockAt(x, 64, 0))).as("leaf " + x).isEmpty();
        }
    }

    @Test
    void persistentLeavesAndLeavesWithoutThePropertyStay() {
        Block trunk = log(0, 64, 0);
        leaf(1, 64, 0, "true");
        leaf(0, 65, 0, null);
        leaf(0, 63, 0, "false");
        breakLog(trunk);
        assertThat(queue).hasSize(1);
        drain();
        assertThat(blocks.stateAt(world.getBlockAt(1, 64, 0))).isPresent();
        assertThat(blocks.stateAt(world.getBlockAt(0, 65, 0))).isPresent();
        assertThat(blocks.stateAt(world.getBlockAt(0, 63, 0))).isEmpty();
    }

    @Test
    void leavesWithAnotherLogInReachDoNotDecay() {
        Block trunk = log(0, 64, 0);
        log(2, 64, 1);
        leaf(1, 64, 0, "false");
        leaf(2, 64, 0, "false");
        breakLog(trunk);
        assertThat(queue).isEmpty();
    }

    @Test
    void aLeafThatGainedALogBeforeItsTurnIsKept() {
        Block trunk = log(0, 64, 0);
        leaf(1, 64, 0, "false");
        breakLog(trunk);
        assertThat(queue).hasSize(1);
        log(1, 65, 0);
        drain();
        assertThat(blocks.stateAt(world.getBlockAt(1, 64, 0))).isPresent();
    }

    @Test
    void aDecayedLeafSometimesLeavesFallenLeavesOnTheGround() {
        Block ground = world.getBlockAt(0, 60, 0);
        ground.setType(Material.STONE);
        decay.fallenLeaves(world, new dev.tfp.core.leaves.LeafRules.Pos(0, 64, 0), LEAVES);
        assertThat(blocks.stateAt(world.getBlockAt(0, 61, 0))).hasValueSatisfying(s -> assertThat(s.id()).isIn(
                "tfp:wood/fallen_leaves/oak", "tfp:wood/twig/oak"));
    }

    @Test
    void delayHasTfcsMean() {
        floats.add(0.5f);
        assertThat(decay.delay()).isEqualTo((long) Math.ceil(-Math.log(0.5) * 4096.0 / 3.0));
        floats.add(0f);
        assertThat(decay.delay()).isEqualTo((long) Math.ceil(-Math.log(1.0E-6) * 4096.0 / 3.0));
    }

    // ---- slowdown

    private final List<Double> applied = new ArrayList<>();

    private SlowdownListener slow() {
        SlowBlocks slow = new SlowBlocks();
        slow.register(SlowBlocks.leaves(0.4f));
        return new SlowdownListener(blocks, slow, (p, m) -> applied.add(m));
    }

    @Test
    void standingInLeavesSlowsWalkingAndFlyingIsNot() {
        SlowdownListener listener = slow();
        Location at = new Location(world, 5.5, 64, 5.5);
        Block leaves = leaf(5, 64, 5, "false");
        assertThat(leaves.getType()).isEqualTo(Material.OAK_LEAVES);
        player.setGameMode(GameMode.SURVIVAL);
        listener.update(player, at, true);
        assertThat(applied.getLast()).isCloseTo(0.4, within(1e-6));
        listener.update(player, at, false);
        assertThat(applied.getLast()).isCloseTo(0.85, within(1e-6));
        listener.update(player, new Location(world, 9.5, 64, 9.5), true);
        assertThat(applied.getLast()).isEqualTo(1.0);
        player.setGameMode(GameMode.SPECTATOR);
        listener.update(player, at, true);
        assertThat(applied.getLast()).isEqualTo(1.0);
    }

    @Test
    void fallenLeavesScaleWithTheirLayersAndOneLayerDoesNotSlow() {
        SlowBlocks slow = new SlowBlocks();
        slow.register(SlowBlocks.leaves(0.4f));
        assertThat(slow.factor(BlockStateRef.of("tfp:wood/fallen_leaves/oak").with("layers", "1"))).isEqualTo(1f);
        assertThat(slow.factor(BlockStateRef.of("tfp:wood/fallen_leaves/oak").with("layers", "4"))).isCloseTo(0.2f, within(1e-6f));
        assertThat(slow.factor(BlockStateRef.of("tfp:rock/raw/granite"))).isEqualTo(1f);
        slow.register(state -> state.id().startsWith("tfp:plant/") ? java.util.OptionalDouble.of(0.5) : java.util.OptionalDouble.empty());
        assertThat(slow.factor(BlockStateRef.of("tfp:plant/fern"))).isEqualTo(0.5f);
    }

    @Test
    void movingIntoLeavesThroughTheEventApplies() {
        SlowdownListener listener = slow();
        leaf(5, 64, 5, "false");
        Player p = player;
        PlayerMoveEvent e = new PlayerMoveEvent(p, new Location(world, 4.5, 64, 5.5), new Location(world, 5.5, 64, 5.5));
        listener.onMove(e);
        assertThat(applied).isNotEmpty();
        assertThat(applied.getLast()).isLessThan(1.0);
    }
}
