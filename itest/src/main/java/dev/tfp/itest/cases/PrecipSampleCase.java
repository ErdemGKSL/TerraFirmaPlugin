/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.itest.cases;

import dev.tfp.core.calendar.Month;
import dev.tfp.core.calendar.TimeCommand;
import dev.tfp.core.climate.CalendarPhase;
import dev.tfp.core.climate.ChunkClimate;
import dev.tfp.core.climate.OverworldClimate;
import dev.tfp.core.climate.Solar;
import dev.tfp.core.weather.PrecipitationType;
import dev.tfp.core.world.BlockPos;
import dev.tfp.itest.SelfTestCase;
import dev.tfp.itest.SelfTestContext;
import dev.tfp.paper.TfpPlugin;
import dev.tfp.paper.calendar.CalendarService;
import dev.tfp.paper.climate.ChunkClimateSource;
import dev.tfp.paper.scheduler.Scheduler;
import dev.tfp.paper.weather.PrecipitationService;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;

/**
 * {@code weather.precip_sample} (PAR-CLIM-15, for PAR-CLIM-02): {@link PrecipitationService} on a real server, found
 * through {@code ModuleServices}, deciding TFC's {@code WeatherHelpers.getPrecipitationAt} behind vanilla's
 * {@code isRainingAt} gates with the self-test world's seed and the real calendar.
 * <ol>
 *   <li>A column near spawn (or a few thousand blocks north or south of it, when spawn's climate cannot give both) is
 *       loaded. Its high cell (two below the build limit) and its low cell (one above the motion-blocking top) are the
 *       cold and the warm position.</li>
 *   <li>From the climate model alone (the same seed, {@link OverworldClimate#rainIntensity}, the chunk's climate), the
 *       case finds the next calendar time in a local winter month where the high cell gets snow, and a later one in a
 *       local summer month where the low cell gets rain, each with a margin that holds for a minute of calendar time.
 *       The calendar is moved there through {@link CalendarService#skip}.</li>
 *   <li>There the service must answer SNOW at the high cell and RAIN at the low cell, with {@code rainReaches} read on
 *       the region thread; NONE under a stone roof placed three blocks above the low cell ({@code rainReaches} false),
 *       and NONE in the Nether.</li>
 * </ol>
 * The calendar only moves forward; nothing else in the run depends on its value. The roof block is restored.
 */
public final class PrecipSampleCase implements SelfTestCase {
    private static final Duration STEP = Duration.ofSeconds(60);
    /** Candidate columns: spawn, then this many blocks north and south of it, up to {@link #MAX_OFFSETS} steps. */
    private static final int Z_STEP = 2_000;
    private static final int MAX_OFFSETS = 5;
    /** How far ahead the search looks: four years of the default 8-day months. */
    private static final long SEARCH_TICKS = 4L * 12 * 8 * 24_000;
    private static final long SEARCH_STEP = 600;
    /** The decision must hold over this much calendar time after the target (the calendar may run during the case). */
    private static final long HOLD_TICKS = 1_200;
    /** Degrees away from zero, and real rain intensity above zero, that the target keeps. */
    private static final float TEMPERATURE_MARGIN = 1.5f;
    private static final float INTENSITY_MARGIN = 0.05f;

    @Override
    public String name() {
        return "weather.precip_sample: PrecipitationService gives SNOW high in winter, RAIN low in summer, NONE under a "
                + "roof and in the Nether (PAR-CLIM-15)";
    }

    @Override
    public Duration timeout() {
        return Duration.ofSeconds(4 * STEP.toSeconds() * (2 * MAX_OFFSETS + 1));
    }

    @Override
    public CompletableFuture<String> run(SelfTestContext ctx) {
        TfpPlugin tfp = ctx.tfp();
        return OnThread.script("precip-sample", () -> {
            PrecipitationService service = tfp.moduleServices().get(PrecipitationService.class)
                    .orElseThrow(() -> new AssertionError("no PrecipitationService in ModuleServices"));
            check(PrecipitationService.current().orElse(null) == service,
                    "ModuleServices holds another PrecipitationService than the started one");
            if (!OnThread.poll(Duration.ofSeconds(20), () -> tfp.clock().get().isSet())) {
                throw new AssertionError("the calendar was never published");
            }
            check(tfp.calendar() != null, "no calendar service");
            return scenario(tfp, service);
        });
    }

    /** A column and its two cells. */
    private record Column(int x, int z, int highY, int lowY) {
        BlockPos high() {
            return new BlockPos(x, highY, z);
        }

        BlockPos low() {
            return new BlockPos(x, lowY, z);
        }
    }

