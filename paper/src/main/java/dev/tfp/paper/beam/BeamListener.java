/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/wood/VerticalSupportBlock.java @ c3b4d9536 (setPlacedBy),
 * file: src/main/java/net/dries007/tfc/common/blocks/wood/HorizontalSupportBlock.java @ c3b4d9536 (setPlacedBy)
 */
package dev.tfp.paper.beam;

import dev.tfp.core.beam.BeamCell;
import dev.tfp.core.beam.BeamPlacement;
import dev.tfp.core.beam.BeamRules;
import dev.tfp.core.beam.BeamShape;
import dev.tfp.core.world.BlockPos;
import dev.tfp.core.world.Direction;
import dev.tfp.paper.integration.CustomItems;
import dev.tfp.paper.scheduler.Scheduler;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.LongAdder;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.BoundingBox;

/**
 * Placing a support item and feeding neighbour changes to the {@link BeamService}. Decide async, apply sync:
 *
 * <pre>
 * click     owner thread: the use of the item is denied, the cells the placement reads (the target, the block below, the two
 *           above, and five cells out and one back along each horizontal side) are copied as {@link BeamCell}s
 * decide    CPU pool: {@link BeamPlacement#plan} (TFC's StandingAndWallBlockItem choice of the vertical or horizontal beam,
 *           then setPlacedBy: two more poles above, or the span to the pole)
 * apply     the target's region: the target must still be free and the player still hold the item; the beams are placed
 *           as their provisional state through {@link BeamService#place}, the items taken (not in creative)
 * </pre>
 * Adventure mode places nothing (vanilla's rule for blocks), spectators cannot click blocks.
 */
public final class BeamListener implements Listener {
    /** The horizontal reach of the snapshot: TFC's span scan looks at most this far out. */
    static final int REACH = BeamRules.MAX_SPAN;
    private static final Direction[] HORIZONTAL = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
    private static final BeamCell BLOCKED = new BeamCell(BeamShape.NONE, false, false);

    private final BeamService service;
    private final Scheduler scheduler;
    private final CustomItems items;
    final LongAdder placements = new LongAdder();
    final LongAdder refused = new LongAdder();

