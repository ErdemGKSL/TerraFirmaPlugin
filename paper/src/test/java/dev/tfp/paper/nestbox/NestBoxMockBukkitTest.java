/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.nestbox;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.clock.ClockSnapshot;
import dev.tfp.core.device.DeviceRecord;
import dev.tfp.core.device.ItemBlob;
import dev.tfp.core.livestock.AnimalState;
import dev.tfp.core.livestock.BreedingRules;
import dev.tfp.core.livestock.LivestockSpecies;
import dev.tfp.core.livestock.products.EggRules;
import dev.tfp.core.livestock.products.NestRules;
import dev.tfp.core.livestock.products.ProductSpec;
import dev.tfp.core.livestock.products.ProductState;
import dev.tfp.core.nestbox.NestBoxRules;
import dev.tfp.core.nestbox.NestBoxState;
import dev.tfp.core.persist.DataMap;
import dev.tfp.paper.device.DeviceInventory;
import dev.tfp.paper.device.DeviceKind;
import dev.tfp.paper.device.DeviceKinds;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.device.DeviceStore;
import dev.tfp.paper.device.StackCodec;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.FakeCustomBlocks;
import dev.tfp.paper.integration.FakeCustomItems;
import dev.tfp.paper.testing.MockServerTest;
import dev.tfp.paper.tooltip.feature.EggPdc;
import dev.tfp.paper.tooltip.feature.FeatureData;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
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
import org.bukkit.entity.Item;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * PAR-FAUNA-07 on MockBukkit, with fakes of the hen, the world effects and the CraftEngine adapter: only fresh eggs go in
 * (one per slot), right click opens the screen unless sneaking, a sitting hen lays into the first free slot on the 30-tick
 * grid with TFC's draws, a full nest or a hen that is not ready stands her up, a plain egg is dropped, and due eggs hatch
 * into a chick at the nest and empty their slot.
 */
class NestBoxMockBukkitTest extends MockServerTest {
    static final long START = 10_000;
    /** Calendar tick of day 100 at player tick {@link #START}. */
    static final long DAY = 24_000L;
    static final long CAL0 = 100 * DAY;
    static final String HEN = "00000000-0000-0000-0000-0000000000aa";
    static final ProductSpec SPEC = ProductSpec.defaults(LivestockSpecies.CHICKEN).orElseThrow();

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

    record Laid(NestHens.Hen hen, AnimalState after, ProductState product) {
    }

    static final class Hens implements NestHens {
        final Map<String, Hen> sitting = new HashMap<>();
        final List<Laid> laid = new ArrayList<>();
        final List<String> released = new ArrayList<>();

        @Override
        public Optional<Hen> find(Block nest, String id) {
            return Optional.ofNullable(sitting.get(id));
        }

        @Override
        public void laid(Block nest, Hen hen, AnimalState after, ProductState product) {
            laid.add(new Laid(hen, after, product));
            sitting.remove(hen.id());
        }

        @Override
        public void release(Block nest, String id) {
            released.add(id);
            sitting.remove(id);
        }
    }

    record Hatch(String species, float familiarity) {
    }

    static final class Effects implements NestEffects {
        int sounds;
        final List<ItemStack> dropped = new ArrayList<>();
        final List<Hatch> hatched = new ArrayList<>();
        final List<Long> wakes = new ArrayList<>();
        int placed;
        int removed;

        @Override
        public void laidSound(Block nest) {
            sounds++;
        }

        @Override
        public void dropEgg(Block nest, ItemStack egg) {
            dropped.add(egg);
        }

        @Override
        public void hatch(Block nest, String species, float familiarity) {
            hatched.add(new Hatch(species, familiarity));
        }

        @Override
        public void wake(Block nest, long delayTicks) {
            wakes.add(delayTicks);
        }

        @Override
        public void placed(Block nest) {
            placed++;
        }

        @Override
        public void removed(Block nest) {
            removed++;
        }
    }

    /** Scripted TFC draws; a draw the TFC order does not make fails the test. */
    static final class Script implements NestRules.NestRandom {
        final Deque<Integer> rolls = new ArrayDeque<>();
        final Deque<Boolean> genders = new ArrayDeque<>();

