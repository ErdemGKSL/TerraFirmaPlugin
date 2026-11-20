/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tfp.core.worldgen.settings.WorldSettings;
import dev.tfp.paper.testing.MockServerTest;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.logging.Logger;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** PAR-WORLD-22: worldgen-settings.yml, the per-world store and the game-rule switches. */
class WorldSettingsStoreTest extends MockServerTest {
    private static final Logger LOG = Logger.getLogger("test");

    @TempDir
    Path dir;

    @AfterEach
    void reset() {
        WorldSettingsConfig.install(WorldSettingsConfig.DEFAULT);
        System.clearProperty(TfcWorldRules.FORCED_PROPERTY);
        System.clearProperty(TfcWorldRules.PORTALS_PROPERTY);
    }

    private static WorldSettingsConfig yaml(String text) throws Exception {
        YamlConfiguration y = new YamlConfiguration();
        y.loadFromString(text);
        return WorldSettingsConfig.of(y);
    }

    @Test
    void theBundledFileIsTfcsPreset() throws Exception {
        try (InputStream in = WorldSettingsConfig.class.getClassLoader().getResourceAsStream(WorldSettingsConfig.FILE)) {
            WorldSettingsConfig config = WorldSettingsConfig.of(YamlConfiguration.loadConfiguration(
                new InputStreamReader(in, StandardCharsets.UTF_8)));
            assertEquals(WorldSettings.DEFAULT, config.settingsFor("world"));
            assertTrue(config.forcedGameRules());
            assertFalse(config.netherPortals());
        }
    }

    @Test
    void perWorldSectionsOverTheDefaults() throws Exception {
        WorldSettingsConfig c = yaml("defaults:\n  grass_density: 0.8\nworlds:\n  cold:\n    temperature_scale: 5000\n");
        assertEquals(0.8f, c.settingsFor("world").grassDensity());
        assertEquals(20_000, c.settingsFor("world").temperatureScale());
        assertEquals(5000, c.settingsFor("cold").temperatureScale());
        assertEquals(0.8f, c.settingsFor("cold").grassDensity());
        assertThrows(IllegalArgumentException.class, () -> yaml("worlds:\n  w:\n    no_such_field: 1\n"));
    }

    @Test
    void storedSettingsWinOverALaterConfigEdit() throws Exception {
        Path world = dir.resolve("world");
        WorldSettings first = WorldSettingsStore.resolve(world, "world", yaml("defaults:\n  flat_bedrock: true\n"), LOG);
        assertTrue(first.flatBedrock());
        assertTrue(Files.isRegularFile(world.resolve(WorldSettingsStore.STORE)));
        WorldSettings later = WorldSettingsStore.resolve(world, "world",
            yaml("defaults:\n  flat_bedrock: false\n  spawn_distance: 100\n"), LOG);
        assertEquals(first, later);
        // a new world takes the edited config
        WorldSettings other = WorldSettingsStore.resolve(dir.resolve("other"), "other",
            yaml("defaults:\n  spawn_distance: 100\n"), LOG);
        assertEquals(100, other.spawnDistance());
        assertEquals(WorldSettings.fromMap(Map.of("spawn_distance", 100), WorldSettings.DEFAULT), other);
    }

    @Test
    void aBrokenStoreIsNotReplaced() throws Exception {
        Path world = dir.resolve("broken");
        Files.createDirectories(world.resolve("tfp"));
        Files.writeString(world.resolve(WorldSettingsStore.STORE), "spawn_distance: lots\n");
        assertThrows(IllegalStateException.class, () -> WorldSettingsStore.resolve(world, "broken", WorldSettingsConfig.DEFAULT, LOG));
        assertEquals("spawn_distance: lots\n", Files.readString(world.resolve(WorldSettingsStore.STORE)));
    }

    @Test
    void gameRuleSwitchesComeFromTheConfigWithPropertyOverrides() throws Exception {
        WorldSettingsConfig.install(yaml("forced-gamerules: false\nnether-portals: true\n"));
        assertFalse(TfcWorldRules.forcedEnabled());
        assertTrue(TfcWorldRules.netherPortalsEnabled());
        System.setProperty(TfcWorldRules.FORCED_PROPERTY, "true");
        System.setProperty(TfcWorldRules.PORTALS_PROPERTY, "false");
        assertTrue(TfcWorldRules.forcedEnabled());
        assertFalse(TfcWorldRules.netherPortalsEnabled());
    }

    @Test
    void theGeneratorUsesTheResolvedSettings() {
        WorldSettings cold = WorldSettings.fromMap(Map.of("temperature_scale", 5000), WorldSettings.DEFAULT);
        TfcChunkGenerator g = new TfcChunkGenerator(java.util.OptionalLong.of(7), () -> null, LOG, info -> cold);
        assertEquals(WorldSettings.DEFAULT, g.settings());
        try {
            g.terrain(server.addSimpleWorld("gen"));
        } catch (IllegalStateException noGeneratedData) {
            // the terrain needs the datagen output; the settings are resolved before it is built
        }
        assertEquals(cold, g.settings());
    }
}
