/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/world/feature/vein/IVeinConfig.java @ c3b4d9536, with VeinConfig.java of the same
 * package.
 */
package dev.tfp.core.worldgen.ore;

import java.util.Map;

/** What every vein type's configuration has in common ({@code IVeinConfig} over {@code VeinConfig}). Immutable. */
public sealed interface VeinConfig permits ClusterVeinConfig, DiscVeinConfig, PipeVeinConfig {
    /** The vein name (the file name, e.g. {@code normal_malachite}). */
    String name();

    /** One vein per {@code rarity} chunks on average. */
    int rarity();

    /** The chance that a position inside the vein's shape is written, before the shape's own factors. */
    float density();

    /** The lowest y the vein writes (inclusive), before the projection. */
    int minY();

    /** The highest y the vein writes (inclusive), before the projection. */
    int maxY();

    /** The vein's seed: the hash of its {@code random_name}, or the long given there. */
    long seed();

    /** The weighted blocks per replaced block id. */
    Map<String, Weighted<VeinBlock>> blocks();

    /** The indicator of the type, or null. */
    Indicator indicator();

    /** {@code project}: the vein's y values are offsets from the ground height of the column (ocean floor heightmap). */
    default boolean project() {
        return false;
    }

    /** {@code project_offset}: the projected height is read at a per-vein offset of up to 15 blocks in x and z. */
    default boolean projectOffset() {
        return false;
    }

    /** {@code near_lava}: a column is written only with lava at y = -55 within four blocks in x and z. */
    default boolean nearLava() {
        return false;
    }

    /** {@code IVeinConfig.chunkRadius}: how many chunks away the vein's centre can be and still reach a chunk. */
    int chunkRadius();

    /** {@code IVeinConfig.verticalRadius}: keeps the centre this far inside the height range when it fits. */
    int verticalRadius();

    /** Whether the vein can only be decided with a view of the world (the ground height or the lava). */
    default boolean needsWorld() {
        return project() || nearLava();
    }
}
