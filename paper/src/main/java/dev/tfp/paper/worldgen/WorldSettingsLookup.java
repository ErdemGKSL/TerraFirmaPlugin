/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.worldgen;

import dev.tfp.core.climate.OverworldClimate;
import dev.tfp.core.worldgen.rock.RockLayerSampler;
import dev.tfp.core.worldgen.rock.RockSettings;
import dev.tfp.core.worldgen.settings.WorldSettings;
import dev.tfp.core.worldwater.WaterTerrain;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.generator.ChunkGenerator;

/**
 * PAR-WORLD-51: the TFC world settings (PAR-WORLD-22) of a world, for every climate reader outside the generator, so the HUD,
 * snow, rain, seasonal colours and plant effects use the world's {@code temperature_scale}, rainfall and continentalness
 * instead of TFC's preset.
 * <p>
 * A world of our generator gives {@link TfcChunkGenerator#settings()}; any other world gives {@link WorldSettings#DEFAULT}.
 * Readers that only captured a world seed (worker-side computations) use {@link #forSeed}, which finds the loaded world of our
 * generator with that seed (memoised per seed; the generator's settings are read each time, so they are current once its
 * terrain is built). Any thread.
 */
public final class WorldSettingsLookup {
    private static final Map<Long, TfcChunkGenerator> BY_SEED = new ConcurrentHashMap<>();

    private WorldSettingsLookup() {
    }

    /** The TFC settings of a world: its generator's when it is ours, else TFC's preset. */
    public static WorldSettings of(World world) {
        if (world == null) {
            return WorldSettings.DEFAULT;
        }
        if (generatorOf(world) instanceof TfcChunkGenerator tfc) {
            BY_SEED.putIfAbsent(world.getSeed(), tfc);
            return tfc.settings();
        }
        return WorldSettings.DEFAULT;
    }

    /**
     * The TFC settings of the loaded world of our generator with {@code worldSeed}, else TFC's preset (no such world, or no
     * server, as in pure tests).
     */
    public static WorldSettings forSeed(long worldSeed) {
        final TfcChunkGenerator known = BY_SEED.get(worldSeed);
        if (known != null) {
            return known.settings();
        }
        final List<World> worlds;
        try {
            worlds = Bukkit.getServer() == null ? List.of() : Bukkit.getWorlds();
        } catch (RuntimeException noServer) {
            return WorldSettings.DEFAULT;
        }
        for (World world : worlds) {
            if (world.getSeed() == worldSeed && generatorOf(world) instanceof TfcChunkGenerator) {
                return of(world);
            }
        }
        return WorldSettings.DEFAULT;
    }

    /** The world's generator, or null when it has none or cannot tell (a mock world). */
    private static ChunkGenerator generatorOf(World world) {
        try {
            return world.getGenerator();
        } catch (RuntimeException unsupported) {
            return null;
        }
    }

    /** The overworld climate model of a world with its own {@code temperature_scale}. */
    public static OverworldClimate climate(World world) {
        return OverworldClimate.forWorldSeed(world.getSeed(), of(world).temperatureScale());
    }

    /** The overworld climate model of the world with {@code worldSeed} (see {@link #forSeed}). */
    public static OverworldClimate climate(long worldSeed) {
        return OverworldClimate.forWorldSeed(worldSeed, forSeed(worldSeed).temperatureScale());
    }

    /**
     * The water terrain of a world (PAR-FLUID-03) built from its own settings: its region settings and rock layers, with the
     * karst surface rock task as {@link TfcTerrain} runs it.
     */
    public static WaterTerrain waterTerrain(World world) {
        final WorldSettings s = of(world);
        return new WaterTerrain(world.getSeed(), s.regionSettings(), RockLayerSampler.decode(s.rockLayerSettings()),
            Karst.predicate());
    }

    /** Test seam: forget the remembered generators. */
    static void clear() {
        BY_SEED.clear();
    }

    /** The karst flag of the rocks, from the rock settings in the jar (read once). */
    private static final class Karst {
        private static volatile Map<String, RockSettings> rocks;

        static Predicate<String> predicate() {
            Map<String, RockSettings> r = rocks;
            if (r == null) {
                r = RockPalette.rockSettingsFromJar(WorldSettingsLookup.class.getClassLoader());
                rocks = r;
            }
            final Map<String, RockSettings> known = r;
            return rock -> {
                final RockSettings rs = known.get(rock);
                return rs != null && rs.isKarst();
            };
        }
    }
}
