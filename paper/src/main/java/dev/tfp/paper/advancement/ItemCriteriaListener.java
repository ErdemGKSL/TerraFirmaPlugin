/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/resources/data/tfc/advancement/story/find_rock.json @ c3b4d9536 (minecraft:inventory_changed)
 */
package dev.tfp.paper.advancement;

import dev.tfp.core.advancement.CriterionRef;
import dev.tfp.core.advancement.ItemCriteria;
import io.papermc.paper.event.player.PlayerInventorySlotChangeEvent;
import java.util.Objects;
import java.util.function.BiPredicate;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;

/**
 * PAR-DATA-02: TFC's {@code minecraft:inventory_changed} criteria of {@link ItemCriteria} on our items. Paper's
 * {@link PlayerInventorySlotChangeEvent} fires from the same container listener as the vanilla trigger, for every way an
 * item gets into the inventory (picking up a loose rock by hand, an item entity, a click, {@code /give}). The tag of the
 * new item is asked of our tag runtime, which knows CraftEngine items; a criterion already earned is skipped, so the
 * common case costs one tag lookup per table entry.
 *
 * <p>Game modes: as vanilla and TFC, every mode in which the inventory changes earns the criterion.
 *
 * <p>Threads: the event runs on the thread that owns the player.
 */
public final class ItemCriteriaListener implements Listener {
    private final ItemCriteria criteria;
    private final BiPredicate<ItemStack, String> inTag;
    private final Advancements advancements;

    /** @param inTag whether a stack is in one of our item tags ({@code Tags::is}) */
    public ItemCriteriaListener(ItemCriteria criteria, BiPredicate<ItemStack, String> inTag, Advancements advancements) {
        this.criteria = Objects.requireNonNull(criteria, "criteria");
        this.inTag = Objects.requireNonNull(inTag, "inTag");
        this.advancements = Objects.requireNonNull(advancements, "advancements");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSlotChange(PlayerInventorySlotChangeEvent event) {
        onItem(event.getPlayer(), event.getNewItemStack());
    }

    /** {@code stack} entered a slot of {@code player}'s inventory. Thread that owns the player. */
    void onItem(Player player, ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return;
        }
        for (CriterionRef c : criteria.earned(tag -> inTag.test(stack, tag), c -> advancements.isAwarded(player, c))) {
            advancements.fire(player, c);
        }
    }
}
