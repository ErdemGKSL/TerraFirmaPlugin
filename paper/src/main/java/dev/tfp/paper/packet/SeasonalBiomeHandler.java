/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.packet;

import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.mapper.MappedEntity;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.protocol.world.chunk.BaseChunk;
import com.github.retrooper.packetevents.protocol.world.chunk.Column;
import com.github.retrooper.packetevents.protocol.world.chunk.impl.v_1_18.Chunk_v1_18;
import com.github.retrooper.packetevents.protocol.world.chunk.palette.DataPalette;
import com.github.retrooper.packetevents.protocol.world.chunk.palette.GlobalPalette;
import com.github.retrooper.packetevents.protocol.world.chunk.palette.ListPalette;
import com.github.retrooper.packetevents.protocol.world.chunk.palette.SingletonPalette;
import com.github.retrooper.packetevents.protocol.world.chunk.storage.BitStorage;
import com.github.retrooper.packetevents.resources.ResourceLocation;
import com.github.retrooper.packetevents.util.Vector2i;
import com.github.retrooper.packetevents.util.mappings.IRegistry;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerChunkBiomes;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerChunkData;
import dev.tfp.paper.climate.SeasonalBiomeRemap;
import dev.tfp.paper.climate.SeasonalChunkTable;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * M2-SEA-02: swaps the biome palette of outgoing chunk packets for the seasonal variants (PacketEvents, Netty thread).
 *
 * <ul>
 *   <li>{@code CHUNK_DATA}: one table lookup per chunk ({@link SeasonalBiomeSource#table}); on a hit each section's 64
 *       cells are read, rewritten by {@link BiomePaletteRewrite} and written back with a fresh palette sized for the
 *       client (PacketEvents' own resize would give a global palette of the wrong width). No table: the packet passes and
 *       the source queues the computation and a resend.</li>
 *   <li>{@code CHUNK_BIOMES} (only {@code /fillbiome}): passes, and the listed chunks are queued for a resend.</li>
 * </ul>
 * The client's network ids come from its synchronized biome registry (M0-NET-01: they differ per version and with our
 * 2,000 datapack variants), resolved once per user and kept until the user's registry object changes or the player quits.
 */
public final class SeasonalBiomeHandler implements PacketHandler {
    public static final String NAME = "seasonal-biomes";
    private static final ResourceLocation BIOME_REGISTRY = ResourceLocation.minecraft("worldgen/biome");

    private record UserIds(Object registry, BiomePaletteRewrite.IdMap ids) {
    }

    private final SeasonalBiomeSource source;
    private final Logger log;
    private final ConcurrentHashMap<UUID, UserIds> users = new ConcurrentHashMap<>();
    private volatile boolean warnedMissing;

    public SeasonalBiomeHandler(SeasonalBiomeSource source, Logger log) {
        this.source = source;
        this.log = log;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public Set<String> sendTypes() {
        return Set.of("CHUNK_DATA", "CHUNK_BIOMES");
    }

    /** Drops the cached ids of a player who left. Any thread. */
    public void forget(UUID player) {
        users.remove(player);
    }

    @Override
    public PacketDecision onSend(PacketContext packet) {
        UUID player = packet.playerId();
        if (player == null || !source.active(player)) {
            return PacketDecision.PASS;
        }
        PacketSendEvent event = packet.nativeEvent(PacketSendEvent.class);
        if (packet.packetType().equals("CHUNK_BIOMES")) {
            for (Vector2i pos : new WrapperPlayServerChunkBiomes(event).getChunks().keySet()) {
                source.biomesChanged(player, pos.getX(), pos.getZ());
            }
            return PacketDecision.PASS;
        }
        WrapperPlayServerChunkData wrapper = new WrapperPlayServerChunkData(event);
        Column column = wrapper.getColumn();
        SeasonalChunkTable table = source.table(player, column.getX(), column.getZ());
        if (table == null) {
            return PacketDecision.PASS;
        }
        BaseChunk[] chunks = column.getChunks();
        if (chunks.length != table.sections()) {
            return PacketDecision.PASS; // another dimension height than the table's world
        }
        BiomePaletteRewrite.IdMap ids = ids(event.getUser(), player);
        if (ids == null) {
            return PacketDecision.PASS;
        }
        int[] cells = new int[SeasonalChunkTable.CELLS_PER_SECTION];
        boolean changed = false;
        for (int s = 0; s < chunks.length; s++) {
            if (chunks[s] instanceof Chunk_v1_18 section && section.getBiomeData() != null) {
                changed |= rewrite(section.getBiomeData(), cells, table, s, ids);
            }
        }
        return changed ? PacketDecision.MODIFIED : PacketDecision.PASS;
    }

    private static boolean rewrite(DataPalette palette, int[] cells, SeasonalChunkTable table, int section,
            BiomePaletteRewrite.IdMap ids) {
        for (int i = 0; i < cells.length; i++) {
            cells[i] = palette.get(i & 3, i >> 4, (i >> 2) & 3);
        }
        if (!BiomePaletteRewrite.rewriteSection(cells, table, section, ids)) {
            return false;
        }
        int distinct = BiomePaletteRewrite.distinct(cells);
        int bits = BiomePaletteRewrite.bits(distinct, ids.registrySize());
        if (bits == 0) {
            palette.palette = new SingletonPalette(cells[0]);
            palette.storage = null;
            return true;
        }
        BitStorage storage = new BitStorage(bits, cells.length);
        if (bits <= BiomePaletteRewrite.MAX_LIST_BITS) {
            ListPalette list = new ListPalette(bits);
            for (int i = 0; i < cells.length; i++) {
                storage.set(i, list.stateToId(cells[i]));
            }
            palette.palette = list;
        } else {
            for (int i = 0; i < cells.length; i++) {
                storage.set(i, cells[i]);
            }
            palette.palette = GlobalPalette.INSTANCE;
        }
        palette.storage = storage;
        return true;
    }

    /** The user's network ids, resolved from its synchronized registry once (and again if the registry is replaced). */
    private BiomePaletteRewrite.IdMap ids(User user, UUID player) {
        if (user == null) {
            return null;
        }
        ClientVersion version = user.getClientVersion();
        IRegistry<?> registry = user.getRegistry(BIOME_REGISTRY, version);
        if (registry == null) {
            return null;
        }
        UserIds cached = users.get(player);
        if (cached != null && cached.registry() == registry) {
            return cached.ids().usable() ? cached.ids() : null;
        }
        int[] base = new int[SeasonalBiomeRemap.BASE_COUNT];
        for (int i = 0; i < base.length; i++) {
            base[i] = idOf(registry, SeasonalBiomeRemap.baseIdOf(i), version);
        }
        int[] variants = new int[SeasonalBiomeRemap.VARIANT_COUNT];
        for (int i = 0; i < variants.length; i++) {
            variants[i] = idOf(registry, SeasonalBiomeRemap.variantIdOf(i), version);
        }
        BiomePaletteRewrite.IdMap ids = new BiomePaletteRewrite.IdMap(base, variants, registry.size());
        users.put(player, new UserIds(registry, ids));
        if (!ids.usable() && !warnedMissing) {
            warnedMissing = true;
            log.warning("Seasonal biomes: the client registry has none of the " + variants.length
                    + " tfp:seasonal variants (datapack not loaded?); chunks keep their base biomes");
        } else if (Arrays.stream(variants).anyMatch(id -> id < 0) && !warnedMissing) {
            warnedMissing = true;
            log.warning("Seasonal biomes: some tfp:seasonal variants are missing from the client registry; those cells keep their base biome");
        }
        return ids.usable() ? ids : null;
    }

    private static int idOf(IRegistry<?> registry, String name, ClientVersion version) {
        Object entry = registry.getByName(name);
        return entry instanceof MappedEntity mapped ? mapped.getId(version) : -1;
    }
}
