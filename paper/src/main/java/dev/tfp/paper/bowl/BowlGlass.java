/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/BowlBlock.java @ c3b4d9536 (useItemOn: glass powder),
 *       src/main/java/net/dries007/tfc/common/items/GlassWorking.java @ c3b4d9536 (apply)
 */
package dev.tfp.paper.bowl;

import org.bukkit.inventory.ItemStack;

/**
 * The glassworking side of a powder bowl (TFC {@code GlassWorking.get(held)} / {@code GlassWorking.apply}). The glass
 * batch of a blowpipe belongs to PAR-PROC-16; until its module registers an implementation as a service the bowl never
 * treats a held item as a glass batch ({@link #NONE}).
 */
public interface BowlGlass {
    BowlGlass NONE = new BowlGlass() {
        @Override
        public boolean working(ItemStack held) {
            return false;
        }

        @Override
        public boolean apply(ItemStack held, String operationId) {
            return false;
        }
    };

    /** Whether {@code held} carries glass working data ({@code !GlassWorking.get(held).isEmpty()}). */
    boolean working(ItemStack held);

    /** Adds the powder operation ({@code tfc:soda_ash}, ...) to the held batch; false when it could not. */
    boolean apply(ItemStack held, String operationId);
}
