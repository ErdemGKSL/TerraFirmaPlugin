/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.largevessel;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.device.DeviceRecord;
import dev.tfp.core.device.ItemBlob;
import dev.tfp.core.food.FoodConfig;
import dev.tfp.core.food.FoodState;
import dev.tfp.core.food.FoodTrait;
import dev.tfp.core.largevessel.LargeVesselState;
import dev.tfp.core.persist.DataMap;
import dev.tfp.core.vessel.LargeVesselRules;
import dev.tfp.paper.device.DeviceKind;
import dev.tfp.paper.device.DeviceKinds;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.device.DeviceStore;
import dev.tfp.paper.device.StackCodec;
import dev.tfp.paper.food.FoodPdc;
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * PAR-POT-06 on MockBukkit with fakes of the CraftEngine adapter and the world effects: the click order (sneak seals, any
 * other click opens), sealing and opening apply and remove {@code preserved} on every food stack, the slot filter, the
 * redstone seal, placement from a sealed item, the screen link refusing edits while sealed, and the record kept on the item.
 */
class LargeVesselMockBukkitTest extends MockServerTest {
    static final long START = 100_000;
    static final String RED = "tfp:ceramic/large_vessel/red";

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

    static final class Effects implements LargeVesselEffects {
        final List<Boolean> shown = new ArrayList<>();
        final List<Boolean> sounds = new ArrayList<>();

        @Override
        public void show(Block block, boolean sealed) {
            shown.add(sealed);
        }

        @Override
        public void sound(Block block, boolean sealed) {
            sounds.add(sealed);
        }
    }

    private final FakeCustomBlocks blocks = new FakeCustomBlocks().known(LargeVesselDevice.ID).known(RED);
    private final MemoryStore store = new MemoryStore();
    private final TableCodec stacks = new TableCodec();
    private final Effects effects = new Effects();
    private final AtomicLong clock = new AtomicLong(START);
    private final Set<FakeCustomBlocks.At> powered = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean redstone = new AtomicBoolean(true);
    private final List<Block> opened = new ArrayList<>();
    private DeviceService service;
    private DeviceKind<LargeVesselState> kind;
    private LargeVesselDevice.Services services;
    private World world;
    private Block vessel;
    private PlayerMock player;

    @BeforeEach
    void setUp() {
        world = server.addSimpleWorld("largevessel");
        service = new DeviceService(new DeviceKinds(), blocks, () -> store, clock::get, stacks, new FakeCustomItems(),
            Logger.getLogger("test"));
        services = new LargeVesselDevice.Services(redstone::get, s -> s.getType() != Material.ANVIL,
            b -> powered.contains(FakeCustomBlocks.at(b)), this::preserve, effects, (p, b) -> opened.add(b));
        kind = service.register(LargeVesselDevice.kind(services));
        vessel = world.getBlockAt(0, 64, 0);
        blocks.place(vessel, LargeVesselDevice.state(RED, "north", LargeVesselState.FRESH), true);
        store.data.put(FakeCustomBlocks.at(vessel), new DataMap());
        player = server.addPlayer();
        player.teleport(new Location(world, 3.5, 64, 0.5));
        player.setGameMode(GameMode.SURVIVAL);
    }

    /** TFC {@code FoodCapability.applyTrait} / {@code removeTrait} of {@code preserved}, decay modifier 1, at the test clock. */
    private ItemStack preserve(ItemStack stack, boolean sealing) {
        Optional<FoodState> food = FoodPdc.read(stack);
        food.ifPresent(f -> FoodPdc.write(stack, LargeVesselRules.sealFood(f, sealing, 1f, clock.get(), FoodConfig.DEFAULT)));
        return stack;
    }

    private ItemStack meat(long created) {
        ItemStack s = new ItemStack(Material.BEEF, 2);
        FoodPdc.write(s, new FoodState(created, List.of()));
        return s;
    }

    private DeviceRecord<LargeVesselState> record() {
        return kind.codec().decode(store.read(vessel).orElseThrow());
    }

    private void put(int slot, ItemStack stack) {
        service.edit(vessel, kind, (r, now) -> r.withSlot(slot, stacks.encode(stack)));
    }

    private ItemStack slot(int slot) {
        return stacks.decode(record().slot(slot));
    }

    private boolean click(boolean sneak, ItemStack held) {
        player.setSneaking(sneak);
        player.getInventory().setItem(EquipmentSlot.HAND, held);
        return service.use(vessel, player, EquipmentSlot.HAND, held == null ? ItemStack.empty() : held);
    }

    @Test
    void sneakClickTogglesTheSealAndAnyOtherClickOpensTheScreen() {
        assertThat(click(false, null)).isTrue();
        assertThat(opened).containsExactly(vessel);
        assertThat(click(false, new ItemStack(Material.BEEF))).isTrue();
        assertThat(opened).hasSize(2);
        assertThat(record().state().sealed()).isFalse();

        assertThat(click(true, new ItemStack(Material.BEEF))).isTrue(); // TFC: isShiftKeyDown, any item
        assertThat(record().state().sealed()).isTrue();
        assertThat(effects.shown).containsExactly(true);
        assertThat(effects.sounds).containsExactly(true);
        assertThat(opened).hasSize(2);
        assertThat(click(true, null)).isTrue();
        assertThat(record().state().sealed()).isFalse();
        assertThat(effects.shown).containsExactly(true, false);
        assertThat(effects.sounds).containsExactly(true, false);
    }

