/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/commands/WeatherCommand.java @ c3b4d9536
 */
package dev.tfp.paper.command;

import dev.tfp.core.command.WeatherCommand;
import dev.tfp.paper.boot.TfpCommands.Reply;
import java.util.function.BiConsumer;
import org.bukkit.World;

/**
 * {@code /tfp weather enable|disable|clear|rain} (TFC {@code WeatherCommand}): the decision is
 * {@link WeatherCommand#decide}; {@code enable} and {@code disable} set the world's weather flag (the climate weather
 * cycle's {@code setEnabled}), and {@code clear} and {@code rain} point at {@code /tfp time set clear|rain}, as TFC points
 * at {@code /time}. Pure glue: the flag setter is passed in.
 */
public final class WeatherCommands {
    private final BiConsumer<World, Boolean> setEnabled;

    /** @param setEnabled the world's weather flag setter, {@code ClimateWeatherCycle.setEnabled} */
    public WeatherCommands(BiConsumer<World, Boolean> setEnabled) {
        this.setEnabled = setEnabled;
    }

    /** Runs {@code action} in {@code world}; any thread (the setter is thread-safe). */
    public Reply run(World world, WeatherCommand.Action action) {
        return switch (WeatherCommand.decide(action)) {
            case WeatherCommand.Decision.SetEnabled set -> {
                setEnabled.accept(world, set.enabled());
                yield Reply.ok(set.enabled() ? "command.data.weather.enabled" : "command.data.weather.disabled");
            }
            case WeatherCommand.Decision.Refused refused ->
                    Reply.error("command.data.disabled_by_tfp", hint(refused.hint()));
        };
    }

    /** TFC's hint with our command prefix: {@code /time set clear} becomes {@code /tfp time set clear}. */
    static String hint(WeatherCommand.Hint hint) {
        return "/tfp " + hint.command().substring(1);
    }
}
