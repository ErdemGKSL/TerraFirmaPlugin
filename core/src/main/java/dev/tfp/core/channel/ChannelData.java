/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/ChannelBlockEntity.java @ c3b4d9536 (the saved state:
 *       flowSource, useLongRenderBox = isConnectedToAnotherChannel, numFlowsOut, texture = fluid; setLinkProperties,
 *       notifyBrokenLink)
 */
package dev.tfp.core.channel;

import dev.tfp.core.crucible.ChannelFlow.Link;
import dev.tfp.core.persist.VersionedCodec;
import dev.tfp.core.world.Direction;
import java.util.Objects;

/**
 * PAR-METAL-13: the state of one casting channel: where its flow comes from, how many routes pass through it and the fluid
 * it last carried. Immutable, any thread.
 *
 * @param source           TFC {@code flowSource}: the direction of the feeding cell and the distance to it; null while the
 *                         channel has no flow
 * @param fromChannel      TFC {@code isConnectedToAnotherChannel}: the feeding cell is a channel, not the crucible
 * @param flows            TFC {@code numFlows}: the routes through this channel
 * @param fluid            TFC {@code fluid}: the fluid of the last flow (kept after the flow ends), empty for none yet
 */
public record ChannelData(Link source, boolean fromChannel, int flows, String fluid) {
    /** A channel that never carried anything. */
    public static final ChannelData IDLE = new ChannelData(null, false, 0, "");

    public static final VersionedCodec<ChannelData> CODEC = VersionedCodec.<ChannelData>builder("channel", 1)
        .build((d, out) -> {
            out.putBoolean("fromChannel", d.fromChannel).putInt("flows", d.flows).putString("fluid", d.fluid);
            if (d.source != null) {
                out.putString("from", d.source.from().name()).putInt("distance", d.source.distance());
            }
        }, in -> {
            Link source = in.has("from")
                ? new Link(Direction.valueOf(in.getString("from", "DOWN")), Math.max(1, in.getInt("distance", 1))) : null;
            return new ChannelData(source, in.getBoolean("fromChannel", false), Math.max(0, in.getInt("flows", 0)),
                in.getString("fluid", ""));
        });

    public ChannelData {
        Objects.requireNonNull(fluid, "fluid");
    }

    /** TFC {@code hasFlow}: the flow source is present. */
    public boolean hasFlow() {
        return source != null;
    }

    /** TFC {@code setLinkProperties}. */
    public ChannelData linked(Link link, boolean fromChannelFlag, int numFlows, String flowFluid) {
        return new ChannelData(Objects.requireNonNull(link, "link"), fromChannelFlag, numFlows, flowFluid);
    }

    /**
     * TFC {@code notifyBrokenLink} on this channel: the route count drops by {@code links}; at zero or below the flow
     * source is cleared (the fluid is kept). The notification of the upstream channel is {@link BrokenLinks}'s.
     */
    public ChannelData afterBroken(int links) {
        int left = flows - links;
        return new ChannelData(left <= 0 ? null : source, fromChannel, left, fluid);
    }
}
