/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.itest.cases;

import dev.tfp.core.animal.AnimalCheck;
import dev.tfp.core.animal.AnimalRecord;
import dev.tfp.core.animal.AnimalScale;
import dev.tfp.core.animal.AnimalSpawnRoll;
import dev.tfp.core.animal.Carrier;
import dev.tfp.core.calendar.CalendarTicks;
import dev.tfp.core.calendar.TimeCommand;
import dev.tfp.core.livestock.LivestockAge;
import dev.tfp.core.livestock.LivestockRules;
import dev.tfp.itest.SelfTestCase;
import dev.tfp.itest.SelfTestContext;
import dev.tfp.paper.TfpPlugin;
import dev.tfp.paper.animal.AnimalService;
import dev.tfp.paper.integration.EntityModels;
import dev.tfp.paper.scheduler.Scheduler;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;

/**
 * {@code animal.spawn_persist} (M5-ENT-01), over two starts on one run directory (as {@code inst.profile_restart}):
 * <ol>
 *   <li>First start: a child {@code tfc:cow} spawns on a cow carrier in a force-loaded chunk. Its PDC record, the SCALE
 *       attribute ({@code AGE_SCALES}), the vanilla baby flag and one model are checked, then the calendar is moved past
 *       its adulthood and the staggered check must turn it adult. The carrier, its chunk and a marker are kept.</li>
 *   <li>Second start: the carrier is found again; the record is unchanged, it is still adult, its check runs and its
 *       model is back. Then it is removed with its chunk ticket and the marker.</li>
 * </ol>
 */
public final class AnimalSpawnPersistCase implements SelfTestCase {
    static final String MARKER = "animal-spawn-persist.marker";
    private static final Duration STEP = Duration.ofSeconds(15);
    private static final Duration CHECK = Duration.ofSeconds(10);
    /** Chunks east of the spawn chunk. */
    private static final int CHUNK_OFFSET = 2;

    @Override
    public String name() {
        return "animal.spawn_persist: a child cow on its carrier grows up on the staggered check and survives a restart (M5-ENT-01)";
    }

    @Override
    public Duration timeout() {
        return Duration.ofSeconds(120);
    }

    @Override
    public CompletableFuture<String> run(SelfTestContext ctx) {
        TfpPlugin tfp = ctx.tfp();
        if (!tfp.integrations().activePlugins().contains("BetterModel")) {
            throw new Skip("BetterModel is not installed or its adapter is disabled");
        }
        Path dataDir = ctx.dataDir();
        if (dataDir == null) {
            throw new Skip("no self-test data folder for the restart marker");
        }
        AnimalService animals = tfp.animals();
        if (animals == null) {
            throw new AssertionError("no animal service");
        }
        Path marker = dataDir.resolve(MARKER);
        return OnThread.script("animal-persist", () -> {
            if (!OnThread.poll(CHECK, () -> tfp.clock().get().isSet())) {
                throw new AssertionError("the calendar was never published");
            }
            if (!OnThread.poll(STEP, () -> tfp.integrations().entityModels().isReady())) {
                throw new AssertionError("BetterModel models are not ready");
            }
            return Files.exists(marker) ? secondStart(tfp, animals, marker) : firstStart(tfp, animals, dataDir, marker);
        });
    }

