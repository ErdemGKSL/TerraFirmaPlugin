/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.thirst;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.tfp.core.recipe.fluid.FluidIngredient;
import dev.tfp.core.registry.DataLoader;
import dev.tfp.core.registry.KnownIds;
import dev.tfp.core.registry.RegistrySnapshot;
import dev.tfp.core.tag.TagRegistry;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * PAR-SURV-04: TFC's {@code drinkable} data from the generated content, the lookup by fluid, the 100 mB source drink
 * of {@code doDrink} and the effect chance of {@code onDrink}.
 */
class DrinkablesTest {
    private static final ThirstConfig CFG = ThirstConfig.DEFAULT;

    private static Drinkables generated() throws IOException {
        String configured = System.getProperty("tfp.contentData");
        Path data = Path.of(configured == null ? "content/generated/content/data" : configured);
        assumeTrue(Files.isDirectory(data), "no generated content at " + data.toAbsolutePath());
        RegistrySnapshot snapshot = DataLoader.standard(KnownIds.fromDataRoot(data)).load(List.of(data));
        return Drinkables.fromRegistry(snapshot);
    }

    @Test
    void generatedDataHasTfcWaterAndSaltWater() throws IOException {
        Drinkables d = generated();
        assertThat(d.size()).isEqualTo(5);
        Drinkable water = d.get(Drinkables.WATER).orElseThrow();
        assertThat(water.water()).isEqualTo(10f);
        assertThat(water.mayDrinkWhenFull()).isFalse();
        assertThat(water.effects()).isEmpty();
        assertThat(d.get("tfp:river_water")).contains(water);

        Drinkable salt = d.get(Drinkables.SALT_WATER).orElseThrow();
        assertThat(salt.water()).isEqualTo(-1f);
        assertThat(salt.mayDrinkWhenFull()).isTrue();
        assertThat(salt.consumeChance()).isZero();
        assertThat(salt.effects()).containsExactly(new Drinkable.Effect("tfp:thirst", 600, 1, 0.25f));

        assertThat(d.get("minecraft:lava")).isEmpty();
    }

    @Test
    void aSourceDrinkIsFourReferenceDrinks() {
        Drinkable water = new Drinkable(new FluidIngredient.Single(Drinkables.WATER), 0, false, 10f, List.of());
        Drinkable.Outcome out = water.drink(new ThirstState(50f, 0), Drinkable.SOURCE_DRINK_MB, () -> 0.5, CFG);
        assertThat(out.thirst().thirst()).isEqualTo(90f);
        assertThat(water.drink(new ThirstState(80f, 0), Drinkable.SOURCE_DRINK_MB, () -> 0.5, CFG).thirst().thirst())
                .isEqualTo(100f);
        assertThat(water.canDrink(new ThirstState(100f, 0), CFG)).isFalse();
    }

    /** TFC's chance for 100 mB: {@code 1 - (1 - 0.25)^4 = 0.68359375} (golden from TFC's expression). */
    @Test
    void saltWaterTakesThirstAndGivesTheEffectAtTfcsChance() {
        Drinkable.Effect thirst = new Drinkable.Effect("tfp:thirst", 600, 1, 0.25f);
        Drinkable salt = new Drinkable(new FluidIngredient.Single(Drinkables.SALT_WATER), 0, true, -1f,
                List.of(thirst));
        assertThat(Drinkable.chance(0.25f, 4f)).isEqualTo(0.68359375);
        assertThat(Drinkable.chance(0.8f, 4f)).isEqualTo(0.9984000003814697);
        assertThat(salt.canDrink(new ThirstState(100f, 0), CFG)).as("may drink when full").isTrue();

        Drinkable.Outcome hit = salt.drink(new ThirstState(100f, 0), Drinkable.SOURCE_DRINK_MB, () -> 0.68, CFG);
        assertThat(hit.thirst().thirst()).isEqualTo(96f);
        assertThat(hit.applied()).containsExactly(thirst);
        Drinkable.Outcome miss = salt.drink(new ThirstState(2f, 0), Drinkable.SOURCE_DRINK_MB, () -> 0.69, CFG);
        assertThat(miss.thirst().thirst()).as("floored at 0").isZero();
        assertThat(miss.applied()).isEmpty();
    }

    @Test
    void tagIngredientsAndTheFirstMatchWins() {
        Drinkable a = new Drinkable(new FluidIngredient.AnyOf(List.of(new FluidIngredient.Single("x:a"),
                new FluidIngredient.Single("x:b"))), 0, false, 1f, List.of());
        Drinkable b = new Drinkable(new FluidIngredient.Single("x:b"), 0, false, 2f, List.of());
        Drinkables d = new Drinkables(List.of(a, b), TagRegistry.EMPTY);
        assertThat(d.get("x:b")).contains(a);
        assertThat(d.get("x:c")).isEmpty();
        assertThat(Drinkables.EMPTY.get(Drinkables.WATER)).isEmpty();
    }
}
