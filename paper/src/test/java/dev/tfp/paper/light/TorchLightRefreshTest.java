/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.light;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import dev.tfp.core.device.ItemBlob;
import dev.tfp.core.light.LightSourceRules;
import dev.tfp.core.persist.DataMap;
import dev.tfp.core.world.BlockPos;
import dev.tfp.paper.device.DeviceKinds;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.device.DeviceStore;
import dev.tfp.paper.device.StackCodec;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.FakeCustomBlocks;
import dev.tfp.paper.integration.FakeCustomItems;
import dev.tfp.paper.pipeline.CommitBudget;
import dev.tfp.paper.pipeline.RegionExecutor;
import dev.tfp.paper.testing.MockServerTest;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * PAR-HEAT-28: a torch that burns out while its chunk loads requests the light resend of the chunk holding the changed
 * position and of the neighbour chunks its light reached, {@link TorchService#REFRESH_DELAY_TICKS} after the apply step (so the client loses the lit torch's block light
 * 14 without a relog); one resend per chunk and change, none for a torch still burning or for a player's douse. The
 * resend waits while the server's light engine still holds the lit light at a changed position ({@link
 * TorchService.LightProbe}), at most {@link TorchService#REFRESH_MAX_TICKS}.
 */
class TorchLightRefreshTest extends MockServerTest {
    static final long START = 10_000;
    static final int DAY = 24_000;

    record Refresh(String world, int chunkX, int chunkZ, long tick) {
    }

    private final FakeCustomBlocks blocks = new FakeCustomBlocks()
        .known("tfp:torch", "tfp:wall_torch", "tfp:dead_torch", "tfp:dead_wall_torch");
    private final Map<FakeCustomBlocks.At, DataMap> data = new ConcurrentHashMap<>();
    private final DeviceStore store = new DeviceStore() {
        private boolean device(Block block) {
            return blocks.stateAt(block).map(s -> s.id().equals("tfp:torch") || s.id().equals("tfp:wall_torch"))
                .orElse(false);
        }

        @Override
        public Optional<DataMap> read(Block block) {
            if (!device(block)) {
                data.remove(FakeCustomBlocks.at(block));
                return Optional.empty();
            }
            DataMap m = data.get(FakeCustomBlocks.at(block));
            return Optional.of(m == null ? new DataMap() : m.copy());
        }

        @Override
        public boolean write(Block block, DataMap map) {
            if (!device(block)) {
                return false;
            }
            data.put(FakeCustomBlocks.at(block), map.copy());
            return true;
        }
    };

    private static final StackCodec NO_SLOTS = new StackCodec() {
        @Override
        public ItemBlob encode(ItemStack stack) {
            return ItemBlob.EMPTY;
        }

        @Override
        public ItemStack decode(ItemBlob blob) {
            return ItemStack.empty();
        }
    };

    /** Region tasks: {@code run} goes into this tick, {@code runNextTick} into the next one. The test thread owns all. */
    private Deque<Runnable> current = new ArrayDeque<>();
    private Deque<Runnable> following = new ArrayDeque<>();
    private long tick;
    private final RegionExecutor regions = new RegionExecutor() {
        @Override
        public void run(int chunkX, int chunkZ, Runnable task) {
            current.add(task);
        }

        @Override
        public void runNextTick(int chunkX, int chunkZ, Runnable task) {
            following.add(task);
        }

        @Override
        public boolean isOwner(int chunkX, int chunkZ) {
            return true;
        }
    };

    private final AtomicLong clock = new AtomicLong(START);
    private final List<Refresh> refreshes = new ArrayList<>();
    /** Positions where the fake light engine still holds the lit torch's light. */
    private final Set<BlockPos> unsettled = ConcurrentHashMap.newKeySet();
    private TorchService service;
    private DeviceService devices;
    private World world;
    private PlayerMock player;

    @BeforeEach
    void setUp() {
        world = server.addSimpleWorld("torch-light");
        world.loadChunk(0, 0);
        world.loadChunk(1, 0);
        devices = new DeviceService(new DeviceKinds(), blocks, () -> store, clock::get, NO_SLOTS, new FakeCustomItems(),
            Logger.getLogger("torch-light-test"));
        TorchEffects effects = new TorchEffects() {
            @Override
            public void burnout(Block block) {
                service.burnout(block);
            }

            @Override
            public void burnoutIn(Block block, long ticks) {
            }

            @Override
            public void takeOne(Player p, EquipmentSlot hand) {
            }

            @Override
            public void give(Player p, ItemStack stack) {
            }
        };
        TorchDevice device = new TorchDevice(new TorchDevice.Services(() -> LightSourceRules.DEFAULT_TORCH_TICKS,
            s -> s.getType() == Material.STICK, () -> new ItemStack(Material.TORCH), effects));
        device.kinds().forEach(devices::register);
        service = new TorchService(blocks, devices, device, clock::get, () -> LightSourceRules.DEFAULT_TORCH_TICKS,
            w -> regions, Runnable::run, new CommitBudget(1_000_000_000L, System::nanoTime),
            (w, cx, cz) -> refreshes.add(new Refresh(w.getName(), cx, cz, tick)),
            (w, p) -> !unsettled.contains(p));
        player = server.addPlayer();
        player.teleport(new Location(world, 4.5, 64, 4.5));
        player.setGameMode(GameMode.SURVIVAL);
    }

    /** One server tick: drains this tick's tasks, then the next tick's queue becomes current. */
    private void tick() {
        while (!current.isEmpty()) {
            current.poll().run();
        }
        tick++;
        Deque<Runnable> t = current;
        current = following;
        following = t;
    }

    private void ticks(int n) {
        for (int i = 0; i < n; i++) {
            tick();
        }
    }

    private Block placeLit(int x, int z, BlockStateRef state) {
        Block b = world.getBlockAt(x, 64, z);
        blocks.place(b, state, true);
        devices.placed(b, player, EquipmentSlot.HAND, new ItemStack(Material.TORCH));
        return b;
    }

    private static BlockPos pos(Block b) {
        return new BlockPos(b.getX(), b.getY(), b.getZ());
    }

    /** The resends of one chunk. */
    private List<Refresh> in(int chunkX, int chunkZ) {
        return refreshes.stream().filter(r -> r.chunkX() == chunkX && r.chunkZ() == chunkZ).toList();
    }

    private static String chunk(long key) {
        return (int) (key >> 32) + "," + (int) key;
    }

    @Test
    void chunkLoadBurnoutRequestsTheLightResendOfItsChunkAfterTheDelay() {
        Block floor = placeLit(2, 3, BlockStateRef.of("tfp:torch"));
        clock.addAndGet(4L * DAY);
        assertThat(service.chunkLoaded(floor.getChunk(), List.of(pos(floor)))).isEmpty();
        tick(); // decision and apply step
        assertThat(blocks.stateAt(floor).orElseThrow().id()).isEqualTo("tfp:dead_torch");
        assertThat(service.burnouts()).isEqualTo(1);
        long applied = tick - 1;
        ticks(TorchService.REFRESH_DELAY_TICKS - 1);
        assertThat(refreshes).isEmpty(); // not before the light engine settled
        ticks(2);
        assertThat(in(0, 0))
            .containsExactly(new Refresh("torch-light", 0, 0, applied + TorchService.REFRESH_DELAY_TICKS));
        // (2, 3) lit the chunks west (3 blocks), north (4), south (13) and north-west (7) too; east is 14 away
        assertThat(refreshes).extracting(r -> r.chunkX() + "," + r.chunkZ())
            .containsExactlyInAnyOrder("0,0", "-1,0", "0,-1", "0,1", "-1,-1");
        assertThat(service.lightRefreshes()).isEqualTo(5);
        ticks(20);
        assertThat(refreshes).hasSize(5); // once per chunk and change
    }

    @Test
    void twoTorchesInOneChunkShareOneResendAndAnotherChunkGetsItsOwn() {
        Block floor = placeLit(2, 3, BlockStateRef.of("tfp:torch"));
        Block wall = placeLit(5, 7, BlockStateRef.of("tfp:wall_torch").with("facing", "east"));
        Block other = placeLit(18, 2, BlockStateRef.of("tfp:torch"));
        clock.addAndGet(4L * DAY);
        service.chunkLoaded(floor.getChunk(), List.of(pos(floor), pos(wall)));
        service.chunkLoaded(other.getChunk(), List.of(pos(other)));
        ticks(TorchService.REFRESH_DELAY_TICKS + 3);
        assertThat(blocks.stateAt(wall).orElseThrow())
            .isEqualTo(BlockStateRef.of("tfp:dead_wall_torch").with("facing", "east"));
        assertThat(refreshes).extracting(r -> r.chunkX() + "," + r.chunkZ()).contains("0,0", "1,0")
            .doesNotHaveDuplicates(); // the three torches' light overlaps: one resend per chunk
        // a later burnout in the same chunk gets a resend of its own
        Block late = placeLit(3, 3, BlockStateRef.of("tfp:torch"));
        clock.addAndGet(4L * DAY);
        service.chunkLoaded(late.getChunk(), List.of(pos(late)));
        ticks(TorchService.REFRESH_DELAY_TICKS + 3);
        assertThat(in(0, 0)).hasSize(2);
        assertThat(in(1, 0)).hasSize(2); // (3, 3) is 13 blocks from chunk 1,0: 16 - 3 = 13, reached
        assertThat(in(2, 0)).isEmpty();
    }

    @Test
    void aTorchStillBurningRequestsNoResend() {
        Block floor = placeLit(2, 3, BlockStateRef.of("tfp:torch"));
        clock.addAndGet(3L * DAY);
        assertThat(service.chunkLoaded(floor.getChunk(), List.of(pos(floor)))).hasSize(1);
        ticks(TorchService.REFRESH_DELAY_TICKS + 3);
        assertThat(blocks.stateAt(floor).orElseThrow().id()).isEqualTo("tfp:torch");
        assertThat(refreshes).isEmpty();
    }

    @Test
    void aDouseRequestsNoResend() {
        Block floor = placeLit(2, 3, BlockStateRef.of("tfp:torch"));
        assertThat(service.douse(floor)).isTrue();
        ticks(TorchService.REFRESH_DELAY_TICKS + 3);
        assertThat(blocks.stateAt(floor).orElseThrow().id()).isEqualTo("tfp:dead_torch");
        assertThat(refreshes).isEmpty();
    }

    @Test
    void theResendWaitsUntilTheLightEngineRemovedTheLitLight() {
        Block floor = placeLit(2, 3, BlockStateRef.of("tfp:torch"));
        unsettled.add(pos(floor));
        clock.addAndGet(4L * DAY);
        service.chunkLoaded(floor.getChunk(), List.of(pos(floor)));
        tick();
        assertThat(blocks.stateAt(floor).orElseThrow().id()).isEqualTo("tfp:dead_torch");
        ticks(TorchService.REFRESH_DELAY_TICKS + 3 * TorchService.REFRESH_POLL_TICKS);
        assertThat(refreshes).isEmpty(); // the engine still holds 14: a resend now would send the stale light again
        unsettled.clear();
        long settledAt = tick;
        ticks(TorchService.REFRESH_POLL_TICKS + 1);
        assertThat(in(0, 0)).hasSize(1);
        assertThat(in(0, 0).get(0).tick()).isBetween(settledAt, settledAt + TorchService.REFRESH_POLL_TICKS);
        int sent = refreshes.size();
        ticks(40);
        assertThat(refreshes).hasSize(sent);
    }

    @Test
    void aLaterBurnoutInThePendingChunkJoinsItsWaitAndTheResendGoesOutAtTheCap() {
        Block floor = placeLit(2, 3, BlockStateRef.of("tfp:torch"));
        Block wall = placeLit(5, 7, BlockStateRef.of("tfp:wall_torch").with("facing", "east"));
        unsettled.add(pos(wall)); // the wall torch's light never settles here
        clock.addAndGet(4L * DAY);
        service.chunkLoaded(floor.getChunk(), List.of(pos(floor)));
        tick();
        ticks(2);
        service.chunkLoaded(wall.getChunk(), List.of(pos(wall))); // joins the floor torch's pending resend
        ticks(TorchService.REFRESH_DELAY_TICKS + 2);
        assertThat(blocks.stateAt(wall).orElseThrow().id()).isEqualTo("tfp:dead_wall_torch");
        assertThat(in(0, 0)).isEmpty(); // waits for the joined wall torch too
        ticks(TorchService.REFRESH_MAX_TICKS);
        assertThat(in(0, 0)).containsExactly(new Refresh("torch-light", 0, 0, TorchService.REFRESH_MAX_TICKS));
    }

    @Test
    void theReachIsTheOwnChunkAndTheNeighboursTheLitLightGotTo() {
        assertThat(TorchService.chunksInReach(new BlockPos(8, 64, 8))).extracting(TorchLightRefreshTest::chunk)
            .containsExactly("0,0", "-1,0", "0,-1", "0,1", "1,0"); // 9 and 8 blocks to the edges; corners 17 and 16
        assertThat(TorchService.chunksInReach(new BlockPos(-1, 64, 15))).extracting(TorchLightRefreshTest::chunk)
            .containsExactly("-1,0", "-1,1", "0,0", "0,1"); // the corner of four chunks
        assertThat(TorchService.chunksInReach(new BlockPos(18 * 16 + 6, 70, -16 + 2)))
            .extracting(TorchLightRefreshTest::chunk)
            .containsExactly("18,-1", "17,-2", "17,-1", "18,-2", "19,-2", "19,-1"); // 19,-2: 10 + 3 = 13, reached
    }

    /**
     * The in-game failure on 946ba98 (GUI 2): a floor torch and a wall torch 9 blocks apart in two chunks. The floor
     * torch's chunk was resent while the server still held the wall torch's light (14 - 9 = 5) in the floor cell, and the
     * client kept 5 there. The floor chunk now waits for the wall torch's light too, and is resent again after a burnout
     * whose light reached it, even when its own resend already went out.
     */
    @Test
    void aNeighbourTorchsBurnoutHoldsAndRepeatsTheResendOfTheChunkItsLightReached() {
        Block floor = placeLit(12, 4, BlockStateRef.of("tfp:torch"));
        Block wall = placeLit(21, 4, BlockStateRef.of("tfp:wall_torch").with("facing", "east"));
        unsettled.add(pos(wall));
        clock.addAndGet(4L * DAY);
        service.chunkLoaded(floor.getChunk(), List.of(pos(floor)));
        service.chunkLoaded(wall.getChunk(), List.of(pos(wall)));
        ticks(TorchService.REFRESH_DELAY_TICKS + 3 * TorchService.REFRESH_POLL_TICKS);
        assertThat(blocks.stateAt(floor).orElseThrow().id()).isEqualTo("tfp:dead_torch");
        assertThat(blocks.stateAt(wall).orElseThrow().id()).isEqualTo("tfp:dead_wall_torch");
        assertThat(in(0, 0)).isEmpty(); // the floor's own light settled, the wall torch's 5 in it did not
        assertThat(in(1, 0)).isEmpty();
        unsettled.clear();
        ticks(TorchService.REFRESH_POLL_TICKS + 1);
        assertThat(in(0, 0)).hasSize(1);
        assertThat(in(1, 0)).hasSize(1);

        // the wall torch is relit and burns out after the floor chunk's resend went out: the floor chunk is sent again
        assertThat(service.light(wall)).isTrue();
        clock.addAndGet(4L * DAY);
        service.chunkLoaded(wall.getChunk(), List.of(pos(wall)));
        ticks(TorchService.REFRESH_DELAY_TICKS + 3);
        assertThat(in(0, 0)).hasSize(2);
        assertThat(in(1, 0)).hasSize(2);
    }

    @Test
    void thePaperProbeCountsAnUnloadedChunkAsSettled() {
        assertThat(TorchService.LightProbe.PAPER.settled(world, new BlockPos(40 * 16, 64, 40 * 16))).isTrue();
        assertThatCode(() -> TorchService.LightProbe.PAPER.settled(world, new BlockPos(2, 64, 3)))
            .doesNotThrowAnyException();
    }

    @Test
    void thePaperRefreshIsSafeWithAndWithoutPlayersSeeingTheChunk() {
        assertThatCode(() -> TorchService.LightRefresh.PAPER.refresh(world, 0, 0)).doesNotThrowAnyException();
        assertThatCode(() -> TorchService.LightRefresh.PAPER.refresh(world, 40, 40)).doesNotThrowAnyException();
    }
}
