/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.itest.cases;

import dev.tfp.core.calendar.CalendarCodec;
import dev.tfp.core.calendar.CalendarState;
import dev.tfp.core.clock.ClockSnapshot;
import dev.tfp.core.clock.GlobalClockHolder;
import dev.tfp.itest.SelfTestCase;
import dev.tfp.itest.SelfTestContext;
import dev.tfp.paper.TfpPlugin;
import dev.tfp.paper.calendar.CalendarService;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import org.bukkit.World;
import org.bukkit.persistence.PersistentDataType;

/**
 * {@code calendar.persist} (M2-CAL-01): the global clock is published and advances on the global region
 * scheduler (forced to run for the check, since the self-test server has no players), and after a save the
 * first world's PDC holds {@code tfp:calendar}, decoding to a state at least as late as the published clock.
 */
public final class CalendarPersistCase implements SelfTestCase {
    @Override
    public String name() {
        return "calendar.persist: global clock advances and is saved to the world PDC (M2-CAL-01)";
    }

    @Override
    public Duration timeout() {
        return Duration.ofSeconds(30);
    }

    @Override
    public CompletableFuture<String> run(SelfTestContext ctx) {
        TfpPlugin tfp = ctx.tfp();
        return OnThread.script("calendar", () -> check(tfp));
    }

    private static String check(TfpPlugin tfp) throws Exception {
        CalendarService cal = tfp.calendar();
        if (cal == null) {
            throw new AssertionError("no calendar service");
        }
        GlobalClockHolder clock = tfp.clock();
        if (!OnThread.poll(Duration.ofSeconds(10), () -> clock.get().isSet())) {
            throw new AssertionError("the clock was never published");
        }
        ClockSnapshot before;
        ClockSnapshot after;
        boolean previous = cal.setRunWhenEmpty(true);
        try {
            before = clock.get();
            Thread.sleep(3_000);
            after = clock.get();
        } finally {
            cal.setRunWhenEmpty(previous);
        }
        if (after.sequence() <= before.sequence()) {
            throw new AssertionError("no publication in 3 s: " + before + " -> " + after);
        }
        if (after.playerTicks() <= before.playerTicks() || after.calendarTicks() <= before.calendarTicks()) {
            throw new AssertionError("the clock did not advance in 3 s: " + before + " -> " + after);
        }
        String saved = OnThread.global(tfp.scheduler(), Duration.ofSeconds(10), () -> {
            if (!cal.save()) {
                throw new AssertionError("save() wrote nothing (not loaded, or newer data in the world)");
            }
            World w = cal.world();
            byte[] bytes = w.getPersistentDataContainer().get(CalendarService.KEY, PersistentDataType.BYTE_ARRAY);
            if (bytes == null) {
                throw new AssertionError("world " + w.getName() + " has no " + CalendarService.KEY + " after save");
            }
            CalendarState s = CalendarCodec.CODEC.fromBytes(bytes);
            if (s.calendarTicks() < after.calendarTicks()) {
                throw new AssertionError("saved calendar " + s + " is older than the published " + after);
            }
            return w.getName() + " " + CalendarService.KEY + " = " + s.calendarTicks() + " calendar ticks";
        });
        return "advanced " + (after.calendarTicks() - before.calendarTicks()) + " calendar / "
                + (after.playerTicks() - before.playerTicks()) + " player ticks in 3 s; " + saved;
    }
}
