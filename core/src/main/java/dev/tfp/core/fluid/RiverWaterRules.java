/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/fluids/RiverWaterFluid.java @ c3b4d9536 (isSource, isSame, getHeight,
 *       getSource, getFlow, getAmount), src/main/java/net/dries007/tfc/world/river/Flow.java @ c3b4d9536 (the vector of each
 *       flow; the lerp is ported in dev.tfp.core.worldgen.river.Flow)
 */
package dev.tfp.core.fluid;

import dev.tfp.core.worldgen.Mth;
import dev.tfp.core.worldgen.river.Flow;
import java.util.Set;

/**
 * The in-world rules of river water, which TFC keeps as its own fluid with a flow direction, unlike vanilla water.
 *
 * <p>Where TFC differs from vanilla, and the rule is decidable from facts:
 * <ul>
 *   <li>Every river water block is a source, so none of them flows down or dries up as a vanilla flowing block does.</li>
 *   <li>Its amount is always 8, whatever the state.</li>
 *   <li>It shares one family with salt water and vanilla water. The fluid type of another block is the same as this one for
 *       any id in {@link #FAMILY}.</li>
 *   <li>Its surface height is full when the block above is of the same family.</li>
 *   <li>Its source flow is the lerp of the four cardinal neighbours' flows. With no flow to lerp, it is a vanilla source.</li>
 *   <li>Its flow vector is the flow's unit vector, pointed down when it falls past a solid face.</li>
 * </ul>
 * Nothing here reads the world: the caller supplies the neighbouring flows and the solid-face test.
 */
public final class RiverWaterRules {

    /** The fluid id of river water. */
    public static final String RIVER_WATER = "tfc:river_water";

    /** TFC {@code RiverWaterFluid.getAmount}: a river water block always holds 8 units. */
    public static final int AMOUNT = 8;

    /**
     * The fluid ids that {@code RiverWaterFluid.isSame} accepts: river water, salt water (source and flowing), and vanilla
     * water (source and flowing).
     */
    public static final Set<String> FAMILY = Set.of(
        RIVER_WATER,
        "tfc:salt_water",
        "tfc:flowing_salt_water",
        "minecraft:water",
        "minecraft:flowing_water");

    private static final double MIN_LENGTH = 1.0E-4;
    private static final double FALL = -6.0;

    private RiverWaterRules() {
    }

    /**
     * A vector in world space, with the y axis up.
     *
     * @param x the east component
     * @param y the up component
     * @param z the south component
     */
    public record Vector(double x, double y, double z) {

        /**
         * Scales this vector to unit length, as vanilla {@code Vec3.normalize}: a vector shorter than 1e-4 becomes zero.
         *
         * @return the normalized vector
         */
        public Vector normalize() {
            final double length = Math.sqrt(x * x + y * y + z * z);
            return length < MIN_LENGTH ? new Vector(0, 0, 0) : new Vector(x / length, y / length, z / length);
        }

        /**
         * @return this vector plus {@code (dx, dy, dz)}
         */
        public Vector add(double dx, double dy, double dz) {
            return new Vector(x + dx, y + dy, z + dz);
        }
    }

    /**
     * Whether {@code RiverWaterFluid.isSame} accepts the other fluid.
     *
     * @param otherFluid the id of the fluid compared with river water
     * @return {@code true} for the ids of {@link #FAMILY}
     */
    public static boolean isSame(String otherFluid) {
        return FAMILY.contains(otherFluid);
    }

    /**
     * The surface height of a river water block. A block with river water above it is full.
     *
     * @param sameAbove  whether the block above is of the same family ({@link #isSame(String)})
     * @param ownHeight  the vanilla height of this block, used when nothing is above it
     * @return {@code 1} when {@code sameAbove}, otherwise {@code ownHeight}
     */
    public static float height(boolean sameAbove, float ownHeight) {
        return sameAbove ? 1f : ownHeight;
    }

    /**
     * The flow of a river water source, the average of the four cardinal neighbours' flows (TFC lerps them at 0.5 and 0.5).
     *
     * @param north the flow of the block to the north
     * @param east  the flow of the block to the east
     * @param west  the flow of the block to the west
     * @param south the flow of the block to the south
     * @return the lerped flow, or {@link Flow#NONE} when the neighbours have no flow, which TFC turns into a vanilla source
     */
    public static Flow sourceFlow(Flow north, Flow east, Flow west, Flow south) {
        return Flow.lerp(north, east, west, south, 0.5f, 0.5f);
    }

    /**
     * The flow vector of a river water block, as TFC's {@code RiverWaterFluid.getFlow}.
     *
     * <p>A falling block whose horizontal neighbour (or the one above it) is a solid face gets a downward tilt before the
     * final normalize. The solid-face test is an input: TFC only needs one face, so the caller passes whether any exists.
     * {@link Flow#NONE} has the vector of the sixteenth eighth-turn, which points east, as TFC's table does.
     *
     * @param flow           the flow of the block
     * @param falling        whether the block is falling
     * @param solidFaceNearby whether a horizontal neighbour, or the block above it, has a solid face toward this block
     * @return the unit flow vector, or zero when the flow vector is degenerate
     */
    public static Vector flowVector(Flow flow, boolean falling, boolean solidFaceNearby) {
        Vector vector = unitVector(flow);
        if (falling && solidFaceNearby) {
            vector = vector.normalize().add(0.0, FALL, 0.0);
        }
        return vector.normalize();
    }

    /** TFC's {@code Flow} vector: {@code (cos(a), 0, -sin(a))} with {@code a = ordinal * PI / 8}. */
    private static Vector unitVector(Flow flow) {
        final float angle = flow.ordinal() * (1 / 8f) * Mth.PI;
        return new Vector(Mth.cos(angle), 0, -Mth.sin(angle));
    }
}
