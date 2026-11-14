/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/AbstractFirepitBlockEntity.java @ c3b4d9536 (isItemValid)
 */
package dev.tfp.paper.firepit.gui;

import dev.tfp.core.firepit.FirepitSim;
import java.util.Objects;
import java.util.function.Predicate;
import org.bukkit.inventory.ItemStack;

/**
 * PAR-HEAT-07: TFC's {@code AbstractFirepitBlockEntity.isItemValid}, as the screen asks it.
 *
 * @param fuel     {@code Fuel.get(stack) != null && #tfc:firepit_fuel}
 * @param heatable {@code HeatCapability.has(stack)}
 */
public record FirepitSlotRules(Predicate<ItemStack> fuel, Predicate<ItemStack> heatable) {
    public FirepitSlotRules {
        Objects.requireNonNull(fuel, "fuel");
        Objects.requireNonNull(heatable, "heatable");
    }

    /** TFC {@code isItemValid}: slot 3 fuel, slot 4 heatable, the outputs anything, fuel slots 0 to 2 nothing. */
    public boolean isItemValid(int tfcSlot, ItemStack stack) {
        return switch (tfcSlot) {
            case FirepitSim.SLOT_FUEL_INPUT -> fuel.test(stack);
            case FirepitSim.SLOT_ITEM_INPUT -> heatable.test(stack);
            case FirepitSim.SLOT_OUTPUT_1, FirepitSim.SLOT_OUTPUT_2 -> true;
            default -> false;
        };
    }
}
