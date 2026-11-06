/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/devices/ScrapingBlock.java @ c3b4d9536 (calculatePoint, PLANE_NORMAL)
 */
package dev.tfp.core.scraping;

import dev.tfp.core.craft.ScrapingRules;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Where a knife click lands on the scraping surface. TFC does not use the hit position on the 1 px thin block directly:
 * {@code ScrapingBlock.calculatePoint} follows the player's look ray down to the block's bottom plane ({@code y = 0},
 * normal {@code (0, 1, 0)}) and takes the {@code x} and {@code z} there. Any point of the ray gives the same plane point,
 * so the Paper side passes the eye position (relative to the block's minimum corner) instead of the hit position.
 */
public final class ScrapingAim {
    private ScrapingAim() {}

    /** A point on the bottom plane of the block, in block units relative to its minimum corner. */
    public record Point(double x, double z) {}

    /**
     * TFC {@code calculatePoint(rayVector, rayPoint)}: {@code rayPoint - rayVector * (rayPoint.y / rayVector.y)}.
     *
     * @return the plane point, empty when the ray is parallel to the plane or the result is not finite
     */
    public static Optional<Point> point(double lookX, double lookY, double lookZ, double rayX, double rayY, double rayZ) {
        if (lookY == 0) return Optional.empty();
        final double t = rayY / lookY;
        final double x = rayX - lookX * t;
        final double z = rayZ - lookZ * t;
        if (!Double.isFinite(x) || !Double.isFinite(z)) return Optional.empty();
        return Optional.of(new Point(x, z));
    }

    /**
     * The grid cell ({@code x + 4 * z}) of a plane point. TFC truncates {@code (int) (hit * 4)}, so a point a little
     * outside the surface (a shallow ray through the edge) lands on a wrong or missing bit; here the point is clamped to
     * the surface first, so it lands on the nearest edge cell.
     */
    public static int cell(Point p) {
        return ScrapingRules.cellOf(clamp(p.x()), clamp(p.z()));
    }

    /** The cell under a look ray (any point of it, relative to the block), empty when the ray never meets the plane. */
    public static OptionalInt cell(double lookX, double lookY, double lookZ, double rayX, double rayY, double rayZ) {
        return point(lookX, lookY, lookZ, rayX, rayY, rayZ).map(p -> OptionalInt.of(cell(p))).orElse(OptionalInt.empty());
    }

    /** {@code v} clamped to {@code [0, 1]}. */
    public static double clamp(double v) {
        return Math.max(0, Math.min(1, v));
    }
}
