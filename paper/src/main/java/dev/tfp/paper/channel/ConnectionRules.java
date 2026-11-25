/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/devices/ChannelBlock.java @ c3b4d9536 (updateShape),
 *       src/main/java/net/dries007/tfc/common/blocks/devices/MoldTableBlock.java @ c3b4d9536 (updateShape)
 */
package dev.tfp.paper.channel;

import dev.tfp.core.neighbour.Neighbourhood;
import dev.tfp.core.neighbour.ShapeRule;
import dev.tfp.core.world.Direction;
import dev.tfp.paper.integration.BlockStateRef;
import java.util.List;
import java.util.Set;

/**
 * The connection properties of channels and mold tables as {@link ShapeRule}s (PAR-INFRA-10: decide async, apply sync).
 *
 * <p>Channel ({@code ChannelBlock.updateShape}): each of {@code north, east, south, west} is true when the cell on that side
 * is a channel, a crucible or a mold table; {@code down} when the cell below is one. TFC's DOWN ray looks up to 127 blocks
 * through air; the rule only sees the 3x3x3 snapshot, so {@code down} is true only for a connectable block directly below
 * (ledger divergence; the flow itself searches the full drop). A side whose chunk is not loaded keeps its value.
 *
 * <p>Mold table ({@code MoldTableBlock.updateShape}): each side is true when the neighbour is a channel.
 */
final class ConnectionRules {
    static final List<String> SIDES = List.of("north", "east", "south", "west");
    static final List<Direction> HORIZONTAL = List.of(Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST);

    private ConnectionRules() {
    }

    static ShapeRule<BlockStateRef> channel() {
        return new Rule("tfp:channel/connections", ChannelWorld.CHANNEL,
            Set.of(ChannelWorld.CHANNEL, ChannelWorld.CRUCIBLE, ChannelWorld.TABLE), true);
    }

    static ShapeRule<BlockStateRef> table() {
        return new Rule("tfp:mold_table/connections", ChannelWorld.TABLE, Set.of(ChannelWorld.CHANNEL), false);
    }

    private record Rule(String id, String block, Set<String> connectsTo, boolean down) implements ShapeRule<BlockStateRef> {
        @Override
        public Set<String> blockIds() {
            return Set.of(block);
        }

        @Override
        public boolean appliesTo(BlockStateRef state) {
            return state.id().equals(block);
        }

        @Override
        public BlockStateRef decide(Neighbourhood<BlockStateRef> n) {
            BlockStateRef center = n.center();
            if (center == null || !appliesTo(center)) {
                return null;
            }
            BlockStateRef next = center;
            for (int i = 0; i < SIDES.size(); i++) {
                next = connect(next, n, HORIZONTAL.get(i), SIDES.get(i));
            }
            if (down) {
                next = connect(next, n, Direction.DOWN, "down");
            }
            return next.equals(center) ? null : next;
        }

        private BlockStateRef connect(BlockStateRef state, Neighbourhood<BlockStateRef> n, Direction d, String property) {
            if (!n.known(d) || !state.properties().containsKey(property)) {
                return state;
            }
            BlockStateRef other = n.at(d);
            boolean connected = other != null && connectsTo.contains(other.id());
            return state.with(property, Boolean.toString(connected));
        }
    }
}
