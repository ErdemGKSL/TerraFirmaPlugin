/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/BowlBlock.java @ c3b4d9536 (useItemOn: glass powder)
 */
package dev.tfp.paper.glass;

import dev.tfp.core.glass.GlassOperation;
import dev.tfp.core.glass.GlassRules;
import dev.tfp.core.glass.GlassState;
import dev.tfp.paper.bowl.BowlGlass;
import org.bukkit.inventory.ItemStack;

/** PAR-PROC-16: the glassworking side of the powder bowl on the item's glass record ({@link GlassPdc}). */
final class GlassBowl implements BowlGlass {
    @Override
    public boolean working(ItemStack held) {
        return GlassPdc.read(held).isPresent();
    }

    @Override
    public boolean apply(ItemStack held, String operationId) {
        GlassState state = GlassPdc.read(held).orElse(null);
        GlassOperation op = GlassOperation.byId(operationId).orElse(null);
        if (state == null || op == null) {
            return false;
        }
        return GlassPdc.write(held, GlassRules.apply(state, op));
    }
}
