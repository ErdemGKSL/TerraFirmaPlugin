/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.player;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.recipe.fluid.FluidIngredient;
import dev.tfp.core.thirst.Drinkable;
import dev.tfp.core.thirst.Drinkables;
import java.util.List;
import java.util.Optional;
import java.util.function.DoubleSupplier;
import org.junit.jupiter.api.Test;

/**
 * The pure drink rule of a source block: TFC's {@code doDrink} drinks 100 mB (four times the JSON's 25 mB values), the
 * cap, the cooldown, the full-player gate, salt water and its effect, and the no-drinkable gate.
 */
class WaterDrinkTest {
    /** A player at thirst 50 whose clock was evaluated at tick 1000, with no cooldown running. */
    private static final PlayerData THIRSTY = PlayerData.NEW.withThirstClock(50f, 1000L, 0L);
    /** TFC {@code water.json}. */
    static final Optional<Drinkable> WATER = Optional.of(new Drinkable(new FluidIngredient.Single(Drinkables.WATER), 0,
            false, 10f, List.of()));
    static final Drinkable.Effect THIRST = new Drinkable.Effect("tfp:thirst", 600, 1, 0.25f);
    /** TFC {@code salt_water.json}. */
    static final Optional<Drinkable> SALT = Optional.of(new Drinkable(new FluidIngredient.Single(Drinkables.SALT_WATER),
            0, true, -1f, List.of(THIRST)));
    private static final DoubleSupplier NEVER = () -> 0.99;
    private static final DoubleSupplier ALWAYS = () -> 0.0;

    @Test
    void aSourceDrinkAddsFourTimesTheWaterOfTheJson() {
        // TFC doDrink passes JUG_DRINK_MB (100 mB) to onDrink, so water.json's 10 per 25 mB gives 40 thirst.
        WaterDrink.Result out = WaterDrink.drink(THIRSTY, 1000L, 22f, WATER, NEVER);
        assertThat(out.drunk()).isTrue();
        assertThat(out.data().thirst()).isEqualTo(90f);
        assertThat(out.data().lastDrinkTick()).isEqualTo(1000L);
        assertThat(out.effects()).isEmpty();
    }

    @Test
    void drinkIsCappedAtMaxThirst() {
        PlayerData nearlyFull = PlayerData.NEW.withThirstClock(95f, 1000L, 0L);
        assertThat(WaterDrink.drink(nearlyFull, 1000L, 22f, WATER, NEVER).data().thirst())
                .isEqualTo(PlayerData.MAX_THIRST);
    }

    @Test
    void fullPlayerDoesNotDrinkFromWater() {
        PlayerData full = PlayerData.NEW.withThirstClock(PlayerData.MAX_THIRST, 1000L, 0L);
        WaterDrink.Result out = WaterDrink.drink(full, 1000L, 22f, WATER, NEVER);
        assertThat(out.drunk()).isFalse();
        assertThat(out.data().thirst()).isEqualTo(PlayerData.MAX_THIRST);
        assertThat(out.data().lastDrinkTick()).isEqualTo(Long.MIN_VALUE);
    }

    @Test
    void drinkWithinTheCooldownIsRefused() {
        PlayerData justDrank = PlayerData.NEW.withThirstClock(50f, 1010L, 0L).withLastDrinkTick(1000L);
        WaterDrink.Result out = WaterDrink.drink(justDrank, 1010L, 22f, WATER, NEVER);
        assertThat(out.drunk()).isFalse();
        assertThat(out.data().thirst()).isEqualTo(50f);
        assertThat(out.data().lastDrinkTick()).isEqualTo(1000L);
    }

    @Test
    void drinkAfterTheCooldownIsAllowed() {
        PlayerData justDrank = PlayerData.NEW.withThirstClock(50f, 1011L, 0L).withLastDrinkTick(1000L);
        assertThat(WaterDrink.drink(justDrank, 1011L, 22f, WATER, NEVER).data().thirst()).isEqualTo(90f);
    }

    @Test
    void saltWaterTakesFourThirstEvenWhenFullAndMayGiveTheThirstEffect() {
        PlayerData full = PlayerData.NEW.withThirstClock(PlayerData.MAX_THIRST, 1000L, 0L);
        WaterDrink.Result hit = WaterDrink.drink(full, 1000L, 22f, SALT, ALWAYS);
        assertThat(hit.drunk()).isTrue();
        assertThat(hit.data().thirst()).isEqualTo(96f);
        assertThat(hit.effects()).containsExactly(THIRST);
        WaterDrink.Result miss = WaterDrink.drink(full, 1000L, 22f, SALT, NEVER);
        assertThat(miss.drunk()).isTrue();
        assertThat(miss.effects()).isEmpty();
    }

    @Test
    void noDrinkableGivesNoChange() {
        // Not a drinkable fluid: the stored data is returned as it was, with no decay written either.
        PlayerData later = THIRSTY.withThirstClock(50f, 1000L, 0L);
        WaterDrink.Result out = WaterDrink.drink(later, 25_000L, 22f, Optional.empty(), NEVER);
        assertThat(out.data()).isSameAs(later);
        assertThat(out.drunk()).isFalse();
    }
}
