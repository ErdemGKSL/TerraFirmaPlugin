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
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.CustomBlocks;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import net.kyori.adventure.text.Component;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MenuType;
import org.bukkit.inventory.view.AnvilView;

/**
 * The scribing table (PAR-PROC-15). A right click on a {@code tfp:wood/scribing_table/<wood>} block opens vanilla's anvil
 * screen (TFC's own screen is an {@code ItemCombinerMenu} of the same shape: item, ink, result, a name field); the result is
 * written here instead of vanilla's repair logic ({@link ScribingTable#result}), costs no levels, and taking it needs an
 * ink in the second slot (or creative mode) and uses one up ({@link ScribingRules#mayPickup}).
 *
 * <p>Only the screens this listener opened are touched ({@link #open}). Everything runs on the region of the player.
 */
public final class ScribingListener implements Listener {
    public static final String BLOCK_PREFIX = "tfp:wood/scribing_table/";
    static final int SLOT_INPUT = 0;
    static final int SLOT_INK = 1;
    static final int SLOT_RESULT = 2;

    private final CustomBlocks blocks;
    private final Predicate<ItemStack> ink;
    private final Map<UUID, Block> open = new ConcurrentHashMap<>();

    ScribingListener(CustomBlocks blocks, Predicate<ItemStack> ink) {
        this.blocks = blocks;
        this.ink = ink;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND
            || event.getClickedBlock() == null || event.useInteractedBlock() == Event.Result.DENY) {
            return;
        }
        Player player = event.getPlayer();
        Block block = event.getClickedBlock();
        Optional<BlockStateRef> state = blocks.stateAt(block);
        if (state.isEmpty() || !state.get().id().startsWith(BLOCK_PREFIX)) {
            return;
        }
        if (player.isSneaking() && !player.getInventory().getItemInMainHand().getType().isAir()) {
            return; // vanilla: sneaking with an item places it
        }
        event.setCancelled(true);
        AnvilView view = MenuType.ANVIL.builder().title(Component.translatable("tfc.screen.scribing_table"))
            .location(block.getLocation().add(0.5, 0.5, 0.5)).checkReachable(true).build(player);
        open.put(player.getUniqueId(), block);
        player.openInventory(view);
    }

    boolean isOpen(Player player) {
        return open.containsKey(player.getUniqueId());
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        open.remove(event.getPlayer().getUniqueId());
    }

    /** TFC {@code createResult}: the name typed in the field decides the result; no repair logic, no level cost. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepare(PrepareAnvilEvent event) {
        if (!(event.getView().getPlayer() instanceof Player player) || !isOpen(player)) {
            return;
        }
        AnvilView view = event.getView();
        AnvilInventory inv = event.getInventory();
        Optional<ItemStack> result = ScribingTable.result(inv.getFirstItem(), view.getRenameText());
        event.setResult(result.orElse(null));
        view.setRepairCost(0);
    }

    /** TFC {@code mayPickup} and {@code onTake}: handled here, the vanilla take needs levels. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !isOpen(player)
            || !(event.getView() instanceof AnvilView) || event.getRawSlot() != SLOT_RESULT
            || !(event.getView().getTopInventory() instanceof AnvilInventory inv)) {
            return;
        }
        event.setCancelled(true);
        ItemStack result = inv.getResult();
        if (result == null || result.getType().isAir()) {
            return;
        }
        ItemStack inkStack = inv.getSecondItem();
        boolean inkOk = inkStack != null && !inkStack.getType().isAir() && ink.test(inkStack);
        if (!ScribingRules.mayPickup(player.getGameMode() == GameMode.CREATIVE, inkOk)) {
            return;
        }
        if (event.isShiftClick()) {
            for (ItemStack rest : player.getInventory().addItem(result.clone()).values()) {
                player.getWorld().dropItem(player.getLocation(), rest, e -> e.setPickupDelay(0));
            }
        } else if (event.getCursor().getType().isAir()) {
            player.setItemOnCursor(result.clone());
        } else {
            return; // vanilla: a full cursor cannot take the result
        }
        inv.setFirstItem(null);
        inv.setSecondItem(ScribingTable.useInk(inkStack));
        inv.setResult(null);
        Location at = player.getLocation();
        player.getWorld().playSound(at, Sound.UI_LOOM_TAKE_RESULT, SoundCategory.BLOCKS, 1f, 1f);
        player.updateInventory();
    }
}
