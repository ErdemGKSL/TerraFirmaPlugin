/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.device;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.device.DeviceRecord;
import dev.tfp.core.persist.DataMap;
import dev.tfp.core.persist.VersionedCodec;
import dev.tfp.paper.integration.FakeCustomBlocks;
import dev.tfp.paper.integration.FakeCustomItems;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.testing.MockServerTest;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * PAR-PROC-35: {@link DeviceService#edit} (load, catch up, change, store, outside a routed callback) and the per-kind
 * {@link DeviceService.AccessHook} around clicks, neighbour updates and removal.
 */
class DeviceServiceEditMockBukkitTest extends MockServerTest {
    record Counter(long ticks, int edits) {
    }

    static final VersionedCodec<Counter> COUNTER = VersionedCodec.<Counter>builder("counter", 1)
        .build((c, out) -> {
            out.putLong("t", c.ticks());
            out.putInt("e", c.edits());
        }, in -> new Counter(in.requireLong("t"), in.getInt("e", 0)));

    static final String ID = "tfp:device/counter";

    private final FakeCustomBlocks blocks = new FakeCustomBlocks().known(ID, "tfp:device/other");
    private final DeviceRoutingMockBukkitTest.MemoryStore store = new DeviceRoutingMockBukkitTest.MemoryStore();
    private final AtomicLong now = new AtomicLong(1_000);
    private final List<String> events = new ArrayList<>();
    private DeviceService service;
    private DeviceKind<Counter> kind;
    private Block block;

    @BeforeEach
    void setUp() {
        World world = server.addSimpleWorld("edits");
        service = new DeviceService(new DeviceKinds(), blocks, () -> store, now::get,
            new DeviceRoutingMockBukkitTest.TableCodec(), new FakeCustomItems(), Logger.getLogger("test"));
        kind = service.register(DeviceKind.builder(ID, COUNTER, new Counter(0, 0))
            .advance((rec, from, to) -> rec.withState(new Counter(rec.state().ticks() + to - from, rec.state().edits())))
            .handler(new DeviceHandler<>() {
                @Override
                public DeviceResult<Counter> useWithItem(DeviceContext<Counter> ctx) {
                    events.add("use");
                    return DeviceResult.consume();
                }

                @Override
                public DeviceResult<Counter> neighbourChanged(DeviceContext<Counter> ctx) {
                    events.add("neighbour");
                    return DeviceResult.pass();
                }
            })
            .build());
        service.register(DeviceKind.builder("tfp:device/other", COUNTER, new Counter(0, 0)).build());
        block = world.getBlockAt(1, 64, 1);
        blocks.place(block, BlockStateRef.of(ID), true);
        store.addBlockEntity(block);
    }

    private Counter stored() {
        return kind.codec().decode(store.read(block).orElseThrow()).state();
    }

    @Test
    void editCatchesUpAppliesTheChangeAndStoresIt() {
        service.catchUp(block);
        now.addAndGet(50);
        long[] seenNow = new long[1];
        Optional<DeviceRecord<Counter>> r = service.edit(block, kind, (rec, t) -> {
            seenNow[0] = t;
            assertThat(rec.state().ticks()).as("caught up before the change").isEqualTo(50);
            return rec.withState(new Counter(rec.state().ticks(), rec.state().edits() + 1));
        });
        assertThat(r).isPresent();
        assertThat(seenNow[0]).isEqualTo(1_050);
        assertThat(stored()).isEqualTo(new Counter(50, 1));
        assertThat(r.get().lastUpdateTick()).isEqualTo(1_050);
    }

    @Test
    void editRefusesAnotherKindOrNoDevice() {
        Block other = block.getRelative(1, 0, 0);
        blocks.place(other, BlockStateRef.of("tfp:device/other"), true);
        store.addBlockEntity(other);
        assertThat(service.edit(other, kind, (rec, t) -> rec)).as("a block of another kind").isEmpty();
        Block plain = block.getRelative(0, 1, 0);
        assertThat(service.edit(plain, kind, (rec, t) -> rec)).isEmpty();
        Block noEntity = block.getRelative(0, 0, 1);
        blocks.place(noEntity, BlockStateRef.of(ID), true);
        assertThat(service.edit(noEntity, kind, (rec, t) -> rec)).as("no block entity").isEmpty();
    }

    @Test
    void aFailingEditStoresNothing() {
        service.catchUp(block);
        DataMap before = store.read(block).orElseThrow();
        now.addAndGet(10);
        assertThat(service.edit(block, kind, (rec, t) -> {
            throw new IllegalStateException("boom");
        })).isEmpty();
        assertThat(store.read(block).orElseThrow()).isEqualTo(before);
    }

    @Test
    void theHookRunsBeforeAndAfterClicksNeighbourUpdatesAndAfterRemoval() throws Exception {
        AutoCloseable h = service.hook(ID, new DeviceService.AccessHook() {
            @Override
            public void before(Block b) {
                events.add("before");
            }

            @Override
            public void after(Block b) {
                events.add("after");
            }
        });
        PlayerMock player = server.addPlayer();
        service.use(block, player, EquipmentSlot.HAND, ItemStack.empty());
        assertThat(events).containsExactly("before", "use", "after");
        events.clear();
        service.neighbourChanged(block);
        assertThat(events).containsExactly("before", "neighbour", "after");
        events.clear();
        service.edit(block, kind, (rec, t) -> rec);
        assertThat(events).as("edit runs no hook").isEmpty();
        DataMap last = store.read(block).orElseThrow();
        service.removed(block.getWorld(), 1, 64, 1, ID, last);
        assertThat(events).containsExactly("after");
        events.clear();
        h.close();
        service.use(block, player, EquipmentSlot.HAND, ItemStack.empty());
        assertThat(events).containsExactly("use");
    }

    @Test
    void aFailingHookDoesNotStopTheClick() {
        service.hook(ID, new DeviceService.AccessHook() {
            @Override
            public void before(Block b) {
                throw new IllegalStateException("hook");
            }
        });
        assertThat(service.use(block, server.addPlayer(), EquipmentSlot.HAND, ItemStack.empty())).isTrue();
        assertThat(events).containsExactly("use");
    }
}
