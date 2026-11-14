/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.charcoal;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.charcoal.BurningPileData;
import dev.tfp.core.charcoal.CharcoalPit;
import dev.tfp.core.charcoal.CharcoalSpread;
import dev.tfp.core.charcoal.LogPileData;
import dev.tfp.core.device.DeviceRecord;
import dev.tfp.core.device.ItemBlob;
import dev.tfp.core.persist.DataMap;
import dev.tfp.paper.device.DeviceKinds;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.device.DeviceStore;
import dev.tfp.paper.device.StackCodec;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.FakeCustomBlocks;
import dev.tfp.paper.integration.FakeCustomItems;
import dev.tfp.paper.testing.MockServerTest;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;
import java.util.random.RandomGenerator;
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
 * PAR-PROC-10 on MockBukkit with fakes of the CraftEngine adapter and the world writes: building a log pile by sneak
 * clicks (16 logs per pile, the push-up into a new pile, the double click), taking logs out, the lost floor, lighting it
 * (fire next to it, a strong start), the burning pile's sweep (fire into air, the neighbour pile 30 ticks later, the 1-in-7
 * burn), the conversion to charcoal after {@code charcoalTicks} (one pile and a column of two, with a charcoal pile below),
 * the {@link PitClock} and the charcoal pile's placement and layer breaks per game mode.
 *
 * <p>Stand-ins: oak and birch logs are {@code #tfp:log_pile_logs}; stone and dirt are sturdy, non-flammable covers; oak
 * planks are a flammable cover.
 */
class CharcoalPitMockBukkitTest extends MockServerTest {
    static final long START = 10_000;
    /** nextFloat() = 0.5, nextInt(7) = 1 (never burns the pile). */
    static final RandomGenerator HALF = () -> 0x8000_0000_0000_0000L;
    /** nextFloat() = 0, nextInt(7) = 0 (always burns). */
    static final RandomGenerator ZERO = () -> 0L;

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

    static final Set<String> DEVICES = Set.of(CharcoalPitDevice.LOG_PILE, CharcoalPitDevice.BURNING);

    /** The world writes: deferred ones queue until {@link #flush}, as region tasks run after the callback. */
    final class Effects implements CharcoalEffects {
        final List<Runnable> queue = new ArrayList<>();
        final List<Long> laterTicks = new ArrayList<>();
        final List<Runnable> laterTasks = new ArrayList<>();
        final List<Sound> sounds = new ArrayList<>();
        final List<ItemStack> given = new ArrayList<>();
        final List<ItemStack> spawned = new ArrayList<>();

        void flush() {
            for (int guard = 0; !queue.isEmpty() && guard < 1000; guard++) {
                queue.remove(0).run();
            }
        }

        void runLater() {
            List<Runnable> tasks = new ArrayList<>(laterTasks);
            laterTasks.clear();
            tasks.forEach(Runnable::run);
            flush();
        }

        boolean is(Block b, String id) {
            return blocks.stateAt(b).map(s -> s.id().equals(id)).orElse(false);
        }

        boolean free(Block b, String replaceId) {
            return replaceId == null ? b.getType().isAir() && blocks.stateAt(b).isEmpty() : is(b, replaceId);
        }

        /** CraftEngine replacing a block: the new state, then the old device's removal with its stored data. */
        void replace(Block b, BlockStateRef state) {
            Optional<BlockStateRef> old = blocks.stateAt(b);
            if (state == null) {
                blocks.remove(b);
            } else {
                blocks.place(b, state, true);
            }
            if (old.isPresent() && DEVICES.contains(old.get().id())) {
                DataMap data = store.data.remove(FakeCustomBlocks.at(b));
                service.removed(b.getWorld(), b.getX(), b.getY(), b.getZ(), old.get().id(), data == null ? new DataMap() : data);
            }
            if (state != null && DEVICES.contains(state.id())) {
                store.data.put(FakeCustomBlocks.at(b), new DataMap());
            }
        }

        @Override
        public void show(Block block, BlockStateRef state) {
            queue.add(() -> {
                if (is(block, state.id())) {
                    blocks.place(block, state, false);
                }
            });
        }

        @Override
        public void place(Block block, BlockStateRef state, String replaceId, Runnable then) {
            queue.add(() -> {
                if (free(block, replaceId)) {
                    replace(block, state);
                    if (then != null) {
                        then.run();
                    }
                }
            });
        }

        @Override
        public void air(Block block, String expectedId) {
            queue.add(() -> {
                if (is(block, expectedId)) {
                    replace(block, null);
                }
            });
        }

        @Override
        public void fire(Block block, String replaceId) {
            queue.add(() -> {
                if (free(block, replaceId)) {
                    if (replaceId != null) {
                        replace(block, null);
                    }
                    block.setType(Material.FIRE);
                }
            });
        }

        @Override
        public void later(Block block, long ticks, Runnable task) {
            laterTicks.add(ticks);
            laterTasks.add(task);
        }

        @Override
        public void breakBlock(Block block) {
            queue.add(() -> {
                if (blocks.stateAt(block).isPresent()) {
                    replace(block, null);
                }
            });
        }

        @Override
        public boolean placeNow(Block block, BlockStateRef state) {
            if (!blocks.exists(state.id())) {
                return false;
            }
            replace(block, state);
            return true;
        }

        @Override
        public void setNow(Block block, BlockStateRef state) {
            blocks.place(block, state, true);
        }

        @Override
        public void give(Player player, ItemStack stack) {
            given.add(stack);
        }

        @Override
        public void spawn(Block block, ItemStack stack) {
            spawned.add(stack);
        }

        @Override
        public void sound(Block block, Sound sound) {
            sounds.add(sound);
        }
    }

    private final FakeCustomBlocks blocks = new FakeCustomBlocks().known(CharcoalPitDevice.LOG_PILE, CharcoalPitDevice.BURNING,
        CharcoalPitDevice.CHARCOAL_PILE);
    private final MemoryStore store = new MemoryStore();
    private final TableCodec codec = new TableCodec();
    private final Effects effects = new Effects();
    private final AtomicLong clock = new AtomicLong(START);
    private RandomGenerator random = HALF;
    private DeviceService service;
    private CharcoalPitDevice device;
    private CharcoalPitDevice.Services services;
    private CharcoalListener listener;
    private World world;
    private Block floor;
    private Block pile;
    private PlayerMock player;

    static boolean solid(Block b) {
        return b.getType() == Material.STONE || b.getType() == Material.DIRT;
    }

    CharcoalSpread.Cover cover(Block side, BlockFace face) {
        Optional<BlockStateRef> s = blocks.stateAt(side);
        if (s.isPresent()) {
            return s.get().id().equals(CharcoalPitDevice.LOG_PILE) ? CharcoalSpread.Cover.LOG_PILE
                : CharcoalSpread.Cover.PIT_INSULATION;
        }
        if (side.getType().isAir()) {
            return CharcoalSpread.Cover.AIR;
        }
        return solid(side) ? CharcoalSpread.Cover.STURDY_NON_FLAMMABLE : CharcoalSpread.Cover.OTHER;
    }

    @BeforeEach
    void setUp() {
        world = server.addSimpleWorld("charcoal");
        service = new DeviceService(new DeviceKinds(), blocks, () -> store, clock::get, codec, new FakeCustomItems(),
            Logger.getLogger("test"));
        services = new CharcoalPitDevice.Services(
            s -> s.getType() == Material.OAK_LOG || s.getType() == Material.BIRCH_LOG,
            blocks::stateAt, blocks::exists, CharcoalPitMockBukkitTest::solid,
            b -> b.getType() == Material.FIRE, this::cover,
            b -> service.inspect(b).flatMap(DeviceService.Inspection::record),
            () -> random, CharcoalPit.DEFAULT_CHARCOAL_TICKS, effects);
        device = new CharcoalPitDevice(services, codec, new PitClock(), service::neighbourChanged);
        service.register(device.logPileKind());
        service.register(device.burningKind());
        listener = new CharcoalListener(device, services, b -> true);
        floor = world.getBlockAt(0, 63, 0);
        floor.setType(Material.STONE);
        pile = world.getBlockAt(0, 64, 0);
        player = server.addPlayer();
        player.teleport(new Location(world, 3.5, 64, 0.5, 90f, 0f)); // facing west: axis x
        player.setGameMode(GameMode.SURVIVAL);
        player.setSneaking(true);
    }

    private ItemStack hold(Material m, int n) {
        ItemStack s = new ItemStack(m, n);
        player.getInventory().setItem(EquipmentSlot.HAND, s);
        return s;
    }

    private boolean sneakClick(Block clicked, BlockFace face) {
        ItemStack held = player.getInventory().getItem(EquipmentSlot.HAND);
        return held.getType() == Material.CHARCOAL
            ? listener.charcoal(player, EquipmentSlot.HAND, clicked, face, held)
            : listener.log(player, EquipmentSlot.HAND, clicked, face, held);
    }

    @SuppressWarnings("unchecked")
    private <S> DeviceRecord<S> record(Block b) {
        return (DeviceRecord<S>) service.inspect(b).flatMap(DeviceService.Inspection::record).orElseThrow();
    }

    private int logs(Block b) {
        int n = 0;
        for (ItemBlob s : record(b).slots()) {
            if (!s.isEmpty()) {
                n++;
            }
        }
        return n;
    }

    private BlockStateRef state(Block b) {
        return blocks.stateAt(b).orElse(null);
    }

    /** Sneak-places {@code n} logs on the floor, one click every 10 ticks (never a double click). */
    private void buildPile(int n) {
        hold(Material.OAK_LOG, 64);
        sneakClick(floor, BlockFace.UP);
        for (int i = 1; i < n; i++) {
            clock.addAndGet(10);
            sneakClick(pile, BlockFace.UP);
        }
        effects.flush();
    }

    /** Surrounds {@code b} with dirt on the four sides and above. */
    private void cover(Block b) {
        for (BlockFace f : new BlockFace[] {BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST, BlockFace.UP}) {
            Block side = b.getRelative(f);
            if (blocks.stateAt(side).isEmpty()) {
                side.setType(Material.DIRT);
            }
        }
    }

    @Test
    void sneakClicksBuildAPileOfSixteenLogsThenANewPileAbove() {
        buildPile(16);
        assertThat(state(pile)).isEqualTo(CharcoalPitDevice.logPile("x", 16));
        assertThat(logs(pile)).isEqualTo(16);
        assertThat(player.getInventory().getItem(EquipmentSlot.HAND).getAmount()).isEqualTo(64 - 16);
        assertThat(effects.sounds).contains(CharcoalEffects.Sound.LOG_PLACE);

        clock.addAndGet(10);
        sneakClick(pile, BlockFace.UP);
        effects.flush();
        Block above = pile.getRelative(BlockFace.UP);
        assertThat(state(above)).isEqualTo(CharcoalPitDevice.logPile("x", 1));
        assertThat(logs(above)).isEqualTo(1);
        assertThat(player.getInventory().getItem(EquipmentSlot.HAND).getAmount()).isEqualTo(64 - 17);
    }

    @Test
    void aQuickSecondSneakClickInsertsAllThatFit() {
        hold(Material.OAK_LOG, 30);
        sneakClick(floor, BlockFace.UP);
        clock.addAndGet(3);
        sneakClick(pile, BlockFace.UP); // placement double click (< 6 ticks): one log is already in, 15 fit, 14 go above
        effects.flush();
        assertThat(logs(pile)).isEqualTo(16);
        assertThat(logs(pile.getRelative(BlockFace.UP))).isEqualTo(14);
        assertThat(StackCodec.isEmpty(player.getInventory().getItem(EquipmentSlot.HAND))).isTrue();
    }

    @Test
    void plainClicksTakeLogsFromTheTopPileAndAQuickSecondClickTakesAll() {
        buildPile(5);
        player.setSneaking(false);
        hold(Material.AIR, 1);
        clock.addAndGet(100);
        assertThat(service.use(pile, player, EquipmentSlot.HAND, ItemStack.empty())).isTrue();
        effects.flush();
        assertThat(effects.given).hasSize(1);
        assertThat(logs(pile)).isEqualTo(4);
        clock.addAndGet(5);
        service.use(pile, player, EquipmentSlot.HAND, ItemStack.empty());
        effects.flush();
        assertThat(effects.given).hasSize(5);
        assertThat(state(pile)).isNull();
    }

    @Test
    void creativeKeepsTheLogsAndAdventureCannotBuild() {
        player.setGameMode(GameMode.ADVENTURE);
        hold(Material.OAK_LOG, 4);
        assertThat(sneakClick(floor, BlockFace.UP)).isFalse();
        assertThat(state(pile)).isNull();
        player.setGameMode(GameMode.CREATIVE);
        assertThat(sneakClick(floor, BlockFace.UP)).isTrue();
        assertThat(player.getInventory().getItem(EquipmentSlot.HAND).getAmount()).isEqualTo(4);
        assertThat(logs(pile)).isEqualTo(1);
        player.setSneaking(false);
        player.setGameMode(GameMode.SURVIVAL);
        assertThat(sneakClick(floor.getRelative(BlockFace.EAST), BlockFace.UP)).as("needs sneaking").isFalse();
    }

    @Test
    void aPileWithoutFloorBreaksAndDropsItsLogs() {
        buildPile(3);
        floor.setType(Material.AIR);
        service.neighbourChanged(pile);
        effects.flush();
        assertThat(state(pile)).isNull();
        assertThat(effects.spawned).hasSize(3);
    }

    @Test
    void fireNextToALogPileLightsIt() {
        buildPile(16);
        cover(pile);
        pile.getRelative(BlockFace.UP).setType(Material.FIRE);
        service.neighbourChanged(pile);
        effects.flush();
        assertThat(state(pile)).isEqualTo(CharcoalPitDevice.burning("x", 16));
        DeviceRecord<BurningPileData> r = record(pile);
        assertThat(r.state()).isEqualTo(BurningPileData.lit(16, clock.get()));
        assertThat(effects.spawned).as("the logs burn, they do not drop").isEmpty();
        assertThat(effects.sounds).contains(CharcoalEffects.Sound.LIGHT);
    }

    @Test
    void aCoveredLitLogPileBecomesCharcoalAfterCharcoalTicks() {
        buildPile(16);
        cover(pile);
        assertThat(device.light(pile)).isTrue();
        effects.flush();
        long lit = clock.get();
        assertThat(device.clock().convertTick(pile)).hasValue(lit + CharcoalPit.DEFAULT_CHARCOAL_TICKS + 1);

        clock.addAndGet(CharcoalPit.DEFAULT_CHARCOAL_TICKS);
        service.neighbourChanged(pile);
        effects.flush();
        assertThat(state(pile)).as("not yet: TFC converts once more than charcoalTicks passed").isEqualTo(
            CharcoalPitDevice.burning("x", 16));

        clock.addAndGet(1);
        assertThat(device.clock().due(clock.get(), w -> 0, b -> 1)).singleElement()
            .satisfies(d -> assertThat(d.convert()).isTrue());
        device.randomTicks(pile, 0);
        effects.flush();
        // getCharcoalAmount: (int) (16 * (0.25 + 0.25 * 0.5)) = 6
        assertThat(state(pile)).isEqualTo(CharcoalPitDevice.charcoalPile(6));
        assertThat(device.clock().size()).isZero();
        assertThat(store.data).doesNotContainKey(FakeCustomBlocks.at(pile));
    }

    @Test
    void aColumnConvertsFromItsTopPileIntoTheCharcoalPileBelow() {
        floor.setType(Material.AIR);
        blocks.place(floor, CharcoalPitDevice.charcoalPile(5), false);
        world.getBlockAt(0, 62, 0).setType(Material.STONE);
        hold(Material.OAK_LOG, 64);
        // a charcoal pile is not a sturdy floor here: put the bottom pile there directly
        effects.placeNow(pile, CharcoalPitDevice.logPile("z", 1));
        device.insert(pile, new ItemStack(Material.OAK_LOG)); // count 1
        for (int i = 0; i < 31; i++) {
            clock.addAndGet(10);
            device.insert(pile, new ItemStack(Material.OAK_LOG));
            effects.flush(); // the next click is a later tick: the new pile above exists by then
        }
        effects.flush();
        Block top = pile.getRelative(BlockFace.UP);
        assertThat(logs(pile)).isEqualTo(16);
        assertThat(logs(top)).isEqualTo(16);
        cover(pile);
        cover(top);
        device.light(pile);
        effects.flush();
        device.light(top);
        effects.flush();
        service.neighbourChanged(pile); // the shape update of the burning pile placed above
        assertThat(device.clock().convertTick(pile)).as("a pile above: the top converts the column")
            .hasValue(CharcoalPit.NEVER);

        clock.addAndGet(CharcoalPit.DEFAULT_CHARCOAL_TICKS + 1);
        service.neighbourChanged(top);
        effects.flush();
        // 6 + 6 = 12: 3 fill the pile below to 8, then 8 into the bottom cell and 1 into the top
        assertThat(state(floor)).isEqualTo(CharcoalPitDevice.charcoalPile(8));
        assertThat(state(pile)).isEqualTo(CharcoalPitDevice.charcoalPile(8));
        assertThat(state(top)).isEqualTo(CharcoalPitDevice.charcoalPile(1));
    }

    @Test
    void theSweepPlacesFireInAirAndLightsTheNeighbourPileThirtyTicksLater() {
        buildPile(4);
        Block east = pile.getRelative(BlockFace.EAST);
        effects.placeNow(east, CharcoalPitDevice.logPile("x", 1));
        device.insert(east, new ItemStack(Material.OAK_LOG));
        effects.flush();
        device.light(pile);
        effects.flush();
        assertThat(pile.getRelative(BlockFace.UP).getType()).isEqualTo(Material.FIRE);
        assertThat(effects.laterTicks).containsExactly(30L);
        assertThat(state(east).id()).isEqualTo(CharcoalPitDevice.LOG_PILE);
        effects.runLater();
        assertThat(state(east).id()).isEqualTo(CharcoalPitDevice.BURNING);
    }

    @Test
    void anUncoveredFlammableSideBurnsThePileOneInSeven() {
        buildPile(4);
        cover(pile);
        pile.getRelative(BlockFace.NORTH).setType(Material.OAK_PLANKS);
        random = ZERO;
        device.light(pile);
        effects.flush();
        assertThat(pile.getType()).isEqualTo(Material.FIRE);
        assertThat(state(pile)).isNull();
        assertThat(device.clock().size()).isZero();
    }

    @Test
    void randomTicksSweepAgain() {
        buildPile(4);
        cover(pile);
        device.light(pile);
        effects.flush();
        pile.getRelative(BlockFace.UP).setType(Material.AIR);
        assertThat(device.clock().due(clock.get(), w -> 3, b -> 0)).singleElement()
            .satisfies(d -> assertThat(d.randomTicks()).isEqualTo(60));
        device.randomTicks(pile, 1);
        effects.flush();
        assertThat(pile.getRelative(BlockFace.UP).getType()).isEqualTo(Material.FIRE);
    }

    @Test
    void charcoalStacksIntoLayersAndBreaksOneLayerAtATime() {
        hold(Material.CHARCOAL, 10);
        assertThat(sneakClick(floor, BlockFace.UP)).isTrue();
        assertThat(state(pile)).isEqualTo(CharcoalPitDevice.charcoalPile(1));
        for (int i = 0; i < 8; i++) {
            sneakClick(pile, BlockFace.UP);
        }
        // 8 layers, then the ninth charcoal starts a pile on top (the block above is free and the pile below is full)
        assertThat(state(pile)).isEqualTo(CharcoalPitDevice.charcoalPile(8));
        Block above = pile.getRelative(BlockFace.UP);
        assertThat(state(above)).isEqualTo(CharcoalPitDevice.charcoalPile(1));
        assertThat(player.getInventory().getItem(EquipmentSlot.HAND).getAmount()).isEqualTo(1);

        assertThat(listener.breakLayer(player, pile, 8, true)).isTrue();
        // 7 left below, the pile above (1) pours into it: 8 and air
        assertThat(state(pile)).isEqualTo(CharcoalPitDevice.charcoalPile(8));
        effects.flush();
        assertThat(state(above)).isNull();
        assertThat(effects.spawned).singleElement().satisfies(s -> assertThat(s.getType()).isEqualTo(Material.CHARCOAL));

        player.setGameMode(GameMode.CREATIVE);
        assertThat(listener.breakLayer(player, pile, 8, false)).as("creative breaks the whole pile").isFalse();
    }

    @Test
    void chunkUnloadForgetsThePiles() {
        buildPile(2);
        cover(pile);
        device.light(pile);
        effects.flush();
        assertThat(device.clock().size()).isEqualTo(1);
        device.clock().untrackChunk(world, 0, 0);
        assertThat(device.clock().size()).isZero();
        service.neighbourChanged(pile); // the chunk load callback lists it again
        assertThat(device.clock().size()).isEqualTo(1);
    }

    @Test
    void logPileDataIsKept() {
        buildPile(2);
        DeviceRecord<LogPileData> r = record(pile);
        assertThat(r.state().lastClickPlacement()).isTrue();
        assertThat(r.state().lastClickTick()).isEqualTo(clock.get());
    }
}
