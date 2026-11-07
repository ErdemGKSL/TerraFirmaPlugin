/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/fluids/RiverWaterFluid.java @ c3b4d9536 (getFlow, getHeight, getAmount: what the
 *       entity fluid push of vanilla reads from a river water block)
 */
package dev.tfp.core.worldwater;

import dev.tfp.core.fluid.RiverWaterRules;
import dev.tfp.core.worldgen.river.Flow;
import java.util.List;

/**
 * The river current on an entity (PAR-FLUID-03). TFC's river water is a fluid whose {@code getFlow} is the flow's unit vector;
 * vanilla's {@code Entity.updateFluidHeightAndDoFluidPushing} turns the flows of the water blocks the entity touches into a
 * velocity change each tick. Vanilla clients cannot hold that fluid, so the server applies the same push to the entity's
 * velocity. Pure; any thread.
 *
 * <p>Vanilla's push, with TFC's river water as the fluid:
 * <ol>
 *   <li>For every water block in the entity's box (deflated by 0.001) whose top ({@code y + height}) reaches the box's minimum
 *       y: the depth {@code d0} becomes the running maximum of {@code top - minY}; the block's flow vector, scaled by {@code d0}
 *       when {@code d0 < 0.4}, is added to the sum, and the count goes up by one.</li>
 *   <li>If the sum is not zero: it is divided by the count, normalized for every entity but a player, scaled by the water
 *       motion scale {@link #WATER_MOTION_SCALE}, and raised to {@link #MIN_PUSH} when the entity is nearly still.</li>
 * </ol>
 * The height of a river water block is {@code 1} with water of the same family above it, else {@code 8 / 9}
 * ({@code getAmount} is always 8, {@code getOwnHeight = amount / 9}). Water that is not river water counts but adds no flow:
 * the vanilla water of the world is a source, whose vanilla flow the server already applies.
 */
public final class RiverCurrent {
    /** {@code Entity.updateInWaterStateAndDoWaterCurrentPushing}: the water motion scale {@code 0.014}. */
    public static final double WATER_MOTION_SCALE = 0.014;
    /** Vanilla's "nearly still" bound on each horizontal velocity component. */
    public static final double STILL = 0.003;
    /** Vanilla's minimum push length for a nearly still entity. */
    public static final double MIN_PUSH = 0.0045000000000000005;
    /** Vanilla scales a block's flow by the depth below this depth. */
    public static final double SHALLOW = 0.4;
    /** {@code getOwnHeight} of a river water block: {@code getAmount() / 9}. */
    public static final float OWN_HEIGHT = RiverWaterRules.AMOUNT / 9.0f;

    private RiverCurrent() {
    }

    /**
     * One water block in the entity's box.
     *
     * @param y      the block's y
     * @param height the block's fluid height ({@link #height})
     * @param flow   the river flow of the block, {@link Flow#NONE} for water that is not river water
     */
    public record Cell(int y, float height, Flow flow) {
    }

    /** The fluid height of a river water block, as {@code RiverWaterFluid.getHeight}. */
    public static float height(boolean sameAbove) {
        return RiverWaterRules.height(sameAbove, OWN_HEIGHT);
    }

    /**
     * The velocity change of one tick.
     *
     * @param cells  the water blocks in the entity's box, in vanilla's x, y, z iteration order
     * @param minY   the box's minimum y (after the 0.001 deflate)
     * @param player whether the entity is a player (players are not normalized)
     * @param vx     the entity's current x velocity
     * @param vz     the entity's current z velocity
     * @return the change to add to the velocity, zero when nothing pushes
     */
    public static RiverWaterRules.Vector push(List<Cell> cells, double minY, boolean player, double vx, double vz) {
        double d0 = 0.0;
        double sx = 0.0;
        double sy = 0.0;
        double sz = 0.0;
        int count = 0;
        for (Cell cell : cells) {
            double top = cell.y() + cell.height();
            if (top < minY) {
                continue;
            }
            d0 = Math.max(top - minY, d0);
            RiverWaterRules.Vector flow = cell.flow() == Flow.NONE
                    ? new RiverWaterRules.Vector(0, 0, 0)
                    : RiverWaterRules.flowVector(cell.flow(), false, false);
            double scale = d0 < SHALLOW ? d0 : 1.0;
            sx += flow.x() * scale;
            sy += flow.y() * scale;
            sz += flow.z() * scale;
            count++;
        }
        RiverWaterRules.Vector sum = new RiverWaterRules.Vector(sx, sy, sz);
        if (length(sum) <= 0.0) {
            return new RiverWaterRules.Vector(0, 0, 0);
        }
        if (count > 0) {
            sum = scale(sum, 1.0 / count);
        }
        if (!player) {
            sum = sum.normalize();
        }
        sum = scale(sum, WATER_MOTION_SCALE);
        if (Math.abs(vx) < STILL && Math.abs(vz) < STILL && length(sum) < MIN_PUSH) {
            sum = scale(sum.normalize(), MIN_PUSH);
        }
        return sum;
    }

    private static double length(RiverWaterRules.Vector v) {
        return Math.sqrt(v.x() * v.x() + v.y() * v.y() + v.z() * v.z());
    }

    private static RiverWaterRules.Vector scale(RiverWaterRules.Vector v, double f) {
        return new RiverWaterRules.Vector(v.x() * f, v.y() * f, v.z() * f);
    }
}
