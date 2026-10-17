/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/world/layer/framework/TypedAreaSource.java @ c3b4d9536
 */
package dev.tfp.core.worldgen.layer;

/**
 * @see AreaSource
 */
@FunctionalInterface
public interface TypedAreaSource<T> {
    T apply(int x, int z);
}
