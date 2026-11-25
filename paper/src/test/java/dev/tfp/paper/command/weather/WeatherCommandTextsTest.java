/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.command.weather;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.command.WeatherCommand;
import dev.tfp.paper.boot.TfpCommands.Reply;
import dev.tfp.paper.command.WeatherCommands;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.Test;

/** PAR-CLIM-07: the replies of {@code /tfp weather} exist as texts and name the {@code /tfp time set} words. */
class WeatherCommandTextsTest {
    private static Properties lang() throws IOException {
        Properties p = new Properties();
        try (InputStream in = WeatherCommandTextsTest.class.getResourceAsStream("/lang/en_us.properties")) {
            assertThat(in).isNotNull();
            p.load(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
        }
        return p;
    }

    @Test
    void everyReplyKeyHasAnEnglishText() throws IOException {
        List<String> flags = new ArrayList<>();
        WeatherCommands commands = new WeatherCommands((w, on) -> flags.add(on ? "on" : "off"));
        Properties lang = lang();
        for (WeatherCommand.Action action : WeatherCommand.Action.values()) {
            Reply reply = commands.run(null, action);
            assertThat(lang.getProperty(reply.key())).as(reply.key()).isNotBlank();
        }
        assertThat(lang.getProperty("command.data.disabled_by_tfp")).contains("{0}");
        assertThat(flags).containsExactly("on", "off");
    }

    @Test
    void clearAndRainPointAtTheTfpTimeCommandNotVanillaTime() {
        WeatherCommands commands = new WeatherCommands((w, on) -> { });
        assertThat(commands.run(null, WeatherCommand.Action.CLEAR).args()).containsExactly("/tfp time set clear");
        assertThat(commands.run(null, WeatherCommand.Action.RAIN).args()).containsExactly("/tfp time set rain");
    }
}