    private static String scenario(TfpPlugin tfp, PrecipitationService service) throws Exception {
        Scheduler s = tfp.scheduler();
        World world = Bukkit.getWorlds().getFirst();
        check(world.getEnvironment() == World.Environment.NORMAL, "the first world is " + world.getEnvironment());
        long seed = world.getSeed();
        OverworldClimate climate = OverworldClimate.forWorldSeed(seed, OverworldClimate.DEFAULT_TEMPERATURE_SCALE);
        int daysInMonth = tfp.clock().get().daysInMonth();
        Location spawn = world.getSpawnLocation();
        List<String> log = new ArrayList<>();

        Column column = null;
        long winter = -1;
        long summer = -1;
        List<String> tried = new ArrayList<>();
        for (int i = 0; i <= 2 * MAX_OFFSETS && column == null; i++) {
            int offset = (i + 1) / 2 * Z_STEP * (i % 2 == 1 ? 1 : -1);
            int x = spawn.getBlockX();
            int z = spawn.getBlockZ() + offset;
            Column c = loadColumn(s, world, x, z);
            ChunkClimate chunk = ChunkClimateSource.climateAt(seed, x, z);
            long now = tfp.clock().get().calendarTicks();
            OptionalLong w = find(climate, chunk, c.high(), now + 1, daysInMonth, PrecipitationType.SNOW);
            OptionalLong su = w.isPresent()
                    ? find(climate, chunk, c.low(), w.getAsLong() + HOLD_TICKS + 1, daysInMonth, PrecipitationType.RAIN)
                    : OptionalLong.empty();
            if (w.isPresent() && su.isPresent()) {
                column = c;
                winter = w.getAsLong();
                summer = su.getAsLong();
            } else {
                tried.add(x + "," + z + (w.isPresent() ? " (no summer rain low at y " + c.lowY() + ")"
                        : " (no winter snow at y " + c.highY() + ")"));
            }
        }
        check(column != null, "no column with winter snow high and summer rain low within "
                + SEARCH_TICKS + " calendar ticks: " + tried);
        if (!tried.isEmpty()) {
            log.add("skipped columns " + tried);
        }
        Column c = column;
        log.add("column " + c.x() + "," + c.z() + " high y " + c.highY() + " low y " + c.lowY());

        // Winter: snow at the high cell.
        moveTo(tfp, s, winter);
        boolean highReaches = OnThread.region(s, world, c.x() >> 4, c.z() >> 4, STEP,
                () -> PrecipitationService.rainReaches(world, c.x(), c.highY(), c.z()));
        check(highReaches, "rain does not reach the high cell " + c.high());
        PrecipitationType high = service.precipitationAt(world, c.high(), highReaches);
        check(high == PrecipitationType.SNOW, "high cell in winter: " + high + " " + describe(climate, seed, c.high(),
                tfp.clock().get().calendarTicks(), daysInMonth));
        check(!service.isRainingAt(world, c.high(), highReaches), "isRainingAt is true where it snows");
        log.add("calendar " + winter + " (" + monthOf(winter, daysInMonth) + "): high SNOW");

        // Summer: rain at the low cell, NONE under a roof, NONE in the Nether.
        moveTo(tfp, s, summer);
        boolean lowReaches = OnThread.region(s, world, c.x() >> 4, c.z() >> 4, STEP,
                () -> PrecipitationService.rainReaches(world, c.x(), c.lowY(), c.z()));
        check(lowReaches, "rain does not reach the low cell " + c.low());
        PrecipitationType low = service.precipitationAt(world, c.low(), lowReaches);
        check(low == PrecipitationType.RAIN, "low cell in summer: " + low + " " + describe(climate, seed, c.low(),
                tfp.clock().get().calendarTicks(), daysInMonth));
        check(service.isRainingAt(world, c.low(), lowReaches), "isRainingAt is false where it rains");
        PrecipitationType read = OnThread.await(service.readAt(world, c.low(), lowReaches), STEP, "readAt");
        check(read == PrecipitationType.RAIN, "readAt on a worker gave " + read);
        log.add("calendar " + summer + " (" + monthOf(summer, daysInMonth) + "): low RAIN");

        int roofY = c.lowY() + 3;
        BlockData[] original = new BlockData[1];
        boolean roofed;
        try {
            roofed = OnThread.region(s, world, c.x() >> 4, c.z() >> 4, STEP, () -> {
                Block roof = world.getBlockAt(c.x(), roofY, c.z());
                original[0] = roof.getBlockData();
                roof.setType(Material.STONE, false);
                return PrecipitationService.rainReaches(world, c.x(), c.lowY(), c.z());
            });
        } finally {
            OnThread.region(s, world, c.x() >> 4, c.z() >> 4, STEP, () -> {
                if (original[0] != null) {
                    world.getBlockAt(c.x(), roofY, c.z()).setBlockData(original[0], false);
                }
                return null;
            });
        }
        check(!roofed, "rain reaches the low cell under a stone roof at y " + roofY);
        PrecipitationType underRoof = service.precipitationAt(world, c.low(), roofed);
        check(underRoof == PrecipitationType.NONE, "under a roof: " + underRoof);
        log.add("roof at y " + roofY + ": rainReaches false, NONE");

        World nether = Bukkit.getWorlds().stream()
                .filter(wd -> wd.getEnvironment() == World.Environment.NETHER).findFirst().orElse(null);
        if (nether != null) {
            PrecipitationType inNether = service.precipitationAt(nether, c.low(), true);
            check(inNether == PrecipitationType.NONE, "in the Nether: " + inNether);
            log.add("nether " + nether.getName() + ": NONE");
        } else {
            log.add("no Nether world loaded in this run: Nether check not run");
        }
        return String.join("; ", log);
    }

