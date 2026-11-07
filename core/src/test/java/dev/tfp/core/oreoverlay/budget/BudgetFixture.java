/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.oreoverlay.budget;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.tfp.core.oreoverlay.ArrayOreSnapshot;
import dev.tfp.core.oreoverlay.OreSnapshot;
import dev.tfp.core.oreoverlay.census.ExposureRule;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The PAR-REL-12 overlay budget fixture: the per-chunk numbers of the S1 census (PAR-WORLD-17,
 * {@code spikes/ore-overlay/out/ore-census-tfc-<seed>-paper.json}, seeds 1-3, 32 × 32 chunks each) in a compact text
 * resource, and the deterministic chunk snapshots built from them.
 *
 * <p>Per chunk the census kept the ore block count and the measured surface and cave faces; the S1 "combined estimate"
 * adds the cave exposure of a world with TFC's caves ({@code blocks × cave_faces_per_block} from the vanilla proxy, since
 * our world has no tunnels yet, PAR-WORLD-06). {@link #snapshot} turns one chunk into an {@link OreSnapshot} that holds
 * exactly that many ore blocks and exactly that many exposed faces (and outcrop faces), so the runtime's
 * {@code ExposedFaces.compute} recounts the census through the real table build.
 *
 * <p>The resource is generated: {@code main} rewrites it from the spike reports, and {@code OreOverlayBudgetTest} checks
 * that it still equals that export. Never edit it by hand.
 */
final class BudgetFixture {
    static final String RESOURCE = "s1-census-tfc.txt";
    static final Path SPIKE_OUT = Path.of("spikes", "ore-overlay", "out");
    static final long[] SEEDS = {1, 2, 3};

    /** Snapshot geometry: a world slice of 128 blocks, the ore slab from y −60 in x, z 1..14 (196 ores per layer). */
    static final int MIN_Y = -64;
    static final int MAX_Y = 64;
    static final int SLAB_Y = -60;
    static final int SLAB_MIN = 1;
    static final int SLAB_MAX = 14;
    static final int PER_LAYER = (SLAB_MAX - SLAB_MIN + 1) * (SLAB_MAX - SLAB_MIN + 1);
    /** Heightmap of a cave column: every open cell of the slice lies more than 8 blocks below it. */
    static final int CAVE_SURFACE = 60;
    /** Heightmap of an outcrop column: every open cell of the slice counts as a surface outcrop. */
    static final int OUTCROP_SURFACE = MIN_Y;

    /** One chunk of the census. */
    record Chunk(int blocks, int facesSurface, int facesCave) {
        /** The S1 combined estimate (CensusReport#estimatedFaces): surface + max(cave, round(blocks × cpb)). */
        int estimate(double caveFacesPerBlock) {
            return facesSurface + Math.max(facesCave, (int) Math.round(blocks * caveFacesPerBlock));
        }
    }

    /** p50, p95 and max as published in the census report. */
    record Published(int p50, int p95, int max) {
    }

    /** One seed's census area. */
    record Area(long seed, int originX, int originZ, int size, double caveFacesPerBlock, Published estimatePerChunk,
            Published estimateGated, Published measuredGated, List<Chunk> chunks) {
        Chunk chunk(int dx, int dz) {
            return chunks.get(dz * size + dx);
        }
    }

    private BudgetFixture() {
    }

    // ------------------------------------------------------------------------------------------------ resource

    /** Reads the fixture resource. */
    static List<Area> load() {
        try (InputStream in = BudgetFixture.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("missing test resource " + RESOURCE);
            }
            return parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    static List<Area> parse(String text) {
        List<Area> out = new ArrayList<>();
        String[] lines = text.split("\n");
        int i = 0;
        while (i < lines.length) {
            String line = lines[i++].strip();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            String[] h = line.split(" ");
            // area <seed> <originX> <originZ> <size> <cpb> est_chunk <3> est_gated <3> measured_gated <3>
            if (!h[0].equals("area") || h.length != 18) {
                throw new IllegalArgumentException("bad area line: " + line);
            }
            int size = Integer.parseInt(h[4]);
            List<Chunk> chunks = new ArrayList<>(size * size);
            for (int row = 0; row < size; row++) {
                String[] cells = lines[i++].strip().split(" ");
                if (cells.length != size) {
                    throw new IllegalArgumentException("row " + row + " of seed " + h[1] + " has " + cells.length + " chunks");
                }
                for (String c : cells) {
                    String[] v = c.split(":");
                    chunks.add(new Chunk(Integer.parseInt(v[0]), Integer.parseInt(v[1]), Integer.parseInt(v[2])));
                }
            }
            out.add(new Area(Long.parseLong(h[1]), Integer.parseInt(h[2]), Integer.parseInt(h[3]), size,
                    Double.parseDouble(h[5]), published(h, 7), published(h, 11), published(h, 15), List.copyOf(chunks)));
        }
        return out;
    }

    private static Published published(String[] h, int at) {
        return new Published(Integer.parseInt(h[at]), Integer.parseInt(h[at + 1]), Integer.parseInt(h[at + 2]));
    }

    /** The resource text exported from the spike reports under {@code spikeOut}. */
    static String export(Path spikeOut) throws IOException {
        StringBuilder b = new StringBuilder();
        b.append("# PAR-REL-12 ore overlay budget fixture. Generated by BudgetFixture.main from\n")
                .append("# spikes/ore-overlay/out/ore-census-tfc-<seed>-paper.json (PAR-WORLD-17 S1 census). Do not edit.\n")
                .append("# area <seed> <origin chunk x> <origin chunk z> <size> <cave faces per block>\n")
                .append("#   est_chunk <p50 p95 max> est_gated <p50 p95 max> measured_gated <p50 p95 max>\n")
                .append("# then <size> rows of <size> chunks, each <ore blocks>:<surface faces>:<cave faces>\n");
        // The cave faces per block the S1 runs used: run-all.sh passes the mean of the three vanilla proxy reports,
        // rounded to 5 decimals (the TFC reports print it rounded to 4).
        double sum = 0;
        for (long seed : SEEDS) {
            Path vanilla = spikeOut.resolve("ore-census-vanilla-" + seed + "-paper.json");
            sum += JsonParser.parseString(Files.readString(vanilla, StandardCharsets.UTF_8)).getAsJsonObject()
                    .getAsJsonObject("census").getAsJsonObject("fractions").get("cave_faces_per_block").getAsDouble();
        }
        double cpb = Math.round(sum / SEEDS.length * 1e5) / 1e5;
        for (long seed : SEEDS) {
            Path file = spikeOut.resolve("ore-census-tfc-" + seed + "-paper.json");
            JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonObject census = root.getAsJsonObject("census");
            JsonObject estimate = census.getAsJsonObject("estimate");
            if (Math.abs(estimate.get("cave_faces_per_block").getAsDouble() - cpb) > 5e-5) {
                throw new IllegalStateException(file + " used another cave faces per block than " + cpb);
            }
            int size = census.get("size").getAsInt();
            JsonArray origin = census.getAsJsonArray("origin_chunk");
            b.append("area ").append(seed).append(' ').append(origin.get(0).getAsInt()).append(' ')
                    .append(origin.get(1).getAsInt()).append(' ').append(size).append(' ')
                    .append(String.format(Locale.ROOT, "%s", cpb))
                    .append(" est_chunk ").append(triple(estimate.getAsJsonObject("per_chunk")))
                    .append(" est_gated ").append(triple(estimate.getAsJsonObject("radius_faces").getAsJsonObject("gated_4")))
                    .append(" measured_gated ")
                    .append(triple(census.getAsJsonObject("radius_faces").getAsJsonObject("gated_4"))).append('\n');
            JsonObject raw = census.getAsJsonObject("per_chunk_raw");
            JsonArray blocks = raw.getAsJsonArray("blocks");
            JsonArray surface = raw.getAsJsonArray("faces_surface");
            JsonArray cave = raw.getAsJsonArray("faces_cave");
            for (int row = 0; row < size; row++) {
                for (int dx = 0; dx < size; dx++) {
                    int k = row * size + dx;
                    if (dx > 0) {
                        b.append(' ');
                    }
                    b.append(blocks.get(k).getAsInt()).append(':').append(surface.get(k).getAsInt()).append(':')
                            .append(cave.get(k).getAsInt());
                }
                b.append('\n');
            }
        }
        return b.toString();
    }

    private static String triple(JsonObject d) {
        return d.get("p50").getAsInt() + " " + d.get("p95").getAsInt() + " " + d.get("max").getAsInt();
    }

    /**
     * Rewrites the resource: {@code BudgetFixture <repo root>} (run from the core test classpath; the output goes to
     * {@code core/src/test/resources/dev/tfp/core/oreoverlay/budget/}).
     */
    public static void main(String[] args) throws IOException {
        Path root = Path.of(args.length > 0 ? args[0] : ".");
        Path out = root.resolve("core/src/test/resources/dev/tfp/core/oreoverlay/budget").resolve(RESOURCE);
        Files.createDirectories(out.getParent());
        Files.writeString(out, export(root.resolve(SPIKE_OUT)), StandardCharsets.UTF_8);
    }

    // ------------------------------------------------------------------------------------------------ snapshots

    /**
     * A chunk at {@code (chunkX, chunkZ)} with {@code blocks} ore blocks (kind 0) in a slab and exactly
     * {@code surfaceFaces + caveFaces} exposed faces, {@code surfaceFaces} of them surface outcrops. Every opened cell
     * touches exactly one ore (cells under the bottom layer, above the top layer, and in the rock ring around the slab),
     * so each opened cell is one face. The neighbours outside the chunk are left unknown (hidden).
     */
    static OreSnapshot snapshot(int chunkX, int chunkZ, int blocks, int surfaceFaces, int caveFaces) {
        int layers = (blocks + PER_LAYER - 1) / PER_LAYER;
        if (SLAB_Y + layers + 1 >= MAX_Y) {
            throw new IllegalArgumentException(blocks + " ores do not fit the slice");
        }
        int bx = chunkX << 4;
        int bz = chunkZ << 4;
        ArrayOreSnapshot.Builder b = ArrayOreSnapshot.builder(chunkX, chunkZ, MIN_Y, MAX_Y, ExposureRule.SOLID);
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                b.surface(bx | x, bz | z, CAVE_SURFACE);
            }
        }
        boolean[] ore = new boolean[16 * 16 * (MAX_Y - MIN_Y)];
        for (int i = 0; i < blocks; i++) {
            int[] p = orePos(i);
            b.set(bx | p[0], p[1], bz | p[2], 0);
            ore[idx(p[0], p[1], p[2])] = true;
        }
        int topLayer = layers - 1;

        // Ring cells by column; the columns used for outcrops take no cave face.
        List<int[]> ring = new ArrayList<>();
        for (int i = 0; i < blocks; i++) {
            int[] p = orePos(i);
            if (p[0] == SLAB_MIN) {
                ring.add(new int[] {0, p[1], p[2]});
            }
            if (p[0] == SLAB_MAX) {
                ring.add(new int[] {15, p[1], p[2]});
            }
            if (p[2] == SLAB_MIN) {
                ring.add(new int[] {p[0], p[1], 0});
            }
            if (p[2] == SLAB_MAX) {
                ring.add(new int[] {p[0], p[1], 15});
            }
        }
        ring.sort((a, c) -> a[0] != c[0] ? Integer.compare(a[0], c[0])
                : a[2] != c[2] ? Integer.compare(a[2], c[2]) : Integer.compare(a[1], c[1]));
        boolean[] outcropColumn = new boolean[256];
        int opened = 0;
        for (int[] c : ring) {
            if (opened == surfaceFaces) {
                break;
            }
            b.set(bx | c[0], c[1], bz | c[2], ExposureRule.OPEN);
            outcropColumn[c[0] * 16 + c[2]] = true;
            b.surface(bx | c[0], bz | c[2], OUTCROP_SURFACE);
            opened++;
        }
        if (opened < surfaceFaces) {
            throw new IllegalArgumentException(blocks + " ores cannot carry " + surfaceFaces + " outcrop faces");
        }

        List<int[]> cave = new ArrayList<>();
        for (int i = 0; i < Math.min(blocks, PER_LAYER); i++) {
            int[] p = orePos(i);
            cave.add(new int[] {p[0], SLAB_Y - 1, p[2]});
        }
        for (int i = topLayer * PER_LAYER; i < blocks; i++) {
            int[] p = orePos(i);
            cave.add(new int[] {p[0], p[1] + 1, p[2]});
        }
        for (int[] c : ring) {
            if (!outcropColumn[c[0] * 16 + c[2]]) {
                cave.add(c);
            }
        }
        int caveOpened = 0;
        boolean[] open = new boolean[ore.length];
        for (int[] c : cave) {
            if (caveOpened == caveFaces) {
                break;
            }
            int k = idx(c[0], c[1], c[2]);
            if (open[k] || ore[k]) {
                continue;
            }
            open[k] = true;
            b.set(bx | c[0], c[1], bz | c[2], ExposureRule.OPEN);
            caveOpened++;
        }
        if (caveOpened < caveFaces) {
            throw new IllegalArgumentException(blocks + " ores cannot carry " + caveFaces + " cave faces");
        }
        return b.build();
    }

    private static int[] orePos(int i) {
        int layer = i / PER_LAYER;
        int k = i % PER_LAYER;
        int w = SLAB_MAX - SLAB_MIN + 1;
        return new int[] {SLAB_MIN + k % w, SLAB_Y + layer, SLAB_MIN + k / w};
    }

    private static int idx(int x, int y, int z) {
        return ((y - MIN_Y) * 16 + z) * 16 + x;
    }
}
