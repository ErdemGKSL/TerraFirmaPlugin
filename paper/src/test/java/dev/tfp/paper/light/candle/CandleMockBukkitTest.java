/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.light.candle;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.device.ItemBlob;
import dev.tfp.core.fuel.FireStart;
import dev.tfp.core.persist.DataMap;
import dev.tfp.core.world.BlockPos;
import dev.tfp.paper.device.DeviceKinds;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.device.DeviceStore;
import dev.tfp.paper.device.StackCodec;
import dev.tfp.paper.fire.FireStartContext;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.FakeCustomBlocks;
import dev.tfp.paper.integration.FakeCustomItems;
import dev.tfp.paper.pipeline.CommitBudget;
import dev.tfp.paper.pipeline.RegionExecutor;
import dev.tfp.paper.testing.MockServerTest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * PAR-HEAT-11 on MockBukkit with fakes of the CraftEngine adapter: lighting stamps the counter, the chunk-load catch-up
 * puts a candle and a candle cake out after TFC's candleTicks (and not before) and turns a jack-o'-lantern back into the
 * carved pumpkin after jackOLanternTicks, the uses (extinguish, the firestarter QoL, the candle cake's bite), dousing,
 * the candle on a cake, the knife carving a pumpkin and the held torch's weak start.
 *
 * <p>Stand-ins: blaze rod = the firestarter, vanilla torch = the {@code tfp:torch} item, white candle = {@code tfp:candle},
 * red candle = {@code tfp:candle/red}, iron sword = a knife.
 */
class CandleMockBukkitTest extends MockServerTest {
    static final long START = 10_000;
    static final int DAY = 24_000;

    /** CraftEngine's block entity: present for our candle, candle cake and jack-o'-lantern blocks. */
    final class Store implements DeviceStore {
        final Map<FakeCustomBlocks.At, DataMap> data = new ConcurrentHashMap<>();

        boolean device(Block block) {
            return blocks.stateAt(block).flatMap(s -> CandleRules.family(s.id())).isPresent();
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
    }

    final class Effects implements CandleEffects {
        final List<Long> callbacks = new ArrayList<>();
        final List<Sound> sounds = new ArrayList<>();
        final List<ItemStack> drops = new ArrayList<>();
        int taken;
        int damaged;

        @Override
        public void burnout(Block block) {
            service.burnout(block);
        }

        @Override
        public void burnoutIn(Block block, long ticks) {
            callbacks.add(ticks);
        }

        @Override
        public boolean setState(Block block, BlockStateRef state) {
            Optional<BlockStateRef> now = blocks.stateAt(block);
            if (now.isEmpty() || !now.get().id().equals(state.id())) {
                return false;
            }
            return blocks.place(block, state, false); // the fake keeps the record: same block id
        }

        @Override
        public boolean place(Block block, BlockStateRef state) {
            store.data.remove(FakeCustomBlocks.at(block)); // a new block: a new block entity
            return blocks.place(block, state, true);
        }

        @Override
        public void placeCarvedPumpkin(Block block, String facing) {
            blocks.remove(block);
            BlockData data = Material.CARVED_PUMPKIN.createBlockData();
            ((Directional) data).setFacing(BlockFace.valueOf(facing.toUpperCase(Locale.ROOT)));
            block.setBlockData(data, false);
        }

        @Override
        public Optional<String> carvedPumpkin(Block block) {
            if (block.getType() != Material.CARVED_PUMPKIN) {
                return Optional.empty();
            }
            return Optional.of(((Directional) block.getBlockData()).getFacing().name().toLowerCase(Locale.ROOT));
        }

        @Override
        public void sound(Block block, Sound sound) {
            sounds.add(sound);
        }

        @Override
        public void takeOne(Player player, EquipmentSlot hand) {
            taken++;
        }

        @Override
        public void damageHeld(Player player, EquipmentSlot hand) {
            damaged++;
        }

        @Override
        public void drop(Block block, ItemStack stack) {
            drops.add(stack);
        }
    }

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

    private final Deque<Runnable> tasks = new ArrayDeque<>();
    private final RegionExecutor regions = new RegionExecutor() {
        @Override
        public void run(int chunkX, int chunkZ, Runnable task) {
            tasks.add(task);
        }

        @Override
        public void runNextTick(int chunkX, int chunkZ, Runnable task) {
            tasks.add(task);
        }

        @Override
        public boolean isOwner(int chunkX, int chunkZ) {
            return true;
        }
    };
    private final FakeCustomBlocks blocks = new FakeCustomBlocks().known(ids());
    private final Store store = new Store();
    private final Effects effects = new Effects();
    private final AtomicLong clock = new AtomicLong(START);
    private final AtomicReference<Optional<CandleDevice.CakeEating>> eating = new AtomicReference<>(Optional.empty());
    private final AtomicReference<Double> hit = new AtomicReference<>(0.9);
    private DeviceService devices;
    private CandleService service;
    private World world;
    private Block at;
    private PlayerMock player;

    private static String[] ids() {
        List<String> ids = new ArrayList<>(CandleRules.candles());
        ids.addAll(CandleRules.candleCakes());
        ids.add(CandleRules.JACK_O_LANTERN);
        ids.add(CandleRules.CAKE);
        ids.add(CandleRules.PUMPKIN);
        return ids.toArray(String[]::new);
    }

    @BeforeEach
    void setUp() {
        world = server.addSimpleWorld("candle");
        world.loadChunk(0, 0);
        devices = new DeviceService(new DeviceKinds(), blocks, () -> store, clock::get, NO_SLOTS, new FakeCustomItems(),
            Logger.getLogger("candle-test"));
        CandleDevice device = new CandleDevice(new CandleDevice.Services(() -> CandleRules.CANDLE_TICKS,
            () -> CandleRules.JACK_O_LANTERN_TICKS, s -> s.getType() == Material.BLAZE_ROD, (p, b) -> hit.get(),
            eating::get, id -> new ItemStack(id.equals("tfp:candle/red") ? Material.RED_CANDLE : Material.CANDLE),
            effects));
        device.kinds().forEach(devices::register);
        service = new CandleService(blocks, devices, device, clock::get, w -> regions, Runnable::run,
            new CommitBudget(1_000_000_000L, System::nanoTime));
        at = world.getBlockAt(2, 64, 3);
        player = server.addPlayer();
        player.teleport(new Location(world, 4.5, 64, 4.5));
        player.setGameMode(GameMode.SURVIVAL);
    }

    private void runTasks() {
        while (!tasks.isEmpty()) {
            tasks.poll().run();
        }
    }

    private static BlockStateRef candle(String id, int candles, boolean lit) {
        return BlockStateRef.of(id).with("candles", Integer.toString(candles)).with("lit", Boolean.toString(lit))
            .with("waterlogged", "false");
    }

    private static BlockStateRef cake(String id, boolean lit) {
        return BlockStateRef.of(id).with("lit", Boolean.toString(lit));
    }

    private List<CandleService.Waiting> reload() {
        List<CandleService.Waiting> w = service.chunkLoaded(at.getChunk(), List.of(new BlockPos(at.getX(), at.getY(), at.getZ())));
        runTasks();
        return w;
    }

    private BlockStateRef state() {
        return blocks.stateAt(at).orElseThrow();
    }

    private long litTick() {
        return service.read(at).litTick();
    }

    private Optional<String> carved() {
        return effects.carvedPumpkin(at);
    }

    @Test
    void lightingStampsTheCounterAndQueuesTheBurnout() {
        blocks.place(at, candle("tfp:candle", 3, false), true);
        assertThat(service.light(at)).isTrue();
        assertThat(state()).isEqualTo(candle("tfp:candle", 3, true));
        assertThat(litTick()).isEqualTo(START);
        assertThat(effects.callbacks).containsExactly(288_001L);
    }

    @Test
    void aLitCandleBurnsOutAfterCandleTicksOnCatchUp() {
        blocks.place(at, candle("tfp:candle/red", 4, false), true);
        service.light(at);
        clock.addAndGet(5L * DAY); // 120,000 < 288,000: still lit, waits for the rest
        assertThat(reload()).singleElement().satisfies(w -> assertThat(w.ticks()).isEqualTo(288_001L - 120_000L));
        assertThat(state()).isEqualTo(candle("tfp:candle/red", 4, true));
        clock.set(START + 288_000); // exactly at the limit it still burns (TFC: ticks > candleTicks)
        assertThat(service.callback(at)).isEqualTo(1);
        clock.set(START + 13L * DAY); // 312,000
        assertThat(reload()).isEmpty();
        assertThat(state()).isEqualTo(candle("tfp:candle/red", 4, false)); // the candles stay, only the flame goes
        assertThat(service.burnouts()).isEqualTo(1);
        assertThat(store.read(at)).isPresent(); // the counter stays with the block
    }

    @Test
    void anUnlitCandleNeverChanges() {
        blocks.place(at, candle("tfp:candle", 1, false), true);
        clock.addAndGet(1_000_000);
        assertThat(reload()).isEmpty();
        assertThat(state()).isEqualTo(candle("tfp:candle", 1, false));
        assertThat(service.burnouts()).isZero();
    }

    @Test
    void aLitCandleCakeBurnsOutLikeACandle() {
        blocks.place(at, cake("tfp:candle_cake/black", false), true);
        service.light(at);
        clock.addAndGet(288_001);
        reload();
        assertThat(state()).isEqualTo(cake("tfp:candle_cake/black", false));
    }

    @Test
    void aJackOLanternBecomesTheCarvedPumpkinAfterJackTicksFacingKept() {
        BlockData pumpkin = Material.CARVED_PUMPKIN.createBlockData();
        ((Directional) pumpkin).setFacing(BlockFace.EAST);
        at.setBlockData(pumpkin);
        assertThat(service.light(at)).isTrue(); // TFC onFireStart: carved pumpkin -> jack-o'-lantern
        assertThat(state()).isEqualTo(BlockStateRef.of("tfp:jack_o_lantern").with("facing", "east"));
        assertThat(litTick()).isEqualTo(START);
        assertThat(effects.callbacks).containsExactly(108_001L);
        clock.addAndGet(108_000);
        reload();
        assertThat(state().id()).isEqualTo("tfp:jack_o_lantern");
        clock.addAndGet(1);
        reload();
        assertThat(blocks.stateAt(at)).isEmpty();
        assertThat(carved()).contains("east");
    }

    @Test
    void aPlacedJackOLanternHasNoCounterAndGoesOutAsInTfc() {
        blocks.place(at, BlockStateRef.of("tfp:jack_o_lantern").with("facing", "south"), true);
        devices.placed(at, player, EquipmentSlot.HAND, new ItemStack(Material.JACK_O_LANTERN));
        runTasks();
        assertThat(carved()).contains("south");
    }

    @Test
    void aWaterloggedCandleIsNotLit() {
        blocks.place(at, candle("tfp:candle", 2, false).with("waterlogged", "true"), true);
        assertThat(service.lightable().light(new FireStartContext(at, BlockFace.UP, null, ItemStack.empty(),
            FireStart.Strength.STRONG))).isFalse();
        assertThat(state().properties()).containsEntry("lit", "false");
    }

    @Test
    void aFireStartOnALitCandleRestartsItsCounter() {
        blocks.place(at, candle("tfp:candle", 1, false), true);
        service.light(at);
        clock.addAndGet(200_000);
        assertThat(service.lightable().light(new FireStartContext(at, BlockFace.UP, null, ItemStack.empty(),
            FireStart.Strength.WEAK))).isTrue();
        assertThat(litTick()).isEqualTo(START + 200_000);
        clock.addAndGet(200_000);
        reload();
        assertThat(state().properties()).containsEntry("lit", "true");
    }

    @Test
    void anEmptyHandPutsALitCandleOutButNotWithAFirestarterInTheMainHand() {
        blocks.place(at, candle("tfp:candle", 2, false), true);
        service.light(at);
        // TFC QoL: the main hand holds the firestarter, so the off-hand click does not put out what was just lit
        player.getInventory().setItemInMainHand(new ItemStack(Material.BLAZE_ROD));
        assertThat(devices.use(at, player, EquipmentSlot.OFF_HAND, ItemStack.empty())).isFalse();
        assertThat(state().properties()).containsEntry("lit", "true");
        // adventure: vanilla's mayBuild is false
        player.getInventory().setItemInMainHand(null);
        player.setGameMode(GameMode.ADVENTURE);
        assertThat(devices.use(at, player, EquipmentSlot.HAND, ItemStack.empty())).isFalse();
        // another item passes (the vanilla default)
        player.setGameMode(GameMode.SURVIVAL);
        assertThat(devices.use(at, player, EquipmentSlot.HAND, new ItemStack(Material.DIRT))).isFalse();
        assertThat(state().properties()).containsEntry("lit", "true");
        assertThat(devices.use(at, player, EquipmentSlot.HAND, ItemStack.empty())).isTrue();
        assertThat(state()).isEqualTo(candle("tfp:candle", 2, false));
        assertThat(effects.sounds).containsExactly(CandleEffects.Sound.EXTINGUISH);
        assertThat(litTick()).isEqualTo(START); // vanilla extinguish keeps the counter
    }

    @Test
    void aClickOnABurntOutCandlePutsItOutThroughThePipeline() {
        blocks.place(at, candle("tfp:candle", 2, false), true);
        service.light(at);
        clock.addAndGet(300_000);
        assertThat(devices.use(at, player, EquipmentSlot.HAND, ItemStack.empty())).isFalse();
        runTasks();
        assertThat(state().properties()).containsEntry("lit", "false");
        assertThat(service.burnouts()).isEqualTo(1);
    }

    @Test
    void theCandleCakeTopPutsItOutAndTheRestIsABite() {
        blocks.place(at, cake("tfp:candle_cake/red", false), true);
        service.light(at);
        hit.set(0.9);
        assertThat(devices.use(at, player, EquipmentSlot.HAND, ItemStack.empty())).isTrue();
        assertThat(state()).isEqualTo(cake("tfp:candle_cake/red", false));
        // low on the block (or unlit): TFC eatCake; without the cake of PAR-DECO-12 nothing happens
        hit.set(0.3);
        assertThat(devices.use(at, player, EquipmentSlot.HAND, ItemStack.empty())).isFalse();
        AtomicBoolean canEat = new AtomicBoolean(false);
        eating.set(Optional.of(p -> canEat.get()));
        assertThat(devices.use(at, player, EquipmentSlot.HAND, ItemStack.empty())).isFalse(); // full: cannot eat
        canEat.set(true);
        assertThat(devices.use(at, player, EquipmentSlot.HAND, ItemStack.empty())).isTrue();
        assertThat(state()).isEqualTo(BlockStateRef.of("tfp:cake").with("bites", "1"));
        assertThat(effects.drops).extracting(ItemStack::getType).containsExactly(Material.RED_CANDLE);
    }

    @Test
    void aRelightBetweenDecisionAndCommitWins() {
        blocks.place(at, candle("tfp:candle", 1, false), true);
        service.light(at);
        clock.addAndGet(300_000);
        service.burnout(at);
        assertThat(tasks).isNotEmpty();
        service.light(at); // the counter moves on before the commit
        runTasks();
        assertThat(state().properties()).containsEntry("lit", "true");
        assertThat(service.burnouts()).isZero();
    }

    @Test
    void douseAndItsLimits() {
        blocks.place(at, candle("tfp:candle", 1, false), true);
        assertThat(service.douse(at)).isFalse(); // not lit
        service.light(at);
        assertThat(service.douse(at)).isTrue();
        assertThat(state().properties()).containsEntry("lit", "false");
        blocks.place(at, BlockStateRef.of("tfp:jack_o_lantern").with("facing", "west"), true);
        assertThat(service.douse(at)).isTrue();
        assertThat(carved()).contains("west");
        assertThat(service.douse(world.getBlockAt(9, 64, 9))).isFalse();
    }

    @Test
    void aCandleOnAFreshCakeMakesItsCandleCake() {
        CandleListener listener = listener(new ArrayList<>());
        blocks.place(at, BlockStateRef.of("tfp:cake").with("bites", "1"), true);
        PlayerInteractEvent bitten = click(new ItemStack(Material.RED_CANDLE), BlockFace.UP);
        listener.onUse(bitten);
        assertThat(bitten.isCancelled()).isFalse();
        blocks.place(at, BlockStateRef.of("tfp:cake").with("bites", "0"), true);
        PlayerInteractEvent fresh = click(new ItemStack(Material.RED_CANDLE), BlockFace.UP);
        listener.onUse(fresh);
        assertThat(fresh.isCancelled()).isTrue();
        assertThat(state()).isEqualTo(BlockStateRef.of("tfp:candle_cake/red"));
        assertThat(effects.taken).isEqualTo(1);
        assertThat(effects.sounds).containsExactly(CandleEffects.Sound.ADD_CANDLE);
        // creative keeps the candle
        player.setGameMode(GameMode.CREATIVE);
        blocks.place(at, BlockStateRef.of("tfp:cake").with("bites", "0"), true);
        listener.onUse(click(new ItemStack(Material.CANDLE), BlockFace.UP));
        assertThat(state()).isEqualTo(BlockStateRef.of("tfp:candle_cake"));
        assertThat(effects.taken).isEqualTo(1);
    }

    @Test
    void aKnifeCarvesAFreshPumpkin() {
        AtomicBoolean rotten = new AtomicBoolean(false);
        CandleListener listener = new CandleListener(CandleMockBukkitTest::itemId,
            s -> s.getType() == Material.IRON_SWORD, blocks, service, effects, (b, f, p, s) -> { }, () -> true,
            () -> b -> rotten.get());
        blocks.place(at, BlockStateRef.of("tfp:pumpkin"), true);
        PlayerInteractEvent side = click(new ItemStack(Material.IRON_SWORD), BlockFace.NORTH);
        listener.onUse(side);
        assertThat(side.isCancelled()).isTrue();
        assertThat(carved()).contains("north");
        assertThat(effects.damaged).isEqualTo(1);
        assertThat(effects.sounds).containsExactly(CandleEffects.Sound.CARVE);

        // on the top the carved face looks at the player
        blocks.place(at, BlockStateRef.of("tfp:pumpkin"), true);
        player.setRotation(0f, 0f); // yaw 0 faces south
        listener.onUse(click(new ItemStack(Material.IRON_SWORD), BlockFace.UP));
        assertThat(carved()).contains("north");

        // rotten: consumed, not carved; sneaking: the block use is skipped
        blocks.place(at, BlockStateRef.of("tfp:pumpkin"), true);
        rotten.set(true);
        PlayerInteractEvent bad = click(new ItemStack(Material.IRON_SWORD), BlockFace.EAST);
        listener.onUse(bad);
        assertThat(bad.isCancelled()).isTrue();
        assertThat(state().id()).isEqualTo("tfp:pumpkin");
        rotten.set(false);
        player.setSneaking(true);
        PlayerInteractEvent sneaking = click(new ItemStack(Material.IRON_SWORD), BlockFace.EAST);
        listener.onUse(sneaking);
        assertThat(sneaking.isCancelled()).isFalse();
        assertThat(effects.damaged).isEqualTo(2);
    }

    @Test
    void aHeldTorchLightsCandlesAndCarvedPumpkinsInsteadOfPlacing() {
        List<Block> started = new ArrayList<>();
        CandleListener listener = listener(started);
        blocks.place(at, candle("tfp:candle", 1, false), true);
        PlayerInteractEvent onCandle = click(new ItemStack(Material.TORCH), BlockFace.UP);
        listener.onUse(onCandle);
        assertThat(onCandle.isCancelled()).isTrue();
        assertThat(state().properties()).containsEntry("lit", "true");

        // a waterlogged candle is not lit: the torch is placed
        blocks.place(at, candle("tfp:candle", 1, false).with("waterlogged", "true"), true);
        PlayerInteractEvent wet = click(new ItemStack(Material.TORCH), BlockFace.UP);
        listener.onUse(wet);
        assertThat(wet.isCancelled()).isFalse();

        blocks.remove(at);
        BlockData pumpkin = Material.CARVED_PUMPKIN.createBlockData();
        ((Directional) pumpkin).setFacing(BlockFace.NORTH);
        at.setBlockData(pumpkin);
        PlayerInteractEvent onPumpkin = click(new ItemStack(Material.TORCH), BlockFace.UP);
        listener.onUse(onPumpkin);
        assertThat(onPumpkin.isCancelled()).isTrue();
        assertThat(state()).isEqualTo(BlockStateRef.of("tfp:jack_o_lantern").with("facing", "north"));

        // adventure: no item use on blocks
        player.setGameMode(GameMode.ADVENTURE);
        PlayerInteractEvent adventure = click(new ItemStack(Material.TORCH), BlockFace.UP);
        listener.onUse(adventure);
        assertThat(adventure.isCancelled()).isFalse();
        assertThat(started).hasSize(2);
    }

    @Test
    void theModuleComesAfterItsDependenciesInTheServiceFile() throws IOException {
        Path file = Path.of("src/main/resources/META-INF/services/dev.tfp.paper.module.FeatureModule");
        List<String> lines = Files.readAllLines(file).stream().map(String::trim).filter(l -> !l.isEmpty()).toList();
        int candle = lines.indexOf(CandleModule.class.getName());
        assertThat(candle).isPositive();
        for (String dep : List.of("dev.tfp.paper.tags.TagsModule", "dev.tfp.paper.device.DeviceModule",
                "dev.tfp.paper.catchup.CatchUpModule", "dev.tfp.paper.fire.FireModule")) {
            assertThat(lines.indexOf(dep)).as(dep).isBetween(0, candle - 1);
        }
    }

    private static Optional<String> itemId(ItemStack s) {
        return switch (s.getType()) {
            case TORCH -> Optional.of("tfp:torch");
            case CANDLE -> Optional.of("tfp:candle");
            case RED_CANDLE -> Optional.of("tfp:candle/red");
            default -> Optional.empty();
        };
    }

    private CandleListener listener(List<Block> started) {
        return new CandleListener(CandleMockBukkitTest::itemId, s -> false, blocks, service, effects,
            (block, face, p, stack) -> {
                started.add(block);
                service.light(block);
            }, () -> true, () -> b -> false);
    }

    private PlayerInteractEvent click(ItemStack stack, BlockFace face) {
        return new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, stack, at, face, EquipmentSlot.HAND);
    }
}
