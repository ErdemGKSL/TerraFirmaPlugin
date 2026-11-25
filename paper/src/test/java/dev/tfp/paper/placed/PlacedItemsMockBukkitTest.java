/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.placed;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.device.DeviceRecord;
import dev.tfp.core.device.ItemBlob;
import dev.tfp.core.item.ContainerRules.PlacedSlot;
import dev.tfp.core.neighbour.Neighbourhood;
import dev.tfp.core.persist.DataMap;
import dev.tfp.core.placed.Gesture;
import dev.tfp.core.placed.PlacedState;
import dev.tfp.paper.device.DeviceKind;
import dev.tfp.paper.device.DeviceKinds;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.device.DeviceStore;
import dev.tfp.paper.device.StackCodec;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.integration.FakeCustomBlocks;
import dev.tfp.paper.integration.FakeCustomItems;
import dev.tfp.paper.scheduler.Scheduler;
import dev.tfp.paper.testing.MockServerTest;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.logging.Logger;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * PAR-POT-01 on MockBukkit, with fakes of the CraftEngine adapter, a memory device store and a fake millisecond clock:
 * placement and take-back per quadrant, the size limits, the hold gesture (deny on every hold event, one placement after
 * 1 s), action first (block items and tools never start a hold), the deferred tap of a vessel on release, the sneak
 * double tap and the support rule.
 *
 * <p>Stand-ins: stone = a sturdy block, iron ingot / stick = small items, saddle = a very large item, elytra = too large,
 * bowl = a vessel (deferred tap), iron hoe = a tool with an action, cobblestone = a block item.
 */
class PlacedItemsMockBukkitTest extends MockServerTest {
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

    /** CraftEngine stand-in: places the custom block over a real server block and creates/removes its block entity. */
    final class Blocks implements CustomBlocks {
        final FakeCustomBlocks fake = new FakeCustomBlocks().known(PlacedItemDevice.ID);

        @Override
        public boolean exists(String id) {
            return fake.exists(id);
        }

        @Override
        public Optional<BlockStateRef> stateAt(Block block) {
            return fake.stateAt(block);
        }

        @Override
        public boolean place(Block block, BlockStateRef state, boolean applyPhysics) {
            if (!fake.place(block, state, applyPhysics)) {
                return false;
            }
            block.setType(Material.HEAVY_WEIGHTED_PRESSURE_PLATE); // the server block under CraftEngine's visual
            store.data.put(FakeCustomBlocks.at(block), devices.created(state.id()).orElseThrow());
            return true;
        }

        @Override
        public boolean remove(Block block) {
            if (!fake.remove(block)) {
                return false;
            }
            block.setType(Material.AIR);
            DataMap data = store.data.remove(FakeCustomBlocks.at(block));
            devices.removed(block.getWorld(), block.getX(), block.getY(), block.getZ(), PlacedItemDevice.ID, data);
            return true;
        }

        @Override
        public void onChunkLoad(Consumer<Chunk> hook) {
        }

        @Override
        public void onChunkUnload(Consumer<Chunk> hook) {
        }
    }

    /** Records what would be drawn. */
    static final class Effects implements PlacedItemEffects {
        final Map<String, List<ItemStack>> shown = new ConcurrentHashMap<>();
        int sounds;

        @Override
        public void show(Block block, PlacedState state, List<ItemStack> slots) {
            shown.put(PaperPlacedItemEffects.marker(block, 0), List.copyOf(slots));
        }

        @Override
        public void hide(Block block) {
            shown.remove(PaperPlacedItemEffects.marker(block, 0));
        }

        @Override
        public void placed(Block block) {
            sounds++;
        }
    }

    /** The player's thread: repeating entity tasks are run by the test. */
    static final class TestScheduler implements Scheduler {
        final List<Runnable> repeating = new ArrayList<>();

        @Override
        public void region(Location location, Runnable task) {
            task.run();
        }

        @Override
        public void region(World world, int chunkX, int chunkZ, Runnable task) {
            task.run();
        }

        @Override
        public TaskHandle regionDelayed(World world, int chunkX, int chunkZ, long delayTicks, Runnable task) {
            throw new UnsupportedOperationException();
        }

        @Override
        public TaskHandle regionRepeating(World world, int chunkX, int chunkZ, long initialDelayTicks, long periodTicks,
                Runnable task) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean entity(Entity entity, Runnable task, Runnable retired) {
            task.run();
            return true;
        }

