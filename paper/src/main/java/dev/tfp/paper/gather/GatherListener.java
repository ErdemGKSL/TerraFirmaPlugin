/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/GroundcoverBlock.java @ c3b4d9536 (useItemOn),
 *       src/main/java/net/dries007/tfc/common/blocks/rock/LooseRockBlock.java @ c3b4d9536 (useItemOn, canSurvive)
 */
package dev.tfp.paper.gather;

import dev.tfp.core.gather.GatherPiece;
import dev.tfp.core.gather.GatherPieces;
import dev.tfp.core.gather.GatherRules;
import dev.tfp.core.gather.GatherYield;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.integration.CustomItems;
import java.util.Objects;
import java.util.Optional;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
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
 * PAR-GATHER-01: right-clicking a loose rock or a groundcover piece picks it up ({@link GatherRules#pickup}): the drops go
 * into the player's inventory (the rest at their feet), nothing in creative, and the block is removed whatever is in the
 * hand. Holding the matching loose rock item in the main hand adds one more rock to a block of fewer than three instead,
 * when the block below can carry it ({@code LooseRockBlock.useItemOn}). Sneaking with an item in either hand leaves the click
 * to vanilla, as the block use is skipped there.
 *
 * <p>Threads: the event runs on the thread that owns the player; one block per click, changed inline when that thread also
 * owns the block's chunk.
 */
public final class GatherListener implements Listener {
    private final CustomBlocks blocks;
    private final CustomItems items;

    public GatherListener(CustomBlocks blocks, CustomItems items) {
        this.blocks = Objects.requireNonNull(blocks, "blocks");
        this.items = Objects.requireNonNull(items, "items");
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND
                || event.useInteractedBlock() == Event.Result.DENY) {
            return;
        }
        Player player = event.getPlayer();
        Block clicked = event.getClickedBlock();
        if (clicked == null || player.getGameMode() == GameMode.SPECTATOR
                || !Bukkit.isOwnedByCurrentRegion(clicked.getLocation())) {
            return;
        }
        ItemStack held = event.getItem();
        boolean holding = held != null && !held.getType().isAir();
        if (player.isSneaking() && (holding || !player.getInventory().getItemInOffHand().getType().isAir())) {
            return;
        }
        Optional<BlockStateRef> state = blocks.stateAt(clicked);
        if (state.isEmpty()) {
            return;
        }
        Optional<GatherPiece> piece = GatherPieces.of(state.get().id(), state.get().properties());
        if (piece.isEmpty()) {
            return;
        }
        boolean handled = holding && piece.get() instanceof GatherPiece.LooseRock rock
                && stack(player, clicked, state.get(), rock, held)
            || pickUp(player, clicked, piece.get());
        if (handled) {
            event.setUseInteractedBlock(Event.Result.DENY);
            event.setUseItemInHand(Event.Result.DENY);
        }
    }

    /** {@code LooseRockBlock.useItemOn}: one more rock from the held stack. Block owner thread. */
    boolean stack(Player player, Block block, BlockStateRef state, GatherPiece.LooseRock rock, ItemStack held) {
        Optional<String> id = items.idOf(held);
        if (id.isEmpty()) {
            return false;
        }
        Optional<Integer> next = GatherPieces.stack(rock, id.get());
        if (next.isEmpty() || !block.getRelative(BlockFace.DOWN).getType().isSolid()) {
            return false;
        }
        if (!blocks.place(block, state.with("count", Integer.toString(next.get())), true)) {
            return false;
        }
        block.getWorld().playSound(block.getLocation().toCenterLocation(), Sound.BLOCK_STONE_PLACE, SoundCategory.BLOCKS, 1f, 1f);
        player.swingHand(EquipmentSlot.HAND);
        if (player.getGameMode() != GameMode.CREATIVE) {
            ItemStack rest = held.clone();
            rest.setAmount(held.getAmount() - 1);
            player.getInventory().setItemInMainHand(rest.getAmount() <= 0 ? null : rest);
        }
        return true;
    }

    /** {@code GroundcoverBlock.useItemOn}: give the drops, remove the block. Block owner thread. */
    boolean pickUp(Player player, Block block, GatherPiece piece) {
        boolean creative = player.getGameMode() == GameMode.CREATIVE;
        for (GatherYield y : GatherRules.pickup(piece, creative)) {
            ItemStack stack = stackOf(GatherPieces.ourItem(y), y.count());
            if (stack == null) {
                continue;
            }
            for (ItemStack rest : player.getInventory().addItem(stack).values()) {
                player.getWorld().dropItem(player.getLocation(), rest);
            }
        }
        if (!blocks.remove(block)) {
            return false;
        }
        player.swingHand(EquipmentSlot.HAND);
        return true;
    }

    private ItemStack stackOf(String id, int count) {
        if (id.startsWith("minecraft:")) {
            Material m = Material.matchMaterial(id);
            return m == null ? null : new ItemStack(m, count);
        }
        return items.create(id, count).orElse(null);
    }
}
