/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.oreoverlay.census;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.function.Function;
import java.util.function.ToIntFunction;

/**
 * The census of a square of chunks (PAR-WORLD-17): per-chunk distributions (p50/p95/max), totals, exposure fractions, the
 * overlay count a player sees within the gated radius and at view distances 6/8/10, per-target totals and an exposed example.
 * Pure; the platform side builds it from {@link ChunkCensus} values and writes {@link #toJson} to {@code ore-census.json}.
 *
 * <p>Radius totals: {@value #SAMPLES} player positions, chunk centres drawn with a fixed seed among the chunks whose whole
 * square of radius r lies inside the area; the total is the sum over the (2r+1)² chunks. A radius that does not fit the area
 * is reported as {@code null}.
 */
public final class CensusReport {
    /** Number of sample player positions per radius. */
    public static final int SAMPLES = 20;
    /** The gated overlay radius in chunks (48 m cutoff: ceil(48 / 16) + 1) and the view distances to compare with. */
    public static final Map<String, Integer> RADII = radii();

    private final String kind;
    private final List<String> targets;
    private final boolean[] counted;
    private final int originX;
    private final int originZ;
    private final int size;
    private final ChunkCensus[] grid;

    /**
     * @param grid the chunk censuses, row-major: index {@code dz * size + dx} is chunk {@code (originX + dx, originZ + dz)}
     */
    public CensusReport(String kind, List<String> targets, boolean[] counted, int originX, int originZ, int size,
            List<ChunkCensus> grid) {
        if (grid.size() != size * size) {
            throw new IllegalArgumentException("grid holds " + grid.size() + " chunks, expected " + size * size);
        }
        if (targets.size() != counted.length) {
            throw new IllegalArgumentException(targets.size() + " targets but " + counted.length + " counted flags");
        }
        this.kind = kind;
        this.targets = List.copyOf(targets);
        this.counted = counted.clone();
        this.originX = originX;
        this.originZ = originZ;
        this.size = size;
        this.grid = grid.toArray(ChunkCensus[]::new);
    }

    public int size() {
        return size;
    }

    public ChunkCensus chunk(int dx, int dz) {
        return grid[dz * size + dx];
    }

    /** The per-chunk values of one measure, row-major. */
    public int[] perChunk(ToIntFunction<ChunkCensus> measure) {
        final int[] out = new int[grid.length];
        for (int i = 0; i < grid.length; i++) {
            out[i] = measure.applyAsInt(grid[i]);
        }
        return out;
    }

    /** Sum of one measure over the area. */
    public long total(ToIntFunction<ChunkCensus> measure) {
        long sum = 0;
        for (ChunkCensus c : grid) {
            sum += measure.applyAsInt(c);
        }
        return sum;
    }

    /**
     * Cave faces per counted block over the area: on a vanilla world the exposure proxy for our world's ores until caves are
     * ported (PAR-WORLD-06).
     */
    public double caveFacesPerBlock() {
        final long blocks = total(ChunkCensus::blocks);
        return blocks == 0 ? 0 : (double) total(ChunkCensus::facesCave) / blocks;
    }

    /**
     * The combined estimate per chunk: measured surface faces plus the larger of the measured cave faces and
     * {@code blocks × caveFacesPerBlock} (the cave exposure of a world that has TFC's caves, from the proxy).
     */
    public int[] estimatedFaces(double caveFacesPerBlock) {
        final int[] out = new int[grid.length];
        for (int i = 0; i < grid.length; i++) {
            final ChunkCensus c = grid[i];
            out[i] = c.facesSurface() + Math.max(c.facesCave(), (int) Math.round(c.blocks() * caveFacesPerBlock));
        }
        return out;
    }

    /** Nearest-rank percentile {@code q} in (0, 1] of {@code values}; 0 for none. */
    public static int percentile(int[] values, double q) {
        if (values.length == 0) {
            return 0;
        }
        final int[] sorted = values.clone();
        Arrays.sort(sorted);
        final int rank = (int) Math.ceil(q * sorted.length);
        return sorted[Math.max(0, Math.min(sorted.length - 1, rank - 1))];
    }

    /** p50, p95, max and mean of {@code values}. */
    public static JsonObject distribution(int[] values) {
        final JsonObject o = new JsonObject();
        o.addProperty("p50", percentile(values, 0.5));
        o.addProperty("p95", percentile(values, 0.95));
        o.addProperty("max", Arrays.stream(values).max().orElse(0));
        o.addProperty("mean", round(Arrays.stream(values).average().orElse(0)));
        return o;
    }

