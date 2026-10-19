/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/commands/WeatherCommand.java @ c3b4d9536
 */
package dev.tfp.core.command;

import dev.tfp.core.command.CommandArgs.Error;
import dev.tfp.core.command.CommandArgs.Parsed;
import java.util.List;

/**
 * {@code /tfp weather enable|disable|clear|rain} (TFC {@code WeatherCommand}, M6-CMD-01). The weather itself is not
 * vanilla: TFC has no weather entity to set, so {@code enable} and {@code disable} only switch the weather of the
 * world (the flag is glue, in the world's tracker), and {@code clear} and {@code rain} are refused with a hint to use
 * {@code /time set clear} or {@code /time set rain}, as in TFC.
 *
 * <p>The sub-command takes no value. The state does not affect the decision: TFC sets the flag whatever it was.
 */
public final class WeatherCommand {
    private WeatherCommand() {
    }

    /** The sub-commands, TFC's literals. */
    public enum Action {
        ENABLE, DISABLE, CLEAR, RAIN
    }

    /** The hint TFC gives for a refused sub-command: the {@code /time} command that does the job. */
    public enum Hint {
        CLEAR("/time set clear"), RAIN("/time set rain");

        private final String command;

        Hint(String command) {
            this.command = command;
        }

        /** The command the player should use. */
        public String command() {
            return command;
        }
    }

    /** What the command does. */
    public sealed interface Decision {
        /** Sets the weather flag of the world to {@code enabled}. */
        record SetEnabled(boolean enabled) implements Decision {
        }

        /** Changes nothing; the player is told to use {@code hint.command()}. */
        record Refused(Hint hint) implements Decision {
        }
    }

    /**
     * Parses the words after {@code weather}: exactly one sub-command.
     *
     * @param args the words after the command name, never null
     */
    public static Parsed<Action> parse(List<String> args) {
        String word = CommandArgs.word(args, 0);
        if (word == null) {
            return new Parsed.Refused<>(Error.MISSING_ARGUMENT, "");
        }
        Parsed.Refused<Action> extra = CommandArgs.endOfLine(args, 1);
        if (extra != null) {
            return extra;
        }
        return switch (CommandArgs.literal(word)) {
            case "enable" -> new Parsed.Ok<>(Action.ENABLE);
            case "disable" -> new Parsed.Ok<>(Action.DISABLE);
            case "clear" -> new Parsed.Ok<>(Action.CLEAR);
            case "rain" -> new Parsed.Ok<>(Action.RAIN);
            default -> new Parsed.Refused<>(Error.UNKNOWN_SUBCOMMAND, word);
        };
    }

    /** The decision for one sub-command. Pure: it does not read the current flag. */
    public static Decision decide(Action action) {
        return switch (action) {
            case ENABLE -> new Decision.SetEnabled(true);
            case DISABLE -> new Decision.SetEnabled(false);
            case CLEAR -> new Decision.Refused(Hint.CLEAR);
            case RAIN -> new Decision.Refused(Hint.RAIN);
        };
    }
}
