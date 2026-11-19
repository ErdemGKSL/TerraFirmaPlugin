/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/world/feature/vein/DiscVeinFeature.java @ c3b4d9536 (DiscVeinFeature.Vein).
 */
package dev.tfp.core.worldgen.ore;

import dev.tfp.core.worldgen.deposit.Metaballs2D;

/** One disc vein: its centre and its 2D metaball shape, relative to the centre. Immutable. */
public record DiscVein(int x, int y, int z, Metaballs2D metaballs) implements Vein {}
