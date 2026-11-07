/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.device;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.persist.VersionedCodec;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.FakeCustomBlocks;
import dev.tfp.paper.integration.FakeCustomItems;
import dev.tfp.paper.testing.MockServerTest;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * PAR-INFRA-40: a device click the router drops before any handler (no kind for the block id, no readable block entity
 * record) leaves exactly one WARNING per position and reason; a handler's own pass stays silent.
 */
class DeviceServiceRouteLogTest extends MockServerTest {
    record Passer(int n) {
    }

    static final VersionedCodec<Passer> CODEC = VersionedCodec.<Passer>builder("passer", 1)
            .build((c, out) -> out.putInt("n", c.n()), in -> new Passer(in.requireInt("n")));

    private static final String PASSER = "tfp:device/passer";
    private static final String ORPHAN = "tfp:device/orphan";

    private final FakeCustomBlocks blocks = new FakeCustomBlocks().known(PASSER, ORPHAN, "tfp:rock/raw/granite");
    private final DeviceRoutingMockBukkitTest.MemoryStore store = new DeviceRoutingMockBukkitTest.MemoryStore();
    private final List<LogRecord> logged = new ArrayList<>();
    private DeviceService service;
    private World world;
    private PlayerMock player;

    @BeforeEach
    void setUp() {
        world = server.addSimpleWorld("devlog");
        player = server.addPlayer();
        Logger logger = Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        logger.addHandler(new Handler() {
            @Override
            public void publish(LogRecord record) {
                logged.add(record);
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        });
        service = new DeviceService(new DeviceKinds(), blocks, () -> store, new AtomicLong(100)::get,
                new DeviceRoutingMockBukkitTest.TableCodec(), new FakeCustomItems(), logger);
        service.register(DeviceKind.builder(PASSER, CODEC, new Passer(0)).build()); // default handler: pass
    }

    private Block place(String id, int x, boolean blockEntity) {
        Block block = world.getBlockAt(x, 64, 0);
        blocks.place(block, BlockStateRef.of(id), false);
        if (blockEntity) {
            store.addBlockEntity(block);
        }
        return block;
    }

    private List<String> warnings() {
        return logged.stream().filter(r -> r.getLevel() == Level.WARNING).map(LogRecord::getMessage).toList();
    }

    @Test
    void noKindLogsOnceForRepeatedClicksAtThePosition() {
        Block block = place(ORPHAN, 0, true);
        for (int i = 0; i < 5; i++) {
            assertThat(service.use(block, player, EquipmentSlot.HAND, ItemStack.empty())).isFalse();
        }
        assertThat(warnings()).containsExactly(
                "Device " + ORPHAN + " at devlog 0,64,0: click dropped: no device kind registered");

        // another position logs its own line
        Block other = place(ORPHAN, 3, true);
        service.use(other, player, EquipmentSlot.HAND, ItemStack.empty());
        assertThat(warnings()).hasSize(2);
        assertThat(warnings().get(1)).contains("3,64,0", "no device kind registered");
    }

    @Test
    void noRecordLogsOnceForRepeatedClicksAtThePosition() {
        Block block = place(PASSER, 1, false);
        for (int i = 0; i < 5; i++) {
            assertThat(service.use(block, player, EquipmentSlot.HAND, ItemStack.empty())).isFalse();
            assertThat(service.attack(block, player)).isFalse();
        }
        assertThat(warnings()).containsExactly("Device " + PASSER
                + " at devlog 1,64,0: click dropped: no device block entity or unreadable record");
    }

    @Test
    void aHandlerPassLogsNothing() {
        Block block = place(PASSER, 2, true);
        for (int i = 0; i < 3; i++) {
            assertThat(service.use(block, player, EquipmentSlot.HAND, ItemStack.empty())).isFalse();
            assertThat(service.attack(block, player)).isFalse();
        }
        assertThat(logged).isEmpty();
    }

    @Test
    void leftClicksOnNonDeviceBlocksAndVanillaBlocksStaySilent() {
        Block rock = place("tfp:rock/raw/granite", 4, false);
        service.attack(rock, player);
        service.attack(world.getBlockAt(5, 64, 0), player);
        service.use(world.getBlockAt(5, 64, 0), player, EquipmentSlot.HAND, ItemStack.empty());
        // neighbour updates are no clicks
        service.neighbourChanged(place(PASSER, 6, false));
        assertThat(logged).isEmpty();
    }

    @Test
    void theRememberedSetIsBounded() {
        for (int i = 0; i <= DeviceService.DROP_LOG_POSITIONS; i++) {
            service.use(place(ORPHAN, 10 + i, false), player, EquipmentSlot.HAND, ItemStack.empty());
        }
        assertThat(warnings()).hasSize(DeviceService.DROP_LOG_POSITIONS + 1);
        // the first position was evicted: it logs again; the last one is still remembered
        service.use(world.getBlockAt(10, 64, 0), player, EquipmentSlot.HAND, ItemStack.empty());
        service.use(world.getBlockAt(10 + DeviceService.DROP_LOG_POSITIONS, 64, 0), player, EquipmentSlot.HAND,
                ItemStack.empty());
        assertThat(warnings()).hasSize(DeviceService.DROP_LOG_POSITIONS + 2);
    }
}
