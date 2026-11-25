/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.command;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.command.WeatherCommand;
import dev.tfp.paper.boot.TfpCommands.Reply;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** PAR-DATA-01: TFC's weather command decisions on the glue. */
class WeatherCommandsTest {
    private final List<String> calls = new ArrayList<>();
    private final WeatherCommands commands = new WeatherCommands((w, on) -> calls.add(on ? "on" : "off"));

    @Test
    void enableAndDisableSetTheFlagAndAnswerWithTfcsTexts() {
        assertThat(commands.run(null, WeatherCommand.Action.ENABLE)).isEqualTo(Reply.ok("command.data.weather.enabled"));
        assertThat(commands.run(null, WeatherCommand.Action.DISABLE)).isEqualTo(Reply.ok("command.data.weather.disabled"));
        assertThat(calls).containsExactly("on", "off");
    }

    @Test
    void clearAndRainChangeNothingAndPointAtTheTimeCommand() {
        Reply clear = commands.run(null, WeatherCommand.Action.CLEAR);
        Reply rain = commands.run(null, WeatherCommand.Action.RAIN);
        assertThat(clear.ok()).isFalse();
        assertThat(clear.args()).containsExactly("/tfp time set clear");
        assertThat(rain.args()).containsExactly("/tfp time set rain");
        assertThat(calls).isEmpty();
    }
}
