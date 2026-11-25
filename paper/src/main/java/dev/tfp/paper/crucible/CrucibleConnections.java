/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/devices/CrucibleBlock.java @ c3b4d9536 (updateShape)
 */
package dev.tfp.paper.crucible;

import dev.tfp.core.neighbour.Neighbourhood;
import dev.tfp.core.neighbour.ShapeRule;
import dev.tfp.core.world.Direction;
import dev.tfp.paper.integration.BlockStateRef;
import java.util.List;
import java.util.Set;

/**
 * PAR-METAL-24: the crucible's {@code east, south, west, north} properties as a {@link ShapeRule} (decide async, apply
 * sync). TFC {@code CrucibleBlock.updateShape}: each side is true when the block on that side is a channel. A side whose
 * chunk is not loaded keeps its value. The block model draws TFC's {@code crucible_connection} nub on each true side.
 */
final class CrucibleConnections implements ShapeRule<BlockStateRef> {
    static final String CRUCIBLE = "tfp:crucible";
    static final String CHANNEL = "tfp:channel";
    private static final List<String> SIDES = List.of("east", "south", "west", "north");
    private static final List<Direction> DIRECTIONS = List.of(Direction.EAST, Direction.SOUTH, Direction.WEST, Direction.NORTH);

    @Override
    public String id() {
        return "tfp:crucible/connections";
    }

    @Override
    public Set<String> blockIds() {
        return Set.of(CRUCIBLE);
    }

    @Override
    public boolean appliesTo(BlockStateRef state) {
        return state.id().equals(CRUCIBLE);
    }

    @Override
    public BlockStateRef decide(Neighbourhood<BlockStateRef> n) {
        BlockStateRef center = n.center();
        if (center == null || !appliesTo(center)) {
            return null;
        }
        BlockStateRef next = center;
        for (int i = 0; i < SIDES.size(); i++) {
            Direction d = DIRECTIONS.get(i);
            if (!n.known(d) || !next.properties().containsKey(SIDES.get(i))) {
                continue;
            }
            BlockStateRef other = n.at(d);
            next = next.with(SIDES.get(i), Boolean.toString(other != null && other.id().equals(CHANNEL)));
        }
        return next.equals(center) ? null : next;
    }
}
