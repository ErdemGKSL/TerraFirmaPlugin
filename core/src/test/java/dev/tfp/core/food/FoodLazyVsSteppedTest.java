/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.food;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.calendar.CalendarState;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * M3-SURV-01: lazy evaluation is exact. The stage computed once at time T from the stored state equals the
 * stage reached by stepping through the same interval in small steps, sanitizing (latching rotten) at each step
 * the way a ticking implementation would.
 */
class FoodLazyVsSteppedTest {
    private static final FoodConfig CFG = FoodConfig.DEFAULT;

    private record Case(float food, List<FoodTrait> traits) {
    }

    private static final List<Case> CASES = List.of(
            new Case(4.9f, List.of()),                                          // blueberry
            new Case(2.0f, List.of(FoodTrait.SALTED)),                          // salted beef
            new Case(2.25f, List.of(FoodTrait.BRINED, FoodTrait.PICKLED)),      // pickled
            new Case(3.0f, List.of(FoodTrait.PRESERVED)),                       // cod in a vessel
            new Case(1.5f, List.of(FoodTrait.WOOD_GRILLED)),                    // cooked beef
            new Case(2.0f, List.of(FoodTrait.BURNT_TO_A_CRISP)),
            new Case(2.0f, List.of(FoodTrait.CANNED)),                          // never rots
            new Case(Float.POSITIVE_INFINITY, List.of()));                      // rotten flesh: rotten at once

    @Test
    void steppingInSmallStepsMatchesTheDirectStageAtEveryTick() {
        for (Case c : CASES) {
            long created = 1_238_400;
            FoodState stored = new FoodState(created, c.traits);
            float m = stored.decayDateModifier(c.food, CFG);
            long horizon = Float.isInfinite(m) || m == 0 ? 50_000 : 2 * FoodDecay.lifeTime(m);
            long step = 37; // small, and coprime to the window, so steps land on every residue
            FoodState stepped = stored;
            // a second, counter-based stepper: remaining life decremented each step, no timestamps
            long remaining = Float.isInfinite(m) ? Long.MAX_VALUE : (m == 0 ? 0 : FoodDecay.lifeTime(m));
            for (long now = created; now <= created + horizon; now += step) {
                if (now > created) {
                    remaining = remaining == Long.MAX_VALUE ? remaining : remaining - step;
                }
                stepped = stepped.sanitize(c.food, now, CFG);
                FoodStage direct = FoodDecay.stage(created, now, c.food, c.traits);
                FoodStage viaSteps = stepped.stage(c.food, now, CFG);
                assertThat(viaSteps).as("%s %s at %d", c.food, c.traits, now).isEqualTo(direct);
                FoodStage counter = remaining == Long.MAX_VALUE ? FoodStage.NEVER_ROTS
                        : (remaining <= 0 ? FoodStage.ROTTEN : FoodStage.FRESH);
                assertThat(counter).as("counter %s %s at %d", c.food, c.traits, now).isEqualTo(direct);
            }
        }
    }

    @Test
    void theRotTickIsExactWithSingleTickSteps() {
        // blueberry: life (long) (1/4.9f * 528000) = 107 755 ticks; step 1 tick across the boundary
        FoodState stored = new FoodState(0, List.of());
        long rot = FoodDecay.rottenDate(0, stored.decayDateModifier(4.9f, CFG));
        assertThat(rot).isEqualTo(107_755);
        FoodState stepped = stored;
        for (long now = rot - 500; now <= rot + 500; now++) {
            stepped = stepped.sanitize(4.9f, now, CFG);
            FoodStage expected = now < rot ? FoodStage.FRESH : FoodStage.ROTTEN;
            assertThat(stepped.stage(4.9f, now, CFG)).isEqualTo(expected);
            assertThat(FoodDecay.stage(0, now, 4.9f, List.of())).isEqualTo(expected);
        }
    }

    @Test
    void steppingTheCalendarGivesTheSameFoodStageAsOneJump() {
        // The food clock is the calendar's player ticks; the calendar advance is exact in steps (M2-CAL-01).
        CalendarState start = CalendarState.defaults().withRunning(true).advance(1_000_000);
        long created = FoodState.createdAt(start.playerTicks(), CFG).creationDate();
        FoodState food = new FoodState(created, List.of(FoodTrait.SALTED));
        long total = 600_000;
        CalendarState jumped = start.advance(total);
        CalendarState stepped = start;
        FoodState steppedFood = food;
        for (long t = 0; t < total; t += 113) {
            stepped = stepped.advance(Math.min(113, total - t));
            steppedFood = steppedFood.sanitize(2.0f, stepped.playerTicks(), CFG);
        }
        assertThat(stepped).isEqualTo(jumped);
        assertThat(steppedFood.stage(2.0f, stepped.playerTicks(), CFG))
                .isEqualTo(food.stage(2.0f, jumped.playerTicks(), CFG))
                .isEqualTo(FoodStage.ROTTEN);
    }
}
