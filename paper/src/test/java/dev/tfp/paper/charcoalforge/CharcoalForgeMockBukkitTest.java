/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.charcoalforge;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.charcoalforge.CharcoalForgeData;
import dev.tfp.core.charcoalforge.CharcoalForgeRules;
import dev.tfp.core.charcoalforge.CharcoalForgeSim;
import dev.tfp.core.device.DeviceRecord;
import dev.tfp.core.device.ItemBlob;
import dev.tfp.core.fuel.Fuel;
import dev.tfp.core.heat.HeatConfig;
import dev.tfp.core.persist.DataMap;
import dev.tfp.core.alloy.FluidAlloy;
import dev.tfp.core.vessel.FluidAmount;
import dev.tfp.paper.crucible.CrucibleMolds;
import dev.tfp.paper.device.DeviceKind;
import dev.tfp.paper.device.DeviceKinds;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.device.DeviceStore;
import dev.tfp.paper.device.StackCodec;
import dev.tfp.paper.firebox.HeatConsumers;
import dev.tfp.paper.heat.HeatPdc;
import dev.tfp.paper.heat.ItemHeatHooks;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.FakeCustomBlocks;
import dev.tfp.paper.integration.FakeCustomItems;
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
 * PAR-METAL-08 on MockBukkit, with fakes of the CraftEngine adapter and the world effects: lighting and creation, an
 * item heating without clicks (the self-test {@code forge.continuous} rule), the heat given to a consumer above, a recipe
 * that fires in the forge, the interim clicks and the slot rules. Charcoal (1800 ticks, 1350 C) is the fuel.
 */
class CharcoalForgeMockBukkitTest extends MockServerTest {
    static final long START = 10_000;
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

    static final class Effects implements CharcoalForgeEffects {
        Integer shown;
        final List<Long> callbacks = new ArrayList<>();
        final List<ItemStack> given = new ArrayList<>();
        final List<TestDrop> drops = new ArrayList<>();

        @Override
        public List<Drop> dropped(Block block) {
            return new ArrayList<>(drops);
        }

        @Override
        public void show(Block block, int heatLevel) {
            shown = heatLevel;
        }

        @Override
        public void give(Player player, ItemStack stack) {
            given.add(stack);
        }

        @Override
        public void callbackIn(Block block, long ticks) {
            callbacks.add(ticks);
        }
    }

    /** A dropped item entity: a stack that shrinks as it is taken. */
    static final class TestDrop implements CharcoalForgeEffects.Drop {
        final ItemStack stack;

        TestDrop(ItemStack stack) {
            this.stack = stack;
        }

        @Override
        public ItemStack stack() {
            return stack;
        }

        @Override
        public int count() {
            return stack.getAmount();
        }

        @Override
        public void take(int n) {
            stack.setAmount(stack.getAmount() - n);
        }
    }

    /** Buckets are molds that hold one mB per lore line (an amount of up to 100 mB), at most 100 mB. */
    static final class FakeMolds implements CrucibleMolds {
        @Override
        public boolean isMold(ItemStack stack) {
            return stack.getType() == Material.BUCKET;
        }

        /** A decorated pot stands for a small vessel: it takes the whole fitting amount of a pour at once (3000 mB). */
        @Override
        public boolean isVessel(ItemStack stack) {
            return stack.getType() == Material.DECORATED_POT;
        }

        @Override
        public int pour(FluidAmount fluid, float temperature, ItemStack container) {
            if (!isVessel(container)) {
                return CrucibleMolds.super.pour(fluid, temperature, container);
            }
            int now = content(container).amount();
            int take = Math.min(fluid.amount(), 3000 - now);
            var meta = container.getItemMeta();
            meta.setLore(List.of(Integer.toString(now + take), Float.toString(temperature)));
            container.setItemMeta(meta);
            return take;
        }

        @Override
        public boolean isMolten(ItemStack mold) {
            return false;
        }

        @Override
        public FluidAmount content(ItemStack mold) {
            return new FluidAmount("tfc:metal/gold", mold.hasItemMeta() && mold.getItemMeta().hasLore()
                ? Integer.parseInt(mold.getItemMeta().getLore().getFirst()) : 0);
        }

