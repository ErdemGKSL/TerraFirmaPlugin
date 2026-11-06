/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.integration.craftengine.oreoverlay;

import dev.tfp.core.oreoverlay.CellLookup;
import dev.tfp.core.oreoverlay.OverlayKeys;
import dev.tfp.paper.oreoverlay.OreBlockSource;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.ToIntFunction;
import net.momirealms.craftengine.bukkit.api.BukkitAdaptor;
import net.momirealms.craftengine.bukkit.api.CraftEngineBlocks;
import net.momirealms.craftengine.bukkit.util.BlockStateUtils;
import net.momirealms.craftengine.core.block.ImmutableBlockState;
import net.momirealms.craftengine.core.world.CEWorld;
import net.momirealms.craftengine.core.world.chunk.CEChunk;
import net.momirealms.craftengine.core.world.chunk.CESection;
import net.momirealms.craftengine.core.world.chunk.PalettedContainer;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;

/**
 * The CraftEngine side of the ore overlays (PAR-INFRA-16). Decided from the CraftEngine 26.9.2 jar: CE keeps a chunk's
 * custom block states in per-section {@link PalettedContainer}s ({@link CEChunk#sections()}), so a chunk copy is one
 * {@link PalettedContainer#copy()} per section on the region thread (palette and packed longs, no per-block call), decoded
 * later on the CPU pool. The fallback of the plan (the deterministic vein layout plus per-position checks) is not needed.
 *
 * <p>Network states: an outgoing block-change id is either a vanilla state (CE's visual of a custom block, or a real
 * vanilla block), whose vanilla {@code canOcclude} is what the client culls with, or one of CE's server-side custom states
 * (when our packet listener runs before CE remaps it), whose visual state decides the occlusion and whose block id tells
 * the ore. Results are cached per id; the lookups are registry reads, safe on Netty threads.
 */
public final class CraftEngineOreSource implements OreBlockSource {
    private final Map<Integer, Integer> network = new ConcurrentHashMap<>();

    /** Reflective entry point of {@code OreOverlayModule}. */
    public CraftEngineOreSource() {
    }

    private static String idOf(ImmutableBlockState s) {
        return s.owner().value().id().toString();
    }

    @Override
    public ChunkCells copy(World world, int chunkX, int chunkZ, ToIntFunction<String> classify) {
        CEWorld ce = BukkitAdaptor.adapt(world).storageWorld();
        CEChunk chunk = ce == null ? null : ce.getChunkAtIfLoaded(chunkX, chunkZ);
        if (chunk == null) {
            return NO_CELLS;
        }
        CESection[] sections = chunk.sections();
        List<Section> copies = new ArrayList<>();
        for (CESection s : sections) {
            if (s != null && !s.isEmpty()) {
                copies.add(new Section(s.sectionY(), s.statesContainer().copy()));
            }
        }
        return new Cells(copies, world.getMinHeight(), classify);
    }

    @Override
    public int code(World world, int x, int y, int z, ToIntFunction<String> classify) {
        CEWorld ce = BukkitAdaptor.adapt(world).storageWorld();
        ImmutableBlockState s = ce == null ? null : ce.getBlockStateAtIfLoaded(x, y, z);
        return s == null || s.isEmpty() ? CellLookup.UNKNOWN : classify.applyAsInt(idOf(s));
    }

    @Override
    public String blockId(BlockData data) {
        ImmutableBlockState s = CraftEngineBlocks.getCustomBlockState(data);
        return s == null || s.isEmpty() ? null : idOf(s);
    }

    @Override
    public int networkState(int stateId, ToIntFunction<String> kindOf) {
        Integer cached = network.get(stateId);
        if (cached != null) {
            return cached;
        }
        int v = resolve(stateId, kindOf);
        network.put(stateId, v);
        return v;
    }

    private static int resolve(int stateId, ToIntFunction<String> kindOf) {
        Object nms;
        try {
            nms = BlockStateUtils.idToBlockState(stateId);
        } catch (RuntimeException e) {
            return -1;
        }
        if (nms == null) {
            return -1;
        }
        ImmutableBlockState custom = BlockStateUtils.getNullableCustomBlockState(nms);
        if (custom != null && !custom.isEmpty()) {
            Object visual = custom.visualBlockState() == null ? nms : custom.visualBlockState().minecraftState();
            int kind = kindOf.applyAsInt(idOf(custom));
            return (BlockStateUtils.isOcclude(visual) ? OCCLUDES : 0) | ((Math.max(-1, kind) + 1) << 1);
        }
        return BlockStateUtils.isOcclude(nms) ? OCCLUDES : 0;
    }

    private record Section(int sectionY, PalettedContainer<ImmutableBlockState> states) {
    }

    /** The copied sections; decoded lazily (one build reads it from one CPU task at a time). */
    private static final class Cells implements ChunkCells {
        private final List<Section> sections;
        private final int minY;
        private final int minSection;
        private final Section[] byIndex;
        private final ToIntFunction<String> classify;
        private final Map<ImmutableBlockState, Integer> codes = new IdentityHashMap<>();

        Cells(List<Section> sections, int minY, ToIntFunction<String> classify) {
            this.sections = sections;
            this.minY = minY;
            this.minSection = minY >> 4;
            int max = minSection;
            for (Section s : sections) {
                max = Math.max(max, s.sectionY());
            }
            this.byIndex = new Section[max - minSection + 1];
            for (Section s : sections) {
                if (s.sectionY() >= minSection) {
                    byIndex[s.sectionY() - minSection] = s;
                }
            }
            this.classify = classify;
        }

        private synchronized int classify(ImmutableBlockState s) {
            if (s == null || s.isEmpty()) {
                return CellLookup.UNKNOWN;
            }
            Integer c = codes.get(s);
            if (c == null) {
                c = classify.applyAsInt(idOf(s));
                codes.put(s, c);
            }
            return c;
        }

        @Override
        public int code(int localX, int y, int localZ) {
            int i = (y >> 4) - minSection;
            if (i < 0 || i >= byIndex.length || byIndex[i] == null) {
                return CellLookup.UNKNOWN;
            }
            return classify(byIndex[i].states().get(localX, y & 15, localZ));
        }

        @Override
        public int[] ores() {
            List<Integer> out = new ArrayList<>();
            for (Section s : sections) {
                PalettedContainer<ImmutableBlockState> c = s.states();
                if (!c.hasAny(st -> classify(st) >= 0)) {
                    continue;
                }
                int baseY = s.sectionY() << 4;
                for (int y = 0; y < 16; y++) {
                    for (int z = 0; z < 16; z++) {
                        for (int x = 0; x < 16; x++) {
                            if (classify(c.get(x, y, z)) >= 0) {
                                out.add(OverlayKeys.local(x, baseY + y, z, minY));
                            }
                        }
                    }
                }
            }
            return out.stream().mapToInt(Integer::intValue).sorted().toArray();
        }
    }
}
