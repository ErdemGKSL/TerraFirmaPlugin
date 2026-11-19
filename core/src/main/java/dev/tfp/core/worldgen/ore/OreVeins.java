/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/world/feature/vein/VeinFeature.java @ c3b4d9536 (place: the projection, the near-lava
 * check, the density roll, the replaceable-rock check of IVeinConfig.getStateToGenerate and the weighted pick), with the
 * per-position random described below.
 */
package dev.tfp.core.worldgen.ore;

import dev.tfp.core.worldgen.random.PositionalRandom;
import dev.tfp.core.worldgen.random.XoroshiroRandomSource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The ore decision of a world: for a block position and the rock there, the vein block that replaces it, or nothing. Pure,
 * immutable and thread-safe; a {@link Chunk} is built per generated chunk and only answers for positions inside it.
 *
 * <p>The rules are TFC's: the veins are tried in the order of the vein tag ({@code #tfc:in_biome/veins}), then those of the
 * biome's own vein tags (the second pass of the montane veins, the river and collisional-mountain veins), which TFC runs in
 * the next generation step. Inside one type the veins come in {@link Veins#nearbyVeins} order. A vein that covers the
 * position rolls its chance; when the roll passes and the rock is one the vein replaces, the weighted pick chooses the block.
 * An ore or any other block that is not a rock is final. A pick that is itself a rock of {@code rockIds} (a dike, the marble
 * belt) becomes the rock that the later veins see, as the later features see it in TFC. When the roll fails or the rock is not
 * listed, the next vein gets its chance.
 *
 * <p>Divergence (ledger): TFC draws the density roll and the pick from the feature's sequential random, whose seed depends on
 * vanilla's decoration seed and the feature's index in the biome feature list. Here each pass of each vein type has a
 * positional random ({@code XoroshiroRandomSource(levelSeed ^ veinSeed).forkPositional().at(x, y, z)}; for a biome-tag pass
 * the source is salted and one long is drawn first, so that the second pass rolls independently), so the decision at a
 * position does not depend on the order in which chunks or positions are generated. The shape, the centres, the box, the
 * chance and the grade weights are TFC's exactly.
 */
public final class OreVeins {
    /** The high seed of the roll of a biome-tag pass, so that a second pass of a type does not repeat the first one's rolls. */
    private static final long BIOME_PASS_SALT = 0x5EC0ADA55L;

    private final long levelSeed;
    private final Slot[] slots;
    private final List<VeinConfig> types;
    private final List<ClusterVeinConfig> configs;
    private final Map<String, Integer> rockIndex;
    private final Map<String, Integer> oreIndex;
    private final List<String> oreIds;

    /**
     * A world with the veins of the main vein tag only.
     *
     * @param levelSeed the world seed
     * @param configs   the vein types in tag order
     * @param rockIds   the block id of each rock index the caller passes to {@link Chunk#decide} ({@code tfp:rock/raw/<name>})
     */
    public OreVeins(long levelSeed, List<? extends VeinConfig> configs, List<String> rockIds) {
        this(levelSeed, configs, Map.of(), rockIds);
    }

    /**
     * @param levelSeed  the world seed
     * @param configs    the vein types of the main tag ({@code in_biome/veins}), in tag order
     * @param biomeVeins the vein types of each biome vein tag ({@code in_biome/veins/<key>}: montane, river,
     *                   collisional_mountains), in tag order; the keys in the order in which a chunk with several tags runs them
     * @param rockIds    the block id of each rock index the caller passes to {@link Chunk#decide}
     */
    public OreVeins(long levelSeed, List<? extends VeinConfig> configs, Map<String, ? extends List<? extends VeinConfig>> biomeVeins,
                    List<String> rockIds) {
        this.levelSeed = levelSeed;
        final Map<String, Integer> rocks = new HashMap<>();
        for (int i = 0; i < rockIds.size(); i++) {
            rocks.put(rockIds.get(i), i);
        }
        this.rockIndex = Map.copyOf(rocks);
        final List<Slot> all = new ArrayList<>();
        final List<ClusterVeinConfig> clusters = new ArrayList<>();
        for (VeinConfig config : configs) {
            int cluster = -1;
            if (config instanceof ClusterVeinConfig c) {
                cluster = clusters.size();
                clusters.add(c);
            }
            all.add(slot(config, null, cluster, rockIds));
        }
        for (Map.Entry<String, ? extends List<? extends VeinConfig>> e : biomeVeins.entrySet()) {
            for (VeinConfig config : e.getValue()) {
                all.add(slot(config, e.getKey(), -1, rockIds));
            }
        }
        this.slots = all.toArray(Slot[]::new);
        this.types = all.stream().map(Slot::config).toList();
        this.configs = List.copyOf(clusters);
        final Map<String, Integer> ores = new LinkedHashMap<>();
        for (Slot slot : slots) {
            for (Weighted<VeinBlock> weighted : slot.config().blocks().values()) {
                for (VeinBlock block : weighted.values()) {
                    if (block.kind() == VeinBlock.Kind.ORE) {
                        ores.putIfAbsent(block.id(), ores.size());
                    }
                }
            }
        }
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

    private Slot slot(VeinConfig config, String tag, int cluster, List<String> rockIds) {
        final XoroshiroRandomSource source = tag == null
            ? new XoroshiroRandomSource(levelSeed ^ config.seed())
            : new XoroshiroRandomSource(levelSeed ^ config.seed(), BIOME_PASS_SALT);
        final List<Weighted<VeinBlock>> table = new ArrayList<>(rockIds.size());
        for (String rockId : rockIds) {
            table.add(config.blocks().get(rockId));
        }
        return new Slot(config, tag, cluster, PositionalRandom.fork(source), Collections.unmodifiableList(table));
    }

    public long levelSeed() {
        return levelSeed;
    }

    /**
     * The cluster vein types of the main tag, in tag order: the types that {@link Box#config()} and
     * {@link Chunk#highestOre} index (the types with indicators).
     */
    public List<ClusterVeinConfig> configs() {
        return configs;
    }

    /** Every pass of every vein type, in decision order: the main tag's types, then those of the biome vein tags. */
    public List<VeinConfig> types() {
        return types;
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

    /**
     * The veins of the main tag that reach a chunk and need no view of the world, with their boxes clipped to it. The
     * projected and near-lava types are left out: use {@link #chunk(int, int, Set, WorldView)} for them.
     */
    public Chunk chunk(int chunkX, int chunkZ) {
        return chunk(chunkX, chunkZ, Set.of(), null);
    }

    /**
     * The veins that reach a chunk, with their boxes clipped to it.
     *
     * @param biomeTags the biome vein tags of the chunk (keys of the constructor's {@code biomeVeins}): their types run
     *                  after the main tag's
     * @param view      the ground height and the lava of the world, or null to leave out the types that need them
     */
    public Chunk chunk(int chunkX, int chunkZ, Set<String> biomeTags, WorldView view) {
        final List<Entry> entries = new ArrayList<>();
        for (int i = 0; i < slots.length; i++) {
            final Slot slot = slots[i];
            final VeinConfig config = slot.config();
            if ((slot.tag() != null && !biomeTags.contains(slot.tag())) || (view == null && config.needsWorld())
                || config.blocks().isEmpty()) {
                continue;
            }
            for (Vein vein : Veins.nearbyVeins(levelSeed, chunkX, chunkZ, config)) {
                final Placed placed = Placed.of(chunkX, chunkZ, vein, config);
                if (placed != null) {
                    entries.add(new Entry(i, slot, placed));
                }
            }
        }
        return new Chunk(chunkX, chunkZ, entries.toArray(Entry[]::new), view);
    }

    /** One pass of one vein type. */
    private record Slot(VeinConfig config, String tag, int cluster, PositionalRandom rolls, List<Weighted<VeinBlock>> byRock) {}

    private record Entry(int index, Slot slot, Placed placed) {}

    /**
     * One vein's reach in a chunk: its box clipped to the chunk and the type's heights, and the offset of its projection.
     * The projected heights of the chunk's columns are cached on first use (the same value whichever thread writes it).
     */
    static final class Placed {
        /** {@link #relativeY}: the position is outside the vein's box. */
        static final int OUTSIDE = Integer.MIN_VALUE;

        private final Vein vein;
        private final int[] box;
        private final int offsetX;
        private final int offsetZ;
        private final int[] heights;

        private Placed(Vein vein, int[] box, int[] offset, boolean project) {
            this.vein = vein;
            this.box = box;
            this.offsetX = offset[0];
            this.offsetZ = offset[1];
            if (project) {
                heights = new int[256];
                Arrays.fill(heights, OUTSIDE);
            } else {
                heights = null;
            }
        }

        /** The vein's reach in a chunk, or null when its box does not touch the chunk. */
        static Placed of(int chunkX, int chunkZ, Vein vein, VeinConfig config) {
            final int[] box = Veins.box(chunkX, chunkZ, vein, config);
            if (box[0] > box[1] || box[2] > box[3] || box[4] > box[5]) {
                return null;
            }
            return new Placed(vein, box, Veins.projectionOffset(vein, config), config.project());
        }

        Vein vein() {
            return vein;
        }

        int[] box() {
            return box;
        }

        boolean containsColumn(int x, int z) {
            return x >= box[0] && x <= box[1] && z >= box[4] && z <= box[5];
        }

        /**
         * The position's y relative to the vein's centre, after undoing the projection; {@link #OUTSIDE} when the position is
         * outside the vein's box.
         */
        int relativeY(int x, int y, int z, VeinConfig config, WorldView view) {
            if (!containsColumn(x, z)) {
                return OUTSIDE;
            }
            int veinY = y;
            if (heights != null) {
                final int i = (x & 15) << 4 | (z & 15);
                int h = heights[i];
                if (h == OUTSIDE) {
                    h = view.surfaceHeight(offsetX + x, offsetZ + z);
                    heights[i] = h;
                }
                veinY = y - h;
            }
            return veinY >= box[2] && veinY <= box[3] ? veinY - vein.y() : OUTSIDE;
        }

        /** The chance that the vein writes a position: 0 outside its box or shape. */
        float chance(int x, int y, int z, VeinConfig config, WorldView view) {
            final int relative = relativeY(x, y, z, config, view);
            return relative == OUTSIDE ? 0 : Veins.chanceToGenerate(x - vein.x(), relative, z - vein.z(), vein, config);
        }
    }

    /**
     * One cluster vein's box in a chunk, inclusive.
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

    /** What the projected and near-lava veins read from the world. Must be safe to call from the deciding thread. */
    public interface WorldView {
        /**
         * The ground height of a column, as the ocean floor heightmap of world generation gives it: the y above the highest
         * block that blocks motion. The column can be outside the chunk (by up to 15 blocks).
         */
        int surfaceHeight(int x, int z);

        /** Whether a block is lava; asked at y = {@link Veins#LAVA_Y}, up to four blocks outside the chunk. */
        boolean isLava(int x, int y, int z);
    }

    /** The decisions of one chunk. Any thread. */
    public final class Chunk {
        private final int chunkX;
        private final int chunkZ;
        private final Entry[] entries;
        private final WorldView view;
        /** Per column: 0 unknown, 1 near lava, 2 not. Filled on first use; null when no vein asks. */
        private final byte[] nearLava;

        private Chunk(int chunkX, int chunkZ, Entry[] entries, WorldView view) {
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.entries = entries;
            this.view = view;
            boolean lava = false;
            for (Entry e : entries) {
                lava |= e.slot().config().nearLava();
            }
            this.nearLava = lava ? new byte[256] : null;
        }

        /** Whether no vein reaches this chunk (every decision is null). */
        public boolean isEmpty() {
            return entries.length == 0;
        }

        /** The number of (vein, chunk) boxes tried per position, for tests and statistics. */
        public int veinCount() {
            return entries.length;
        }

        /**
         * The cluster vein boxes of this chunk's main pass, in decision order: each covering vein, clipped to the chunk and its
         * type's heights.
         */
        public List<Box> boxes() {
            final List<Box> out = new ArrayList<>(entries.length);
            for (Entry e : entries) {
                if (e.slot().cluster() >= 0) {
                    final int[] b = e.placed().box();
                    out.add(new Box(e.slot().cluster(), (ClusterVein) e.placed().vein(), b[0], b[1], b[2], b[3], b[4], b[5]));
                }
            }
            return out;
        }

        /**
         * The highest y of a column of this chunk where the decision is a block of cluster vein type {@code config}, which is
         * TFC's {@code maxVeinY} of the column; {@link Integer#MIN_VALUE} when the type places nothing there.
         *
         * @param config the index of the type in {@link #configs()}
         * @param rocks  the rock index at a position, or a negative value where there is no rock (soil, air, water)
         */
        public int highestOre(int config, int x, int z, RockAt rocks) {
            int top = Integer.MIN_VALUE;
            int bottom = Integer.MAX_VALUE;
            for (Entry e : entries) {
                if (e.slot().cluster() == config && e.placed().containsColumn(x, z)) {
                    top = Math.max(top, e.placed().box()[3]);
                    bottom = Math.min(bottom, e.placed().box()[2]);
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

        private boolean isNearLava(int x, int z) {
            final int i = (x & 15) << 4 | (z & 15);
            byte known = nearLava[i];
            if (known == 0) {
                known = (byte) (Veins.isNearLava(x, z, view::isLava) ? 1 : 2);
                nearLava[i] = known;
            }
            return known == 1;
        }

        /**
         * The block that replaces the rock at a position of this chunk, or null to keep the rock. The result can be another
         * rock (a dike), or an ore of another rock than {@code rock} when a later vein replaced a dike's rock.
         *
         * @param rock the index of the rock at the position, in the {@code rockIds} order of the constructor
         * @throws IllegalArgumentException when the position is outside this chunk
         */
        public VeinBlock decide(int x, int y, int z, int rock) {
            if (x >> 4 != chunkX || z >> 4 != chunkZ) {
                throw new IllegalArgumentException("position " + x + "," + z + " is outside chunk " + chunkX + "," + chunkZ);
            }
            XoroshiroRandomSource roll = null;
            int rollSlot = -1;
            VeinBlock result = null;
            for (Entry e : entries) {
                final Placed placed = e.placed();
                if (!placed.containsColumn(x, z)) {
                    continue;
                }
                final VeinConfig config = e.slot().config();
                if (config.nearLava() && !isNearLava(x, z)) {
                    continue;
                }
                final float chance = placed.chance(x, y, z, config, view);
                if (chance <= 0) {
                    continue;
                }
                if (rollSlot != e.index()) {
                    roll = e.slot().rolls().at(x, y, z);
                    if (e.slot().tag() != null) {
                        // The first output of at() is a weak function of the position: without this draw, a second pass
                        // of a type rolls nearly the same floats as its first pass and covers ~13% fewer positions than TFC
                        roll.nextLong();
                    }
                    rollSlot = e.index();
                }
                if (roll.nextFloat() < chance) {
                    final Weighted<VeinBlock> weighted = e.slot().byRock().get(rock);
                    if (weighted != null) {
                        final VeinBlock block = weighted.get(roll.nextDouble());
                        if (block != null) {
                            final Integer newRock = block.kind() == VeinBlock.Kind.OTHER ? rockIndex.get(block.id()) : null;
                            if (newRock == null) {
                                return block;
                            }
                            rock = newRock;
                            result = block;
                        }
                    }
                }
            }
            return result;
        }
    }
}
