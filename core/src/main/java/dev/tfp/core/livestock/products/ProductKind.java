/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/entities/livestock/DairyAnimal.java @ c3b4d9536,
 * src/main/java/net/dries007/tfc/common/entities/livestock/WoolyAnimal.java and
 * src/main/java/net/dries007/tfc/common/entities/livestock/OviparousAnimal.java
 */
package dev.tfp.core.livestock.products;

/**
 * The three animal products TFC makes, each with its own harvest rule.
 *
 * <ul>
 *   <li>{@link #MILK}: {@code DairyAnimal}, cows, goats and yaks. A female adult, off cooldown.</li>
 *   <li>{@link #SHEAR}: {@code WoolyAnimal} (sheep, alpaca, musk ox) and the bactrian camel. Either gender, an adult,
 *       and the cooldown also counts as ready if the animal was never sheared.</li>
 *   <li>{@link #EGG}: {@code OviparousAnimal} (chicken, duck, quail). Laid in a nest box, not harvested by the player.</li>
 * </ul>
 */
public enum ProductKind {
    MILK,
    SHEAR,
    EGG
}