        Script rolls(int... values) {
            for (int v : values) {
                rolls.add(v);
            }
            return this;
        }

        @Override
        public int nextInt(int bound) {
            assertThat(bound).isEqualTo(7);
            assertThat(rolls).as("roll script").isNotEmpty();
            return rolls.poll();
        }

        @Override
        public boolean nextBoolean() {
            return genders.isEmpty() || genders.poll();
        }
    }

    private final FakeCustomBlocks blocks = new FakeCustomBlocks().known(NestBoxDevice.ID);
    private final MemoryStore store = new MemoryStore();
    private final TableCodec stacks = new TableCodec();
    private final Effects effects = new Effects();
    private final Hens hens = new Hens();
    private final Script script = new Script();
    private final AtomicLong clock = new AtomicLong(START);
    private final List<Block> opened = new ArrayList<>();
    private final List<ItemStack> rotten = new ArrayList<>();
    private DeviceService service;
    private DeviceKind<NestBoxState> kind;
    private NestBoxDevice.Services services;
    private World world;
    private Block nest;
    private PlayerMock player;

    @BeforeEach
    void setUp() {
        world = server.addSimpleWorld("nestbox");
        service = new DeviceService(new DeviceKinds(), blocks, () -> store, clock::get, stacks, new FakeCustomItems(),
            Logger.getLogger("test"));
        services = new NestBoxDevice.Services(s -> rotten.stream().anyMatch(r -> r.isSimilar(s)),
            () -> new ClockSnapshot(1, clock.get(), CAL0 + (clock.get() - START), 8, true), () -> script, hens, effects,
            (p, b) -> opened.add(b));
        kind = service.register(NestBoxDevice.kind(services));
        nest = world.getBlockAt(0, 64, 0);
        blocks.place(nest, BlockStateRef.of(NestBoxDevice.ID), true);
        store.data.put(FakeCustomBlocks.at(nest), new DataMap());
        player = server.addPlayer();
        player.teleport(new Location(world, 3.5, 64, 0.5));
        player.setGameMode(GameMode.SURVIVAL);
    }

    private DeviceRecord<NestBoxState> record() {
        return kind.codec().decode(store.read(nest).orElseThrow());
    }

    private static NestHens.Hen hen(boolean fertilized, long producedTick) {
        AnimalState state = new AnimalState(false, 0.5f, 0L, -10_000_000L, AnimalState.NO_OLD_TICK, AnimalState.NEVER,
            AnimalState.NEVER, 3, fertilized, AnimalState.NOT_PREGNANT);
        return new NestHens.Hen(HEN, "tfc:chicken", LivestockSpecies.CHICKEN, SPEC, state, new ProductState(producedTick));
    }

    private void sit(NestHens.Hen hen) {
        hens.sitting.put(hen.id(), hen);
        service.edit(nest, kind, (r, now) -> r.withState(r.state().seat(hen.id(), now)));
    }

    private static ItemStack egg(boolean fertilized, long hatchDay) {
        ItemStack stack = new ItemStack(Material.EGG);
        EggPdc.write(stack, new FeatureData.Egg(fertilized, hatchDay));
        return stack;
    }

    private void put(int slot, ItemStack stack) {
        service.edit(nest, kind, (r, now) -> r.withSlot(slot, stacks.encode(stack)));
    }

    private ItemStack slot(int i) {
        return stacks.decode(record().slot(i));
    }

    private void tick(long to) {
        clock.set(to);
        service.neighbourChanged(nest);
    }

