/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.tfp.core.command.CommandArgs.Error;
import dev.tfp.core.command.CommandArgs.Parsed;
import dev.tfp.core.command.PlayerCommand.Request;
import dev.tfp.core.command.PlayerCommand.Stat;
import dev.tfp.core.command.PlayerCommand.Verb;
import dev.tfp.core.food.HungerState;
import dev.tfp.core.thirst.ThirstConfig;
import dev.tfp.core.thirst.ThirstState;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * {@code /tfp player} from TFC {@code PlayerCommand.java}. The line numbers name the argument definitions and the
 * set functions the cases follow. The player's food is a {@link HungerState} with hunger 10, saturation 3, no
 * exhaustion; thirst is a {@link ThirstState} at 80 with the default config (max 100).
 */
class PlayerCommandTest {
    static final ThirstConfig THIRST = ThirstConfig.DEFAULT;
    static final HungerState HUNGER = new HungerState(10, 3f, 0L, 7L);
    static final ThirstState THIRST_80 = new ThirstState(80f, 7L);

    static List<String> words(String line) {
        return line.isEmpty() ? List.of() : List.of(line.split(" "));
    }

    static Request parsed(String line) {
        return ((Parsed.Ok<Request>) PlayerCommand.parse(words(line))).value();
    }

    /** PlayerCommand.java lines 36-47: {@code query} takes one of four stats and no value. */
    @Test
    void queryTakesAnyStatAndNoValue() {
        assertThat(parsed("query hunger")).isEqualTo(new Request(Verb.QUERY, Stat.HUNGER, 0));
        assertThat(parsed("query nutrition")).isEqualTo(new Request(Verb.QUERY, Stat.NUTRITION, 0));
    }

    /** PlayerCommand.java lines 69, 72, 75: reset hunger 20, saturation 5, water 100 (no argument). */
    @Test
    void resetDefaultsAreTfcs() {
        assertThat(parsed("reset hunger").value()).isEqualTo(20);
        assertThat(parsed("reset saturation").value()).isEqualTo(5);
        assertThat(parsed("reset water").value()).isEqualTo(100);
    }

    /** PlayerCommand.java line 52: {@code set hunger} takes 0 to 20. */
    @Test
    void setHungerRangeIsZeroToTwenty() {
        assertThat(parsed("set hunger 20")).isEqualTo(new Request(Verb.SET, Stat.HUNGER, 20));
        assertThat(PlayerCommand.parse(words("set hunger 21")))
            .isEqualTo(new Parsed.Refused<>(Error.OUT_OF_RANGE, "21"));
    }

    /** PlayerCommand.java line 80 and line 90: {@code add hunger} is -20 to 20, {@code add water} is 0 to 100. */
    @Test
    void addRangesAreTfcs() {
        assertThat(parsed("add hunger -20")).isEqualTo(new Request(Verb.ADD, Stat.HUNGER, -20));
        assertThat(PlayerCommand.parse(words("add hunger -21")))
            .isEqualTo(new Parsed.Refused<>(Error.OUT_OF_RANGE, "-21"));
        assertThat(PlayerCommand.parse(words("add water -1")))
            .isEqualTo(new Parsed.Refused<>(Error.OUT_OF_RANGE, "-1"));
    }

    /** PlayerCommand.java: nutrition is only queried; there is no set, reset or add literal for it. */
    @Test
    void nutritionHasNoSetResetOrAdd() {
        assertThat(PlayerCommand.parse(words("set nutrition 3")))
            .isEqualTo(new Parsed.Refused<>(Error.UNKNOWN_VALUE, "nutrition"));
        assertThat(PlayerCommand.parse(words("reset nutrition")))
            .isEqualTo(new Parsed.Refused<>(Error.UNKNOWN_VALUE, "nutrition"));
    }

    @Test
    void shapeErrors() {
        assertThat(PlayerCommand.parse(words("")))
            .isEqualTo(new Parsed.Refused<>(Error.MISSING_ARGUMENT, ""));
        assertThat(PlayerCommand.parse(words("set hunger")))
            .isEqualTo(new Parsed.Refused<>(Error.MISSING_ARGUMENT, ""));
        assertThat(PlayerCommand.parse(words("fly hunger")))
            .isEqualTo(new Parsed.Refused<>(Error.UNKNOWN_SUBCOMMAND, "fly"));
        assertThat(PlayerCommand.parse(words("query hunger now")))
            .isEqualTo(new Parsed.Refused<>(Error.TOO_MANY_ARGUMENTS, "now"));
    }

