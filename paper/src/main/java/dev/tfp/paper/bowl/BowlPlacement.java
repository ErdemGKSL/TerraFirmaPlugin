/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/util/BlockItemPlacement.java @ c3b4d9536 (onItemUse, canPlace),
 *       src/main/java/net/dries007/tfc/util/InteractionManager.java @ c3b4d9536 (WOODEN_BOWL: Items.BOWL places the wooden bowl),
 *       src/main/java/net/dries007/tfc/common/blocks/devices/BottomSupportedDeviceBlock.java @ c3b4d9536 (getStateForPlacement)
 */
package dev.tfp.paper.bowl;

import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.CustomBlocks;
import java.util.Objects;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockSupport;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * PAR-POT-09: the vanilla bowl is TFC's wooden bowl item ({@code BlockItemPlacement(Items.BOWL, WOODEN_BOWL)}): a right click
 * on a block without sneaking (sneaking opens the salad screen, PAR-PROC-14) puts {@code tfp:wooden_bowl} on the clicked face
 * when that is free and the block below has a sturdy top. The ceramic bowl is a CraftEngine block item and needs no listener.
 * Adventure and spectator players place nothing ({@code mayBuild}).
 */
public final class BowlPlacement implements Listener {
    private final CustomBlocks blocks;
    private final BowlEffects effects;

    public BowlPlacement(CustomBlocks blocks, BowlEffects effects) {
        this.blocks = Objects.requireNonNull(blocks, "blocks");
        this.effects = Objects.requireNonNull(effects, "effects");
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
        if (clicked == null || held == null || held.getType() != Material.BOWL || player.isSneaking()
                || mode == GameMode.SPECTATOR || mode == GameMode.ADVENTURE
                || !Bukkit.isOwnedByCurrentRegion(clicked.getLocation())) {
            return;
        }
        if (ownInteraction(clicked)) {
            return;
        }
        Block target = clicked.isReplaceable() && blocks.stateAt(clicked).isEmpty() ? clicked : clicked.getRelative(event.getBlockFace());
        if (!canPlace(target) || !blocks.place(target, BlockStateRef.of(BowlDevices.WOODEN), true)) {
            return;
        }
        event.setUseInteractedBlock(Event.Result.DENY);
        event.setUseItemInHand(Event.Result.DENY);
        player.swingHand(event.getHand());
        effects.placed(target, true);
        if (mode != GameMode.CREATIVE) {
            consume(player, event.getHand(), held);
        }
    }

    /** A custom block or a vanilla block with a use of its own consumes the click first. */
    private boolean ownInteraction(Block clicked) {
        return blocks.stateAt(clicked).isPresent() || clicked.getType().isInteractable();
    }

    /** {@code BlockItemPlacement.canPlace}: the spot is free and the floor sturdy. */
    boolean canPlace(Block target) {
        if (!target.isReplaceable() || blocks.stateAt(target).isPresent()) {
            return false;
        }
        Block below = target.getRelative(BlockFace.DOWN);
        try {
            return below.getBlockData().isFaceSturdy(BlockFace.UP, BlockSupport.FULL);
        } catch (RuntimeException e) {
            return below.getType().isSolid();
        }
    }

    private static void consume(Player player, EquipmentSlot hand, ItemStack held) {
        ItemStack rest = held.clone();
        rest.setAmount(held.getAmount() - 1);
        player.getInventory().setItem(hand, rest.getAmount() <= 0 ? null : rest);
    }
}
