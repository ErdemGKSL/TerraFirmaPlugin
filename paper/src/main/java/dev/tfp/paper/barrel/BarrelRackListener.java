/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/devices/BarrelBlock.java @ c3b4d9536 (getStateForPlacement: the clicked
 * face, down counted as up; on a barrel rack the player's horizontal direction and RACK; otherwise a sturdy CENTER face
 * below; onDestroyedByPlayer: a racked barrel leaves the rack; onRemove: a racked barrel removed otherwise spawns the rack
 * item),
 * file: src/main/java/net/dries007/tfc/common/blocks/devices/BarrelRackBlock.java @ c3b4d9536 (canBeReplaced: a Rackable
 * item replaces the rack when it can survive there),
 * file: src/main/java/net/dries007/tfc/common/items/BarrelBlockItem.java @ c3b4d9536 (the barrel item is Rackable)
 */
package dev.tfp.paper.barrel;

import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.integration.CustomItems;
import dev.tfp.paper.scheduler.Scheduler;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockSupport;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.RayTraceResult;

/**
 * PAR-PROC-38: the barrel rack and the barrel's {@code facing} and {@code rack} states in the world, and the
 * {@link BarrelRackWorld} the barrel device reads.
 *
 * <ul>
 *   <li>Placing a barrel (TFC {@code getStateForPlacement}): the clicked face sets {@code facing} (down counts as up); a
 *       placement with no sturdy centre face below is refused. CraftEngine places the default (upright) state; a facing
 *       other than up is written by a region task after the event (decided in the event, applied after it).</li>
 *   <li>A barrel item used on a barrel rack (TFC {@code canBeReplaced}: the barrel item is {@code Rackable}): the click is
 *       cancelled before CraftEngine (priority LOW) and the rack is replaced by the barrel on its side, facing the player's
 *       horizontal direction, racked; the item is used up outside creative.</li>
 *   <li>A player breaking a racked barrel leaves the rack (TFC {@code onDestroyedByPlayer}); an explosion takes the barrel
 *       and drops the rack item (TFC {@code onRemove}).</li>
 * </ul>
 *
 * <p>Threads: every handler runs on the event's thread, which owns the block (Paper, Folia); a block of a region the thread
 * does not own is left alone.
 */
public final class BarrelRackListener implements Listener, BarrelRackWorld {
    /** The rack block and item id. */
    public static final String RACK_ID = "tfp:barrel_rack";
    static final String BARREL_PREFIX = BarrelDevice.ID + "/";
    /** How far {@link #hitHeight} traces (vanilla's block reach). */
    static final double REACH = 6.0;

    private final CustomBlocks blocks;
    private final CustomItems items;
    private final DeviceService devices;
    private final Scheduler scheduler;
    private final BarrelEffects effects;
    private final Supplier<BarrelRackTap> tap;
    private final Predicate<Block> support;
    private final BiFunction<Player, Block, Double> hit;
    /** The player's own tick ({@link Player#getTicksLived()}) of their last barrel laid on a rack. */
    private final Map<UUID, Integer> lastLaid = new ConcurrentHashMap<>();

    public BarrelRackListener(CustomBlocks blocks, CustomItems items, DeviceService devices, Scheduler scheduler,
            BarrelEffects effects, Supplier<BarrelRackTap> tap) {
        this(blocks, items, devices, scheduler, effects, tap, BarrelRackListener::sturdyBelow, BarrelRackListener::traced);
    }

    /**
     * @param support TFC's sturdy centre face below a block ({@link #sturdyBelow})
     * @param hit     the height of a player's hit on a block ({@link #traced})
     */
    public BarrelRackListener(CustomBlocks blocks, CustomItems items, DeviceService devices, Scheduler scheduler,
            BarrelEffects effects, Supplier<BarrelRackTap> tap, Predicate<Block> support, BiFunction<Player, Block, Double> hit) {
        this.support = Objects.requireNonNull(support, "support");
        this.hit = Objects.requireNonNull(hit, "hit");
        this.blocks = Objects.requireNonNull(blocks, "blocks");
        this.items = Objects.requireNonNull(items, "items");
        this.devices = Objects.requireNonNull(devices, "devices");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.effects = Objects.requireNonNull(effects, "effects");
        this.tap = Objects.requireNonNull(tap, "tap");
    }

