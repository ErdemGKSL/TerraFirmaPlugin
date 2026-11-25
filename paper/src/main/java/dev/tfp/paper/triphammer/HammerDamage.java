/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.triphammer;

import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

/** TFC {@code Helpers.damageItem(stack, level)} without an entity: vanilla's {@code hurtAndBreak(1, level, null, ...)}. */
final class HammerDamage {
    private HammerDamage() {
    }

    /**
     * Unbreaking level n skips the damage with chance n / (n + 1) ({@code roll % (n + 1) != 0}); a stack whose damage reaches its
     * max damage breaks (empty). Undamageable stacks are unchanged. A changed stack is a new copy.
     */
    static ItemStack damageOnce(ItemStack stack, int roll) {
        ItemMeta meta = stack.getItemMeta();
        if (!(meta instanceof Damageable dmg) || !dmg.hasMaxDamage() && stack.getType().getMaxDurability() <= 0) {
            return stack;
        }
        int unbreaking = stack.getEnchantmentLevel(Enchantment.UNBREAKING);
        if (unbreaking > 0 && roll % (unbreaking + 1) != 0) {
            return stack;
        }
        int max = dmg.hasMaxDamage() ? dmg.getMaxDamage() : stack.getType().getMaxDurability();
        int next = dmg.getDamage() + 1;
        if (next >= max) {
            return ItemStack.empty();
        }
        dmg.setDamage(next);
        ItemStack out = stack.clone();
        out.setItemMeta(dmg);
        return out;
    }
}