        @Override
        public boolean entityDelayed(Entity entity, long delayTicks, Runnable task, Runnable retired) {
            throw new UnsupportedOperationException();
        }

        @Override
        public TaskHandle entityRepeating(Entity entity, long initialDelayTicks, long periodTicks, Runnable task,
                Runnable retired) {
            repeating.add(task);
            boolean[] cancelled = {false};
            return new TaskHandle() {
                @Override
                public void cancel() {
                    cancelled[0] = true;
                    repeating.remove(task);
                }

                @Override
                public boolean isCancelled() {
                    return cancelled[0];
                }
            };
        }

        void runRepeating() {
            new ArrayList<>(repeating).forEach(Runnable::run);
        }

        @Override
        public void global(Runnable task) {
            task.run();
        }

        @Override
        public TaskHandle globalRepeating(long initialDelayTicks, long periodTicks, Runnable task) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void cpu(Runnable task) {
            task.run();
        }

        @Override
        public Executor cpuExecutor() {
            return Runnable::run;
        }

        @Override
        public CompletableFuture<Void> io(Runnable task) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Executor ioExecutor() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isOwner(World world, int chunkX, int chunkZ) {
            return true;
        }

        @Override
        public void shutdown() {
        }
    }

    /** Captures the synthetic right-click a deferred tap replays. */
    static final class TapCatcher implements Listener {
        final List<PlayerInteractEvent> seen = new ArrayList<>();

        @EventHandler
        public void on(PlayerInteractEvent e) {
            seen.add(e);
        }
    }

    final MemoryStore store = new MemoryStore();
    final TableCodec stacks = new TableCodec();
    final Effects effects = new Effects();
    final TestScheduler scheduler = new TestScheduler();
    final AtomicLong clock = new AtomicLong(1_000_000);
    long tick = 1000;
    DeviceService devices;
    Blocks blocks;
    DeviceKind<PlacedState> kind;
    PlacedItems placed;
    World world;
    Block stone;
    Block above;
    PlayerMock player;
    boolean[] support = {true, true, true, true};

    static PlacedSlot size(ItemStack stack) {
        return switch (stack.getType()) {
            case SADDLE -> PlacedSlot.SINGLE;
            case ELYTRA -> PlacedSlot.NONE;
            default -> PlacedSlot.GROUP;
        };
    }

    @BeforeEach
    void setUp() {
        world = server.addSimpleWorld("placed");
        blocks = new Blocks();
        devices = new DeviceService(new DeviceKinds(), blocks, () -> store, new AtomicLong(100)::get, stacks,
            new FakeCustomItems(), Logger.getLogger("test"));
        kind = devices.register(PlacedItemDevice.kind(effects));
        placed = new PlacedItems(blocks, devices, kind, PlacedItemsMockBukkitTest::size, below -> support.clone(), effects);
        stone = world.getBlockAt(10, 64, 4);
        stone.setType(Material.STONE);
        above = stone.getRelative(BlockFace.UP);
        player = server.addPlayer();
        player.teleport(new Location(world, 10.5, 65, 6.5));
    }

    PlacedItemListener listener(Gesture gesture) {
        UseClassifier classifier = new UseClassifier(s -> Optional.empty(), blocks::exists,
            (s, tag) -> tag.equals("minecraft:hoes") && s.getType() == Material.IRON_HOE, block -> false);
        return new PlacedItemListener(placed, classifier, s -> s.getType() == Material.BOWL, scheduler, gesture, clock::get,
            p -> tick);
    }

    private void hold(Material type, int amount) {
        player.getInventory().setItem(EquipmentSlot.HAND, new ItemStack(type, amount));
    }

    private ItemStack inHand() {
        return player.getInventory().getItem(EquipmentSlot.HAND);
    }

    @SuppressWarnings("unchecked")
    private DeviceRecord<PlacedState> record(Block block) {
        return kind.codec().decode(store.read(block).orElseThrow());
    }

    private ItemStack slot(Block block, int slot) {
        return stacks.decode(record(block).slot(slot));
    }

