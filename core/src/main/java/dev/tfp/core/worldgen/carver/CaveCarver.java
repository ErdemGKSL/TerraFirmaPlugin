/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/world/carver/CarverHelpers.java @ c3b4d9536 (getCarveState, with the debug settings
 * removed), and the cave configuration src/main/resources/data/tfc/worldgen/configured_carver/cave.json (lava_level).
 */
package dev.tfp.core.worldgen.carver;

/**
 * The carve decision of a cave block: inside the tunnel shape, below the lava level the position is lava, otherwise the
 * aquifer decides (air, water or lava, or keeps solid rock). The tunnel shape ({@code inCave}) is the caller's: TFC's
 * vanilla {@code CaveWorldCarver} tunnels are not ported yet. Pure.
 */
public final class CaveCarver {
    /** cave.json: {@code lava_level: {above_bottom: 8}}, resolved against the world's lowest block. */
    public static final int LAVA_ABOVE_BOTTOM = 8;

    private CaveCarver() {}

    /**
     * @param aquifer the chunk's aquifers
     * @param x       the block x
     * @param y       the block y
     * @param z       the block z
     * @param inCave  whether the position is inside a cave's tunnel shape (only those positions are carved)
     * @return the carve of the position: {@link Carve#KEEP} outside the tunnel or where the rock stays solid
     */
    public static Carve carve(Aquifer aquifer, int x, int y, int z, boolean inCave) {
        if (!inCave) {
            return Carve.KEEP;
        }
        if (y <= aquifer.minY() + LAVA_ABOVE_BOTTOM) {
            return Carve.LAVA;
        }
        return aquifer.sample(x, y, z, 0.0);
    }
}
