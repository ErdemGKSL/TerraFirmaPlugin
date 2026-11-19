/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/ForgeEventHandler.java @ c3b4d9536 (onShieldBlock)
 */
package dev.tfp.paper.shield;

import dev.tfp.core.shield.ShieldRules;
import java.util.Optional;
import java.util.function.Function;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.inventory.ItemStack;

/**
 * PAR-TOOL-07: TFC scales the damage a shield blocks (not for projectiles): 0.25 for the vanilla shield, and
 * {@code clampedMap(tier bonus, 0, 12, 0.25, 1)} for a metal shield. The vanilla block takes all of the hit; here the blocked
 * amount (the BLOCKING modifier) is multiplied by that factor, so the rest goes through.
 */
@SuppressWarnings("deprecation")
public final class ShieldListener implements Listener {
    private final Function<ItemStack, Optional<String>> idOf;

    /** @param idOf the custom item id of a stack */
    public ShieldListener(Function<ItemStack, Optional<String>> idOf) {
        this.idOf = idOf;
    }

    /** The metal of a TFC shield id ({@code tfp:metal/shield/copper} gives {@code copper}). */
    public static Optional<String> metal(String id) {
        return id != null && id.startsWith("tfp:metal/shield/") ? Optional.of(id.substring("tfp:metal/shield/".length()))
            : Optional.empty();
    }

    /** The factor for the held shield stack: metal shield, vanilla shield, or empty for no shield. */
    public Optional<Float> factor(ItemStack stack) {
        if (stack == null) {
            return Optional.empty();
        }
        Optional<String> metal = idOf.apply(stack).flatMap(ShieldListener::metal);
        if (metal.isPresent()) {
            return Optional.of(ShieldRules.factorForMetal(metal.get()));
        }
        return stack.getType() == Material.SHIELD ? Optional.of(ShieldRules.VANILLA_FACTOR) : Optional.empty();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || event.getCause() == DamageCause.PROJECTILE
                || !event.isApplicable(EntityDamageEvent.DamageModifier.BLOCKING)) {
            return;
        }
        double blocked = event.getDamage(EntityDamageEvent.DamageModifier.BLOCKING);
        if (blocked == 0) {
            return;
        }
        factor(player.getActiveItem()).ifPresent(f -> event.setDamage(EntityDamageEvent.DamageModifier.BLOCKING, blocked * f));
    }
}