    /** Loads the column's chunk and reads its two cells on the region thread. */
    private static Column loadColumn(Scheduler s, World world, int x, int z) throws Exception {
        OnThread.await(world.getChunkAtAsync(x >> 4, z >> 4), STEP, "loading the chunk at " + x + "," + z);
        return OnThread.region(s, world, x >> 4, z >> 4, STEP, () -> {
            int top = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING);
            return new Column(x, z, world.getMaxHeight() - 2, top + 1);
        });
    }

    /**
     * The first calendar time from {@code from} where {@code pos} gets {@code type} with a margin over
     * {@link #HOLD_TICKS}, in a local winter (SNOW) or summer (RAIN) month.
     */
    private static OptionalLong find(OverworldClimate climate, ChunkClimate chunk, BlockPos pos, long from,
            int daysInMonth, PrecipitationType type) {
        boolean north = Solar.inNorthernHemisphere(pos.z(), climate.hemisphereScale());
        for (long t = from; t < from + SEARCH_TICKS; t += SEARCH_STEP) {
            Month month = CalendarPhase.of(t, daysInMonth).month();
            float local = north ? month.temperatureModifier() : -month.temperatureModifier();
            if (type == PrecipitationType.SNOW ? local > -0.8f : local < 0.8f) { // winter: DEC to FEB; summer: JUN to AUG
                continue;
            }
            if (holds(climate, chunk, pos, t, daysInMonth, type)) {
                return OptionalLong.of(t);
            }
        }
        return OptionalLong.empty();
    }

    private static boolean holds(OverworldClimate climate, ChunkClimate chunk, BlockPos pos, long t, int daysInMonth,
            PrecipitationType type) {
        for (long u = t; u <= t + HOLD_TICKS; u += HOLD_TICKS / 4) {
            float intensity = climate.rainIntensity(u);
            float temperature = climate.instantTemperature(pos, chunk, u, daysInMonth);
            float rainfall = climate.instantRainfall(chunk, u, daysInMonth);
            if (PrecipitationType.decide(temperature, rainfall, intensity, true) != type) {
                return false;
            }
            if (PrecipitationType.decide(temperature, rainfall, intensity - INTENSITY_MARGIN, true) != type) {
                return false;
            }
            if (type == PrecipitationType.SNOW ? temperature > -TEMPERATURE_MARGIN : temperature < TEMPERATURE_MARGIN) {
                return false;
            }
        }
        return true;
    }

    /** Moves the calendar forward to {@code target} on the global thread. */
    private static void moveTo(TfpPlugin tfp, Scheduler s, long target) throws Exception {
        long now = tfp.clock().get().calendarTicks();
        check(target > now, "the calendar already passed " + target + " (now " + now + ")");
        long delta = target - now;
        check(delta <= Integer.MAX_VALUE, "skip of " + delta + " ticks is too long");
        CalendarService.Skipped skipped = OnThread.global(s, STEP,
                () -> tfp.calendar().skip(TimeCommand.add(TimeCommand.Unit.TICKS, (int) delta)));
        check(skipped != null, "the calendar is not loaded");
        long after = tfp.clock().get().calendarTicks();
        check(after >= target && after <= target + HOLD_TICKS,
                "the calendar is at " + after + " after the skip to " + target);
    }

    private static String monthOf(long t, int daysInMonth) {
        return CalendarPhase.of(t, daysInMonth).month().name();
    }

    private static String describe(OverworldClimate climate, long seed, BlockPos pos, long t, int daysInMonth) {
        ChunkClimate chunk = ChunkClimateSource.climateAt(seed, pos.x(), pos.z());
        return "at " + pos + " calendar " + t + ": temperature " + climate.instantTemperature(pos, chunk, t, daysInMonth)
                + ", rainfall " + climate.instantRainfall(chunk, t, daysInMonth) + ", rain intensity "
                + climate.rainIntensity(t);
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
