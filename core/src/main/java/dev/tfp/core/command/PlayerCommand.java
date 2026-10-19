/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/commands/PlayerCommand.java @ c3b4d9536
 */
package dev.tfp.core.command;

import dev.tfp.core.command.CommandArgs.Error;
import dev.tfp.core.command.CommandArgs.Parsed;
import dev.tfp.core.food.HungerState;
import dev.tfp.core.food.Nutrient;
import dev.tfp.core.food.NutritionState;
import dev.tfp.core.thirst.ThirstConfig;
import dev.tfp.core.thirst.ThirstState;
import java.util.List;

/**
 * The arithmetic of {@code /tfp player <target> query|set|reset|add} (TFC {@code PlayerCommand}, M6-CMD-01). The
 * target selector and the messages are glue. Here: the sub-command grammar, the ranges and defaults of each form, the
 * sum of an {@code add}, and the nutrition percentages of {@code query nutrition}.
 *
 * <p>TFC's ranges, kept as they are: {@code set} hunger and saturation 0 to 20, water 0 to 100; {@code add} hunger and
 * saturation -20 to 20, water 0 to 100 (so water can only be added to). {@code reset} sets hunger 20, saturation 5 and
 * water 100 (TFC {@code setSaturation(5)}, the vanilla start). Nutrition has no set, reset or add.
 *
 * <p>Results go through the core state types, so they keep their invariants: the hunger is clamped to 0 to 20 (TFC
 * passes the raw sum on), and the saturation is kept at zero or more. See the ledger's Divergences.
 */
public final class PlayerCommand {
    /** TFC {@code set hunger} and {@code reset hunger}: the vanilla maximum. */
    public static final int RESET_HUNGER = NutritionState.MAX_HUNGER;
    /** TFC {@code reset saturation}: 5. */
    public static final int RESET_SATURATION = 5;
    /** TFC {@code reset water}: the full value of {@code PlayerInfo.MAX_THIRST}. */
    public static final int RESET_WATER = 100;

    private PlayerCommand() {
    }

    /** The sub-command after the target: TFC's literals. */
    public enum Verb {
        QUERY, SET, RESET, ADD
    }

    /** The stat named by the second word. {@code NUTRITION} is only queried. */
    public enum Stat {
        HUNGER, SATURATION, WATER, NUTRITION
    }

    /**
     * One parsed line. {@code value} is the argument of {@code set} and {@code add}, the default of {@code reset}, and
     * 0 for {@code query}.
     */
    public record Request(Verb verb, Stat stat, int value) {
        public Request {
            if (verb == null || stat == null) {
                throw new IllegalArgumentException("verb and stat are required");
            }
        }
    }

    /** The player's food and thirst after the command. Unchanged for a query. */
    public record Outcome(HungerState hunger, ThirstState thirst) {
    }

    /**
     * Parses the words after the target selector, as Brigadier does: {@code query <stat>}, {@code reset <stat>},
     * {@code set <stat> <value>}, {@code add <stat> <value>}.
     *
     * @param args the words after the target, never null
     */
    public static Parsed<Request> parse(List<String> args) {
        String verbWord = CommandArgs.word(args, 0);
        if (verbWord == null) {
            return new Parsed.Refused<>(Error.MISSING_ARGUMENT, "");
        }
        final Verb verb;
        switch (CommandArgs.literal(verbWord)) {
            case "query" -> verb = Verb.QUERY;
            case "set" -> verb = Verb.SET;
            case "reset" -> verb = Verb.RESET;
            case "add" -> verb = Verb.ADD;
            default -> {
                return new Parsed.Refused<>(Error.UNKNOWN_SUBCOMMAND, verbWord);
            }
        }
        String statWord = CommandArgs.word(args, 1);
        if (statWord == null) {
            return new Parsed.Refused<>(Error.MISSING_ARGUMENT, "");
        }
        if (!(CommandArgs.enumValue(Stat.class, statWord) instanceof Parsed.Ok<Stat> ok)) {
            return new Parsed.Refused<>(Error.UNKNOWN_VALUE, statWord);
        }
        Stat stat = ok.value();
        if (verb == Verb.QUERY) {
            Parsed.Refused<Request> extra = CommandArgs.endOfLine(args, 2);
            return extra != null ? extra : new Parsed.Ok<>(new Request(verb, stat, 0));
        }
        if (stat == Stat.NUTRITION) {
            return new Parsed.Refused<>(Error.UNKNOWN_VALUE, statWord);
        }
        if (verb == Verb.RESET) {
            Parsed.Refused<Request> extra = CommandArgs.endOfLine(args, 2);
            return extra != null ? extra : new Parsed.Ok<>(new Request(verb, stat, resetValue(stat)));
        }
        String valueWord = CommandArgs.word(args, 2);
        if (valueWord == null) {
            return new Parsed.Refused<>(Error.MISSING_ARGUMENT, "");
        }
        int[] range = range(verb, stat);
        switch (CommandArgs.integer(valueWord, range[0], range[1])) {
            case Parsed.Refused<Integer> refused -> {
                return new Parsed.Refused<>(refused.error(), refused.token());
            }
            case Parsed.Ok<Integer> value -> {
                Parsed.Refused<Request> extra = CommandArgs.endOfLine(args, 3);
                return extra != null ? extra : new Parsed.Ok<>(new Request(verb, stat, value.value()));
            }
        }
    }