        @Override
        public int pourStep(FluidAmount fluid, float temperature, ItemStack mold) {
            int now = content(mold).amount();
            if (now >= 100) {
                return 0;
            }
            var meta = mold.getItemMeta();
            meta.setLore(List.of(Integer.toString(now + 1), Float.toString(temperature)));
            mold.setItemMeta(meta);
            return 1;
        }

        @Override
        public boolean drainOne(ItemStack mold) {
            return false;
        }

        @Override
        public String result(FluidAlloy alloy) {
            return "";
        }
    }

    record Heat(long from, long to, float temperature) {
    }

    private final FakeCustomBlocks blocks = new FakeCustomBlocks().known(CharcoalForgeDevice.ID, "tfp:crucible");
    private final MemoryStore store = new MemoryStore();
    private final TableCodec stacks = new TableCodec();
    private final Effects effects = new Effects();
    private final AtomicLong clock = new AtomicLong(START);
    private final HeatConsumers consumers = new HeatConsumers();
    private final List<Heat> heat = new ArrayList<>();
    private final List<Block> opened = new ArrayList<>();
    private DeviceService service;
    private CharcoalForgeDevice device;
    private DeviceKind<CharcoalForgeData> kind;
    private Block forge;
    private PlayerMock player;

    @BeforeEach
    void setUp() {
        World world = server.addSimpleWorld("charcoalforge");
        service = new DeviceService(new DeviceKinds(), blocks, () -> store, clock::get, stacks, new FakeCustomItems(),
            Logger.getLogger("test"));
        ItemHeatHooks.Capacities capacities = s -> switch (s.getType()) {
            case IRON_INGOT, RAW_COPPER, COPPER_INGOT, GOLD_INGOT, BUCKET -> 0.5f;
            default -> 0f;
        };
        device = new CharcoalForgeDevice(new CharcoalForgeDevice.Services(
            s -> s.getType() == Material.CHARCOAL ? Optional.of(CHARCOAL) : Optional.empty(),
            s -> s.getType() == Material.CHARCOAL,
            capacities,
            s -> s.getType() == Material.BUCKET,
            new CharcoalForgeDevice.Converter() {
                @Override
                public float recipeTemperature(ItemStack stack) {
                    return stack.getType() == Material.RAW_COPPER ? 1000f : stack.getType() == Material.GOLD_INGOT ? 800f
                        : Float.NaN;
                }

                @Override
                public Optional<CharcoalForgeDevice.Melted> melt(ItemStack heated, float temperature) {
                    if (heated.getType() == Material.GOLD_INGOT && temperature >= 800f) {
                        return Optional.of(new CharcoalForgeDevice.Melted(ItemStack.empty(),
                            new FluidAmount("tfc:metal/gold", 100)));
                    }
                    return convert(heated, temperature).map(i -> new CharcoalForgeDevice.Melted(i, FluidAmount.EMPTY));
                }

                @Override
                public Optional<ItemStack> convert(ItemStack heated, float temperature) {
                    return heated.getType() == Material.RAW_COPPER && temperature >= 1000f
                        ? Optional.of(new ItemStack(Material.COPPER_INGOT)) : Optional.empty();
                }
            },
            HeatConfig.DEFAULT,
            b -> false,
            consumers,
            b -> blocks.stateAt(b).map(BlockStateRef::id),
            effects,
            stack -> {
                var meta = stack.getItemMeta();
                meta.setLore(List.of("grilled"));
                stack.setItemMeta(meta);
                return stack;
            },
            new FakeMolds()), stacks);
        kind = service.register(device.kind());
        forge = world.getBlockAt(0, 64, 0);
        blocks.place(forge, CharcoalForgeDevice.state(0), true);
        store.data.put(FakeCustomBlocks.at(forge), new DataMap());
        player = server.addPlayer();
        player.teleport(new Location(world, 3.5, 64, 0.5));
        player.setGameMode(GameMode.SURVIVAL);
    }

    private DeviceRecord<CharcoalForgeData> record() {
        return kind.codec().decode(store.read(forge).orElseThrow());
    }

