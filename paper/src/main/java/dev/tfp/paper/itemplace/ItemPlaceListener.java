/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/util/InteractionManager.java @ c3b4d9536,
 *       src/main/java/net/dries007/tfc/util/BlockItemPlacement.java @ c3b4d9536
 */
package dev.tfp.paper.itemplace;

import dev.tfp.core.itemplace.ItemPlacementRules;
import dev.tfp.core.itemplace.ItemPlacementRules.Groundcover;
import dev.tfp.core.itemplace.ItemPlacementRules.SnowAction;
import dev.tfp.core.itemplace.ItemPlacementRules.Spot;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.integration.CustomItems;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockSupport;
import org.bukkit.block.data.Levelled;
import org.bukkit.block.data.BlockData;
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
 * PAR-GATHER-07: right-clicking with a vanilla stick, bone, feather, flint, rotten flesh or TFC salt places the TFC groundcover
 * block ({@link ItemPlacementRules#placeGroundcover}); a snow item used on a snow-pileable block places a snow pile
 * ({@link ItemPlacementRules#snow}) through {@link SnowPilePlacer}. The placement is tried after the clicked block's own
 * interaction (a click another listener already denied, or on a block with its own use, is left alone; sneaking skips the
 * block's use). When nothing applies the item keeps its vanilla use (rotten flesh is eaten). Adventure and spectator players
 * cannot build ({@code mayBuild}); creative keeps the stack.
 *
 * <p>Threads: the player's thread, which owns the clicked block; one block per click, written inline.
 */
public final class ItemPlaceListener implements Listener {
    /** The property of a groundcover block that holds a water source ({@code GroundcoverBlock.FLUID}). */
    static final String FLUID_PROPERTY = "fluid";

    private final CustomBlocks blocks;
    private final CustomItems items;
    private final Supplier<Optional<SnowPilePlacer>> snow;
    private final Predicate<Block> snowPileable;

    public ItemPlaceListener(CustomBlocks blocks, CustomItems items, Supplier<Optional<SnowPilePlacer>> snow,
            Predicate<Block> snowPileable) {
        this.blocks = Objects.requireNonNull(blocks, "blocks");
        this.items = Objects.requireNonNull(items, "items");
        this.snow = Objects.requireNonNull(snow, "snow");
        this.snowPileable = Objects.requireNonNull(snowPileable, "snowPileable");
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
        if (clicked == null || held == null || held.getType().isAir()
                || mode == GameMode.SPECTATOR || mode == GameMode.ADVENTURE
                || !Bukkit.isOwnedByCurrentRegion(clicked.getLocation())) {
            return;
        }
        if (!player.isSneaking() && ownInteraction(clicked)) {
            return;
        }
        String id = items.idOf(held).orElseGet(() -> held.getType().getKey().toString());
        boolean done;
        if (id.equals(ItemPlacementRules.SNOW_ITEM)) {
            done = snow(clicked, event.getBlockFace());
            if (done) {
                sound(clicked, "block.snow.place", 1f);
                sound(clicked, "block.snow.place", 0.8f);
            }
        } else {
            Optional<Block> placed = groundcover(id, clicked, event.getBlockFace());
            done = placed.isPresent();
            if (done) {
                sound(placed.get(), "block.nether_wart.place", 0.8f);
            }
        }
        if (!done) {
            return;
        }
        event.setUseInteractedBlock(Event.Result.DENY);
        event.setUseItemInHand(Event.Result.DENY);
        player.swingHand(event.getHand());
        if (mode != GameMode.CREATIVE) {
            consume(player, event.getHand(), held);
        }
    }

    /** A custom block, or a vanilla block with a use of its own (chest, door, ...), consumes the click first. */
    private boolean ownInteraction(Block clicked) {
        return blocks.stateAt(clicked).isPresent() && !snowPileable.test(clicked) && !isPile(clicked)
                || clicked.getType().isInteractable();
    }

    private boolean isPile(Block block) {
        return blocks.stateAt(block).map(s -> s.id().equals("tfp:snow_pile")).orElse(false);
    }

    private Optional<Block> groundcover(String itemId, Block clicked, BlockFace face) {
        if (ItemPlacementRules.typeOf(itemId).isEmpty()) {
            return Optional.empty();
        }
        Block target = clicked.isReplaceable() && blocks.stateAt(clicked).isEmpty() ? clicked : clicked.getRelative(face);
        if (blocks.stateAt(target).isPresent()) {
            return Optional.empty();
        }
        Block below = target.getRelative(BlockFace.DOWN);
        boolean sturdy = sturdyTop(below);
        Optional<Groundcover> decision = ItemPlacementRules.placeGroundcover(itemId,
                new Spot(target.isReplaceable(), waterSource(target)), sturdy);
        if (decision.isEmpty()) {
            return Optional.empty();
        }
        BlockStateRef state = BlockStateRef.of(decision.get().blockId());
        boolean placed = decision.get().waterlogged() && blocks.place(target, state.with(FLUID_PROPERTY, "water"), true)
                || blocks.place(target, state, true);
        return placed ? Optional.of(target) : Optional.empty();
    }

    /** {@code isFaceSturdy(UP)}: a custom block reads as its carrier's shape; falls back to solidity where the API cannot tell. */
    private static boolean sturdyTop(Block below) {
        try {
            return below.getBlockData().isFaceSturdy(BlockFace.UP, BlockSupport.FULL);
        } catch (RuntimeException e) {
            return below.getType().isSolid();
        }
    }

    private static boolean waterSource(Block block) {
        BlockData data = block.getBlockData();
        return block.getType() == Material.WATER && data instanceof Levelled level && level.getLevel() == 0;
    }

    private boolean snow(Block clicked, BlockFace face) {
        Optional<SnowPilePlacer> placer = snow.get();
        if (placer.isEmpty()) {
            return false;
        }
        Block below = clicked.getRelative(BlockFace.DOWN);
        boolean sturdy = sturdyTop(below);
        int pileLayers = blocks.stateAt(clicked).filter(s -> s.id().equals("tfp:snow_pile"))
                .map(s -> parse(s.properties().get("layers"))).orElse(0);
        SnowAction action = ItemPlacementRules.snow(snowPileable.test(clicked), sturdy, pileLayers);
        return switch (action) {
            case SnowAction.Pile p -> placer.get().placePile(clicked);
            case SnowAction.AddLayer a -> placer.get().addLayer(clicked);
            case SnowAction.Defer d -> false;
        };
    }

    private static int parse(String layers) {
        try {
            return layers == null ? 1 : Integer.parseInt(layers);
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    private static void sound(Block block, String sound, float pitch) {
        block.getWorld().playSound(block.getLocation().toCenterLocation(), sound, SoundCategory.BLOCKS, 1f, pitch);
    }

    private static void consume(Player player, EquipmentSlot hand, ItemStack held) {
        ItemStack rest = held.clone();
        rest.setAmount(held.getAmount() - 1);
        ItemStack value = rest.getAmount() <= 0 ? null : rest;
        if (hand == EquipmentSlot.OFF_HAND) {
            player.getInventory().setItemInOffHand(value);
        } else {
            player.getInventory().setItemInMainHand(value);
        }
    }
}