    // ---- BarrelRackWorld --------------------------------------------------------------------------------

    private Optional<BlockStateRef> barrel(Block block) {
        return blocks.stateAt(block).filter(s -> s.id().startsWith(BARREL_PREFIX));
    }

    @Override
    public BlockFace facing(Block block) {
        return barrel(block).map(s -> face(s.properties().get(BarrelDevice.FACING))).orElse(BlockFace.UP);
    }

    /** A {@code facing} value; anything unknown (an old record without the property) is up. */
    static BlockFace face(String value) {
        if (value == null) {
            return BlockFace.UP;
        }
        return switch (value) {
            case "north" -> BlockFace.NORTH;
            case "south" -> BlockFace.SOUTH;
            case "east" -> BlockFace.EAST;
            case "west" -> BlockFace.WEST;
            default -> BlockFace.UP;
        };
    }

    @Override
    public boolean racked(Block block) {
        return barrel(block).map(s -> Boolean.parseBoolean(s.properties().get(BarrelDevice.RACK))).orElse(false);
    }

    @Override
    public boolean supported(Block block) {
        return support.test(block);
    }

    /** TFC {@code isFaceSturdy(level, below, UP, SupportType.CENTER)} on the Bukkit state below (a carrier for a CE block). */
    static boolean sturdyBelow(Block block) {
        Block below = block.getRelative(BlockFace.DOWN);
        return below.getBlockData().isFaceSturdy(BlockFace.UP, BlockSupport.CENTER);
    }

    @Override
    public boolean isRackItem(ItemStack stack) {
        return stack != null && !stack.isEmpty() && items.idOf(stack).filter(RACK_ID::equals).isPresent();
    }

    @Override
    public Optional<ItemStack> rackItem() {
        return items.create(RACK_ID, 1);
    }

    @Override
    public double hitHeight(Player player, Block block) {
        return hit.apply(player, block);
    }

    static double traced(Player player, Block block) {
        RayTraceResult hit = player.rayTraceBlocks(REACH);
        if (hit == null || hit.getHitBlock() == null || !hit.getHitBlock().equals(block)) {
            return Double.NaN;
        }
        return hit.getHitPosition().getY() - block.getY();
    }

    @Override
    public void tap(Block block) {
        BarrelRackTap t = tap.get();
        if (t != null) {
            t.settleAround(block);
        }
    }

    // ---- events ----------------------------------------------------------------------------------------

    private boolean owned(Block block) {
        return scheduler.isOwner(block.getWorld(), block.getX() >> 4, block.getZ() >> 4);
    }

    private boolean isBarrelItem(ItemStack stack) {
        return stack != null && !stack.isEmpty() && items.idOf(stack).filter(id -> id.startsWith(BARREL_PREFIX)).isPresent();
    }

    /** TFC {@code getStateForPlacement} of a barrel placed by CraftEngine (not on a rack). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Block placed = event.getBlockPlaced();
        if (!owned(placed)) {
            return;
        }
        Optional<BlockStateRef> state = barrel(placed);
        if (state.isEmpty() || Boolean.parseBoolean(state.get().properties().get(BarrelDevice.RACK))) {
            return; // not a barrel, or laid on a rack by onInteract
        }
        if (!supported(placed)) {
            event.setCancelled(true);
            return;
        }
        BlockFace clicked = event.getBlockAgainst().getFace(placed);
        BlockFace facing = clickedFacing(clicked);
        if (facing != BlockFace.UP) {
            effects.state(placed, BlockStateRef.of(state.get().id()).with(BarrelDevice.FACING,
                facing.name().toLowerCase(java.util.Locale.ROOT)));
        }
    }

    /** TFC: the clicked face, down (and a click into the block itself) counted as up. */
    static BlockFace clickedFacing(BlockFace clicked) {
        if (clicked == null) {
            return BlockFace.UP;
        }
        return switch (clicked) {
            case NORTH, SOUTH, EAST, WEST -> clicked;
            default -> BlockFace.UP;
        };
    }

