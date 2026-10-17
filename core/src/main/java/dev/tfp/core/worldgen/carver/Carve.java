/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.worldgen.carver;

/** What a carver does to one block position: leave the block, or replace it with air, water or lava. */
public enum Carve {
    /** The block is kept (solid rock, or not in a cave). */
    KEEP,
    /** The position becomes air (cave air in TFC; plain air here). */
    AIR,
    /** The position becomes water. */
    WATER,
    /** The position becomes lava. */
    LAVA
}
