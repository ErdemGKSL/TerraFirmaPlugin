/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/world/feature/vein/IVein.java @ c3b4d9536.
 */
package dev.tfp.core.worldgen.ore;

/** One vein of any type: all that the placement needs from it is its centre. Immutable. */
public sealed interface Vein permits ClusterVein, DiscVein, PipeVein {
    /** The centre's block x. */
    int x();

    /** The centre's y (before the projection, for a projected type). */
    int y();

    /** The centre's block z. */
    int z();
}
