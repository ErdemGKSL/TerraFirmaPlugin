/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/commands/CountBlockCommand.java @ c3b4d9536
 */
package dev.tfp.core.command;

import dev.tfp.core.command.CommandArgs.Error;
import dev.tfp.core.command.CommandArgs.Parsed;
import java.util.List;

/**
 * {@code /tfp count <radius> <block>} (TFC {@code CountBlockCommand}, M6-CMD-01): counts the blocks that match a
 * selector in a square of columns around the player, over the whole build height. The world reads and the message are
 * glue; this class parses the radius and the block, and counts over the cells it is given.
 *
 * <p>The radius is TFC's {@code IntegerArgumentType.integer(1, 250)}, and the square is {@code 2 * radius + 1} wide, so
 * a radius of 1 counts a 3 by 3 area.
 */
public final class CountBlockCommand {
    /** TFC's smallest radius. */
    public static final int MIN_RADIUS = 1;
    /** TFC's largest radius. */
    public static final int MAX_RADIUS = 250;

    private CountBlockCommand() {
    }

    /**
     * One parsed line.
     *
     * @param radius the radius of the square, in blocks, 1 to 250
     * @param block  the selector of the blocks to count
     */
    public record Request(int radius, BlockSelector block) {
        public Request {
            if (radius < MIN_RADIUS || radius > MAX_RADIUS) {
                throw new IllegalArgumentException("radius out of " + MIN_RADIUS + ".." + MAX_RADIUS + ": " + radius);
            }
        }
    }

    /**
     * The cells to look at: the block at a column offset from the player's column and a height. The offsets are in
     * blocks; {@code dx} and {@code dz} run from {@code -radius} to {@code radius}.
     */
    @FunctionalInterface
    public interface Cells {
        /** Whether the block at {@code (dx, y, dz)} matches the selector. */
        boolean matches(int dx, int y, int dz);
    }

    /**
     * Parses the words after {@code count}: {@code <radius> <block>}.
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
        String blockWord = CommandArgs.word(args, 1);
        if (blockWord == null) {
            return new Parsed.Refused<>(Error.MISSING_ARGUMENT, "");
        }
        switch (BlockSelector.parse(blockWord)) {
            case Parsed.Refused<BlockSelector> refused -> {
                return new Parsed.Refused<>(refused.error(), refused.token());
            }
            case Parsed.Ok<BlockSelector> block -> {
                Parsed.Refused<Request> extra = CommandArgs.endOfLine(args, 2);
                return extra != null ? extra : new Parsed.Ok<>(new Request(radius, block.value()));
            }
        }
    }

    /**
     * Counts the matching cells in the square: every column from {@code -radius} to {@code radius} in each direction
     * (TFC's loop order, x then z then y), every height from {@code minY} inclusive to {@code maxY} exclusive.
     *
     * @param radius the radius, 1 to 250
     * @param minY   the world's minimum build height (TFC {@code getMinBuildHeight})
     * @param maxY   the world's maximum build height (TFC {@code getMaxBuildHeight}), exclusive
     * @param cells  the matching test, called once per block
     * @return the number of matching blocks; 0 when {@code minY >= maxY}
     * @throws IllegalArgumentException if the radius is outside 1 to 250
     */
    public static int count(int radius, int minY, int maxY, Cells cells) {
        if (radius < MIN_RADIUS || radius > MAX_RADIUS) {
            throw new IllegalArgumentException("radius out of " + MIN_RADIUS + ".." + MAX_RADIUS + ": " + radius);
        }
        int found = 0;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int y = minY; y < maxY; y++) {
                    if (cells.matches(dx, y, dz)) {
                        found++;
                    }
                }
            }
        }
        return found;
    }
}