    /** PlayerCommand.java lines 133-140: {@code set hunger} stores the value; saturation is left as it is. */
    @Test
    void setHungerStoresTheValue() {
        var out = PlayerCommand.apply(new Request(Verb.SET, Stat.HUNGER, 15), HUNGER, THIRST_80, THIRST);
        assertThat(out.hunger()).isEqualTo(new HungerState(15, 3f, 0L, 7L));
        assertThat(out.thirst()).isEqualTo(THIRST_80);
    }

    /** PlayerCommand.java lines 143-150: {@code add saturation} adds to the current saturation. */
    @Test
    void addSaturationAddsToTheCurrentValue() {
        var out = PlayerCommand.apply(new Request(Verb.ADD, Stat.SATURATION, 4), HUNGER, THIRST_80, THIRST);
        assertThat(out.hunger().saturation()).isEqualTo(7f);
    }

    /**
     * PlayerCommand.java lines 133-140 with the core clamp: hunger 18 plus 5 is 23, which the hunger state keeps at 20.
     * Divergence: see the ledger row for PlayerCommand add.
     */
    @Test
    void addHungerIsClampedToTheMaximum() {
        var out = PlayerCommand.apply(new Request(Verb.ADD, Stat.HUNGER, 5),
            new HungerState(18, 3f, 0L, 7L), THIRST_80, THIRST);
        assertThat(out.hunger().hunger()).isEqualTo(20);
    }

    /** PlayerCommand.java line 80 allows -20: 10 minus 20 is below zero, and the hunger state keeps it at 0. */
    @Test
    void addNegativeHungerIsClampedToZero() {
        var out = PlayerCommand.apply(new Request(Verb.ADD, Stat.HUNGER, -20), HUNGER, THIRST_80, THIRST);
        assertThat(out.hunger().hunger()).isEqualTo(0);
    }

    /** PlayerCommand.java line 80 with saturation 3 minus 20: the saturation is kept at zero or more. */
    @Test
    void addNegativeSaturationIsKeptAtZero() {
        var out = PlayerCommand.apply(new Request(Verb.ADD, Stat.SATURATION, -20), HUNGER, THIRST_80, THIRST);
        assertThat(out.hunger().saturation()).isEqualTo(0f);
    }

    /** PlayerCommand.java lines 153-161: {@code add water} adds, and the thirst is capped at the maximum. */
    @Test
    void addWaterIsCappedAtTheMaximum() {
        var out = PlayerCommand.apply(new Request(Verb.ADD, Stat.WATER, 30), HUNGER, THIRST_80, THIRST);
        assertThat(out.thirst().thirst()).isEqualTo(100f);
        var set = PlayerCommand.apply(new Request(Verb.SET, Stat.WATER, 50), HUNGER, THIRST_80, THIRST);
        assertThat(set.thirst().thirst()).isEqualTo(50f);
    }

    /** PlayerCommand.java lines 69-75: reset sets the TFC default of each stat. */
    @Test
    void resetSetsTheDefaults() {
        var out = PlayerCommand.apply(new Request(Verb.RESET, Stat.SATURATION, 5), HUNGER, THIRST_80, THIRST);
        assertThat(out.hunger()).isEqualTo(new HungerState(10, 5f, 0L, 7L));
        var water = PlayerCommand.apply(new Request(Verb.RESET, Stat.WATER, 100), HUNGER, THIRST_80, THIRST);
        assertThat(water.thirst().thirst()).isEqualTo(100f);
    }

    @Test
    void queryChangesNothing() {
        var out = PlayerCommand.apply(new Request(Verb.QUERY, Stat.HUNGER, 0), HUNGER, THIRST_80, THIRST);
        assertThat(out.hunger()).isEqualTo(HUNGER);
        assertThat(out.thirst()).isEqualTo(THIRST_80);
    }

    @Test
    void nutritionHasNoStateChange() {
        var query = PlayerCommand.apply(new Request(Verb.QUERY, Stat.NUTRITION, 0), HUNGER, THIRST_80, THIRST);
        assertThat(query.hunger()).isEqualTo(HUNGER);
        assertThatThrownBy(() -> PlayerCommand.apply(
            new Request(Verb.SET, Stat.NUTRITION, 0), HUNGER, THIRST_80, THIRST))
            .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * PlayerCommand.java line 125: {@code (int) (100 * nutrition[i])} per nutrient, in {@code Nutrient.VALUES} order.
     * The values are exact in float, so the truncation is not in question.
     */
    @Test
    void nutritionPercentsTruncateEachFraction() {
        assertThat(PlayerCommand.nutritionPercents(new float[] {0.25f, 0.5f, 0f, 0.4f, 1f}))
            .containsExactly(25, 50, 0, 40, 100);
    }

    @Test
    void nutritionPercentsNeedFiveValues() {
        assertThatThrownBy(() -> PlayerCommand.nutritionPercents(new float[] {0.5f}))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
