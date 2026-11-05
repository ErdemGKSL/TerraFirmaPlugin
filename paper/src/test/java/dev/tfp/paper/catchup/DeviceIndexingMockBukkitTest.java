/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.catchup;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.device.DeviceRecord;
import dev.tfp.core.device.ItemBlob;
import dev.tfp.core.persist.DataMap;
import dev.tfp.paper.blockdata.InMemoryChunkDataStore;
import dev.tfp.paper.device.DeviceKinds;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.device.DeviceStore;
import dev.tfp.paper.device.ProbeDevice;
import dev.tfp.paper.device.StackCodec;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.FakeCustomBlocks;
import dev.tfp.paper.integration.FakeCustomItems;
import dev.tfp.paper.module.ModuleServices;
import dev.tfp.paper.testing.MockServerTest;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * PAR-INFRA-25 on MockBukkit: a placed device is listed in its chunk's lazy block index under
 * {@value CatchUpModule#DEVICE_KIND} and caught up when the chunk loads again, with the wiring of
 * {@code DeviceModule} (the catch-up service looked up per call, so a device module enabled first still indexes) and
 * the direct kind {@code CatchUpModule} registers over {@code DeviceService.catchUp}.
 */
class DeviceIndexingMockBukkitTest extends MockServerTest {
    private static final Logger LOG = Logger.getLogger("tfp-test");

    /** Device data per position; only positions with a "block entity" answer. */
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

    /** Slots stay empty in these tests. */
    static final StackCodec NO_STACKS = new StackCodec() {
        @Override
        public ItemBlob encode(ItemStack stack) {
            return ItemBlob.EMPTY;
        }

        @Override
        public ItemStack decode(ItemBlob blob) {
            return ItemStack.empty();
        }
    };

    private final ManualRegions regions = new ManualRegions();
    private final InMemoryChunkDataStore chunkData = new InMemoryChunkDataStore();
    private final FakeCustomBlocks blocks = new FakeCustomBlocks().known(ProbeDevice.ID);
    private final MemoryStore store = new MemoryStore();
    private final AtomicLong now = new AtomicLong(1_000);
    private final ModuleServices services = new ModuleServices();
    private DeviceService devices;
    private World world;

    @BeforeEach
    void setUp() {
        world = server.addSimpleWorld("device-index");
        devices = new DeviceService(new DeviceKinds(), blocks, () -> store, now::get, NO_STACKS,
                new FakeCustomItems(), LOG);
        devices.register(ProbeDevice.kind());
        // DeviceModule.enable: the catch-up service does not exist yet when the device module is enabled
        devices.indexWith(block -> services.get(CatchUpService.class)
                .ifPresent(c -> c.track(block, CatchUpModule.DEVICE_KIND)));
    }

    /** CatchUpModule.enable, after the device module. */
    private CatchUpService enableCatchUp() {
        CatchUpService catchUp = new CatchUpService(new CatchUpKinds(), chunkData, w -> regions, Runnable::run,
                now::get, regions.clock::get, CatchUpSettings.DEFAULTS, LOG);
        catchUp.kinds().registerDirect(CatchUpModule.DEVICE_KIND,
                (w, pos) -> devices.catchUp(w.getBlockAt(pos.x(), pos.y(), pos.z())).isPresent());
        services.provide(CatchUpService.class, catchUp);
        server.getPluginManager().registerEvents(new CatchUpListener(catchUp), plugin);
        return catchUp;
    }

    /** Places the probe device as CraftEngine would: the custom block, then its block entity stamped at creation. */
    private Block placeProbe(int x, int y, int z) {
        Block b = world.getBlockAt(x, y, z);
        b.setType(Material.STONE);
        assertThat(blocks.place(b, BlockStateRef.of(ProbeDevice.ID), false)).isTrue();
        store.data.put(FakeCustomBlocks.at(b), devices.created(ProbeDevice.ID).orElseThrow());
        return b;
    }

    @SuppressWarnings("unchecked")
    private DeviceRecord<ProbeDevice.State> record(Block b) {
        DeviceService.Inspection i = devices.inspect(b).orElseThrow();
        return (DeviceRecord<ProbeDevice.State>) i.record().orElseThrow();
    }

    private void reload(Chunk chunk) {
        server.getPluginManager().callEvent(new ChunkUnloadEvent(chunk));
        server.getPluginManager().callEvent(new ChunkLoadEvent(chunk, false));
        regions.settle(100);
    }

    @Test
    void placedDeviceIsIndexedAndCaughtUpOnChunkLoad() {
        CatchUpService catchUp = enableCatchUp();
        Block b = placeProbe(4, 64, 4);
        PlayerMock player = server.addPlayer();
        devices.placed(b, player, EquipmentSlot.HAND, ItemStack.empty());

        assertThat(catchUp.kindAt(b)).contains(CatchUpModule.DEVICE_KIND);
        assertThat(chunkData.load(b.getChunk(), CatchUpService.INDEX_KEY).orElseThrow().kindAt(
                new dev.tfp.core.world.BlockPos(4, 64, 4))).contains(CatchUpModule.DEVICE_KIND);
        DeviceRecord<ProbeDevice.State> atPlace = record(b);
        assertThat(atPlace.lastUpdateTick()).isEqualTo(1_000);

        // nothing ticks the device: the calendar moves, the record stays
        now.set(4_000);
        assertThat(record(b).lastUpdateTick()).isEqualTo(1_000);

        reload(b.getChunk());
        DeviceRecord<ProbeDevice.State> caught = record(b);
        assertThat(caught.lastUpdateTick()).isEqualTo(4_000);
        assertThat(caught.state().simulatedTicks()).isEqualTo(atPlace.state().simulatedTicks() + 3_000);
        assertThat(catchUp.stats().unknownKinds()).isZero();
        assertThat(catchUp.stats().orphans()).isZero();

        // placing (or using) it again does not duplicate the entry
        devices.use(b, player, EquipmentSlot.HAND, ItemStack.empty());
        assertThat(catchUp.index(b.getChunk()).orElseThrow().size()).isEqualTo(1);
    }

    @Test
    void deviceUsedBeforeTheServiceExistsIsIndexedAtItsNextInteraction() {
        Block b = placeProbe(20, 64, 4);
        PlayerMock player = server.addPlayer();
        devices.placed(b, player, EquipmentSlot.HAND, ItemStack.empty()); // no catch-up service: nothing to do
        assertThat(chunkData.load(b.getChunk(), CatchUpService.INDEX_KEY)).isEmpty();

        CatchUpService catchUp = enableCatchUp();
        devices.use(b, player, EquipmentSlot.HAND, ItemStack.empty());
        assertThat(catchUp.kindAt(b)).contains(CatchUpModule.DEVICE_KIND);

        now.set(9_000);
        reload(b.getChunk());
        assertThat(record(b).lastUpdateTick()).isEqualTo(9_000);
    }

    @Test
    void aBrokenDeviceIsUntrackedAndAFailingIndexerNeverBreaksTheDevice() {
        CatchUpService catchUp = enableCatchUp();
        Block b = placeProbe(5, 64, 5);
        PlayerMock player = server.addPlayer();
        devices.placed(b, player, EquipmentSlot.HAND, ItemStack.empty());
        assertThat(catchUp.kindAt(b)).isPresent();
        // the device block entity is gone (broken by something else): the next load drops the entry
        blocks.remove(b);
        store.data.remove(FakeCustomBlocks.at(b));
        reload(b.getChunk());
        assertThat(catchUp.stats().orphans()).isEqualTo(1);
        assertThat(catchUp.index(b.getChunk()).orElseThrow().isEmpty()).isTrue();

        Block c = placeProbe(6, 64, 6);
        devices.indexWith(block -> {
            throw new IllegalStateException("boom");
        });
        now.set(2_000);
        assertThat(devices.use(c, player, EquipmentSlot.HAND, ItemStack.empty())).isTrue();
        assertThat(record(c).state().uses()).isEqualTo(1);
    }
}
