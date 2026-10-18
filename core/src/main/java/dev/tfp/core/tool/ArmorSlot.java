/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/util/Metal.java (armorDurability) @ c3b4d9536
 */
package dev.tfp.core.tool;

/**
 * The armor pieces of a metal armor set. {@link #BODY} is the horse armor, which uses the chestplate's durability and defense,
 * as TFC's {@code Metal.armorDurability} and {@code TFCArmorMaterials} do.
 */
public enum ArmorSlot {
    HEAD,
    CHEST,
    LEGS,
    FEET,
    BODY,
}
