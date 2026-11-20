/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/soil/DirtBlock.java @ c3b4d9536 (getToolModifiedState, useItemOn),
 *       src/main/java/net/dries007/tfc/common/blocks/soil/IMudBlock.java @ c3b4d9536 (transformToMud),
 *       src/main/java/net/dries007/tfc/common/blocks/soil/ConnectedGrassBlock.java @ c3b4d9536 (getToolModifiedState),
 *       src/main/java/net/dries007/tfc/common/blocks/soil/TFCRootedDirtBlock.java @ c3b4d9536 (getToolModifiedState, useItemOn)
 */
package dev.tfp.paper.soil;

import dev.tfp.core.fluid.FluidContainerRules;
import dev.tfp.core.soil.SoilTable;
import dev.tfp.core.soil.SoilTable.Tool;
import dev.tfp.core.vessel.FluidAmount;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.itemdata.FluidPdc;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Particle;
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
 * PAR-GATHER-05: a hoe tills TFC dirt, clay and grass to farmland (and rooted dirt back to dirt), a shovel flattens them to a
 * path, and a container holding 100 mB of fresh or salt water turns dirt, clay and rooted dirt to mud ({@link SoilTable}).
 * Tools need a clicked face other than down and air above; a tool loses one durability (not in creative) and the vanilla
 * hoe or shovel sound plays. The water conversion drains the container (creative too, as TFC's fluid handler) and
 * splashes five particles, an explicit action. Adventure and spectator do nothing. A target block that is not loaded (the
 * farmland and path blocks come with their own items) leaves the click to vanilla.
 *
 * <p>Threads: the event runs on the thread that owns the player; one block per click, changed inline when that thread also
 * owns the block's chunk, no async decide step needed.
 */
public final class SoilListener implements Listener {
    private final CustomBlocks blocks;
    private final Predicate<ItemStack> hoes;
    private final Predicate<ItemStack> shovels;
    /** Whether a fluid id is {@code #tfc:any_fresh_water} or {@code #tfc:salt_water}. */
    private final Predicate<String> mudWater;
    private final SoilTable.Config config;
    private final BiConsumer<Player, EquipmentSlot> damage;

    public SoilListener(CustomBlocks blocks, Predicate<ItemStack> hoes, Predicate<ItemStack> shovels, Predicate<String> mudWater,
            SoilTable.Config config) {
        this(blocks, hoes, shovels, mudWater, config, (player, hand) -> player.damageItemStack(hand, 1));
    }

    /** @param damage takes one durability from the item in the hand; a seam because MockBukkit does not implement it */
    SoilListener(CustomBlocks blocks, Predicate<ItemStack> hoes, Predicate<ItemStack> shovels, Predicate<String> mudWater,
            SoilTable.Config config, BiConsumer<Player, EquipmentSlot> damage) {
        this.blocks = Objects.requireNonNull(blocks, "blocks");
        this.hoes = Objects.requireNonNull(hoes, "hoes");
        this.shovels = Objects.requireNonNull(shovels, "shovels");
        this.mudWater = Objects.requireNonNull(mudWater, "mudWater");
        this.config = Objects.requireNonNull(config, "config");
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
                || player.getGameMode() == GameMode.ADVENTURE || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        Optional<BlockStateRef> state = blocks.stateAt(clicked);
        if (state.isEmpty()) {
            return;
        }
        String id = state.get().id();
        if (water(player, event.getHand(), clicked, id, item) || tool(player, event.getHand(), clicked, event.getBlockFace(), id, item)) {
            event.setUseInteractedBlock(Event.Result.DENY);
            event.setUseItemInHand(Event.Result.DENY);
        }
    }

    /** {@code IMudBlock.transformToMud}: 100 mB of fresh or salt water from the held container. Block owner thread. */
    boolean water(Player player, EquipmentSlot hand, Block clicked, String id, ItemStack item) {
        Optional<String> mud = SoilTable.mudOf(id, config);
        if (mud.isEmpty() || item.getAmount() != 1 || !blocks.exists(mud.get())) {
            return false;
        }
        FluidAmount content = FluidPdc.content(item);
        if (content.isEmpty() || content.amount() < SoilTable.MUD_WATER || !mudWater.test(content.fluid())) {
            return false;
        }
        FluidContainerRules.Drain drained = FluidContainerRules.drain(content, SoilTable.MUD_WATER);
        if (!FluidPdc.write(item, drained.content()) || !blocks.place(clicked, BlockStateRef.of(mud.get()), true)) {
            return false;
        }
        player.getInventory().setItem(hand, item);
        clicked.getWorld().playSound(clicked.getLocation().toCenterLocation(), Sound.ITEM_BUCKET_FILL, SoundCategory.BLOCKS, 1f, 1f);
        clicked.getWorld().spawnParticle(Particle.SPLASH, clicked.getLocation().toCenterLocation().add(0, 0.5, 0), 5, 0.5, 0, 0.5, 0);
        player.swingHand(hand);
        return true;
    }

    /** Hoe tilling and shovel flattening. Block owner thread. */
    boolean tool(Player player, EquipmentSlot hand, Block clicked, BlockFace face, String id, ItemStack item) {
        Tool tool = hoes.test(item) ? Tool.HOE : shovels.test(item) ? Tool.SHOVEL : null;
        if (tool == null || face == BlockFace.DOWN) {
            return false;
        }
        Optional<String> target = SoilTable.toolResult(id, tool, config);
        if (target.isEmpty() || !blocks.exists(target.get())) {
            return false;
        }
        Block above = clicked.getRelative(BlockFace.UP);
        if (SoilTable.needsAirAbove(id, tool) && !above.getType().isAir()) {
            return false;
        }
        if (!blocks.place(clicked, BlockStateRef.of(target.get()), true)) {
            return false;
        }
        clicked.getWorld().playSound(clicked.getLocation().toCenterLocation(),
            tool == Tool.HOE ? Sound.ITEM_HOE_TILL : Sound.ITEM_SHOVEL_FLATTEN, SoundCategory.BLOCKS, 1f, 1f);
        player.swingHand(hand);
        if (player.getGameMode() != GameMode.CREATIVE) {
            damage.accept(player, hand);
        }
        return true;
    }
}
