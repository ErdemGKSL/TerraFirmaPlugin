/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/items/MoldItem.java @ c3b4d9536 (use, overrideOtherStackedOnMe),
 *       src/main/java/net/dries007/tfc/common/items/VesselItem.java @ c3b4d9536 (use: molten alloy opens the pour screen)
 */
package dev.tfp.paper.mold;

import dev.tfp.core.vessel.MoldRules;
import dev.tfp.paper.gui.GuiService;
import dev.tfp.paper.gui.GuiSession;
import dev.tfp.paper.i18n.Messages;
import java.util.Objects;
import java.util.function.DoubleSupplier;
import net.kyori.adventure.text.Component;
import org.bukkit.GameMode;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * PAR-POT-05: the mold's events.
 * <ul>
 *   <li>right-click a molten vessel or a molten mold (not sneaking): the pour screen; sneaking with a molten vessel and a
 *       fired mold in the other hand pours at once (a plugin shortcut, ledger);</li>
 *   <li>sneak + right-click a mold that holds a cooled casting: the casting is given to the player, the mold is drained and
 *       breaks with the recipe's break chance; a molten mold only says so;</li>
 *   <li>right-click a cooled mold in an inventory slot: the casting goes onto the cursor (TFC
 *       {@code overrideOtherStackedOnMe}), not in creative mode.</li>
 * </ul>
 * Runs at normal priority, before the vessel's own handler at high priority, so a molten vessel opens the pour screen.
 */
public final class MoldListener implements Listener {
    private final MoldItems molds;
    private final MoldPourer pourer;
    private final MoldScreens screens;
    private final Messages messages;
    private final DoubleSupplier random;
    /** Player and the tick-time (ms) of the last shortcut pour: the off hand event of the same click is ignored. */
    private final java.util.Map<java.util.UUID, Long> poured = new java.util.concurrent.ConcurrentHashMap<>();