    @Test
    void onlyFreshEggsGoInOnePerSlot() {
        assertThat(kind.slots()).isEqualTo(4);
        assertThat(kind.filter().canInsert(0, egg(true, 108))).isTrue();
        assertThat(kind.filter().canInsert(0, egg(false, 0))).as("an unfertilized egg record is an egg").isTrue();
        assertThat(kind.filter().canInsert(0, new ItemStack(Material.EGG))).as("plain egg").isFalse();
        assertThat(kind.filter().canInsert(0, new ItemStack(Material.COBBLESTONE))).isFalse();
        ItemStack bad = egg(true, 109);
        rotten.add(bad);
        assertThat(kind.filter().canInsert(0, bad)).as("rotten").isFalse();

        ItemStack three = egg(true, 108);
        three.setAmount(3);
        DeviceInventory<NestBoxState> inv = new DeviceInventory<>(kind, kind.fresh(), stacks);
        DeviceInventory.Insert<NestBoxState> r = inv.insert(0, three);
        assertThat(r.inventory().get(0).getAmount()).isEqualTo(1);
        assertThat(r.remainder().getAmount()).isEqualTo(2);
        DeviceInventory.Insert<NestBoxState> whole = inv.insert(new ItemStack(Material.COBBLESTONE, 2));
        assertThat(whole.remainder().getAmount()).isEqualTo(2);
    }

    @Test
    void rightClickOpensTheScreenUnlessSneaking() {
        player.setSneaking(false);
        assertThat(service.use(nest, player, EquipmentSlot.HAND, ItemStack.empty())).isTrue();
        assertThat(opened).containsExactly(nest);
        player.setSneaking(true);
        assertThat(service.use(nest, player, EquipmentSlot.HAND, new ItemStack(Material.EGG))).isFalse();
        assertThat(opened).hasSize(1);
    }

    @Test
    void aSittingFertilizedHenLaysIntoTheFirstFreeSlotOnTheCheckGrid() {
        put(0, egg(true, 500)); // slot 0 taken
        sit(hen(true, 0L)); // seated at START = 10000, first check 10020
        script.rolls(3, 5, 0); // 10020, 10050 miss; 10080 lays
        tick(START + 200);

        assertThat(script.rolls).isEmpty();
        assertThat(effects.sounds).isEqualTo(1);
        ItemStack laid = slot(1);
        assertThat(laid.getType()).isEqualTo(Material.EGG);
        FeatureData.Egg record = EggPdc.read(laid).orElseThrow();
        assertThat(record.fertilized()).isTrue();
        long layTick = START + 80;
        assertThat(record.hatchDay()).isEqualTo(BreedingRules.eggHatchDay(CAL0 + 80, LivestockSpecies.CHICKEN));
        assertThat(NestEggs.chickOf(laid).species()).isEqualTo("tfc:chicken");
        assertThat(NestEggs.chickOf(laid).familiarity()).isEqualTo(0.25f);
        assertThat(slot(0).getType()).isEqualTo(Material.EGG);

        assertThat(hens.laid).hasSize(1);
        assertThat(hens.laid.getFirst().after().fertilized()).isFalse();
        assertThat(hens.laid.getFirst().after().uses()).isEqualTo(4);
        assertThat(hens.laid.getFirst().product().producedTick()).isEqualTo(layTick);
        assertThat(record().state()).isEqualTo(NestBoxState.FRESH);
    }

    @Test
    void anUnfertilizedHenDropsAPlainEggAndTheSlotsStayFree() {
        sit(hen(false, 0L));
        script.rolls(0);
        tick(START + 30);
        assertThat(effects.dropped).hasSize(1);
        assertThat(effects.dropped.getFirst().getType()).isEqualTo(Material.EGG);
        assertThat(EggPdc.read(effects.dropped.getFirst())).isEmpty();
        assertThat(record().nonEmptySlots()).isEmpty();
        assertThat(hens.laid).hasSize(1);
    }

    @Test
    void aFullNestStandsTheHenUpWithoutLaying() {
        for (int i = 0; i < 4; i++) {
            put(i, egg(true, 999));
        }
        sit(hen(true, 0L));
        script.rolls(0);
        tick(START + 30);
        assertThat(hens.released).containsExactly(HEN);
        assertThat(hens.laid).isEmpty();
        assertThat(effects.sounds).isZero();
        assertThat(record().state().occupied()).isFalse();
        assertThat(record().nonEmptySlots()).hasSize(4);
    }

    @Test
    void aHenThatIsNotReadyStandsUpAtTheFirstCheck() {
        sit(hen(true, START - 100)); // laid a moment ago: cooldown
        tick(START + 30);
        assertThat(hens.released).containsExactly(HEN);
        assertThat(hens.laid).isEmpty();
        assertThat(record().state().occupied()).isFalse();
    }

