/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/world/feature/vein/PipeVeinFeature.java @ c3b4d9536 (PipeVeinFeature.Vein).
 */
package dev.tfp.core.worldgen.ore;

/**
 * One pipe vein. Immutable.
 *
 * @param sign  the direction in which the radius changes by {@code slant}: 1 or -1
 * @param skewX the x part of the skew direction (a cosine)
 * @param skewZ the z part of the skew direction (a sine)
 * @param skew  the horizontal shift over the half-height
 * @param slant the change of the radius from one end to the other
 */
public record PipeVein(int x, int y, int z, int sign, float skewX, float skewZ, int skew, int slant) implements Vein {}
