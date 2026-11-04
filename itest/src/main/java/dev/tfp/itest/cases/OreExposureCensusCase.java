/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.itest.cases;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.tfp.core.oreoverlay.census.ArrayChunkView;
import dev.tfp.core.oreoverlay.census.CensusReport;
import dev.tfp.core.oreoverlay.census.ChunkCensus;
import dev.tfp.core.oreoverlay.census.ExposureCensus;
import dev.tfp.core.oreoverlay.census.ExposureRule;
import dev.tfp.core.worldgen.chunk.PlaceholderRocks;
import dev.tfp.core.worldgen.ore.ClusterVeinConfig;
import dev.tfp.core.worldgen.ore.OreVeins;
import dev.tfp.core.worldgen.ore.VeinData;
import dev.tfp.itest.SelfTestCase;
import dev.tfp.itest.SelfTestContext;
import dev.tfp.paper.TfpPlugin;
import dev.tfp.paper.data.RegistryService;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.BlockVisuals;
import dev.tfp.paper.scheduler.Scheduler;
import dev.tfp.paper.worldgen.TfcChunkGenerator;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import org.bukkit.Bukkit;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Waterlogged;

/**
 * {@code ore.census} (PAR-WORLD-17, spike S1): counts the ore faces an exposed-face overlay would draw around spawn.
 *
 * <p>A square of {@code tfp.oreCensus.chunks} × {@code tfp.oreCensus.chunks} chunks (default {@value #DEFAULT_SIZE}) centred
 * on the spawn chunk, plus a one-chunk ring for the border cells, is generated row by row: each row is generated
 * asynchronously, every chunk is snapshotted on its own region thread and classified into cell codes on the CPU pool;
 * once a row's neighbours are classified the row's {@link ExposureCensus} runs on the CPU pool. No world write.
 *
 * <ul>
 *   <li>TFC world (our generator): the targets are the ore blocks the veins can place (counted) and the raw rocks (counted
 *       only per target, for the rock exposure).</li>
 *   <li>Any other world: the exposure proxy (TFC's caves are not ported yet, PAR-WORLD-06): stone-family blocks and vanilla
 *       ores, all counted.</li>
 * </ul>
 * The report ({@link CensusReport#toJson}) goes to {@code ore-census.json} in the server folder and to
 * {@code ore-census-<kind>-<seed>.json}. {@code tfp.oreCensus.caveFacesPerBlock} (a vanilla run's
 * {@code fractions.cave_faces_per_block}) adds the combined estimate to a TFC run.
 */
public final class OreExposureCensusCase implements SelfTestCase {
    static final int DEFAULT_SIZE = 4;
    static final List<Material> PROXY_ROCKS = List.of(Material.STONE, Material.DEEPSLATE, Material.GRANITE, Material.DIORITE,
            Material.ANDESITE, Material.TUFF);
    private static final Duration STEP = Duration.ofSeconds(60);

    private final int size = Math.max(1, Integer.getInteger("tfp.oreCensus.chunks", DEFAULT_SIZE));
    private final double caveFacesPerBlock = parse(System.getProperty("tfp.oreCensus.caveFacesPerBlock"));
    /** Chunks (ring included) that came back empty: refused by the generator. Their cells never expose a neighbour. */
    private final List<String> emptyChunks = new ArrayList<>();

    @Override
    public String name() {
        return "ore.census: exposed ore faces per chunk around spawn (PAR-WORLD-17)";
    }

    @Override
    public Duration timeout() {
        final int ring = size + 2;
        return Duration.ofSeconds(CraftEngineBlockRoundTripCase.REBUILD.toSeconds() + 120 + 2L * ring * ring);
    }

