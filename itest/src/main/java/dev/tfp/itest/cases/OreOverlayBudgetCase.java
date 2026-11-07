/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.itest.cases;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.tfp.core.oreoverlay.OreOverlayTable;
import dev.tfp.core.oreoverlay.census.CensusReport;
import dev.tfp.itest.SelfTestCase;
import dev.tfp.itest.SelfTestContext;
import dev.tfp.paper.TfpPlugin;
import dev.tfp.paper.oreoverlay.OreOverlayService;
import dev.tfp.paper.oreoverlay.Outbox;
import dev.tfp.paper.scheduler.Scheduler;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.SplittableRandom;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

/**
 * {@code ore.overlay.budget} (PAR-REL-12): the overlay entity budget of the real runtime on the S1 area.
 *
 * <p>A square of {@code tfp.oreOverlayBudget.chunks} × {@code tfp.oreOverlayBudget.chunks} chunks (default
 * {@value #DEFAULT_SIZE}; 32 is the S1 area) centred on the spawn chunk, plus a one-chunk ring for the border reads, is
 * generated asynchronously and held by force-load tickets (set on the global region, after generation, so loading is a
 * disk read). Then, for {@value CensusReport#SAMPLES} sample positions (the same centres as the S1 census,
 * {@link CensusReport#radiusTotals}: seeded by the world seed), a fake viewer joins at the sample's chunk: the service is
 * told the viewer's world and centre and is sent every chunk of the gated radius ({@code chunkSent}, as the packet
 * handler does on join). Once every table is published and the rate limiter released every deferred face, the case
 * records the entities the viewer holds, the packets that took (one {@code ADD_ENTITY} and one {@code SET_ENTITY_DATA}
 * each, as {@code OverlayDisplaySender} sends them), the bytes (S1's measured ≈ 125 B uncompressed per overlay; the fake
 * viewer has no connection to measure) and the table faces within the radius. Table builds: count, average and maximum
 * CPU time from the service's counters.
 *
 * <p>Fails when a viewer holds more overlays than {@code ore-overlay.max-per-player} or than the S1 gate
 * ({@value #GATE}), when the p95 of the raw faces within the radius exceeds the gate, or when a viewer's entities
 * differ from {@code min(faces, cap)}. Writes {@code ore-overlay-budget.json} in the server folder.
 */
public final class OreOverlayBudgetCase implements SelfTestCase {
    static final int DEFAULT_SIZE = 10;
    /** The S1 gate: overlays per player within the gated radius. */
    static final int GATE = 8000;
    /** S1 (PAR-WORLD-17 §3): uncompressed bytes per overlay (spawn + data). */
    static final int BYTES_PER_OVERLAY = 125;
    private static final Duration STEP = Duration.ofSeconds(60);

    private final int size = Integer.getInteger("tfp.oreOverlayBudget.chunks", DEFAULT_SIZE);

    @Override
    public String name() {
        return "ore.overlay.budget: overlay entities per player at the S1 sample positions stay within the gate (PAR-REL-12)";
    }

    @Override
    public Duration timeout() {
        final int ring = Math.max(size, 9) + 2;
        return Duration.ofSeconds(CraftEngineBlockRoundTripCase.REBUILD.toSeconds() + 240 + 2L * ring * ring);
    }

    @Override
    public CompletableFuture<String> run(SelfTestContext ctx) {
        final TfpPlugin tfp = ctx.tfp();
        return OnThread.script("ore-overlay-budget", () -> {
            OnThread.awaitPackRebuild(tfp, CraftEngineBlockRoundTripCase.REBUILD);
            final OreOverlayService service = tfp.moduleServices().get(OreOverlayService.class)
                    .orElseThrow(() -> new Skip("the ore overlay module is off (CraftEngine or PacketEvents missing)"));
            return measure(service, tfp.scheduler(), Bukkit.getWorlds().getFirst());
        });
    }

