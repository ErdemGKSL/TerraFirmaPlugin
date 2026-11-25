/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/component/mold/Vessel.java @ c3b4d9536 (updateInventoryOnMelt),
 *       src/main/java/net/dries007/tfc/util/FluidAlloy.java @ c3b4d9536 (fill, getResult)
 */
package dev.tfp.core.vessel;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.alloy.AlloyRange;
import dev.tfp.core.alloy.AlloyRecipe;
import dev.tfp.core.alloy.FluidAlloy;
import dev.tfp.core.heat.HeatConfig;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** PAR-METAL-22: a vessel melts copper and tin into one mix whose result gives the heat values. */
class VesselMixTest {
    private static final String COPPER = "tfp:metal/copper";
    private static final String TIN = "tfp:metal/tin";
    private static final List<AlloyRecipe> RECIPES = List.of(new AlloyRecipe("tfp:metal/bronze",
        List.of(new AlloyRange(COPPER, 0.88, 0.92), new AlloyRange(TIN, 0.08, 0.12))));
    private static final HeatConfig CFG = HeatConfig.DEFAULT;

    private static VesselSlot slot(String id, String fluid, int count, float recipe) {
        return new VesselSlot(id, count, 1f, new Melt(recipe, new FluidAmount(fluid, 100), 0.5f, 1f));
    }

    private static List<VesselSlot> slots(VesselSlot... used) {
        List<VesselSlot> out = new ArrayList<>(List.of(used));
        while (out.size() < VesselRules.SLOTS) {
            out.add(VesselSlot.EMPTY);
        }
        return out;
    }

    @Test
    void copperAndTinMeltIntoBronzeWithTheBronzeHeatValues() {
        List<VesselSlot> in = slots(slot("copper", COPPER, 9, 1080f), slot("tin", TIN, 1, 230f));
        VesselRules.MeltOutcome out = VesselRules.meltInto(FluidAlloy.empty(), in, 1100f);
        assertThat(out.changed()).isTrue();
        assertThat(out.slots()).allMatch(VesselSlot::isEmpty);
        assertThat(out.mix().amount()).isEqualTo(1000);
        assertThat(out.mix().contents()).containsEntry(COPPER, 900d).containsEntry(TIN, 100d);

        Alloy alloy = Alloy.ofMix(out.mix(), RECIPES);
        assertThat(alloy.amount()).isEqualTo(1000);
        assertThat(alloy.specificHeat()).isEqualTo(0.008571429f); // TFC fluid_heat bronze
        assertThat(alloy.meltTemperature()).isEqualTo(950f);
    }

    @Test
    void onlyTheSlotsAtTheirRecipeTemperatureMelt() {
        List<VesselSlot> in = slots(slot("copper", COPPER, 9, 1080f), slot("tin", TIN, 1, 230f));
        VesselRules.MeltOutcome out = VesselRules.meltInto(FluidAlloy.empty(), in, 500f);
        assertThat(out.mix().contents()).containsOnlyKeys(TIN);
        assertThat(out.slots().get(0).isEmpty()).isFalse();
        assertThat(out.slots().get(1).isEmpty()).isTrue();
        assertThat(Alloy.ofMix(out.mix(), RECIPES).meltTemperature()).as("a single metal is itself").isEqualTo(230f);
        assertThat(VesselRules.meltInto(FluidAlloy.empty(), in, 100f).changed()).isFalse();
    }

    @Test
    void anUnmatchedMixIsUnknownWithTFCsUnknownHeat() {
        FluidAlloy mix = FluidAlloy.empty().fill(COPPER, 500, 3000).alloy().fill(TIN, 500, 3000).alloy();
        Alloy alloy = Alloy.ofMix(mix, RECIPES);
        assertThat(alloy.specificHeat()).isEqualTo(0.006f);
        assertThat(alloy.meltTemperature()).isEqualTo(400f);
        assertThat(Alloy.ofMix(FluidAlloy.empty(), RECIPES)).isEqualTo(Alloy.EMPTY);
    }

    @Test
    void theMixTotalMatchesTheSingleFluidMeltIncludingTheCapacityCap() {
        for (int copper : new int[] {1, 9, 16}) {
            for (int tin : new int[] {0, 1, 16}) {
                List<VesselSlot> in = slots(slot("copper", COPPER, copper, 1080f), slot("tin", TIN, tin == 0 ? 0 : tin, 230f),
                    slot("copper2", COPPER, 16, 1080f), slot("copper3", COPPER, 16, 1080f));
                VesselState state = new VesselState(in, Alloy.EMPTY, 1f, dev.tfp.core.heat.HeatState.cold(0));
                VesselState single = state.heatTo(10, 1100f, CFG);
                VesselRules.MeltOutcome mixed = VesselRules.meltInto(FluidAlloy.empty(), in, 1100f);
                assertThat(mixed.mix().amount()).as("%d copper %d tin", copper, tin).isEqualTo(single.alloy().amount());
                assertThat(mixed.slots()).isEqualTo(single.slots());
            }
        }
    }

    @Test
    void aMeltAddsToTheExistingMixAndWithAlloyRecomputesTheCapacity() {
        FluidAlloy existing = FluidAlloy.empty().fill(COPPER, 2950, 3000).alloy();
        VesselRules.MeltOutcome out = VesselRules.meltInto(existing, slots(slot("tin", TIN, 1, 230f)), 300f);
        assertThat(out.mix().amount()).as("capacity 3000: 50 of 100 fit").isEqualTo(3000);
        assertThat(out.mix().contents().get(TIN)).isEqualTo(50d);

        VesselState state = VesselState.empty(0).withAlloy(0, new Alloy(1000, 0.008571429f, 950f), CFG);
        assertThat(state.heatCapacity()).isEqualTo(VesselRules.contentHeatCapacity(slots(), 1000, 0.008571429f));
        assertThat(state.alloy().meltTemperature()).isEqualTo(950f);
    }
}