    @Override
    public CompletableFuture<String> run(SelfTestContext ctx) {
        final TfpPlugin tfp = ctx.tfp();
        final Scheduler s = tfp.scheduler();
        return OnThread.script("ore-census", () -> {
            OnThread.awaitPackRebuild(tfp, CraftEngineBlockRoundTripCase.REBUILD);
            final World world = Bukkit.getWorlds().getFirst();
            final Targets targets = world.getGenerator() instanceof TfcChunkGenerator
                    ? tfcTargets(world.getSeed(), tfp.integrations().blockVisuals())
                    : proxyTargets();
            final Location spawn = world.getSpawnLocation();
            final int originX = (spawn.getBlockX() >> 4) - size / 2;
            final int originZ = (spawn.getBlockZ() >> 4) - size / 2;
            emptyChunks.clear();
            final long started = System.nanoTime();
            final CensusReport report = census(s, world, targets, originX, originZ);
            final double seconds = (System.nanoTime() - started) / 1e9;

            final JsonObject root = new JsonObject();
            root.addProperty("server", Bukkit.getName() + " " + Bukkit.getVersion());
            root.addProperty("minecraft", Bukkit.getMinecraftVersion());
            root.addProperty("world_kind", targets.kind());
            root.addProperty("seed", world.getSeed());
            root.addProperty("seconds", Math.round(seconds * 10) / 10.0);
            final JsonArray empty = new JsonArray();
            emptyChunks.forEach(empty::add);
            root.add("empty_chunks", empty);
            root.add("census", report.toJson(OreExposureCensusCase::group, world.getSeed(), caveFacesPerBlock));
            final String text = new GsonBuilder().setPrettyPrinting().serializeNulls().create().toJson(root);
            final Path out = Path.of(System.getProperty("tfp.oreCensus.output", "ore-census.json"));
            Files.writeString(out, text, StandardCharsets.UTF_8);
            Files.writeString(out.resolveSibling("ore-census-" + targets.kind() + "-" + world.getSeed() + ".json"), text,
                    StandardCharsets.UTF_8);

            final long blocks = report.total(ChunkCensus::blocks);
            if (blocks == 0) {
                throw new AssertionError("no " + (targets.kind().equals("tfc") ? "ore" : "stone or ore") + " block in the "
                        + size + "x" + size + " chunks around spawn: the census saw nothing (" + targets.size() + " targets)");
            }
            final int[] faces = report.perChunk(ChunkCensus::faces);
            return targets.kind() + " world, " + size + "x" + size + " chunks at " + originX + "," + originZ + ": "
                    + blocks + " counted blocks, faces per chunk p50 " + CensusReport.percentile(faces, 0.5) + " p95 "
                    + CensusReport.percentile(faces, 0.95) + " max " + CensusReport.percentile(faces, 1.0) + "; "
                    + emptyChunks.size() + " empty (refused) chunks; " + String.format(Locale.ROOT, "%.1f", seconds) + " s; " + out;
        });
    }

    /** The census of the square; blocks the script thread (never a tick thread). */
    private CensusReport census(Scheduler s, World world, Targets targets, int originX, int originZ) throws Exception {
        final int minY = world.getMinHeight();
        final int height = world.getMaxHeight() - minY;
        final ConcurrentHashMap<BlockData, Integer> codes = new ConcurrentHashMap<>();
        final Function<BlockData, Integer> classify = data -> codes.computeIfAbsent(data, targets::code);
        final Map<Integer, Classified[]> rows = new HashMap<>();
        final ChunkCensus[] grid = new ChunkCensus[size * size];
        for (int dz = -1; dz <= size; dz++) {
            final Classified[] classified = classifyRow(s, world, originX - 1, originZ + dz, size + 2, minY, height, classify);
            for (int i = 0; i < classified.length; i++) {
                if (classified[i].empty) {
                    emptyChunks.add((originX - 1 + i) + "," + (originZ + dz));
                }
            }
            rows.put(dz, classified);
            final int row = dz - 1;
            if (row >= 0) {
                final List<CompletableFuture<Void>> work = new ArrayList<>();
                for (int dx = 0; dx < size; dx++) {
                    final int x = dx;
                    work.add(CompletableFuture.runAsync(() -> grid[row * size + x] = ExposureCensus.count(
                            view(rows, row, x, minY, height), targets.counted()), s.cpuExecutor()));
                }
                OnThread.await(CompletableFuture.allOf(work.toArray(CompletableFuture[]::new)), STEP, "census of row " + row);
                rows.remove(row - 1);
            }
        }
        return new CensusReport(targets.kind(), targets.ids(), targets.counted(), originX, originZ, size, Arrays.asList(grid));
    }