    private String measure(OreOverlayService service, Scheduler s, World world) throws Exception {
        final int radius = service.gate().radiusChunks();
        final int width = 2 * radius + 1;
        if (radius < 0) {
            throw new Skip("ore-overlay.cutoff-blocks is 0: no cave overlays are sent");
        }
        if (size < width) {
            throw new AssertionError("tfp.oreOverlayBudget.chunks " + size + " is smaller than the gated square " + width);
        }
        final int cap = service.stats(null).viewerCap();
        final Location spawn = world.getSpawnLocation();
        final int originX = (spawn.getBlockX() >> 4) - size / 2;
        final int originZ = (spawn.getBlockZ() >> 4) - size / 2;
        final UUID w = world.getUID();
        service.world(world.getName(), w);

        final long started = System.nanoTime();
        final List<int[]> loaded = new ArrayList<>();
        try {
            // generate (async) and hold the square plus its ring
            for (int dz = -1; dz <= size; dz++) {
                final List<CompletableFuture<?>> row = new ArrayList<>();
                for (int dx = -1; dx <= size; dx++) {
                    row.add(world.getChunkAtAsync(originX + dx, originZ + dz));
                }
                OnThread.await(CompletableFuture.allOf(row.toArray(CompletableFuture[]::new)), STEP,
                        "generating chunk row " + (originZ + dz));
                final int z = originZ + dz;
                OnThread.global(s, STEP, () -> {
                    for (int dx = -1; dx <= size; dx++) {
                        world.setChunkForceLoaded(originX + dx, z, true);
                        loaded.add(new int[] {originX + dx, z});
                    }
                    return null;
                });
            }
            final double genSeconds = (System.nanoTime() - started) / 1e9;
            final OreOverlayService.Stats before = service.stats(null);

            final SplittableRandom random = new SplittableRandom(world.getSeed() ^ radius * 0x9E3779B97F4A7C15L);
            final int span = size - width + 1;
            final int[] entities = new int[CensusReport.SAMPLES];
            final int[] faces = new int[CensusReport.SAMPLES];
            final double[] settleMs = new double[CensusReport.SAMPLES];
            final JsonArray samples = new JsonArray();
            final List<String> problems = new ArrayList<>();
            for (int i = 0; i < CensusReport.SAMPLES; i++) {
                final int vcx = originX + random.nextInt(span) + radius;
                final int vcz = originZ + random.nextInt(span) + radius;
                final UUID viewer = UUID.randomUUID();
                final long t0 = System.nanoTime();
                try {
                    service.viewerWorld(viewer, w);
                    service.center(viewer, vcx, vcz, new Outbox(viewer));
                    for (int dz = -radius; dz <= radius; dz++) {
                        for (int dx = -radius; dx <= radius; dx++) {
                            service.chunkSent(viewer, vcx + dx, vcz + dz, new Outbox(viewer));
                        }
                    }
                    int sum = 0;
                    for (int dz = -radius; dz <= radius; dz++) {
                        for (int dx = -radius; dx <= radius; dx++) {
                            final OreOverlayTable t = OnThread.await(service.settled(w, vcx + dx, vcz + dz), STEP,
                                    "the overlay table of chunk " + (vcx + dx) + "," + (vcz + dz));
                            sum += t.faceCount();
                        }
                    }
                    if (!OnThread.poll(STEP, () -> service.stats(viewer).viewerDeferred() == 0)) {
                        problems.add("sample " + i + ": faces still deferred after " + STEP.toSeconds() + " s");
                    }
                    faces[i] = sum;
                    entities[i] = service.sentCount(viewer);
                    settleMs[i] = (System.nanoTime() - t0) / 1e6;
                } finally {
                    service.quit(viewer);
                }
                if (entities[i] != Math.min(faces[i], cap)) {
                    problems.add("sample " + i + " at chunk " + vcx + "," + vcz + ": viewer holds " + entities[i]
                            + " overlays, expected min(" + faces[i] + " faces, cap " + cap + ")");
                }
                if (entities[i] > Math.min(cap, GATE)) {
                    problems.add("sample " + i + " at chunk " + vcx + "," + vcz + ": " + entities[i]
                            + " overlays > gate " + Math.min(cap, GATE));
                }
                final JsonObject o = new JsonObject();
                o.addProperty("chunk_x", vcx);
                o.addProperty("chunk_z", vcz);
                o.addProperty("faces", faces[i]);
                o.addProperty("entities", entities[i]);
                o.addProperty("packets", 2L * entities[i]);
                o.addProperty("bytes_estimate", (long) BYTES_PER_OVERLAY * entities[i]);
                o.addProperty("settle_ms", Math.round(settleMs[i]));
                samples.add(o);
            }
            final OreOverlayService.Stats after = service.stats(null);
            final long builds = after.builds() - before.builds();
            final double avgBuildMs = builds == 0 ? 0 : (after.buildNanos() - before.buildNanos()) / 1e6 / builds;
            final double maxBuildMs = after.maxBuildNanos() / 1e6;
            final int p95Faces = CensusReport.percentile(faces, 0.95);
            if (p95Faces > GATE) {
                problems.add("p95 faces within the gated radius " + p95Faces + " > gate " + GATE);
            }

            final JsonObject root = new JsonObject();
            root.addProperty("server", Bukkit.getName() + " " + Bukkit.getVersion());
            root.addProperty("minecraft", Bukkit.getMinecraftVersion());
            root.addProperty("seed", world.getSeed());
            root.addProperty("size", size);
            final JsonArray origin = new JsonArray();
            origin.add(originX);
            origin.add(originZ);
            root.add("origin_chunk", origin);
            root.addProperty("radius_chunks", radius);
            root.addProperty("cap", cap);
            root.addProperty("gate", GATE);
            root.addProperty("generation_seconds", Math.round(genSeconds * 10) / 10.0);
            root.add("faces", CensusReport.distribution(faces));
            root.add("entities", CensusReport.distribution(entities));
            final int[] packets = Arrays.stream(entities).map(e -> 2 * e).toArray();
            root.add("packets", CensusReport.distribution(packets));
            root.add("bytes_estimate", CensusReport.distribution(Arrays.stream(entities).map(e -> BYTES_PER_OVERLAY * e)
                    .toArray()));
            final JsonObject build = new JsonObject();
            build.addProperty("builds", builds);
            build.addProperty("avg_ms", Math.round(avgBuildMs * 1000) / 1000.0);
            build.addProperty("max_ms_since_start", Math.round(maxBuildMs * 1000) / 1000.0);
            root.add("table_build", build);
            root.add("samples", samples);
            final JsonArray found = new JsonArray();
            problems.forEach(found::add);
            root.add("problems", found);
            final Path out = Path.of(System.getProperty("tfp.oreOverlayBudget.output", "ore-overlay-budget.json"));
            Files.writeString(out, new GsonBuilder().setPrettyPrinting().create().toJson(root), StandardCharsets.UTF_8);

            final String summary = String.format(Locale.ROOT,
                    "%dx%d chunks at %d,%d, radius %d, cap %d: faces p50/p95/max %d/%d/%d, entities p95/max %d/%d, "
                            + "join packets p95 %d (~%d B est.), %d table builds avg %.3f ms max %.3f ms; %s",
                    size, size, originX, originZ, radius, cap, CensusReport.percentile(faces, 0.5), p95Faces,
                    CensusReport.percentile(faces, 1.0), CensusReport.percentile(entities, 0.95),
                    CensusReport.percentile(entities, 1.0), 2 * CensusReport.percentile(entities, 0.95),
                    (long) BYTES_PER_OVERLAY * CensusReport.percentile(entities, 0.95), builds, avgBuildMs, maxBuildMs,
                    out);
            if (!problems.isEmpty()) {
                throw new AssertionError(problems.size() + " budget problem(s): " + String.join("; ", problems)
                        + " (" + summary + ")");
            }
            return summary;
        } finally {
            if (!loaded.isEmpty()) {
                OnThread.global(s, STEP, () -> {
                    for (int[] c : loaded) {
                        world.setChunkForceLoaded(c[0], c[1], false);
                    }
                    return null;
                });
            }
        }
    }
}
