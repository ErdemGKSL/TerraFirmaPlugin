/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.heat;

import dev.tfp.core.charcoalforge.CharcoalForgeRules;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.CustomBlocks;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import org.bukkit.block.Block;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * M4-FORGE-01 (first heating glue): a right-click on a lit charcoal forge heats the item in the main hand one step
 * toward the forge's temperature ({@link ItemHeater}). The forge's temperature is a stand-in until its block entity keeps
 * the real one: the temperature of the {@code heat_level} it shows ({@link CharcoalForgeRules#levelTemperature}). An item
 * that cannot be heated, or a cold forge (level 0), does nothing and the click goes on. Runs on the region thread of the
 * clicked block (a Bukkit interaction event), where the CraftEngine state read and the stack write belong.
 */
public final class ForgeHeatListener implements Listener {
    public static final String FORGE_ID = "tfp:charcoal_forge";
    public static final String HEAT_LEVEL = "heat_level";

    private final Supplier<CustomBlocks> blocks;
    private final Supplier<ItemHeater> heater;

    public ForgeHeatListener(Supplier<CustomBlocks> blocks, Supplier<ItemHeater> heater) {
        this.blocks = Objects.requireNonNull(blocks, "blocks");
        this.heater = Objects.requireNonNull(heater, "heater");
    }

    /** The heat level shown by the forge at {@code block}, or empty when the block is not a charcoal forge. */
    Optional<Integer> forgeLevel(Block block) {
        CustomBlocks custom = blocks.get();
        if (custom == null) {
            return Optional.empty();
        }
        Optional<BlockStateRef> state = custom.stateAt(block);
        if (state.isEmpty() || !FORGE_ID.equals(state.get().id())) {
            return Optional.empty();
        }
        String level = state.get().properties().get(HEAT_LEVEL);
        try {
            return level == null ? Optional.empty() : Optional.of(Integer.parseInt(level));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND
                || event.getClickedBlock() == null) {
            return;
        }
        ItemHeater h = heater.get();
        ItemStack held = event.getItem();
        if (h == null || held == null || held.getType().isAir()) {
            return;
        }
        Optional<Integer> level = forgeLevel(event.getClickedBlock());
        if (level.isEmpty() || level.get() <= 0) {
            return;
        }
        if (h.heatToward(held, CharcoalForgeRules.levelTemperature(level.get())).isPresent()) {
            event.getPlayer().getInventory().setItemInMainHand(held);
            event.setUseInteractedBlock(Event.Result.DENY);
            event.setUseItemInHand(Event.Result.DENY);
        }
    }
}