    /** One right-click event through both listener phases, at {@code atMs} on the fake clock (a tick per 50 ms). */
    private PlayerInteractEvent click(PlacedItemListener l, long atMs, Block block, BlockFace face, double x, double z) {
        clock.set(1_000_000 + atMs);
        tick = 1000 + atMs / 50;
        Vector pos = new Vector(x - block.getX(), face == BlockFace.UP ? 1 : 0.5, z - block.getZ());
        PlayerInteractEvent e = new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, inHand().getType().isAir() ? null
            : inHand(), block, face, EquipmentSlot.HAND, pos);
        l.onInteract(e);
        l.onInteractDone(e);
        return e;
    }

    private static boolean denied(PlayerInteractEvent e) {
        return e.useItemInHand() == Event.Result.DENY && e.useInteractedBlock() == Event.Result.DENY;
    }

    // ---- place and take -----------------------------------------------------------------------------

    @Test
    void placeAndTakePerQuadrant() {
        hold(Material.IRON_INGOT, 8);
        // NW quadrant first (slot 3), then NE (2), SW (1), SE (0)
        assertThat(placed.place(player, EquipmentSlot.HAND, stone, 10.25, 4.25)).isTrue();
        assertThat(blocks.stateAt(above)).contains(BlockStateRef.of(PlacedItemDevice.ID));
        assertThat(slot(above, 3).getType()).isEqualTo(Material.IRON_INGOT);
        assertThat(slot(above, 3).getAmount()).isEqualTo(1);
        assertThat(inHand().getAmount()).isEqualTo(7);
        assertThat(placed.insert(player, EquipmentSlot.HAND, above, 10.75, 4.25)).isTrue();
        hold(Material.STICK, 2);
        assertThat(placed.insert(player, EquipmentSlot.HAND, above, 10.25, 4.75)).isTrue();
        assertThat(placed.insert(player, EquipmentSlot.HAND, above, 10.75, 4.75)).isTrue();
        assertThat(placed.insert(player, EquipmentSlot.HAND, above, 10.75, 4.75)).as("occupied").isFalse();
        assertThat(List.of(slot(above, 0).getType(), slot(above, 1).getType(), slot(above, 2).getType(),
            slot(above, 3).getType())).containsExactly(Material.STICK, Material.STICK, Material.IRON_INGOT, Material.IRON_INGOT);
        assertThat(effects.shown.get(PaperPlacedItemEffects.marker(above, 0))).hasSize(4);
        assertThat(effects.sounds).isEqualTo(4);

        // empty hand: each quadrant gives its item back; the last take removes the block
        player.getInventory().clear();
        player.getInventory().setHeldItemSlot(8); // the returned items fill slot 0 onwards; the hand stays empty
        PlacedItemListener l = listener(Gesture.BOTH);
        PlayerInteractEvent e = click(l, 0, above, BlockFace.UP, 10.75, 4.75);
        assertThat(denied(e)).isTrue();
        assertThat(player.getInventory().contains(Material.STICK, 1)).isTrue();
        click(l, 100, above, BlockFace.UP, 10.25, 4.75);
        click(l, 200, above, BlockFace.UP, 10.75, 4.25);
        assertThat(blocks.stateAt(above)).isPresent();
        click(l, 300, above, BlockFace.UP, 10.25, 4.25);
        assertThat(player.getInventory().contains(Material.STICK, 2)).isTrue();
        assertThat(player.getInventory().contains(Material.IRON_INGOT, 2)).isTrue();
        assertThat(blocks.stateAt(above)).as("empty block removed").isEmpty();
        assertThat(above.getType()).isEqualTo(Material.AIR);
        assertThat(effects.shown).isEmpty();
    }

    @Test
    void sneakRightClickWithAnItemTakesBackAsTfc() {
        hold(Material.IRON_INGOT, 3);
        placed.place(player, EquipmentSlot.HAND, stone, 10.75, 4.75);
        hold(Material.STICK, 1);
        player.setSneaking(true);
        PlayerInteractEvent e = click(listener(Gesture.BOTH), 0, above, BlockFace.UP, 10.75, 4.75);
        assertThat(denied(e)).isTrue();
        assertThat(player.getInventory().contains(Material.IRON_INGOT, 1)).isTrue();
        assertThat(inHand().getType()).isEqualTo(Material.STICK);
    }

    @Test
    void sizeLimits() {
        hold(Material.ELYTRA, 1);
        assertThat(placed.place(player, EquipmentSlot.HAND, stone, 10.25, 4.25)).as("above maxPlacedLargeItemSize").isFalse();
        assertThat(blocks.stateAt(above)).isEmpty();
        support = new boolean[] {true, true, false, true};
        hold(Material.SADDLE, 1);
        assertThat(placed.place(player, EquipmentSlot.HAND, stone, 10.25, 4.25)).as("a large item needs full support")
            .isFalse();
        support = new boolean[] {true, true, true, true};
        assertThat(placed.place(player, EquipmentSlot.HAND, stone, 10.25, 4.25)).isTrue();
        assertThat(record(above).state().large()).isTrue();
        assertThat(slot(above, 0).getType()).as("the large slot").isEqualTo(Material.SADDLE);
        hold(Material.IRON_INGOT, 1);
        assertThat(placed.insert(player, EquipmentSlot.HAND, above, 10.75, 4.25)).as("nothing joins a large item").isFalse();
        // taking the large item from any quadrant
        assertThat(placed.take(player, above, 10.75, 4.75)).isTrue();
        assertThat(player.getInventory().contains(Material.SADDLE)).isTrue();
        assertThat(blocks.stateAt(above)).isEmpty();
    }

    @Test
    void anUnsupportedQuadrantOrAFullSpaceRefuses() {
        hold(Material.IRON_INGOT, 1);
        support = new boolean[] {true, true, true, false};
        assertThat(placed.place(player, EquipmentSlot.HAND, stone, 10.25, 4.25)).isFalse();
        support = new boolean[] {true, true, true, true};
        above.setType(Material.TORCH);
        assertThat(placed.place(player, EquipmentSlot.HAND, stone, 10.25, 4.25)).isFalse();
        assertThat(inHand().getAmount()).isEqualTo(1);
    }

    @Test
    void creativeKeepsTheStack() {
        player.setGameMode(GameMode.CREATIVE);
        hold(Material.IRON_INGOT, 2);
        assertThat(placed.place(player, EquipmentSlot.HAND, stone, 10.25, 4.25)).isTrue();
        assertThat(inHand().getAmount()).isEqualTo(2);
    }

    @Test
    void breakingTheBlockDropsEveryItem() {
        hold(Material.IRON_INGOT, 4);
        placed.place(player, EquipmentSlot.HAND, stone, 10.25, 4.25);
        placed.insert(player, EquipmentSlot.HAND, above, 10.75, 4.75);
        blocks.remove(above);
        assertThat(world.getEntities().stream().filter(e -> e instanceof org.bukkit.entity.Item)
            .mapToInt(e -> ((org.bukkit.entity.Item) e).getItemStack().getAmount()).sum()).isEqualTo(2);
        assertThat(effects.shown).isEmpty();
    }

    // ---- the hold gesture ---------------------------------------------------------------------------

    @Test
    void holdingOneSecondPlacesOnceAndEveryHoldEventIsDenied() {
        PlacedItemListener l = listener(Gesture.BOTH);
        hold(Material.IRON_INGOT, 5);
        List<PlayerInteractEvent> events = new ArrayList<>();
        for (long t = 0; t <= 800; t += 200) {
            events.add(click(l, t, stone, BlockFace.UP, 10.25, 4.25));
        }
        assertThat(blocks.stateAt(above)).as("0.8 s: nothing").isEmpty();
        events.add(click(l, 1000, stone, BlockFace.UP, 10.25, 4.25));
        assertThat(blocks.stateAt(above)).as("placed at 1 s").isPresent();
        assertThat(slot(above, 3).getType()).isEqualTo(Material.IRON_INGOT);
        assertThat(inHand().getAmount()).isEqualTo(4);
        // still holding: the cursor now rests on the placed item; nothing more happens until a release
        for (long t = 1200; t <= 2400; t += 200) {
            events.add(click(l, t, above, BlockFace.UP, 10.25, 4.25));
        }
        assertThat(inHand().getAmount()).isEqualTo(4);
        assertThat(events.subList(1, events.size())).allMatch(PlacedItemsMockBukkitTest::denied);
        assertThat(events.getFirst().useItemInHand()).as("the first event of a plain item goes through").isNotEqualTo(
            Event.Result.DENY);
    }

    @Test
    void releasingEarlyPlacesNothingAndAnotherTargetRestarts() {
        PlacedItemListener l = listener(Gesture.BOTH);
        hold(Material.IRON_INGOT, 5);
        click(l, 0, stone, BlockFace.UP, 10.25, 4.25);
        click(l, 200, stone, BlockFace.UP, 10.25, 4.25);
        click(l, 400, stone, BlockFace.UP, 10.25, 4.25);
        Block other = world.getBlockAt(12, 64, 4);
        other.setType(Material.STONE);
        for (long t = 600; t <= 1400; t += 200) {
            click(l, t, other, BlockFace.UP, 12.25, 4.25);
        }
        assertThat(blocks.stateAt(above)).isEmpty();
        assertThat(blocks.stateAt(other.getRelative(BlockFace.UP))).as("0.8 s on the new target").isEmpty();
        click(l, 1600, other, BlockFace.UP, 12.25, 4.25);
        assertThat(blocks.stateAt(other.getRelative(BlockFace.UP))).isPresent();
    }

    @Test
    void aSideFaceNeverPlaces() {
        PlacedItemListener l = listener(Gesture.BOTH);
        hold(Material.IRON_INGOT, 5);
        for (long t = 0; t <= 2000; t += 200) {
            PlayerInteractEvent e = click(l, t, stone, BlockFace.NORTH, 10.5, 4.0);
            assertThat(denied(e)).isFalse();
        }
        assertThat(l.holding(player)).isFalse();
        assertThat(blocks.fake.placed()).isEmpty();
    }

    @Test
    void blockItemsAndToolsWithAnActionNeverStartAHold() {
        PlacedItemListener l = listener(Gesture.BOTH);
        for (Material m : List.of(Material.COBBLESTONE, Material.IRON_HOE, Material.WATER_BUCKET, Material.FLINT_AND_STEEL)) {
            hold(m, 1);
            for (long t = 0; t <= 2000; t += 200) {
                PlayerInteractEvent e = click(l, t, stone, BlockFace.UP, 10.25, 4.25);
                assertThat(denied(e)).as(m.name()).isFalse();
            }
            assertThat(l.holding(player)).as(m.name()).isFalse();
            assertThat(blocks.stateAt(above)).as(m.name()).isEmpty();
        }
    }

    @Test
    void anInteractableTargetActsFirst() {
        PlacedItemListener l = listener(Gesture.BOTH);
        stone.setType(Material.CRAFTING_TABLE);
        hold(Material.IRON_INGOT, 1);
        for (long t = 0; t <= 1400; t += 200) {
            assertThat(denied(click(l, t, stone, BlockFace.UP, 10.25, 4.25))).isFalse();
        }
        assertThat(blocks.stateAt(above)).isEmpty();
    }

    @Test
    void aClickAnotherListenerActedOnStartsNoHold() {
        // e.g. a stick that placed a twig: the first event was denied by its listener before ours saw it at MONITOR
        PlacedItemListener l = listener(Gesture.BOTH);
        hold(Material.STICK, 4);
        for (long t = 0; t <= 1400; t += 200) {
            clock.set(1_000_000 + t);
            tick = 1000 + t / 50;
            PlayerInteractEvent e = new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, inHand(), stone, BlockFace.UP,
                EquipmentSlot.HAND, new Vector(0.25, 1, 0.25));
            l.onInteract(e);
            e.setUseItemInHand(Event.Result.DENY); // the other listener
            l.onInteractDone(e);
        }
        assertThat(l.holding(player)).isFalse();
        assertThat(blocks.stateAt(above)).isEmpty();
    }

    @Test
    void aVesselTapOpensOnReleaseAndAHoldPlacesIt() {
        TapCatcher catcher = new TapCatcher();
        server.getPluginManager().registerEvents(catcher, plugin);
        PlacedItemListener l = listener(Gesture.BOTH);
        hold(Material.BOWL, 1);
        PlayerInteractEvent first = click(l, 0, stone, BlockFace.UP, 10.75, 4.25);
        assertThat(denied(first)).as("the tap waits for the release").isTrue();
        clock.set(1_000_000 + 250);
        scheduler.runRepeating();
        assertThat(catcher.seen).isEmpty();
        clock.set(1_000_000 + 350);
        scheduler.runRepeating();
        assertThat(catcher.seen).as("released: the tap runs").hasSize(1);
        PlayerInteractEvent tap = catcher.seen.getFirst();
        assertThat(tap.getClickedBlock()).isEqualTo(stone);
        assertThat(tap.getItem().getType()).isEqualTo(Material.BOWL);
        assertThat(scheduler.repeating).as("the release check stops with the hold").isEmpty();

        catcher.seen.clear();
        for (long t = 1000; t <= 2000; t += 200) {
            click(l, t, stone, BlockFace.UP, 10.75, 4.25);
        }
        assertThat(slot(above, 2).getType()).isEqualTo(Material.BOWL);
        clock.set(1_000_000 + 2400);
        scheduler.runRepeating();
        assertThat(catcher.seen).as("a completed hold has no tap").isEmpty();
    }

    @Test
    void theDoubleTapPlacesAtOnceAndHoldingDoesNothingWithoutTheHoldGesture() {
        PlacedItemListener l = listener(Gesture.SNEAK_DOUBLE_TAP);
        hold(Material.IRON_INGOT, 3);
        for (long t = 0; t <= 1600; t += 200) {
            assertThat(denied(click(l, t, stone, BlockFace.UP, 10.25, 4.25))).isFalse();
        }
        assertThat(blocks.stateAt(above)).isEmpty();
        clock.set(1_000_000 + 5000);
        l.onSneak(new PlayerToggleSneakEvent(player, true));
        clock.set(1_000_000 + 5200);
        l.onSneak(new PlayerToggleSneakEvent(player, true));
        PlayerInteractEvent e = click(l, 5400, stone, BlockFace.UP, 10.25, 4.25);
        assertThat(denied(e)).isTrue();
        assertThat(slot(above, 3).getType()).isEqualTo(Material.IRON_INGOT);
        assertThat(inHand().getAmount()).isEqualTo(2);
    }

    @Test
    void spectatorAndAdventureCannotPlaceAndQuitDropsTheHold() {
        PlacedItemListener l = listener(Gesture.BOTH);
        hold(Material.IRON_INGOT, 3);
        for (GameMode mode : List.of(GameMode.ADVENTURE, GameMode.SPECTATOR)) {
            player.setGameMode(mode);
            for (long t = 0; t <= 1400; t += 200) {
                click(l, t, stone, BlockFace.UP, 10.25, 4.25);
            }
            assertThat(blocks.stateAt(above)).as(mode.name()).isEmpty();
        }
        player.setGameMode(GameMode.SURVIVAL);
        click(l, 3000, stone, BlockFace.UP, 10.25, 4.25);
        click(l, 3200, stone, BlockFace.UP, 10.25, 4.25);
        assertThat(l.holding(player)).isTrue();
        l.onQuit(new PlayerQuitEvent(player, net.kyori.adventure.text.Component.empty(),
            PlayerQuitEvent.QuitReason.DISCONNECTED));
        assertThat(l.holding(player)).isFalse();
    }

    // ---- support --------------------------------------------------------------------------------------

    @Test
    void theSupportRuleRemovesTheBlockOverNothingSolid() {
        PlacedSupport rule = new PlacedSupport(PlacedSupport::solidId);
        BlockStateRef self = BlockStateRef.of(PlacedItemDevice.ID);
        for (String below : List.of("minecraft:stone", "tfp:rock/raw/granite")) {
            Neighbourhood<BlockStateRef> n = Neighbourhood.of((dx, dy, dz) -> dx == 0 && dz == 0 && dy == -1
                ? BlockStateRef.of(below) : dx == 0 && dy == 0 && dz == 0 ? self : BlockStateRef.of("minecraft:air"));
            assertThat(rule.decide(n)).as(below).isNull();
        }
        for (String below : List.of("minecraft:air", "minecraft:water", "minecraft:short_grass", PlacedItemDevice.ID)) {
            Neighbourhood<BlockStateRef> n = Neighbourhood.of((dx, dy, dz) -> dx == 0 && dz == 0 && dy == -1
                ? BlockStateRef.of(below) : dx == 0 && dy == 0 && dz == 0 ? self : BlockStateRef.of("minecraft:air"));
            assertThat(rule.decide(n)).as(below).isEqualTo(BlockStateRef.of("minecraft:air"));
        }
        assertThat(rule.blockIds()).isEqualTo(Set.of(PlacedItemDevice.ID));
    }
}
