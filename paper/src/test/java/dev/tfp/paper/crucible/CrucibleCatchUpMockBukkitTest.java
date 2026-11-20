/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.crucible;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.crucible.CrucibleData;
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

    @Test
    void theOutputSlotTakesNothingUntilTheMoldsExistAndInputsTakeOnlyHeatableItems() {
        CrucibleDevice device = device();
        assertThat(device.isItemValid(CrucibleDevice.SLOT_OUTPUT, new ItemStack(Material.RAW_COPPER))).isFalse();
        assertThat(device.isItemValid(0, new ItemStack(Material.RAW_COPPER))).isTrue();
        assertThat(device.kind().slotLimit(3)).isEqualTo(1);
        assertThat(device.kind().removal()).isEqualTo(dev.tfp.paper.device.DeviceKind.RemoveBehaviour.SAVE);
    }
}
