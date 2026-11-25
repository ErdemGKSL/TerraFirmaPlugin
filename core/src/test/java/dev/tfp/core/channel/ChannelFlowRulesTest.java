/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.channel;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.crucible.ChannelFlow.Link;
import dev.tfp.core.world.BlockPos;
import dev.tfp.core.world.Direction;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** PAR-METAL-13: the saved state, the flow check and the broken-link walk, by TFC's rules. */
class ChannelFlowRulesTest {
    /** A world of channels, air and one crucible. */
    private static final class World implements FlowFacts {
        final Map<BlockPos, ChannelData> channels = new HashMap<>();
        final Set<BlockPos> solid = new HashSet<>();
        final Set<BlockPos> crucibles = new HashSet<>();

        @Override
        public ChannelData channel(BlockPos pos) {
            return channels.get(pos);
        }

        @Override
        public boolean isAir(BlockPos pos) {
            return !channels.containsKey(pos) && !solid.contains(pos) && !crucibles.contains(pos);
        }

        @Override
        public boolean isCrucible(BlockPos pos) {
            return crucibles.contains(pos);
        }
    }

    private static final String FLUID = "tfc:metal/copper";

    /** crucible (0,0,0) - channel a (1,0,0) - channel b (2,0,0) - channel c (3,0,0), all flowing, one route each. */
    private static World line() {
        World w = new World();
        w.crucibles.add(new BlockPos(0, 0, 0));
        w.channels.put(new BlockPos(1, 0, 0), new ChannelData(new Link(Direction.WEST, 1), false, 2, FLUID));
        w.channels.put(new BlockPos(2, 0, 0), new ChannelData(new Link(Direction.WEST, 1), true, 2, FLUID));
        w.channels.put(new BlockPos(3, 0, 0), new ChannelData(new Link(Direction.WEST, 1), true, 1, FLUID));
        return w;
    }

    @Test
    void aChannelHasNoFlowUntilLinked() {
        assertThat(ChannelData.IDLE.hasFlow()).isFalse();
        ChannelData linked = ChannelData.IDLE.linked(new Link(Direction.NORTH, 3), true, 2, FLUID);
        assertThat(linked.hasFlow()).isTrue();
        assertThat(linked.flows()).isEqualTo(2);
    }

    @Test
    void breakingLinksCountsDownAndClearsTheFlowAtZeroKeepingTheFluid() {
        ChannelData two = new ChannelData(new Link(Direction.WEST, 1), true, 2, FLUID);
        ChannelData one = two.afterBroken(1);
        assertThat(one.hasFlow()).isTrue();
        assertThat(one.flows()).isEqualTo(1);
        ChannelData none = one.afterBroken(1);
        assertThat(none.hasFlow()).isFalse();
        assertThat(none.fluid()).isEqualTo(FLUID);
        // TFC: numFlows may go below zero when a channel is broken with more links than it counted
        assertThat(two.afterBroken(5).hasFlow()).isFalse();
    }

    @Test
    void theStateRoundTripsThroughItsCodec() {
        for (ChannelData d : new ChannelData[] {ChannelData.IDLE, new ChannelData(new Link(Direction.UP, 4), true, 3, FLUID),
            new ChannelData(null, false, 0, FLUID)}) {
            assertThat(ChannelData.CODEC.decode(ChannelData.CODEC.encode(d))).isEqualTo(d);
        }
        MoldTableData m = MoldTableData.IDLE.fed(new BlockPos(-3, 64, 9), FLUID, new Link(Direction.UP, 2));
        assertThat(MoldTableData.CODEC.decode(MoldTableData.CODEC.encode(m))).isEqualTo(m);
        assertThat(MoldTableData.CODEC.decode(MoldTableData.CODEC.encode(MoldTableData.IDLE))).isEqualTo(MoldTableData.IDLE);
        assertThat(m.finished()).isEqualTo(MoldTableData.IDLE);
    }

    @Test
    void aChainReachingTheCrucibleIsFlowing() {
        World w = line();
        assertThat(FlowCheck.channel(w, new BlockPos(3, 0, 0)).flowing()).isTrue();
        assertThat(FlowCheck.channel(w, new BlockPos(1, 0, 0)).flowing()).isTrue();
    }

    @Test
    void aMissingCrucibleOrChannelBreaksTheChain() {
        World w = line();
        w.crucibles.clear();
        assertThat(FlowCheck.channel(w, new BlockPos(3, 0, 0)).flowing()).isFalse();
        World v = line();
        v.channels.remove(new BlockPos(2, 0, 0));
        assertThat(FlowCheck.channel(v, new BlockPos(3, 0, 0)).flowing()).isFalse();
        assertThat(FlowCheck.channel(v, new BlockPos(1, 0, 0)).flowing()).isTrue();
    }

