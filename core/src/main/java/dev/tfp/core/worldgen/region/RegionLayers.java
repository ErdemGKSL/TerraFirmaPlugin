/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/world/layer/TFCLayers.java @ c3b4d9536 (createUniformLayer)
 */
package dev.tfp.core.worldgen.region;

import dev.tfp.core.worldgen.Seed;
import dev.tfp.core.worldgen.layer.AreaFactory;
import dev.tfp.core.worldgen.layer.SmoothLayer;
import dev.tfp.core.worldgen.layer.UniformLayer;
import dev.tfp.core.worldgen.layer.ZoomLayer;

/** The layer stacks the region generator builds from the seed. */
public final class RegionLayers {
    private RegionLayers() {}

    /**
     * A uniform random id field, zoomed {@code zoomLevels} times with a smooth after each zoom. Every {@link Seed#next()}
     * call is taken in TFC's order (the source, then zoom and smooth per level), so the seeds of the later layers match.
     */
    public static AreaFactory createUniformLayer(Seed seed, int zoomLevels) {
        AreaFactory layer = UniformLayer.INSTANCE.apply(seed.next());
        for (int i = 0; i < zoomLevels; i++) {
            layer = ZoomLayer.NORMAL.apply(seed.next(), layer);
            layer = SmoothLayer.INSTANCE.apply(seed.next(), layer);
        }
        return layer;
    }
}
