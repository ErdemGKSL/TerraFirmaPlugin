/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/MoldTableBlockEntity.java @ c3b4d9536 (the saved state:
 *       sourcePosition, flowSource, fluid; setSource, finishFlow)
 */
package dev.tfp.core.channel;

import dev.tfp.core.crucible.ChannelFlow.Link;
import dev.tfp.core.persist.VersionedCodec;
import dev.tfp.core.world.BlockPos;
import dev.tfp.core.world.Direction;
import java.util.Objects;

/**
 * PAR-METAL-13: the state of one mold table besides its two slots (mold, output): the crucible it draws from, the fluid it
 * expects and the link to the channel that feeds it. All three are present together or none. Immutable, any thread.
 *
 * @param source the crucible position (TFC {@code sourcePosition}), null while the table has no flow
 * @param fluid  TFC {@code fluid}: the fluid the flow carries ("" for none)
 * @param feed   TFC {@code flowSource}: the direction of the feeding channel and the distance to it
 */
public record MoldTableData(BlockPos source, String fluid, Link feed) {
    public static final MoldTableData IDLE = new MoldTableData(null, "", null);

    public static final VersionedCodec<MoldTableData> CODEC = VersionedCodec.<MoldTableData>builder("mold_table", 1)
        .build((d, out) -> {
            if (d.source != null) {
                out.putInt("sx", d.source.x()).putInt("sy", d.source.y()).putInt("sz", d.source.z())
                    .putString("fluid", d.fluid).putString("from", d.feed.from().name()).putInt("distance", d.feed.distance());
            }
        }, in -> {
            if (!in.has("sx") || !in.has("from")) {
                return IDLE;
            }
            return new MoldTableData(new BlockPos(in.getInt("sx", 0), in.getInt("sy", 0), in.getInt("sz", 0)),
                in.getString("fluid", ""), new Link(Direction.valueOf(in.getString("from", "DOWN")),
                    Math.max(1, in.getInt("distance", 1))));
        });

    public MoldTableData {
        Objects.requireNonNull(fluid, "fluid");
        if ((source == null) != (feed == null)) {
            throw new IllegalArgumentException("a mold table has its source and its feed together or neither");
        }
    }

    /** TFC {@code hasSource}. */
    public boolean hasSource() {
        return source != null;
    }

    /** TFC {@code setSource}. */
    public MoldTableData fed(BlockPos crucible, String flowFluid, Link link) {
        return new MoldTableData(Objects.requireNonNull(crucible, "crucible"), flowFluid, link);
    }

    /** TFC {@code finishFlow}'s own part: the source, fluid and feed are cleared. */
    public MoldTableData finished() {
        return IDLE;
    }
}
