/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.calendar;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.calendar.CalendarCodec;
import dev.tfp.core.calendar.CalendarState;
import dev.tfp.core.calendar.SleepSkip;
import dev.tfp.core.clock.GlobalClockHolder;
import dev.tfp.paper.i18n.Messages;
import dev.tfp.paper.scheduler.Scheduler;
import dev.tfp.paper.testing.MockServerTest;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.bukkit.World;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.event.world.TimeSkipEvent;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.world.WorldMock;

/**
 * PAR-CLIM-22: the skip reason is read on both API families. The 1.21.11 {@link TimeSkipEvent} is the real class; the
 * 26.x shape (a {@code getSkipReason()} returning another enum with the same constant names, on a world-less
 * {@code ClockTimeSkipEvent}) is a stand-in, since that API is not on the test class path.
 */
class TimeSkipReasonsTest extends MockServerTest {
    /** June 1, 1000 (day 40), 21:00. */
    private static final long NINE_PM = 40L * 24_000 + 21_000;

    /** The 26.x {@code ClockTimeSkipEvent} shape: its own reason enum, no world. */
    public static final class ClockSkipStandIn extends Event implements Cancellable {
        private static final HandlerList HANDLERS = new HandlerList();

        /** The 26.x {@code ClockTimeSkipEvent$SkipReason} constants. */
        public enum SkipReason { COMMAND, CUSTOM, NIGHT_SKIP }

        private final SkipReason reason;
        private long amount;
        private boolean cancelled;

        public ClockSkipStandIn(SkipReason reason, long amount) {
            this.reason = reason;
            this.amount = amount;
        }

        public SkipReason getSkipReason() {
            return reason;
        }

        public long getSkipAmount() {
            return amount;
        }

        public void setSkipAmount(long amount) {
            this.amount = amount;
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }

        @Override
        public void setCancelled(boolean cancel) {
            cancelled = cancel;
        }

        @Override
        public HandlerList getHandlers() {
            return HANDLERS;
        }

        public static HandlerList getHandlerList() {
            return HANDLERS;
        }
    }

    /** A reason enum with a constant neither API has. */
    public static final class OddReasonEvent extends Event {
        public enum SkipReason { SOMETHING_NEW }

        public SkipReason getSkipReason() {
            return SkipReason.SOMETHING_NEW;
        }

        @Override
        public HandlerList getHandlers() {
            return ClockSkipStandIn.HANDLERS;
        }
    }

    /** An event without {@code getSkipReason()}. */
    public static final class NoReasonEvent extends Event {
        @Override
        public HandlerList getHandlers() {
            return ClockSkipStandIn.HANDLERS;
        }
    }

    @Test
    void the12111TimeSkipEventMapsEachReason() {
        WorldMock world = server.addSimpleWorld("world");
        assertThat(TimeSkipReasons.of(new TimeSkipEvent(world, TimeSkipEvent.SkipReason.COMMAND, 1)))
                .isEqualTo(TimeSkipReasons.Reason.COMMAND);
        assertThat(TimeSkipReasons.of(new TimeSkipEvent(world, TimeSkipEvent.SkipReason.CUSTOM, 1)))
                .isEqualTo(TimeSkipReasons.Reason.CUSTOM);
        assertThat(TimeSkipReasons.of(new TimeSkipEvent(world, TimeSkipEvent.SkipReason.NIGHT_SKIP, 1)))
                .isEqualTo(TimeSkipReasons.Reason.NIGHT_SKIP);
        assertThat(TimeSkipReasons.Reason.values()).extracting(Enum::name).containsAll(
                List.of(TimeSkipEvent.SkipReason.values()).stream().map(Enum::name).toList());
    }

    @Test
    void anotherEnumWithTheSameNamesMapsTheSameWayAsOn26x() {
        assertThat(TimeSkipReasons.of(new ClockSkipStandIn(ClockSkipStandIn.SkipReason.COMMAND, 1)))
                .isEqualTo(TimeSkipReasons.Reason.COMMAND);
        assertThat(TimeSkipReasons.of(new ClockSkipStandIn(ClockSkipStandIn.SkipReason.CUSTOM, 1)))
                .isEqualTo(TimeSkipReasons.Reason.CUSTOM);
        assertThat(TimeSkipReasons.of(new ClockSkipStandIn(ClockSkipStandIn.SkipReason.NIGHT_SKIP, 1)))
                .isEqualTo(TimeSkipReasons.Reason.NIGHT_SKIP);
    }

    @Test
    void missingOrUnknownReasonIsUnknownWithoutThrowing() {
        assertThat(TimeSkipReasons.of(new NoReasonEvent())).isEqualTo(TimeSkipReasons.Reason.UNKNOWN);
        assertThat(TimeSkipReasons.of(new OddReasonEvent())).isEqualTo(TimeSkipReasons.Reason.UNKNOWN);
        assertThat(TimeSkipReasons.of(null)).isEqualTo(TimeSkipReasons.Reason.UNKNOWN);
        assertThat(TimeSkipReasons.hasAmount(new NoReasonEvent())).isFalse();
    }

