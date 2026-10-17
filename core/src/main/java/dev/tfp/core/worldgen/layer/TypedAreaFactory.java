/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/world/layer/framework/TypedAreaFactory.java @ c3b4d9536
 */
package dev.tfp.core.worldgen.layer;

import java.util.function.Supplier;

/**
 * @see AreaFactory
 */
public interface TypedAreaFactory<A> extends Supplier<TypedArea<A>> {}