    private void put(int slot, ItemStack stack) {
        service.edit(forge, kind, (r, now) -> r.withSlot(slot, stacks.encode(stack)));
    }

    private float itemTemperature(int slot) {
        return HeatPdc.read(stacks.decode(record().slot(slot))).orElseThrow().temperature();
    }

    private boolean click(boolean sneak, ItemStack held) {
        player.setSneaking(sneak);
        player.getInventory().setItem(EquipmentSlot.HAND, held);
        return service.use(forge, player, EquipmentSlot.HAND, held == null ? ItemStack.empty() : held);
    }

    @Test
    void anIngotLeftInTheForgeReachesTheForgeTemperatureWithoutClicks() {
        put(0, new ItemStack(Material.CHARCOAL));
        put(5, new ItemStack(Material.IRON_INGOT));
        assertThat(device.lightAt(service, forge)).isTrue();
        assertThat(record().state().state().lit()).isTrue();
        assertThat(record().slot(0).isEmpty()).as("the fuel was taken").isTrue();
        // 60 seconds later, nobody touched it: the next access replays the heating
        clock.set(START + 1200);
        service.neighbourChanged(forge);
        assertThat(record().state().temperature()).isEqualTo(1200f);
        assertThat(itemTemperature(5)).isEqualTo(1200f);
        clock.set(START + 1700);
        service.neighbourChanged(forge);
        assertThat(record().state().temperature()).isEqualTo(1350f);
        assertThat(itemTemperature(5)).isEqualTo(1350f);
        assertThat(effects.shown).isEqualTo(CharcoalForgeRules.heatLevel(1350f));
    }

    @Test
    void anUnlitForgeDoesNotHeatAndLightingNeedsFuel() {
        put(5, new ItemStack(Material.IRON_INGOT));
        assertThat(device.lightAt(service, forge)).as("no fuel").isFalse();
        clock.set(START + 500);
        service.neighbourChanged(forge);
        assertThat(HeatPdc.read(stacks.decode(record().slot(5)))).isEmpty();
        assertThat(record().state().state().lit()).isFalse();
    }

    @Test
    void aForgeBuiltFromACharcoalPileStartsLitWithTheFirstBurn() {
        device.createFromPile(service, forge);
        CharcoalForgeData d = record().state();
        assertThat(d.state().lit()).isTrue();
        assertThat(d.state().burnTicks()).isEqualTo(200);
        assertThat(d.state().burnTemperature()).isEqualTo(500f);
        assertThat(effects.shown).isEqualTo(1);
        assertThat(effects.callbacks).isNotEmpty();
        // with no fuel it burns out after 200 ticks and the display goes to 0
        blocks.place(forge, CharcoalForgeDevice.state(1), true); // what the effects wrote
        clock.set(START + 200);
        service.neighbourChanged(forge);
        assertThat(record().state().state().lit()).isFalse();
        assertThat(effects.shown).isZero();
    }

    @Test
    void anItemFiresItsRecipeInTheForgeAndTheOutputKeepsHeating() {
        device.createFromPile(service, forge);
        put(0, new ItemStack(Material.CHARCOAL));
        put(5, new ItemStack(Material.RAW_COPPER));
        service.edit(forge, kind, (r, now) -> r.withState(r.state().withState(CharcoalForgeRules.light(
            r.state().state(), CHARCOAL).state())));
        clock.set(START + 2000);
        service.neighbourChanged(forge);
        assertThat(stacks.decode(record().slot(5)).getType()).isEqualTo(Material.COPPER_INGOT);
    }

    private void fire(ItemStack input) {
        device.createFromPile(service, forge);
        put(0, new ItemStack(Material.CHARCOAL));
        put(5, input);
        clock.set(START + 2000);
        service.neighbourChanged(forge);
    }

    @Test
    void aMeltedIngotFillsTheMoldInAnExtraSlotAndTheInputEmpties() {
        put(11, new ItemStack(Material.BUCKET));
        fire(new ItemStack(Material.GOLD_INGOT));
        assertThat(record().slot(5).isEmpty()).isTrue();
        ItemStack mold = stacks.decode(record().slot(11));
        assertThat(new FakeMolds().content(mold).amount()).isEqualTo(100);
    }

