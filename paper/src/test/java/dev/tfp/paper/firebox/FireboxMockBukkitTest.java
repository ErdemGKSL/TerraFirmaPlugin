/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.firebox;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.device.DeviceRecord;
import dev.tfp.core.device.ItemBlob;
import dev.tfp.core.firebox.FireboxData;
import dev.tfp.core.firebox.FireboxKiln;
import dev.tfp.core.firebox.FireboxSim;
import dev.tfp.core.fuel.Fuel;
import dev.tfp.core.persist.DataMap;
import dev.tfp.paper.device.DeviceKind;
import dev.tfp.paper.device.DeviceKinds;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.device.DeviceStore;
import dev.tfp.paper.device.StackCodec;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.FakeCustomBlocks;
import dev.tfp.paper.integration.FakeCustomItems;
import dev.tfp.paper.scheduler.Scheduler;
import dev.tfp.paper.testing.MockServerTest;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * PAR-HEAT-12 on MockBukkit, with fakes of the CraftEngine adapter and the world effects: the click order (fuel in,
 * sneak take-out, douse, screen), lighting by fire, the lazy burn-out, the heat given to a consumer above, the kiln count,
 * the screen commit and the game modes. Coal (2000 ticks, 1415 C) and charcoal (1800, 1350) are the fuels.
 */
class FireboxMockBukkitTest extends MockServerTest {
    static final long START = 10_000;
    static final Fuel COAL = new Fuel("minecraft:coal", 2000, 1415f, 1f);
    static final Fuel CHARCOAL = new Fuel("minecraft:charcoal", 1800, 1350f, 1f);

    static final class TableCodec implements StackCodec {
        final List<ItemStack> table = new ArrayList<>();

        @Override
        public ItemBlob encode(ItemStack stack) {
            if (StackCodec.isEmpty(stack)) {
                return ItemBlob.EMPTY;
            }
            table.add(stack.clone());
            return ItemBlob.of(ByteBuffer.allocate(4).putInt(table.size() - 1).array());
        }

        @Override
        public ItemStack decode(ItemBlob blob) {
            return blob.isEmpty() ? ItemStack.empty() : table.get(ByteBuffer.wrap(blob.bytes()).getInt()).clone();
        }
    }

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

    static final class Effects implements FireboxEffects {
        Boolean shown;
        final List<Long> callbacks = new ArrayList<>();
        final List<ItemStack> given = new ArrayList<>();
        final List<Douse> doused = new ArrayList<>();

        @Override
        public void show(Block block, boolean lit) {
            shown = lit;
        }

        @Override
        public void give(Player player, ItemStack stack) {
            given.add(stack);
        }

        @Override
        public void useDouse(Player player, EquipmentSlot hand, Douse douse) {
            doused.add(douse);
        }

        @Override
        public void callbackIn(Block block, long ticks) {
            callbacks.add(ticks);
        }
    }

    /** A kiln world of one closed 3 x 3 x 3 chamber over the firebox at (0, 64, 0). */
    static final class Chamber implements FireboxKiln.World {
        @Override
        public boolean exterior(FireboxKiln.Pos p, FireboxKiln.Dir moved) {
            return !inside(p) && !(p.x() == 0 && p.y() == 64 && p.z() == 0);
        }

        @Override
        public boolean interior(FireboxKiln.Pos p) {
            return inside(p);
        }

        static boolean inside(FireboxKiln.Pos p) {
            return p.x() >= 0 && p.x() < 3 && p.y() >= 65 && p.y() < 68 && p.z() >= 0 && p.z() < 3;
        }
    }

    record Heat(long from, long to, float temperature) {
    }

    private final FakeCustomBlocks blocks = new FakeCustomBlocks().known(FireboxDevice.ID, "tfp:crucible");
    private final MemoryStore store = new MemoryStore();
    private final TableCodec stacks = new TableCodec();
    private final Effects effects = new Effects();
    private final AtomicLong clock = new AtomicLong(START);
    private final List<Block> opened = new ArrayList<>();
    private final HeatConsumers consumers = new HeatConsumers();
    private final List<Heat> heat = new ArrayList<>();
    private DeviceService service;
    private FireboxDevice device;
    private DeviceKind<FireboxData> kind;
    private World world;
    private Block box;
    private PlayerMock player;

    @BeforeEach
    void setUp() {
        world = server.addSimpleWorld("firebox");
        service = new DeviceService(new DeviceKinds(), blocks, () -> store, clock::get, stacks, new FakeCustomItems(),
            Logger.getLogger("test"));
        device = new FireboxDevice(new FireboxDevice.Services(
            s -> switch (s.getType()) {
                case COAL -> Optional.of(COAL);
                case CHARCOAL -> Optional.of(CHARCOAL);
                default -> Optional.empty();
            },
            s -> s.getType() == Material.WATER_BUCKET ? FireboxEffects.Douse.WATER : FireboxEffects.Douse.NONE,
            b -> new Chamber(),
            consumers,
            b -> blocks.stateAt(b).map(BlockStateRef::id),
            effects,
            (p, b) -> opened.add(b)), stacks);
        kind = service.register(device.kind());
        box = world.getBlockAt(0, 64, 0);
        blocks.place(box, FireboxDevice.state(false), true);
        store.data.put(FakeCustomBlocks.at(box), new DataMap());
        player = server.addPlayer();
        player.teleport(new Location(world, 3.5, 64, 0.5));
        player.setGameMode(GameMode.SURVIVAL);
    }

