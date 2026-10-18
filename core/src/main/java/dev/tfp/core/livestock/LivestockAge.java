/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/entities/livestock/Age.java @ c3b4d9536
 */
package dev.tfp.core.livestock;

/** TFC {@code Age}: the life stage of a livestock animal, derived lazily from its stored birth and old ticks. */
public enum LivestockAge {
    CHILD,
    ADULT,
    OLD
}
