/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.pile;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.device.DeviceRecord;
import dev.tfp.core.device.ItemBlob;
import dev.tfp.core.persist.DataMap;
import dev.tfp.core.pile.PileKind;
import dev.tfp.paper.device.DeviceKinds;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.device.DeviceStore;
import dev.tfp.paper.device.StackCodec;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.integration.CustomItems;
import dev.tfp.paper.integration.FakeCustomBlocks;
import dev.tfp.paper.integration.FakeCustomItems;
import dev.tfp.paper.scheduler.Scheduler;
import dev.tfp.paper.testing.MockServerTest;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
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
import org.bukkit.entity.Item;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * PAR-METAL-16 on MockBukkit with fakes of the CraftEngine adapter, a memory device store and a queued region scheduler: the
 * sneak use of an ingot (a new pile, an ingot into a pile, the column climb, a pile of the other kind), the plain use that takes
 * the top ingot, the support rule, the contents that pop out, creative and adventure.
 *
 * <p>Stand-ins: iron ingot = {@code c:ingots} (metal wrought_iron), gold ingot = {@code c:ingots} (gold), netherite ingot =
 * {@code c:double_ingots} (a double ingot of bronze), stone = a sturdy base.
 */
class PileMockBukkitTest extends MockServerTest {
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

    /** CraftEngine stand-in: a pile is a daylight detector; a removal runs the device removal. */
    final class Blocks implements CustomBlocks {
        final FakeCustomBlocks fake = new FakeCustomBlocks().known(PileService.id(PileKind.INGOT), PileService.id(PileKind.DOUBLE_INGOT));

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
            if (!fake.exists(state.id())) {
                return false;
            }
            fake.place(block, state, applyPhysics);
            block.setType(Material.DAYLIGHT_DETECTOR);
            store.data.put(FakeCustomBlocks.at(block), devices.created(state.id()).orElseThrow());
            return true;
        }

        @Override
        public boolean remove(Block block) {
            Optional<BlockStateRef> was = fake.stateAt(block);
            if (was.isEmpty() || !fake.remove(block)) {
                return false;
            }
            block.setType(Material.AIR);
            DataMap data = store.data.remove(FakeCustomBlocks.at(block));
            devices.removed(block.getWorld(), block.getX(), block.getY(), block.getZ(), was.get().id(), data);
            return true;
        }

        @Override
        public void onChunkLoad(Consumer<Chunk> hook) {
        }

