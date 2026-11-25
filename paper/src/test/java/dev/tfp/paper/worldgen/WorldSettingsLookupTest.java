/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tfp.core.climate.OverworldClimate;
import dev.tfp.core.worldgen.region.RegionSettings;
import dev.tfp.core.worldgen.settings.WorldSettings;
import dev.tfp.paper.climate.ChunkClimateSource;
import dev.tfp.paper.hud.HudHemisphere;
import dev.tfp.paper.testing.MockServerTest;
import java.util.Map;
import java.util.OptionalLong;
import java.util.logging.Logger;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.generator.ChunkGenerator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.world.WorldMock;

/** PAR-WORLD-51: the climate readers use a world's own TFC settings, not TFC's preset. */
class WorldSettingsLookupTest extends MockServerTest {
    private static final Logger LOG = Logger.getLogger("WorldSettingsLookupTest");
    private static final long SEED = 918_273_645L;

    @AfterEach
    void forget() {
        WorldSettingsLookup.clear();
    }

    /** A world of our generator whose settings resolved to {@code settings} (as with its terrain built). */
    private World tfcWorld(String name, WorldSettings settings) {
        TfcChunkGenerator g = new TfcChunkGenerator(OptionalLong.of(SEED), () -> null, LOG, info -> settings);
        WorldMock world = new WorldMock(new WorldCreator(name).seed(SEED)) {
            @Override
            public ChunkGenerator getGenerator() {
                return g;
            }
        };
        server.addWorld(world);
        try {
            g.terrain(world);
        } catch (IllegalStateException | IllegalArgumentException noGeneratedData) {
            // the terrain needs the datagen output; the settings are resolved before it is built
        }
        assertEquals(settings, g.settings());
        return world;
    }

    @Test
    void aWorldWithTemperatureScale5000GivesThatClimate() {
        WorldSettings cold = WorldSettings.fromMap(Map.of("temperature_scale", 5000, "rainfall_scale", 7000),
            WorldSettings.DEFAULT);
        World world = tfcWorld("cold", cold);
        long seed = world.getSeed();

        assertEquals(cold, WorldSettingsLookup.of(world));
        assertEquals(cold, WorldSettingsLookup.forSeed(seed));
        assertEquals(5000f, WorldSettingsLookup.climate(world).hemisphereScale());
        // the worker-side readers (HUD, snow, rain, tabs) only captured the seed
        assertEquals(5000f, WorldSettingsLookup.climate(seed).hemisphereScale());

        // HUD hemisphere: z = 6000 is south of the equator with a 5000 scale, north with TFC's 20000
        boolean presetNorth = dev.tfp.core.climate.Solar.inNorthernHemisphere(6000, OverworldClimate.DEFAULT_TEMPERATURE_SCALE);
        boolean coldNorth = dev.tfp.core.climate.Solar.inNorthernHemisphere(6000, 5000f);
        assertNotEquals(presetNorth, coldNorth, "sample z must tell the scales apart");
        assertEquals(coldNorth, HudHemisphere.north(true, seed, 6000));

        // the chunk climate (snow, rain, plant effects) is the world's region settings, not the preset's
        assertEquals(ChunkClimateSource.climateAt(seed, cold.regionSettings(), 6000, 6000),
            ChunkClimateSource.climateAt(seed, 6000, 6000));
        assertNotEquals(ChunkClimateSource.climateAt(seed, RegionSettings.OVERWORLD, 6000, 6000),
            ChunkClimateSource.climateAt(seed, 6000, 6000));
    }

    @Test
    void otherWorldsAndUnknownSeedsUseThePreset() {
        World plain = server.addSimpleWorld("plain");
        assertEquals(WorldSettings.DEFAULT, WorldSettingsLookup.of(plain));
        assertEquals(WorldSettings.DEFAULT, WorldSettingsLookup.forSeed(plain.getSeed()));
        assertEquals(WorldSettings.DEFAULT, WorldSettingsLookup.forSeed(12345L));
        assertEquals((float) OverworldClimate.DEFAULT_TEMPERATURE_SCALE,
            WorldSettingsLookup.climate(12345L).hemisphereScale());
        assertTrue(HudHemisphere.north(false, 12345L, 1_000_000));
        assertEquals(WorldSettings.DEFAULT, WorldSettingsLookup.of(null));
    }
}
