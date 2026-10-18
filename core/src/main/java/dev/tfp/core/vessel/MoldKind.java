/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/items/TFCItems.java @ c3b4d9536 (the mold items),
 *       src/main/java/net/dries007/tfc/common/items/MoldItem.java @ c3b4d9536 (mapItemTypeToConfigValue),
 *       src/main/java/net/dries007/tfc/config/ServerConfig.java @ c3b4d9536 (mold capacity defaults)
 */
package dev.tfp.core.vessel;

/**
 * The mold kinds of TFC, with the default tank capacity of each in mB ({@code mold*Capacity} in {@code ServerConfig}).
 * TFC's fire ingot and bell molds read the key {@code moldIngotCapacity} and {@code moldScytheBladeCapacity} respectively
 * when their default is written; the values are the defaults, 100 each, and are what this enum records.
 */
public enum MoldKind {
    INGOT(100),
    FIRE_INGOT(100),
    PICKAXE_HEAD(100),
    PROPICK_HEAD(100),
    AXE_HEAD(100),
    SHOVEL_HEAD(100),
    HOE_HEAD(100),
    CHISEL_HEAD(100),
    HAMMER_HEAD(100),
    SAW_BLADE(100),
    JAVELIN_HEAD(100),
    SWORD_BLADE(200),
    MACE_HEAD(200),
    KNIFE_BLADE(100),
    SCYTHE_BLADE(100),
    BELL(100);

    private final int defaultCapacity;

    MoldKind(int defaultCapacity) {
        this.defaultCapacity = defaultCapacity;
    }

    /** The TFC default tank capacity of this mold in mB. */
    public int defaultCapacity() {
        return defaultCapacity;
    }
}
