/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.worldgen.ore;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.worldgen.deposit.Metaballs2D;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Golden values of TFC's disc, kaolin disc and pipe veins and of the montane second pass at the pinned commit (c3b4d9536), for
 * every such type of the shipped data (extends {@link OreVeinGoldenTest}, which covers the cluster veins).
 *
 * <p>The oracle ({@link VeinTypesGolden}) is TFC's {@code VeinFeature}, {@code DiscVeinFeature}, {@code KaolinDiscVeinFeature},
 * {@code PipeVeinFeature} and the config records with the method bodies pasted unchanged, TFC's {@code Metaballs2D},
 * {@code Metaballs3D}, {@code OpenSimplex3D}, {@code FastNoiseLite} and {@code Helpers.hash/uniform}, compiled against
 * Minecraft 1.21.11's {@code RandomSource}, {@code XoroshiroRandomSource}, {@code RandomSupport}, {@code BlockPos},
 * {@code BoundingBox} and {@code Mth} (with 1.21.1's float {@code Mth.sin/cos}, as TFC runs them). The level is synthetic:
 * the ocean floor height of column (x, z) is {@code 70 + floorMod(3x + 5z, 9)}, lava is at y = -55 where
 * {@code floorMod(7x + 13z, 97) == 0}, every block is rock (for the kaolin disc: grass at height - 1, dirt down to height - 4,
 * rock below, air above). The vein JSON was TFC's {@code data/tfc/worldgen/configured_feature/vein/}. Nothing ran on a server.
 * <ul>
 *   <li>{@code SEED name seed}: {@code VeinConfig.hash(random_name)}.</li>
 *   <li>{@code VEIN levelSeed name count hash first}: {@code getVeinsAtChunk} over chunks x, z in [-16, 15] (x outer): the
 *       count, an FNV-style hash of each vein's chunk, centre and shape (the disc's {@code Metaballs2D.Ball} records; the pipe's
 *       sign, skew direction, skew and slant), and the first vein.</li>
 *   <li>{@code MASK levelSeed name (chunk count:hash)x3}: {@code VeinFeature.place} for the first vein's chunk, its east and
 *       its north-west neighbour, with a random whose rolls always pass: the number of {@code setBlock} calls and a hash of
 *       their positions (and, for kaolin, the clay) in call order. This checks the shape, centre, box, height clamp,
 *       projection offset, near-lava check and the kaolin colours.</li>
 *   <li>{@code STAT levelSeed name total}: the 100-chunk statistic, chunks x, z in [-5, 4], one fresh level per chunk, the real
 *       rolls of one sequential random: the number of distinct positions written.</li>
 *   <li>{@code STAT2 levelSeed name total}: the same for a montane cluster type that runs twice per chunk (main vein tag and
 *       {@code #in_biome/veins/montane}).</li>
 * </ul>
 */
class VeinTypesGoldenTest {
    /** The tolerance of the distribution statistic (PAR-WORLD-08: within 10% of TFC). */
    static final double TOLERANCE = 0.10;
    /** Below this many TFC positions over the four seeds, the statistic is too noisy for 10% and gets 4 sigma instead. */
    static final int STAT_MIN = 2000;

    static int height(int x, int z) {
        return 70 + Math.floorMod(3 * x + 5 * z, 9);
    }

    static boolean lava(int x, int z) {
        return Math.floorMod(7 * x + 13 * z, 97) == 0;
    }

    static final OreVeins.WorldView VIEW = new OreVeins.WorldView() {
        @Override
        public int surfaceHeight(int x, int z) {
            return height(x, z);
        }

        @Override
        public boolean isLava(int x, int y, int z) {
            return y == Veins.LAVA_Y && lava(x, z);
        }
    };

    /** The synthetic level's soil for the kaolin disc. */
    static KaolinDisc.Soil soil(int x, int y, int z) {
        final int h = height(x, z);
        if (y == h - 1) {
            return KaolinDisc.Soil.GRASS;
        }
        return y < h && y >= h - 4 ? KaolinDisc.Soil.REPLACEABLE : KaolinDisc.Soil.OTHER;
    }

    static List<String[]> rows(String kind) {
        final List<String[]> out = new ArrayList<>();
        for (String line : VeinTypesGolden.ROWS.strip().split("\n")) {
            final String[] parts = line.strip().split(" ", -1);
            if (parts[0].equals(kind)) {
                out.add(parts);
            }
        }
        return out;
    }

    /** Every vein type of the shipped data by name. */
    static Map<String, VeinConfig> configs() {
        final Map<String, VeinConfig> out = new LinkedHashMap<>();
        try (Stream<Path> files = Files.list(VeinTestData.dataRoot().resolve(VeinData.FEATURE_DIR))) {
            for (Path file : files.sorted().toList()) {
                final String name = file.getFileName().toString().replace(".json", "");
                out.put(name, VeinData.parseAny(name, VeinTestData.json(file)));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out;
    }

    @Test
    void everyTypeOfTheDataParsesAndIsCovered() {
        final Map<String, VeinConfig> configs = configs();
        final Set<String> golden = new HashSet<>();
        rows("SEED").forEach(r -> golden.add(r[1]));
        final Set<String> nonCluster = new HashSet<>();
        configs.forEach((name, c) -> {
            if (!(c instanceof ClusterVeinConfig)) {
                nonCluster.add(name);
            }
        });
        assertThat(golden).isEqualTo(nonCluster).hasSize(19);
        assertThat(configs.values()).filteredOn(c -> c instanceof PipeVeinConfig).hasSize(5);
        assertThat(configs.values()).filteredOn(c -> c instanceof DiscVeinConfig d && d.kaolin()).hasSize(1);
    }

    @Test
    void seedsMatchTfc() {
        final Map<String, VeinConfig> configs = configs();
        for (String[] r : rows("SEED")) {
            assertThat(configs.get(r[1]).seed()).as(r[1]).isEqualTo(Long.parseLong(r[2]));
        }
    }

    @Test
    void veinsMatchTfc() {
        final Map<String, VeinConfig> configs = configs();
        final List<String[]> rows = rows("VEIN");
        assertThat(rows).hasSize(19 * 4);
        for (String[] row : rows) {
            final long levelSeed = Long.parseLong(row[1]);
            final VeinConfig config = configs.get(row[2]);
            long h = 0xcbf29ce484222325L;
            int count = 0;
            String first = "-";
            for (int cx = -16; cx < 16; cx++) {
                for (int cz = -16; cz < 16; cz++) {
                    final Vein v = Veins.veinAtChunk(levelSeed, cx, cz, config);
                    if (v == null) {
                        continue;
                    }
                    h = OreVeinGoldenTest.mix(OreVeinGoldenTest.mix(OreVeinGoldenTest.mix(OreVeinGoldenTest.mix(
                        OreVeinGoldenTest.mix(h, cx), cz), v.x()), v.y()), v.z());
                    final String d = describe(v);
                    for (char ch : d.toCharArray()) {
                        h = OreVeinGoldenTest.mix(h, ch);
                    }
                    if (count == 0) {
                        first = cx + "," + cz + " " + v.x() + "," + v.y() + "," + v.z() + " " + d;
                    }
                    count++;
                }
            }
            final String actual = String.join(" ", "VEIN", row[1], row[2], Integer.toString(count), Long.toHexString(h), first);
            assertThat(actual).as("seed %s vein %s", row[1], row[2]).isEqualTo(String.join(" ", row));
        }
    }

    @Test
    void placementMasksMatchTfc() {
        final Map<String, VeinConfig> configs = configs();
        final List<String[]> rows = rows("MASK");
        assertThat(rows).hasSize(19 * 4);
        int nonEmpty = 0;
        for (String[] row : rows) {
            final long levelSeed = Long.parseLong(row[1]);
            final VeinConfig config = configs.get(row[2]);
            final StringBuilder actual = new StringBuilder("MASK " + row[1] + " " + row[2]);
            for (int i = 3; i < row.length; i += 2) {
                final String[] chunk = row[i].split(",");
                final long[] mask = mask(levelSeed, Integer.parseInt(chunk[0]), Integer.parseInt(chunk[1]), config);
                actual.append(' ').append(row[i]).append(' ').append(mask[0]).append(':').append(Long.toHexString(mask[1]));
                nonEmpty += mask[0] > 0 ? 1 : 0;
            }
            assertThat(actual.toString()).isEqualTo(String.join(" ", row));
        }
        assertThat(nonEmpty).as("chunks with blocks in the golden set").isGreaterThan(150);
    }

    /**
     * The kaolin mask through {@link KaolinDisc.Chunk#decide} (with every chance 1 the positional roll always passes, as the
     * oracle's): the same positions and colours as TFC's, as a set, since the decision has no call order.
     */
    @Test
    void kaolinDecisionsMatchTheMask() {
        final DiscVeinConfig config = (DiscVeinConfig) configs().get("kaolin_disc");
        assertThat(config.density()).isEqualTo(1f);
        for (String[] row : rows("MASK")) {
            if (!row[2].equals("kaolin_disc")) {
                continue;
            }
            final long levelSeed = Long.parseLong(row[1]);
            final KaolinDisc disc = new KaolinDisc(levelSeed, config);
            for (int i = 3; i < row.length; i += 2) {
                final String[] c = row[i].split(",");
                final int cx = Integer.parseInt(c[0]);
                final int cz = Integer.parseInt(c[1]);
                final Set<List<Integer>> expected = new HashSet<>(maskPositions(levelSeed, cx, cz, config));
                final Set<List<Integer>> actual = new HashSet<>();
                final KaolinDisc.Chunk chunk = disc.chunk(cx, cz, VIEW);
                for (int x = cx << 4; x < (cx << 4) + 16; x++) {
                    for (int z = cz << 4; z < (cz << 4) + 16; z++) {
                        final int h = height(x, z);
                        for (int y = h - 12; y <= h + 12; y++) {
                            final KaolinDisc.Clay clay = chunk.decide(x, y, z, soil(x, y, z));
                            if (clay != null) {
                                actual.add(List.of(x, y, z, clay.ordinal()));
                            }
                        }
                    }
                }
                // Density 1 with the clamped factor: positions whose chance is below 1 roll, so the decision is a subset
                assertThat(expected).as("kaolin %s chunk %s", row[1], row[i]).containsAll(actual);
                assertThat(actual.size()).as("kaolin %s chunk %s", row[1], row[i]).isGreaterThanOrEqualTo(expected.size() / 2);
            }
        }
    }

    /**
     * The distribution statistic: the 100 chunks of the oracle's {@code STAT} through {@link OreVeins} (and {@link KaolinDisc}),
     * whose rolls are positional instead of TFC's sequential random. The shapes are identical, so the totals over the four
     * seeds must agree within 10% wherever the count is large enough for that to be meaningful.
     */
    @Test
    void distributionMatchesTfc() {
        final Map<String, VeinConfig> configs = configs();
        final Map<String, long[]> totals = new LinkedHashMap<>();
        for (String[] row : rows("STAT")) {
            final long[] t = totals.computeIfAbsent(row[2], k -> new long[2]);
            t[0] += Long.parseLong(row[3]);
            t[1] += statistic(Long.parseLong(row[1]), configs.get(row[2]), false);
        }
        for (String[] row : rows("STAT2")) {
            final long[] t = totals.computeIfAbsent(row[2] + " (two passes)", k -> new long[2]);
            t[0] += Long.parseLong(row[3]);
            t[1] += statistic(Long.parseLong(row[1]), configs.get(row[2]), true);
        }
        assertThat(totals).hasSize(19 + 11);
        int checked = 0;
        for (Map.Entry<String, long[]> e : totals.entrySet()) {
            final long tfc = e.getValue()[0];
            final long ours = e.getValue()[1];
            if (tfc == 0) {
                assertThat(ours).as(e.getKey()).isZero();
            } else if (tfc >= STAT_MIN) {
                assertThat((double) Math.abs(ours - tfc) / tfc).as("%s: ours %d, TFC %d", e.getKey(), ours, tfc)
                    .isLessThanOrEqualTo(TOLERANCE);
                checked++;
            } else {
                assertThat((double) Math.abs(ours - tfc)).as("%s: ours %d, TFC %d", e.getKey(), ours, tfc)
                    .isLessThanOrEqualTo(4 * Math.sqrt(tfc));
            }
        }
        assertThat(checked).as("types checked at 10%").isGreaterThanOrEqualTo(20);
    }

    /** The 100-chunk count of positions the decision writes, in the oracle's synthetic level. */
    static long statistic(long levelSeed, VeinConfig config, boolean montanePass) {
        long total = 0;
        if (config instanceof DiscVeinConfig d && d.kaolin()) {
            final KaolinDisc disc = new KaolinDisc(levelSeed, d);
            for (int cx = -5; cx < 5; cx++) {
                for (int cz = -5; cz < 5; cz++) {
                    final KaolinDisc.Chunk chunk = disc.chunk(cx, cz, VIEW);
                    for (int x = cx << 4; x < (cx << 4) + 16; x++) {
                        for (int z = cz << 4; z < (cz << 4) + 16; z++) {
                            final int h = height(x, z);
                            for (int y = h - 12; y <= h + 12; y++) {
                                total += chunk.decide(x, y, z, soil(x, y, z)) != null ? 1 : 0;
                            }
                        }
                    }
                }
            }
            return total;
        }
        final String rock = config.blocks().keySet().stream().sorted().findFirst().orElseThrow();
        final OreVeins veins = montanePass
            ? new OreVeins(levelSeed, List.of(config), Map.of("montane", List.of(config)), List.of(rock))
            : new OreVeins(levelSeed, List.of(config), List.of(rock));
        final Set<String> tags = montanePass ? Set.of("montane") : Set.of();
        final int lowY = config.minY() + (config.project() ? 70 : 0);
        final int highY = config.maxY() + (config.project() ? 78 : 0);
        for (int cx = -5; cx < 5; cx++) {
            for (int cz = -5; cz < 5; cz++) {
                final OreVeins.Chunk chunk = veins.chunk(cx, cz, tags, VIEW);
                if (chunk.isEmpty()) {
                    continue;
                }
                for (int x = cx << 4; x < (cx << 4) + 16; x++) {
                    for (int z = cz << 4; z < (cz << 4) + 16; z++) {
                        for (int y = lowY; y <= highY; y++) {
                            total += chunk.decide(x, y, z, 0) != null ? 1 : 0;
                        }
                    }
                }
            }
        }
        return total;
    }

    /** {@code VeinFeature.place} with every roll passing: the count and hash of the written positions, in TFC's call order. */
    static long[] mask(long levelSeed, int chunkX, int chunkZ, VeinConfig config) {
        final boolean kaolin = config instanceof DiscVeinConfig d && d.kaolin();
        long h = 0xcbf29ce484222325L;
        long count = 0;
        for (List<Integer> p : maskPositions(levelSeed, chunkX, chunkZ, config)) {
            h = OreVeinGoldenTest.mix(OreVeinGoldenTest.mix(OreVeinGoldenTest.mix(h, p.get(0)), p.get(1)), p.get(2));
            if (kaolin) {
                h = OreVeinGoldenTest.mix(h, p.get(3));
            }
            count++;
        }
        return new long[] {count, h};
    }

    /** The positions {@code VeinFeature.place} writes with every roll passing, in call order; for kaolin with the clay. */
    static List<List<Integer>> maskPositions(long levelSeed, int chunkX, int chunkZ, VeinConfig config) {
        final boolean kaolin = config instanceof DiscVeinConfig d && d.kaolin();
        final Set<List<Integer>> changed = new HashSet<>();
        final List<List<Integer>> out = new ArrayList<>();
        for (Vein v : Veins.nearbyVeins(levelSeed, chunkX, chunkZ, config)) {
            final int[] b = Veins.box(chunkX, chunkZ, v, config);
            final int[] offset = Veins.projectionOffset(v, config);
            for (int x = b[0]; x <= b[1]; x++) {
                for (int z = b[4]; z <= b[5]; z++) {
                    final int projectedY = config.project() ? height(offset[0] + x, offset[1] + z) : 0;
                    if (config.nearLava() && !Veins.isNearLava(x, z, VIEW::isLava)) {
                        continue;
                    }
                    for (int y = b[2]; y <= b[3]; y++) {
                        if (0f < Veins.chanceToGenerate(x - v.x(), y - v.y(), z - v.z(), v, config)) {
                            final int ay = y + projectedY;
                            final List<Integer> pos = List.of(x, ay, z);
                            if (changed.contains(pos)) {
                                continue; // ore or clay: neither rock nor soil
                            }
                            if (kaolin) {
                                final KaolinDisc.Clay clay = KaolinDisc.stateToGenerate(soil(x, ay, z), y - v.y());
                                if (clay == null) {
                                    continue;
                                }
                                changed.add(pos);
                                out.add(List.of(x, ay, z, clay.ordinal()));
                            } else {
                                changed.add(pos);
                                out.add(pos);
                            }
                        }
                    }
                }
            }
        }
        return out;
    }

    /** The oracle's text of a vein's shape. */
    static String describe(Vein v) {
        return switch (v) {
            case PipeVein p -> p.sign() + "/" + p.skewX() + "/" + p.skewZ() + "/" + p.skew() + "/" + p.slant();
            case DiscVein d -> describe(d.metaballs());
            case ClusterVein c -> OreVeinGoldenTest.describe(c.metaballs());
        };
    }

    /** Each {@code Metaballs2D.Ball} record's {@code toString} without letters, brackets and '='. */
    static String describe(Metaballs2D m) {
        final double[] x = field(m, "x");
        final double[] z = field(m, "z");
        final double[] w = field(m, "weight");
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < m.balls(); i++) {
            final String record = "Ball[x=" + x[i] + ", z=" + z[i] + ", weight=" + w[i] + "]";
            sb.append(record.replaceAll("[A-Za-z\\[\\]=]", "").replace(", ", "/")).append(';');
        }
        return sb.toString();
    }

    private static double[] field(Metaballs2D m, String name) {
        try {
            final Field f = Metaballs2D.class.getDeclaredField(name);
            f.setAccessible(true);
            return (double[]) f.get(m);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
