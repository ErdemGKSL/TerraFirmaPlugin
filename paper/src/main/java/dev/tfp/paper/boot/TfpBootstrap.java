/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.boot;

import dev.tfp.paper.TfpPlugin;
import dev.tfp.paper.i18n.Messages;
import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;
import io.papermc.paper.plugin.bootstrap.PluginProviderContext;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Paper bootstrapper ({@code dependency-bootstrap} §2, ARCHITECTURE.md §12 step 1): registers our
 * datapack (biomes, tags, damage types; empty in M1) and the Brigadier {@code /tfp} commands. Runs
 * before any world or plugin instance exists, so it touches no Bukkit API.
 */
public final class TfpBootstrap implements PluginBootstrap {
    /** Jar directory of our datapack. */
    public static final String DATAPACK_RESOURCE = "datapack/tfp";
    /** Pack id under which Paper discovers it. */
    public static final String DATAPACK_ID = "tfp";
    /** M2-SEA-02: jar directory of the generated seasonal colour biomes (datagen {@code SeasonalBiomes}). */
    public static final String SEASONAL_DATAPACK_RESOURCE = "content/datapack/tfp_seasonal_biomes";
    /** Pack id of the seasonal colour biomes. */
    public static final String SEASONAL_DATAPACK_ID = "tfp_seasonal_biomes";

    private volatile TfpPlugin plugin;

    @Override
    public void bootstrap(BootstrapContext context) {
        // CraftEngine and BetterHud render pack images with AWT. A server has no display, so AWT must run headless before anything touches it.
        if (System.getProperty("java.awt.headless") == null) {
            System.setProperty("java.awt.headless", "true");
        }
        context.getLifecycleManager().registerEventHandler(LifecycleEvents.DATAPACK_DISCOVERY.newHandler(event -> {
            try {
                event.registrar().discoverPack(datapackUri(), DATAPACK_ID);
            } catch (IOException | URISyntaxException | IllegalStateException e) {
                context.getLogger().error("Cannot register the TerraFirmaPlugin datapack: {}", e.toString());
            }
            // M2-SEA-02: the 2,000 seasonal variants the packet layer swaps into the chunk biome palette.
            try {
                event.registrar().discoverPack(resourceUri(SEASONAL_DATAPACK_RESOURCE), SEASONAL_DATAPACK_ID);
            } catch (IOException | URISyntaxException | IllegalStateException e) {
                context.getLogger().warn("Seasonal biome colours are off: cannot register their datapack: {}", e.toString());
            }
        }));
        String version = context.getConfiguration().getVersion();
        Messages messages = Messages.fromJar();
        context.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS.newHandler(event ->
                event.registrar().register(TfpCommands.build(version, () -> {
                    TfpPlugin p = plugin;
                    return p != null && p.isEnabled() ? p : null;
                }, messages), "TerraFirmaPlugin commands")));
    }

    @Override
    public JavaPlugin createPlugin(PluginProviderContext context) {
        TfpPlugin p = new TfpPlugin();
        plugin = p;
        return p;
    }

    static URI datapackUri() throws URISyntaxException {
        return resourceUri(DATAPACK_RESOURCE);
    }

    static URI resourceUri(String resource) throws URISyntaxException {
        URL url = TfpBootstrap.class.getClassLoader().getResource(resource);
        if (url == null) {
            throw new IllegalStateException(resource + " is missing from the plugin jar");
        }
        return url.toURI();
    }
}
