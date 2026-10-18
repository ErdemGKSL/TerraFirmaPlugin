/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/component/fluid/FluidComponent.java @ c3b4d9536 (an empty stack is one with no amount)
 */
package dev.tfp.core.vessel;

import java.util.Objects;

/**
 * A fluid identifier and an amount in mB. An amount of zero is the empty stack, whatever the identifier, so every empty
 * value compares equal to {@link #EMPTY}.
 *
 * @param fluid  the fluid identifier, for example {@code tfc:metal/copper}; ignored while empty
 * @param amount the amount in mB, {@code >= 0}
 */
public record FluidAmount(String fluid, int amount) {
    public static final FluidAmount EMPTY = new FluidAmount("", 0);

    public FluidAmount {
        Objects.requireNonNull(fluid, "fluid");
        if (amount < 0) {
            throw new IllegalArgumentException("amount must be >= 0: " + amount);
        }
        if (amount == 0) {
            fluid = "";
        }
    }

    public boolean isEmpty() {
        return amount == 0;
    }
}
