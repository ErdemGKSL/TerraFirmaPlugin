/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/mixin/TadpoleMixin.java @ c3b4d9536 (isFood, ageUp),
 * ForgeEventHandler.java (onMount), same commit
 */
package dev.tfp.paper.worldrules;

import dev.tfp.core.worldrules.WorldRules;
import java.util.function.Predicate;
import org.bukkit.Material;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Tadpole;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Entity rules: tadpoles are not fed (vanilla's slime ball does nothing), a growing tadpole becomes TFC's frog instead of
 * the vanilla frog, and predators never ride boats. Every handler runs on the entity's owning thread and only cancels or
 * hands the tadpole to the {@link FrogConversion}.
 */
final class EntityRulesListener implements Listener {
    /** Replaces a grown tadpole with TFC's frog; false when it cannot (no frog species, calendar not loaded). */
    @FunctionalInterface
    interface FrogConversion {
        boolean convert(Tadpole tadpole);
    }

    private final Predicate<Entity> predator;
    private final FrogConversion frogs;

    EntityRulesListener(Predicate<Entity> predator, FrogConversion frogs) {
        this.predator = predator;
        this.frogs = frogs;
    }

    /** {@code TadpoleMixin.isFood}: vanilla feeds a tadpole with {@code #minecraft:frog_food} (the slime ball). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFeed(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Tadpole) || WorldRules.tadpoleFood()) {
            return;
        }
        ItemStack held = event.getPlayer().getInventory().getItem(event.getHand());
        if (held != null && held.getType() == Material.SLIME_BALL) {
            event.setCancelled(true);
        }
    }

    /** {@code TadpoleMixin.ageUp}: the vanilla conversion is cancelled once TFC's frog takes over. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onGrowUp(EntityTransformEvent event) {
        if (event.getTransformReason() == EntityTransformEvent.TransformReason.METAMORPHOSIS
                && event.getEntity() instanceof Tadpole tadpole && frogs.convert(tadpole)) {
            event.setCancelled(true);
        }
    }

    /** {@code onMount}. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMount(EntityMountEvent event) {
        if (!WorldRules.mayMount(event.getMount() instanceof Boat, predator.test(event.getEntity()))) {
            event.setCancelled(true);
        }
    }
}
