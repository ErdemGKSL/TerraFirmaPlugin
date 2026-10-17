/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/world/feature/vein/VeinFeature.java @ c3b4d9536 (place: the density roll, the
 * replaceable-rock check of IVeinConfig.getStateToGenerate and the weighted pick), with the per-position random described below.
 */
package dev.tfp.core.worldgen.ore;

import dev.tfp.core.worldgen.random.PositionalRandom;
import dev.tfp.core.worldgen.random.XoroshiroRandomSource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The ore decision of a world: for a block position and the rock there, the vein block that replaces it, or nothing. Pure,
 * immutable and thread-safe; a {@link Chunk} is built per generated chunk and only answers for positions inside it.
 *
 * <p>The rules are TFC's: the veins are tried in the order of the vein tag ({@code #tfc:in_biome/veins}), and inside one type
 * in {@link ClusterVeins#nearbyVeins} order. A vein that covers the position rolls its density; when the roll passes and the
 * rock is one the vein replaces, the weighted pick chooses the block and the decision is final. When the roll fails or the rock
 * is not listed, the next vein gets its chance, as the later features see the rock still in place in TFC.
 *
 * <p>Divergence (ledger): TFC draws the density roll and the pick from the feature's sequential random, whose seed depends on
 * vanilla's decoration seed and the feature's index in the biome feature list. Here each vein type has a positional random
 * ({@code XoroshiroRandomSource(levelSeed ^ veinSeed).forkPositional().at(x, y, z)}), so the decision at a position does not
 * depend on the order in which chunks or positions are generated. The shape, the centres, the box, the density and the grade
 * weights are TFC's exactly.
 */
public final class OreVeins {
    private final long levelSeed;
    private final List<ClusterVeinConfig> configs;
    private final PositionalRandom[] rolls;
    /** Per configuration, the weighted blocks per rock index (null where the rock is not replaced). */
    private final List<List<Weighted<VeinBlock>>> byRock;
    private final Map<String, Integer> oreIndex;
    private final List<String> oreIds;

    /**
     * @param levelSeed the world seed
     * @param configs   the cluster vein types in tag order
     * @param rockIds   the block id of each rock index the caller passes to {@link Chunk#decide} ({@code tfp:rock/raw/<name>})
     */
    public OreVeins(long levelSeed, List<ClusterVeinConfig> configs, List<String> rockIds) {
        this.levelSeed = levelSeed;
        this.configs = List.copyOf(configs);
        this.rolls = new PositionalRandom[this.configs.size()];
        final List<List<Weighted<VeinBlock>>> tables = new ArrayList<>();
        final Map<String, Integer> ores = new LinkedHashMap<>();
        for (int i = 0; i < this.configs.size(); i++) {
            final ClusterVeinConfig config = this.configs.get(i);
            rolls[i] = PositionalRandom.fork(new XoroshiroRandomSource(levelSeed ^ config.seed()));
            final List<Weighted<VeinBlock>> table = new ArrayList<>(rockIds.size());
            for (String rockId : rockIds) {
                table.add(config.blocks().get(rockId));
            }
            tables.add(Collections.unmodifiableList(table));
            for (Weighted<VeinBlock> weighted : config.blocks().values()) {
                for (VeinBlock block : weighted.values()) {
                    if (block.kind() == VeinBlock.Kind.ORE) {
                        ores.putIfAbsent(block.id(), ores.size());
                    }
                }
            }
        }
        this.byRock = List.copyOf(tables);
        // Sorted ids give a stable palette whatever the tag order
        final List<String> sorted = new ArrayList<>(ores.keySet());
        sorted.sort(null);
        this.oreIds = List.copyOf(sorted);
        final Map<String, Integer> index = new HashMap<>();
        for (int i = 0; i < sorted.size(); i++) {
            index.put(sorted.get(i), i);
        }
        this.oreIndex = Map.copyOf(index);
    }

    public long levelSeed() {
        return levelSeed;
    }

    public List<ClusterVeinConfig> configs() {
        return configs;
    }

    /** Every ore block id a decision can return, sorted: the ore palette. */
    public List<String> oreIds() {
        return oreIds;
    }

    /** The index of an ore block in {@link #oreIds()}, or -1 for a block that is not an ore ({@link VeinBlock.Kind#DEPOSIT}). */
    public int oreIndex(VeinBlock block) {
        final Integer i = oreIndex.get(block.id());
        return i == null ? -1 : i;
    }

    /** The veins that reach a chunk, with their boxes clipped to it. */
    public Chunk chunk(int chunkX, int chunkZ) {
        final List<Entry> entries = new ArrayList<>();
        for (int i = 0; i < configs.size(); i++) {
            final ClusterVeinConfig config = configs.get(i);
            for (ClusterVein vein : ClusterVeins.nearbyVeins(levelSeed, chunkX, chunkZ, config)) {
                final int[] box = ClusterVeins.box(chunkX, chunkZ, vein, config);
                if (box[0] <= box[1] && box[2] <= box[3] && box[4] <= box[5]) {
                    entries.add(new Entry(i, config, vein, box));
                }
            }
        }
        return new Chunk(chunkX, chunkZ, entries.toArray(Entry[]::new));
    }

    private record Entry(int config, ClusterVeinConfig settings, ClusterVein vein, int[] box) {
        boolean contains(int x, int y, int z) {
            return x >= box[0] && x <= box[1] && y >= box[2] && y <= box[3] && z >= box[4] && z <= box[5];
        }
    }

    /**
     * One vein's box in a chunk, inclusive.
     *
     * @param config the index of the vein type in {@link #configs()}
     * @param vein   the vein
     */
    public record Box(int config, ClusterVein vein, int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {}

    /** The rock at a block position, as a rock index, or a negative value where the position holds no rock. */
    @FunctionalInterface
    public interface RockAt {
        int rock(int x, int y, int z);
    }

    /** The decisions of one chunk. Immutable; any thread. */
    public final class Chunk {
        private final int chunkX;
        private final int chunkZ;
        private final Entry[] entries;

        private Chunk(int chunkX, int chunkZ, Entry[] entries) {
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.entries = entries;
        }

        /** Whether no vein reaches this chunk (every decision is null). */
        public boolean isEmpty() {
            return entries.length == 0;
        }

        /** The number of (vein, chunk) boxes tried per position, for tests and statistics. */
        public int veinCount() {
            return entries.length;
        }

        /** The vein boxes of this chunk, in decision order: each covering vein, clipped to the chunk and its type's heights. */
        public List<Box> boxes() {
            final List<Box> out = new ArrayList<>(entries.length);
            for (Entry e : entries) {
                out.add(new Box(e.config(), e.vein(), e.box()[0], e.box()[1], e.box()[2], e.box()[3], e.box()[4], e.box()[5]));
            }
            return out;
        }

        /**
         * The highest y of a column of this chunk where the decision is a block of vein type {@code config}, which is TFC's
         * {@code maxVeinY} of the column; {@link Integer#MIN_VALUE} when the type places nothing there.
         *
         * @param rocks the rock index at a position, or a negative value where there is no rock (soil, air, water)
         */
        public int highestOre(int config, int x, int z, RockAt rocks) {
            int top = Integer.MIN_VALUE;
            int bottom = Integer.MAX_VALUE;
            for (Entry e : entries) {
                if (e.config() == config && x >= e.box()[0] && x <= e.box()[1] && z >= e.box()[4] && z <= e.box()[5]) {
                    top = Math.max(top, e.box()[3]);
                    bottom = Math.min(bottom, e.box()[2]);
                }
            }
            final String name = configs.get(config).name();
            for (int y = top; y >= bottom && top != Integer.MIN_VALUE; y--) {
                final int rock = rocks.rock(x, y, z);
                if (rock >= 0) {
                    final VeinBlock block = decide(x, y, z, rock);
                    if (block != null && block.vein().equals(name)) {
                        return y;
                    }
                }
            }
            return Integer.MIN_VALUE;
        }

        /**
         * The block that replaces the rock at a position of this chunk, or null to keep the rock.
         *
         * @param rock the index of the rock at the position, in the {@code rockIds} order of the constructor
         * @throws IllegalArgumentException when the position is outside this chunk
         */
        public VeinBlock decide(int x, int y, int z, int rock) {
            if (x >> 4 != chunkX || z >> 4 != chunkZ) {
                throw new IllegalArgumentException("position " + x + "," + z + " is outside chunk " + chunkX + "," + chunkZ);
            }
            XoroshiroRandomSource roll = null;
            int rollConfig = -1;
            for (Entry e : entries) {
                if (!e.contains(x, y, z)) {
                    continue;
                }
                final ClusterVein vein = e.vein();
                final float chance = ClusterVeins.chanceToGenerate(x - vein.x(), y - vein.y(), z - vein.z(), vein, e.settings());
                if (chance <= 0) {
                    continue;
                }
                if (rollConfig != e.config()) {
                    roll = rolls[e.config()].at(x, y, z);
                    rollConfig = e.config();
                }
                if (roll.nextFloat() < chance) {
                    final Weighted<VeinBlock> weighted = byRock.get(e.config()).get(rock);
                    if (weighted != null) {
                        final VeinBlock block = weighted.get(roll.nextDouble());
                        if (block != null) {
                            return block;
                        }
                    }
                }
            }
            return null;
        }
    }
}
