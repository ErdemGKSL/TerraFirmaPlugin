/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.firebox;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.cooking.FirepitRules;
import dev.tfp.core.crucible.CrucibleTemperature;
import dev.tfp.core.fuel.Fuel;
import dev.tfp.core.persist.DataMap;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

/** PAR-HEAT-12: the firebox replay against TFC's live tick (golden), lazy against stepped, and the small rules. */
class FireboxSimTest {
    static final Fuel CHARCOAL = new Fuel("tfc:charcoal", 1800, 1350f, 1f);
    static final Fuel PEAT = new Fuel("tfc:peat", 2500, 600f, 1f);
    static final Fuel COAL = new Fuel("tfc:coal", 2000, 1415f, 1f);
    static final Map<Long, Integer> AIR = Map.of(500L, 200, 520L, 200, 3000L, 600, 5200L, 150);
    static final int HEATING_COUNT = 10;
    static final FirepitRules.DeviceConfig CONFIG = FirepitRules.DeviceConfig.DEFAULT;

    record Row(long tick, float temperature, int burnTicks, float burnTemperature, int airTicks, boolean lit, int consumed,
            long timestamp, long heatTicks, double heatSum) {
    }

    /** The replay's running state: the data, the fuel left and the heat given. */
    static final class Run implements FireboxSim.HeatSink {
        FireboxData data;
        List<Optional<Fuel>> queue = new ArrayList<>(List.of(Optional.of(CHARCOAL), Optional.of(PEAT), Optional.of(COAL)));
        int consumed;
        long heatTicks;
        double heatSum;
        long lastHeatTick = Long.MIN_VALUE;

        Run() {
            FireboxData fresh = new FireboxData(0, 0f, 0f, 0, false, HEATING_COUNT, 0L);
            FireboxSim.Result lit = FireboxSim.light(fresh, queue);
            take(lit.consumed());
            data = lit.data();
        }

        void take(int n) {
            consumed += n;
            queue = new ArrayList<>(queue.subList(n, queue.size()));
        }

        void advance(long from, long to) {
            FireboxSim.Result r = FireboxSim.advance(data, from, to, queue, CONFIG, this);
            take(r.consumed());
            data = r.data();
        }

        @Override
        public void heat(long fromTick, long toTick, float temperature) {
            assertThat(fromTick).isGreaterThan(lastHeatTick).isLessThanOrEqualTo(toTick);
            lastHeatTick = toTick;
            heatTicks += toTick - fromTick + 1;
            heatSum += (double) temperature * (toTick - fromTick + 1);
        }

        void check(Row r) {
            assertThat(data.temperature()).as("temperature at %d", r.tick()).isEqualTo(r.temperature());
            assertThat(data.burnTicks()).as("burn at %d", r.tick()).isEqualTo(r.burnTicks());
            assertThat(data.burnTemperature()).as("burn temperature at %d", r.tick()).isEqualTo(r.burnTemperature());
            assertThat(data.airTicks()).as("air at %d", r.tick()).isEqualTo(r.airTicks());
            assertThat(data.lit()).as("lit at %d", r.tick()).isEqualTo(r.lit());
            assertThat(consumed).as("consumed at %d", r.tick()).isEqualTo(r.consumed());
            assertThat(data.heatingTimestamp()).as("timer at %d", r.tick()).isEqualTo(r.timestamp());
            assertThat(heatTicks).as("heat ticks at %d", r.tick()).isEqualTo(r.heatTicks());
            assertThat(heatSum).as("heat sum at %d", r.tick()).isEqualTo(r.heatSum());
        }
    }

