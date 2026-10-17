/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/world/feature/SoilDiscFeature.java @ c3b4d9536 (place), with SoilDiscConfig.java of
 * the same package (getRadius, getState) and Helpers.fork from src/main/java/net/dries007/tfc/util/Helpers.java.
 */
package dev.tfp.core.worldgen.deposit;

import dev.tfp.core.worldgen.random.XoroshiroRandomSource;
import java.util.Map;

/**
 * TFC's {@code tfc:soil_disc}: a flat metaball blob of radius {@code minRadius..maxRadius - 1} around the origin, {@code 2 *
 * height + 1} blocks thick, where each position, with the chance {@code integrity}, has its block replaced when it is one of
 * the disc's {@code replace} blocks. Immutable.
 *
 * @param minRadius the smallest radius
 * @param maxRadius the largest radius plus one ({@code getRadius} draws in {@code [min, max)}), or the radius when equal
 * @param height    the half thickness
 * @param integrity the chance that a position of the shape is tried
 * @param replace   what replaces each replaceable material
 * @param mineral   the deposit's mineral, for the ore deposits; null for the soil discs
 */
public record SoilDisc(int minRadius, int maxRadius, int height, float integrity, Map<DepositMaterial, DepositKind> replace,
                       String mineral) {
    public SoilDisc {
        if (minRadius <= 0 || maxRadius < minRadius || height < 0 || height > 256) {
            throw new IllegalArgumentException("radius must be positive with max >= min, and height in [0, 256]");
        }
        replace = Map.copyOf(replace);
    }

    /** A visitor of the positions of a disc that are tried, in TFC's order. */
    @FunctionalInterface
    public interface Visitor {
        void visit(int x, int y, int z);
    }

    /** {@code SoilDiscConfig.getRadius}. */
    int radius(XoroshiroRandomSource random) {
        if (maxRadius > minRadius) {
            return minRadius + random.nextInt(maxRadius - minRadius);
        }
        return minRadius;
    }

    /**
     * {@code SoilDiscFeature.place} without the world: the radius, the shape ({@code Metaballs2D.simple} of a fork of the
     * random, size twice the radius), then x outer, z inner and y innermost, one integrity float per position of the shape.
     * {@code visitor} gets each position whose float passed; the caller replaces the block there when it is replaceable.
     */
    public void shape(XoroshiroRandomSource random, int originX, int originY, int originZ, Visitor visitor) {
        final int radius = radius(random);
        // Helpers.fork: new XoroshiroRandomSource(random.nextLong(), random.nextLong())
        final long lo = random.nextLong();
        final Metaballs2D noise = Metaballs2D.simple(new XoroshiroRandomSource(lo, random.nextLong()), radius * 2);
        for (int x = originX - radius; x <= originX + radius; ++x) {
            for (int z = originZ - radius; z <= originZ + radius; ++z) {
                if (noise.inside(x - originX, z - originZ)) {
                    for (int y = originY - height; y <= originY + height; ++y) {
                        if (random.nextFloat() < integrity) {
                            visitor.visit(x, y, z);
                        }
                    }
                }
            }
        }
    }

    /** {@code SoilDiscConfig.getState}: what replaces a material, or null when the disc does not replace it. */
    public DepositKind replacement(DepositMaterial material) {
        return material == null ? null : replace.get(material);
    }
}
