/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.crucible;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.alloy.FluidAlloy;
import dev.tfp.core.crucible.CrucibleData;
import dev.tfp.core.crucible.CrucibleTemperature;
import dev.tfp.core.vessel.FluidAmount;
import dev.tfp.paper.itemdata.FluidPdc;
import dev.tfp.core.crucible.CrucibleRecipe;
import dev.tfp.core.crucible.CrucibleRules;
import dev.tfp.core.device.DeviceRecord;
import dev.tfp.core.device.ItemBlob;
import dev.tfp.core.heat.HeatConfig;
import dev.tfp.paper.device.StackCodec;
import dev.tfp.paper.heat.HeatPdc;
import dev.tfp.paper.heat.ItemHeatHooks;
import dev.tfp.paper.testing.MockServerTest;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

/**
 * PAR-METAL-12: the crucible's lazy melt. One call of the kind's catch-up over a long span, with the heat source's run
 * known to it, melts the inputs into the alloy and cools the crucible after the source stops, as TFC's tick would have.
 */
class CrucibleCatchUpMockBukkitTest extends MockServerTest {
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

    private static final Map<Material, CrucibleRecipe> RECIPES = Map.of(
        Material.RAW_COPPER, new CrucibleRecipe("raw_copper", 1080f, "tfc:metal/copper", 90, false, false),
        Material.RAW_IRON, new CrucibleRecipe("raw_iron", 1535f, "tfc:metal/wrought_iron", 100, false, false),
        Material.RAW_GOLD, new CrucibleRecipe("raw_gold", 1060f, "tfc:metal/gold", 10, false, false),
        Material.COPPER_INGOT, new CrucibleRecipe("copper_ingot", 1080f, "tfc:metal/copper", 100, false, false));

    private final TableCodec codec = new TableCodec();

    private CrucibleDevice device() {
        CrucibleDevice.Heating heating = new CrucibleDevice.Heating() {
            @Override
            public Optional<CrucibleRecipe> recipe(ItemStack stack) {
                return Optional.ofNullable(RECIPES.get(stack.getType()));
            }

            @Override
            public ItemStack melted(ItemStack heated) {
                return ItemStack.empty();
            }
        };
        return new CrucibleDevice(new CrucibleDevice.Services(ItemHeatHooks.Capacities.UNIT, heating, HeatConfig.DEFAULT,
            CrucibleRules.DEFAULT_CAPACITY, stack -> false, (p, b) -> { }, b -> { }), codec);
    }

    private DeviceRecord<CrucibleData> record(Material... items) {
        DeviceRecord<CrucibleData> r = DeviceRecord.fresh(CrucibleData.FRESH, CrucibleDevice.SLOTS);
        for (int i = 0; i < items.length; i++) {
            r = r.withSlot(i, codec.encode(new ItemStack(items[i])));
        }
        return r;
    }

    @Test
    void aLongRunMeltsEveryInputThatReachesItsRecipeTemperatureAndOnlyThose() {
        CrucibleDevice device = device();
        DeviceRecord<CrucibleData> start = record(Material.RAW_COPPER, Material.RAW_IRON, Material.RAW_GOLD);
        DeviceRecord<CrucibleData> after = CrucibleDevice.heat(1, 20_000, 1200f, () -> device.catchUp(start, 0, 20_000));
        // 1200 C: copper (1080) and gold (1060) melt, iron (1535) cannot
        assertThat(after.state().alloy().amount()).isEqualTo(100);
        assertThat(after.state().alloy().contents()).containsOnlyKeys("tfc:metal/copper", "tfc:metal/gold");
        assertThat(codec.decode(after.slot(0)).isEmpty()).isTrue();
        assertThat(codec.decode(after.slot(1)).getType()).isEqualTo(Material.RAW_IRON);
        assertThat(codec.decode(after.slot(2)).isEmpty()).isTrue();
        assertThat(HeatPdc.read(codec.decode(after.slot(1))).orElseThrow().temperature()).isLessThanOrEqualTo(1200f).isGreaterThan(1000f);
    }

    @Test
    void theCrucibleCoolsAfterTheSourceStopsSoTheMeltFreezesAtWhatWasReached() {
        CrucibleDevice device = device();
        DeviceRecord<CrucibleData> start = record(Material.RAW_IRON);
        // the source heats for 400 ticks only: the iron never gets near 1535 and the crucible then goes cold
        DeviceRecord<CrucibleData> after = CrucibleDevice.heat(1, 400, 1500f, () -> device.catchUp(start, 0, 60_000));
        assertThat(after.state().alloy().isEmpty()).isTrue();
        assertThat(after.state().degrees()).isZero();
        assertThat(codec.decode(after.slot(0)).getType()).isEqualTo(Material.RAW_IRON);
    }

