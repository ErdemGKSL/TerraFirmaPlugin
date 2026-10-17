/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/world/layer/framework/TypedSourceLayer.java @ c3b4d9536
 */
package dev.tfp.core.worldgen.layer;

/**
 * @see SourceLayer
 */
public interface TypedSourceLayer<A> {
    default TypedAreaFactory<A> apply(long seed) {
        return () -> {
            final AreaContext context = new AreaContext(seed);
            return new TypedArea<>((x, z) -> {
                context.setSeed(x, z);
                return apply(context, x, z);
            }, 1024);
        };
    }

    A apply(AreaContext context, int x, int z);
}
