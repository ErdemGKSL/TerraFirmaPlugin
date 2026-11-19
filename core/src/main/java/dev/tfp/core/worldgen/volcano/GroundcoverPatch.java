/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/resources/data/tfc/worldgen/configured_feature/cinder_cone_pumice_patch.json @ c3b4d9536, with the other
 * {cinder_cone,tuya,stratovolcano}_{pumice,obsidian_shard}_patch.json and {pumice,obsidian_shard}_shield_volcano_patch.json
 * (random_patch: tries 10, xz_spread 10, y_spread 1), their simple_block features and placed features (heightmap
 * WORLD_SURFACE_WG, tfc:replaceable, would_survive), the vanilla 1.21 RandomPatchFeature (the draws) and
 * src/main/java/net/dries007/tfc/common/blocks/GroundcoverBlock.java (canSurvive)
 */
package dev.tfp.core.worldgen.volcano;

import dev.tfp.core.worldgen.random.XoroshiroRandomSource;

/**
 * TFC's pumice and obsidian shard patches of the cinder cones, tuyas, stratovolcanoes and shield volcanoes: ten tries within
 * ten blocks of the origin, each a groundcover block on the surface where the block is replaceable and the floor is a full
 * block. Pure: it reads and writes a {@link VolcanoLevel} only.
 */
final class GroundcoverPatch {
    static final int TRIES = 10;
    static final int XZ_SPREAD = 10;
    static final int Y_SPREAD = 1;

    private GroundcoverPatch() {}

    /**
     * Vanilla's {@code RandomPatchFeature.place} with TFC's config at the column {@code (ox, oz)}, draw for draw (the y
     * offset is drawn, then replaced by the heightmap). Returns the number of blocks placed.
     */
    static int place(VolcanoLevel level, XoroshiroRandomSource random, int ox, int oz, VolcanicKind kind) {
        final VolcanoView view = level.view();
        final int j = XZ_SPREAD + 1;
        final int k = Y_SPREAD + 1;
        int placed = 0;
        for (int l = 0; l < TRIES; l++) {
            final int x = ox + random.nextInt(j) - random.nextInt(j);
            random.nextInt(k);
            random.nextInt(k);
            final int z = oz + random.nextInt(j) - random.nextInt(j);
            // heightmap WORLD_SURFACE_WG
            final int y = view.worldSurface(x, z);
            if (y <= view.minY()) {
                continue;
            }
            // tfc:replaceable (isWorldgenReplaceable), then would_survive (a sturdy top below)
            if (level.isAirOrEmptyFluid(x, y, z) && level.sturdyTop(x, y - 1, z)) {
                level.set(x, y, z, kind);
                placed++;
            }
        }
        return placed;
    }
}
