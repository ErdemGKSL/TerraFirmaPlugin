/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.integration;

import dev.tfp.core.visuals.ProfileCheck.PoolUsage;
import java.util.Optional;

/** {@link VisualPools} with test-set counts. */
public final class FakeVisualPools implements VisualPools {
    private volatile PoolUsage solid;

    public FakeVisualPools solid(int capacity, int used, int ours) {
        this.solid = new PoolUsage(capacity, used, ours);
        return this;
    }

    @Override
    public Optional<PoolUsage> solid() {
        return Optional.ofNullable(solid);
    }
}
