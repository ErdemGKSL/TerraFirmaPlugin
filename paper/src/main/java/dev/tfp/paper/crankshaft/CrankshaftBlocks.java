/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/rotation/CrankshaftBlock.java @ c3b4d9536 (FACING, PART)
 */
package dev.tfp.paper.crankshaft;

import dev.tfp.core.crankshaft.CrankshaftRules;
import dev.tfp.core.crankshaft.CrankshaftRules.Part;
import dev.tfp.core.crankshaft.CrankshaftRules.Piece;
import dev.tfp.core.world.BlockPos;
import dev.tfp.core.world.Direction;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.scheduler.Scheduler;
import java.util.Locale;
import java.util.Optional;
import org.bukkit.World;
import org.bukkit.block.Block;

/**
 * The crankshaft block {@code tfp:crankshaft} (properties {@code facing=north|east|south|west}, {@code part=base|shaft}) and
 * TFC's {@code getCrankShaftAt} on the live world. Region thread of the block asked about.
 */
public final class CrankshaftBlocks {
    public static final String ID = "tfp:crankshaft";
    public static final String FACING = "facing";
    public static final String PART = "part";
    /** The bellows block and its facing property (PAR-METAL-11). */
    public static final String BELLOWS = "tfp:bellows";

    private CrankshaftBlocks() {
    }

    /** The piece of a crankshaft state; empty for another block or a bad value. */
    public static Optional<Piece> piece(BlockStateRef state) {
        if (!state.id().equals(ID)) {
            return Optional.empty();
        }
        Optional<Direction> facing = horizontal(state.properties().get(FACING));
        Optional<Part> part = Part.parse(state.properties().getOrDefault(PART, "base"));
        return facing.isPresent() && part.isPresent() ? Optional.of(new Piece(part.get(), facing.get())) : Optional.empty();
    }

    /** The state of {@code piece}. */
    public static BlockStateRef state(Piece piece) {
        return BlockStateRef.of(ID).with(FACING, name(piece.facing())).with(PART, piece.part().serializedName());
    }

    /** A horizontal direction of a property value. */
    public static Optional<Direction> horizontal(String value) {
        if (value == null) {
            return Optional.empty();
        }
        return switch (value) {
            case "north" -> Optional.of(Direction.NORTH);
            case "east" -> Optional.of(Direction.EAST);
            case "south" -> Optional.of(Direction.SOUTH);
            case "west" -> Optional.of(Direction.WEST);
            default -> Optional.empty();
        };
    }

    public static String name(Direction d) {
        return d.name().toLowerCase(Locale.ROOT);
    }

    static BlockPos pos(Block block) {
        return new BlockPos(block.getX(), block.getY(), block.getZ());
    }

    /**
     * The piece at {@code pos} of {@code world}, read only when this thread owns its chunk and the chunk is loaded (a block of
     * another region reads as no crankshaft).
     */
    static Optional<Piece> pieceAt(World world, BlockPos pos, CustomBlocks blocks, Scheduler scheduler) {
        int cx = pos.x() >> 4;
        int cz = pos.z() >> 4;
        if (!world.isChunkLoaded(cx, cz) || !scheduler.isOwner(world, cx, cz)) {
            return Optional.empty();
        }
        return blocks.stateAt(world.getBlockAt(pos.x(), pos.y(), pos.z())).flatMap(CrankshaftBlocks::piece);
    }

    /** TFC {@code getCrankShaftAt(level, pos, direction)} on the live world: the base, empty when there is none. */
    static Optional<Block> crankAt(Block at, Direction direction, CustomBlocks blocks, Scheduler scheduler) {
        World world = at.getWorld();
        return CrankshaftRules.crankAt(pos(at), direction, p -> pieceAt(world, p, blocks, scheduler))
            .map(p -> world.getBlockAt(p.x(), p.y(), p.z()));
    }
}