    @Test
    void aMissedRollKeepsHerSeatedAndEachCheckIsRolledOnce() {
        sit(hen(true, 0L));
        script.rolls(4, 4);
        tick(START + 40); // check 10020 rolled
        assertThat(script.rolls).hasSize(1);
        assertThat(record().state().occupied()).isTrue();
        assertThat(effects.wakes).contains(NestBoxRules.delayToCheck(START + 40, 1));
        tick(START + 41); // no new check: nothing is rolled
        tick(START + 49);
        assertThat(script.rolls).hasSize(1);
        script.rolls(0); // 10050 lays (the script's first remaining 4 is used at 10050)
        tick(START + 60);
        assertThat(script.rolls).hasSize(1);
        assertThat(hens.laid).isEmpty();
    }

    @Test
    void aHenThatIsGoneIsForgotten() {
        service.edit(nest, kind, (r, now) -> r.withState(r.state().seat(HEN, now)));
        tick(START + 100);
        assertThat(record().state().occupied()).isFalse();
        assertThat(hens.laid).isEmpty();
    }

    @Test
    void aDueFertilizedEggHatchesAtTheNestAndTheSlotEmpties() {
        ItemStack chick = NestEggs.fertile("tfc:duck", new EggRules.FertileEgg(108, LivestockSpecies.DUCK,
            AnimalState.offspring(true, 0.4f, START)));
        put(2, chick);
        put(0, egg(false, 90)); // unfertilized: never
        ItemStack stale = egg(true, 90);
        rotten.add(stale);
        put(3, stale); // rotten: never
        tick(START + 10);
        assertThat(effects.hatched).isEmpty();
        assertThat(effects.wakes).as("the hatch is queued").isNotEmpty();
        // day 108 begins DAY * 8 ticks after day 100
        tick(START + 8 * DAY + 5);
        assertThat(effects.hatched).containsExactly(new Hatch("tfc:duck", 0.4f));
        assertThat(slot(2).isEmpty()).isTrue();
        assertThat(slot(0).getType()).isEqualTo(Material.EGG);
        assertThat(slot(3).getType()).isEqualTo(Material.EGG);
        tick(START + 9 * DAY);
        assertThat(effects.hatched).hasSize(1);
    }

    @Test
    void anEggWithNoChickRecordHatchesAsAChicken() {
        put(1, egg(true, 100));
        tick(START + 1);
        assertThat(effects.hatched).containsExactly(new Hatch("tfc:chicken", 0f));
        assertThat(record().nonEmptySlots()).isEmpty();
    }

    @Test
    void theHatchHappensWhenTheScreenOpensAfterATimeSkip() {
        put(0, NestEggs.fertile("tfc:chicken", new EggRules.FertileEgg(110, LivestockSpecies.CHICKEN,
            AnimalState.offspring(false, 0.1f, START))));
        clock.set(START + 20 * DAY); // tfp time add days 20
        assertThat(service.use(nest, player, EquipmentSlot.HAND, ItemStack.empty())).isTrue();
        assertThat(effects.hatched).hasSize(1);
        assertThat(record().nonEmptySlots()).isEmpty();
        assertThat(opened).containsExactly(nest);
    }

    @Test
    void removalReleasesTheSitterAndDropsTheEggs() {
        put(0, egg(true, 999));
        sit(hen(true, 0L));
        DataMap last = store.read(nest).orElseThrow();
        blocks.remove(nest);
        service.removed(world, nest.getX(), nest.getY(), nest.getZ(), NestBoxDevice.ID, last);
        assertThat(hens.released).containsExactly(HEN);
        assertThat(effects.removed).isEqualTo(1);
        List<Item> drops = List.copyOf(world.getEntitiesByClass(Item.class));
        assertThat(drops).hasSize(1);
        assertThat(drops.getFirst().getItemStack().getType()).isEqualTo(Material.EGG);
    }

    @Test
    void placementRegistersTheNestForHens() {
        service.placed(nest, player, EquipmentSlot.HAND, new ItemStack(Material.EGG));
        assertThat(effects.placed).isEqualTo(1);
    }
}