    public MoldListener(MoldItems molds, MoldPourer pourer, MoldScreens screens, Messages messages, DoubleSupplier random) {
        this.molds = Objects.requireNonNull(molds, "molds");
        this.pourer = Objects.requireNonNull(pourer, "pourer");
        this.screens = Objects.requireNonNull(screens, "screens");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.random = Objects.requireNonNull(random, "random");
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onInteract(org.bukkit.event.player.PlayerInteractEvent e) {
        if ((e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK)
            || e.getHand() == null || e.useItemInHand() == Event.Result.DENY) {
            return;
        }
        ItemStack held = e.getItem();
        if (held == null || held.isEmpty() || !pourer.isSource(held)) {
            return;
        }
        Long last = poured.get(e.getPlayer().getUniqueId());
        if (last != null && System.currentTimeMillis() - last < 150) {
            e.setCancelled(true); // the other hand's event of the click that just poured
            return;
        }
        if (e.getAction() == Action.RIGHT_CLICK_BLOCK && e.getClickedBlock() != null
            && e.getClickedBlock().getType().isInteractable() && !e.getPlayer().isSneaking()) {
            return; // a block that takes the click (a door, a chest) wins, as in vanilla
        }
        Player player = e.getPlayer();
        boolean isMold = molds.isMold(held);
        if (player.isSneaking()) {
            if (isMold && cast(player, e.getHand(), held)) {
                e.setCancelled(true);
            } else if (!isMold && pourAtOnce(player, e.getHand(), held)) {
                e.setCancelled(true);
            }
            return;
        }
        if (!pourer.isMolten(held)) {
            if (isMold && !molds.content(held).isEmpty()) {
                player.sendActionBar(Component.text(messages.get(player, "mold.alloy_solid")));
                e.setCancelled(true);
            }
            return;
        }
        if (screens.open(player, e.getHand()) != null) {
            e.setCancelled(true);
        }
    }

    /** TFC {@code MoldItem.use} while sneaking: un-mold. */
    private boolean cast(Player player, EquipmentSlot hand, ItemStack mold) {
        if (molds.content(mold).isEmpty()) {
            return false;
        }
        if (molds.isMolten(mold)) {
            player.sendActionBar(Component.text(messages.get(player, "mold.alloy_molten")));
            return true;
        }
        MoldItems.Cast cast = molds.cast(mold).orElse(null);
        if (cast == null) {
            return false;
        }
        ItemStack kept = mold.clone();
        molds.drain(kept);
        boolean breaks = MoldRules.breaks((float) random.getAsDouble(), cast.breakChance());
        PlayerInventory inv = player.getInventory();
        // the result goes first, as in TFC (giveItemToPlayer), then the mold is shrunk or kept
        for (ItemStack rest : inv.addItem(cast.result().clone()).values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), rest);
        }
        if (breaks) {
            inv.setItem(hand, null);
            playBreak(player);
        } else {
            inv.setItem(hand, kept);
        }
        return true;
    }

    /** Sneaking with a molten vessel (or mold) and a fired mold in the other hand: pours until the mold is full. */
    private boolean pourAtOnce(Player player, EquipmentSlot hand, ItemStack source) {
        PlayerInventory inv = player.getInventory();
        EquipmentSlot other = hand == EquipmentSlot.HAND ? EquipmentSlot.OFF_HAND : EquipmentSlot.HAND;
        ItemStack target = inv.getItem(other);
        if (target == null || !molds.isMold(target) || target.getAmount() != 1 || !pourer.isMolten(source)) {
            return false;
        }
        int amount = pourer.pour(source, target, Integer.MAX_VALUE);
        if (amount == 0) {
            return false;
        }
        inv.setItem(hand, source);
        inv.setItem(other, target);
        poured.put(player.getUniqueId(), System.currentTimeMillis());
        return true;
    }

    private void playBreak(Player player) {
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_DECORATED_POT_SHATTER, 1f, 1f);
    }

    @EventHandler
    public void onQuit(org.bukkit.event.player.PlayerQuitEvent e) {
        poured.remove(e.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) {
            return;
        }
        GuiSession session = GuiService.session(e.getView().getTopInventory());
        if (screens.isPourScreen(session)) {
            if (touchesSource(e, player, screens.sourceIndex(session))) {
                e.setCancelled(true);
            }
            return;
        }
        if (e.getClick() != ClickType.RIGHT || player.getGameMode() == GameMode.CREATIVE) {
            return;
        }
        ItemStack slot = e.getCurrentItem();
        if (slot == null || slot.isEmpty() || !molds.isMold(slot) || molds.content(slot).isEmpty() || molds.isMolten(slot)) {
            return;
        }
        MoldItems.Cast cast = molds.cast(slot).orElse(null);
        if (cast == null) {
            return;
        }
        ItemStack cursor = e.getCursor();
        ItemStack result = cast.result();
        boolean noCarry = cursor == null || cursor.isEmpty();
        boolean stackable = !noCarry && cursor.isSimilar(result)
            && result.getAmount() + cursor.getAmount() <= cursor.getMaxStackSize();
        if (!noCarry && !stackable) {
            return;
        }
        e.setCancelled(true);
        ItemStack kept = slot.clone();
        molds.drain(kept);
        if (noCarry) {
            e.getView().setCursor(result.clone());
        } else {
            ItemStack grown = cursor.clone();
            grown.setAmount(cursor.getAmount() + result.getAmount());
            e.getView().setCursor(grown);
        }
        if (MoldRules.breaks((float) random.getAsDouble(), cast.breakChance())) {
            e.setCurrentItem(null);
            playBreak(player);
        } else {
            e.setCurrentItem(kept);
        }
    }

    /** Whether the click would take the source out of its slot or put something on it. */
    static boolean touchesSource(InventoryClickEvent e, Player player, int index) {
        if (index < 0) {
            return false;
        }
        if (e.getClickedInventory() != null && e.getClickedInventory().equals(player.getInventory()) && e.getSlot() == index) {
            return true;
        }
        if (e.getClick() == ClickType.NUMBER_KEY && e.getHotbarButton() == index) {
            return true;
        }
        return e.getClick() == ClickType.SWAP_OFFHAND && index == MoldScreens.OFF_HAND_INDEX;
    }
}