    @Test
    void withoutARunNothingHeatsAndNothingMelts() {
        CrucibleDevice device = device();
        DeviceRecord<CrucibleData> after = device.catchUp(record(Material.RAW_COPPER), 0, 100_000);
        assertThat(after.state()).isEqualTo(CrucibleData.FRESH);
        assertThat(codec.decode(after.slot(0)).getType()).isEqualTo(Material.RAW_COPPER);
    }

    @Test
    void twoInputsOfOneMetalFillTheAlloyOnceEach() {
        CrucibleDevice device = device();
        DeviceRecord<CrucibleData> after = CrucibleDevice.heat(1, 20_000, 1200f,
            () -> device.catchUp(record(Material.RAW_COPPER, Material.COPPER_INGOT), 0, 20_000));
        assertThat(after.state().alloy().amount()).isEqualTo(190);
        assertThat(after.state().alloy().contents()).containsOnlyKeys("tfc:metal/copper");
    }

    /** Bricks are molds of 100 mB; molten when they hold something and {@code cold} is off. */
    private static final class FakeMolds implements CrucibleMolds {
        boolean cold;

        @Override
        public boolean isMold(ItemStack stack) {
            return stack.getType() == Material.BRICK;
        }

        @Override
        public boolean isMolten(ItemStack mold) {
            return isMold(mold) && !cold && !content(mold).isEmpty();
        }

        @Override
        public FluidAmount content(ItemStack mold) {
            return FluidPdc.content(mold);
        }

        @Override
        public int pourStep(FluidAmount fluid, float temperature, ItemStack mold) {
            FluidAmount c = content(mold);
            if (c.amount() >= 100 || (!c.isEmpty() && !c.fluid().equals(fluid.fluid()))) {
                return 0;
            }
            FluidPdc.write(mold, new FluidAmount(fluid.fluid(), c.amount() + 1));
            return 1;
        }

        @Override
        public boolean drainOne(ItemStack mold) {
            FluidAmount c = content(mold);
            FluidPdc.write(mold, c.amount() <= 1 ? null : new FluidAmount(c.fluid(), c.amount() - 1));
            return true;
        }

        @Override
        public String result(dev.tfp.core.alloy.FluidAlloy alloy) {
            return alloy.contents().keySet().iterator().next();
        }
    }

    private CrucibleDevice deviceWith(CrucibleMolds molds) {
        CrucibleDevice.Heating none = new CrucibleDevice.Heating() {
            @Override
            public Optional<CrucibleRecipe> recipe(ItemStack stack) {
                return Optional.empty();
            }

            @Override
            public ItemStack melted(ItemStack heated) {
                return ItemStack.empty();
            }
        };
        return new CrucibleDevice(new CrucibleDevice.Services(ItemHeatHooks.Capacities.UNIT, none, HeatConfig.DEFAULT,
            CrucibleRules.DEFAULT_CAPACITY, molds::isMold, (p, b) -> { }, b -> { }, molds), codec);
    }

    private DeviceRecord<CrucibleData> hot(String fluid, int amount, float degrees) {
        FluidAlloy alloy = FluidAlloy.empty().fill(fluid, amount, 4000).alloy();
        return DeviceRecord.fresh(new CrucibleData(new CrucibleTemperature(degrees, degrees, 5), alloy), CrucibleDevice.SLOTS);
    }

    @Test
    void aMoldInTheOutputSlotFillsOneMbPerTickFromAMoltenAlloy() {
        CrucibleDevice device = deviceWith(new FakeMolds());
        DeviceRecord<CrucibleData> r = hot("tfc:metal/copper", 250, 1200f)
            .withSlot(CrucibleDevice.SLOT_OUTPUT, codec.encode(new ItemStack(Material.BRICK)));
        DeviceRecord<CrucibleData> after = device.moldPass(r, 60);
        assertThat(FluidPdc.content(codec.decode(after.slot(CrucibleDevice.SLOT_OUTPUT))).amount()).isEqualTo(60);
        assertThat(after.state().alloy().amount()).isEqualTo(190);
        // a long span stops at the mold's capacity
        after = device.moldPass(after, 10_000);
        assertThat(FluidPdc.content(codec.decode(after.slot(CrucibleDevice.SLOT_OUTPUT))).amount()).isEqualTo(100);
        assertThat(after.state().alloy().amount()).isEqualTo(150);
    }