    @Test
    void aChannelWithoutFlowIsNotFlowing() {
        World w = line();
        w.channels.put(new BlockPos(3, 0, 0), ChannelData.IDLE);
        assertThat(FlowCheck.channel(w, new BlockPos(3, 0, 0)).flowing()).isFalse();
    }

    @Test
    void aDropNeedsAirBetweenTheCells() {
        World w = line();
        // channel d two blocks below c, fed from above at distance 2
        BlockPos d = new BlockPos(3, -2, 0);
        w.channels.put(d, new ChannelData(new Link(Direction.UP, 2), true, 1, FLUID));
        FlowCheck.Result open = FlowCheck.channel(w, d);
        assertThat(open.flowing()).isTrue();
        assertThat(open.falls()).containsExactly(new FlowCheck.Fall(d, 2));
        w.solid.add(new BlockPos(3, -1, 0));
        assertThat(FlowCheck.channel(w, d).flowing()).isFalse();
    }

    @Test
    void aOneBlockStepDownIsNotAFall() {
        World w = line();
        BlockPos d = new BlockPos(3, -1, 0);
        w.channels.put(d, new ChannelData(new Link(Direction.UP, 1), true, 1, FLUID));
        FlowCheck.Result r = FlowCheck.channel(w, d);
        assertThat(r.flowing()).isTrue();
        assertThat(r.falls()).isEmpty();
    }

    @Test
    void aCycleIsNotFlowing() {
        World w = new World();
        w.channels.put(new BlockPos(0, 0, 0), new ChannelData(new Link(Direction.EAST, 1), true, 1, FLUID));
        w.channels.put(new BlockPos(1, 0, 0), new ChannelData(new Link(Direction.WEST, 1), true, 1, FLUID));
        assertThat(FlowCheck.channel(w, new BlockPos(0, 0, 0)).flowing()).isFalse();
    }

    @Test
    void aMoldTableIsFlowingThroughItsFeedingChannel() {
        World w = line();
        BlockPos table = new BlockPos(3, 0, 1);
        Link feed = new Link(Direction.NORTH, 1);
        assertThat(FlowCheck.mold(w, table, feed).flowing()).isTrue();
        w.channels.remove(new BlockPos(3, 0, 0));
        assertThat(FlowCheck.mold(w, table, feed).flowing()).isFalse();
        // a table two blocks under the last channel: a fall of 2
        World f = line();
        BlockPos under = new BlockPos(3, -2, 0);
        FlowCheck.Result fall = FlowCheck.mold(f, under, new Link(Direction.UP, 2));
        assertThat(fall.flowing()).isTrue();
        assertThat(fall.falls()).containsExactly(new FlowCheck.Fall(under, 2));
    }

    @Test
    void breakingOneRouteWalksUpstreamAndClearsWhatReachesZero() {
        World w = line();
        // the mold at (3,0,1) was fed through c; one route ends: c loses 1 (to 0), b and a lose 1 (to 1)
        Map<BlockPos, ChannelData> changed = BrokenLinks.finishMold(w, new BlockPos(3, 0, 1), new Link(Direction.NORTH, 1));
        assertThat(changed.keySet()).containsExactly(new BlockPos(3, 0, 0), new BlockPos(2, 0, 0), new BlockPos(1, 0, 0));
        assertThat(changed.get(new BlockPos(3, 0, 0)).hasFlow()).isFalse();
        assertThat(changed.get(new BlockPos(2, 0, 0)).flows()).isEqualTo(1);
        assertThat(changed.get(new BlockPos(2, 0, 0)).hasFlow()).isTrue();
        assertThat(changed.get(new BlockPos(1, 0, 0)).flows()).isEqualTo(1);
    }

    @Test
    void breakingAChannelWithItsWholeCountClearsTheChainAbove() {
        World w = line();
        Map<BlockPos, ChannelData> changed = BrokenLinks.propagate(w, new BlockPos(2, 0, 0), 2);
        assertThat(changed.get(new BlockPos(2, 0, 0)).hasFlow()).isFalse();
        assertThat(changed.get(new BlockPos(1, 0, 0)).hasFlow()).isFalse();
        assertThat(changed).doesNotContainKey(new BlockPos(3, 0, 0));
    }

    @Test
    void theBrokenWalkStopsAtACycleAndAtAMissingChannel() {
        World w = new World();
        w.channels.put(new BlockPos(0, 0, 0), new ChannelData(new Link(Direction.EAST, 1), true, 1, FLUID));
        w.channels.put(new BlockPos(1, 0, 0), new ChannelData(new Link(Direction.WEST, 1), true, 1, FLUID));
        assertThat(BrokenLinks.propagate(w, new BlockPos(0, 0, 0), 1)).hasSize(2);
        assertThat(BrokenLinks.propagate(w, new BlockPos(9, 9, 9), 1)).isEmpty();
    }
}
