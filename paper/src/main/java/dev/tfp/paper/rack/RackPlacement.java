/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/wood/ToolRackBlock.java @ c3b4d9536 (getStateForPlacement, canSurvive)
 * src/main/java/net/dries007/tfc/common/blocks/ShelfBlock.java @ c3b4d9536 (getStateForPlacement via Helpers.getSupportedDirectionalStateForPlacement)
 * vanilla net.minecraft.world.level.block.{LecternBlock,ChiseledBookShelfBlock} (getStateForPlacement: the opposite of the placer's horizontal direction)
 */
package dev.tfp.paper.rack;

import dev.tfp.core.chest.ChestSide;
import dev.tfp.core.rack.LookingOrder;
import dev.tfp.core.rack.ToolRackRules;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.scheduler.Scheduler;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;

/**
 * PAR-DECO-05: the facing of a placed tool rack, shelf, bookshelf or lectern. CraftEngine places the default state (north);
 * the placement event decides the facing, cancels a rack or shelf that has nothing to hang on (TFC {@code getStateForPlacement}
 * returns null), and hands the write to the block's region.
 */
public final class RackPlacement implements Listener {
    private final CustomBlocks blocks;
    private final Scheduler scheduler;
    private final BiConsumer<Block, BlockStateRef> writer;
    private final BiPredicate<Block, BlockFace> vanillaSturdy;

    /**
     * @param writer        replaces the state of a custom block of the same id without physics (the visuals' block data)
     * @param vanillaSturdy whether a vanilla block has a full face on a side (Paper's {@code BlockData.isFaceSturdy})
     */
    public RackPlacement(CustomBlocks blocks, Scheduler scheduler, BiConsumer<Block, BlockStateRef> writer,
            BiPredicate<Block, BlockFace> vanillaSturdy) {
        this.vanillaSturdy = Objects.requireNonNull(vanillaSturdy, "vanillaSturdy");
        this.blocks = Objects.requireNonNull(blocks, "blocks");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.writer = Objects.requireNonNull(writer, "writer");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();
        Optional<RackBlocks.Found> self = blocks.stateAt(block).flatMap(RackBlocks::parse);
        if (self.isEmpty()) {
            return;
        }
        Player player = event.getPlayer();
        BlockFace toAgainst = block.getFace(event.getBlockAgainst());
        ChestSide clicked = toAgainst == null ? null : RackInteractions.side(toAgainst.getOppositeFace());
        Optional<ChestSide> facing = decide(block, self.get(), player.getLocation().getYaw(), player.getLocation().getPitch(),
            block.equals(event.getBlockAgainst()) ? null : event.getBlockAgainst(), clicked);
        if (facing.isEmpty()) {
            event.setCancelled(true);
            return;
        }
        RackBlocks.Found next = self.get().withFacing(facing.get());
        scheduler.region(block.getLocation(), () -> {
            Optional<BlockStateRef> now = blocks.stateAt(block);
            if (now.isPresent() && now.get().id().equals(next.id()) && !now.get().equals(next.state())) {
                writer.accept(block, next.state());
            }
        });
    }

    /**
     * The facing a placement takes, or empty when nothing is placed.
     *
     * @param against the clicked block when the new block is placed beside it, null when it replaces it
     * @param clicked the clicked face of {@code against}
     */
    Optional<ChestSide> decide(Block block, RackBlocks.Found self, float yaw, float pitch, Block against, ChestSide clicked) {
        if (self.is(RackBlocks.LECTERN) || self.is(RackBlocks.BOOKSHELF)) {
            return Optional.of(horizontal(yaw).opposite());
        }
        List<ChestSide> looking = LookingOrder.horizontal(yaw, pitch);
        if (self.is(RackBlocks.TOOL_RACK)) {
            if (against != null) {
                RackBlocks.Found other = blocks.stateAt(against).flatMap(RackBlocks::parse).orElse(null);
                if (other != null && other.is(RackBlocks.TOOL_RACK)
                        && ToolRackRules.stacksOnRack(other.facing(), clicked)) {
                    return Optional.empty();
                }
            }
            return Optional.ofNullable(ToolRackRules.rackFacing(looking, side -> sturdy(block, side)));
        }
        return Optional.ofNullable(ToolRackRules.shelfFacing(looking, side -> sturdy(block, side)));
    }

    /** Vanilla {@code Direction.fromYRot}: {@code floor(yaw / 90 + 0.5) & 3} is south, west, north, east. */
    static ChestSide horizontal(float yaw) {
        return switch (((int) Math.floor(yaw / 90.0 + 0.5)) & 3) {
            case 0 -> ChestSide.SOUTH;
            case 1 -> ChestSide.WEST;
            case 2 -> ChestSide.NORTH;
            default -> ChestSide.EAST;
        };
    }

    /** Whether the block on {@code side} of {@code block} carries a rack or shelf (a full face towards it). */
    boolean sturdy(Block block, ChestSide side) {
        Block n = block.getRelative(side.dx(), 0, side.dz());
        Optional<BlockStateRef> custom = blocks.stateAt(n);
        if (custom.isPresent()) {
            return RackSupport.solidId(custom.get().id());
        }
        BlockFace towards = switch (side) {
            case NORTH -> BlockFace.SOUTH;
            case SOUTH -> BlockFace.NORTH;
            case EAST -> BlockFace.WEST;
            case WEST -> BlockFace.EAST;
        };
        return vanillaSturdy.test(n, towards);
    }
}
