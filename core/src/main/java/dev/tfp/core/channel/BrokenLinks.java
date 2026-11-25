/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/ChannelBlockEntity.java @ c3b4d9536 (notifyBrokenLink),
 *       src/main/java/net/dries007/tfc/common/blockentities/MoldTableBlockEntity.java @ c3b4d9536 (finishFlow)
 */
package dev.tfp.core.channel;

import dev.tfp.core.crucible.ChannelFlow.Link;
import dev.tfp.core.world.BlockPos;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * PAR-METAL-13: TFC's {@code notifyBrokenLink}: when a mold table stops taking flow (it filled, was broken, lost its feed) or
 * a channel is broken, the notification walks upstream along the flow sources; every channel on the way loses that many
 * routes and, at zero, its flow. Pure: it returns the new state of each channel that changed.
 *
 * <p>TFC notifies the upstream channel first and then clears the channel itself; its {@code recursionVisiting} flag stops a
 * cycle. The walk here follows the same sources in the same order and stops at a channel it has already visited.
 */
public final class BrokenLinks {
    private BrokenLinks() {
    }

    /**
     * {@code channel.notifyBrokenLink(links)} at {@code start}, and upstream.
     *
     * @return the changed channels in walk order (a position never appears twice)
     */
    public static Map<BlockPos, ChannelData> propagate(FlowFacts facts, BlockPos start, int links) {
        Map<BlockPos, ChannelData> changed = new LinkedHashMap<>();
        Set<BlockPos> visited = new HashSet<>();
        BlockPos at = start;
        while (at != null && visited.add(at)) {
            ChannelData data = facts.channel(at);
            if (data == null) {
                break;
            }
            ChannelData next = data.afterBroken(links);
            if (!next.equals(data)) {
                changed.put(at, next);
            }
            Link source = data.source();
            at = source == null ? null : FlowCheck.offset(at, source.from(), source.distance());
        }
        return changed;
    }

    /**
     * {@code MoldTableBlockEntity.finishFlow}: the channel that feeds a mold table loses one route.
     *
     * @param table the mold table
     * @param feed  its flow source
     */
    public static Map<BlockPos, ChannelData> finishMold(FlowFacts facts, BlockPos table, Link feed) {
        return propagate(facts, FlowCheck.offset(table, feed.from(), feed.distance()), 1);
    }
}
