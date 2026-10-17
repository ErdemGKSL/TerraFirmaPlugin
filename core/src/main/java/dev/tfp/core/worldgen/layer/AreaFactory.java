/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/world/layer/framework/AreaFactory.java @ c3b4d9536
 */
package dev.tfp.core.worldgen.layer;

import java.util.function.Supplier;

/**
 * Creates a new {@link Area} (and its own context) on each call. Hand each thread its own {@link Area} via {@link ConcurrentArea}.
 */
public interface AreaFactory extends Supplier<Area> {}