    private DeviceRecord<FireboxData> record() {
        return kind.codec().decode(store.read(box).orElseThrow());
    }

    private boolean click(boolean sneak, ItemStack held) {
        player.setSneaking(sneak);
        player.getInventory().setItem(EquipmentSlot.HAND, held);
        return service.use(box, player, EquipmentSlot.HAND, held == null ? ItemStack.empty() : held);
    }

    private void fill(Material... fuel) {
        service.edit(box, kind, (r, now) -> {
            DeviceRecord<FireboxData> next = r;
            for (int i = 0; i < fuel.length; i++) {
                next = next.withSlot(i, stacks.encode(new ItemStack(fuel[i])));
            }
            return next;
        });
    }

    @Test
    void fuelGoesIntoTheFirstEmptySlotOneAtATime() {
        assertThat(click(false, new ItemStack(Material.COAL, 5))).isTrue();
        assertThat(player.getInventory().getItemInMainHand().getAmount()).isEqualTo(4);
        assertThat(click(false, new ItemStack(Material.CHARCOAL, 3))).isTrue();
        DeviceRecord<FireboxData> r = record();
        assertThat(stacks.decode(r.slot(0)).getType()).isEqualTo(Material.COAL);
        assertThat(stacks.decode(r.slot(1)).getType()).isEqualTo(Material.CHARCOAL);
        assertThat(stacks.decode(r.slot(1)).getAmount()).isEqualTo(1);
        assertThat(opened).isEmpty();
        assertThat(kind.slots()).isEqualTo(16);
        assertThat(kind.slotLimit(7)).isEqualTo(1);
        assertThat(kind.filter().canInsert(3, new ItemStack(Material.SAND))).isFalse();
    }

    @Test
    void aFullFireboxKeepsTheFuelInTheHand() {
        Material[] full = new Material[16];
        java.util.Arrays.fill(full, Material.COAL);
        fill(full);
        assertThat(click(false, new ItemStack(Material.COAL, 2))).isTrue();
        assertThat(player.getInventory().getItemInMainHand().getAmount()).isEqualTo(2);
    }

    /** TFC splits the stack whatever the game mode (FireboxBlock.useItemOn), creative included. */
    @Test
    void creativeAlsoPutsTheItemIn() {
        player.setGameMode(GameMode.CREATIVE);
        click(false, new ItemStack(Material.COAL, 2));
        assertThat(player.getInventory().getItemInMainHand().getAmount()).isEqualTo(1);
        assertThat(record().nonEmptySlots()).hasSize(1);
    }

    @Test
    void sneakWithAnEmptyHandTakesSlotZeroAndOtherClicksOpenTheScreen() {
        fill(Material.COAL, Material.CHARCOAL);
        assertThat(click(true, null)).isTrue();
        assertThat(effects.given).extracting(ItemStack::getType).containsExactly(Material.COAL);
        assertThat(stacks.decode(record().slot(0)).getType()).isEqualTo(Material.CHARCOAL); // cascaded down
        assertThat(record().slot(1).isEmpty()).isTrue();
        assertThat(click(false, null)).isTrue();
        assertThat(click(false, new ItemStack(Material.SAND))).isTrue();
        assertThat(opened).containsExactly(box, box);
    }

    @Test
    void aStrongFireStartLightsItAndItGoesOutColdWhenTheFuelRunsOut() {
        fill(Material.COAL);
        assertThat(device.lightAt(service, box)).isTrue();
        DeviceRecord<FireboxData> r = record();
        assertThat(r.state().lit()).isTrue();
        assertThat(r.state().burnTicks()).isEqualTo(2000);
        assertThat(r.nonEmptySlots()).isEmpty();
        assertThat(effects.shown).isTrue();
        assertThat(effects.callbacks).containsExactly(2000L);
        blocks.place(box, FireboxDevice.state(true), true);

        clock.set(START + 1000);
        service.neighbourChanged(box);
        assertThat(record().state().temperature()).isEqualTo(1000f);
        assertThat(record().state().heatingCount()).isEqualTo(27); // the kiln over it
        clock.set(START + 2500);
        service.neighbourChanged(box);
        FireboxData out = record().state();
        assertThat(out.lit()).isFalse();
        assertThat(out.temperature()).isZero();
        assertThat(effects.shown).isFalse();
        assertThat(device.lightAt(service, box)).isFalse(); // nothing left to burn
    }

