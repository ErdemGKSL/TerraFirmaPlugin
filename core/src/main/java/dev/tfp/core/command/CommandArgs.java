/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/commands/PlayerCommand.java @ c3b4d9536 (the argument types of the TFC commands,
 * IntegerArgumentType, EnumArgument, ItemArgument)
 */
package dev.tfp.core.command;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Argument parsing shared by the {@code /tfp} command ports (M6-CMD-01). It is the pure side of Brigadier's argument
 * types: the value, its range and the reason a token is refused. Brigadier's messages, the sender checks and the
 * registration are glue.
 *
 * <p>The command name is TFC's, under the {@code /tfp} root in the glue. Tokens are the words after the command name
 * and after the target selector, which the glue resolves.
 */
public final class CommandArgs {
    private static final Pattern ITEM_ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");

    private CommandArgs() {
    }

    /** Why a token or an argument list is refused. */
    public enum Error {
        /** A required argument is missing from the end of the line. */
        MISSING_ARGUMENT,
        /** The first word is not a sub-command of this command. */
        UNKNOWN_SUBCOMMAND,
        /** A value is not an integer. */
        NOT_AN_INTEGER,
        /** An integer is outside the range of its argument. */
        OUT_OF_RANGE,
        /** A word is not one of the named values of its argument. */
        UNKNOWN_VALUE,
        /** A token is not in the argument's syntax. */
        BAD_SYNTAX,
        /** Words are left over after the last argument. */
        TOO_MANY_ARGUMENTS
    }

    /**
     * A parse result: the value, or the error and the token it refers to. Values are immutable records.
     *
     * @param <T> the parsed value
     */
    public sealed interface Parsed<T> permits Parsed.Ok, Parsed.Refused {
        /** The parsed value. */
        record Ok<T>(T value) implements Parsed<T> {
        }

        /** A refused parse, with the error and the offending token (empty when the line ended early). */
        record Refused<T>(Error error, String token) implements Parsed<T> {
            public Refused {
                token = token == null ? "" : token;
            }
        }

        /** Whether the parse produced a value. */
        default boolean isOk() {
            return this instanceof Ok<T>;
        }
    }

    /**
     * An integer argument with an inclusive range, as Brigadier's {@code IntegerArgumentType.integer(min, max)}.
     *
     * @throws IllegalArgumentException if {@code min > max}
     */
    public static Parsed<Integer> integer(String token, int min, int max) {
        if (min > max) {
            throw new IllegalArgumentException("min > max: " + min + " > " + max);
        }
        final int value;
        try {
            value = Integer.parseInt(token);
        } catch (NumberFormatException e) {
            return new Parsed.Refused<>(Error.NOT_AN_INTEGER, token);
        }
        if (value < min || value > max) {
            return new Parsed.Refused<>(Error.OUT_OF_RANGE, token);
        }
        return new Parsed.Ok<>(value);
    }

    /**
     * A named value, as Brigadier's {@code EnumArgument}: the constant name, compared without case.
     *
     * @throws NullPointerException if {@code type} is null
     */
    public static <E extends Enum<E>> Parsed<E> enumValue(Class<E> type, String token) {
        for (E constant : type.getEnumConstants()) {
            if (constant.name().equalsIgnoreCase(token)) {
                return new Parsed.Ok<>(constant);
            }
        }
        return new Parsed.Refused<>(Error.UNKNOWN_VALUE, token);
    }

    /**
     * An item id as Brigadier's {@code ItemArgument} reads it: {@code namespace:path}, or {@code path} in the
     * {@code minecraft} namespace. Anything after a {@code [} or {@code {} (components or NBT) is dropped: TFC only uses
     * the item's default instance, so the components do not change the result.
     */
    public static Parsed<String> itemId(String token) {
        int cut = indexOfAny(token, '[', '{');
        String id = (cut < 0 ? token : token.substring(0, cut)).trim();
        if (id.isEmpty()) {
            return new Parsed.Refused<>(Error.BAD_SYNTAX, token);
        }
        if (id.indexOf(':') < 0) {
            id = "minecraft:" + id;
        }
        if (!ITEM_ID.matcher(id).matches()) {
            return new Parsed.Refused<>(Error.BAD_SYNTAX, token);
        }
        return new Parsed.Ok<>(id);
    }

    /**
     * The argument list must end here: the first leftover word is refused as {@link Error#TOO_MANY_ARGUMENTS}.
     *
     * @return the refusal, or {@code null} when the list is exhausted at {@code index}
     */
    static <T> Parsed.Refused<T> endOfLine(List<String> args, int index) {
        return index < args.size() ? new Parsed.Refused<>(Error.TOO_MANY_ARGUMENTS, args.get(index)) : null;
    }

    /** The word at {@code index}, or {@code null} when the line ended before it. */
    static String word(List<String> args, int index) {
        return index < args.size() ? args.get(index) : null;
    }

    /** Lower-case form of a sub-command word, for comparisons with the literals of TFC. */
    static String literal(String word) {
        return word.toLowerCase(Locale.ROOT);
    }

    private static int indexOfAny(String text, char... chars) {
        int best = -1;
        for (char c : chars) {
            int at = text.indexOf(c);
            if (at >= 0 && (best < 0 || at < best)) {
                best = at;
            }
        }
        return best;
    }
}
