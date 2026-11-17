/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/FireboxBlockEntity.java @ c3b4d9536 (floodfill,
 *       isValidInterior, isValidExterior)
 */
package dev.tfp.core.firebox;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;

/**
 * PAR-HEAT-12: TFC's kiln flood fill above a firebox. From the block above the firebox it floods every interior block
 * (one that does not occlude or is {@code #tfc:heat_passable}) walled in by exterior blocks ({@code #tfc:heat_insulation}
 * with a sturdy face toward the inside, or a door). Anything else at the edge, too many blocks, or interior next to or
 * below the firebox empties the result. The size of the result is TFC's {@code heatingCount}.
 *
 * <p>Kept exactly as TFC, including its quirks: an interior block beside the firebox clears the list but the fill goes on
 * (TFC's {@code break} only leaves the inner loop), and a blocking edge clears it and skips the rest of that block's
 * neighbours. Pure: the world is read through {@link World}, on the caller's thread.
 */
public final class FireboxKiln {
    /** Minecraft {@code Direction.values()} order (TFC {@code Helpers.DIRECTIONS}): down, up, north, south, west, east. */
    public enum Dir {
        DOWN(0, -1, 0), UP(0, 1, 0), NORTH(0, 0, -1), SOUTH(0, 0, 1), WEST(-1, 0, 0), EAST(1, 0, 0);

        public final int dx;
        public final int dy;
        public final int dz;

        Dir(int dx, int dy, int dz) {
            this.dx = dx;
            this.dy = dy;
            this.dz = dz;
        }

        public Dir opposite() {
            return switch (this) {
                case DOWN -> UP;
                case UP -> DOWN;
                case NORTH -> SOUTH;
                case SOUTH -> NORTH;
                case WEST -> EAST;
                case EAST -> WEST;
            };
        }
    }

    /** A block position. */
    public record Pos(int x, int y, int z) {
        public Pos relative(Dir d) {
            return new Pos(x + d.dx, y + d.dy, z + d.dz);
        }
    }

    /** The blocks the fill reads. */
    public interface World {
        /**
         * TFC {@code isValidExterior}: the block at {@code pos}, reached by moving {@code moved}, is
         * {@code #tfc:heat_insulation} and sturdy on its face {@code moved.opposite()}, or a door.
         */
        boolean exterior(Pos pos, Dir moved);

        /** TFC {@code isValidInterior}: the block at {@code pos} does not occlude or is {@code #tfc:heat_passable}. */
        boolean interior(Pos pos);
    }

    private FireboxKiln() {
    }

    /**
     * TFC {@code floodfill(level, pos, firebox)}: the heated positions above the firebox at {@code firebox}, empty when the
     * firebox is cold.
     */
    public static List<Pos> floodfill(World world, Pos firebox, float temperature) {
        Objects.requireNonNull(world, "world");
        if (temperature <= 0f) {
            return List.of();
        }
        record Path(Pos pos, int cost) {
        }
        List<Pos> positions = new ArrayList<>(16);
        Set<Pos> seen = new HashSet<>();
        Queue<Path> queue = new ArrayDeque<>();
        Pos above = firebox.relative(Dir.UP);
        positions.add(above);
        seen.add(above);
        queue.add(new Path(above, 0));
        int capacity = FireboxSim.totalHeatableBlocks(temperature);
        while (!queue.isEmpty()) {
            Path current = queue.remove();
            capacity--;
            if (capacity <= 0) {
                positions.clear();
                seen.clear();
                break;
            }
            for (Dir direction : Dir.values()) {
                Pos cursor = current.pos().relative(direction);
                if (!seen.contains(cursor) && !cursor.equals(firebox)) {
                    if (!world.exterior(cursor, direction)) {
                        if (world.interior(cursor)) {
                            // next to or below the firebox invalidates the kiln (TFC clears, then still adds)
                            for (Dir d1 : Dir.values()) {
                                if (d1 != Dir.UP && cursor.equals(firebox.relative(d1))) {
                                    positions.clear();
                                    seen.clear();
                                    break;
                                }
                            }
                            queue.add(new Path(cursor, current.cost() + 1));
                            positions.add(cursor);
                            seen.add(cursor);
                        } else {
                            positions.clear();
                            seen.clear();
                            break;
                        }
                    }
                }
            }
        }
        return List.copyOf(positions);
    }
}
