/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/util/InteractionManager.java @ c3b4d9536 (log pile creation and insertion, charcoal
 *       placement),
 *       src/main/java/net/dries007/tfc/common/blocks/CharcoalPileBlock.java @ c3b4d9536 (onDestroyedByPlayer, updateShape,
 *       canSurvive),
 *       src/main/java/net/dries007/tfc/common/blocks/devices/LogPileBlock.java @ c3b4d9536 (getStateForPlacement,
 *       canSurvive)
 */
package dev.tfp.paper.charcoal;

import dev.tfp.core.charcoal.CharcoalPile;
import dev.tfp.paper.device.StackCodec;
import dev.tfp.paper.integration.BlockStateRef;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
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
 * TFC's {@code InteractionManager} entries for log piles and charcoal, and the charcoal pile's layer-by-layer break.
 *
 * <ul>
 *   <li>Holding a log and sneaking: on a log pile, insert one (a quick second click: all that fit) and push up into a new
 *       pile above a full one; on another block, place a pile on the player's horizontal axis where a block would be
 *       placed, when the block below the clicked side has a sturdy top.</li>
 *   <li>Holding charcoal: add a layer to the clicked charcoal pile (or the one at the placement position) below eight
 *       layers, else place a new pile.</li>
 *   <li>Breaking a charcoal pile of several layers outside creative takes one layer off (one charcoal drops) and lets a pile
 *       above pour into it ({@code updateShape}).</li>
 * </ul>
 *
 * Runs on the event's thread, which owns the player; a click whose blocks that thread does not own is left alone. Adventure
 * and spectator players cannot build ({@code player.mayBuild()}); creative players keep their item.
 */
public final class CharcoalListener implements Listener {
    private final CharcoalPitDevice device;
    private final CharcoalPitDevice.Services services;
    private final Predicate<Block> owns;
    private final Predicate<String> deviceKind;

