/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/ChannelBlockEntity.java @ c3b4d9536 (isFlowing),
 *       src/main/java/net/dries007/tfc/common/blockentities/MoldTableBlockEntity.java @ c3b4d9536 (isFlowing)
 */
package dev.tfp.core.channel;

import dev.tfp.core.crucible.ChannelFlow.Link;
import dev.tfp.core.world.BlockPos;
import dev.tfp.core.world.Direction;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * PAR-METAL-13: TFC's {@code isFlowing}: whether the chain of links from a channel or a mold table still reaches the
 * crucible. Pure over {@link FlowFacts}.
 *
 * <p>The walk is TFC's recursion: a channel with no flow is not flowing; a channel fed by another channel needs that
 * channel to exist, air in every cell of a drop between them, and the upstream channel flowing; a channel fed by the crucible
 * needs the crucible. TFC's {@code recursionVisiting} flag makes a cycle answer "not flowing"; a visited set does the same.
 * A fall of more than one block from above ({@code UP}, distance over 1) is reported in {@link Result#falls} so the caller
 * can hurt entities in the stream ({@code ChannelFlow.damageEntities}).
 */
public final class FlowCheck {
    private FlowCheck() {
    }

    /** A stream of molten metal falling onto {@code pos} from {@code blocks} blocks up (TFC {@code damageEntities(level, pos, n)}). */
    public record Fall(BlockPos pos, int blocks) {
    }

    /**
     * @param flowing whether the chain reaches the crucible
     * @param falls   the falls on the chain TFC would hurt entities in, nearest the checked cell first
     */
    public record Result(boolean flowing, List<Fall> falls) {
        public Result {
            falls = List.copyOf(falls);
        }
    }

    /** {@code ChannelBlockEntity.isFlowing()} at {@code pos}. */
    public static Result channel(FlowFacts facts, BlockPos pos) {
        List<Fall> falls = new ArrayList<>();
        boolean flowing = channel(facts, pos, new HashSet<>(), falls);
        return new Result(flowing, flowing ? falls : List.of());
    }

    /**
     * {@code MoldTableBlockEntity.isFlowing()}: the mold table at {@code pos} fed by {@code feed}: the feeding channel must
     * exist, the cells of a drop be air, and the channel be flowing.
     */
    public static Result mold(FlowFacts facts, BlockPos pos, Link feed) {
        BlockPos feeder = offset(pos, feed.from(), feed.distance());
        if (facts.channel(feeder) == null) {
            return new Result(false, List.of());
        }
        for (int i = 1; i < feed.distance(); i++) {
            if (!facts.isAir(offset(pos, feed.from(), i))) {
                return new Result(false, List.of());
            }
        }
        List<Fall> falls = new ArrayList<>();
        if (!channel(facts, feeder, new HashSet<>(), falls)) {
            return new Result(false, List.of());
        }
        if (feed.from() == Direction.UP && feed.distance() > 1) {
            falls.addFirst(new Fall(pos, feed.distance()));
        }
        return new Result(true, falls);
    }

    private static boolean channel(FlowFacts facts, BlockPos pos, Set<BlockPos> visiting, List<Fall> falls) {
        ChannelData data = facts.channel(pos);
        if (data == null || !data.hasFlow() || !visiting.add(pos)) {
            return false;
        }
        try {
            Link link = data.source();
            BlockPos expected = offset(pos, link.from(), link.distance());
            if (!data.fromChannel()) {
                return facts.isCrucible(expected);
            }
            if (facts.channel(expected) == null) {
                return false;
            }
            for (int i = 1; i < link.distance(); i++) {
                if (!facts.isAir(offset(pos, link.from(), i))) {
                    return false;
                }
            }
            if (channel(facts, expected, visiting, falls)) {
                if (link.from() == Direction.UP && link.distance() > 1) {
                    falls.addFirst(new Fall(pos, link.distance()));
                }
                return true;
            }
            return false;
        } finally {
            visiting.remove(pos);
        }
    }

    static BlockPos offset(BlockPos pos, Direction d, int n) {
        return pos.offset(d.dx() * n, d.dy() * n, d.dz() * n);
    }
}
