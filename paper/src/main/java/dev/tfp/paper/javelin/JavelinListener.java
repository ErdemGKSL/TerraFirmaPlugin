/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/items/JavelinItem.java @ c3b4d9536,
 *       src/main/java/net/dries007/tfc/common/entities/misc/ThrownJavelin.java @ c3b4d9536 (onHitEntity)
 */
package dev.tfp.paper.javelin;

import dev.tfp.core.javelin.JavelinDamage;
import java.util.Optional;
import java.util.function.Function;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Trident;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;

/**
 * PAR-TOOL-05: TFC javelins are trident items ({@code tfp:item/javelin}), so vanilla does the use animation, the 10 tick
 * charge, the throw, the durability point, the refusal at {@code maxDamage - 1}, the pickup and the creative-only pickup in
 * creative (TFC's {@code JavelinItem} is a copy of {@code TridentItem}). What differs is the damage: TFC deals a flat
 * {@code 1.5 x tier attack bonus}, not the trident's speed-scaled 8, so a trident that carries a javelin gets that damage.
 */
public final class JavelinListener implements Listener {
    private final Function<ItemStack, Optional<String>> idOf;

    /** @param idOf the custom item id of a stack */
    public JavelinListener(Function<ItemStack, Optional<String>> idOf) {
        this.idOf = idOf;
    }

    /** Whether an item id is one of TFC's javelins ({@code tfp:stone/javelin/..}, {@code tfp:obsidian_javelin}, {@code tfp:metal/javelin/..}). */
    public static boolean isJavelinId(String id) {
        return id != null && (id.startsWith("tfp:stone/javelin/") || id.startsWith("tfp:metal/javelin/")
            || id.equals("tfp:obsidian_javelin"));
    }

    /** The thrown damage of a javelin stack from its attack modifier, empty when the stack is not a javelin. */
    public Optional<Float> thrownDamage(ItemStack stack) {
        if (stack == null || !idOf.apply(stack).filter(JavelinListener::isJavelinId).isPresent() || !stack.hasItemMeta()) {
            return Optional.empty();
        }
        double amount = 0;
        var mods = stack.getItemMeta().getAttributeModifiers(Attribute.ATTACK_DAMAGE);
        if (mods != null) {
            for (AttributeModifier m : mods) {
                if (m.getOperation() == AttributeModifier.Operation.ADD_NUMBER) {
                    amount += m.getAmount();
                }
            }
        }
        return amount > 0 ? Optional.of(JavelinDamage.thrownDamage(amount)) : Optional.empty();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Trident trident) {
            thrownDamage(trident.getItemStack()).ifPresent(d -> event.setDamage(d));
        }
    }
}
