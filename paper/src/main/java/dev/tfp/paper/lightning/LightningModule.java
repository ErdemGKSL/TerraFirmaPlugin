/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/config/ServerConfig.java @ c3b4d9536 (enableLightning, enableLightningStrippingLogs),
 *       src/main/java/net/dries007/tfc/ForgeEventHandler.java @ c3b4d9536 (onEntityJoinLevel)
 */
package dev.tfp.paper.lightning;

import dev.tfp.core.lightning.LightningRules.Config;
import dev.tfp.core.lightning.LightningRules.Draws;
import dev.tfp.paper.module.FeatureModule;
import dev.tfp.paper.module.ModuleContext;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * PAR-CLIM-08: lightning rules. TFC's two options live in {@value #FILE} in the plugin folder under TFC's names, written with
 * the defaults when missing and read at enable (a change needs a restart).
 */
public final class LightningModule implements FeatureModule {
    public static final String ID = "lightning";
    public static final String FILE = "lightning.yml";

    static final String DEFAULT_TEXT = """
            # TerraFirmaPlugin lightning rules (TFC's server options, under TFC's names). Changes need a restart.
            #
            # enableLightning: if false, lightning will not strike (the bolt is cancelled).
            # enableLightningStrippingLogs: if true, a strike has a 20% chance of stripping the bark off logs within
            #   5 blocks of it.
            enableLightning: true
            enableLightningStrippingLogs: true
            """;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void enable(ModuleContext context) throws IOException {
        Config config = load(context.plugin().getDataFolder().toPath().resolve(FILE), context);
        var scheduler = context.scheduler();
        context.listen(new LightningStrikes(config, context.integrations().customBlocks(), LightningModule::threadLocalDraws,
            (block, task) -> scheduler.region(block.getWorld(), block.getX() >> 4, block.getZ() >> 4, task)));
    }

    static Draws threadLocalDraws() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        return new Draws() {
            @Override
            public float nextFloat() {
                return r.nextFloat();
            }

            @Override
            public int nextInt(int bound) {
                return r.nextInt(bound);
            }
        };
    }

    static Config load(Path file, ModuleContext context) throws IOException {
        if (!Files.exists(file)) {
            Files.createDirectories(file.getParent());
            Files.writeString(file, DEFAULT_TEXT, StandardCharsets.UTF_8);
        }
        return parse(Files.readString(file, StandardCharsets.UTF_8), context);
    }

    /** A broken file gives TFC's defaults. */
    static Config parse(String text, ModuleContext context) {
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.loadFromString(text);
        } catch (InvalidConfigurationException e) {
            if (context != null) {
                context.logger().log(Level.SEVERE, FILE + " is not valid YAML; using TFC's lightning defaults", e);
            }
            return Config.DEFAULTS;
        }
        return new Config(yaml.getBoolean("enableLightning", true), yaml.getBoolean("enableLightningStrippingLogs", true));
    }
}
