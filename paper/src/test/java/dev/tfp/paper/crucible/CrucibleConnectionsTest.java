/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.crucible;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.neighbour.Neighbourhood;
import dev.tfp.core.world.Direction;
import dev.tfp.paper.integration.BlockStateRef;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CrucibleConnectionsTest {
    private final CrucibleConnections rule = new CrucibleConnections();

    private static BlockStateRef crucible(String... props) {
        BlockStateRef s = BlockStateRef.of(CrucibleConnections.CRUCIBLE,
            Map.of("east", "false", "south", "false", "west", "false", "north", "false"));
        for (int i = 0; i < props.length; i += 2) {
            s = s.with(props[i], props[i + 1]);
        }
        return s;
    }

    private static Neighbourhood<BlockStateRef> around(BlockStateRef center, Map<Direction, BlockStateRef> sides) {
        return Neighbourhood.of((dx, dy, dz) -> {
            if (dx == 0 && dy == 0 && dz == 0) {
                return center;
            }
            for (Direction d : Direction.values()) {
                if (d.dx() == dx && d.dy() == dy && d.dz() == dz) {
                    return sides.getOrDefault(d, BlockStateRef.of("minecraft:air"));
                }
            }
            return BlockStateRef.of("minecraft:air");
        });
    }

    @Test
    void aSideIsTrueOnlyBesideAChannel() {
        BlockStateRef channel = BlockStateRef.of(CrucibleConnections.CHANNEL);
        BlockStateRef next = rule.decide(around(crucible(), Map.of(Direction.EAST, channel, Direction.NORTH, channel,
            Direction.SOUTH, BlockStateRef.of("tfp:mold_table"), Direction.WEST, BlockStateRef.of("tfp:crucible"),
            Direction.UP, channel, Direction.DOWN, channel)));
        assertThat(next.properties()).containsEntry("east", "true").containsEntry("north", "true")
            .containsEntry("south", "false").containsEntry("west", "false");
    }

    @Test
    void aRemovedChannelClearsItsSide() {
        BlockStateRef next = rule.decide(around(crucible("east", "true", "west", "true"),
            Map.of(Direction.WEST, BlockStateRef.of(CrucibleConnections.CHANNEL))));
        assertThat(next.properties()).containsEntry("east", "false").containsEntry("west", "true");
    }

    @Test
    void aSettledCrucibleAsksForNoChange() {
        assertThat(rule.decide(around(crucible(), Map.of()))).isNull();
        assertThat(rule.decide(around(crucible("south", "true"),
            Map.of(Direction.SOUTH, BlockStateRef.of(CrucibleConnections.CHANNEL))))).isNull();
    }

    @Test
    void anUnloadedSideKeepsItsValueAndOtherBlocksAreIgnored() {
        BlockStateRef connected = crucible("north", "true");
        Neighbourhood<BlockStateRef> n = Neighbourhood.of((dx, dy, dz) -> dx == 0 && dy == 0 && dz == 0 ? connected
            : dz == -1 && dx == 0 && dy == 0 ? null : BlockStateRef.of("minecraft:air"));
        assertThat(rule.decide(n)).isNull();
        assertThat(rule.appliesTo(BlockStateRef.of("tfp:channel"))).isFalse();
        assertThat(rule.decide(around(BlockStateRef.of("minecraft:stone"), Map.of()))).isNull();
        assertThat(rule.blockIds()).containsExactly(CrucibleConnections.CRUCIBLE);
    }
}