    @Test
    void aMeltedIngotWithNoMoldIsLost() {
        fire(new ItemStack(Material.GOLD_INGOT));
        assertThat(record().slot(5).isEmpty()).isTrue();
    }

    @Test
    void aFullMoldPassesTheRestOnToTheNextMold() {
        put(10, new ItemStack(Material.BUCKET));
        put(12, new ItemStack(Material.BUCKET));
        service.edit(forge, kind, (r, now) -> {
            ItemStack full = stacks.decode(r.slot(10));
            for (int i = 0; i < 70; i++) {
                new FakeMolds().pourStep(new FluidAmount("tfc:metal/gold", 1), 0f, full);
            }
            return r.withSlot(10, stacks.encode(full));
        });
        fire(new ItemStack(Material.GOLD_INGOT));
        assertThat(new FakeMolds().content(stacks.decode(record().slot(10))).amount()).isEqualTo(100);
        assertThat(new FakeMolds().content(stacks.decode(record().slot(12))).amount()).isEqualTo(70);
    }

    /** PAR-METAL-29: a vessel in an extra slot takes the melted fluid like a mold (the whole fitting amount at once). */
    @Test
    void aVesselInAnExtraSlotTakesTheMeltedFluidAfterAFullMold() {
        put(10, new ItemStack(Material.BUCKET));
        put(13, new ItemStack(Material.DECORATED_POT));
        service.edit(forge, kind, (r, now) -> {
            ItemStack full = stacks.decode(r.slot(10));
            for (int i = 0; i < 70; i++) {
                new FakeMolds().pourStep(new FluidAmount("tfc:metal/gold", 1), 0f, full);
            }
            return r.withSlot(10, stacks.encode(full));
        });
        fire(new ItemStack(Material.GOLD_INGOT));
        assertThat(record().slot(5).isEmpty()).isTrue();
        assertThat(new FakeMolds().content(stacks.decode(record().slot(10))).amount()).isEqualTo(100);
        assertThat(new FakeMolds().content(stacks.decode(record().slot(13))).amount()).isEqualTo(70);
    }

    @Test
    void aVesselAloneTakesTheWholeMeltedFluid() {
        put(11, new ItemStack(Material.DECORATED_POT));
        fire(new ItemStack(Material.GOLD_INGOT));
        assertThat(new FakeMolds().content(stacks.decode(record().slot(11))).amount()).isEqualTo(100);
    }

    @Test
    void theOutputOfAFiredRecipeIsGrilled() {
        fire(new ItemStack(Material.RAW_COPPER));
        ItemStack out = stacks.decode(record().slot(5));
        assertThat(out.getType()).isEqualTo(Material.COPPER_INGOT);
        assertThat(out.getItemMeta().getLore()).containsExactly("grilled");
    }

    @Test
    void droppedFuelAboveTheForgeGoesIntoTheFirstFreeFuelSlots() {
        put(0, new ItemStack(Material.CHARCOAL));
        TestDrop stack = new TestDrop(new ItemStack(Material.CHARCOAL, 3));
        TestDrop other = new TestDrop(new ItemStack(Material.IRON_INGOT, 2));
        effects.drops.add(other);
        effects.drops.add(stack);
        service.neighbourChanged(forge);
        for (int i = 0; i <= 3; i++) {
            assertThat(stacks.decode(record().slot(i)).getType()).as("slot " + i).isEqualTo(Material.CHARCOAL);
        }
        assertThat(record().slot(4).isEmpty()).isTrue();
        assertThat(stack.count()).isZero();
        assertThat(other.count()).as("not a fuel").isEqualTo(2);
    }

    @Test
    void fuelSlotsThatAreFullTakeNothingMore() {
        for (int i = 0; i <= 4; i++) {
            put(i, new ItemStack(Material.CHARCOAL));
        }
        TestDrop drop = new TestDrop(new ItemStack(Material.CHARCOAL, 2));
        effects.drops.add(drop);
        service.neighbourChanged(forge);
        assertThat(drop.count()).isEqualTo(2);
    }

