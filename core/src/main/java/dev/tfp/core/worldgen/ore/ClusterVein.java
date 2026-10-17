/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/world/feature/vein/ClusterVeinFeature.java @ c3b4d9536 (ClusterVeinFeature.Vein).
 */
package dev.tfp.core.worldgen.ore;

/**
 * One cluster vein: its centre and its metaball shape, relative to the centre. Immutable.
 *
 * @param x the centre's block x
 * @param y the centre's block y
 * @param z the centre's block z
 */
public record ClusterVein(int x, int y, int z, Metaballs3D metaballs) {}
