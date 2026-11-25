/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/container/ScribingTableContainer.java @ c3b4d9536 (createResult, onTake,
 * mayPickup, isInkInput),
 * file: src/main/java/net/dries007/tfc/common/blocks/wood/ScribingTableBlock.java @ c3b4d9536 (useItemOn)
 */
package dev.tfp.paper.scribing;

import dev.tfp.core.scribing.ScribingRules;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/** The scribing table's item rules on Bukkit stacks (PAR-PROC-15): the result of a typed name and the ink use. */
final class ScribingTable {
    private ScribingTable() {
    }

    /** The item's display name as plain text: its custom name, else its default name ({@code ""} when unknown). */
    static String hoverName(ItemStack stack) {
        ItemMeta meta = stack.getItemMeta();
        Component name = meta != null && meta.hasCustomName() ? meta.customName() : stack.effectiveName();
        return name == null ? "" : PlainTextComponentSerializer.plainText().serialize(name);
    }

    /** TFC {@code createResult}: the renamed (or un-named) copy of {@code input}, empty for no result. */
    static Optional<ItemStack> result(ItemStack input, String typed) {
        if (input == null || input.getType().isAir() || input.getAmount() <= 0) {
            return Optional.empty();
        }
        ItemMeta meta = input.getItemMeta();
        boolean hasName = meta != null && meta.hasCustomName();
        ScribingRules.Rename rename = ScribingRules.rename(hasName, hoverName(input), typed);
        switch (rename.kind()) {
            case NONE:
                return Optional.empty();
            case CLEAR: {
                ItemStack out = input.clone();
                out.editMeta(m -> m.customName(null));
                return Optional.of(out);
            }
            default: {
                ItemStack out = input.clone();
                out.editMeta(m -> m.customName(Component.text(rename.name())));
                return Optional.of(out);
            }
        }
    }

    /** TFC {@code onTake} for a plain ink item: one is used up. Returns the ink slot after the take. */
    static ItemStack useInk(ItemStack ink) {
        if (ink == null || ink.getType().isAir()) {
            return ItemStack.empty();
        }
        ItemStack left = ink.clone();
        left.setAmount(ink.getAmount() - 1);
        return left.getAmount() <= 0 ? ItemStack.empty() : left;
    }
}
