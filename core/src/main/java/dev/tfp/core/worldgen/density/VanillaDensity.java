/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/world/density/Spaghetti2D.java @ c3b4d9536 and Spaghetti3D.java of the same package
 * (compute, getRarity). The other nodes mirror Minecraft 1.21 DensityFunctions (constant, add, mul, min, max, clamp, abs,
 * square, cube, cache_once, y_clamped_gradient, range_choice, noise) and the vanilla density function minecraft:y.
 */
package dev.tfp.core.worldgen.density;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.tfp.core.worldgen.Mth;
import java.util.function.Function;

/**
 * A density function of a block position, as Minecraft's {@code DensityFunction.compute} at a single point. The nodes are the
 * subset TFC's cave density files use; {@link #parse} reads them from the JSON of a {@code worldgen/density_function} file.
 * Immutable and thread-safe.
 */
@FunctionalInterface
public interface VanillaDensity {
    double compute(int x, int y, int z);

    /**
     * Parses a density function.
     *
     * @param json   the JSON of the function (an object, a number or a reference)
     * @param noises the world's noise of a noise id ({@code minecraft:cave_cheese})
     * @throws IllegalArgumentException for a node type or reference that is not ported
     */
    static VanillaDensity parse(JsonElement json, Function<String, VanillaDensityNoise> noises) {
        if (json.isJsonPrimitive()) {
            if (json.getAsJsonPrimitive().isString()) {
                if (json.getAsString().equals("minecraft:y")) {
                    // data/minecraft/worldgen/density_function/y.json
                    return yClampedGradient(-4064, 4062, -4064.0, 4062.0);
                }
                throw new IllegalArgumentException("unknown density function reference " + json.getAsString());
            }
            final double value = json.getAsDouble();
            return (x, y, z) -> value;
        }
        final JsonObject o = json.getAsJsonObject();
        final String type = o.get("type").getAsString();
        return switch (type) {
            case "minecraft:add" -> {
                final VanillaDensity a = parse(o.get("argument1"), noises);
                final VanillaDensity b = parse(o.get("argument2"), noises);
                yield (x, y, z) -> a.compute(x, y, z) + b.compute(x, y, z);
            }
            case "minecraft:mul" -> {
                final VanillaDensity a = parse(o.get("argument1"), noises);
                final VanillaDensity b = parse(o.get("argument2"), noises);
                yield (x, y, z) -> {
                    final double d = a.compute(x, y, z);
                    return d == 0.0 ? 0.0 : d * b.compute(x, y, z);
                };
            }
            case "minecraft:min" -> {
                final VanillaDensity a = parse(o.get("argument1"), noises);
                final VanillaDensity b = parse(o.get("argument2"), noises);
                yield (x, y, z) -> Math.min(a.compute(x, y, z), b.compute(x, y, z));
            }
            case "minecraft:max" -> {
                final VanillaDensity a = parse(o.get("argument1"), noises);
                final VanillaDensity b = parse(o.get("argument2"), noises);
                yield (x, y, z) -> Math.max(a.compute(x, y, z), b.compute(x, y, z));
            }
            case "minecraft:clamp" -> {
                final VanillaDensity input = parse(o.get("input"), noises);
                final double min = o.get("min").getAsDouble();
                final double max = o.get("max").getAsDouble();
                yield (x, y, z) -> clamp(input.compute(x, y, z), min, max);
            }
            case "minecraft:abs" -> {
                final VanillaDensity a = parse(o.get("argument"), noises);
                yield (x, y, z) -> Math.abs(a.compute(x, y, z));
            }
            case "minecraft:square" -> {
                final VanillaDensity a = parse(o.get("argument"), noises);
                yield (x, y, z) -> {
                    final double d = a.compute(x, y, z);
                    return d * d;
                };
            }
            case "minecraft:cube" -> {
                final VanillaDensity a = parse(o.get("argument"), noises);
                yield (x, y, z) -> {
                    final double d = a.compute(x, y, z);
                    return d * d * d;
                };
            }
            case "minecraft:cache_once" -> parse(o.get("argument"), noises);
            case "minecraft:y_clamped_gradient" -> yClampedGradient(o.get("from_y").getAsInt(), o.get("to_y").getAsInt(),
                o.get("from_value").getAsDouble(), o.get("to_value").getAsDouble());
            case "minecraft:range_choice" -> {
                final VanillaDensity input = parse(o.get("input"), noises);
                final double min = o.get("min_inclusive").getAsDouble();
                final double max = o.get("max_exclusive").getAsDouble();
                final VanillaDensity in = parse(o.get("when_in_range"), noises);
                final VanillaDensity out = parse(o.get("when_out_of_range"), noises);
                yield (x, y, z) -> {
                    final double d = input.compute(x, y, z);
                    return d >= min && d < max ? in.compute(x, y, z) : out.compute(x, y, z);
                };
            }
            case "minecraft:noise" -> {
                final VanillaDensityNoise noise = noises.apply(o.get("noise").getAsString());
                final double xzScale = o.get("xz_scale").getAsDouble();
                final double yScale = o.get("y_scale").getAsDouble();
                yield (x, y, z) -> noise.getValue(x * xzScale, y * yScale, z * xzScale);
            }
            case "tfc:spaghetti2d" -> spaghetti2d(parse(o.get("rarity"), noises), parse(o.get("thickness"), noises),
                parse(o.get("elevation"), noises), noises.apply(o.get("noise").getAsString()));
            case "tfc:spaghetti3d" -> spaghetti3d(parse(o.get("rarity"), noises), parse(o.get("thickness"), noises),
                noises.apply(o.get("noise1").getAsString()), noises.apply(o.get("noise2").getAsString()));
            default -> throw new IllegalArgumentException("density function type not ported: " + type);
        };
    }

