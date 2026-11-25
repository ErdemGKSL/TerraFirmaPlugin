/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.crankshaft;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.charcoalforge.BellowsRules;
import dev.tfp.core.persist.DataMap;
import dev.tfp.core.power.WaterWheel;
import dev.tfp.core.rotation.Rotation;
import dev.tfp.core.world.BlockPos;
import dev.tfp.core.world.Direction;
import dev.tfp.paper.bellows.BellowsConsumers;
import dev.tfp.paper.blockdata.PdcChunkDataStore;
import dev.tfp.paper.device.DeviceKinds;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.device.DeviceStore;
import dev.tfp.paper.device.StackCodec;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.FakeCustomBlocks;
import dev.tfp.paper.integration.FakeCustomItems;
import dev.tfp.paper.pump.ShaftSource;
import dev.tfp.paper.testing.MockServerTest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * PAR-MECH-05 on MockBukkit with fakes of the CraftEngine adapter: a steel rod grows the shaft, a crank turned by a water wheel's
 * rotation drives the bellows in front of it (TFC's push ticks), the bellows and the pump read the crank, and the shaft goes with
 * its base. Stand-ins: a blaze rod is the steel rod; the rotation is a fake {@link CrankRotation} (the network sink is PAR-MECH-21).
 */
class CrankshaftMockBukkitTest extends MockServerTest {
    static final String FORGE = "tfp:test/forge";

    /** In-memory device store: positions with a "block entity". */
    static final class MemoryStore implements DeviceStore {
        final Map<FakeCustomBlocks.At, DataMap> data = new ConcurrentHashMap<>();

        @Override
        public Optional<DataMap> read(Block block) {
            DataMap m = data.get(FakeCustomBlocks.at(block));
            return m == null ? Optional.empty() : Optional.of(m.copy());
        }

        @Override
        public boolean write(Block block, DataMap map) {
            if (!data.containsKey(FakeCustomBlocks.at(block))) {
                return false;
            }
            data.put(FakeCustomBlocks.at(block), map.copy());
            return true;
        }
    }

    private final FakeCustomBlocks blocks = new FakeCustomBlocks().known(CrankshaftBlocks.ID, CrankshaftBlocks.BELLOWS, FORGE);
    private final MemoryStore store = new MemoryStore();
    private final InlineScheduler scheduler = new InlineScheduler();
    private final AtomicReference<Optional<Rotation>> rotation = new AtomicReference<>(Optional.empty());
    private final BellowsConsumers consumers = new BellowsConsumers();
    private final List<Integer> air = new ArrayList<>();
    private final List<Block> blows = new ArrayList<>();
    private boolean accepts = true;
    private CrankshaftService service;
    private DeviceService devices;
    private CrankshaftChunkIndex index;
    private World world;
    private PlayerMock player;

    @BeforeEach
    void setUp() {
        world = server.addSimpleWorld("crank");
        service = new CrankshaftService(scheduler, blocks, () -> (w, pos) -> rotation.get(), () -> Optional.of(consumers),
            blows::add);
        index = new CrankshaftChunkIndex(new PdcChunkDataStore());
        CrankshaftDevices kinds = new CrankshaftDevices(service, index, blocks, scheduler, s -> s.getType() == Material.BLAZE_ROD);
        devices = new DeviceService(new DeviceKinds(), blocks, () -> store, new AtomicLong(1000)::get, StackCodec.PAPER,
            new FakeCustomItems(), Logger.getLogger("test"));
        devices.register(kinds.kind());
        consumers.register(FORGE, new BellowsConsumers.Consumer() {
            @Override
            public boolean canAcceptAir(Block block) {
                return accepts;
            }

            @Override
            public void intakeAir(Block block, int amount) {
                air.add(amount);
            }
        });
        player = server.addPlayer();
        player.setGameMode(GameMode.SURVIVAL);
    }

    /** Base at (0, 64, 0) facing east, its shaft grown with a rod, a bellows at (2, 64, 0) facing east, a forge in front. */
    private Block line() {
        Block base = world.getBlockAt(0, 64, 0);
        blocks.place(base, CrankshaftBlocks.state(new dev.tfp.core.crankshaft.CrankshaftRules.Piece(
            dev.tfp.core.crankshaft.CrankshaftRules.Part.BASE, Direction.EAST)), true);
        store.data.put(FakeCustomBlocks.at(base), new DataMap());
        devices.placed(base, player, EquipmentSlot.HAND, ItemStack.empty());
        ItemStack rods = new ItemStack(Material.BLAZE_ROD, 2);
        player.getInventory().setItem(EquipmentSlot.HAND, rods);
        devices.use(base, player, EquipmentSlot.HAND, rods);
        blocks.place(world.getBlockAt(2, 64, 0), BlockStateRef.of(CrankshaftBlocks.BELLOWS).with("facing", "east"), true);
        blocks.place(world.getBlockAt(3, 64, 0), BlockStateRef.of(FORGE), true);
        return base;
    }

    private void runTicks(int ticks) {
        for (int t = 0; t < ticks; t += CrankshaftService.PERIOD) {
            new ArrayList<>(scheduler.repeating).forEach(Runnable::run);
        }
    }

    @Test
    void steelRodGrowsTheShaft() {
        line();
        assertThat(blocks.stateAt(world.getBlockAt(1, 64, 0)).orElseThrow().properties())
            .containsEntry("part", "shaft").containsEntry("facing", "east");
        assertThat(player.getInventory().getItem(EquipmentSlot.HAND).getAmount()).isEqualTo(1);
        assertThat(scheduler.repeating).hasSize(1);
        assertThat(index.positions(world.getBlockAt(0, 64, 0).getChunk())).containsExactly(new BlockPos(0, 64, 0));
        // a rod on the shaft, or with the block in front taken, passes
        ItemStack rod = new ItemStack(Material.BLAZE_ROD, 1);
        player.getInventory().setItem(EquipmentSlot.HAND, rod);
        devices.use(world.getBlockAt(1, 64, 0), player, EquipmentSlot.HAND, rod);
        assertThat(blocks.stateAt(world.getBlockAt(2, 64, 0)).orElseThrow().id()).isEqualTo(CrankshaftBlocks.BELLOWS);
    }

    /** The done criterion: a water wheel's rotation on the crank drives the bellows at TFC's push ticks (32, 112, 192). */
    @Test
    void waterWheelDrivesBellows() {
        line();
        runTicks(200);
        assertThat(air).isEmpty(); // not in a network: nothing
        rotation.set(Optional.of(Rotation.of(Direction.SOUTH, WaterWheel.MAX_SPEED)));
        runTicks(200);
        assertThat(air).containsExactly(BellowsRules.BELLOWS_AIR, BellowsRules.BELLOWS_AIR, BellowsRules.BELLOWS_AIR);
        assertThat(blows).hasSize(3).allSatisfy(b -> assertThat(b).isEqualTo(world.getBlockAt(2, 64, 0)));
        // a receiver that refuses air: no push effects (TFC doPush), the cadence goes on
        accepts = false;
        runTicks(200);
        assertThat(air).hasSize(3);
        assertThat(blows).hasSize(3);
        assertThat(service.pushes(world.getUID(), new BlockPos(0, 64, 0))).isEqualTo(5);
    }

    /** BellowsBlockEntity.onRightClick: with a crank the hand press reads it as idle or turning; the pump reads its rotation. */
    @Test
    void bellowsAndPumpReadTheCrank() {
        line();
        var crank = CrankshaftModule.crank(service, blocks, scheduler);
        ShaftSource shafts = CrankshaftModule.shaftSource(service, blocks, scheduler);
        Block bellows = world.getBlockAt(2, 64, 0);
        assertThat(crank.apply(bellows)).isEqualTo(BellowsRules.Crank.IDLE);
        assertThat(shafts.rotationAt(bellows, Direction.WEST)).isNull();
        Rotation r = Rotation.of(Direction.SOUTH, 0.1f);
        rotation.set(Optional.of(r));
        assertThat(crank.apply(bellows)).isEqualTo(BellowsRules.Crank.TURNING);
        assertThat(shafts.rotationAt(bellows, Direction.WEST)).isEqualTo(r);
        assertThat(shafts.rotationAt(bellows, Direction.EAST)).isNull();
        // a bellows facing the crank sees none
        blocks.place(bellows, BlockStateRef.of(CrankshaftBlocks.BELLOWS).with("facing", "west"), true);
        assertThat(crank.apply(bellows)).isEqualTo(BellowsRules.Crank.NONE);
    }

    /** CrankshaftBlock.updateShape: the shaft goes with its base (no drop); the task stops. */
    @Test
    void shaftGoesWithItsBase() {
        Block base = line();
        DataMap last = store.data.get(FakeCustomBlocks.at(base));
        blocks.remove(base);
        devices.removed(world, 0, 64, 0, CrankshaftBlocks.ID, last);
        assertThat(blocks.stateAt(world.getBlockAt(1, 64, 0))).isEmpty();
        assertThat(service.running()).isZero();
        assertThat(index.positions(base.getChunk())).isEmpty();
    }

    /** Chunk load starts the listed bases again and forgets stale entries. */
    @Test
    void chunkLoadRestartsBases() {
        Block base = line();
        service.stop(world.getUID(), new BlockPos(0, 64, 0));
        assertThat(service.running()).isZero();
        CrankshaftModule.chunkLoaded(base.getChunk(), blocks, index, service);
        assertThat(service.running()).isEqualTo(1);
    }
}
