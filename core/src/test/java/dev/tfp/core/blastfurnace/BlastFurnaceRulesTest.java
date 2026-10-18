/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/BlastFurnaceBlockEntity.java @ c3b4d9536 (serverTick, consumeFuel,
 *       addItemsFromWorld, the fluid conversion), src/main/java/net/dries007/tfc/common/component/heat/HeatCapability.java
 *       @ c3b4d9536 (adjustDeviceTemp)
 */
package dev.tfp.core.blastfurnace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.tfp.core.blastfurnace.BlastFurnaceRules.Charge;
import dev.tfp.core.blastfurnace.BlastFurnaceRules.FluidAmount;
import dev.tfp.core.blastfurnace.BlastFurnaceRules.FuelHead;
import dev.tfp.core.blastfurnace.BlastFurnaceRules.Tank;
import dev.tfp.core.blastfurnace.BlastFurnaceRules.WorldItem;
import dev.tfp.core.cooking.FirepitRules;
import dev.tfp.core.crucible.CrucibleRecipe;
import dev.tfp.core.fuel.Fuel;
import dev.tfp.core.heat.HeatConfig;
import dev.tfp.core.heat.HeatState;
import java.util.List;
import org.junit.jupiter.api.Test;

class BlastFurnaceRulesTest {
    private static final FirepitRules.DeviceConfig DEVICE = FirepitRules.DeviceConfig.DEFAULT;
    private static final HeatConfig HEAT = HeatConfig.DEFAULT;
    private static final BlastFurnaceRules.Config CONFIG = BlastFurnaceRules.Config.DEFAULT;
    private static final String CAST_IRON = "tfc:metal/cast_iron";
    private static final String PIG_IRON = "tfc:metal/pig_iron";
    private static final String FLUX = "tfc:powder/flux";
    private static final String COAL = "tfc:fuel/charcoal";
    private static final String IRON_INGOT = "minecraft:iron_ingot";
    private static final String COPPER = "tfc:metal/copper";

    private static final BlastFurnaceRecipe PIG_IRON_RECIPE = new BlastFurnaceRecipe(CAST_IRON, 1, FLUX, PIG_IRON, 1);
    private static final List<BlastFurnaceRecipe> RECIPES = List.of(PIG_IRON_RECIPE);
    private static final Fuel CHARCOAL = new Fuel(COAL, 1000, 1500f, 1f);
    /** A heating recipe: iron ingots melt at 1000 degrees into 100 mB of cast iron. */
    private static final CrucibleRecipe IRON_MELT = new CrucibleRecipe(IRON_INGOT, 1000f, CAST_IRON, 100, false, false);

