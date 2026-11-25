/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/wood/ToolRackBlock.java @ c3b4d9536 (updateShape, canSurvive)
 * src/main/java/net/dries007/tfc/common/blocks/ShelfBlock.java @ c3b4d9536 (updateShape, canSurvive)
 */
package dev.tfp.paper.rack;

import dev.tfp.core.chest.ChestSide;
import dev.tfp.core.neighbour.Neighbourhood;
import dev.tfp.core.neighbour.ShapeRule;
import dev.tfp.core.rack.ToolRackRules;
import dev.tfp.core.world.Direction;
import dev.tfp.paper.integration.BlockStateRef;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

/**
 * TFC {@code ToolRackBlock.updateShape} and {@code ShelfBlock.updateShape}: when the block a rack or a shelf hangs on is no
 * longer sturdy, it becomes air and the device framework drops its contents. Decided async on the neighbour-update runner from
 * block ids only: the whole block counts as sturdy (TFC asks {@code isFaceSturdy}; ledger).
 */
public record RackSupport(Predicate<String> solid) implements ShapeRule<BlockStateRef> {
    public static final String RULE_ID = "tfp:rack/support";
    static final BlockStateRef AIR = BlockStateRef.of("minecraft:air");

    public RackSupport {
        Objects.requireNonNull(solid, "solid");
    }

    @Override
    public String id() {
        return RULE_ID;
    }

    @Override
    public Set<String> blockIds() {
        Set<String> ids = new HashSet<>(RackBlocks.ids(RackBlocks.TOOL_RACK));
        ids.addAll(RackBlocks.ids(RackBlocks.SHELF));
        return Set.copyOf(ids);
    }

    @Override
    public boolean appliesTo(BlockStateRef state) {
        return RackBlocks.isOf(RackBlocks.TOOL_RACK, state.id()) || RackBlocks.isOf(RackBlocks.SHELF, state.id());
    }

    @Override
    public BlockStateRef decide(Neighbourhood<BlockStateRef> n) {
        BlockStateRef centre = n.center();
        if (centre == null || !appliesTo(centre)) {
            return null;
        }
        RackBlocks.Found found = RackBlocks.parse(centre).orElse(null);
        if (found == null) {
            return null;
        }
        ChestSide side = found.is(RackBlocks.TOOL_RACK) ? ToolRackRules.rackAttached(found.facing())
            : ToolRackRules.shelfAttached(found.facing());
        Direction d = direction(side);
        if (!n.known(d)) {
            return null;
        }
        BlockStateRef behind = n.at(d);
        return behind != null && solid.test(behind.id()) ? null : AIR;
    }

    /**
     * Whether a block id carries a rack or a shelf: a vanilla solid block or any custom block that is not itself a rack, shelf,
     * lectern or placed item (those have no full face). Static data only, any thread.
     */
    public static boolean solidId(String id) {
        if (RackBlocks.isOf(RackBlocks.TOOL_RACK, id) || RackBlocks.isOf(RackBlocks.SHELF, id)
                || RackBlocks.isOf(RackBlocks.LECTERN, id)) {
            return false;
        }
        return dev.tfp.paper.placed.PlacedSupport.solidId(id);
    }

    static Direction direction(ChestSide side) {
        return switch (side) {
            case NORTH -> Direction.NORTH;
            case EAST -> Direction.EAST;
            case SOUTH -> Direction.SOUTH;
            case WEST -> Direction.WEST;
        };
    }
}