        @Override
        public void onChunkUnload(Consumer<Chunk> hook) {
        }
    }

    /** Region tasks wait in a queue (the region scheduler never runs a task inline). */
    static final class QueueScheduler implements Scheduler {
        final List<Runnable> queue = new ArrayList<>();

        void drain() {
            for (int i = 0; i < 100 && !queue.isEmpty(); i++) {
                List<Runnable> now = new ArrayList<>(queue);
                queue.clear();
                now.forEach(Runnable::run);
            }
        }

        @Override
        public void region(Location location, Runnable task) {
            queue.add(task);
        }

        @Override
        public void region(World world, int chunkX, int chunkZ, Runnable task) {
            queue.add(task);
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
            throw new UnsupportedOperationException();
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

    /** The custom ids of the stand-in items, by material. */
    static final class Ids implements CustomItems {
        @Override
        public boolean exists(String id) {
            return true;
        }

        @Override
        public Optional<ItemStack> create(String id, int amount) {
            return Optional.empty();
        }

        @Override
        public Optional<String> idOf(ItemStack stack) {
            return switch (stack.getType()) {
                case IRON_INGOT -> Optional.of("tfp:metal/ingot/wrought_iron");
                case GOLD_INGOT -> Optional.of("tfp:metal/ingot/gold");
                case NETHERITE_INGOT -> Optional.of("tfp:metal/double_ingot/bronze");
                default -> Optional.empty();
            };
        }
    }

    final MemoryStore store = new MemoryStore();
    final TableCodec stacks = new TableCodec();
    final QueueScheduler scheduler = new QueueScheduler();
    final java.util.concurrent.atomic.AtomicLong nanos = new java.util.concurrent.atomic.AtomicLong(1_000_000_000L);
    DeviceService devices;
    Blocks blocks;
    PileService service;
    PileListener listener;
    World world;
    Block floor;
    PlayerMock player;

    static boolean tagged(ItemStack stack, String tag) {
        return switch (tag) {
            case "c:ingots" -> stack.getType() == Material.IRON_INGOT || stack.getType() == Material.GOLD_INGOT;
            case "c:double_ingots" -> stack.getType() == Material.NETHERITE_INGOT;
            default -> false;
        };
    }

    @BeforeEach
    void setUp() {
        world = server.addSimpleWorld("piles");
        blocks = new Blocks();
        devices = new DeviceService(new DeviceKinds(), blocks, () -> store, nanos::get, stacks, new FakeCustomItems(),
            Logger.getLogger("test"));
        service = new PileService(blocks, new Ids(), () -> devices, scheduler, (block, state) -> blocks.fake.place(block, state, false),
            stacks, nanos::get);
        for (PileKind kind : PileKind.values()) {
            service.bind(kind, devices.register(PileDevice.kind(kind, service)));
        }
        listener = new PileListener(service, PileMockBukkitTest::tagged);
        floor = world.getBlockAt(10, 63, 4);
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                world.getBlockAt(10 + dx, 63, 4 + dz).setType(Material.STONE);
            }
        }
        player = server.addPlayer();
        player.teleport(new Location(world, 10.5, 65, 8.5));
        player.setGameMode(GameMode.SURVIVAL);
        player.setSneaking(true);
    }

    Block pileBlock(int dy) {
        return world.getBlockAt(10, 64 + dy, 4);
    }

    void hold(Material type, int amount) {
        player.getInventory().setItem(EquipmentSlot.HAND, type == Material.AIR ? null : new ItemStack(type, amount));
    }

    ItemStack inHand() {
        return player.getInventory().getItem(EquipmentSlot.HAND);
    }

    PlayerInteractEvent click(Block block) {
        ItemStack held = inHand();
        PlayerInteractEvent e = new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK,
            StackCodec.isEmpty(held) ? null : held, block, BlockFace.UP, EquipmentSlot.HAND, new Vector(0.5, 1, 0.5));
        listener.onInteract(e);
        scheduler.drain();
        return e;
    }

    boolean use(Block pile) {
        boolean used = devices.use(pile, player, EquipmentSlot.HAND, inHand());
        scheduler.drain();
        return used;
    }

    static boolean denied(PlayerInteractEvent e) {
        return e.useInteractedBlock() == Event.Result.DENY && e.useItemInHand() == Event.Result.DENY;
    }

    int count(Block block) {
        return service.countOf(block);
    }

    DeviceRecord<Boolean> record(Block block, PileKind kind) {
        return devices.edit(block, service.device(kind), (r, n) -> r).orElseThrow();
    }

    /** A pile of {@code kind} with {@code n} iron ingots (double: netherite) standing on {@code y} above the floor. */
    Block fill(int dy, PileKind kind, int n, Material... cycle) {
        Block b = pileBlock(dy);
        if (dy > 0) {
            b.getRelative(BlockFace.DOWN).setType(Material.DAYLIGHT_DETECTOR);
        }
        assertThat(blocks.place(b, BlockStateRef.of(PileService.id(kind)).with("count", Integer.toString(n)), false)).isTrue();
        List<ItemStack> ingots = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            ingots.add(new ItemStack(cycle[i % cycle.length]));
        }
        service.fill(b, kind, ingots);
        return b;
    }

    int dropped() {
        return world.getEntitiesByClass(Item.class).stream().mapToInt(i -> i.getItemStack().getAmount()).sum();
    }

    // ---- adding ---------------------------------------------------------------------------------------------

    @Test
    void aSneakUseOfAnIngotOnTheGroundPlacesAPileOfOneAndUsesTheIngot() {
        hold(Material.IRON_INGOT, 5);
        PlayerInteractEvent e = click(floor);
        assertThat(denied(e)).isTrue();
        Block pile = pileBlock(0);
        assertThat(service.kindAt(pile)).contains(PileKind.INGOT);
        assertThat(count(pile)).isEqualTo(1);
        assertThat(service.filled(record(pile, PileKind.INGOT))).isEqualTo(1);
        assertThat(inHand().getAmount()).isEqualTo(4);
    }

    @Test
    void aSneakUseOnAPileAddsOneIngotAndTheCountFollowsTheSlots() {
        hold(Material.IRON_INGOT, 20);
        click(floor);
        Block pile = pileBlock(0);
        for (int i = 0; i < 9; i++) {
            assertThat(denied(click(pile))).isTrue();
        }
        assertThat(count(pile)).isEqualTo(10);
        assertThat(service.filled(record(pile, PileKind.INGOT))).isEqualTo(10);
        assertThat(inHand().getAmount()).isEqualTo(10);
    }

    @Test
    void aFullPileTakesTheNextIngotInANewPileOnTop() {
        Block pile = fill(0, PileKind.INGOT, 64, Material.IRON_INGOT);
        hold(Material.GOLD_INGOT, 3);
        assertThat(denied(click(pile))).isTrue();
        Block top = pileBlock(1);
        assertThat(service.kindAt(top)).contains(PileKind.INGOT);
        assertThat(count(top)).isEqualTo(1);
        assertThat(count(pile)).isEqualTo(64);
        // the next one goes into the pile on top, found by climbing the column
        click(pile);
        assertThat(count(top)).isEqualTo(2);
        assertThat(service.metals(record(top, PileKind.INGOT))).containsExactly("gold", "gold");
    }

    @Test
    void aColumnBlockedByAnotherBlockFailsAndKeepsTheIngot() {
        Block pile = fill(0, PileKind.INGOT, 64, Material.IRON_INGOT);
        pileBlock(1).setType(Material.STONE);
        hold(Material.GOLD_INGOT, 3);
        PlayerInteractEvent e = click(pile);
        assertThat(denied(e)).isTrue();
        assertThat(inHand().getAmount()).isEqualTo(3);
        assertThat(count(pile)).isEqualTo(64);
    }

    @Test
    void aDoubleIngotMakesADoublePileAndASingleOnTheSidePlacesItNextTo() {
        hold(Material.NETHERITE_INGOT, 4);
        click(floor);
        Block dbl = pileBlock(0);
        assertThat(service.kindAt(dbl)).contains(PileKind.DOUBLE_INGOT);
        // a single ingot on a double pile is no pile of its kind: it is placed on the clicked face (on top of the pile)
        hold(Material.IRON_INGOT, 2);
        click(dbl);
        assertThat(service.kindAt(pileBlock(1))).isEmpty(); // a double pile has no sturdy top: TFC canSurvive fails
        assertThat(inHand().getAmount()).isEqualTo(2);
        // a double ingot on the double pile adds
        hold(Material.NETHERITE_INGOT, 4);
        click(dbl);
        assertThat(count(dbl)).isEqualTo(2);
        assertThat(service.metals(record(dbl, PileKind.DOUBLE_INGOT))).containsExactly("bronze", "bronze");
    }

    @Test
    void withoutSneakingOrWithAnythingButAnIngotTheListenerPasses() {
        hold(Material.IRON_INGOT, 2);
        player.setSneaking(false);
        assertThat(denied(click(floor))).isFalse();
        player.setSneaking(true);
        hold(Material.STICK, 2);
        assertThat(denied(click(floor))).isFalse();
        assertThat(service.kindAt(pileBlock(0))).isEmpty();
    }

    @Test
    void adventureCannotBuildAndSpectatorNeither() {
        hold(Material.IRON_INGOT, 2);
        player.setGameMode(GameMode.ADVENTURE);
        assertThat(denied(click(floor))).isFalse();
        player.setGameMode(GameMode.SPECTATOR);
        assertThat(denied(click(floor))).isFalse();
        assertThat(service.kindAt(pileBlock(0))).isEmpty();
        assertThat(inHand().getAmount()).isEqualTo(2);
    }

    @Test
    void creativeKeepsTheStackWhenPlacingButSpendsAnIngotAddedToAPile() {
        player.setGameMode(GameMode.CREATIVE);
        hold(Material.IRON_INGOT, 5);
        click(floor);
        assertThat(inHand().getAmount()).isEqualTo(5);
        click(pileBlock(0));
        assertThat(count(pileBlock(0))).isEqualTo(2);
        assertThat(inHand().getAmount()).isEqualTo(4); // TFC stack.split(1)
    }

    @Test
    void aPileDoesNotGoWhereAnEntityStands() {
        hold(Material.IRON_INGOT, 2);
        world.spawn(new Location(world, 10.5, 64, 4.5), org.bukkit.entity.Cow.class);
        click(floor);
        assertThat(service.kindAt(pileBlock(0))).isEmpty();
        assertThat(inHand().getAmount()).isEqualTo(2);
    }

    // ---- removing -------------------------------------------------------------------------------------------

    @Test
    void aPlainUseTakesTheTopIngotAndTheLastOneRemovesTheBlock() {
        Block pile = fill(0, PileKind.INGOT, 3, Material.IRON_INGOT, Material.GOLD_INGOT);
        player.setSneaking(false);
        hold(Material.AIR, 0);
        assertThat(use(pile)).isTrue();
        assertThat(count(pile)).isEqualTo(2);
        assertThat(player.getInventory().contains(Material.IRON_INGOT)).isTrue(); // slot 2 was iron (0 iron, 1 gold, 2 iron)
        assertThat(use(pile)).isTrue();
        assertThat(player.getInventory().contains(Material.GOLD_INGOT)).isTrue();
        assertThat(count(pile)).isEqualTo(1);
        assertThat(use(pile)).isTrue();
        assertThat(service.kindAt(pile)).isEmpty();
        assertThat(pile.getType()).isEqualTo(Material.AIR);
        assertThat(dropped()).isZero();
    }

    @Test
    void aPlainUseWithAnyItemInHandTakesAnIngotToo() {
        Block pile = fill(0, PileKind.INGOT, 2, Material.IRON_INGOT);
        player.setSneaking(false);
        hold(Material.STICK, 1);
        assertThat(use(pile)).isTrue();
        assertThat(count(pile)).isEqualTo(1);
    }

    @Test
    void aSneakingUseOfAnEmptyHandDoesNothing() {
        Block pile = fill(0, PileKind.INGOT, 2, Material.IRON_INGOT);
        hold(Material.AIR, 0);
        assertThat(use(pile)).isFalse();
        assertThat(count(pile)).isEqualTo(2);
    }

    @Test
    void aPlainUseOnAStackTakesTheIngotFromTheTopPileOfTheColumn() {
        Block bottom = fill(0, PileKind.INGOT, 64, Material.IRON_INGOT);
        Block top = fill(1, PileKind.INGOT, 2, Material.GOLD_INGOT);
        player.setSneaking(false);
        hold(Material.AIR, 0);
        assertThat(use(bottom)).isTrue();
        assertThat(count(top)).isEqualTo(1);
        assertThat(count(bottom)).isEqualTo(64);
        assertThat(player.getInventory().contains(Material.GOLD_INGOT)).isTrue();
        use(bottom);
        assertThat(service.kindAt(top)).isEmpty();
        assertThat(count(bottom)).isEqualTo(64);
    }

    @Test
    void creativeRemovesTheIngotButIsNotGivenIt() {
        Block pile = fill(0, PileKind.INGOT, 2, Material.IRON_INGOT);
        player.setSneaking(false);
        player.setGameMode(GameMode.CREATIVE);
        hold(Material.AIR, 0);
        assertThat(use(pile)).isTrue();
        assertThat(count(pile)).isEqualTo(1);
        assertThat(player.getInventory().contains(Material.IRON_INGOT)).isFalse();
    }

    // ---- breaking, support ----------------------------------------------------------------------------------

    @Test
    void aBrokenPileDropsEveryIngotAndItsDisplaysGoWithIt() {
        Block pile = fill(0, PileKind.INGOT, 5, Material.IRON_INGOT);
        service.sync(pile);
        assertThat(PileDisplays.signatures(pile)).hasSize(5);
        blocks.remove(pile);
        scheduler.drain();
        assertThat(dropped()).isEqualTo(5);
        assertThat(PileDisplays.signatures(pile)).isEmpty();
    }

    @Test
    void aPileBrokenByACreativePlayerVoidsItsIngots() {
        Block pile = fill(0, PileKind.INGOT, 5, Material.IRON_INGOT);
        service.markVoid(pile);
        blocks.remove(pile);
        scheduler.drain();
        assertThat(dropped()).isZero();
    }

    @Test
    void aStaleCreativeMarkDoesNotVoidALaterBreak() {
        Block pile = fill(0, PileKind.INGOT, 3, Material.IRON_INGOT);
        service.markVoid(pile);
        nanos.addAndGet(PileService.VOID_MARK_NANOS + 1);
        blocks.remove(pile);
        assertThat(dropped()).isEqualTo(3);
    }

    @Test
    void aPileWhoseSupportWentPopsOffButOneOnAnotherPileStays() {
        Block bottom = fill(0, PileKind.INGOT, 64, Material.IRON_INGOT);
        Block top = fill(1, PileKind.INGOT, 4, Material.IRON_INGOT);
        service.checkSupport(top);
        service.checkSupport(bottom);
        scheduler.drain();
        assertThat(service.kindAt(top)).isPresent();
        floor.setType(Material.AIR);
        service.checkSupport(bottom);
        scheduler.drain();
        assertThat(service.kindAt(bottom)).isEmpty();
        assertThat(dropped()).isEqualTo(64);
        service.checkSupport(top);
        scheduler.drain();
        assertThat(service.kindAt(top)).isEmpty();
        assertThat(dropped()).isEqualTo(68);
    }

    @Test
    void aDoublePileOnASinglePileHasNoSupport() {
        Block single = fill(0, PileKind.INGOT, 64, Material.IRON_INGOT);
        assertThat(service.supported(single.getRelative(BlockFace.UP), PileKind.DOUBLE_INGOT)).isFalse();
        assertThat(service.supported(single.getRelative(BlockFace.UP), PileKind.INGOT)).isTrue();
    }

    // ---- the displays ---------------------------------------------------------------------------------------

    @Test
    void theDisplaysAreOnePerIngotAndOneForAFullLayerOfOneMetalAndOnlyTheChangedOnesAreTouched() {
        Block pile = fill(0, PileKind.INGOT, 11, Material.IRON_INGOT);
        service.sync(pile);
        // layer 1: 8 irons = 1 display; layer 2: 3 irons = 3 displays
        assertThat(PileDisplays.signatures(pile)).containsExactlyInAnyOrder("L:0:wrought_iron", "S:8:wrought_iron",
            "S:9:wrought_iron", "S:10:wrought_iron");
        List<org.bukkit.entity.ItemDisplay> before = world.getEntitiesByClass(org.bukkit.entity.ItemDisplay.class).stream().toList();
        hold(Material.GOLD_INGOT, 1);
        click(pile);
        assertThat(PileDisplays.signatures(pile)).contains("L:0:wrought_iron", "S:11:gold").hasSize(5);
        assertThat(world.getEntitiesByClass(org.bukkit.entity.ItemDisplay.class)).containsAll(before);
    }

    @Test
    void aMixedLayerIsDrawnIngotByIngot() {
        Block pile = fill(0, PileKind.INGOT, 8, Material.IRON_INGOT, Material.GOLD_INGOT);
        service.sync(pile);
        assertThat(PileDisplays.signatures(pile)).hasSize(8).allMatch(s -> s.startsWith("S:"));
    }
}
