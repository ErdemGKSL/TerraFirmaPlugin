/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.gather;

/**
 * The item tags a loot condition can test on the held tool: {@code #tfc:tools/sharp}, {@code #c:tools/shear},
 * {@code #minecraft:axes} and the silk touch enchantment.
 */
public enum ToolTag {
    SHARP,
    SHEAR,
    AXE,
    SILK_TOUCH
}