    static List<Row> golden() throws IOException {
        List<Row> rows = new ArrayList<>();
        try (InputStream in = FireboxSimTest.class.getResourceAsStream("firebox_golden.csv")) {
            assertThat(in).isNotNull();
            BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            while ((line = r.readLine()) != null) {
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }
                String[] f = line.split(",");
                rows.add(new Row(Long.parseLong(f[0]), Float.parseFloat(f[1]), Integer.parseInt(f[2]), Float.parseFloat(f[3]),
                    Integer.parseInt(f[4]), Boolean.parseBoolean(f[5]), Integer.parseInt(f[6]), Long.parseLong(f[7]),
                    Long.parseLong(f[8]), Double.parseDouble(f[9])));
            }
        }
        assertThat(rows).hasSizeGreaterThan(250);
        return rows;
    }

    @Test
    void steppedMatchesTfcGolden() throws IOException {
        Map<Long, Row> rows = new TreeMap<>();
        golden().forEach(r -> rows.put(r.tick(), r));
        Run run = new Run();
        long end = rows.keySet().stream().mapToLong(Long::longValue).max().orElseThrow();
        for (long t = 1; t <= end; t++) {
            run.advance(t - 1, t);
            Row r = rows.get(t);
            if (r != null) {
                run.check(r);
            }
            if (AIR.containsKey(t)) {
                run.data = FireboxSim.intakeAir(run.data, AIR.get(t));
            }
        }
    }

    @Test
    void lazyMatchesTfcGolden() throws IOException {
        Run run = new Run();
        long last = 0;
        for (Row r : golden()) {
            run.advance(last, r.tick());
            run.check(r);
            last = r.tick();
            if (AIR.containsKey(r.tick())) {
                run.data = FireboxSim.intakeAir(run.data, AIR.get(r.tick()));
            }
        }
    }

    @Test
    void oneLongGapEqualsStepping() {
        Run lazy = new Run();
        lazy.advance(0, 10_000);
        Run stepped = new Run();
        for (long t = 1; t <= 10_000; t++) {
            stepped.advance(t - 1, t);
        }
        assertThat(lazy.data).isEqualTo(stepped.data);
        assertThat(lazy.consumed).isEqualTo(stepped.consumed).isEqualTo(3);
        assertThat(lazy.heatSum).isEqualTo(stepped.heatSum);
        assertThat(lazy.heatTicks).isEqualTo(stepped.heatTicks);
        assertThat(lazy.data.lit()).isFalse();
        assertThat(lazy.data.temperature()).isZero(); // TFC: extinguish cools at once
    }

    @Test
    void ticksUntilOutIsTheExtinguishTick() {
        Run run = new Run();
        run.data = FireboxSim.intakeAir(run.data, 300);
        long predicted = FireboxSim.ticksUntilOut(run.data, run.queue);
        long t = 0;
        while (run.data.lit()) {
            run.advance(t, t + 1);
            t++;
        }
        assertThat(predicted).isEqualTo(t);
        assertThat(FireboxSim.ticksUntilOut(FireboxData.FRESH, List.of())).isEqualTo(-1);
    }

    @Test
    void lightTakesOneFuelAndKeepsBurningBoxes() {
        FireboxSim.Result r = FireboxSim.light(FireboxData.FRESH, List.of(Optional.empty(), Optional.of(PEAT)));
        assertThat(r.consumed()).isEqualTo(1);
        assertThat(r.data().lit()).isFalse(); // a non-fuel item gives no burn time
        FireboxSim.Result ok = FireboxSim.light(FireboxData.FRESH, List.of(Optional.of(PEAT)));
        assertThat(ok.data().lit()).isTrue();
        assertThat(ok.data().burnTicks()).isEqualTo(2500);
        assertThat(ok.data().burnTemperature()).isEqualTo(600f);
        FireboxSim.Result again = FireboxSim.light(ok.data(), List.of(Optional.of(COAL)));
        assertThat(again.consumed()).isZero();
        assertThat(again.data()).isEqualTo(ok.data());
    }

    @Test
    void extinguishAndAir() {
        FireboxData hot = new FireboxData(100, 1350f, 900f, 40, true, 7, 55L);
        FireboxData out = FireboxSim.extinguish(hot);
        assertThat(out).isEqualTo(new FireboxData(0, 0f, 0f, 40, false, 7, 55L));
        assertThat(FireboxSim.intakeAir(hot, 1000).airTicks()).isEqualTo(600);
        assertThat(FireboxSim.intakeAir(hot, 5).airTicks()).isEqualTo(45);
    }

    @Test
    void coldUnlitBoxOnlyLosesAir() {
        FireboxData box = new FireboxData(0, 0f, 0f, 30, false, 5, 0L);
        List<long[]> heat = new ArrayList<>();
        FireboxSim.Result r = FireboxSim.advance(box, 100, 120, List.of(Optional.of(COAL)), CONFIG,
            (a, b, t) -> heat.add(new long[] {a, b}));
        assertThat(r.data()).isEqualTo(new FireboxData(0, 0f, 0f, 10, false, 5, 120L));
        assertThat(r.consumed()).isZero();
        assertThat(heat).isEmpty();
    }

    @Test
    void timeToHeatAsTfc() {
        assertThat(FireboxSim.timeToHeat(0, 0f)).isEqualTo(3000);
        assertThat(FireboxSim.timeToHeat(64, 500f)).isEqualTo(27_000);
        assertThat(FireboxSim.timeToHeat(32, 1150f)).isEqualTo(3000 + 6000 - 3000); // bright red: 128 blocks
        assertThat(FireboxSim.timeToHeat(128, 1600f)).isEqualTo(3000 + 24_000 - 6000);
        assertThat(FireboxSim.timeToHeat(64, 1600f)).isEqualTo(3000 + 12_000 - 6000);
        assertThat(FireboxSim.totalHeatableBlocks(729.9f)).isEqualTo(64);
        assertThat(FireboxSim.totalHeatableBlocks(730f)).isEqualTo(128);
        FireboxData d = new FireboxData(10, 1350f, 1350f, 0, true, 20, 1000L);
        assertThat(FireboxSim.timeLeft(d, 1500)).isEqualTo(FireboxSim.timeToHeat(20, 1350f) - 500);
        assertThat(FireboxSim.timeLeft(FireboxData.FRESH, 1500)).isEqualTo(-1);
        assertThat(FireboxSim.isHeating(d)).isTrue();
        assertThat(FireboxSim.isHeating(d.withHeatingCount(4, 9))).isFalse();
        assertThat(d.withHeatingCount(20, 9)).isSameAs(d);
        assertThat(d.withHeatingCount(21, 9).heatingTimestamp()).isEqualTo(9);
    }

    @Test
    void analogOutputAsTfc() {
        List<Boolean> none = new ArrayList<>();
        for (int i = 0; i < 16; i++) {
            none.add(false);
        }
        assertThat(FireboxSim.analogOutput(none)).isZero();
        List<Boolean> one = new ArrayList<>(none);
        one.set(0, true);
        assertThat(FireboxSim.analogOutput(one)).isEqualTo(1); // clamp(1 * 15 / 16, 1, 15)
        List<Boolean> eight = new ArrayList<>(none);
        for (int i = 0; i < 8; i++) {
            eight.set(i, true);
        }
        assertThat(FireboxSim.analogOutput(eight)).isEqualTo(7);
        List<Boolean> all = new ArrayList<>(none);
        all.replaceAll(b -> true);
        assertThat(FireboxSim.analogOutput(all)).isEqualTo(15);
    }

    @Test
    void codecRoundTrip() {
        FireboxData d = new FireboxData(123, 1350.5f, 812.25f, 77, true, 31, 987_654_321L);
        DataMap map = FireboxData.CODEC.encode(d);
        assertThat(FireboxData.CODEC.decode(map)).isEqualTo(d);
    }

    /** The heat handed up drives a crucible above to the firebox's temperature (TFC IHeatConsumer.setTemperature). */
    @Test
    void heatTransferRaisesACrucibleAbove() {
        Run run = new Run();
        CrucibleTemperature[] crucible = {CrucibleTemperature.COLD};
        FireboxSim.HeatSink toCrucible = (from, to, temperature) -> {
            for (long t = from; t <= to; t++) {
                crucible[0] = crucible[0].setTemperature(temperature).tick(CONFIG);
            }
        };
        FireboxSim.Result r = FireboxSim.advance(run.data, 0, 1500, run.queue, CONFIG, toCrucible);
        assertThat(r.data().temperature()).isEqualTo(1350f);
        assertThat(crucible[0].temperature()).isGreaterThanOrEqualTo(1349f);
    }
}