    /**
     * The totals of {@code perChunk} over the (2r+1)² chunks around {@value #SAMPLES} sample centres, or null if the square
     * does not fit the area.
     */
    public int[] radiusTotals(int[] perChunk, int radius, long seed) {
        final int width = 2 * radius + 1;
        if (width > size) {
            return null;
        }
        final long[][] prefix = new long[size + 1][size + 1];
        for (int dz = 0; dz < size; dz++) {
            for (int dx = 0; dx < size; dx++) {
                prefix[dz + 1][dx + 1] = perChunk[dz * size + dx] + prefix[dz][dx + 1] + prefix[dz + 1][dx] - prefix[dz][dx];
            }
        }
        final SplittableRandom random = new SplittableRandom(seed ^ radius * 0x9E3779B97F4A7C15L);
        final int[] out = new int[SAMPLES];
        final int span = size - width + 1;
        for (int i = 0; i < SAMPLES; i++) {
            final int x0 = random.nextInt(span);
            final int z0 = random.nextInt(span);
            out[i] = (int) (prefix[z0 + width][x0 + width] - prefix[z0][x0 + width] - prefix[z0 + width][x0] + prefix[z0][x0]);
        }
        return out;
    }

    /** Distributions of the radius totals of {@code perChunk} for every {@link #RADII} entry (null where it does not fit). */
    public JsonObject radiusJson(int[] perChunk, long seed) {
        final JsonObject o = new JsonObject();
        for (Map.Entry<String, Integer> e : RADII.entrySet()) {
            final int[] totals = radiusTotals(perChunk, e.getValue(), seed);
            if (totals == null) {
                o.add(e.getKey(), null);
                continue;
            }
            final JsonObject d = distribution(totals);
            final int width = 2 * e.getValue() + 1;
            d.addProperty("chunks", width * width);
            o.add(e.getKey(), d);
        }
        return o;
    }

    /**
     * The report as JSON.
     *
     * @param group      maps a target id to the row it is summed into in {@code per_target} (e.g. the ore without its rock)
     * @param sampleSeed seed of the sample player positions
     * @param caveFacesPerBlock the proxy's {@link #caveFacesPerBlock()} for the combined estimate, or NaN for none
     */
    public JsonObject toJson(Function<String, String> group, long sampleSeed, double caveFacesPerBlock) {
        final JsonObject o = new JsonObject();
        o.addProperty("kind", kind);
        o.addProperty("size", size);
        final JsonArray origin = new JsonArray();
        origin.add(originX);
        origin.add(originZ);
        o.add("origin_chunk", origin);
        o.addProperty("chunks", grid.length);
        o.addProperty("surface_depth", ExposureCensus.SURFACE_DEPTH);

        final JsonObject perChunk = new JsonObject();
        perChunk.add("blocks", distribution(perChunk(ChunkCensus::blocks)));
        perChunk.add("exposed", distribution(perChunk(ChunkCensus::exposed)));
        perChunk.add("faces", distribution(perChunk(ChunkCensus::faces)));
        perChunk.add("faces_surface", distribution(perChunk(ChunkCensus::facesSurface)));
        perChunk.add("faces_cave", distribution(perChunk(ChunkCensus::facesCave)));
        o.add("per_chunk", perChunk);

        final long blocks = total(ChunkCensus::blocks);
        final long exposed = total(ChunkCensus::exposed);
        final long faces = total(ChunkCensus::faces);
        final JsonObject totals = new JsonObject();
        totals.addProperty("blocks", blocks);
        totals.addProperty("exposed", exposed);
        totals.addProperty("exposed_surface", total(ChunkCensus::exposedSurface));
        totals.addProperty("exposed_cave", total(ChunkCensus::exposedCave));
        totals.addProperty("faces", faces);
        totals.addProperty("faces_surface", total(ChunkCensus::facesSurface));
        totals.addProperty("faces_cave", total(ChunkCensus::facesCave));
        final JsonObject byDirection = new JsonObject();
        for (int d = 0; d < ChunkCensus.DIRECTIONS.length; d++) {
            final int dir = d;
            byDirection.addProperty(ChunkCensus.DIRECTIONS[d], total(c -> c.facesByDirection(dir)));
        }
        totals.add("faces_by_direction", byDirection);
        o.add("totals", totals);

        final JsonObject fractions = new JsonObject();
        fractions.addProperty("exposed_per_block", ratio(exposed, blocks));
        fractions.addProperty("exposed_surface_per_block", ratio(total(ChunkCensus::exposedSurface), blocks));
        fractions.addProperty("exposed_cave_per_block", ratio(total(ChunkCensus::exposedCave), blocks));
        fractions.addProperty("faces_per_exposed", ratio(faces, exposed));
        fractions.addProperty("faces_per_block", ratio(faces, blocks));
        fractions.addProperty("cave_faces_per_block", round(caveFacesPerBlock()));
        o.add("fractions", fractions);

        o.add("radius_faces", radiusJson(perChunk(ChunkCensus::faces), sampleSeed));
        if (!Double.isNaN(caveFacesPerBlock)) {
            final JsonObject estimate = new JsonObject();
            estimate.addProperty("cave_faces_per_block", round(caveFacesPerBlock));
            final int[] est = estimatedFaces(caveFacesPerBlock);
            estimate.add("per_chunk", distribution(est));
            estimate.add("radius_faces", radiusJson(est, sampleSeed));
            o.add("estimate", estimate);
        }

        o.add("per_target", perTarget(group));
        o.add("exposed_example", example());
        final JsonObject raw = new JsonObject();
        raw.add("blocks", array(perChunk(ChunkCensus::blocks)));
        raw.add("faces_surface", array(perChunk(ChunkCensus::facesSurface)));
        raw.add("faces_cave", array(perChunk(ChunkCensus::facesCave)));
        o.add("per_chunk_raw", raw);
        return o;
    }