    /** The default of {@code reset} for a stat. */
    static int resetValue(Stat stat) {
        return switch (stat) {
            case HUNGER -> RESET_HUNGER;
            case SATURATION -> RESET_SATURATION;
            case WATER -> RESET_WATER;
            case NUTRITION -> throw new IllegalArgumentException("nutrition has no reset");
        };
    }

    /** The inclusive range of the value of {@code set} or {@code add}: {@code {min, max}}. */
    static int[] range(Verb verb, Stat stat) {
        return switch (stat) {
            case HUNGER -> verb == Verb.ADD ? new int[] {-20, 20} : new int[] {0, 20};
            case SATURATION -> verb == Verb.ADD ? new int[] {-20, 20} : new int[] {0, 20};
            case WATER -> new int[] {0, 100};
            case NUTRITION -> throw new IllegalArgumentException("nutrition has no value");
        };
    }

    /**
     * The state after the command: {@code set} and {@code reset} store the value, {@code add} stores the value plus the
     * current one. A query returns the state unchanged. Hunger and water go through the state types' clamps; the
     * saturation is kept at zero or more.
     *
     * @throws IllegalArgumentException for a nutrition request, which has no state change
     */
    public static Outcome apply(Request request, HungerState hunger, ThirstState thirst, ThirstConfig thirstConfig) {
        if (request.verb() == Verb.QUERY) {
            return new Outcome(hunger, thirst);
        }
        if (request.stat() == Stat.NUTRITION) {
            throw new IllegalArgumentException("nutrition has no state change");
        }
        boolean add = request.verb() == Verb.ADD;
        return switch (request.stat()) {
            case HUNGER -> new Outcome(
                hunger.withHunger(request.value() + (add ? hunger.hunger() : 0)), thirst);
            case SATURATION -> new Outcome(
                withSaturation(hunger, request.value() + (add ? hunger.saturation() : 0f)), thirst);
            case WATER -> new Outcome(
                hunger, thirst.withThirst(request.value() + (add ? thirst.thirst() : 0f), thirstConfig));
            case NUTRITION -> throw new IllegalArgumentException("nutrition has no state change");
        };
    }

    /**
     * The nutrition percentages for {@code query nutrition}: TFC's {@code (int) (100 * nutrient)} for each nutrient in
     * {@link Nutrient#VALUES} order.
     *
     * @param nutrition the nutrient fractions, indexed by {@link Nutrient#ordinal()}, length {@link Nutrient#TOTAL}
     * @return a new array of the percentages
     * @throws IllegalArgumentException if the length is not {@link Nutrient#TOTAL}
     */
    public static int[] nutritionPercents(float[] nutrition) {
        if (nutrition.length != Nutrient.TOTAL) {
            throw new IllegalArgumentException("nutrition length must be " + Nutrient.TOTAL + ": " + nutrition.length);
        }
        int[] percents = new int[Nutrient.TOTAL];
        for (Nutrient nutrient : Nutrient.VALUES) {
            percents[nutrient.ordinal()] = (int) (100 * nutrition[nutrient.ordinal()]);
        }
        return percents;
    }

    private static HungerState withSaturation(HungerState state, float saturation) {
        return new HungerState(state.hunger(), Math.max(0f, saturation), state.exhaustion(), state.lastTick());
    }
}