    @Test
    void aConsumerAboveGetsTheHeatOfEveryTick() {
        Block above = forge.getRelative(BlockFace.UP);
        blocks.place(above, BlockStateRef.of("tfp:crucible"), true);
        consumers.register("tfp:crucible", (b, from, to, t) -> {
            assertThat(b).isEqualTo(above);
            heat.add(new Heat(from, to, t));
        });
        put(0, new ItemStack(Material.CHARCOAL));
        device.lightAt(service, forge);
        assertThat(effects.callbacks).containsExactly(CharcoalForgeDevice.HEAT_PERIOD);
        clock.set(START + 1500);
        service.neighbourChanged(forge);
        assertThat(heat).isNotEmpty();
        assertThat(heat.getFirst().from()).isEqualTo(START + 1);
        assertThat(heat.getLast().to()).isEqualTo(START + 1500);
        assertThat(heat.stream().mapToLong(h -> h.to() - h.from() + 1).sum()).isEqualTo(1500);
        assertThat(heat.getLast().temperature()).isEqualTo(1350f);
    }

    @Test
    void theInterimClicksPutFuelAndItemsInAndTakeTheLastItemBack() {
        assertThat(click(false, new ItemStack(Material.CHARCOAL, 3))).isTrue();
        assertThat(click(false, new ItemStack(Material.IRON_INGOT, 2))).isTrue();
        assertThat(player.getInventory().getItemInMainHand().getAmount()).isEqualTo(1);
        assertThat(stacks.decode(record().slot(0)).getType()).isEqualTo(Material.CHARCOAL);
        assertThat(stacks.decode(record().slot(5)).getType()).isEqualTo(Material.IRON_INGOT);
        assertThat(click(false, new ItemStack(Material.SAND))).as("nothing to do: the click goes on").isFalse();
        assertThat(click(true, null)).isTrue();
        assertThat(effects.given).extracting(ItemStack::getType).containsExactly(Material.IRON_INGOT);
        assertThat(record().slot(5).isEmpty()).isTrue();
    }

    @Test
    void aScreenOpensOnAClickWhenOneIsInstalled() {
        device.screen((p, b) -> opened.add(b));
        assertThat(click(false, new ItemStack(Material.SAND))).isTrue();
        assertThat(opened).containsExactly(forge);
        device.screen(null);
    }

    @Test
    void theSlotsFollowTfc() {
        assertThat(kind.slots()).isEqualTo(14);
        assertThat(kind.slotLimit(3)).isEqualTo(1);
        assertThat(device.isItemValid(0, new ItemStack(Material.CHARCOAL))).isTrue();
        assertThat(device.isItemValid(0, new ItemStack(Material.IRON_INGOT))).as("fuel slots take fuel only").isFalse();
        assertThat(device.isItemValid(5, new ItemStack(Material.IRON_INGOT))).isTrue();
        assertThat(device.isItemValid(5, new ItemStack(Material.SAND))).isFalse();
        assertThat(device.isItemValid(10, new ItemStack(Material.BUCKET))).isTrue();
        assertThat(device.isItemValid(10, new ItemStack(Material.IRON_INGOT))).as("extra slots take containers").isFalse();
        assertThat(CharcoalForgeSim.SLOT_EXTRA_MAX).isEqualTo(13);
    }

    @Test
    void theFuelCascadesDownAsItBurns() {
        put(1, new ItemStack(Material.CHARCOAL));
        put(3, new ItemStack(Material.CHARCOAL));
        device.createFromPile(service, forge);
        clock.set(START + 200 + 10);
        service.neighbourChanged(forge);
        // the burn of 200 ran out, the first fuel was taken, the second cascaded to slot 0
        assertThat(record().state().state().lit()).isTrue();
        assertThat(stacks.decode(record().slot(0)).getType()).isEqualTo(Material.CHARCOAL);
        assertThat(record().slot(1).isEmpty()).isTrue();
        assertThat(record().slot(3).isEmpty()).isTrue();
    }
}
