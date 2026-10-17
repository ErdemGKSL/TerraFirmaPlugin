/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/world/layer/framework/SourceLayer.java @ c3b4d9536
 */
package dev.tfp.core.worldgen.layer;

/**
 * The source layer of a stack of layers. Computes an integer value from the (x, z) coordinate and the context's random.
 */
public interface SourceLayer {
    default AreaFactory apply(long seed) {
        return () -> {
            final AreaContext context = new AreaContext(seed);
            return new Area((x, z) -> {
                context.setSeed(x, z);
                return apply(context, x, z);
            }, 1024);
        };
    }

    int apply(AreaContext context, int x, int z);
}
