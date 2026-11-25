/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.guide;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Opens the field guide when a player uses the {@value #ITEM} item (PAR-DATA-04): right click in the air or on a block,
 * in either hand. The use is consumed (no block interaction, no placement). Every game mode may read the guide; a
 * spectator cannot use items, as in vanilla. Runs on the player's thread; opening only reads immutable books.
 */
public final class FieldGuideListener implements Listener {
    public static final String ITEM = "tfp:field_guide";

    private final Function<ItemStack, Optional<String>> idOf;
    private final Consumer<Player> open;

    public FieldGuideListener(Function<ItemStack, Optional<String>> idOf, Consumer<Player> open) {
        this.idOf = idOf;
        this.open = open;
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onUse(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (event.useItemInHand() == Event.Result.DENY) {
            return;
        }
        ItemStack item = event.getItem();
        if (item == null || item.isEmpty() || !idOf.apply(item).map(ITEM::equals).orElse(false)) {
            return;
        }
        event.setUseInteractedBlock(Event.Result.DENY);
        event.setUseItemInHand(Event.Result.DENY);
        open.accept(event.getPlayer());
    }
}
