/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/world/region/KarstSurfaceRocks.java @ c3b4d9536
 */
package dev.tfp.core.worldgen.region;

/**
 * TFC's karst annotation: a point is karst when the rock of its top layer ({@code getSurfaceRock}) is marked {@code karst}
 * in its rock settings. Runs after {@code ChooseRocks}; draws nothing from the region random. Skipped (every point stays
 * non-karst) by a generator built without rock settings.
 */
enum KarstSurfaceRocks implements RegionTask {
    INSTANCE;

    @Override
    public void apply(RegionGenerator.Context context) {
        final RegionGenerator generator = context.generator();
        if (generator.karstRock == null) {
            return;
        }
        for (final var point : context.region.points()) {
            point.isSurfaceRockKarst = generator.karstRock.test(generator.getSurfaceRock(point));
        }
    }
}