    private static String firstStart(TfpPlugin tfp, AnimalService animals, Path dataDir, Path marker) throws Exception {
        Scheduler s = tfp.scheduler();
        EntityModels models = tfp.integrations().entityModels();
        World world = Bukkit.getWorlds().getFirst();
        // Two chunks away from bm.spawn_persist's spawn chunk: that case un-force-loads its chunk when it cleans up,
        // which would unload our carrier between the two starts.
        Location spawn = world.getSpawnLocation();
        int cx = (spawn.getBlockX() >> 4) + CHUNK_OFFSET;
        int cz = spawn.getBlockZ() >> 4;
        OnThread.await(world.getChunkAtAsync(cx, cz), STEP, "loading chunk " + cx + "," + cz);
        OnThread.global(s, STEP, () -> {
            world.setChunkForceLoaded(cx, cz, true);
            return null;
        });
        Location at = OnThread.region(s, world, cx, cz, STEP, () -> world.getHighestBlockAt((cx << 4) + 8, (cz << 4) + 8)
                .getLocation().add(0.5, 1, 0.5));
        Carrier cow = animals.table().find("tfc:cow").orElseThrow();
        Entity e = OnThread.await(animals.spawn(at, cow, AnimalSpawnRoll.AgeHint.CHILD, null), STEP, "spawning tfc:cow");
        AnimalRecord record = OnThread.entity(s, e, STEP, () -> animals.record(e).orElseThrow(
                () -> new AssertionError("no tfp:animal record on the carrier")));
        long now = tfp.clock().get().playerTicks();
        if (AnimalCheck.ageAt(record, now) != LivestockAge.CHILD) {
            throw new AssertionError("spawned with the child hint but reads " + AnimalCheck.ageAt(record, now));
        }
        String body = OnThread.entity(s, e, STEP, () -> bodyOf(e));
        String expectChild = "baby=true scale=" + AnimalScale.ageScale(record.geneticSize());
        if (!body.equals(expectChild)) {
            throw new AssertionError("child carrier: expected " + expectChild + ", got " + body);
        }
        if (!OnThread.poll(STEP, () -> models.modelCount(e) == 1)) {
            throw new AssertionError("expected 1 model on the carrier, got " + models.modelCount(e));
        }
        if (!OnThread.poll(CHECK, () -> animals.checks(e) >= 1)) {
            throw new AssertionError("the staggered check did not run within " + CHECK.toSeconds() + " s");
        }
        // Move the calendar past adulthood (adulthood is in player-day units of 28 800 ticks, /tfp time adds 24 000).
        long adultTick = record.state().birthTick()
                + (long) cow.livestock().orElseThrow().adulthoodDays() * LivestockRules.PLAYER_TICKS_IN_DEFAULT_DAY;
        int days = (int) ((adultTick - now) / CalendarTicks.CALENDAR_TICKS_IN_DAY) + 2;
        OnThread.global(s, STEP, () -> tfp.calendar().skip(TimeCommand.add(TimeCommand.Unit.DAYS, days)));
        long before = animals.checks(e);
        if (!OnThread.poll(CHECK, () -> animals.checks(e) > before + 1)) {
            throw new AssertionError("the check stopped after the time skip");
        }
        String expectAdult = "baby=false scale=" + AnimalScale.ageScale(record.geneticSize());
        if (!OnThread.poll(CHECK, () -> expectAdult.equals(entityBody(s, e)))) {
            throw new AssertionError("after " + days + " days: expected " + expectAdult + ", got " + entityBody(s, e));
        }
        if (models.modelCount(e) != 1) {
            throw new AssertionError("after growing up: " + models.modelCount(e) + " model(s)");
        }
        // The carrier keeps its vanilla goals and wanders: keep the 3x3 chunks around where it is now loaded, so it
        // is saved and comes back in one of them.
        int[] now2 = OnThread.entity(s, e, STEP, () -> new int[] {e.getLocation().getBlockX() >> 4,
                e.getLocation().getBlockZ() >> 4});
        int ax = now2[0];
        int az = now2[1];
        OnThread.global(s, STEP, () -> {
            forceArea(world, ax, az, true);
            if (Math.abs(cx - ax) > 1 || Math.abs(cz - az) > 1) {
                world.setChunkForceLoaded(cx, cz, false);
            }
            return null;
        });
        Properties p = new Properties();
        p.setProperty("uuid", e.getUniqueId().toString());
        p.setProperty("cx", Integer.toString(ax));
        p.setProperty("cz", Integer.toString(az));
        p.setProperty("birthTick", Long.toString(record.state().birthTick()));
        p.setProperty("size", Integer.toString(record.geneticSize()));
        Files.createDirectories(dataDir);
        try (var w = Files.newBufferedWriter(marker, StandardCharsets.UTF_8)) {
            p.store(w, "animal.spawn_persist first start");
        }
        return "first start: child tfc:cow " + e.getUniqueId() + " (size " + record.geneticSize() + ") on "
                + e.getType().getKey() + ", 1 model, adult after +" + days + " days; kept for the second start";
    }

