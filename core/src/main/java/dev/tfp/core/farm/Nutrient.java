/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/FarmlandBlockEntity.java @ c3b4d9536 (NutrientType)
 */
package dev.tfp.core.farm;

/** The three soil nutrients of a farmland block. Order matches TFC's {@code NutrientType}. */
public enum Nutrient {
    NITROGEN,
    PHOSPHOROUS,
    POTASSIUM
}