    /**
     * @param owns whether the current thread owns a block's region
     * @param deviceKind whether a CraftEngine block id has a registered device kind (its own use: log pile, firepit...)
     */
    public CharcoalListener(CharcoalPitDevice device, CharcoalPitDevice.Services services, Predicate<Block> owns,
            Predicate<String> deviceKind) {
        this.device = Objects.requireNonNull(device, "device");
        this.services = Objects.requireNonNull(services, "services");
        this.owns = Objects.requireNonNull(owns, "owns");
        this.deviceKind = Objects.requireNonNull(deviceKind, "deviceKind");
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() == null
                || event.useItemInHand() == Event.Result.DENY) {
            return;
        }
        Block clicked = event.getClickedBlock();
        ItemStack item = event.getItem();
        if (clicked == null || StackCodec.isEmpty(item)) {
            return;
        }
        boolean isCharcoal = item.getType() == Material.CHARCOAL;
        // TFC runs the charcoal entry at ITEM_AFTER_BLOCK: the clicked block's own use comes first, unless the player
        // sneaks (vanilla skips the block's use for a sneaking player holding an item). The log entry needs sneaking.
        if (isCharcoal && !event.getPlayer().isSneaking() && blockUseFirst(clicked, event.useInteractedBlock())) {
            return;
        }
        boolean handled = isCharcoal
            ? charcoal(event.getPlayer(), event.getHand(), clicked, event.getBlockFace(), item)
            : log(event.getPlayer(), event.getHand(), clicked, event.getBlockFace(), item);
        if (handled) {
            event.setUseInteractedBlock(Event.Result.DENY);
            event.setUseItemInHand(Event.Result.DENY);
        }
    }

    /**
     * Whether the clicked block has a use of its own that runs before TFC's charcoal entry: a CraftEngine block with a
     * device kind (the log pile extracts a log, a firepit opens), or an interactable vanilla block (chest, furnace,
     * crafting table) whose use the event has not denied. A charcoal pile has no use, so a layer is still added whatever
     * its carrier block is (a note-block carrier is interactable to Bukkit).
     */
    @SuppressWarnings("deprecation") // Material.isInteractable: the closest Bukkit answer to "the block has a use"
    boolean blockUseFirst(Block clicked, Event.Result useBlock) {
        Optional<BlockStateRef> s = services.state().apply(clicked);
        if (s.isPresent()) {
            return !s.get().id().equals(CharcoalPitDevice.CHARCOAL_PILE) && deviceKind.test(s.get().id());
        }
        return clicked.getType().isInteractable() && useBlock != Event.Result.DENY;
    }

    /** The log entry. Returns whether the click was used. */
    boolean log(Player player, EquipmentSlot hand, Block clicked, BlockFace face, ItemStack item) {
        if (!mayBuild(player) || !player.isSneaking() || !services.log().test(item) || !owns.test(clicked)) {
            return false;
        }
        Optional<BlockStateRef> state = services.state().apply(clicked);
        if (state.isPresent() && state.get().id().equals(CharcoalPitDevice.LOG_PILE)) {
            int inserted = device.insert(clicked, item);
            if (inserted > 0) {
                consume(player, hand, inserted);
            }
            return true; // TFC: sidedSuccess whenever the pile has its block entity
        }
        Block relative = clicked.getRelative(face);
        if (!owns.test(relative) || !services.sturdyTop().test(relative.getRelative(BlockFace.DOWN))) {
            return false;
        }
        // BlockPlaceContext: a replaceable clicked block is replaced, else the block on the clicked side
        Block at = replaceable(clicked) ? clicked : relative;
        if (!owns.test(at) || !replaceable(at) || !logPileCanSurvive(at)) {
            return false;
        }
        String axis = horizontalAxis(player);
        if (!device.placePile(at, axis, item)) {
            return false;
        }
        consume(player, hand, 1);
        return true;
    }

    /** The charcoal entry. Returns whether the click was used. */
    boolean charcoal(Player player, EquipmentSlot hand, Block clicked, BlockFace face, ItemStack item) {
        if (!mayBuild(player) || !owns.test(clicked) || !services.exists().test(CharcoalPitDevice.CHARCOAL_PILE)) {
            return false;
        }
        Block offset = replaceable(clicked) ? clicked : clicked.getRelative(face);
        if (!owns.test(offset)) {
            return false;
        }
        for (Block target : new Block[] {clicked, offset}) {
            Optional<BlockStateRef> s = services.state().apply(target);
            if (s.isPresent() && s.get().id().equals(CharcoalPitDevice.CHARCOAL_PILE)) {
                int layers = CharcoalPitDevice.layers(s.get());
                if (CharcoalPile.canAddLayer(layers)) {
                    if (!charcoalCanSurvive(target)) {
                        return true; // InteractionResult.FAIL: no space
                    }
                    services.effects().setNow(target, CharcoalPitDevice.charcoalPile(layers + 1));
                    placed(player, hand, target);
                    return true;
                }
            }
        }
        if (!replaceable(offset) || !charcoalCanSurvive(offset)) {
            return false;
        }
        if (!services.effects().placeNow(offset, CharcoalPitDevice.charcoalPile(1))) {
            return false;
        }
        placed(player, hand, offset);
        return true;
    }

    /** TFC {@code onDestroyedByPlayer}: a layer comes off instead of the whole pile. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Optional<BlockStateRef> s = services.state().apply(block);
        if (s.isEmpty() || !s.get().id().equals(CharcoalPitDevice.CHARCOAL_PILE)) {
            return;
        }
        if (breakLayer(event.getPlayer(), block, CharcoalPitDevice.layers(s.get()), event.isDropItems())) {
            event.setCancelled(true);
        }
    }

    /** Returns whether a layer was taken off (the break is then cancelled). */
    boolean breakLayer(Player player, Block block, int layers, boolean drops) {
        boolean creative = player.getGameMode() == GameMode.CREATIVE;
        int left = CharcoalPile.afterPlayerBreak(layers, creative);
        if (left == 0) {
            return false;
        }
        services.effects().setNow(block, CharcoalPitDevice.charcoalPile(left));
        services.effects().sound(block, CharcoalEffects.Sound.CHARCOAL_BREAK);
        if (drops) {
            services.effects().spawn(block, new ItemStack(Material.CHARCOAL));
        }
        // updateShape of the pile above, facing down onto this one
        Block above = block.getRelative(BlockFace.UP);
        Optional<BlockStateRef> a = owns.test(above) ? services.state().apply(above) : Optional.empty();
        if (a.isPresent() && a.get().id().equals(CharcoalPitDevice.CHARCOAL_PILE)) {
            CharcoalPile.Merge m = CharcoalPile.mergeDown(CharcoalPitDevice.layers(a.get()), left);
            if (m != null) {
                services.effects().setNow(block, CharcoalPitDevice.charcoalPile(m.under()));
                if (m.at() == 0) {
                    services.effects().air(above, CharcoalPitDevice.CHARCOAL_PILE);
                } else {
                    services.effects().setNow(above, CharcoalPitDevice.charcoalPile(m.at()));
                }
            }
        }
        return true;
    }

    private void placed(Player player, EquipmentSlot hand, Block at) {
        services.effects().sound(at, CharcoalEffects.Sound.CHARCOAL_PLACE);
        consume(player, hand, 1);
    }

    /** LogPileBlock.canSurvive: a full top face below, or a log pile. */
    private boolean logPileCanSurvive(Block at) {
        Block below = at.getRelative(BlockFace.DOWN);
        return services.sturdyTop().test(below)
            || services.state().apply(below).filter(s -> s.id().equals(CharcoalPitDevice.LOG_PILE)).isPresent();
    }

    /** CharcoalPileBlock.canSurvive. */
    private boolean charcoalCanSurvive(Block at) {
        Block below = at.getRelative(BlockFace.DOWN);
        int belowLayers = services.state().apply(below).filter(s -> s.id().equals(CharcoalPitDevice.CHARCOAL_PILE))
            .map(CharcoalPitDevice::layers).orElse(0);
        return CharcoalPile.canSurvive(services.sturdyTop().test(below), belowLayers);
    }

    /** {@code BlockState.canBeReplaced()} for the placement position: air, fluids, grass and the like (vanilla blocks). */
    private boolean replaceable(Block block) {
        return services.state().apply(block).isEmpty() && block.isReplaceable();
    }

    /** {@code player.mayBuild()}: survival and creative. */
    static boolean mayBuild(Player player) {
        GameMode mode = player.getGameMode();
        return mode == GameMode.SURVIVAL || mode == GameMode.CREATIVE;
    }

    /** {@code context.getHorizontalDirection().getAxis()}. */
    static String horizontalAxis(Player player) {
        float yaw = player.getLocation().getYaw();
        int quarter = Math.floorMod(Math.round(yaw / 90f), 4); // 0 south, 1 west, 2 north, 3 east
        return quarter % 2 == 0 ? "z" : "x";
    }

    /** {@code stack.shrink(n)} on the hand, unless creative. */
    static void consume(Player player, EquipmentSlot hand, int n) {
        if (player.getGameMode() == GameMode.CREATIVE || n <= 0) {
            return;
        }
        ItemStack held = player.getInventory().getItem(hand);
        if (StackCodec.isEmpty(held)) {
            return;
        }
        if (held.getAmount() <= n) {
            player.getInventory().setItem(hand, null);
        } else {
            held.setAmount(held.getAmount() - n);
            player.getInventory().setItem(hand, held);
        }
    }
}
