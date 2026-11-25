/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/util/InteractionManager.java @ c3b4d9536 (doIngotPiling: the sneak use of an ingot),
 *       src/main/java/net/dries007/tfc/common/blocks/devices/IngotPileBlock.java @ c3b4d9536 (onDestroyedByPlayer)
 */
package dev.tfp.paper.pile;

import dev.tfp.core.pile.PileKind;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiPredicate;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * PAR-METAL-16: a sneak right click with an ingot ({@code c:ingots}) or a double ingot ({@code c:double_ingots}) adds it to the
 * clicked pile, to the first pile of the column with room, or starts a new pile (TFC's {@code doIngotPiling}, run before the
 * clicked block's own use, as the event's {@code RightClickBlock} registration). Adventure and spectator players cannot build
 * (TFC {@code mayBuild}). A creative player breaking a pile voids its ingots. Threads: the player's thread, which owns the
 * clicked block; the column is one chunk.
 */
final class PileListener implements Listener {
    private final PileService service;
    private final BiPredicate<ItemStack, String> tagged;

    PileListener(PileService service, BiPredicate<ItemStack, String> tagged) {
        this.service = Objects.requireNonNull(service, "service");
        this.tagged = Objects.requireNonNull(tagged, "tagged");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() == null
                || event.useInteractedBlock() == Event.Result.DENY) {
            return;
        }
        Player player = event.getPlayer();
        GameMode mode = player.getGameMode();
        Block clicked = event.getClickedBlock();
        ItemStack held = event.getItem();
        if (clicked == null || held == null || !player.isSneaking() || mode == GameMode.SPECTATOR || mode == GameMode.ADVENTURE
                || !Bukkit.isOwnedByCurrentRegion(clicked.getLocation())) {
            return;
        }
        Optional<PileKind> kind = service.kindOf(held, tagged);
        if (kind.isEmpty()) {
            return;
        }
        PileService.Outcome outcome = service.add(player, event.getHand(), held, kind.get(), clicked, event.getBlockFace());
        if (outcome == PileService.Outcome.PASS) {
            return;
        }
        event.setUseInteractedBlock(Event.Result.DENY);
        event.setUseItemInHand(Event.Result.DENY);
        if (outcome == PileService.Outcome.DONE) {
            player.swingHand(event.getHand() == EquipmentSlot.OFF_HAND ? EquipmentSlot.OFF_HAND : EquipmentSlot.HAND);
        }
    }

    /** TFC {@code onDestroyedByPlayer}: in creative the contents are voided so breaking the block drops nothing. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (event.getPlayer().getGameMode() == GameMode.CREATIVE && service.kindAt(event.getBlock()).isPresent()) {
            service.markVoid(event.getBlock());
        }
    }
}
