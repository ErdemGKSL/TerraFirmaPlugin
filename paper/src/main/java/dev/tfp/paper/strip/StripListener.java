/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/wood/LogBlock.java @ c3b4d9536 (getToolModifiedState, AXE_STRIP),
 *       src/main/java/net/dries007/tfc/common/blocks/BambooBlock.java @ c3b4d9536
 */
package dev.tfp.paper.strip;

import dev.tfp.core.strip.StripTable;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.CustomBlocks;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Orientable;
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
 * PAR-GATHER-08: right-clicking one of TFC's logs or woods (or the golden bamboo block) with any item in {@code #minecraft:axes}
 * strips it as vanilla's axe does, using {@link StripTable} (axis and branch direction kept): the strip sound, one durability
 * (not in creative) and the arm swing. Adventure and spectator do nothing, like vanilla's can-build rule.
 *
 * <p>Threads: the event runs on the thread that owns the player; the single block is changed inline when that thread also owns
 * the block's chunk (always on Paper, normal within reach on Folia), else the click is left to vanilla. One block per click does
 * not need the async decide step.
 */
public final class StripListener implements Listener {
    private final CustomBlocks blocks;
    private final Predicate<ItemStack> axes;
    private final BiConsumer<Player, EquipmentSlot> damage;

    /**
     * @param axes whether a stack is in {@code #minecraft:axes} (any thread)
     */
    public StripListener(CustomBlocks blocks, Predicate<ItemStack> axes) {
        this(blocks, axes, (player, hand) -> player.damageItemStack(hand, 1));
    }

    /**
     * @param damage takes one durability from the item in the hand (unbreaking and breaking as vanilla); a seam because
     *               MockBukkit does not implement it
     */
    StripListener(CustomBlocks blocks, Predicate<ItemStack> axes, BiConsumer<Player, EquipmentSlot> damage) {
        this.blocks = Objects.requireNonNull(blocks, "blocks");
        this.axes = Objects.requireNonNull(axes, "axes");
        this.damage = Objects.requireNonNull(damage, "damage");
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() == null
                || event.useItemInHand() == Event.Result.DENY || event.useInteractedBlock() == Event.Result.DENY) {
            return;
        }
        Player player = event.getPlayer();
        Block clicked = event.getClickedBlock();
        ItemStack item = event.getItem();
        if (clicked == null || item == null || item.getType().isAir() || !Bukkit.isOwnedByCurrentRegion(clicked.getLocation())
                || player.getGameMode() == GameMode.ADVENTURE || player.getGameMode() == GameMode.SPECTATOR
                || !axes.test(item)) {
            return;
        }
        if (strip(player, event.getHand(), clicked, item)) {
            event.setUseInteractedBlock(Event.Result.DENY);
            event.setUseItemInHand(Event.Result.DENY);
        }
    }

    /** Strips {@code clicked} when the table has it; returns whether the click was handled. Block owner thread. */
    boolean strip(Player player, EquipmentSlot hand, Block clicked, ItemStack item) {
        Optional<BlockStateRef> state = blocks.stateAt(clicked);
        if (state.isEmpty()) {
            return false;
        }
        Optional<String> target = StripTable.stripped(state.get().id());
        if (target.isEmpty()) {
            return false;
        }
        String id = target.get();
        Map<String, String> props = StripTable.copyProperties(id, state.get().properties());
        if (id.startsWith("minecraft:")) {
            Material material = Material.matchMaterial(id);
            if (material == null) {
                return false;
            }
            BlockData next = material.createBlockData();
            if (next instanceof Orientable o && props.containsKey("axis")) {
                o.setAxis(org.bukkit.Axis.valueOf(props.get("axis").toUpperCase(java.util.Locale.ROOT)));
            }
            clicked.setBlockData(next, true);
        } else if (!blocks.place(clicked, BlockStateRef.of(id, props), true)) {
            return false;
        }
        clicked.getWorld().playSound(clicked.getLocation().add(0.5, 0.5, 0.5), Sound.ITEM_AXE_STRIP, SoundCategory.BLOCKS, 1f, 1f);
        player.swingHand(hand);
        if (player.getGameMode() != GameMode.CREATIVE) {
            damage.accept(player, hand);
        }
        return true;
    }
}