    @Test
    void aColdCrucibleOrAnEmptyAlloyFillsNothing() {
        CrucibleDevice device = deviceWith(new FakeMolds());
        DeviceRecord<CrucibleData> cold = hot("tfc:metal/copper", 250, 500f)
            .withSlot(CrucibleDevice.SLOT_OUTPUT, codec.encode(new ItemStack(Material.BRICK)));
        assertThat(device.moldPass(cold, 100)).isEqualTo(cold);
        DeviceRecord<CrucibleData> empty = DeviceRecord.fresh(CrucibleData.FRESH, CrucibleDevice.SLOTS)
            .withSlot(CrucibleDevice.SLOT_OUTPUT, codec.encode(new ItemStack(Material.BRICK)));
        assertThat(device.moldPass(empty, 100)).isEqualTo(empty);
    }

    @Test
    void aMoltenMoldInAnInputPoursIntoTheAlloyAtThePouringRate() {
        CrucibleDevice device = deviceWith(new FakeMolds());
        ItemStack mold = new ItemStack(Material.BRICK);
        FluidPdc.write(mold, new FluidAmount("tfc:metal/copper", 100));
        DeviceRecord<CrucibleData> r = DeviceRecord.fresh(CrucibleData.FRESH, CrucibleDevice.SLOTS).withSlot(2, codec.encode(mold));
        // 40 ticks at one mB per 4 ticks: ticks 1, 5, 9 ... give 10 mB
        DeviceRecord<CrucibleData> after = device.moldPass(r, 40);
        assertThat(after.state().alloy().amount()).isEqualTo(10);
        assertThat(FluidPdc.content(codec.decode(after.slot(2))).amount()).isEqualTo(90);
        // the whole mold, given time
        after = device.moldPass(after, 1000);
        assertThat(after.state().alloy().amount()).isEqualTo(100);
        assertThat(FluidPdc.content(codec.decode(after.slot(2))).isEmpty()).isTrue();
    }

    @Test
    void aSolidMoldDoesNotPourAndFastPouringPoursOneMbPerTickForTwentyOneUnits() {
        FakeMolds molds = new FakeMolds();
        CrucibleDevice device = deviceWith(molds);
        ItemStack mold = new ItemStack(Material.BRICK);
        FluidPdc.write(mold, new FluidAmount("tfc:metal/copper", 100));
        DeviceRecord<CrucibleData> r = DeviceRecord.fresh(CrucibleData.FRESH, CrucibleDevice.SLOTS).withSlot(2, codec.encode(mold));
        molds.cold = true;
        assertThat(device.moldPass(r, 100).state().alloy().amount()).isZero();
        assertThat(device.pourFaster(r, 2)).isNull();
        molds.cold = false;
        DeviceRecord<CrucibleData> fast = device.pourFaster(r, 2);
        assertThat(fast.state().pour().fastPourTicks()).isEqualTo(CrucibleRules.FAST_POUR_TICKS);
        // TFC's counter runs 20 down to -1: 21 units at one per tick, then the normal rate
        assertThat(device.moldPass(fast, 21).state().alloy().amount()).isEqualTo(21);
        assertThat(device.moldPass(fast, 25).state().alloy().amount()).isEqualTo(21 + 1);
    }

    @Test
    void theOutputSlotTakesMoldsOnlyWhenTheyExistAndInputsTakeOnlyHeatableItems() {
        assertThat(deviceWith(new FakeMolds()).isItemValid(CrucibleDevice.SLOT_OUTPUT, new ItemStack(Material.BRICK))).isTrue();
        assertThat(deviceWith(new FakeMolds()).isItemValid(CrucibleDevice.SLOT_OUTPUT, new ItemStack(Material.RAW_COPPER))).isFalse();
        CrucibleDevice device = device();
        assertThat(device.isItemValid(CrucibleDevice.SLOT_OUTPUT, new ItemStack(Material.RAW_COPPER))).isFalse();
        assertThat(device.isItemValid(0, new ItemStack(Material.RAW_COPPER))).isTrue();
        assertThat(device.kind().slotLimit(3)).isEqualTo(1);
        assertThat(device.kind().removal()).isEqualTo(dev.tfp.paper.device.DeviceKind.RemoveBehaviour.SAVE);
    }
}
