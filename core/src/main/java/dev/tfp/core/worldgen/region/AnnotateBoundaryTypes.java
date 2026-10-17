/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/world/region/AnnotateBoundaryTypes.java @ c3b4d9536
 */
package dev.tfp.core.worldgen.region;

/** The tectonic divergence at each point: negative near converging boundaries, positive near diverging ones. */
enum AnnotateBoundaryTypes implements RegionTask {
    INSTANCE;

    @Override
    public void apply(RegionGenerator.Context context) {
        final Region region = context.region;

        for (final var point : region.points()) {
            point.divergence = (float) context.generator().getDivergence(point);
        }
    }
}
