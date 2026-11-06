/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.oreoverlay;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.oreoverlay.ArrayOreSnapshot;
import dev.tfp.core.oreoverlay.OreOverlayTable;
import dev.tfp.core.oreoverlay.OreSnapshot;
import dev.tfp.core.oreoverlay.OverlayGate;
import dev.tfp.core.oreoverlay.OverlayKeys;
import dev.tfp.core.oreoverlay.census.ExposureRule;
import dev.tfp.core.visuals.OverlayFace;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The runtime on fakes: two "regions" (chunk x below 1 on {@code region-A}, the rest on {@code region-B}), a CPU pool,
 * a recording sender. Checks the same-bundle paths, the build/patch threading (world reads only on the owner region,
 * decisions on the CPU pool), border reads across a region border, cracks, the cap and the rate limit.
 */
class OreOverlayServiceTest {
    private static final UUID WORLD = UUID.randomUUID();
    private static final UUID BOT = UUID.randomUUID();
    private static final int MIN_Y = 0;
    private static final int MAX_Y = 32;
    private static final int AIR = 0;
    private static final int STONE = 1;

    private final ScheduledExecutorService regionA = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "region-A"));
    private final ScheduledExecutorService regionB = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "region-B"));
    private final ExecutorService cpu = Executors.newFixedThreadPool(2, r -> new Thread(r, "cpu"));
    private final Map<Long, Integer> world = new ConcurrentHashMap<>();
    private final List<String> violations = new CopyOnWriteArrayList<>();
    private final Recorder sender = new Recorder();
    private final OreOverlayData data = new OreOverlayData(
            Map.of("tfp:ore/normal_native_gold/granite", "tfp:ore_overlay/normal_native_gold",
                    "tfp:ore/rich_malachite/marble", "tfp:ore_overlay/rich_malachite"),
            Set.of("tfp:rock/raw/granite"), Set.of());
    private OreOverlayService service;

    /** Records every packet the service sends, with the thread. */
    static final class Recorder implements OverlaySender {
        final AtomicInteger ids = new AtomicInteger(1000);
        final List<Spawn> spawns = new CopyOnWriteArrayList<>();
        final List<int[]> removes = new CopyOnWriteArrayList<>();
        final List<String> morphs = new CopyOnWriteArrayList<>();
        final List<Rider> riders = new CopyOnWriteArrayList<>();

        @Override
        public int nextEntityId() {
            return ids.incrementAndGet();
        }

        @Override
        public void spawn(UUID viewer, List<Spawn> s) {
            spawns.addAll(s);
        }

        @Override
        public void remove(UUID viewer, int[] i) {
            removes.add(i);
        }

        @Override
        public void morph(UUID viewer, int entityId, OverlayFace face, String itemModel) {
            morphs.add(entityId + " " + face + " " + itemModel);
        }

        @Override
        public void ride(UUID viewer, int vehicleId, double x, double y, double z, List<Rider> r) {
            riders.addAll(r);
        }

        int removed() {
            return removes.stream().mapToInt(a -> a.length).sum();
        }
    }

    private ScheduledExecutorService owner(int chunkX) {
        return chunkX < 1 ? regionA : regionB;
    }

    private void assertOwner(int chunkX, String what) {
        String expected = chunkX < 1 ? "region-A" : "region-B";
        if (!Thread.currentThread().getName().equals(expected)) {
            violations.add(what + " of chunk x " + chunkX + " on " + Thread.currentThread().getName());
        }
    }

    private int code(int x, int y, int z) {
        if (y < MIN_Y) {
            return ExposureRule.SOLID;
        }
        if (y >= MAX_Y) {
            return ExposureRule.OPEN;
        }
        return world.getOrDefault(OverlayKeys.block(x, y, z), ExposureRule.SOLID);
    }

    private void set(int x, int y, int z, int code) {
        world.put(OverlayKeys.block(x, y, z), code);
    }

    @BeforeEach
    void start() {
        CellReader reader = new CellReader() {
            @Override
            public boolean loaded(UUID w, int chunkX, int chunkZ) {
                return chunkZ == 0 && chunkX >= -1 && chunkX <= 2;
            }

            @Override
            public OreSnapshot snapshot(UUID w, int chunkX, int chunkZ) {
                assertOwner(chunkX, "snapshot");
                ArrayOreSnapshot.Builder b = ArrayOreSnapshot.builder(chunkX, chunkZ, MIN_Y, MAX_Y, ExposureRule.SOLID);
                world.forEach((k, c) -> {
                    int x = OverlayKeys.blockX(k);
                    int z = OverlayKeys.blockZ(k);
                    int y = OverlayKeys.blockY(k);
                    if ((x >> 4) == chunkX && (z >> 4) == chunkZ && y >= MIN_Y && y < MAX_Y) {
                        b.set(x, y, z, c);
                    }
                });
                return b.build();
            }

            @Override
            public long cell(UUID w, int x, int y, int z) {
                assertOwner(x >> 4, "cell read");
                return CellMap.pack(code(x, y, z), MAX_Y + 100);
            }

            @Override
            public int minY(UUID w) {
                return MIN_Y;
            }

            @Override
            public int maxY(UUID w) {
                return MAX_Y;
            }
        };
        OreOverlayService.Regions regions = new OreOverlayService.Regions() {
            @Override
            public void run(UUID w, int chunkX, int chunkZ, Runnable task) {
                owner(chunkX).execute(task);
            }

            @Override
            public void nextTick(UUID w, int chunkX, int chunkZ, Runnable task) {
                owner(chunkX).schedule(task, 1, TimeUnit.MILLISECONDS);
            }
        };
        service = new OreOverlayService(data, reader, regions, cpu, sender, new OverlayGate(48, true), 8000, 2.5f,
                id -> id == STONE ? OreBlockSource.OCCLUDES : 0, Logger.getLogger("test"));
        service.world("minecraft:overworld", WORLD);
        service.viewerWorld(BOT, WORLD);
        service.center(BOT, 0, 0, new Outbox(BOT));
    }

    @AfterEach
    void stop() {
        regionA.shutdownNow();
        regionB.shutdownNow();
        cpu.shutdownNow();
    }

    private OreOverlayTable settle(int chunkX) {
        return service.settled(WORLD, chunkX, 0).orTimeout(10, TimeUnit.SECONDS).join();
    }

    /** Waits until no build, patch or dirty cell is pending. */
    private void quiesce() throws Exception {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        int idleRounds = 0;
        while (idleRounds < 3) {
            if (System.nanoTime() > end) {
                throw new AssertionError("the overlay service did not settle");
            }
            Thread.sleep(10);
            idleRounds = service.idle() ? idleRounds + 1 : 0;
        }
    }

    @Test
    void chunkSendBuildsOffThreadThenSpawnsAndResendUsesTheSameBundle() throws Exception {
        set(5, 10, 5, 0);
        set(5, 11, 5, ExposureRule.OPEN);
        Outbox first = new Outbox(BOT);
        service.chunkSent(BOT, 0, 0, first);
        assertThat(first.isEmpty()).as("no table yet: a build is queued").isTrue();
        OreOverlayTable t = settle(0);
        assertThat(t.faceCount()).isEqualTo(1);
        assertThat(sender.spawns).singleElement().satisfies(s -> {
            assertThat(s.face()).isEqualTo(OverlayFace.UP);
            assertThat(s.itemModel()).isEqualTo("tfp:ore_overlay/normal_native_gold");
            assertThat(s.viewRange()).isEqualTo(0.75f);
        });
        // a resend of a chunk the client has: nothing new; after an unload, the spawns travel in the chunk's own outbox
        Outbox resend = new Outbox(BOT);
        service.chunkSent(BOT, 0, 0, resend);
        assertThat(resend.isEmpty()).isTrue();
        Outbox unload = new Outbox(BOT);
        service.chunkUnloaded(BOT, 0, 0, unload);
        assertThat(unload.removeCount()).isEqualTo(1);
        assertThat(service.table(WORLD, 0, 0)).as("nobody has the chunk: table dropped").isNull();
        assertThat(violations).isEmpty();
    }

    @Test
    void unloadSendsOneRemovalWithEveryId() {
        for (int x = 2; x < 6; x++) {
            set(x, 10, 5, 0);
            set(x, 11, 5, ExposureRule.OPEN);
        }
        service.chunkSent(BOT, 0, 0, new Outbox(BOT));
        settle(0);
        assertThat(sender.spawns).hasSize(4);
        Outbox out = new Outbox(BOT);
        service.chunkUnloaded(BOT, 0, 0, out);
        out.flush(sender);
        assertThat(sender.removes).singleElement().satisfies(ids -> assertThat(ids).hasSize(4));
        assertThat(service.sentCount(BOT)).isZero();
    }

    @Test
    void blockChangeFastPathRidesWithThePacketAndThePatchAddsNothing() throws Exception {
        set(5, 10, 5, 0);
        service.chunkSent(BOT, 0, 0, new Outbox(BOT));
        assertThat(settle(0).faceCount()).isZero();
        // the Bot mines the stone above the ore
        set(5, 11, 5, ExposureRule.OPEN);
        Outbox out = new Outbox(BOT);
        service.blockChanged(BOT, 5, 11, 5, AIR, out);
        assertThat(out.spawnCount()).as("same bundle as the block change").isEqualTo(1);
        out.flush(sender);
        quiesce();
        assertThat(service.table(WORLD, 0, 0).faceCount()).isEqualTo(1);
        assertThat(sender.spawns).as("the patch agrees: no second spawn").hasSize(1);
        // and places it back
        set(5, 11, 5, ExposureRule.SOLID);
        Outbox cover = new Outbox(BOT);
        service.blockChanged(BOT, 5, 11, 5, STONE, cover);
        assertThat(cover.removeCount()).isEqualTo(1);
        cover.flush(sender);
        quiesce();
        assertThat(service.sentCount(BOT)).isZero();
        assertThat(sender.spawns).hasSize(1);
        assertThat(violations).isEmpty();
    }

    @Test
    void anOrePlacedNextToAirGetsItsFacesFromThePatch() throws Exception {
        set(5, 10, 5, ExposureRule.OPEN);
        service.chunkSent(BOT, 0, 0, new Outbox(BOT));
        settle(0);
        set(5, 10, 5, 1);
        Outbox out = new Outbox(BOT);
        service.blockChanged(BOT, 5, 10, 5, STONE, out);
        assertThat(out.isEmpty()).as("a host-rock visual cannot tell an ore").isTrue();
        quiesce();
        assertThat(sender.spawns).hasSize(0);
        // all six neighbours are stone: buried. Open one and patch.
        set(5, 9, 5, ExposureRule.OPEN);
        service.blockChanged(BOT, 5, 9, 5, AIR, new Outbox(BOT));
        quiesce();
        OreOverlayTable t = service.table(WORLD, 0, 0);
        assertThat(t.mask(t.find(5, 10, 5))).isEqualTo(1 << OverlayFace.DOWN.ordinal());
    }

    @Test
    void borderCellsAreReadOnTheNeighbourRegionAndEdgeChangesPatchBothChunks() throws Exception {
        set(15, 10, 0, 0);
        set(16, 10, 0, ExposureRule.OPEN); // chunk 1, region B
        service.chunkSent(BOT, 0, 0, new Outbox(BOT));
        service.chunkSent(BOT, 1, 0, new Outbox(BOT));
        OreOverlayTable t = settle(0);
        assertThat(t.mask(t.find(15, 10, 0))).isEqualTo(1 << OverlayFace.EAST.ordinal());
        // an ore in chunk 1 next to the edge, exposed by mining chunk 0's cell
        set(16, 12, 0, 1);
        set(15, 12, 0, ExposureRule.OPEN);
        service.markDirty(WORLD, 16, 12, 0);
        quiesce();
        service.blockChanged(BOT, 15, 12, 0, AIR, new Outbox(BOT));
        quiesce();
        OreOverlayTable east = service.table(WORLD, 1, 0);
        assertThat(east.mask(east.find(16, 12, 0))).isEqualTo(1 << OverlayFace.WEST.ordinal());
        assertThat(violations).as("every world read on its owner region").isEmpty();
    }

    @Test
    void cracksMorphEveryExposedFaceAndRestore() {
        set(5, 10, 5, 0);
        set(5, 11, 5, ExposureRule.OPEN);
        set(6, 10, 5, ExposureRule.OPEN);
        service.chunkSent(BOT, 0, 0, new Outbox(BOT));
        settle(0);
        service.crack(WORLD, 5, 10, 5, 3);
        assertThat(sender.morphs).hasSize(2).allMatch(m -> m.endsWith("tfp:ore_overlay/normal_native_gold/crack_3"));
        service.crack(WORLD, 5, 10, 5, 3);
        assertThat(sender.morphs).as("same stage: no packet").hasSize(2);
        service.crack(WORLD, 5, 10, 5, -1);
        assertThat(sender.morphs).hasSize(4);
        assertThat(sender.morphs.subList(2, 4)).allMatch(m -> m.endsWith("tfp:ore_overlay/normal_native_gold"));
    }

    @Test
    void rateLimitDefersBeyondTheBurstAndTickSendsTheRest() {
        // 1200 exposed faces in one chunk: a 30 x 1 x 16 sheet? Use 2 layers of 16 x 16 ores with air above and below.
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                set(x, 10, z, 0);
                set(x, 9, z, ExposureRule.OPEN);
                set(x, 11, z, ExposureRule.OPEN);
                set(x, 13, z, 1);
                set(x, 12, z, ExposureRule.OPEN);
                set(x, 14, z, ExposureRule.OPEN);
            }
        }
        // plus the open column cells around: the sheet's side faces at x = -1 and 16 are in other chunks (solid there)
        service.chunkSent(BOT, 0, 0, new Outbox(BOT));
        OreOverlayTable t = settle(0);
        assertThat(t.faceCount()).isEqualTo(1024);
        assertThat(sender.spawns).as("the 1000 burst").hasSize(1000);
        service.tick();
        assertThat(sender.spawns).as("+25 per tick").hasSize(1025 - 1);
        assertThat(service.stats(BOT).viewerDeferred()).isZero();
    }

    @Test
    void fallingOresGetSixRidersAndLoseThemWithTheEntity() {
        service.fallingSpawned(42, 1);
        assertThat(service.hasFalling()).isTrue();
        service.rideFalling(BOT, 42, 0.5, 20, 0.5);
        assertThat(sender.riders).hasSize(6).allMatch(r -> r.itemModel().equals("tfp:ore_overlay/rich_malachite"));
        assertThat(service.hasRiders(BOT)).isTrue();
        Outbox out = new Outbox(BOT);
        service.entitiesRemoved(BOT, new int[] {7, 42}, out);
        assertThat(out.removeCount()).isEqualTo(6);
        assertThat(service.hasRiders(BOT)).isFalse();
        service.fallingRemoved(42);
        assertThat(service.hasFalling()).isFalse();
    }

    @Test
    void respawnForgetsWithoutRemovalsAndQuitDropsUnwatchedTables() {
        set(5, 10, 5, 0);
        set(5, 11, 5, ExposureRule.OPEN);
        service.chunkSent(BOT, 0, 0, new Outbox(BOT));
        settle(0);
        assertThat(service.sentCount(BOT)).isEqualTo(1);
        service.viewerWorld(BOT, WORLD);
        assertThat(service.sentCount(BOT)).isZero();
        assertThat(sender.removes).isEmpty();
        service.chunkSent(BOT, 0, 0, new Outbox(BOT));
        service.quit(BOT);
        assertThat(service.table(WORLD, 0, 0)).isNull();
    }
}
