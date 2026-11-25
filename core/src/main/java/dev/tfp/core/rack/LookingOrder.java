/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/wood/ToolRackBlock.java @ c3b4d9536 (getStateForPlacement walks getNearestLookingDirections)
 * vanilla net.minecraft.core.Direction.orderedByNearest (the order of the six directions by how near they are to the look)
 */
package dev.tfp.core.rack;

import dev.tfp.core.chest.ChestSide;
import java.util.ArrayList;
import java.util.List;

/**
 * PAR-DECO-05: vanilla's {@code Direction.orderedByNearest(entity)} ({@code BlockPlaceContext.getNearestLookingDirections}),
 * reduced to the four horizontal directions in the same relative order.
 */
public final class LookingOrder {
    private LookingOrder() {
    }

    private enum Dir {
        DOWN, UP, NORTH, SOUTH, WEST, EAST;

        Dir opposite() {
            return switch (this) {
                case DOWN -> UP;
                case UP -> DOWN;
                case NORTH -> SOUTH;
                case SOUTH -> NORTH;
                case WEST -> EAST;
                case EAST -> WEST;
            };
        }

        ChestSide side() {
            return switch (this) {
                case NORTH -> ChestSide.NORTH;
                case SOUTH -> ChestSide.SOUTH;
                case WEST -> ChestSide.WEST;
                case EAST -> ChestSide.EAST;
                default -> null;
            };
        }
    }

    /** The horizontal directions, nearest to the look first (the look is Bukkit's yaw and pitch in degrees). */
    public static List<ChestSide> horizontal(float yaw, float pitch) {
        double f = pitch * (Math.PI / 180.0);
        double g = -yaw * (Math.PI / 180.0);
        double h = Math.sin(f);
        double i = Math.cos(f);
        double j = Math.sin(g);
        double k = Math.cos(g);
        boolean east = j > 0.0;
        boolean up = h < 0.0;
        boolean south = k > 0.0;
        double l = east ? j : -j;
        double m = up ? -h : h;
        double n = south ? k : -k;
        double o = l * i;
        double p = n * i;
        Dir d1 = east ? Dir.EAST : Dir.WEST;
        Dir d2 = up ? Dir.UP : Dir.DOWN;
        Dir d3 = south ? Dir.SOUTH : Dir.NORTH;
        Dir[] order;
        if (l > n) {
            if (m > o) {
                order = of(d2, d1, d3);
            } else {
                order = p > m ? of(d1, d3, d2) : of(d1, d2, d3);
            }
        } else if (m > p) {
            order = of(d2, d3, d1);
        } else {
            order = o > m ? of(d3, d1, d2) : of(d3, d2, d1);
        }
        List<ChestSide> out = new ArrayList<>(4);
        for (Dir d : order) {
            if (d.side() != null) {
                out.add(d.side());
            }
        }
        return out;
    }

    private static Dir[] of(Dir a, Dir b, Dir c) {
        return new Dir[] {a, b, c, c.opposite(), b.opposite(), a.opposite()};
    }
}