    private JsonArray perTarget(Function<String, String> group) {
        final Map<String, long[]> rows = new LinkedHashMap<>();
        final Map<String, Boolean> countedRows = new LinkedHashMap<>();
        for (int t = 0; t < targets.size(); t++) {
            final int target = t;
            final long b = total(c -> c.targetBlocks(target));
            if (b == 0) {
                continue;
            }
            final String key = group.apply(targets.get(t));
            final long[] row = rows.computeIfAbsent(key, k -> new long[4]);
            row[0] += b;
            row[1] += total(c -> c.targetExposed(target));
            row[2] += total(c -> c.targetFaces(target));
            row[3] += total(c -> c.targetFacesSurface(target));
            countedRows.merge(key, counted[t], Boolean::logicalOr);
        }
        final List<Map.Entry<String, long[]>> sorted = new ArrayList<>(rows.entrySet());
        sorted.sort((a, b) -> Long.compare(b.getValue()[2], a.getValue()[2]) != 0
                ? Long.compare(b.getValue()[2], a.getValue()[2]) : a.getKey().compareTo(b.getKey()));
        final JsonArray out = new JsonArray();
        for (Map.Entry<String, long[]> e : sorted) {
            final JsonObject row = new JsonObject();
            row.addProperty("id", e.getKey());
            row.addProperty("counted", countedRows.get(e.getKey()));
            row.addProperty("blocks", e.getValue()[0]);
            row.addProperty("exposed", e.getValue()[1]);
            row.addProperty("faces", e.getValue()[2]);
            row.addProperty("faces_surface", e.getValue()[3]);
            row.addProperty("blocks_per_chunk", round((double) e.getValue()[0] / grid.length));
            row.addProperty("exposed_per_block", ratio(e.getValue()[1], e.getValue()[0]));
            out.add(row);
        }
        return out;
    }

    /** The exposed surface block of the chunk with the most surface faces, in world coordinates; null if none. */
    private JsonObject example() {
        int best = -1;
        for (int i = 0; i < grid.length; i++) {
            if (grid[i].surfaceExample() >= 0 && (best < 0 || grid[i].facesSurface() > grid[best].facesSurface())) {
                best = i;
            }
        }
        if (best < 0) {
            return null;
        }
        final int cx = originX + best % size;
        final int cz = originZ + best / size;
        final int packed = grid[best].surfaceExample();
        final JsonObject o = new JsonObject();
        o.addProperty("x", (cx << 4) + ChunkCensus.unpackX(packed));
        o.addProperty("y", ChunkCensus.unpackY(packed));
        o.addProperty("z", (cz << 4) + ChunkCensus.unpackZ(packed));
        o.addProperty("chunk_faces_surface", grid[best].facesSurface());
        return o;
    }

    private static JsonArray array(int[] values) {
        final JsonArray a = new JsonArray();
        for (int v : values) {
            a.add(v);
        }
        return a;
    }

    private static double ratio(long a, long b) {
        return b == 0 ? 0 : round((double) a / b);
    }

    private static double round(double v) {
        return Math.round(v * 10000.0) / 10000.0;
    }

    private static Map<String, Integer> radii() {
        final Map<String, Integer> m = new LinkedHashMap<>();
        m.put("gated_4", 4);
        m.put("view_distance_6", 6);
        m.put("view_distance_8", 8);
        m.put("view_distance_10", 10);
        return java.util.Collections.unmodifiableMap(m);
    }
}
