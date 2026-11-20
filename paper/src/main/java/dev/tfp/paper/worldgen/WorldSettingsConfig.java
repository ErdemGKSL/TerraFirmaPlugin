/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.worldgen;

import dev.tfp.core.worldgen.settings.WorldSettings;
import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * The server's {@code worldgen-settings.yml} (PAR-WORLD-22, the NP-CLIENT-05 substitute for TFC's Create TFC World screen
 * and its server config switches): {@code forced-gamerules} and {@code nether-portals} (TFC's
 * {@code enableForcedTFCGameRules}, {@code enableNetherPortals}), {@code defaults} (TFC's {@code Settings} fields by codec
 * name, over TFC's overworld preset) and {@code worlds.<name>} (per-world fields over the defaults). The values only seed a
 * new world: {@link WorldSettingsStore} keeps what a world was created with. Immutable; the current one is a volatile field.
 *
 * @param forcedGameRules TFC's {@code enableForcedTFCGameRules}
 * @param netherPortals   TFC's {@code enableNetherPortals}
 * @param defaults        the fields every world starts from, over {@link WorldSettings#DEFAULT}
 * @param worlds          per world name, the fields over the defaults
 */
public record WorldSettingsConfig(boolean forcedGameRules, boolean netherPortals, Map<String, Object> defaults,
                                  Map<String, Map<String, Object>> worlds) {
    /** The file name in the plugin's data folder (and the bundled resource). */
    public static final String FILE = "worldgen-settings.yml";

    /** TFC's defaults: forced rules on, portals off, the preset for every world. */
    public static final WorldSettingsConfig DEFAULT = new WorldSettingsConfig(true, false, Map.of(), Map.of());

    private static volatile WorldSettingsConfig current = DEFAULT;

    public WorldSettingsConfig {
        defaults = Map.copyOf(defaults);
        worlds = Map.copyOf(worlds);
        // fail early: every section must read
        WorldSettings base = WorldSettings.fromMap(defaults, WorldSettings.DEFAULT);
        for (Map.Entry<String, Map<String, Object>> e : worlds.entrySet()) {
            try {
                WorldSettings.fromMap(e.getValue(), base);
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException("worlds." + e.getKey() + ": " + ex.getMessage(), ex);
            }
        }
    }

    /** The config in use (set by the {@code world_settings} module at enable). */
    public static WorldSettingsConfig current() {
        return current;
    }

    public static void install(WorldSettingsConfig config) {
        current = config;
    }

    /** The settings a new world named {@code world} is created with. */
    public WorldSettings settingsFor(String world) {
        WorldSettings base = WorldSettings.fromMap(defaults, WorldSettings.DEFAULT);
        Map<String, Object> own = world == null ? null : worlds.get(world);
        return own == null ? base : WorldSettings.fromMap(own, base);
    }

    /**
     * Reads the file.
     *
     * @throws IllegalArgumentException on an unknown or badly typed field
     */
    public static WorldSettingsConfig load(File file) {
        return of(YamlConfiguration.loadConfiguration(file));
    }

    /** Reads a parsed file. */
    public static WorldSettingsConfig of(ConfigurationSection yaml) {
        Map<String, Object> defaults = yaml.isConfigurationSection("defaults")
            ? plainMap(yaml.getConfigurationSection("defaults")) : Map.of();
        Map<String, Map<String, Object>> worlds = new LinkedHashMap<>();
        ConfigurationSection ws = yaml.getConfigurationSection("worlds");
        if (ws != null) {
            for (String name : ws.getKeys(false)) {
                ConfigurationSection w = ws.getConfigurationSection(name);
                worlds.put(name, w == null ? Map.of() : plainMap(w));
            }
        }
        return new WorldSettingsConfig(yaml.getBoolean("forced-gamerules", true), yaml.getBoolean("nether-portals", false),
            defaults, worlds);
    }

    /** A section as plain maps and lists, recursively, in file order. */
    static Map<String, Object> plainMap(ConfigurationSection section) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            out.put(key, plain(section.get(key)));
        }
        return out;
    }

    private static Object plain(Object value) {
        if (value instanceof ConfigurationSection s) {
            return plainMap(s);
        }
        if (value instanceof Map<?, ?> m) {
            Map<String, Object> out = new LinkedHashMap<>();
            m.forEach((k, v) -> out.put(String.valueOf(k), plain(v)));
            return out;
        }
        if (value instanceof List<?> l) {
            List<Object> out = new ArrayList<>();
            l.forEach(v -> out.add(plain(v)));
            return out;
        }
        return value;
    }
}
