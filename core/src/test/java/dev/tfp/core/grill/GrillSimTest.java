/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.grill;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.tfp.core.cooking.FirepitRules;
import dev.tfp.core.cooking.FirepitState;
import dev.tfp.core.cooking.GrillRules;
import dev.tfp.core.firepit.FirepitData;
import dev.tfp.core.firepit.FirepitSim;
import dev.tfp.core.fuel.Fuel;
import dev.tfp.core.fuel.FuelTable;
import dev.tfp.core.heat.HeatConfig;
import dev.tfp.core.heat.HeatState;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.random.RandomGenerator;
import org.junit.jupiter.api.Test;

/**
 * PAR-HEAT-08: the grill replay against TFC's {@code serverTick} + {@code GrillBlockEntity.handleCooking} (addTemp on every
 * grill slot, then the recipe test on the heated temperature), with TFC's data (oak logs 2250 ticks at 728 C, pine logs
 * 1250 at 627 C, meat heat capacity 1, the cooked meat heating recipes at 200 C).
 */
class GrillSimTest {
    static final Fuel OAK = FuelTable.byId("oak_logs").orElseThrow();
    static final Fuel PINE = FuelTable.byId("pine_logs").orElseThrow();
    static final FirepitRules.DeviceConfig DEVICE = FirepitRules.DeviceConfig.DEFAULT;
    static final HeatConfig HEAT = HeatConfig.DEFAULT;
    static final RandomGenerator ASH = () -> 0L;

    static List<Optional<Fuel>> queue(Fuel... fuels) {
        List<Optional<Fuel>> out = new ArrayList<>();
        for (Fuel f : fuels) {
            out.add(Optional.ofNullable(f));
        }
        return out;
    }

    static List<GrillSim.Item> items(GrillSim.Item... five) {
        return Arrays.asList(five);
    }

    static GrillSim.Item beef(long tick) {
        return new GrillSim.Item(1f, HeatState.cold(tick), 200f);
    }

    static GrillSim.Item cookedMeat(long tick) {
        return new GrillSim.Item(1f, HeatState.cold(tick), Float.NaN);
    }

    @Test
    void anEmptyGrillIsTheFirepit() {
        List<Optional<Fuel>> fuels = queue(OAK, PINE, OAK);
        FirepitSim.Result lit = FirepitSim.light(FirepitData.FRESH, fuels, ASH);
        List<Optional<Fuel>> rest = fuels.subList(lit.consumed(), fuels.size());
        for (boolean rain : new boolean[] {false, true}) {
            FirepitData start = FirepitSim.intakeAir(lit.data().withRaining(rain), 200);
            GrillSim.Result grill = GrillSim.advance(start, 0, 9000, rest, items(null, null, null, null, null),
                new Random(7), DEVICE, HEAT);
            FirepitSim.Result firepit = FirepitSim.advance(start, 0, 9000, rest, null, new Random(7), DEVICE, HEAT);
            assertThat(grill.data()).as("rain " + rain).isEqualTo(firepit.data());
            assertThat(grill.consumed()).isEqualTo(firepit.consumed());
            assertThat(grill.tick()).isEqualTo(9000);
            assertThat(grill.anyConverted()).isFalse();
        }
    }

    @Test
    void heatingItemsDoesNotChangeTheFirepitAndSplitWindowsEqualOne() {
        List<Optional<Fuel>> fuels = queue(PINE, OAK, PINE);
        FirepitSim.Result lit = FirepitSim.light(FirepitData.FRESH, fuels, ASH);
        List<Optional<Fuel>> rest = fuels.subList(lit.consumed(), fuels.size());
        List<GrillSim.Item> meat = items(cookedMeat(0), null, new GrillSim.Item(2.5f, HeatState.cold(0), Float.NaN), null,
            new GrillSim.Item(0f, HeatState.cold(0), Float.NaN));
        GrillSim.Result once = GrillSim.advance(lit.data(), 0, 6000, rest, meat, new Random(3), DEVICE, HEAT);
        assertThat(once.data()).isEqualTo(FirepitSim.advance(lit.data(), 0, 6000, rest, null, new Random(3), DEVICE, HEAT).data());
        assertThat(once.heats().get(4)).isNull(); // capacity 0: not heatable
        assertThat(once.heats().get(1)).isNull();

        Random split = new Random(11);
        Random ash = new Random(3);
        FirepitData d = lit.data();
        List<GrillSim.Item> now = new ArrayList<>(meat);
        int used = 0;
        long t = 0;
        while (t < 6000) {
            long to = Math.min(6000, t + 1 + split.nextInt(700));
            GrillSim.Result r = GrillSim.advance(d, t, to, rest.subList(used, rest.size()), now, ash, DEVICE, HEAT);
            assertThat(r.anyConverted()).isFalse();
            d = r.data();
            used += r.consumed();
            for (int i = 0; i < GrillSim.GRILL_SLOTS; i++) {
                if (r.heats().get(i) != null) {
                    now.set(i, new GrillSim.Item(now.get(i).capacity(), r.heats().get(i), Float.NaN));
                }
            }
            t = to;
        }
        assertThat(d).isEqualTo(once.data());
        assertThat(used).isEqualTo(once.consumed());
        for (int i = 0; i < GrillSim.GRILL_SLOTS; i++) {
            HeatState a = once.heats().get(i);
            HeatState b = now.get(i) == null || now.get(i).capacity() <= 0 ? null : now.get(i).heat();
            if (a == null) {
                assertThat(b).isNull();
            } else {
                assertThat(b.temperatureAt(6000, now.get(i).capacity(), HEAT))
                    .as("slot " + (i + 4)).isEqualTo(a.temperatureAt(6000, now.get(i).capacity(), HEAT));
            }
        }
    }