    /**
     * TFC {@code BarrelRackBlock.canBeReplaced}: a barrel item used on a rack lays the barrel on it. The other interactions
     * of the same player in that tick (the off-hand event Paper fires after the cancelled main-hand one, which would reach
     * the new barrel and open its screen) are cancelled, as vanilla ends the click with the placement.
     */
    @EventHandler(priority = EventPriority.LOW)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK && event.getAction() != Action.RIGHT_CLICK_AIR) {
            return;
        }
        Integer laid = lastLaid.get(event.getPlayer().getUniqueId());
        if (laid != null && laid == event.getPlayer().getTicksLived()) {
            event.setCancelled(true);
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() == null || event.getClickedBlock() == null
                || event.useItemInHand() == Event.Result.DENY) {
            return;
        }
        Block rack = event.getClickedBlock();
        ItemStack held = event.getItem();
        if (!isBarrelItem(held) || !owned(rack)) {
            return;
        }
        Optional<BlockStateRef> at = blocks.stateAt(rack);
        if (at.isEmpty() || !at.get().id().equals(RACK_ID)) {
            return;
        }
        Player player = event.getPlayer();
        event.setCancelled(true);
        if (!supported(rack)) {
            return;
        }
        String barrelId = items.idOf(held).orElseThrow();
        if (!placeOnRack(rack, barrelId, horizontal(player))) {
            return;
        }
        lastLaid.put(player.getUniqueId(), player.getTicksLived());
        ItemStack used = held.clone();
        if (player.getGameMode() != GameMode.CREATIVE) {
            ItemStack rest = held.clone();
            rest.setAmount(held.getAmount() - 1);
            player.getInventory().setItem(event.getHand(), rest.getAmount() <= 0 ? ItemStack.empty() : rest);
        }
        rack.getWorld().playSound(rack.getLocation().add(0.5, 0.5, 0.5), Sound.BLOCK_WOOD_PLACE, SoundCategory.BLOCKS,
            1.0f, 0.8f);
        used.setAmount(1);
        devices.placed(rack, player, event.getHand(), used);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastLaid.remove(event.getPlayer().getUniqueId());
    }

    /** The barrel {@code barrelId} on its side facing {@code facing}, racked, in place of the rack at {@code rack}. */
    boolean placeOnRack(Block rack, String barrelId, BlockFace facing) {
        BlockStateRef state = BarrelDevice.rackState(barrelId, facing, true).with(BarrelDevice.SEALED, "false");
        return blocks.place(rack, state, true);
    }

    /** TFC {@code context.getHorizontalDirection()}: the player's horizontal facing. */
    static BlockFace horizontal(Player player) {
        return horizontal(player.getLocation().getYaw());
    }

    /** Vanilla {@code Direction.fromYRot}: {@code floor(yaw / 90 + 0.5) & 3} is south, west, north, east. */
    static BlockFace horizontal(float yaw) {
        return switch (((int) Math.floor(yaw / 90.0 + 0.5)) & 3) {
            case 0 -> BlockFace.SOUTH;
            case 1 -> BlockFace.WEST;
            case 2 -> BlockFace.NORTH;
            default -> BlockFace.EAST;
        };
    }

    /** TFC {@code onDestroyedByPlayer}: a racked barrel leaves the rack behind (the barrel drops as usual). */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!owned(block) || !racked(block)) {
            return;
        }
        Location at = block.getLocation();
        scheduler.region(at, () -> leaveRack(block));
    }

    /** After the break: the rack where the barrel was, if nothing took the place. */
    void leaveRack(Block block) {
        if (block.getType().isAir() && blocks.stateAt(block).isEmpty()) {
            blocks.place(block, BlockStateRef.of(RACK_ID), false);
        }
    }

    /** TFC {@code onRemove}: a racked barrel taken by an explosion spawns the rack item. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        exploded(event.blockList());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        exploded(event.blockList());
    }

    private void exploded(List<Block> blocksHit) {
        for (Block b : blocksHit) {
            if (owned(b) && racked(b)) {
                rackItem().ifPresent(item -> effects.spawn(b, item));
            }
        }
    }
}
