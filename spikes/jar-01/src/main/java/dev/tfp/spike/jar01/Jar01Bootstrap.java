package dev.tfp.spike.jar01;

import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

/** Paper bootstrapper: registers nothing, logs that it ran and leaves a marker file the plugin checks. */
public final class Jar01Bootstrap implements PluginBootstrap {
    @Override
    public void bootstrap(BootstrapContext context) {
        context.getLogger().info("[jar01] bootstrapper ran");
        try {
            Path dir = context.getDataDirectory();
            Files.createDirectories(dir);
            Files.writeString(dir.resolve("bootstrap-ran.txt"), "bootstrap ran at " + Instant.now() + "\n");
        } catch (Exception e) {
            context.getLogger().error("[jar01] bootstrap marker not written: " + e);
        }
    }
}
