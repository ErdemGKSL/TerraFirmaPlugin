/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.kiln;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.device.DeviceRecord;
import dev.tfp.core.device.ItemBlob;
import dev.tfp.core.heat.HeatState;
import dev.tfp.core.kiln.PitKilnFiring;
import dev.tfp.core.kiln.PitKilnLayers;
import dev.tfp.core.kiln.PitKilnRules;
import dev.tfp.core.kiln.PitKilnState;
import dev.tfp.core.persist.DataMap;
import dev.tfp.core.placed.PlacedState;
import dev.tfp.paper.device.DeviceKind;
import dev.tfp.paper.device.DeviceKinds;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.device.DeviceStore;
import dev.tfp.paper.device.StackCodec;
import dev.tfp.paper.heat.HeatPdc;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.integration.FakeCustomBlocks;
import dev.tfp.paper.integration.FakeCustomItems;
import dev.tfp.paper.placed.PlacedItemDevice;
import dev.tfp.paper.placed.PlacedItemEffects;
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
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
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
 * PAR-POT-03 on MockBukkit with fakes of the CraftEngine adapter, a memory device store, a queued region scheduler and a
 * calendar clock: straw on a placed item builds a kiln, the straw and log stages and their take-back, lighting (fire
 * start, fire above, diagonal kilns), the lazy firing (the clock, a calendar jump, an evaluation before the end), the
 * failures and the game-mode rules.
 *
 * <p>Stand-ins: wheat = straw (1), hay block = a 4-straw bundle (TFC thatch), oak log = a pit kiln log, clay ball = an
 * unfired item with a heating recipe at 1399 into a brick, iron ingot = an item that melts at 1000 (no item result),
 * stick = an item without heat.
 */
class PitKilnMockBukkitTest extends MockServerTest {
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

    /** CraftEngine stand-in: a placement over another custom block removes it first (its device removal runs). */
    final class Blocks implements CustomBlocks {
        final FakeCustomBlocks fake = new FakeCustomBlocks().known(PlacedItemDevice.ID, PitKilnDevice.ID);

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
            if (fake.stateAt(block).isPresent()) {
                remove(block);
            }
            fake.place(block, state, applyPhysics);
            block.setType(state.id().equals(PitKilnDevice.ID) ? Material.MUD : Material.HEAVY_WEIGHTED_PRESSURE_PLATE);
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

    /** Records the stage changes and sounds; the stage goes into the fake block state. */
    final class Effects implements KilnEffects {
        final List<Sound> sounds = new ArrayList<>();

        @Override
        public void stage(Block block, int stage, boolean physics) {
            blocks.fake.place(block, PitKilnDevice.state(stage), physics);
        }

        @Override
        public void sound(Block block, Sound sound) {
            sounds.add(sound);
        }

        @Override
        public void give(Player player, ItemStack stack) {
            player.getInventory().addItem(stack);
        }

        @Override
        public void spawn(Block block, ItemStack stack) {
            block.getWorld().dropItem(block.getLocation().add(0.5, 0.5, 0.5), stack);
        }
    }

    static final class Displays implements PlacedItemEffects {
        final Map<String, List<ItemStack>> shown = new ConcurrentHashMap<>();

        @Override
        public void show(Block block, PlacedState state, List<ItemStack> slots) {
            shown.put(key(block, 0), List.copyOf(slots));
        }

        @Override
        public void hide(Block block) {
            shown.remove(key(block, 0));
        }