    public BeamListener(BeamService service, Scheduler scheduler, CustomItems items) {
        this.service = service;
        this.scheduler = scheduler;
        this.items = items;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPhysics(BlockPhysicsEvent event) {
        service.notified(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        // the block is still there during the event: look at the sides once it is gone
        scheduler.regionDelayed(block.getWorld(), block.getX() >> 4, block.getZ() >> 4, 1L, () -> service.changed(block));
    }

    @EventHandler(priority = EventPriority.HIGH)
    @SuppressWarnings("deprecation") // Material.isInteractable: the closest Bukkit answer to "the block has a use"
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() == null || event.getClickedBlock() == null
                || event.useItemInHand() == Event.Result.DENY) {
            return;
        }
        ItemStack item = event.getItem();
        if (item == null) {
            return;
        }
        Optional<String> wood = items.idOf(item).flatMap(BeamBlocks::woodOfItem);
        if (wood.isEmpty()) {
            return;
        }
        Player player = event.getPlayer();
        Block clicked = event.getClickedBlock();
        if (!player.isSneaking() && clicked.getType().isInteractable() && service.beamAt(clicked).isEmpty()
                && !clicked.getType().isAir()) {
            return; // the clicked block is used (a chest opens), as vanilla does before placing
        }
        event.setUseItemInHand(Event.Result.DENY);
        if (player.getGameMode() == GameMode.ADVENTURE || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        Direction face;
        try {
            face = Direction.valueOf(event.getBlockFace().name());
        } catch (IllegalArgumentException e) {
            return;
        }
        boolean replaceClicked = replaceable(clicked);
        Block target = replaceClicked ? clicked : clicked.getRelative(event.getBlockFace());
        if (!replaceable(target)) {
            return;
        }
        Location eye = player.getEyeLocation();
        List<Direction> looking = BeamPlacement.lookingDirections(eye.getYaw(), eye.getPitch(), face, replaceClicked);
        Map<BlockPos, BeamCell> cells = snapshot(target);
        BlockPos pos = BeamService.pos(target);
        int count = item.getAmount();
        boolean sneaking = player.isSneaking();
        EquipmentSlot hand = event.getHand();
        String w = wood.get();
        scheduler.cpu(() -> {
            Optional<BeamPlacement.Plan> plan = BeamPlacement.plan(p -> cells.getOrDefault(p, BLOCKED), pos, looking, count,
                sneaking);
            if (plan.isEmpty()) {
                refused.increment();
                return;
            }
            scheduler.region(target.getWorld(), target.getX() >> 4, target.getZ() >> 4,
                () -> apply(player, hand, w, target, plan.get()));
        });
    }

    /** A block the placement may replace: no custom block, and replaceable (air, water, grass). Owner thread. */
    private boolean replaceable(Block block) {
        return block.getWorld().isChunkLoaded(block.getX() >> 4, block.getZ() >> 4)
            && (block.getType().isAir() || block.isReplaceable()) && !service.isCustom(block);
    }

    /** Owner thread of {@code target}: the cells {@link BeamPlacement#plan} reads; a cell of another region reads as blocked. */
    Map<BlockPos, BeamCell> snapshot(Block target) {
        Map<BlockPos, BeamCell> cells = new HashMap<>();
        World world = target.getWorld();
        BlockPos t = BeamService.pos(target);
        put(cells, world, t);
        put(cells, world, t.offset(Direction.DOWN));
        put(cells, world, t.offset(Direction.UP));
        put(cells, world, t.offset(0, 2, 0));
        for (Direction d : HORIZONTAL) {
            for (int k = -1; k <= REACH; k++) {
                put(cells, world, t.offset(d.dx() * k, 0, d.dz() * k));
            }
        }
        return cells;
    }

    private void put(Map<BlockPos, BeamCell> cells, World world, BlockPos p) {
        if (cells.containsKey(p) || p.y() < world.getMinHeight() || p.y() >= world.getMaxHeight()
                || !world.isChunkLoaded(p.chunkX(), p.chunkZ()) || !scheduler.isOwner(world, p.chunkX(), p.chunkZ())) {
            return;
        }
        cells.put(p, service.read(world.getBlockAt(p.x(), p.y(), p.z())).cell());
    }

    /** Owner thread of {@code target}: re-validate and write the plan. */
    void apply(Player player, EquipmentSlot hand, String wood, Block target, BeamPlacement.Plan plan) {
        World world = target.getWorld();
        Block feet = player.getLocation().getBlock();
        if (!player.isOnline() || !player.getWorld().equals(world) || !scheduler.isOwner(world, feet.getX() >> 4, feet.getZ() >> 4)
                || !replaceable(target) || occupied(target, true)) {
            refused.increment();
            return;
        }
        boolean creative = player.getGameMode() == GameMode.CREATIVE;
        if (player.getGameMode() == GameMode.ADVENTURE || player.getGameMode() == GameMode.SPECTATOR) {
            refused.increment();
            return;
        }
        ItemStack held = player.getInventory().getItem(hand);
        if (held == null || !items.idOf(held).flatMap(BeamBlocks::woodOfItem).map(wood::equals).orElse(false)) {
            refused.increment();
            return;
        }
        int extraItems = plan.extraItems();
        List<BlockPos> extra = plan.extra();
        if (plan.shape() == BeamShape.VERTICAL && !extra.isEmpty()) {
            Block above = block(world, extra.get(0));
            Block above2 = block(world, extra.get(1));
            boolean free1 = open(above) && !occupied(above, false);
            boolean free2 = free1 && open(above2) && !occupied(above2, false);
            extraItems = BeamPlacement.verticalExtras(free1, free2);
            extra = extra.subList(0, extraItems);
        }
        if (!creative && held.getAmount() < 1 + extraItems) {
            refused.increment();
            return;
        }
        service.place(target, BeamBlocks.state(wood, plan.shape(), java.util.Set.of()));
        for (BlockPos p : extra) {
            Runnable put = () -> {
                Block b = block(world, p);
                if (open(b)) {
                    service.place(b, BeamBlocks.state(wood, plan.shape(), java.util.Set.of()));
                }
            };
            if (scheduler.isOwner(world, p.chunkX(), p.chunkZ())) {
                put.run();
            } else {
                scheduler.region(world, p.chunkX(), p.chunkZ(), put);
            }
        }
        if (!creative) {
            int left = held.getAmount() - 1 - extraItems;
            if (left <= 0) {
                player.getInventory().setItem(hand, null);
            } else {
                held.setAmount(left);
                player.getInventory().setItem(hand, held);
            }
        }
        // the block item's place sound (SoundType.WOOD: volume (1 + 1) / 2, pitch 1 * 0.8)
        world.playSound(target.getLocation().add(0.5, 0.5, 0.5), Sound.BLOCK_WOOD_PLACE, SoundCategory.BLOCKS, 1.0f, 0.8f);
        player.swingHand(hand);
        placements.increment();
    }

    private boolean open(Block block) {
        return service.read(block).open();
    }

    /**
     * An entity in the block: TFC's {@code getEntities(null, new AABB(pos)).isEmpty()} for the poles above (any entity), and
     * for the target vanilla's {@code isUnobstructed} (living entities only; against the whole block, not the beam's shape).
     */
    private static boolean occupied(Block block, boolean livingOnly) {
        BoundingBox box = BoundingBox.of(block);
        for (Entity e : block.getWorld().getNearbyEntities(box)) {
            if (livingOnly && !(e instanceof org.bukkit.entity.LivingEntity)) {
                continue;
            }
            if (!(e instanceof Player p && p.getGameMode() == GameMode.SPECTATOR) && e.getBoundingBox().overlaps(box)) {
                return true;
            }
        }
        return false;
    }

    private static Block block(World world, BlockPos p) {
        return world.getBlockAt(p.x(), p.y(), p.z());
    }
}
