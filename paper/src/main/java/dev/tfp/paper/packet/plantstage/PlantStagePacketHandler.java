/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/client/model/PlantBlockModel.java @ c3b4d9536 (getModelFromBlockState: the stage
 *       model a client draws for a plant block)
 */
package dev.tfp.paper.packet.plantstage;

import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.world.chunk.BaseChunk;
import com.github.retrooper.packetevents.protocol.world.chunk.Column;
import com.github.retrooper.packetevents.protocol.world.chunk.impl.v_1_18.Chunk_v1_18;
import com.github.retrooper.packetevents.util.Vector3i;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerBlockChange;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerChunkData;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerMultiBlockChange;
import dev.tfp.paper.packet.PacketContext;
import dev.tfp.paper.packet.PacketDecision;
import dev.tfp.paper.packet.PacketHandler;
import dev.tfp.paper.plant.lifecycle.PlantStageRemap;
import dev.tfp.paper.plant.lifecycle.PlantStageSource;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * PAR-WORLD-63: sends the dormant look of a plant ({@code tfp:plant/dormant_look}) instead of its blooming state in the
 * weeks TFC's {@code tfc:plant} model shows the dormant or dying model (PAR-WORLD-61's {@link PlantStageRemap}).
 *
 * <ul>
 *   <li>{@code CHUNK_DATA}: only sections whose palette names a plant state with a dormant look are decoded
 *       ({@link PlantSectionRewrite}).</li>
 *   <li>{@code BLOCK_CHANGE}, {@code MULTI_BLOCK_CHANGE}: each id through {@link PlantStageRemap#block}.</li>
 * </ul>
 * A packet without a changed id passes untouched (PASS: PacketEvents sends the original bytes). Netty thread: reads only
 * the source's snapshots and the pure climate, never the world. Runs on the server's ids: PacketEvents' encoder precedes
 * CraftEngine's in the pipeline (docs/spikes/M0-NET-01.md), so CraftEngine then maps the dormant look to its client state.
 */
public final class PlantStagePacketHandler implements PacketHandler {
    public static final String NAME = "plant-stage";

    private final PlantStageSource source;
    private final Logger log;
    private volatile boolean loggedFirst;

    public PlantStagePacketHandler(PlantStageSource source, Logger log) {
        this.source = source;
        this.log = log;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public Set<String> sendTypes() {
        return Set.of("CHUNK_DATA", "BLOCK_CHANGE", "MULTI_BLOCK_CHANGE");
    }

    @Override
    public PacketDecision onSend(PacketContext packet) {
        UUID player = packet.playerId();
        if (player == null) {
            return PacketDecision.PASS;
        }
        PlantStageRemap remap = source.remap();
        if (remap.size() == 0) {
            return PacketDecision.PASS;
        }
        PlantStageSource.Viewer viewer = source.viewer(player);
        if (viewer == null) {
            return PacketDecision.PASS;
        }
        PacketSendEvent event = packet.nativeEvent(PacketSendEvent.class);
        boolean changed = switch (packet.packetType()) {
            case "CHUNK_DATA" -> column(new WrapperPlayServerChunkData(event).getColumn(), remap, viewer);
            case "BLOCK_CHANGE" -> blockChange(new WrapperPlayServerBlockChange(event), remap, viewer);
            case "MULTI_BLOCK_CHANGE" -> multiBlockChange(new WrapperPlayServerMultiBlockChange(event), remap, viewer);
            default -> false;
        };
        return changed ? PacketDecision.MODIFIED : PacketDecision.PASS;
    }

    /** Rewrites the sections of one chunk column; true when any section changed. */
    boolean column(Column column, PlantStageRemap remap, PlantStageSource.Viewer viewer) {
        int originX = column.getX() << 4;
        int originZ = column.getZ() << 4;
        PlantStageRemap.View view = viewer.view(source.clock(), originZ + 8);
        if (view == null) {
            return false;
        }
        BaseChunk[] chunks = column.getChunks();
        boolean changed = false;
        for (int s = 0; s < chunks.length; s++) {
            if (chunks[s] instanceof Chunk_v1_18 section && section.getChunkData() != null) {
                changed |= PlantSectionRewrite.rewrite(section.getChunkData(), originX, viewer.minY() + (s << 4), originZ,
                    remap, viewer.climate(), view);
            }
        }
        if (changed && !loggedFirst) {
            loggedFirst = true;
            log.info("Plant stage looks: first chunk rewritten at " + column.getX() + ", " + column.getZ()
                + " (server ids reach the packet layer before CraftEngine maps them)");
        }
        return changed;
    }

    boolean blockChange(WrapperPlayServerBlockChange change, PlantStageRemap remap, PlantStageSource.Viewer viewer) {
        int id = change.getBlockId();
        if (!remap.rewrites(id)) {
            return false;
        }
        Vector3i p = change.getBlockPosition();
        int to = block(id, p.getX(), p.getY(), p.getZ(), remap, viewer);
        if (to == id) {
            return false;
        }
        change.setBlockID(to);
        return true;
    }

    boolean multiBlockChange(WrapperPlayServerMultiBlockChange change, PlantStageRemap remap,
            PlantStageSource.Viewer viewer) {
        return blocks(change.getBlocks(), remap, viewer);
    }

    /** Rewrites the entries of a {@code MULTI_BLOCK_CHANGE} (absolute coordinates); true when any changed. */
    boolean blocks(WrapperPlayServerMultiBlockChange.EncodedBlock[] blocks, PlantStageRemap remap,
            PlantStageSource.Viewer viewer) {
        boolean changed = false;
        for (WrapperPlayServerMultiBlockChange.EncodedBlock b : blocks) {
            int id = b.getBlockId();
            if (!remap.rewrites(id)) {
                continue;
            }
            int to = block(id, b.getX(), b.getY(), b.getZ(), remap, viewer);
            if (to != id) {
                b.setBlockId(to);
                changed = true;
            }
        }
        return changed;
    }

    /** The id to send for one block ({@code BLOCK_CHANGE} and each {@code MULTI_BLOCK_CHANGE} entry). */
    int block(int id, int x, int y, int z, PlantStageRemap remap, PlantStageSource.Viewer viewer) {
        if (!remap.rewrites(id)) {
            return id;
        }
        PlantStageRemap.View view = viewer.view(source.clock(), z);
        return view == null ? id : remap.block(id, x, y, z, viewer.climate(), view);
    }
}
