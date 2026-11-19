/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.jug;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.tfp.core.fluid.FluidContainerKind;
import dev.tfp.core.vessel.FluidAmount;
import org.junit.jupiter.api.Test;

/** PAR-POT-07 golden values: TFC c3b4d9536 ServerConfig defaults and the JugItem / GlassBottleItem drinks. */
class DrinkContainerRulesTest {
    private static final FluidAmount WATER_100 = new FluidAmount("minecraft:water", 100);

    @Test
    void breakChancesAreTheServerConfigDefaults() {
        assertThat(DrinkContainerRules.breakChance(FluidContainerKind.JUG)).isEqualTo(0.02);
        assertThat(DrinkContainerRules.breakChance(FluidContainerKind.SILICA_GLASS_BOTTLE)).isEqualTo(0.005);
        assertThat(DrinkContainerRules.breakChance(FluidContainerKind.HEMATITIC_GLASS_BOTTLE)).isEqualTo(0.02);
        assertThat(DrinkContainerRules.breakChance(FluidContainerKind.VOLCANIC_GLASS_BOTTLE)).isEqualTo(0.04);
        assertThat(DrinkContainerRules.breakChance(FluidContainerKind.OLIVINE_GLASS_BOTTLE)).isEqualTo(0.01);
    }

    @Test
    void jugDrinkTakesEverything() {
        DrinkContainerRules.Drink d = DrinkContainerRules.drink(FluidContainerKind.JUG, WATER_100, 0.5f);
        assertThat(d.drained()).isEqualTo(WATER_100);
        assertThat(d.content().isEmpty()).isTrue();
        assertThat(d.breaks()).isFalse();
    }

    @Test
    void bottleDrinkTakes100AndLeavesTheRest() {
        FluidAmount full = new FluidAmount("minecraft:water", 500);
        DrinkContainerRules.Drink d = DrinkContainerRules.drink(FluidContainerKind.SILICA_GLASS_BOTTLE, full, 0.5f);
        assertThat(d.drained().amount()).isEqualTo(100);
        assertThat(d.content().amount()).isEqualTo(400);
        // a bottle with less than 100 mB gives what it has
        DrinkContainerRules.Drink small = DrinkContainerRules.drink(FluidContainerKind.OLIVINE_GLASS_BOTTLE,
            new FluidAmount("minecraft:water", 40), 0.5f);
        assertThat(small.drained().amount()).isEqualTo(40);
        assertThat(small.content().isEmpty()).isTrue();
    }

    @Test
    void breakDrawIsStrictlyBelowTheChance() {
        assertThat(DrinkContainerRules.drink(FluidContainerKind.JUG, WATER_100, 0.0199f).breaks()).isTrue();
        assertThat(DrinkContainerRules.drink(FluidContainerKind.JUG, WATER_100, 0.0201f).breaks()).isFalse();
        assertThat(DrinkContainerRules.drink(FluidContainerKind.VOLCANIC_GLASS_BOTTLE, WATER_100, 0.039f).breaks()).isTrue();
        assertThat(DrinkContainerRules.drink(FluidContainerKind.SILICA_GLASS_BOTTLE, WATER_100, 0.01f).breaks()).isFalse();
    }

    @Test
    void pourOutDrainsAll() {
        assertThat(DrinkContainerRules.pourOut(new FluidAmount("minecraft:water", 70)).amount()).isEqualTo(70);
        assertThat(DrinkContainerRules.pourOut(FluidAmount.EMPTY).isEmpty()).isTrue();
    }

    @Test
    void otherKindsAreNotDrinkContainers() {
        assertThat(DrinkContainerRules.isDrinkContainer(FluidContainerKind.WOODEN_BUCKET)).isFalse();
        assertThat(DrinkContainerRules.isDrinkContainer(FluidContainerKind.OLIVINE_GLASS_BOTTLE)).isTrue();
        assertThatThrownBy(() -> DrinkContainerRules.breakChance(FluidContainerKind.BARREL))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