    @Test
    void aConsumerAboveGetsTheHeatAndKeepsTheFireboxRunning() {
        Block above = box.getRelative(BlockFace.UP);
        blocks.place(above, BlockStateRef.of("tfp:crucible"), true);
        consumers.register("tfp:crucible", (b, from, to, t) -> {
            assertThat(b).isEqualTo(above);
            heat.add(new Heat(from, to, t));
        });
        fill(Material.CHARCOAL);
        device.lightAt(service, box);
        assertThat(effects.callbacks).containsExactly(FireboxDevice.HEAT_PERIOD);
        clock.set(START + 1500);
        service.neighbourChanged(box);
        assertThat(heat).isNotEmpty();
        assertThat(heat.getFirst().from()).isEqualTo(START + 1);
        assertThat(heat.getLast().to()).isEqualTo(START + 1500);
        long ticks = heat.stream().mapToLong(h -> h.to() - h.from() + 1).sum();
        assertThat(ticks).isEqualTo(1500);
        assertThat(heat.getLast().temperature()).isEqualTo(1350f);
        assertThat(effects.callbacks.getLast()).isEqualTo(FireboxDevice.HEAT_PERIOD);
        // a snapshot through the screen link hands its catch-up's heat over too
        heat.clear();
        clock.set(START + 1510);
        FireboxLink link = new FireboxLink(service, device, scheduler(), box);
        assertThat(link.snapshot()).isPresent();
        assertThat(heat).containsExactly(new Heat(START + 1501, START + 1510, 1350f));
    }

    @Test
    void waterDousesAWarmFirebox() {
        fill(Material.COAL);
        device.lightAt(service, box);
        clock.set(START + 50);
        assertThat(click(false, new ItemStack(Material.WATER_BUCKET))).isTrue();
        assertThat(record().state().lit()).isFalse();
        assertThat(record().state().temperature()).isZero();
        assertThat(effects.doused).containsExactly(FireboxEffects.Douse.WATER);
        assertThat(opened).isEmpty();
    }

    @Test
    void theScreenCommitsFuelCascadesAndRefusesOtherItems() {
        FireboxLink link = new FireboxLink(service, device, scheduler(), box);
        List<ItemStack> slots = new ArrayList<>();
        for (int i = 0; i < FireboxSim.SLOTS; i++) {
            slots.add(i == 5 ? new ItemStack(Material.COAL) : ItemStack.empty());
        }
        FireboxLink.Snapshot after = link.commit(slots).orElseThrow();
        assertThat(after.slots().get(0).getType()).isEqualTo(Material.COAL);
        assertThat(after.slots().get(5).isEmpty()).isTrue();
        List<ItemStack> sand = new ArrayList<>(after.slots());
        sand.set(1, new ItemStack(Material.SAND));
        assertThat(link.commit(sand)).isEmpty();
        List<ItemStack> two = new ArrayList<>(after.slots());
        two.set(1, new ItemStack(Material.COAL, 2));
        assertThat(link.commit(two)).isEmpty();
        assertThat(record().nonEmptySlots()).hasSize(1);
    }

    private Scheduler scheduler() {
        return new Scheduler() {
            @Override
            public void region(Location location, Runnable task) {
                task.run();
            }

            @Override
            public void region(World w, int chunkX, int chunkZ, Runnable task) {
                task.run();
            }

            @Override
            public TaskHandle regionDelayed(World w, int chunkX, int chunkZ, long delayTicks, Runnable task) {
                return null;
            }

            @Override
            public TaskHandle regionRepeating(World w, int chunkX, int chunkZ, long initialDelayTicks, long periodTicks,
                    Runnable task) {
                return null;
            }

            @Override
            public boolean entity(org.bukkit.entity.Entity entity, Runnable task, Runnable retired) {
                task.run();
                return true;
            }

            @Override
            public boolean entityDelayed(org.bukkit.entity.Entity entity, long delayTicks, Runnable task, Runnable retired) {
                return false;
            }

            @Override
            public TaskHandle entityRepeating(org.bukkit.entity.Entity entity, long initialDelayTicks, long periodTicks,
                    Runnable task, Runnable retired) {
                return null;
            }

            @Override
            public void global(Runnable task) {
                task.run();
            }

            @Override
            public TaskHandle globalRepeating(long initialDelayTicks, long periodTicks, Runnable task) {
                return null;
            }

            @Override
            public void cpu(Runnable task) {
                task.run();
            }

            @Override
            public java.util.concurrent.Executor cpuExecutor() {
                return Runnable::run;
            }

            @Override
            public java.util.concurrent.CompletableFuture<Void> io(Runnable task) {
                task.run();
                return java.util.concurrent.CompletableFuture.completedFuture(null);
            }

            @Override
            public java.util.concurrent.Executor ioExecutor() {
                return Runnable::run;
            }

            @Override
            public boolean isOwner(World w, int chunkX, int chunkZ) {
                return true;
            }

            @Override
            public void shutdown() {
            }
        };
    }
}
