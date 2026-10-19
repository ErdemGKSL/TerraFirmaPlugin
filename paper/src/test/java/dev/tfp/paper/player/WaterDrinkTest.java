/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.player;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** The pure drink rule of a plain water source: TFC's 10 water per hand drink, the cap, the cooldown and the block gate. */
class WaterDrinkTest {
    /** A player at thirst 50 whose clock was evaluated at tick 1000, with no cooldown running. */
    private static final PlayerData THIRSTY = PlayerData.NEW.withThirstClock(50f, 1000L, 0L);

    @Test
    void drinkAddsTheWaterOfOneHandDrink() {
        // TFC water.json: "water": 10.0 at the 25 mB reference, so one drink gives 10 thirst.
        PlayerData out = WaterDrink.drink(THIRSTY, 1000L, 22f, true);
        assertThat(out.thirst()).isEqualTo(60f);
        assertThat(out.lastDrinkTick()).isEqualTo(1000L);
    }

    @Test
    void drinkIsCappedAtMaxThirst() {
        PlayerData nearlyFull = PlayerData.NEW.withThirstClock(95f, 1000L, 0L);
        assertThat(WaterDrink.drink(nearlyFull, 1000L, 22f, true).thirst()).isEqualTo(PlayerData.MAX_THIRST);
    }

    @Test
    void fullPlayerDoesNotDrinkFromWater() {
        // TFC canDrink: water 10 > 0 and not mayDrinkWhenFull, so a full player is refused and gains nothing.
        PlayerData full = PlayerData.NEW.withThirstClock(PlayerData.MAX_THIRST, 1000L, 0L);
        PlayerData out = WaterDrink.drink(full, 1000L, 22f, true);
        assertThat(out.thirst()).isEqualTo(PlayerData.MAX_THIRST);
        assertThat(out.lastDrinkTick()).isEqualTo(Long.MIN_VALUE);
    }

    @Test
    void drinkWithinTheCooldownIsRefused() {
        // TFC canDrink: lastDrinkTick + 10 < ticks must hold, so a drink at the same tick as the last one is refused.
        PlayerData justDrank = PlayerData.NEW.withThirstClock(50f, 1010L, 0L).withLastDrinkTick(1000L);
        PlayerData out = WaterDrink.drink(justDrank, 1010L, 22f, true);
        assertThat(out.thirst()).isEqualTo(50f);
        assertThat(out.lastDrinkTick()).isEqualTo(1000L);
    }

    @Test
    void drinkAfterTheCooldownIsAllowed() {
        PlayerData justDrank = PlayerData.NEW.withThirstClock(50f, 1011L, 0L).withLastDrinkTick(1000L);
        assertThat(WaterDrink.drink(justDrank, 1011L, 22f, true).thirst()).isEqualTo(60f);
    }

    @Test
    void nonWaterBlockGivesNoChange() {
        // Not a plain water source: the stored data is returned as it was, with no decay written either.
        PlayerData later = THIRSTY.withThirstClock(50f, 1000L, 0L);
        assertThat(WaterDrink.drink(later, 25_000L, 22f, false)).isSameAs(later);
    }
}