    private static String secondStart(TfpPlugin tfp, AnimalService animals, Path marker) throws Exception {
        Properties p = new Properties();
        try {
            p.load(new StringReader(Files.readString(marker, StandardCharsets.UTF_8)));
        } catch (IOException e) {
            Files.deleteIfExists(marker);
            throw e;
        }
        Scheduler s = tfp.scheduler();
        EntityModels models = tfp.integrations().entityModels();
        World world = Bukkit.getWorlds().getFirst();
        UUID id = UUID.fromString(p.getProperty("uuid"));
        int cx = Integer.parseInt(p.getProperty("cx"));
        int cz = Integer.parseInt(p.getProperty("cz"));
        Entity[] found = {null};
        try {
            OnThread.global(s, STEP, () -> {
                forceArea(world, cx, cz, true); // kept from the first start; again in case it was lost
                return null;
            });
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    OnThread.await(world.getChunkAtAsync(cx + dx, cz + dz), STEP, "loading chunk " + (cx + dx) + ","
                            + (cz + dz));
                }
            }
            boolean seen = OnThread.poll(Duration.ofSeconds(30), () -> {
                try {
                    found[0] = findIn(s, world, cx, cz, id, null);
                } catch (Exception ex) {
                    throw new IllegalStateException(ex);
                }
                return found[0] != null;
            });
            if (!seen) {
                throw new AssertionError("the carrier " + id + " did not come back in the 3x3 chunks around " + cx
                        + "," + cz);
            }
            Entity e = found[0];
            if (!e.isValid()) {
                throw new AssertionError("found " + id + " in chunk " + cx + "," + cz + " but it is not valid: dead="
                        + e.isDead() + " inWorld=" + e.isInWorld() + " type=" + e.getType());
            }
            AnimalRecord record = OnThread.entity(s, e, STEP, () -> animals.record(e).orElseThrow(
                    () -> new AssertionError("the tfp:animal record did not survive the restart")));
            if (record.state().birthTick() != Long.parseLong(p.getProperty("birthTick"))
                    || record.geneticSize() != Integer.parseInt(p.getProperty("size"))) {
                throw new AssertionError("record changed over the restart: " + record + " vs " + p);
            }
            if (AnimalCheck.ageAt(record, tfp.clock().get().playerTicks()) != LivestockAge.ADULT) {
                throw new AssertionError("not adult after the restart");
            }
            if (!OnThread.poll(CHECK, () -> animals.checks(e) >= 1)) {
                throw new AssertionError("the staggered check did not restart with the carrier");
            }
            if (!OnThread.poll(STEP, () -> models.modelCount(e) == 1)) {
                throw new AssertionError("the model did not come back: " + models.modelCount(e) + " model(s)");
            }
            String expect = "baby=false scale=" + AnimalScale.ageScale(record.geneticSize());
            if (!OnThread.poll(CHECK, () -> expect.equals(entityBody(s, e)))) {
                throw new AssertionError("after restart: expected " + expect + ", got " + entityBody(s, e));
            }
            return "second start: " + id + " came back adult with its record, check and 1 model";
        } finally {
            // Cleanup must not hide the case's own failure: remove by UUID on the chunk's region.
            try {
                findIn(s, world, cx, cz, id, models);
                OnThread.global(s, STEP, () -> {
                    forceArea(world, cx, cz, false); // Folia: force-loading only on the global region
                    return null;
                });
            } catch (Exception | AssertionError ex) {
                tfp.getLogger().warning("animal.spawn_persist cleanup failed: " + ex);
            }
            Files.deleteIfExists(marker);
        }
    }

    /** Force-loads (or releases) the 3x3 chunks around {@code cx, cz}. Global thread. */
    private static void forceArea(World world, int cx, int cz, boolean on) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                world.setChunkForceLoaded(cx + dx, cz + dz, on);
            }
        }
    }

    /**
     * The entity {@code id} in the 3x3 chunks around {@code cx, cz}, each searched on its own region; {@code remove}
     * detaches its model and removes it.
     */
    private static Entity findIn(Scheduler s, World world, int cx, int cz, UUID id, EntityModels removeWith)
            throws Exception {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                int x = cx + dx;
                int z = cz + dz;
                Entity hit = OnThread.region(s, world, x, z, STEP, () -> {
                    for (Entity en : world.getChunkAt(x, z).getEntities()) {
                        if (en.getUniqueId().equals(id)) {
                            if (removeWith != null) {
                                removeWith.detach(en);
                                en.remove();
                            }
                            return en;
                        }
                    }
                    return null;
                });
                if (hit != null) {
                    return hit;
                }
            }
        }
        return null;
    }

    private static String entityBody(Scheduler s, Entity e) {
        try {
            return OnThread.entity(s, e, STEP, () -> bodyOf(e));
        } catch (Exception ex) {
            return "unreadable: " + ex;
        }
    }

    /** The carrier's vanilla baby flag and SCALE attribute base. Owning thread. */
    private static String bodyOf(Entity e) {
        boolean baby = e instanceof Ageable a && !a.isAdult();
        double scale = e instanceof LivingEntity l && l.getAttribute(Attribute.SCALE) != null
                ? l.getAttribute(Attribute.SCALE).getBaseValue() : Double.NaN;
        return "baby=" + baby + " scale=" + (float) scale;
    }
}
