/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.command;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.command.CommandArgs.Error;
import dev.tfp.core.command.CommandArgs.Parsed;
import dev.tfp.core.command.WeatherCommand.Action;
import dev.tfp.core.command.WeatherCommand.Decision;
import dev.tfp.core.command.WeatherCommand.Hint;
import java.util.List;
import org.junit.jupiter.api.Test;

/** {@code /tfp weather} from TFC {@code WeatherCommand.java}: the four literals, and the two refusals. */
class WeatherCommandTest {
    /** WeatherCommand.java lines 24-34: enable, disable, clear and rain are the only literals. */
    @Test
    void parsesTheFourLiterals() {
        assertThat(WeatherCommand.parse(List.of("enable"))).isEqualTo(new Parsed.Ok<>(Action.ENABLE));
        assertThat(WeatherCommand.parse(List.of("disable"))).isEqualTo(new Parsed.Ok<>(Action.DISABLE));
        assertThat(WeatherCommand.parse(List.of("clear"))).isEqualTo(new Parsed.Ok<>(Action.CLEAR));
        assertThat(WeatherCommand.parse(List.of("rain"))).isEqualTo(new Parsed.Ok<>(Action.RAIN));
    }

    /** WeatherCommand.java lines 40: enable sets the world's weather flag to true. */
    @Test
    void enableAndDisableSetTheFlag() {
        assertThat(WeatherCommand.decide(Action.ENABLE)).isEqualTo(new Decision.SetEnabled(true));
        assertThat(WeatherCommand.decide(Action.DISABLE)).isEqualTo(new Decision.SetEnabled(false));
    }

    /** WeatherCommand.java lines 31 and 34: clear and rain change nothing and point at /time set. */
    @Test
    void clearAndRainAreRefusedWithTheTimeHint() {
        assertThat(WeatherCommand.decide(Action.CLEAR)).isEqualTo(new Decision.Refused(Hint.CLEAR));
        assertThat(WeatherCommand.decide(Action.RAIN)).isEqualTo(new Decision.Refused(Hint.RAIN));
        assertThat(Hint.CLEAR.command()).isEqualTo("/time set clear");
        assertThat(Hint.RAIN.command()).isEqualTo("/time set rain");
    }

    @Test
    void shapeErrors() {
        assertThat(WeatherCommand.parse(List.of())).isEqualTo(new Parsed.Refused<>(Error.MISSING_ARGUMENT, ""));
        assertThat(WeatherCommand.parse(List.of("fog"))).isEqualTo(new Parsed.Refused<>(Error.UNKNOWN_SUBCOMMAND, "fog"));
        assertThat(WeatherCommand.parse(List.of("enable", "now")))
            .isEqualTo(new Parsed.Refused<>(Error.TOO_MANY_ARGUMENTS, "now"));
    }
}
