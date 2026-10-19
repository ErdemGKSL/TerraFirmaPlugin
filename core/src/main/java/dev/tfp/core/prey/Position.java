/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.prey;

/**
 * A world position as the three doubles of {@code Entity.position()}. Used by the flee rules for the threat distances
 * (TFC's {@code distanceToSqr} is the full three-dimensional distance) and for the flee target.
 *
 * @param x the x coordinate
 * @param y the y coordinate
 * @param z the z coordinate
 */
public record Position(double x, double y, double z) {

    /**
     * The squared three-dimensional distance, the same quantity as {@code Entity.distanceToSqr}.
     *
     * @param other the other position
     * @return the squared distance between the two positions
     */
    public double distanceSq(Position other) {
        final double dx = x - other.x;
        final double dy = y - other.y;
        final double dz = z - other.z;
        return dx * dx + dy * dy + dz * dz;
    }
}