    /** {@code Mth.clamp(double, double, double)}. */
    private static double clamp(double value, double min, double max) {
        return value < min ? min : Math.min(value, max);
    }

    /** {@code DensityFunctions.YClampedGradient}: {@code Mth.clampedMap(y, fromY, toY, fromValue, toValue)}. */
    static VanillaDensity yClampedGradient(int fromY, int toY, double fromValue, double toValue) {
        return (x, y, z) -> Mth.clampedMap((double) y, fromY, toY, fromValue, toValue);
    }

    /** TFC's {@code Spaghetti2D.compute}. */
    private static VanillaDensity spaghetti2d(VanillaDensity rarityModulator, VanillaDensity thicknessModulator,
                                              VanillaDensity elevationModulator, VanillaDensityNoise noise) {
        return (x, y, z) -> {
            final double modulator = rarityModulator.compute(x, y, z);
            final double rarity;
            if (modulator < -0.75) {
                rarity = 0.5;
            } else if (modulator < -0.5) {
                rarity = 0.75;
            } else if (modulator < 0.5) {
                rarity = 1;
            } else {
                rarity = modulator < 0.75 ? 2 : 3;
            }
            final double rarityNoise = noise.getValue(x / rarity, y / rarity, z / rarity);
            final double thickness = thicknessModulator.compute(x, y, z);
            final double elevation = elevationModulator.compute(x, y, z);
            final double left = Math.abs(elevation - y / 8.0) - thickness;
            final double right = Math.abs(rarity * rarityNoise) - 0.083D * thickness;
            return clamp(Math.max(left * left * left, right), -1.0, 1.0);
        };
    }

    /** TFC's {@code Spaghetti3D.compute}. */
    private static VanillaDensity spaghetti3d(VanillaDensity rarityModulator, VanillaDensity thicknessModulator,
                                              VanillaDensityNoise noise1, VanillaDensityNoise noise2) {
        return (x, y, z) -> {
            final double modulator = rarityModulator.compute(x, y, z);
            final double rarity;
            if (modulator < -0.5) {
                rarity = 0.75;
            } else if (modulator < 0) {
                rarity = 1;
            } else {
                rarity = modulator < 0.5 ? 1.5 : 2;
            }
            final double thickness = thicknessModulator.compute(x, y, z);
            final double value1 = noise1.getValue(x / rarity, y / rarity, z / rarity);
            final double value2 = noise2.getValue(x / rarity, y / rarity, z / rarity);
            return clamp(Math.max(Math.abs(rarity * value1), Math.abs(rarity * value2)) - thickness, -1.0, 1.0);
        };
    }
}
