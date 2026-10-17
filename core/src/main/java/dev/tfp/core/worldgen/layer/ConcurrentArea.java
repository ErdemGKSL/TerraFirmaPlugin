/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/world/layer/framework/ConcurrentArea.java @ c3b4d9536
 */
package dev.tfp.core.worldgen.layer;

import java.util.function.IntFunction;

/**
 * A thread-confined view of an {@link AreaFactory}: each thread builds its own {@link Area} on first use, so the
 * unsynchronized caches and contexts are never shared. Optionally maps each id to another type.
 */
public final class ConcurrentArea<T> {
    private final ThreadLocal<Area> area;
    private final IntFunction<T> mappingFunction;

    public ConcurrentArea(AreaFactory factory, IntFunction<T> mappingFunction) {
        this.area = ThreadLocal.withInitial(factory);
        this.mappingFunction = mappingFunction;
    }

    public T get(int x, int z) {
        return mappingFunction.apply(area.get().get(x, z));
    }
}