    /**
     * Generates (asynchronously, never on a tick thread), snapshots (each on its region thread) and classifies one row of
     * {@code count} chunks from {@code x0}. No force-loading: {@code setChunkForceLoaded} would generate synchronously on the
     * tick thread.
     */
    private static Classified[] classifyRow(Scheduler s, World world, int x0, int cz, int count, int minY, int height,
            Function<BlockData, Integer> classify) throws Exception {
        final List<CompletableFuture<Classified>> chunks = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            final CompletableFuture<Classified> f = new CompletableFuture<>();
            request(s, world, x0 + i, cz, minY, height, classify, f);
            chunks.add(f);
        }
        final Classified[] out = new Classified[count];
        for (int i = 0; i < count; i++) {
            out[i] = OnThread.await(chunks.get(i), STEP, "generating and snapshotting chunk " + (x0 + i) + "," + cz);
        }
        return out;
    }

    private static void request(Scheduler s, World world, int cx, int cz, int minY, int height,
            Function<BlockData, Integer> classify, CompletableFuture<Classified> f) {
        world.getChunkAtAsync(cx, cz).whenComplete((loaded, error) -> {
            if (error != null) {
                f.completeExceptionally(error);
                return;
            }
            s.region(world, cx, cz, () -> {
                try {
                    // Folia drops the async load's ticket at once: a chunk unloaded again by now is generated and saved,
                    // so loading it back here is a cheap read on its own region thread, never a generation.
                    final ChunkSnapshot snapshot = world.getChunkAt(cx, cz).getChunkSnapshot(true, false, false);
                    CompletableFuture.supplyAsync(() -> Classified.of(snapshot, minY, height, classify), s.cpuExecutor())
                            .whenComplete((c, e) -> {
                                if (e != null) {
                                    f.completeExceptionally(e);
                                } else {
                                    f.complete(c);
                                }
                            });
                } catch (Throwable t) {
                    f.completeExceptionally(t);
                }
            });
        });
    }

    /** The view of chunk {@code (dx, row)} of the square with its border from the eight neighbours. */
    private static ArrayChunkView view(Map<Integer, Classified[]> rows, int row, int dx, int minY, int height) {
        final ArrayChunkView v = new ArrayChunkView(minY, height);
        for (int x = -1; x <= 16; x++) {
            for (int z = -1; z <= 16; z++) {
                final Classified c = rows.get(row + Math.floorDiv(z, 16))[dx + 1 + Math.floorDiv(x, 16)];
                if (c.empty) {
                    // A refused (empty) chunk: its cells stay SOLID, so it exposes no face of its neighbours
                    continue;
                }
                final int lx = Math.floorMod(x, 16);
                final int lz = Math.floorMod(z, 16);
                v.surface(x, z, c.surface[lx * 16 + lz]);
                final int base = (lx * 16 + lz) * height;
                for (int i = 0; i < height; i++) {
                    v.set(x, minY + i, z, c.cells[base + i]);
                }
            }
        }
        return v;
    }

    /** One chunk as cell codes ({@code (x * 16 + z) * height + (y - minY)}) and its surface per column. */
    private record Classified(short[] cells, int[] surface, boolean empty) {
        static Classified of(ChunkSnapshot snap, int minY, int height, Function<BlockData, Integer> classify) {
            final short[] cells = new short[256 * height];
            final int[] surface = new int[256];
            boolean empty = true;
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    // getHighestBlockYAt is the highest non-air block of the WORLD_SURFACE heightmap
                    surface[x * 16 + z] = Math.max(minY, snap.getHighestBlockYAt(x, z) + 1);
                    empty &= surface[x * 16 + z] == minY;
                    final int base = (x * 16 + z) * height;
                    for (int i = 0; i < height; i++) {
                        cells[base + i] = (short) (int) classify.apply(snap.getBlockData(x, minY + i, z));
                    }
                }
            }
            return new Classified(cells, surface, empty);
        }
    }

    /** The targets of a world: ids, which are counted, and the code of any block data. */
    private record Targets(String kind, List<String> ids, boolean[] counted, Map<BlockData, Integer> index) {
        int size() {
            return ids.size();
        }

        int code(BlockData data) {
            final Integer target = index.get(data);
            if (target != null) {
                return target;
            }
            final Material m = data.getMaterial();
            final boolean fluid = m == Material.WATER || m == Material.LAVA || m == Material.BUBBLE_COLUMN
                    || data instanceof Waterlogged w && w.isWaterlogged();
            return ExposureRule.cell(m.isAir(), fluid, m.isOccluding());
        }
    }

    /** Our world: every ore block the veins can place (counted) and the raw rocks (per target only). */
    static Targets tfcTargets(long seed, BlockVisuals visuals) {
        if (!visuals.isLoaded()) {
            throw new Skip("CraftEngine's blocks are not loaded: ore blocks cannot be recognised");
        }
        final List<String> rocks = PlaceholderRocks.names().stream().map(n -> "tfp:rock/raw/" + n).toList();
        final List<String> ores = new OreVeins(seed, veins(), rocks).oreIds();
        final List<String> ids = new ArrayList<>(ores);
        ids.addAll(rocks);
        final boolean[] counted = new boolean[ids.size()];
        Arrays.fill(counted, 0, ores.size(), true);
        final Map<BlockData, Integer> index = new HashMap<>();
        final List<String> missing = new ArrayList<>();
        for (int i = 0; i < ids.size(); i++) {
            final Optional<BlockData> data = visuals.blockData(BlockStateRef.parse(ids.get(i)));
            if (data.isEmpty()) {
                missing.add(ids.get(i));
            } else if (index.putIfAbsent(data.get(), i) != null) {
                throw new AssertionError(ids.get(i) + " shares its server state with " + ids.get(index.get(data.get())));
            }
        }
        if (!missing.isEmpty()) {
            throw new AssertionError(missing.size() + " ore/rock blocks have no server state, e.g. " + missing.getFirst());
        }
        return new Targets("tfc", List.copyOf(ids), counted, Map.copyOf(index));
    }

    /** A vanilla world: stone-family blocks and the vanilla ores, all counted. */
    static Targets proxyTargets() {
        final List<Material> materials = new ArrayList<>(PROXY_ROCKS);
        for (Material m : Material.values()) {
            if (!m.isLegacy() && m.isBlock() && m.name().endsWith("_ORE")) {
                materials.add(m);
            }
        }
        final List<String> ids = new ArrayList<>();
        final Map<BlockData, Integer> index = new HashMap<>();
        for (Material m : materials) {
            index.put(m.createBlockData(), ids.size());
            ids.add(m.getKey().toString());
        }
        final boolean[] counted = new boolean[ids.size()];
        Arrays.fill(counted, true);
        return new Targets("vanilla", List.copyOf(ids), counted, Map.copyOf(index));
    }

    /** The cluster veins of the shipped vein tag, read from the main plugin's jar. */
    private static List<ClusterVeinConfig> veins() {
        final List<ClusterVeinConfig> out = new ArrayList<>();
        for (String name : VeinData.tagOrder(json(VeinData.VEIN_TAG))) {
            VeinData.parse(name, json(VeinData.FEATURE_DIR + name + ".json")).ifPresent(out::add);
        }
        return out;
    }

    private static JsonObject json(String path) {
        final String full = RegistryService.JAR_DATA_ROOT + "/" + path;
        try (InputStream in = TfpPlugin.class.getClassLoader().getResourceAsStream(full)) {
            if (in == null) {
                throw new AssertionError(full + " is missing from the plugin jar");
            }
            return JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException e) {
            throw new AssertionError("cannot read " + full, e);
        }
    }

    /** {@code per_target} rows: an ore or rock without its host rock ({@code tfp:ore/normal_native_gold}). */
    static String group(String id) {
        final int colon = id.indexOf(':');
        final String path = id.substring(colon + 1);
        final int slash = path.lastIndexOf('/');
        return path.indexOf('/') != slash ? id.substring(0, colon + 1 + slash) : id;
    }

    private static double parse(String value) {
        if (value == null || value.isBlank()) {
            return Double.NaN;
        }
        return Double.parseDouble(value);
    }
}
