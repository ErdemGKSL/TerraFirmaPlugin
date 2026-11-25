/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/wood/VerticalSupportBlock.java @ c3b4d9536 (updateShape),
 * file: src/main/java/net/dries007/tfc/common/blocks/wood/HorizontalSupportBlock.java @ c3b4d9536 (updateShape)
 */
package dev.tfp.paper.beam;

import dev.tfp.core.beam.BeamCell;
import dev.tfp.core.beam.BeamRules;
import dev.tfp.core.beam.BeamShape;
import dev.tfp.core.neighbour.Neighbourhood;
import dev.tfp.core.neighbour.ShapeRule;
import dev.tfp.core.world.BlockPos;
import dev.tfp.core.world.Direction;
import java.util.Set;

/**
 * TFC's support beam {@code updateShape} as a shape rule of the beam runner: the connections to the four horizontal
 * neighbours, and air (the beam drops) when it lost its support ({@link BeamRules#settle}). Pure and idempotent; a needed
 * neighbour that is unknown (unloaded) keeps the beam as it is.
 */
public final class BeamShapeRule implements ShapeRule<BeamState> {
    public static final String RULE_ID = "tfp:beam/shape";
    private static final Direction[] NEEDED = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST, Direction.DOWN};

    @Override
    public String id() {
        return RULE_ID;
    }

    @Override
    public Set<String> blockIds() {
        return BeamBlocks.IDS;
    }

    @Override
    public boolean appliesTo(BeamState state) {
        return BeamBlocks.shape(state.state().id()) != BeamShape.NONE;
    }

    @Override
    public BeamState decide(Neighbourhood<BeamState> n) {
        BeamState centre = n.center();
        if (centre == null || !appliesTo(centre)) {
            return null;
        }
        for (Direction d : NEEDED) {
            if (!n.known(d)) {
                return null;
            }
        }
        BlockPos origin = new BlockPos(0, 0, 0);
        BeamRules.BeamUpdate update = BeamRules.settle(p -> {
            BeamState s = n.at(p.x(), p.y(), p.z());
            return s == null ? BeamCell.AIR : s.cell();
        }, origin);
        if (update.drop()) {
            return BeamState.air();
        }
        return new BeamState(BeamBlocks.withConnections(centre.state(), update.connections()), centre.sturdyUp(),
            centre.open());
    }

    /** TFC's default state: no connections; the runner decides them. */
    @Override
    public BeamState provisional(BeamState placed) {
        return new BeamState(BeamBlocks.withConnections(placed.state(), Set.of()), placed.sturdyUp(), placed.open());
    }
}