    @Test
    void rawBeefCooksOnTheFirstTickItIsHotEnoughAfterHeating() {
        // oak firepit lit from cold at tick 0: the device climbs 1 C per tick, the beef (capacity 1) follows it,
        // capped at the device; TFC's grill tests the recipe after addTemp, so it converts at 200 C on tick 200
        FirepitSim.Result lit = FirepitSim.light(FirepitData.FRESH, queue(OAK), ASH);
        GrillSim.Result r = GrillSim.advance(lit.data(), 0, 5000, List.of(), items(beef(0), null, beef(0), null, null), ASH,
            DEVICE, HEAT);
        assertThat(r.converted()).containsExactly(0, 2);
        long tick = r.tick();
        assertThat(r.temperatureTested().get(0)).isGreaterThanOrEqualTo(200f);
        assertThat(r.temperatureTested().get(1)).isNull();
        // one tick earlier nothing has fired yet
        GrillSim.Result before = GrillSim.advance(lit.data(), 0, tick - 1, List.of(), items(beef(0), null, beef(0), null, null),
            ASH, DEVICE, HEAT);
        assertThat(before.anyConverted()).isFalse();
        assertThat(before.heats().get(0).temperatureAt(tick - 1, 1f, HEAT)).isLessThan(200f);
        assertThat(tick).isEqualTo(200);
        assertThat(r.data()).isEqualTo(FirepitSim.advance(lit.data(), 0, tick, List.of(), null, ASH, DEVICE, HEAT).data());
    }

    @Test
    void atASteadyDeviceTemperatureTheCookTickIsGrillRules() {
        // a firepit held at its burn temperature for the whole window
        FirepitData hot = new FirepitData(new FirepitState(100_000, 728f, 728f, 0), true, 0, 0f, 0, 2250, false);
        float[] capacities = {1f, 0.5f, 2.5f, 4f};
        float[] recipes = {200f, 480f, 700f, 728f};
        for (float c : capacities) {
            for (float recipe : recipes) {
                GrillSim.Result r = GrillSim.advance(hot, 0, 100_000, List.of(),
                    items(new GrillSim.Item(c, HeatState.cold(0), recipe), null, null, null, null), ASH, DEVICE, HEAT);
                assertThat(r.data().temperature()).isEqualTo(728f);
                long expected = GrillRules.ticksToCook(0f, c, 728f, recipe, HEAT);
                assertThat(Math.abs(r.tick() - expected)).as("capacity " + c + " recipe " + recipe).isLessThanOrEqualTo(1);
            }
        }
        // above the device temperature a recipe never fires
        GrillSim.Result never = GrillSim.advance(hot, 0, 20_000, List.of(),
            items(new GrillSim.Item(1f, HeatState.cold(0), 900f), null, null, null, null), ASH, DEVICE, HEAT);
        assertThat(never.anyConverted()).isFalse();
        assertThat(never.heats().get(0).temperature()).isEqualTo(728f);
    }

    @Test
    void aHotItemConvertsOnAColdGrillToo() {
        // TFC's grill runs handleCooking whatever the device temperature: addTemp(0) does nothing, the recipe still fires
        GrillSim.Result r = GrillSim.advance(FirepitData.FRESH, 100, 10_000, List.of(),
            items(null, new GrillSim.Item(1f, new HeatState(400f, 100), 200f), null, null, null), ASH, DEVICE, HEAT);
        assertThat(r.converted()).containsExactly(1);
        assertThat(r.tick()).isEqualTo(101);
        assertThat(r.temperatureTested().get(1)).isEqualTo(400f - 0.8f);
        // a cold grill with a cold item: the firepit's catch-up at once
        GrillSim.Result cold = GrillSim.advance(FirepitData.FRESH, 0, 1_000_000, List.of(), items(beef(0), null, null, null, null),
            ASH, DEVICE, HEAT);
        assertThat(cold.anyConverted()).isFalse();
        assertThat(cold.tick()).isEqualTo(1_000_000);
        assertThat(cold.heats().get(0)).isEqualTo(HeatState.cold(0));
    }

    @Test
    void theConvertedDeviceKeepsTfcsCopyFromFields() {
        FirepitData pit = new FirepitData(new FirepitState(1200, 728f, 512.5f, 33), true, 3, 0.7f, 6, 2250, true);
        FirepitData grill = GrillSim.converted(pit);
        assertThat(grill.heat()).isEqualTo(pit.heat());
        assertThat(grill.lit()).isTrue();
        assertThat(grill.smokeLevel()).isEqualTo(3);
        assertThat(grill.raining()).isTrue();
        assertThat(grill.ash()).isZero(); // the old block entity drops its ash
        assertThat(grill.dirtiness()).isZero();
        assertThat(grill.lastMaxBurnTicks()).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    void slotsAndTheirSize() {
        assertThat(GrillSim.SLOTS).isEqualTo(9);
        assertThat(GrillSim.GRILL_SLOTS).isEqualTo(5);
        for (int s = 0; s < GrillSim.SLOTS; s++) {
            assertThat(GrillSim.slotLimit(s)).isEqualTo(1);
            assertThat(GrillSim.isGrillSlot(s)).isEqualTo(s >= 4);
        }
        assertThatThrownBy(() -> GrillSim.advance(FirepitData.FRESH, 0, 1, List.of(), List.of(), ASH, DEVICE, HEAT))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
