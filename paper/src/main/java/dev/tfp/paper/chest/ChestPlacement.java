/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/wood/TFCChestBlock.java @ c3b4d9536 (extends vanilla ChestBlock and
 *       inherits getStateForPlacement and updateShape, which this ports)
 */
package dev.tfp.paper.chest;

import dev.tfp.core.chest.ChestRules;
import dev.tfp.core.chest.ChestSide;
import dev.tfp.core.chest.ChestType;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.scheduler.Scheduler;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;

/**
 * PAR-DECO-04: the facing and the type of a placed chest (vanilla {@code getStateForPlacement}) and the pairing of the
 * other half ({@code updateShape}), and a half going single when its partner is broken. CraftEngine places the default
 * state (north, single); the placement event hands the placing facts to the block's region, which reads the neighbours,
 * decides with {@link ChestRules} and writes the states (no physics).
 */
public final class ChestPlacement implements Listener {
    private final CustomBlocks blocks;
    private final Scheduler scheduler;
    private final BiConsumer<Block, BlockStateRef> writer;

    /** @param writer replaces the state of a custom block of the same id without physics (the visuals' block data) */
    public ChestPlacement(CustomBlocks blocks, Scheduler scheduler, BiConsumer<Block, BlockStateRef> writer) {
        this.blocks = Objects.requireNonNull(blocks, "blocks");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.writer = Objects.requireNonNull(writer, "writer");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();
        Player player = event.getPlayer();
        ChestSide looking = side(player.getLocation().getYaw());
        BlockFace face = block.getFace(event.getBlockAgainst());
        ChestSide clicked = face == null ? null : of(face.getOppositeFace());
        boolean sneaking = player.isSneaking();
        scheduler.region(block.getLocation(), () -> placed(block, looking, clicked, sneaking));
    }

    /** Vanilla {@code Direction.fromYRot}: {@code floor(yaw / 90 + 0.5) & 3} is south, west, north, east. */
    static ChestSide side(float yaw) {
        return switch (((int) Math.floor(yaw / 90.0 + 0.5)) & 3) {
            case 0 -> ChestSide.SOUTH;
            case 1 -> ChestSide.WEST;
            case 2 -> ChestSide.NORTH;
            default -> ChestSide.EAST;
        };
    }

    /** The horizontal side of a block face, or null for up and down. */
    static ChestSide of(BlockFace face) {
        return switch (face) {
            case NORTH -> ChestSide.NORTH;
            case EAST -> ChestSide.EAST;
            case SOUTH -> ChestSide.SOUTH;
            case WEST -> ChestSide.WEST;
            default -> null;
        };
    }

    /** Region thread: the placed chest takes its facing and type, and its new partner the opposite type. */
    void placed(Block block, ChestSide looking, ChestSide clicked, boolean sneaking) {
        Optional<ChestBlocks.Found> self = ChestBlocks.at(blocks, block);
        if (self.isEmpty()) {
            return;
        }
        Function<ChestSide, ChestRules.Chest> neighbours = side -> neighbour(block, self.get().id(), side)
            .map(ChestBlocks.Found::chest).orElse(null);
        ChestRules.Chest decided = ChestRules.placement(looking, clicked, sneaking, neighbours);
        ChestBlocks.Found next = self.get().with(decided.facing(), decided.type());
        write(block, next);
        if (decided.type() != ChestType.SINGLE) {
            ChestSide toPartner = ChestRules.partnerSide(decided.facing(), decided.type());
            Block partner = ChestBlocks.relative(block, toPartner);
            neighbour(block, self.get().id(), toPartner).ifPresent(p -> {
                ChestType type = ChestRules.afterNeighbourChange(p.chest(), toPartner.opposite(), next.chest());
                write(partner, p.withType(type));
            });
        }
    }

    /**
     * A chest was removed or replaced at {@code block}: each half that was paired with it becomes single (vanilla
     * {@code updateShape} when the neighbour is no longer a chest). Region thread.
     */
    void removed(Block block) {
        if (blocks.stateAt(block).filter(s -> ChestBlocks.isChestId(s.id())).isPresent()) {
            return; // replaced by a chest again: the placement decides
        }
        for (ChestSide side : ChestSide.values()) {
            Block n = ChestBlocks.relative(block, side);
            if (!scheduler.isOwner(n.getWorld(), n.getX() >> 4, n.getZ() >> 4)) {
                continue;
            }
            Optional<ChestBlocks.Found> found = ChestBlocks.at(blocks, n);
            if (found.isEmpty()) {
                continue;
            }
            ChestType type = ChestRules.afterNeighbourChange(found.get().chest(), side.opposite(), null);
            if (type != found.get().chest().type()) {
                write(n, found.get().withType(type));
            }
        }
    }

    private Optional<ChestBlocks.Found> neighbour(Block block, String id, ChestSide side) {
        Block n = ChestBlocks.relative(block, side);
        if (!scheduler.isOwner(n.getWorld(), n.getX() >> 4, n.getZ() >> 4)
            || !n.getWorld().isChunkLoaded(n.getX() >> 4, n.getZ() >> 4)) {
            return Optional.empty();
        }
        return ChestBlocks.at(blocks, n).filter(f -> f.id().equals(id));
    }

    private void write(Block block, ChestBlocks.Found next) {
        Optional<BlockStateRef> now = blocks.stateAt(block);
        if (now.isEmpty() || !now.get().id().equals(next.id()) || now.get().equals(next.state())) {
            return;
        }
        writer.accept(block, next.state());
    }
}
