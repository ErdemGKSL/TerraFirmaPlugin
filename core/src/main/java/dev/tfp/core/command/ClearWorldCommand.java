/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/commands/ClearWorldCommand.java @ c3b4d9536
 */
package dev.tfp.core.command;

import dev.tfp.core.command.CommandArgs.Error;
import dev.tfp.core.command.CommandArgs.Parsed;
import java.util.List;

/**
 * {@code /tfp clearworld <radius> [preset]} (TFC {@code ClearWorldCommand}, M6-CMD-01): removes the matching blocks in a
 * square of columns, from the bottom of the world up to the surface of each column. The presets are TFC's:
 * {@code ALL} (default), {@code RAW_ROCK}, {@code EARTH} and {@code NOT_ORE}.
 *
 * <p>The core decides which cells go. The glue classifies each block into a {@link Kind} (what TFC's block sets say),
 * reads the surface height of each column, and writes air at each cell the sweep reports, as TFC does with flag 2 | 16.
 * Air is never removed, as in TFC.
 */
public final class ClearWorldCommand {
    /** TFC's smallest radius. */
    public static final int MIN_RADIUS = 1;
    /** TFC's largest radius. */
    public static final int MAX_RADIUS = 250;

    private ClearWorldCommand() {
    }

    /**
     * The block sets of the presets. The glue maps each block to its kind: a raw, hardened or gravel rock block, dirt,
     * grass or sand from TFC's sets, a vein ore block of any vein configuration, or anything else.
     */
    public enum Kind {
        AIR, RAW_ROCK, HARDENED_ROCK, GRAVEL_ROCK, DIRT, GRASS, SAND, ORE, OTHER
    }

    /** The removal presets, TFC's names and order. */
    public enum Preset {
        /** Every block that is not air. */
        ALL,
        /** Raw rock blocks only. */
        RAW_ROCK,
        /** Raw, hardened and gravel rock, dirt, grass and sand. */
        EARTH,
        /** Everything except vein ore blocks. */
        NOT_ORE;

        /**
         * Whether a block of this kind is removed. Air is never removed; the sweep checks that first.
         *
         * @return whether the preset takes the kind
         */
        public boolean removes(Kind kind) {
            return switch (this) {
                case ALL -> kind != Kind.AIR;
                case RAW_ROCK -> kind == Kind.RAW_ROCK;
                case EARTH -> switch (kind) {
                    case RAW_ROCK, HARDENED_ROCK, GRAVEL_ROCK, DIRT, GRASS, SAND -> true;
                    default -> false;
                };
                case NOT_ORE -> kind != Kind.ORE && kind != Kind.AIR;
            };
        }
    }

    /**
     * One parsed line.
     *
     * @param radius the radius of the square, in blocks, 1 to 250
     * @param preset the blocks to remove
     */
    public record Request(int radius, Preset preset) {
        public Request {
            if (radius < MIN_RADIUS || radius > MAX_RADIUS) {
                throw new IllegalArgumentException("radius out of " + MIN_RADIUS + ".." + MAX_RADIUS + ": " + radius);
            }
            if (preset == null) {
                throw new IllegalArgumentException("preset is required");
            }
        }
    }

    /** The surface height of a column (TFC {@code getHeight(WORLD_SURFACE)}): the first height above its blocks. */
    @FunctionalInterface
    public interface Surface {
        /** The surface height at the column offset {@code (dx, dz)}. */
        int heightAt(int dx, int dz);
    }

    /** The kind of the block at an offset and a height. */
    @FunctionalInterface
    public interface Kinds {
        /** The kind of the block at {@code (dx, y, dz)}. */
        Kind kindAt(int dx, int y, int dz);
    }

    /** Receives each block the sweep removes, once, in TFC's order. */
    @FunctionalInterface
    public interface Sink {
        /** Writes air at {@code (dx, y, dz)}. */
        void remove(int dx, int y, int dz);
    }

    /**
     * Parses the words after {@code clearworld}: a radius, then an optional preset, which defaults to {@code ALL}.
     *
     * @param args the words after the command name, never null
     */
    public static Parsed<Request> parse(List<String> args) {
        String radiusWord = CommandArgs.word(args, 0);
        if (radiusWord == null) {
            return new Parsed.Refused<>(Error.MISSING_ARGUMENT, "");
        }
        final int radius;
        switch (CommandArgs.integer(radiusWord, MIN_RADIUS, MAX_RADIUS)) {
            case Parsed.Refused<Integer> refused -> {
                return new Parsed.Refused<>(refused.error(), refused.token());
            }
            case Parsed.Ok<Integer> ok -> radius = ok.value();
        }
        String presetWord = CommandArgs.word(args, 1);
        final Preset preset;
        if (presetWord == null) {
            preset = Preset.ALL;
        } else {
            switch (CommandArgs.enumValue(Preset.class, presetWord)) {
                case Parsed.Refused<Preset> refused -> {
                    return new Parsed.Refused<>(refused.error(), refused.token());
                }
                case Parsed.Ok<Preset> ok -> preset = ok.value();
            }
        }
        Parsed.Refused<Request> extra = CommandArgs.endOfLine(args, 2);
        return extra != null ? extra : new Parsed.Ok<>(new Request(radius, preset));
    }

    /**
     * The sweep of TFC's {@code clearWorld}: for each column in TFC's order (x, then z), the heights from {@code minY}
     * up to the column's surface, exclusive. A block is removed when it is not air and the preset takes its kind.
     *
     * @param radius  the radius, 1 to 250
     * @param preset  the blocks to remove
     * @param minY    the world's minimum build height
     * @param surface the surface height of each column, read once per column
     * @param kinds   the kind of each block, read before the sweep writes it
     * @param sink    the receiver of each removal
     * @return the number of blocks removed
     * @throws IllegalArgumentException if the radius is outside 1 to 250
     */
    public static int sweep(int radius, Preset preset, int minY, Surface surface, Kinds kinds, Sink sink) {
        if (radius < MIN_RADIUS || radius > MAX_RADIUS) {
            throw new IllegalArgumentException("radius out of " + MIN_RADIUS + ".." + MAX_RADIUS + ": " + radius);
        }
        int removed = 0;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int height = surface.heightAt(dx, dz);
                for (int y = minY; y < height; y++) {
                    Kind kind = kinds.kindAt(dx, y, dz);
                    if (kind != Kind.AIR && preset.removes(kind)) {
                        sink.remove(dx, y, dz);
                        removed++;
                    }
                }
            }
        }
        return removed;
    }
}
