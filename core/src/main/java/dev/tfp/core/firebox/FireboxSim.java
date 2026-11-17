/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/FireboxBlockEntity.java @ c3b4d9536 (serverTick, consumeFuel,
 *       extinguish, light, intakeAir, getTotalHeatableBlocks, getTimeToHeat, getTimeLeft, isHeating,
 *       isHeatingTimerRunning, cascadeFuelSlots, SLOTS, getSlotStackLimit),
 *       src/main/java/net/dries007/tfc/common/blocks/FireboxBlock.java @ c3b4d9536 (getAnalogOutputSignal),
 *       src/main/java/net/dries007/tfc/common/component/heat/HeatCapability.java @ c3b4d9536 (adjustDeviceTemp,
 *       provideHeatTo)
 */
package dev.tfp.core.firebox;

import dev.tfp.core.calendar.CalendarTicks;
import dev.tfp.core.cooking.FirepitRules;
import dev.tfp.core.cooking.FirepitState;
import dev.tfp.core.fuel.Fuel;
import dev.tfp.core.heat.HeatLevel;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * PAR-HEAT-12: the firebox's {@code serverTick} as a lazy catch-up. {@link #advance} replays TFC's live tick from the last
 * update to now: the burn and fuel consumption while lit (twice as fast with bellows air), the air, the device
 * temperature ({@link FirepitRules#adjustDeviceTemp}, never rained on: TFC passes {@code false}), the heat handed to the
 * block above ({@code HeatCapability.provideHeatTo}, here a {@link HeatSink}) and the kiln heating timer.
 *
 * <p>Unlike the firepit, TFC's firebox goes cold at once when its fuel runs out ({@code extinguish} sets the temperature
 * to 0).
 *
 * <p><b>Cost.</b> While the temperature has settled on the fuel's temperature and no air is left, nothing but the burn
 * counter moves, so the replay jumps to the tick the next fuel item is taken. A replay costs a few steps per fuel item
 * plus the warm-up (up to the fuel temperature, one degree per tick), whatever the gap.
 *
 * <p><b>Fuel queue.</b> TFC takes slot 0 and cascades the other slots down, so fuel is taken in the order of the non-empty
 * slots. The queue holds one entry per non-empty slot ({@link Optional#empty()} for a non-fuel item, which TFC removes
 * without burn time).
 *
 * <p>Pure and thread-safe.
 */
public final class FireboxSim {
    /** TFC {@code FireboxBlockEntity.SLOTS}. */
    public static final int SLOTS = 16;
    /** TFC {@code getSlotStackLimit}: one item per slot. */
    public static final int SLOT_LIMIT = 1;
    /** TFC {@code BellowsBlockEntity.MAX_DEVICE_AIR_TICKS}. */
    public static final int MAX_AIR_TICKS = FirepitState.MAX_AIR_TICKS;
    /** TFC {@code getTotalHeatableBlocks}: 64 blocks, 128 from bright red. */
    public static final int HEATABLE_BLOCKS = 64;
    public static final int HEATABLE_BLOCKS_BRIGHT_RED = 128;
    /** TFC {@code isHeating}: more than this many flooded blocks. */
    public static final int MIN_HEATING_BLOCKS = 4;

    private FireboxSim() {
    }

    /**
     * TFC {@code HeatCapability.provideHeatTo(level, pos.above(), DOWN, temperature)}: every tick in {@code fromTick ..
     * toTick} (inclusive) the firebox gave {@code temperature} to the block above. Constant runs come as one call.
     */
    @FunctionalInterface
    public interface HeatSink {
        HeatSink NONE = (from, to, temperature) -> {
        };

        void heat(long fromTick, long toTick, float temperature);
    }

    /**
     * The state after a span.
     *
     * @param data     the firebox after the span
     * @param consumed how many queue entries were taken (TFC empties those slots, then cascades)
     */
    public record Result(FireboxData data, int consumed) {
    }

    /**
     * The firebox after the ticks {@code fromTick + 1 .. toTick}, as TFC's {@code serverTick} would leave it.
     *
     * @param queue  the fuel queue (see the class comment)
     * @param config the device heating modifier (TFC {@code deviceHeatingModifier})
     * @param sink   receives the heat given to the block above
     */
    public static Result advance(FireboxData data, long fromTick, long toTick, List<Optional<Fuel>> queue,
            FirepitRules.DeviceConfig config, HeatSink sink) {
        Objects.requireNonNull(data, "data");
        Objects.requireNonNull(queue, "queue");
        Objects.requireNonNull(sink, "sink");
        Mut m = new Mut(data, queue);
        float delta = (float) config.heatingModifier();
        long t = fromTick;
        while (t < toTick) {
            if (!m.lit && m.burnTemperature == 0f && m.temperature == 0f) {
                // cold and unlit: only the air runs down; the timer is not running (temperature 0)
                long k = Math.min(m.air, toTick - t);
                m.air -= (int) k;
                m.timestamp = toTick;
                t = toTick;
                break;
            }
            if (m.lit && m.air == 0 && m.burn > 1 && m.temperature == m.burnTemperature) {
                // settled: only the burn counter moves until the tick the next item is taken
                long k = Math.min(m.burn - 1L, toTick - t);
                m.burn -= (int) k;
                if (m.temperature > 0f || m.burnTemperature > 0f) {
                    sink.heat(t + 1, t + k, m.temperature);
                }
                t += k;
                if (!m.timerRunning()) {
                    m.timestamp = t;
                }
                continue;
            }
            t++;
            m.tick(t, delta, sink);
        }
        return new Result(m.data(), m.used);
    }

    /**
     * TFC {@code light}: a firebox with burn time left reports lit; otherwise it takes one fuel item and is lit when that
     * gives burn time.
     *
     * @return the firebox and how many queue entries were taken (0 or 1)
     */
    public static Result light(FireboxData data, List<Optional<Fuel>> queue) {
        if (data.burnTicks() > 0) {
            return new Result(data, 0);
        }
        Mut m = new Mut(data, queue);
        if (m.consumeFuel()) {
            m.lit = true;
        }
        return new Result(m.data(), m.used);
    }

    /** TFC {@code extinguish}: unlit, burn, burn temperature and temperature drop to 0 (the air stays). */
    public static FireboxData extinguish(FireboxData data) {
        return new FireboxData(0, 0f, 0f, data.airTicks(), false, data.heatingCount(), data.heatingTimestamp());
    }

    /** TFC {@code intakeAir}: air ticks add up to {@link #MAX_AIR_TICKS}. */
    public static FireboxData intakeAir(FireboxData data, int amount) {
        int air = (int) Math.min(MAX_AIR_TICKS, Math.max(0L, (long) data.airTicks() + amount));
        return new FireboxData(data.burnTicks(), data.burnTemperature(), data.temperature(), air, data.lit(),
            data.heatingCount(), data.heatingTimestamp());
    }

    /**
     * The ticks until a lit firebox goes out with the given fuel (the tick of {@code extinguish}), or -1 when it is not lit.
     * Used to schedule the visual change while the chunk is loaded.
     */
    public static long ticksUntilOut(FireboxData data, List<Optional<Fuel>> queue) {
        if (!data.lit()) {
            return -1;
        }
        Mut m = new Mut(data, queue);
        long ticks = 0;
        while (m.lit) {
            if (m.air == 0 && m.burn > 1) {
                ticks += m.burn - 1L;
                m.burn = 1;
            }
            ticks++;
            if (m.burn > 0) {
                m.burn -= m.air > 0 ? 2 : 1;
            }
            if (m.burn <= 0 && !m.consumeFuel()) {
                m.lit = false;
            }
            if (m.air > 0) {
                m.air--;
            }
        }
        return ticks;
    }

    /** TFC {@code getTotalHeatableBlocks}: how many blocks the kiln may flood at this temperature. */
    public static int totalHeatableBlocks(float temperature) {
        return temperature >= HeatLevel.BRIGHT_RED.min() ? HEATABLE_BLOCKS_BRIGHT_RED : HEATABLE_BLOCKS;
    }

    /**
     * TFC {@code getTimeToHeat}: three hours, plus up to a day for a full kiln, minus up to six hours from 700 C up to
     * the hottest visible heat, never under three hours.
     */
    public static int timeToHeat(int heatingCount, float temperature) {
        int maxBlocks = totalHeatableBlocks(temperature);
        int hour = CalendarTicks.CALENDAR_TICKS_IN_HOUR;
        return (int) Math.max(3 * hour, (3 * hour) + clampedMap(heatingCount, 0, maxBlocks, 0, hour * 24)
            - clampedMap(temperature, 700, HeatLevel.MAX_VISIBLE, 0, hour * 6));
    }

    /** TFC {@code getTimeLeft}: ticks until the kiln is heated, -1 when the timer never started. */
    public static long timeLeft(FireboxData data, long now) {
        if (data.heatingTimestamp() == 0) {
            return -1;
        }
        return timeToHeat(data.heatingCount(), data.temperature()) - (now - data.heatingTimestamp());
    }

    /** TFC {@code isHeating}: the kiln holds more than four blocks. */
    public static boolean isHeating(FireboxData data) {
        return data.heatingCount() > MIN_HEATING_BLOCKS;
    }

    /** TFC {@code isHeatingTimerRunning}. */
    public static boolean timerRunning(float temperature, float burnTemperature, int heatingCount) {
        return temperature > 0 && heatingCount >= MIN_HEATING_BLOCKS
            && Math.abs(temperature - burnTemperature) <= MAX_AIR_TICKS + 1;
    }

    /**
     * TFC {@code FireboxBlock.getAnalogOutputSignal}: 0 without fuel in slot 0, otherwise the first empty slot scaled to
     * 1..15, or 15 when every slot is full.
     *
     * @param filled whether each slot holds an item, in slot order
     */
    public static int analogOutput(List<Boolean> filled) {
        if (filled.isEmpty() || !filled.getFirst()) {
            return 0;
        }
        for (int i = 0; i < SLOTS && i < filled.size(); i++) {
            if (!filled.get(i)) {
                return Math.clamp(i * 15 / SLOTS, 1, 15);
            }
        }
        return 15;
    }

    /** Minecraft {@code Mth.clampedMap}: {@code value} mapped from [a, b] to [c, d], clamped to the ends. */
    static double clampedMap(double value, double fromMin, double fromMax, double toMin, double toMax) {
        double pct = (value - fromMin) / (fromMax - fromMin);
        if (pct < 0) {
            return toMin;
        }
        if (pct > 1) {
            return toMax;
        }
        return toMin + pct * (toMax - toMin);
    }

    /** The mutable working copy of one replay. */
    private static final class Mut {
        final List<Optional<Fuel>> queue;
        int burn;
        float burnTemperature;
        float temperature;
        int air;
        boolean lit;
        final int heatingCount;
        long timestamp;
        int used;

        Mut(FireboxData d, List<Optional<Fuel>> queue) {
            this.queue = queue;
            burn = d.burnTicks();
            burnTemperature = d.burnTemperature();
            temperature = d.temperature();
            air = d.airTicks();
            lit = d.lit();
            heatingCount = d.heatingCount();
            timestamp = d.heatingTimestamp();
        }

        /** One TFC {@code serverTick} with the calendar advancing (tick {@code t}). */
        void tick(long t, float delta, HeatSink sink) {
            if (lit) {
                if (burn > 0) {
                    burn -= air > 0 ? 2 : 1; // fuel burns twice as fast using bellows
                }
                if (burn <= 0 && !consumeFuel()) {
                    extinguish();
                }
            } else if (burnTemperature > 0) {
                extinguish();
            }
            if (air > 0) {
                air--;
            }
            if (temperature > 0 || burnTemperature > 0) {
                temperature = FirepitRules.adjustDeviceTemp(temperature, burnTemperature, air, false, delta);
                sink.heat(t, t, temperature);
            }
            if (!timerRunning()) {
                timestamp = t;
            }
        }

        boolean timerRunning() {
            return FireboxSim.timerRunning(temperature, burnTemperature, heatingCount);
        }

        /** TFC {@code consumeFuel}: empties slot 0; a fuel adds its burn time and sets the burn temperature. */
        boolean consumeFuel() {
            if (used < queue.size()) {
                Optional<Fuel> next = queue.get(used++);
                if (next.isPresent()) {
                    burn += next.get().duration();
                    burnTemperature = next.get().temperature();
                }
            }
            return burn > 0;
        }

        void extinguish() {
            lit = false;
            burn = 0;
            burnTemperature = 0;
            temperature = 0;
        }

        FireboxData data() {
            return new FireboxData(Math.max(0, burn), burnTemperature, temperature, air, lit, heatingCount, timestamp);
        }
    }
}
