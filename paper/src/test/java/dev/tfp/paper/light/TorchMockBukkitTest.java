/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.light;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.device.ItemBlob;
import dev.tfp.core.light.LightSourceRules;
import dev.tfp.core.persist.DataMap;
import dev.tfp.core.world.BlockPos;
import dev.tfp.paper.device.DeviceKinds;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.device.DeviceStore;
import dev.tfp.paper.device.StackCodec;
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
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * PAR-HEAT-10 on MockBukkit with fakes of the CraftEngine adapter: a placed torch is stamped, the chunk-load catch-up
 * swaps it for the dead torch once its burn time is over (and not before), the wall torch keeps its facing, relighting
 * restarts the burn time, a relight between decision and commit wins, the stick lighting, the use rules, dousing and the
 * held torch's weak start on a dead torch.
 *
 * <p>Stand-ins: stick = {@code #tfc:can_be_lit_on_torch}, vanilla torch = the {@code tfp:torch} item.
 */
class TorchMockBukkitTest extends MockServerTest {
    static final long START = 10_000;
    static final int DAY = 24_000;

    /** CraftEngine's block entity: present for a lit torch block (the tfp:device behaviour), gone with it. */
    final class Store implements DeviceStore {
        final Map<FakeCustomBlocks.At, DataMap> data = new ConcurrentHashMap<>();

        boolean device(Block block) {
            return blocks.stateAt(block).map(s -> s.id().equals("tfp:torch") || s.id().equals("tfp:wall_torch"))
                .orElse(false);
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

    final class Effects implements TorchEffects {
        final List<Long> callbacks = new ArrayList<>();
        final List<ItemStack> given = new ArrayList<>();
        int taken;

        @Override
        public void burnout(Block block) {
            service.burnout(block);
        }

        @Override
        public void burnoutIn(Block block, long ticks) {
            callbacks.add(ticks);
        }

        @Override
        public void takeOne(Player player, EquipmentSlot hand) {
            taken++;
        }

        @Override
        public void give(Player player, ItemStack stack) {
            given.add(stack);
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

    /** Region tasks; "a tick" runs what is queued. The test thread owns every region. */
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
    private final FakeCustomBlocks blocks = new FakeCustomBlocks()
        .known("tfp:torch", "tfp:wall_torch", "tfp:dead_torch", "tfp:dead_wall_torch");
    private final Store store = new Store();
    private final Effects effects = new Effects();
    private final AtomicLong clock = new AtomicLong(START);
    private DeviceService devices;
    private TorchService service;
    private World world;
    private Block at;
    private PlayerMock player;

    @BeforeEach
    void setUp() {
        world = server.addSimpleWorld("torch");
        world.loadChunk(0, 0);
        devices = new DeviceService(new DeviceKinds(), blocks, () -> store, clock::get, NO_SLOTS, new FakeCustomItems(),
            Logger.getLogger("torch-test"));
        TorchDevice device = new TorchDevice(new TorchDevice.Services(() -> LightSourceRules.DEFAULT_TORCH_TICKS,
            s -> s.getType() == Material.STICK, () -> new ItemStack(Material.TORCH), effects));
        device.kinds().forEach(devices::register);
        service = new TorchService(blocks, devices, device, clock::get, () -> LightSourceRules.DEFAULT_TORCH_TICKS,
            w -> regions, Runnable::run, new CommitBudget(1_000_000_000L, System::nanoTime));
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

    /** A player places the torch block (CraftEngine's placement, then the device's setPlacedBy). */
    private void place(BlockStateRef state) {
        blocks.place(at, state, true);
        devices.placed(at, player, EquipmentSlot.HAND, new ItemStack(Material.TORCH));
    }

    /** The chunk loads after the time passed unloaded: the lazy block index lists the torch. */
    private List<TorchService.Waiting> reload() {
        List<TorchService.Waiting> w = service.chunkLoaded(at.getChunk(), List.of(new BlockPos(at.getX(), at.getY(), at.getZ())));
        runTasks();
        return w;
    }

    private BlockStateRef state() {
        return blocks.stateAt(at).orElseThrow();
    }

    private long litTick() {
        return service.read(at).litTick();
    }

    @Test
    void placedTorchIsStampedAndQueuesItsBurnout() {
        place(BlockStateRef.of("tfp:torch"));
        assertThat(litTick()).isEqualTo(START);
        assertThat(effects.callbacks).containsExactly(86_401L);
    }

    @Test
    void catchUpAfterFourDaysUnloadedSwapsToTheDeadTorch() {
        place(BlockStateRef.of("tfp:torch"));
        clock.addAndGet(4L * DAY); // 96,000 calendar ticks > TFC torchTicks 86,400
        List<TorchService.Waiting> waiting = reload();
        assertThat(waiting).isEmpty();
        assertThat(state()).isEqualTo(BlockStateRef.of("tfp:dead_torch"));
        assertThat(service.burnouts()).isEqualTo(1);
        assertThat(store.read(at)).isEmpty(); // the dead torch has no counter
    }

    @Test
    void catchUpAfterThreeDaysKeepsTheTorchAndWaitsForTheRest() {
        place(BlockStateRef.of("tfp:torch"));
        clock.addAndGet(3L * DAY); // 72,000: TFC's torch lasts 86,400 ticks, 3.6 days
        List<TorchService.Waiting> waiting = reload();
        assertThat(state()).isEqualTo(BlockStateRef.of("tfp:torch"));
        assertThat(waiting).singleElement().satisfies(w -> assertThat(w.ticks()).isEqualTo(86_401L - 72_000L));
        // exactly at the limit it still burns (TFC: ticks > torchTicks), one tick later it is dead
        clock.set(START + 86_400);
        assertThat(service.callback(at)).isEqualTo(1);
        runTasks();
        assertThat(state()).isEqualTo(BlockStateRef.of("tfp:torch"));
        clock.set(START + 86_401);
        assertThat(service.callback(at)).isZero();
        runTasks();
        assertThat(state()).isEqualTo(BlockStateRef.of("tfp:dead_torch"));
    }

    @Test
    void aTorchPlacedWithoutAPlayerCountsAsBurntOutAsInTfc() {
        blocks.place(at, BlockStateRef.of("tfp:torch"), true); // a command: no setPlacedBy, TFC's Integer.MIN_VALUE counter
        devices.neighbourChanged(at); // the placement's own neighbour updates do not kill it
        runTasks();
        assertThat(state().id()).isEqualTo("tfp:torch");
        assertThat(reload()).isEmpty(); // its first check does (TFC: its first random tick)
        assertThat(state().id()).isEqualTo("tfp:dead_torch");
    }

    @Test
    void wallTorchKeepsItsFacingWhenItDies() {
        place(BlockStateRef.of("tfp:wall_torch").with("facing", "east"));
        clock.addAndGet(86_401);
        reload();
        assertThat(state()).isEqualTo(BlockStateRef.of("tfp:dead_wall_torch").with("facing", "east"));
    }

    @Test
    void relightingADeadTorchRestartsTheBurnTime() {
        blocks.place(at, BlockStateRef.of("tfp:dead_wall_torch").with("facing", "north"), true);
        clock.set(500_000);
        assertThat(service.light(at)).isTrue();
        assertThat(state()).isEqualTo(BlockStateRef.of("tfp:wall_torch").with("facing", "north"));
        assertThat(litTick()).isEqualTo(500_000);
        assertThat(effects.callbacks).containsExactly(86_401L);
        clock.set(500_000 + 86_400);
        reload();
        assertThat(state().id()).isEqualTo("tfp:wall_torch");
        clock.set(500_000 + 86_401);
        reload();
        assertThat(state().id()).isEqualTo("tfp:dead_wall_torch");
    }

    @Test
    void aFireStartOnALitTorchResetsItsCounter() {
        place(BlockStateRef.of("tfp:torch"));
        clock.addAndGet(80_000);
        assertThat(service.lightable().light(new dev.tfp.paper.fire.FireStartContext(at, BlockFace.UP, null,
            ItemStack.empty(), dev.tfp.core.fuel.FireStart.Strength.WEAK))).isTrue();
        assertThat(litTick()).isEqualTo(START + 80_000);
        clock.addAndGet(80_000); // dead by the first stamp, alive by the reset
        reload();
        assertThat(state().id()).isEqualTo("tfp:torch");
    }

    @Test
    void aRelightBetweenDecisionAndCommitWins() {
        place(BlockStateRef.of("tfp:torch"));
        clock.addAndGet(100_000);
        service.burnout(at); // snapshot taken and decided (dead); the commit waits for the region
        assertThat(tasks).isNotEmpty();
        service.light(at); // the counter moves on before the commit
        runTasks();
        assertThat(state().id()).isEqualTo("tfp:torch");
        assertThat(service.burnouts()).isZero();
    }

    @Test
    void aBrokenTorchIsNotRevivedAsADeadOne() {
        place(BlockStateRef.of("tfp:torch"));
        clock.addAndGet(100_000);
        service.burnout(at);
        blocks.remove(at);
        runTasks();
        assertThat(blocks.stateAt(at)).isEmpty();
    }

    @Test
    void aStickOnALitTorchBecomesATorchAndEveryClickIsConsumed() {
        place(BlockStateRef.of("tfp:torch"));
        ItemStack stick = new ItemStack(Material.STICK, 3);
        player.getInventory().setItem(EquipmentSlot.HAND, stick);
        assertThat(devices.use(at, player, EquipmentSlot.HAND, stick)).isTrue();
        assertThat(effects.taken).isEqualTo(1);
        assertThat(effects.given).extracting(ItemStack::getType).containsExactly(Material.TORCH);
        // TFC sidedSuccess: an empty hand or another item is consumed too, and nothing is given
        assertThat(devices.use(at, player, EquipmentSlot.HAND, ItemStack.empty())).isTrue();
        assertThat(devices.use(at, player, EquipmentSlot.HAND, new ItemStack(Material.DIRT))).isTrue();
        assertThat(effects.given).hasSize(1);
    }

    @Test
    void clickingABurntOutTorchSwapsItAndPasses() {
        place(BlockStateRef.of("tfp:torch"));
        clock.addAndGet(100_000);
        assertThat(devices.use(at, player, EquipmentSlot.HAND, new ItemStack(Material.STICK))).isFalse();
        assertThat(effects.given).isEmpty();
        runTasks();
        assertThat(state().id()).isEqualTo("tfp:dead_torch");
    }

    @Test
    void aNeighbourUpdatePastTheBurnTimeSwapsIt() {
        place(BlockStateRef.of("tfp:wall_torch").with("facing", "south"));
        clock.addAndGet(86_401);
        devices.neighbourChanged(at);
        runTasks();
        assertThat(state()).isEqualTo(BlockStateRef.of("tfp:dead_wall_torch").with("facing", "south"));
    }

    @Test
    void douseKillsALitTorchOnly() {
        place(BlockStateRef.of("tfp:wall_torch").with("facing", "west"));
        assertThat(service.douse(at)).isTrue();
        assertThat(state()).isEqualTo(BlockStateRef.of("tfp:dead_wall_torch").with("facing", "west"));
        assertThat(service.douse(at)).isFalse();
    }

    @Test
    void aNonTorchBlockInTheIndexIsLeftAlone() {
        blocks.place(at, BlockStateRef.of("tfp:dead_torch"), true);
        clock.addAndGet(1_000_000);
        assertThat(reload()).isEmpty();
        assertThat(state().id()).isEqualTo("tfp:dead_torch");
        assertThat(service.light(world.getBlockAt(9, 64, 9))).isFalse();
    }

    @Test
    void aHeldTorchRelightsADeadTorchInsteadOfPlacing() {
        blocks.place(at, BlockStateRef.of("tfp:dead_torch"), true);
        List<Block> started = new ArrayList<>();
        TorchListener listener = new TorchListener(
            s -> s.getType() == Material.TORCH ? Optional.of("tfp:torch") : Optional.empty(), blocks,
            (block, face, p, stack) -> {
                started.add(block);
                service.light(block);
            });
        ItemStack torch = new ItemStack(Material.TORCH);
        PlayerInteractEvent click = new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, torch, at, BlockFace.UP,
            EquipmentSlot.HAND);
        listener.onUse(click);
        assertThat(click.isCancelled()).isTrue();
        assertThat(started).containsExactly(at);
        assertThat(state().id()).isEqualTo("tfp:torch");

        // on a lit torch the held torch does nothing here (the torch's own use consumes the click)
        PlayerInteractEvent lit = new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, torch, at, BlockFace.UP,
            EquipmentSlot.HAND);
        listener.onUse(lit);
        assertThat(lit.isCancelled()).isFalse();

        // adventure mode: vanilla runs no item use on blocks
        blocks.place(at, BlockStateRef.of("tfp:dead_torch"), true);
        player.setGameMode(GameMode.ADVENTURE);
        PlayerInteractEvent adventure = new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, torch, at,
            BlockFace.UP, EquipmentSlot.HAND);
        listener.onUse(adventure);
        assertThat(adventure.isCancelled()).isFalse();
        assertThat(started).hasSize(1);

        // another item does nothing
        player.setGameMode(GameMode.CREATIVE);
        PlayerInteractEvent stick = new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK,
            new ItemStack(Material.STICK), at, BlockFace.UP, EquipmentSlot.HAND);
        listener.onUse(stick);
        assertThat(stick.isCancelled()).isFalse();
    }

    @Test
    void theModuleComesAfterItsDependenciesInTheServiceFile() throws IOException {
        Path file = Path.of("src/main/resources/META-INF/services/dev.tfp.paper.module.FeatureModule");
        List<String> lines = Files.readAllLines(file).stream().map(String::trim).filter(l -> !l.isEmpty()).toList();
        int torch = lines.indexOf(TorchModule.class.getName());
        assertThat(torch).isPositive();
        for (String dep : List.of("dev.tfp.paper.tags.TagsModule", "dev.tfp.paper.device.DeviceModule",
                "dev.tfp.paper.catchup.CatchUpModule", "dev.tfp.paper.fire.FireModule")) {
            assertThat(lines.indexOf(dep)).as(dep).isBetween(0, torch - 1);
        }
    }
}