    @Test
    void sealingPreservesFoodAndOpeningTakesItOffAgain() {
        put(0, meat(START));
        put(4, new ItemStack(Material.COBBLESTONE, 5));
        clock.set(START + 1000);
        assertThat(click(true, null)).isTrue();

        FoodState sealed = FoodPdc.read(slot(0)).orElseThrow();
        assertThat(sealed.hasTrait(FoodTrait.PRESERVED)).isTrue();
        // TFC applyTrait moves the creation date to keep the decayed fraction: a later expiry than the unsealed food
        FoodState expected = LargeVesselRules.sealFood(new FoodState(START, List.of()), true, 1f, START + 1000, FoodConfig.DEFAULT);
        assertThat(sealed).isEqualTo(expected);
        assertThat(FoodPdc.read(slot(4))).isEmpty();
        assertThat(slot(4).getAmount()).isEqualTo(5);

        clock.set(START + 5000);
        assertThat(click(true, null)).isTrue();
        FoodState open = FoodPdc.read(slot(0)).orElseThrow();
        assertThat(open.hasTrait(FoodTrait.PRESERVED)).isFalse();
    }

    @Test
    void theSlotFilterIsTheLargeVesselsSizeRule() {
        assertThat(kind.slots()).isEqualTo(9);
        assertThat(kind.filter().canInsert(0, new ItemStack(Material.BEEF))).isTrue();
        assertThat(kind.filter().canInsert(8, new ItemStack(Material.ANVIL))).isFalse();
    }

    @Test
    void aSignalSealsAndTheEndOfItOpensTheVessel() {
        put(2, meat(START));
        powered.add(FakeCustomBlocks.at(vessel));
        service.neighbourChanged(vessel);
        assertThat(record().state()).isEqualTo(new LargeVesselState(true, true));
        assertThat(FoodPdc.read(slot(2)).orElseThrow().hasTrait(FoodTrait.PRESERVED)).isTrue();
        assertThat(effects.shown).containsExactly(true);

        service.neighbourChanged(vessel); // nothing changed
        assertThat(effects.shown).hasSize(1);

        powered.clear();
        service.neighbourChanged(vessel);
        assertThat(record().state()).isEqualTo(LargeVesselState.FRESH);
        assertThat(FoodPdc.read(slot(2)).orElseThrow().hasTrait(FoodTrait.PRESERVED)).isFalse();
    }

    @Test
    void aSignalOnAHandSealedVesselOnlyRecordsPowered() {
        assertThat(click(true, null)).isTrue();
        put(0, meat(START));
        powered.add(FakeCustomBlocks.at(vessel));
        service.neighbourChanged(vessel);
        assertThat(record().state()).isEqualTo(new LargeVesselState(true, true));
        assertThat(effects.sounds).hasSize(1);
    }

    @Test
    void theRedstoneSealCanBeSwitchedOff() {
        redstone.set(false);
        powered.add(FakeCustomBlocks.at(vessel));
        service.neighbourChanged(vessel);
        assertThat(record().state()).isEqualTo(LargeVesselState.FRESH);
    }

    @Test
    void placingFromASealedItemShowsItSealedAndFollowsTheSignal() {
        DeviceRecord<LargeVesselState> saved = kind.fresh().withState(new LargeVesselState(true, false))
            .withSlot(0, stacks.encode(meat(START)));
        store.data.put(FakeCustomBlocks.at(vessel), kind.codec().encode(saved));
        powered.add(FakeCustomBlocks.at(vessel));
        service.placed(vessel, player, EquipmentSlot.HAND, new ItemStack(Material.CLAY));
        assertThat(record().state()).isEqualTo(new LargeVesselState(true, true));
        assertThat(effects.shown).containsExactly(true);
    }

    @Test
    void theScreenLinkRefusesEditsWhileSealedAndTogglesLikeTheBlock() {
        LargeVesselLink link = new LargeVesselLink(service, kind, services, scheduler(), vessel);
        put(0, meat(START));
        List<ItemStack> change = new ArrayList<>();
        for (int i = 0; i < LargeVesselRules.SLOTS; i++) {
            change.add(i == 1 ? new ItemStack(Material.BEEF, 3) : ItemStack.empty());
        }
        assertThat(link.commit(change)).isPresent();
        assertThat(slot(1).getAmount()).isEqualTo(3);
        assertThat(link.toggleSeal(player).orElseThrow().sealed()).isTrue();
        change.set(2, new ItemStack(Material.BEEF));
        assertThat(link.commit(change)).as("a sealed vessel takes no edit").isEmpty();
        assertThat(slot(2).isEmpty()).isTrue();
        assertThat(link.toggleSeal(player).orElseThrow().sealed()).isFalse();
    }

    @Test
    void breakingKeepsTheWholeRecordOnTheItem() {
        assertThat(kind.removal()).isEqualTo(DeviceKind.RemoveBehaviour.SAVE);
        assertThat(kind.covers("tfp:ceramic/large_vessel/black")).isTrue();
        assertThat(LargeVesselDevice.covers("tfp:ceramic/unfired_large_vessel")).isFalse();
    }

    @Test
    void facingFollowsTheLookDirection() {
        assertThat(LargeVesselListener.facing(0f)).isEqualTo("south");
        assertThat(LargeVesselListener.facing(90f)).isEqualTo("west");
        assertThat(LargeVesselListener.facing(180f)).isEqualTo("north");
        assertThat(LargeVesselListener.facing(-90f)).isEqualTo("east");
    }

    private dev.tfp.paper.scheduler.Scheduler scheduler() {
        return new dev.tfp.paper.scheduler.Scheduler() {
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