    @Test
    void amountIsReadAndWrittenOnBothShapes() {
        ClockSkipStandIn clock = new ClockSkipStandIn(ClockSkipStandIn.SkipReason.COMMAND, 7);
        assertThat(TimeSkipReasons.hasAmount(clock)).isTrue();
        assertThat(TimeSkipReasons.amount(clock)).isEqualTo(7);
        TimeSkipReasons.setAmount(clock, -3);
        assertThat(clock.getSkipAmount()).isEqualTo(-3);
        TimeSkipEvent world = new TimeSkipEvent(server.addSimpleWorld("w"), TimeSkipEvent.SkipReason.COMMAND, 5);
        assertThat(TimeSkipReasons.amount(world)).isEqualTo(5);
    }

    // --- the module on the world-less clock event and on unknown reasons -------------------------------------------

    private record Wired(CalendarModule module, CalendarService service, WorldMock world, List<LogRecord> log) { }

    private Wired wire() {
        WorldMock nether = server.addSimpleWorld("world_nether");
        nether.setEnvironment(World.Environment.NETHER);
        WorldMock world = server.addSimpleWorld("world");
        Logger logger = Logger.getLogger("time-skip-test-" + System.nanoTime());
        logger.setUseParentHandlers(false);
        List<LogRecord> records = new java.util.concurrent.CopyOnWriteArrayList<>();
        logger.addHandler(new Handler() {
            @Override
            public void publish(LogRecord record) {
                records.add(record);
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        });
        GlobalClockHolder clock = new GlobalClockHolder();
        CalendarService service = new CalendarService(clock, () -> world, () -> true, logger);
        DaylightSync sync = new DaylightSync(() -> List.<World>of(world));
        Scheduler scheduler = (Scheduler) Proxy.newProxyInstance(Scheduler.class.getClassLoader(),
                new Class<?>[] {Scheduler.class}, (proxy, method, args) -> {
                    throw new UnsupportedOperationException(method.getName());
                });
        CalendarModule module = new CalendarModule(service, sync, clock, scheduler, Messages.fromJar(), logger,
                () -> true);
        CalendarState s = new CalendarState(100_000L, NINE_PM, 0, 24, 8, false);
        world.getPersistentDataContainer().set(CalendarService.KEY, PersistentDataType.BYTE_ARRAY,
                CalendarCodec.CODEC.toBytes(s));
        service.tick();
        return new Wired(module, service, world, records);
    }

    @Test
    void worldlessClockSkipMovesTheCalendarAgainstTheFirstOverworld() {
        Wired w = wire();
        ClockSkipStandIn add = new ClockSkipStandIn(ClockSkipStandIn.SkipReason.COMMAND, 1_000);
        w.module().onClockTimeSkip(add);
        assertThat(w.service().state().calendarTicks()).isEqualTo(NINE_PM + 1_000);
        assertThat(w.world().getFullTime() + add.getSkipAmount())
                .isEqualTo(SleepSkip.vanillaDayTime(w.service().state().calendarTicks()));

        ClockSkipStandIn night = new ClockSkipStandIn(ClockSkipStandIn.SkipReason.NIGHT_SKIP, 9_000);
        long before = w.service().state().calendarTicks();
        w.module().onClockTimeSkip(night);
        assertThat(w.service().state().calendarTicks())
                .isEqualTo(before + SleepSkip.calendarTicksSlept(before));
    }

    @Test
    void worldlessCustomOrCancelledSkipsAreLeftAlone() {
        Wired w = wire();
        ClockSkipStandIn custom = new ClockSkipStandIn(ClockSkipStandIn.SkipReason.CUSTOM, 5_000);
        w.module().onClockTimeSkip(custom);
        ClockSkipStandIn cancelled = new ClockSkipStandIn(ClockSkipStandIn.SkipReason.COMMAND, 5_000);
        cancelled.setCancelled(true);
        w.module().onClockTimeSkip(cancelled);
        assertThat(w.service().state().calendarTicks()).isEqualTo(NINE_PM);
        assertThat(custom.getSkipAmount()).isEqualTo(5_000);
        assertThat(cancelled.getSkipAmount()).isEqualTo(5_000);
    }

    @Test
    void unknownReasonIsLeftAloneAndLoggedOnce() {
        Wired w = wire();
        OddReasonEvent odd = new OddReasonEvent();
        // OddReasonEvent has no amount: the module skips it before reading the reason; use the stand-in shape instead
        w.module().onClockTimeSkip(odd);
        assertThat(w.log()).isEmpty();

        UnknownClockSkip a = new UnknownClockSkip(1_000);
        UnknownClockSkip b = new UnknownClockSkip(2_000);
        w.module().onClockTimeSkip(a);
        w.module().onClockTimeSkip(b);
        assertThat(w.service().state().calendarTicks()).isEqualTo(NINE_PM);
        assertThat(a.getSkipAmount()).isEqualTo(1_000);
        assertThat(w.log()).filteredOn(r -> r.getMessage().contains("unknown reason")).hasSize(1);
    }

    /** A clock-shaped event whose reason is a constant neither API has. */
    public static final class UnknownClockSkip extends Event {
        public enum SkipReason { SOMETHING_NEW }

        private long amount;

        public UnknownClockSkip(long amount) {
            this.amount = amount;
        }

        public SkipReason getSkipReason() {
            return SkipReason.SOMETHING_NEW;
        }

        public long getSkipAmount() {
            return amount;
        }

        public void setSkipAmount(long amount) {
            this.amount = amount;
        }

        @Override
        public HandlerList getHandlers() {
            return ClockSkipStandIn.HANDLERS;
        }
    }
}