    @Test
    void configRejectsNonPositiveValues() {
        assertThatThrownBy(() -> new BlastFurnaceRules.Config(0, 5, 10_000, 4)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BlastFurnaceRules.Config(4, 0, 10_000, 4)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BlastFurnaceRules.Config(4, 5, 0, 4)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BlastFurnaceRules.Config(4, 5, 10_000, 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void stateCapsAirAtBellowsMaximum() {
        assertThat(BlastFurnaceState.COLD.intakeAir(700).airTicks()).isEqualTo(BlastFurnaceState.MAX_AIR_TICKS);
        assertThat(BlastFurnaceState.COLD.intakeAir(5).airTicks()).isEqualTo(5);
        assertThatThrownBy(() -> new BlastFurnaceState(false, 0, 0f, 601, 0f)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void burnRateIsTheMultiplierAndDoublesWithAir() {
        assertThat(BlastFurnaceRules.burnRate(0, 4)).isEqualTo(4);
        assertThat(BlastFurnaceRules.burnRate(1, 4)).isEqualTo(8);
        assertThat(BlastFurnaceRules.burnRate(0, 1)).isEqualTo(1);
    }

    @Test
    void chimneyLevelsCountTheLeadingFormedLevelsUpToTheMaximum() {
        assertThat(BlastFurnaceRules.chimneyLevels(new boolean[] {true, true, false, true}, 5)).isEqualTo(2);
        assertThat(BlastFurnaceRules.chimneyLevels(new boolean[] {true, true, true, true, true, true}, 5)).isEqualTo(5);
        assertThat(BlastFurnaceRules.chimneyLevels(new boolean[] {true}, 3)).isEqualTo(1);
        assertThat(BlastFurnaceRules.chimneyLevels(new boolean[] {false}, 3)).isZero();
        assertThat(BlastFurnaceRules.capacity(2, CONFIG.capacityPerLevel())).isEqualTo(8);
        assertThat(BlastFurnaceRules.capacity(0, CONFIG.capacityPerLevel())).isZero();
    }

    @Test
    void lightNeedsFuelAndKeepsALitFurnace() {
        assertThat(BlastFurnaceRules.light(BlastFurnaceState.COLD, false)).isEqualTo(BlastFurnaceState.COLD);
        assertThat(BlastFurnaceRules.light(BlastFurnaceState.COLD, true).lit()).isTrue();
        BlastFurnaceState lit = new BlastFurnaceState(true, 50, 0f, 0, 0f);
        assertThat(BlastFurnaceRules.light(lit, false)).isEqualTo(lit);
    }

    @Test
    void tickBurnsFuelAtTheMultiplierWithoutAir() {
        BlastFurnaceState next = BlastFurnaceRules.tick(new BlastFurnaceState(true, 100, 1000f, 0, 0f), FuelHead.EMPTY, CONFIG, DEVICE).state();
        assertThat(next.burnTicks()).isEqualTo(96);
        assertThat(next.lit()).isTrue();
    }

    @Test
    void tickBurnsTwiceAsFastWithAir() {
        BlastFurnaceRules.TickResult result = BlastFurnaceRules.tick(new BlastFurnaceState(true, 100, 1000f, 10, 0f), FuelHead.EMPTY, CONFIG, DEVICE);
        assertThat(result.state().burnTicks()).isEqualTo(92);
        assertThat(result.state().airTicks()).isEqualTo(9);
    }

    @Test
    void tickTakesTheNextFuelWhenTheBurnRunsOut() {
        BlastFurnaceRules.TickResult result = BlastFurnaceRules.tick(
            new BlastFurnaceState(true, 4, 0f, 0, 0f), FuelHead.of(CHARCOAL), CONFIG, DEVICE);
        assertThat(result.consumedFuel()).isTrue();
        assertThat(result.state().lit()).isTrue();
        assertThat(result.state().burnTicks()).isEqualTo(1000);
        assertThat(result.state().burnTemperature()).isEqualTo(1500f);
    }

    @Test
    void tickGoesOutWhenTheQueueIsEmpty() {
        BlastFurnaceRules.TickResult result = BlastFurnaceRules.tick(
            new BlastFurnaceState(true, 4, 1500f, 0, 0f), FuelHead.EMPTY, CONFIG, DEVICE);
        assertThat(result.consumedFuel()).isFalse();
        assertThat(result.state().lit()).isFalse();
        assertThat(result.state().burnTicks()).isZero();
        assertThat(result.state().burnTemperature()).isZero();
    }

    @Test
    void anItemWithoutFuelDataIsConsumedAndTheFurnaceGoesOut() {
        BlastFurnaceRules.TickResult result = BlastFurnaceRules.tick(
            new BlastFurnaceState(true, 4, 0f, 0, 0f), FuelHead.of(null), CONFIG, DEVICE);
        assertThat(result.consumedFuel()).isTrue();
        assertThat(result.state().lit()).isFalse();
    }

    @Test
    void anUnlitFurnaceWithBurnTemperatureGoesOutAndCoolsItsFuelTemperature() {
        BlastFurnaceRules.TickResult result = BlastFurnaceRules.tick(
            new BlastFurnaceState(false, 0, 800f, 0, 500f), FuelHead.EMPTY, CONFIG, DEVICE);
        assertThat(result.state().burnTemperature()).isZero();
        assertThat(result.hot()).isTrue();
    }

    @Test
    void tuyereTakesDamageOnTwentyTicksOverFourHundredDegrees() {
        assertThat(BlastFurnaceRules.tick(new BlastFurnaceState(false, 0, 0f, 20, 500f), FuelHead.EMPTY, CONFIG, DEVICE).tuyereDamaged()).isTrue();
        assertThat(BlastFurnaceRules.tick(new BlastFurnaceState(false, 0, 0f, 21, 500f), FuelHead.EMPTY, CONFIG, DEVICE).tuyereDamaged()).isFalse();
        assertThat(BlastFurnaceRules.tick(new BlastFurnaceState(false, 0, 0f, 20, 400f), FuelHead.EMPTY, CONFIG, DEVICE).tuyereDamaged()).isFalse();
    }

    @Test
    void deviceTemperatureFollowsTheFirepitRuleWithTheAirCountedDown() {
        BlastFurnaceRules.TickResult noAir = BlastFurnaceRules.tick(
            new BlastFurnaceState(true, 100, 1000f, 0, 0f), FuelHead.EMPTY, CONFIG, DEVICE);
        assertThat(noAir.state().temperature()).isEqualTo(FirepitRules.adjustDeviceTemp(0f, 1000f, 0, false, 1f));

        BlastFurnaceRules.TickResult withAir = BlastFurnaceRules.tick(
            new BlastFurnaceState(true, 100, 1000f, 10, 0f), FuelHead.EMPTY, CONFIG, DEVICE);
        assertThat(withAir.state().temperature()).isEqualTo(FirepitRules.adjustDeviceTemp(0f, 1000f, 9, false, 1f));
        assertThat(withAir.state().temperature()).isGreaterThan(noAir.state().temperature());
    }

    @Test
    void coldFurnaceIsNotHotAndDoesNotHeat() {
        BlastFurnaceRules.TickResult result = BlastFurnaceRules.tick(BlastFurnaceState.COLD, FuelHead.EMPTY, CONFIG, DEVICE);
        assertThat(result.hot()).isFalse();
        assertThat(result.state().temperature()).isZero();
    }

    @Test
    void chargeBelowItsMeltTemperatureHeatsButDoesNotMelt() {
        Charge charge = new Charge(new HeatState(500f, 0L), 1f, IRON_MELT, 0, 0);
        BlastFurnaceRules.MeltResult result = BlastFurnaceRules.melt(List.of(charge), 800f, 0L, HEAT);
        assertThat(result.produced()).isEmpty();
        assertThat(result.remaining()).hasSize(1);
        assertThat(result.remaining().get(0).heat().temperature()).isGreaterThan(500f);
    }

    @Test
    void chargeAtItsMeltTemperatureMeltsIntoTheRecipeFluid() {
        Charge charge = new Charge(new HeatState(1000f, 0L), 1f, IRON_MELT, 0, 0);
        BlastFurnaceRules.MeltResult result = BlastFurnaceRules.melt(List.of(charge), 1000f, 0L, HEAT);
        assertThat(result.remaining()).isEmpty();
        assertThat(result.produced()).containsExactly(new FluidAmount(CAST_IRON, 100));
    }

    @Test
    void chargesWithoutHeatOrRecipeNeverMelt() {
        Charge noHeat = new Charge(null, 1f, IRON_MELT, 0, 0);
        Charge noRecipe = new Charge(new HeatState(1000f, 0L), 1f, null, 0, 0);
        BlastFurnaceRules.MeltResult result = BlastFurnaceRules.melt(List.of(noHeat, noRecipe), 1000f, 0L, HEAT);
        assertThat(result.produced()).isEmpty();
        assertThat(result.remaining()).containsExactly(noHeat, noRecipe);
    }

    @Test
    void recipeConvertsWholeBatchesAndKeepsTheRemainder() {
        BlastFurnaceRecipe two = new BlastFurnaceRecipe(CAST_IRON, 2, FLUX, PIG_IRON, 3);
        BlastFurnaceRecipe.Assembly assembled = two.assemble(5);
        assertThat(assembled).isEqualTo(new BlastFurnaceRecipe.Assembly(PIG_IRON, 6, 1));
        assertThat(two.assemble(1)).isEqualTo(new BlastFurnaceRecipe.Assembly(PIG_IRON, 0, 1));
    }

    @Test
    void meltedCastIronBecomesPigIronAtOneToOne() {
        BlastFurnaceRules.Converted converted = BlastFurnaceRules.convert(
            Tank.EMPTY, List.of(new FluidAmount(CAST_IRON, 5)), RECIPES, Tank.EMPTY, CONFIG.fluidCapacity());
        assertThat(converted.input().isEmpty()).isTrue();
        assertThat(converted.output()).isEqualTo(new Tank(PIG_IRON, 5));
    }

    @Test
    void aSteadyPigIronRunMakesOneMilliBucketPerMelt() {
        Tank input = Tank.EMPTY;
        Tank output = Tank.EMPTY;
        for (int tick = 0; tick < 4; tick++) {
            BlastFurnaceRules.Converted converted = BlastFurnaceRules.convert(
                input, List.of(new FluidAmount(CAST_IRON, 1)), RECIPES, output, CONFIG.fluidCapacity());
            input = converted.input();
            output = converted.output();
        }
        assertThat(output).isEqualTo(new Tank(PIG_IRON, 4));
        assertThat(input.isEmpty()).isTrue();
    }

    @Test
    void aDifferentInputFluidReplacesTheTankAndNoRecipeMakesNoOutput() {
        BlastFurnaceRules.Converted converted = BlastFurnaceRules.convert(
            new Tank(CAST_IRON, 3), List.of(new FluidAmount(COPPER, 2)), RECIPES, Tank.EMPTY, CONFIG.fluidCapacity());
        assertThat(converted.input()).isEqualTo(new Tank(COPPER, 2));
        assertThat(converted.output().isEmpty()).isTrue();
    }

    @Test
    void outputIsCappedAtTheTankCapacity() {
        BlastFurnaceRules.Converted converted = BlastFurnaceRules.convert(
            Tank.EMPTY, List.of(new FluidAmount(CAST_IRON, 5)), RECIPES, new Tank(PIG_IRON, 9998), 10_000);
        assertThat(converted.output()).isEqualTo(new Tank(PIG_IRON, 10_000));
    }

    @Test
    void outputTankRefusesADifferentFluid() {
        BlastFurnaceRules.Converted converted = BlastFurnaceRules.convert(
            Tank.EMPTY, List.of(new FluidAmount(CAST_IRON, 5)), RECIPES, new Tank(COPPER, 10), CONFIG.fluidCapacity());
        assertThat(converted.output()).isEqualTo(new Tank(COPPER, 10));
    }

    @Test
    void dripMovesOneMilliBucketOnlyWhenTheBelowAcceptsIt() {
        assertThat(BlastFurnaceRules.drip(new Tank(PIG_IRON, 3), true))
            .isEqualTo(new BlastFurnaceRules.Drip(new Tank(PIG_IRON, 2), true));
        assertThat(BlastFurnaceRules.drip(new Tank(PIG_IRON, 3), false))
            .isEqualTo(new BlastFurnaceRules.Drip(new Tank(PIG_IRON, 3), false));
        assertThat(BlastFurnaceRules.drip(Tank.EMPTY, true).transferred()).isFalse();
    }

    @Test
    void intakeNeedsARecipeAndAFreeFurnace() {
        List<WorldItem> world = List.of(new WorldItem(FLUX, 2, null, false), new WorldItem(IRON_INGOT, 2, CAST_IRON, false));
        assertThat(BlastFurnaceRules.intake(null, world, 0, 0, 0, 16).inputTotal()).isZero();
        assertThat(BlastFurnaceRules.intake(PIG_IRON_RECIPE, world, 4, 4, 4, 4).inputTotal()).isZero();
        assertThat(BlastFurnaceRules.intake(PIG_IRON_RECIPE, world, 0, 0, 0, 16).inputTotal()).isEqualTo(2);
    }

    @Test
    void intakeIsLimitedByTheRoomLeftInTheFurnace() {
        List<WorldItem> world = List.of(new WorldItem(FLUX, 5, null, false), new WorldItem(IRON_INGOT, 5, CAST_IRON, false));
        BlastFurnaceRules.Intake intake = BlastFurnaceRules.intake(PIG_IRON_RECIPE, world, 3, 3, 0, 4);
        assertThat(intake.inputTotal()).isEqualTo(1);
        assertThat(intake.catalystTotal()).isEqualTo(1);
    }

    @Test
    void intakeClassifiesInputsThenCatalystsThenFuel() {
        List<WorldItem> world = List.of(
            new WorldItem(FLUX, 1, null, true),
            new WorldItem(IRON_INGOT, 1, CAST_IRON, true),
            new WorldItem(COAL, 9, null, true));
        BlastFurnaceRules.Intake intake = BlastFurnaceRules.intake(PIG_IRON_RECIPE, world, 0, 0, 0, 4);
        assertThat(intake.inputs()).containsExactly(0, 1, 0);
        assertThat(intake.catalysts()).containsExactly(1, 0, 0);
        assertThat(intake.fuel()).containsExactly(0, 0, 4);
    }

    @Test
    void worldRecipeIsTheFirstWorldItemThatMelts() {
        assertThat(BlastFurnaceRules.worldRecipe(RECIPES, List.of(new WorldItem(FLUX, 1, null, false)))).isNull();
        assertThat(BlastFurnaceRules.worldRecipe(RECIPES, List.of(new WorldItem(IRON_INGOT, 1, COPPER, false)))).isNull();
    }

    @Test
    void inputsWithoutARecipeMustBeDumped() {
        assertThat(BlastFurnaceRules.mustDump(List.of(COPPER), BlastFurnaceRules.cachedRecipe(RECIPES, List.of(COPPER)))).isTrue();
        assertThat(BlastFurnaceRules.mustDump(List.of(CAST_IRON), BlastFurnaceRules.cachedRecipe(RECIPES, List.of(CAST_IRON)))).isFalse();
        assertThat(BlastFurnaceRules.mustDump(List.of(), null)).isFalse();
    }
}