        @Override
        public void placed(Block block) {
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

    /** Clay ball -> brick at 1399 (both heatable), sticks have no heat; temperatures as heat records. */
    static final class Items implements PitKilnFiring.Items<ItemStack> {
        @Override
        public boolean isEmpty(ItemStack stack) {
            return StackCodec.isEmpty(stack);
        }

        @Override
        public boolean heatable(ItemStack stack) {
            return stack.getType() == Material.CLAY_BALL || stack.getType() == Material.BRICK
                || stack.getType() == Material.IRON_INGOT;
        }

        @Override
        public float recipeTemperature(ItemStack stack) {
            return switch (stack.getType()) {
                case CLAY_BALL -> 1399f;
                case IRON_INGOT -> 1000f; // melts: no item result
                default -> Float.NaN;
            };
        }

        @Override
        public ItemStack withTemperature(ItemStack stack, float temperature, long tick) {
            ItemStack copy = stack.clone();
            HeatPdc.write(copy, HeatState.cold(tick).set(temperature, tick));
            return copy;
        }

        @Override
        public ItemStack convert(ItemStack stack) {
            if (stack.getType() == Material.IRON_INGOT) {
                return ItemStack.empty();
            }
            ItemStack out = new ItemStack(Material.BRICK, stack.getAmount());
            HeatPdc.read(stack).ifPresent(h -> HeatPdc.write(out, h));
            return out;
        }
    }

    /** Stone around, a sturdy base, the side marked by {@link #weakSide} is not sturdy. */
    final class Structure implements PitKilns.Structure {
        @Override
        public boolean valid(Block kiln) {
            return sidesSturdy(kiln) && baseSturdy(kiln) && !flammableSide;
        }

        @Override
        public boolean sidesSturdy(Block kiln) {
            for (BlockFace f : List.of(BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST)) {
                if (!kiln.getRelative(f).getType().isSolid()) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public boolean baseSturdy(Block kiln) {
            return kiln.getRelative(BlockFace.DOWN).getType().isSolid();
        }
    }

    final MemoryStore store = new MemoryStore();
    final TableCodec stacks = new TableCodec();
    final Displays displays = new Displays();
    final QueueScheduler scheduler = new QueueScheduler();
    final AtomicLong now = new AtomicLong(10_000);
    final KilnClock clock = new KilnClock();
    boolean flammableSide;
    Effects effects;
    DeviceService devices;
    Blocks blocks;
    DeviceKind<PlacedState> placedKind;
    DeviceKind<PitKilnState> kind;
    PitKilns kilns;
    PitKilnListener listener;
    World world;
    Block kiln;
    PlayerMock player;

    @BeforeEach
    void setUp() {
        world = server.addSimpleWorld("kiln");
        blocks = new Blocks();
        effects = new Effects();
        devices = new DeviceService(new DeviceKinds(), blocks, () -> store, now::get, stacks, new FakeCustomItems(),
            Logger.getLogger("test"));
        placedKind = devices.register(dev.tfp.paper.placed.PlacedItemDevice.kind(displays));
        kilns = new PitKilns(new PitKilns.Services(blocks, devices, () -> Optional.of(placedKind),
            s -> s.getType() == Material.HAY_BLOCK ? 4 : s.getType() == Material.WHEAT ? 1 : 0,
            s -> s.getType() == Material.OAK_LOG, new Items(), new Structure(), PitKilnRules.Config.DEFAULT, now::get,
            effects, displays, clock, scheduler));
        kind = devices.register(PitKilnDevice.kind(displays, kilns::later, clock));
        kilns.kind(kind);
        listener = new PitKilnListener(kilns, blocks, scheduler);
        kiln = pit(10, 64, 4);
        player = server.addPlayer();
        player.teleport(new Location(world, 10.5, 65, 6.5));
        player.setGameMode(GameMode.SURVIVAL);
    }

    /** A pit: stone below and on the four sides of {@code (x, y, z)}. */
    Block pit(int x, int y, int z) {
        Block b = world.getBlockAt(x, y, z);
        b.getRelative(BlockFace.DOWN).setType(Material.STONE);
        for (BlockFace f : List.of(BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST)) {
            b.getRelative(f).setType(Material.STONE);
        }
        b.getRelative(BlockFace.UP).setType(Material.AIR);
        return b;
    }

    /** A placed item block at {@code block} holding {@code items} in slots 0.. (TFC's state). */
    void placedItem(Block block, Material... items) {
        assertThat(blocks.place(block, BlockStateRef.of(PlacedItemDevice.ID), true)).isTrue();
        devices.edit(block, placedKind, (record, t) -> {
            DeviceRecord<PlacedState> r = record.withState(new PlacedState(false, 15f, 0f, 0f, 0f));
            for (int i = 0; i < items.length; i++) {
                r = r.withSlot(i, stacks.encode(new ItemStack(items[i])));
            }
            return r;
        });
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
        return e;
    }

    int stage(Block block) {
        return kilns.stageAt(block);
    }

    DeviceRecord<PitKilnState> record(Block block) {
        return kind.codec().decode(store.read(block).orElseThrow());
    }

    ItemStack slot(Block block, int slot) {
        return stacks.decode(record(block).slot(slot));
    }

    DeviceRecord<PlacedState> placedRecord(Block block) {
        return placedKind.codec().decode(store.read(block).orElseThrow());
    }

    /** Builds a full kiln (8 straw, 8 logs) holding a clay ball at {@code block}. */
    void fullKiln(Block block) {
        placedItem(block, Material.CLAY_BALL);
        hold(Material.WHEAT, 64);
        for (int i = 0; i < 8; i++) {
            assertThat(denied(click(block))).isTrue();
        }
        hold(Material.OAK_LOG, 64);
        for (int i = 0; i < 8; i++) {
            assertThat(denied(click(block))).isTrue();
        }
        assertThat(stage(block)).isEqualTo(PitKilnLayers.LOG_END);
    }

    static String key(Block block, int slot) {
        return block.getX() + "," + block.getY() + "," + block.getZ() + ":" + slot;
    }

    static boolean denied(PlayerInteractEvent e) {
        return e.useInteractedBlock() == Event.Result.DENY && e.useItemInHand() == Event.Result.DENY;
    }

    // ---- building and stages -----------------------------------------------------------------------------------

    @Test
    void strawOnAPlacedItemBuildsAKilnHoldingTheItems() {
        placedItem(kiln, Material.CLAY_BALL, Material.STICK);
        hold(Material.WHEAT, 5);
        PlayerInteractEvent e = click(kiln);
        assertThat(denied(e)).isTrue();
        assertThat(blocks.stateAt(kiln)).contains(PitKilnDevice.state(0));
        assertThat(slot(kiln, 0).getType()).isEqualTo(Material.CLAY_BALL);
        assertThat(slot(kiln, 1).getType()).isEqualTo(Material.STICK);
        assertThat(slot(kiln, PitKilnState.strawSlot(0)).getType()).isEqualTo(Material.WHEAT);
        assertThat(record(kiln).state().placed().rotation0()).isEqualTo(15f);
        assertThat(inHand().getAmount()).isEqualTo(4);
        assertThat(effects.sounds).containsExactly(KilnEffects.Sound.CONVERT);
        assertThat(displays.shown.get(key(kiln, 0))).hasSize(4);
        assertThat(world.getEntitiesByClass(Item.class)).as("nothing dropped by the conversion").isEmpty();
    }

    @Test
    void aBundleStartsAtStageThreeAndAnInvalidPitRefuses() {
        placedItem(kiln, Material.CLAY_BALL);
        kiln.getRelative(BlockFace.NORTH).setType(Material.AIR);
        hold(Material.HAY_BLOCK, 4);
        assertThat(denied(click(kiln))).as("no sturdy side").isFalse();
        assertThat(blocks.stateAt(kiln)).contains(BlockStateRef.of(PlacedItemDevice.ID));
        kiln.getRelative(BlockFace.NORTH).setType(Material.STONE);
        assertThat(denied(click(kiln))).isTrue();
        assertThat(stage(kiln)).isEqualTo(3);
        assertThat(slot(kiln, PitKilnState.strawSlot(3)).getType()).isEqualTo(Material.HAY_BLOCK);
        assertThat(denied(click(kiln))).as("3 + 4 = 7").isTrue();
        assertThat(stage(kiln)).isEqualTo(7);
        assertThat(denied(click(kiln))).as("no room for a bundle").isFalse();
    }

    @Test
    void strawThenLogsUpToTheFullKilnAndTakenBackInReverse() {
        placedItem(kiln, Material.CLAY_BALL);
        hold(Material.OAK_LOG, 4);
        assertThat(denied(click(kiln))).as("a log needs a kiln").isFalse();
        hold(Material.WHEAT, 64);
        click(kiln);
        hold(Material.OAK_LOG, 4);
        assertThat(denied(click(kiln))).as("logs only on full straw").isFalse();
        hold(Material.WHEAT, 64);
        for (int s = 1; s <= 7; s++) {
            assertThat(denied(click(kiln))).isTrue();
            assertThat(stage(kiln)).isEqualTo(s);
        }
        assertThat(denied(click(kiln))).as("straw stops at stage 7").isFalse();
        hold(Material.OAK_LOG, 64);
        for (int s = 8; s <= 15; s++) {
            assertThat(denied(click(kiln))).isTrue();
            assertThat(stage(kiln)).isEqualTo(s);
        }
        assertThat(denied(click(kiln))).as("eight logs").isFalse();
        assertThat(inHand().getAmount()).isEqualTo(56);
        assertThat(effects.sounds).contains(KilnEffects.Sound.LOG, KilnEffects.Sound.STRAW);
        assertThat(displays.shown).as("contents hidden above stage 9").isEmpty();

        player.getInventory().clear();
        player.getInventory().setHeldItemSlot(8);
        for (int s = 14; s >= 7; s--) {
            assertThat(denied(click(kiln))).isTrue();
            assertThat(stage(kiln)).isEqualTo(s);
        }
        assertThat(player.getInventory().contains(Material.OAK_LOG, 8)).isTrue();
        for (int s = 6; s >= 0; s--) {
            click(kiln);
            assertThat(stage(kiln)).isEqualTo(s);
        }
        click(kiln); // the last straw: back to a placed item with the clay
        assertThat(blocks.stateAt(kiln)).contains(BlockStateRef.of(PlacedItemDevice.ID));
        assertThat(stacks.decode(placedRecord(kiln).slot(0)).getType()).isEqualTo(Material.CLAY_BALL);
        assertThat(placedRecord(kiln).state().rotation0()).isEqualTo(15f);
        assertThat(player.getInventory().contains(Material.WHEAT, 8)).isTrue();
        assertThat(world.getEntitiesByClass(Item.class)).isEmpty();
    }

    @Test
    void creativeKeepsTheStackAndSneakingWithAnItemDoesNothing() {
        placedItem(kiln, Material.CLAY_BALL);
        player.setGameMode(GameMode.CREATIVE);
        hold(Material.WHEAT, 3);
        click(kiln);
        assertThat(inHand().getAmount()).isEqualTo(3);
        player.setGameMode(GameMode.SURVIVAL);
        player.setSneaking(true);
        assertThat(denied(click(kiln))).isFalse();
        assertThat(stage(kiln)).isZero();
        player.setSneaking(false);
        player.setGameMode(GameMode.SPECTATOR);
        assertThat(denied(click(kiln))).isFalse();
        player.setGameMode(GameMode.ADVENTURE);
        assertThat(denied(click(kiln))).as("adventure uses blocks as survival").isTrue();
        assertThat(stage(kiln)).isEqualTo(1);
    }

    // ---- lighting and firing -----------------------------------------------------------------------------------

    @Test
    void lightingNeedsTheEightLogsAndSetsFire() {
        placedItem(kiln, Material.CLAY_BALL);
        hold(Material.WHEAT, 8);
        click(kiln);
        assertThat(kilns.tryLight(kiln)).as("no logs").isFalse();
        fullKiln(pit(20, 64, 4));
        Block full = world.getBlockAt(20, 64, 4);
        full.getRelative(BlockFace.UP).setType(Material.STONE);
        assertThat(kilns.tryLight(full)).as("no room for fire").isFalse();
        full.getRelative(BlockFace.UP).setType(Material.AIR);
        assertThat(kilns.tryLight(full)).isTrue();
        assertThat(stage(full)).isEqualTo(PitKilnLayers.LIT);
        assertThat(full.getRelative(BlockFace.UP).getType()).isEqualTo(Material.FIRE);
        DeviceRecord<PitKilnState> r = record(full);
        assertThat(r.state().lit()).isTrue();
        assertThat(r.state().litTick()).isEqualTo(now.get());
        for (int i = PitKilnState.FIRST_STRAW; i < PitKilnState.SLOTS; i++) {
            assertThat(r.slot(i).isEmpty()).as("fuel slot %d burns", i).isTrue();
        }
        assertThat(slot(full, 0).getType()).isEqualTo(Material.CLAY_BALL);
        assertThat(clock.tracked(full)).isTrue();
        assertThat(kilns.tryLight(full)).as("already lit").isFalse();
        player.getInventory().clear();
        assertThat(denied(click(full))).as("a lit kiln gives nothing back").isFalse();
    }

    @Test
    void lightingSpreadsToDiagonalKilns() {
        Block a = pit(30, 64, 30);
        Block b = pit(31, 64, 31);
        // the diagonal pits share side blocks: make them stone again
        fullKiln(a);
        fullKiln(b);
        a.getRelative(1, 0, 0).setType(Material.STONE);
        a.getRelative(0, 0, 1).setType(Material.STONE);
        assertThat(kilns.tryLight(a)).isTrue();
        scheduler.drain();
        assertThat(stage(b)).isEqualTo(PitKilnLayers.LIT);
    }

    @Test
    void fireAboveAnUnlitFullKilnLightsIt() {
        fullKiln(kiln);
        kiln.getRelative(BlockFace.UP).setType(Material.FIRE);
        devices.neighbourChanged(kiln);
        scheduler.drain();
        assertThat(stage(kiln)).isEqualTo(PitKilnLayers.LIT);
        assertThat(record(kiln).state().lit()).isTrue();
    }

    @Test
    void theLitKilnFiresLazilyAfterPitKilnTicks() {
        fullKiln(kiln);
        kilns.tryLight(kiln);
        long lit = now.get();
        now.set(lit + 3600);
        PitKilnModule.period(clock, kilns, scheduler, now.get());
        scheduler.drain();
        assertThat(stage(kiln)).as("half way").isEqualTo(PitKilnLayers.LIT);
        // the fire burnt out: a neighbour update puts it back (serverTick: isEmptyBlock -> fire)
        kiln.getRelative(BlockFace.UP).setType(Material.AIR);
        devices.neighbourChanged(kiln);
        scheduler.drain();
        assertThat(kiln.getRelative(BlockFace.UP).getType()).isEqualTo(Material.FIRE);
        assertThat(clock.tracked(kiln)).isTrue();

        now.set(lit + PitKilnRules.DEFAULT_DURATION_TICKS + 9000); // tfp time add: a calendar jump past the end
        PitKilnModule.period(clock, kilns, scheduler, now.get());
        scheduler.drain();
        assertThat(blocks.stateAt(kiln)).contains(BlockStateRef.of(PlacedItemDevice.ID));
        ItemStack out = stacks.decode(placedRecord(kiln).slot(0));
        assertThat(out.getType()).isEqualTo(Material.BRICK);
        HeatState heat = HeatPdc.read(out).orElseThrow();
        assertThat(heat.temperature()).isEqualTo(1400f);
        assertThat(heat.lastTick()).as("cooling from the end of the burn").isEqualTo(lit + PitKilnRules.DEFAULT_DURATION_TICKS);
        assertThat(kiln.getRelative(BlockFace.UP).getType()).isEqualTo(Material.AIR);
        assertThat(effects.sounds).contains(KilnEffects.Sound.EXTINGUISH);
        assertThat(clock.tracked(kiln)).isFalse();
        assertThat(displays.shown.get(key(kiln, 0))).hasSize(4);
        assertThat(world.getEntitiesByClass(Item.class)).as("no fuel or contents dropped").isEmpty();
    }

    @Test
    void aBlockOverTheFireFailsTheKilnWithItsContentsCookedSoFar() {
        fullKiln(kiln);
        kilns.tryLight(kiln);
        long lit = now.get();
        now.set(lit + 1200);
        kiln.getRelative(BlockFace.UP).setType(Material.DIRT);
        devices.neighbourChanged(kiln);
        scheduler.drain();
        assertThat(blocks.stateAt(kiln)).contains(BlockStateRef.of(PlacedItemDevice.ID));
        ItemStack out = stacks.decode(placedRecord(kiln).slot(0));
        assertThat(out.getType()).as("not fired").isEqualTo(Material.CLAY_BALL);
        // cooked through the tick before the failure: 1400 x 1.125 x 1199 / 7200
        assertThat(HeatPdc.read(out).orElseThrow().temperature())
            .isEqualTo(PitKilnRules.temperatureAt(lit + 1199, lit, PitKilnRules.Config.DEFAULT));
        assertThat(kiln.getRelative(BlockFace.UP).getType()).isEqualTo(Material.DIRT);
    }

    @Test
    void aBrokenSideFailsTheKiln() {
        fullKiln(kiln);
        kilns.tryLight(kiln);
        now.addAndGet(100);
        flammableSide = true;
        devices.neighbourChanged(kiln);
        scheduler.drain();
        assertThat(blocks.stateAt(kiln)).contains(BlockStateRef.of(PlacedItemDevice.ID));
        assertThat(stacks.decode(placedRecord(kiln).slot(0)).getType()).isEqualTo(Material.CLAY_BALL);
    }

    @Test
    void losingTheBaseDropsTheContentsAndTheFuelOfAnUnlitKiln() {
        placedItem(kiln, Material.CLAY_BALL);
        hold(Material.WHEAT, 3);
        click(kiln);
        click(kiln);
        kiln.getRelative(BlockFace.DOWN).setType(Material.AIR);
        devices.neighbourChanged(kiln);
        scheduler.drain();
        assertThat(blocks.stateAt(kiln)).isEmpty();
        List<Material> dropped = world.getEntitiesByClass(Item.class).stream().map(i -> i.getItemStack().getType()).toList();
        assertThat(dropped).containsExactlyInAnyOrder(Material.CLAY_BALL, Material.WHEAT, Material.WHEAT);
    }

    @Test
    void itemsWithoutHeatSurviveTheFiringUnchanged() {
        placedItem(kiln, Material.STICK);
        fillAndLight(kiln);
        now.addAndGet(PitKilnRules.DEFAULT_DURATION_TICKS);
        kilns.evaluate(kiln);
        ItemStack out = stacks.decode(placedRecord(kiln).slot(0));
        assertThat(out.getType()).isEqualTo(Material.STICK);
        assertThat(HeatPdc.read(out)).isEmpty();
    }

    @Test
    void aKilnWhoseContentsMeltBecomesAir() {
        placedItem(kiln, Material.IRON_INGOT);
        fillAndLight(kiln);
        now.addAndGet(PitKilnRules.DEFAULT_DURATION_TICKS);
        kilns.evaluate(kiln);
        assertThat(blocks.stateAt(kiln)).isEmpty();
        assertThat(kiln.getType()).isEqualTo(Material.AIR);
        assertThat(world.getEntitiesByClass(Item.class)).isEmpty();
    }

    /** Eight straw and eight logs on the placed item at {@code block}, then lit. */
    void fillAndLight(Block block) {
        hold(Material.WHEAT, 8);
        for (int i = 0; i < 8; i++) {
            click(block);
        }
        hold(Material.OAK_LOG, 8);
        for (int i = 0; i < 8; i++) {
            click(block);
        }
        assertThat(kilns.tryLight(block)).isTrue();
    }

    @Test
    void aLitKilnCannotBeBrokenInSurvivalOrAdventure() {
        fullKiln(pit(20, 64, 4));
        Block full = world.getBlockAt(20, 64, 4);
        org.bukkit.event.block.BlockBreakEvent unlit = new org.bukkit.event.block.BlockBreakEvent(full, player);
        listener.onBreak(unlit);
        assertThat(unlit.isCancelled()).as("an unlit kiln breaks").isFalse();
        assertThat(kilns.tryLight(full)).isTrue();
        for (GameMode mode : List.of(GameMode.SURVIVAL, GameMode.ADVENTURE)) {
            player.setGameMode(mode);
            org.bukkit.event.block.BlockBreakEvent e = new org.bukkit.event.block.BlockBreakEvent(full, player);
            listener.onBreak(e);
            assertThat(e.isCancelled()).as("lit kiln in " + mode).isTrue();
        }
        player.setGameMode(GameMode.CREATIVE);
        org.bukkit.event.block.BlockBreakEvent creative = new org.bukkit.event.block.BlockBreakEvent(full, player);
        listener.onBreak(creative);
        assertThat(creative.isCancelled()).as("creative breaks it").isFalse();
    }

    @Test
    void configKeysFallBackToTfcDefaults() {
        org.bukkit.configuration.file.YamlConfiguration yaml = new org.bukkit.configuration.file.YamlConfiguration();
        assertThat(PitKilnModule.config(yaml, Logger.getLogger("test"))).isEqualTo(PitKilnRules.Config.DEFAULT);
        yaml.set(PitKilnModule.TICKS_KEY, 10);
        yaml.set(PitKilnModule.TEMPERATURE_KEY, 1200);
        PitKilnRules.Config c = PitKilnModule.config(yaml, Logger.getLogger("test"));
        assertThat(c.durationTicks()).as("below TFC's minimum 20").isEqualTo(7200);
        assertThat(c.maxTemperature()).isEqualTo(1200f);
    }
}
