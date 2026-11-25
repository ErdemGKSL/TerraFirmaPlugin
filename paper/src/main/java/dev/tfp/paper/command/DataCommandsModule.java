/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/commands/TFCCommands.java @ c3b4d9536
 */
package dev.tfp.paper.command;

import dev.tfp.paper.module.FeatureModule;
import dev.tfp.paper.module.ModuleContext;
import dev.tfp.paper.tags.Tags;
import dev.tfp.paper.weather.cycle.ClimateWeatherCycle;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;

/**
 * PAR-DATA-01: TFC's commands under {@code /tfp} ({@code player}, {@code count}, {@code clearworld}, {@code trim},
 * {@code weather}). The module builds the operations when it is enabled; {@link DataCommandTree}'s literals, which
 * {@code TfpCommands} adds to its tree at bootstrap, read them through {@link #ops()} and are refused while the module
 * is off. Needs the tags module (block tags) and the weather cycle module before it in the service file.
 */
public final class DataCommandsModule implements FeatureModule {
    public static final String ID = "datacommands";

    private static volatile DataCommandTree.Ops ops;

    @Override
    public String id() {
        return ID;
    }

    /** The live operations, or null before the module is enabled. */
    public static DataCommandTree.Ops ops() {
        return ops;
    }

    @Override
    public void enable(ModuleContext context) {
        Tags tags = context.services().require(Tags.class);
        var weather = context.services().get(ClimateWeatherCycle.class);
        WorldCommands world = new WorldCommands(context.scheduler(), context.integrations().customBlocks(),
                tags::is, (type, tag) -> tags.is(type, tag, dev.tfp.core.tag.TagType.BLOCK), System::nanoTime);
        PlayerCommands player = new PlayerCommands(context.scheduler(), context.playerData(),
                () -> context.clock().get().playerTicks(), id -> context.hudCache().snapshot(id).ambientTemperature());
        TrimCommands trim = new TrimCommands(context.scheduler(), context.integrations().customItems());
        WeatherCommands weatherCommands = new WeatherCommands((w, on) -> weather.ifPresent(c -> c.setEnabled(w, on)));
        ops = new DataCommandTree.Ops(player, world, trim, weatherCommands, context.messages());
    }

    @Override
    public void disable() {
        ops = null;
    }
}
