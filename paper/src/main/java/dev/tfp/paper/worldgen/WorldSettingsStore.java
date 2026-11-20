/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.worldgen;

import dev.tfp.core.worldgen.settings.WorldSettings;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * The TFC settings a world was created with (PAR-WORLD-22). TFC keeps its {@code Settings} in level.dat, so a world never
 * changes after creation; here the first resolve writes them to {@code <world folder>/tfp/world-settings.yml} and every later
 * resolve reads that file, so a later edit of {@code worldgen-settings.yml} does not change an existing world.
 */
public final class WorldSettingsStore {
    /** The file in the world folder. */
    public static final String STORE = "tfp/world-settings.yml";

    private WorldSettingsStore() {}

    /**
     * The settings of a world: the stored ones when the world has them, else the config's for {@code worldName}, stored now.
     * A stored file that cannot be read is an error (never silently replaced); a failed write is logged and the config's
     * settings are used for this run.
     *
     * @throws IllegalStateException when the stored file exists but cannot be read
     */
    public static WorldSettings resolve(Path worldFolder, String worldName, WorldSettingsConfig config, Logger log) {
        Path file = worldFolder.resolve(STORE);
        if (Files.isRegularFile(file)) {
            try {
                YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file.toFile());
                WorldSettings stored = WorldSettings.fromMap(WorldSettingsConfig.plainMap(yaml), WorldSettings.DEFAULT);
                if (!stored.equals(config.settingsFor(worldName))) {
                    log.info("TFC world settings of " + worldName + " come from " + file
                        + "; worldgen-settings.yml differs and applies to new worlds only");
                }
                return stored;
            } catch (IllegalArgumentException e) {
                throw new IllegalStateException("cannot read the TFC world settings " + file + ": " + e.getMessage(), e);
            }
        }
        WorldSettings created = config.settingsFor(worldName);
        try {
            write(file, created);
            log.info("TFC world settings of " + worldName + " stored in " + file);
        } catch (IOException e) {
            log.log(Level.WARNING, "could not store the TFC world settings of " + worldName + " in " + file, e);
        }
        return created;
    }

    static void write(Path file, WorldSettings settings) throws IOException {
        Files.createDirectories(file.getParent());
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.options().setHeader(List.of("TFC world settings this world was created with (PAR-WORLD-22).",
            "Read on every start; editing worldgen-settings.yml does not change this world. Edit at your own risk."));
        for (Map.Entry<String, Object> e : settings.toMap().entrySet()) {
            yaml.set(e.getKey(), e.getValue());
        }
        yaml.save(file.toFile());
    }
}
