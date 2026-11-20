/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/world/ChunkNoiseFiller.java @ c3b4d9536 (calculateBlockStateAtNoise: the noodle and
 * noise caves), with world/noise/NoiseSampler.java (the five functions and their noise wiring) and the generated density
 * functions src/generated/resources/data/tfc/worldgen/density_function/{noise_caves,noodle_caves,noodle_thickness,
 * noodle_ridge_a,noodle_ridge_b}.json, kept verbatim below.
 */
package dev.tfp.core.worldgen.density;

import com.google.gson.JsonParser;
import dev.tfp.core.worldgen.Mth;
import dev.tfp.core.worldgen.random.PositionalRandom;
import dev.tfp.core.worldgen.random.XoroshiroRandomSource;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * TFC's density caves of one world: the noise caves (cave cheese with the cave layer, spaghetti 2D and 3D with their
 * roughness, the cave entrances, faded out towards y 320) and the noodle caves, each interpolated over the chunk's
 * 4 x 8 x 4 cells ({@link CellInterpolator}). {@link Chunk#density} is the cave part of TFC's
 * {@code calculateBlockStateAtNoise}: the density of a block is {@code min(terrain, density)}, and a block whose result is not
 * positive is the aquifer's (air, water or lava). There is no beardifier (no structures). Immutable and thread-safe; a
 * {@link Chunk} too.
 */
public final class NoiseCaves {
    /** The overworld noise settings: the lowest y and the number of 8-block cells. */
    public static final int MIN_Y = -64;
    public static final int CELL_COUNT_Y = 48;
    public static final int MAX_Y_EXCLUSIVE = MIN_Y + CELL_COUNT_Y * CellInterpolator.CELL_HEIGHT;

    /** {@code density_function/noise_caves.json}. */
    static final String NOISE_CAVES = """
        {"type":"minecraft:add","argument1":{"type":"minecraft:mul","argument1":{"type":"minecraft:clamp","i\
        nput":{"type":"minecraft:min","argument1":{"type":"minecraft:add","argument1":{"type":"minecraft:cla\
        mp","input":{"type":"minecraft:add","argument1":0.27,"argument2":{"type":"minecraft:noise","noise":"\
        minecraft:cave_cheese","xz_scale":1.0,"y_scale":0.6666666666666666}},"max":1.0,"min":-1.0},"argument\
        2":{"type":"minecraft:mul","argument1":4.0,"argument2":{"type":"minecraft:square","argument":{"type"\
        :"minecraft:noise","noise":"minecraft:cave_layer","xz_scale":1.0,"y_scale":8.0}}}},"argument2":{"typ\
        e":"minecraft:min","argument1":{"type":"minecraft:add","argument1":{"type":"minecraft:mul","argument\
        1":{"type":"minecraft:add","argument1":0.4,"argument2":{"type":"minecraft:mul","argument1":-1.0,"arg\
        ument2":{"type":"minecraft:abs","argument":{"type":"minecraft:noise","noise":"minecraft:spaghetti_ro\
        ughness","xz_scale":1.0,"y_scale":1.0}}}},"argument2":{"type":"minecraft:add","argument1":0.05,"argu\
        ment2":{"type":"minecraft:mul","argument1":0.05,"argument2":{"type":"minecraft:noise","noise":"minec\
        raft:spaghetti_roughness_modulator","xz_scale":1.0,"y_scale":1.0}}}},"argument2":{"type":"minecraft:\
        min","argument1":{"type":"tfc:spaghetti2d","elevation":{"type":"minecraft:add","argument1":0.0,"argu\
        ment2":{"type":"minecraft:mul","argument1":8.0,"argument2":{"type":"minecraft:noise","noise":"minecr\
        aft:spaghetti_2d_elevation","xz_scale":1.0,"y_scale":0.0}}},"noise":"minecraft:spaghetti_2d","rarity\
        ":{"type":"minecraft:noise","noise":"minecraft:spaghetti_2d_modulator","xz_scale":2.0,"y_scale":1.0}\
        ,"thickness":{"type":"minecraft:add","argument1":0.95,"argument2":{"type":"minecraft:mul","argument1\
        ":0.35000000000000003,"argument2":{"type":"minecraft:noise","noise":"minecraft:spaghetti_2d_thicknes\
        s","xz_scale":2.0,"y_scale":1.0}}}},"argument2":{"type":"tfc:spaghetti3d","noise1":"minecraft:spaghe\
        tti_3d_1","noise2":"minecraft:spaghetti_3d_2","rarity":{"type":"minecraft:noise","noise":"minecraft:\
        spaghetti_3d_rarity","xz_scale":2.0,"y_scale":1.0},"thickness":{"type":"minecraft:add","argument1":0\
        .0765,"argument2":{"type":"minecraft:mul","argument1":0.011499999999999996,"argument2":{"type":"mine\
        craft:noise","noise":"minecraft:spaghetti_3d_thickness","xz_scale":1.0,"y_scale":1.0}}}}}},"argument\
        2":{"type":"minecraft:add","argument1":{"type":"minecraft:y_clamped_gradient","from_value":0.3,"from\
        _y":-10,"to_value":0.0,"to_y":30},"argument2":{"type":"minecraft:add","argument1":0.37,"argument2":{\
        "type":"minecraft:noise","noise":"minecraft:cave_entrance","xz_scale":0.75,"y_scale":0.5}}}}},"max":\
        1.0,"min":-1.0},"argument2":{"type":"minecraft:add","argument1":1.0,"argument2":{"type":"minecraft:m\
        ul","argument1":-1.0,"argument2":{"type":"minecraft:cache_once","argument":{"type":"minecraft:y_clam\
        ped_gradient","from_value":0.0,"from_y":20,"to_value":1.0,"to_y":320}}}}},"argument2":{"type":"minec\
        raft:mul","argument1":2.5,"argument2":{"type":"minecraft:cache_once","argument":{"type":"minecraft:y\
        _clamped_gradient","from_value":0.0,"from_y":20,"to_value":1.0,"to_y":320}}}}\
        """;

    /** {@code density_function/noodle_caves.json}. */
    static final String NOODLE_CAVES = """
        {"type":"minecraft:range_choice","input":"minecraft:y","max_exclusive":320.0,"min_inclusive":-60.0,"\
        when_in_range":{"type":"minecraft:noise","noise":"minecraft:noodle","xz_scale":1.0,"y_scale":1.0},"w\
        hen_out_of_range":-1.0}\
        """;

    /** {@code density_function/noodle_thickness.json}. */
    static final String NOODLE_THICKNESS = """
        {"type":"minecraft:range_choice","input":"minecraft:y","max_exclusive":320.0,"min_inclusive":-60.0,"\
        when_in_range":{"type":"minecraft:noise","noise":"minecraft:noodle_thickness","xz_scale":1.0,"y_scal\
        e":1.0},"when_out_of_range":0.0}\
        """;

    /** {@code density_function/noodle_ridge_a.json}. */
    static final String NOODLE_RIDGE_A = """
        {"type":"minecraft:range_choice","input":"minecraft:y","max_exclusive":320.0,"min_inclusive":-60.0,"\
        when_in_range":{"type":"minecraft:noise","noise":"minecraft:noodle_ridge_a","xz_scale":2.66666666666\
        66665,"y_scale":2.6666666666666665},"when_out_of_range":1.0}\
        """;

    /** {@code density_function/noodle_ridge_b.json}. */
    static final String NOODLE_RIDGE_B = """
        {"type":"minecraft:range_choice","input":"minecraft:y","max_exclusive":320.0,"min_inclusive":-60.0,"\
        when_in_range":{"type":"minecraft:noise","noise":"minecraft:noodle_ridge_b","xz_scale":2.66666666666\
        66665,"y_scale":2.6666666666666665},"when_out_of_range":1.0}\
        """;

    private final VanillaDensity noiseCaves;
    private final VanillaDensity noodleToggle;
    private final VanillaDensity noodleThickness;
    private final VanillaDensity noodleRidgeA;
    private final VanillaDensity noodleRidgeB;

    /** @param fork the noise sampler's positional random factory: {@code new XoroshiroRandomSource(seed).forkPositional()} */
    public NoiseCaves(PositionalRandom fork) {
        final Map<String, VanillaDensityNoise> noises = new ConcurrentHashMap<>();
        final Function<String, VanillaDensityNoise> wire = id -> noises.computeIfAbsent(id, k -> VanillaDensityNoise.create(fork, k));
        this.noiseCaves = parse(NOISE_CAVES, wire);
        this.noodleToggle = parse(NOODLE_CAVES, wire);
        this.noodleThickness = parse(NOODLE_THICKNESS, wire);
        this.noodleRidgeA = parse(NOODLE_RIDGE_A, wire);
        this.noodleRidgeB = parse(NOODLE_RIDGE_B, wire);
    }

    public static NoiseCaves forSeed(long seed) {
        return new NoiseCaves(PositionalRandom.fork(new XoroshiroRandomSource(seed)));
    }

    private static VanillaDensity parse(String json, Function<String, VanillaDensityNoise> wire) {
        return VanillaDensity.parse(JsonParser.parseString(json), wire);
    }

    /** The JSON of a density file ({@code noise_caves}, {@code noodle_caves}, ...), as TFC generates it. */
    public static String json(String name) {
        return switch (name) {
            case "noise_caves" -> NOISE_CAVES;
            case "noodle_caves" -> NOODLE_CAVES;
            case "noodle_thickness" -> NOODLE_THICKNESS;
            case "noodle_ridge_a" -> NOODLE_RIDGE_A;
            case "noodle_ridge_b" -> NOODLE_RIDGE_B;
            default -> throw new IllegalArgumentException(name);
        };
    }

    /** The function of a density file, uninterpolated. */
    public VanillaDensity function(String name) {
        return switch (name) {
            case "noise_caves" -> noiseCaves;
            case "noodle_caves" -> noodleToggle;
            case "noodle_thickness" -> noodleThickness;
            case "noodle_ridge_a" -> noodleRidgeA;
            case "noodle_ridge_b" -> noodleRidgeB;
            default -> throw new IllegalArgumentException(name);
        };
    }

    /** The interpolated caves of a chunk (the five functions at its cell corners). */
    public Chunk chunk(int chunkX, int chunkZ) {
        return new Chunk(chunkX, chunkZ);
    }

    /** The density caves of one chunk. */
    public final class Chunk {
        private final int chunkX;
        private final int chunkZ;
        private final CellInterpolator caves;
        private final CellInterpolator toggle;
        private final CellInterpolator thickness;
        private final CellInterpolator ridgeA;
        private final CellInterpolator ridgeB;

        private Chunk(int chunkX, int chunkZ) {
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.caves = new CellInterpolator(noiseCaves, chunkX, chunkZ, MIN_Y, CELL_COUNT_Y);
            this.toggle = new CellInterpolator(noodleToggle, chunkX, chunkZ, MIN_Y, CELL_COUNT_Y);
            this.thickness = new CellInterpolator(noodleThickness, chunkX, chunkZ, MIN_Y, CELL_COUNT_Y);
            this.ridgeA = new CellInterpolator(noodleRidgeA, chunkX, chunkZ, MIN_Y, CELL_COUNT_Y);
            this.ridgeB = new CellInterpolator(noodleRidgeB, chunkX, chunkZ, MIN_Y, CELL_COUNT_Y);
        }

        public int chunkX() {
            return chunkX;
        }

        public int chunkZ() {
            return chunkZ;
        }

        /**
         * The cave density at a block of this chunk: {@code min(noodle ridge, noise caves)}, the noodle ridge only where the
         * interpolated noodle toggle is at least 0 ({@code calculateBlockStateAtNoise}); {@link Double#POSITIVE_INFINITY}
         * outside the noise settings' height. A block is a cave where {@code min(terrain density, this)} is not positive.
         */
        public double density(int x, int y, int z) {
            if (y < MIN_Y || y >= MAX_Y_EXCLUSIVE) {
                return Double.POSITIVE_INFINITY;
            }
            double density = Double.POSITIVE_INFINITY;
            if (toggle.sample(x, y, z) >= 0) {
                final double noodle = Mth.clampedMap(thickness.sample(x, y, z), -1, 1, 0.05, 0.1);
                final double a = Math.abs(1.5 * ridgeA.sample(x, y, z)) - noodle;
                final double b = Math.abs(1.5 * ridgeB.sample(x, y, z)) - noodle;
                density = Math.max(a, b);
            }
            return Math.min(density, caves.sample(x, y, z));
        }
    }
}
