/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.firepit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.tfp.core.cooking.FirepitRules;
import dev.tfp.core.cooking.FirepitState;
import dev.tfp.core.fuel.Fuel;
import dev.tfp.core.fuel.FuelTable;
import dev.tfp.core.heat.HeatConfig;
import dev.tfp.core.heat.HeatState;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.random.RandomGenerator;
import org.junit.jupiter.api.Test;

/**
 * PAR-HEAT-06: the firepit replay against TFC's {@code serverTick}. Golden values are hand-traced from TFC's code at
 * c3b4d9536 (AbstractFirepitBlockEntity, FirepitBlockEntity.handleCooking, HeatCapability.addTemp) with TFC's data
 * (oak logs 2250 ticks at 728 C purity 1, pine logs 1250 at 627 C purity 0.6, item_heat foods/meat capacity 1, the
 * cooked_beef heating recipe at 200 C, itemCoolingModifier 0.8, itemHeatingModifier 1).
 */
class FirepitSimTest {
    static final Fuel OAK = FuelTable.byId("oak_logs").orElseThrow();
    static final Fuel PINE = FuelTable.byId("pine_logs").orElseThrow();
    static final FirepitRules.DeviceConfig DEVICE = FirepitRules.DeviceConfig.DEFAULT;
    static final HeatConfig HEAT = HeatConfig.DEFAULT;
    /** A random whose ash draw always lands (nextFloat 0 < 0.5). */
    static final RandomGenerator ASH = () -> 0L;

    static List<Optional<Fuel>> queue(Fuel... fuels) {
        List<Optional<Fuel>> out = new ArrayList<>();
        for (Fuel f : fuels) {
            out.add(Optional.ofNullable(f));
        }
        return out;
    }

    /** A firepit lit from cold on {@code fuels} at tick 0 (TFC StartFireEvent: logs in, then light). */
    static FirepitSim.Result litOn(List<Optional<Fuel>> fuels) {
        return FirepitSim.light(FirepitData.FRESH, fuels, ASH);
    }

    @Test
    void lightTakesOneFuelAndAddsAshAndDirtiness() {
        FirepitSim.Result r = litOn(queue(PINE, OAK));
        assertThat(r.consumed()).isEqualTo(1);
        assertThat(r.data().lit()).isTrue();
        assertThat(r.data().heat()).isEqualTo(new FirepitState(1250, 627f, 0f, 0));
        assertThat(r.data().dirtiness()).isEqualTo(1f - 0.6f);
        assertThat(r.data().lastMaxBurnTicks()).isEqualTo(1250);
        assertThat(r.data().ash()).isEqualTo(1);
        // a lit firepit stays lit and takes nothing
        assertThat(FirepitSim.light(r.data(), queue(OAK), ASH).consumed()).isZero();
    }

    @Test
    void lightingOnANonFuelItemRemovesItButStaysUnlit() {
        FirepitSim.Result r = FirepitSim.light(FirepitData.FRESH, queue((Fuel) null), () -> -1L);
        assertThat(r.consumed()).isEqualTo(1);
        assertThat(r.data().lit()).isFalse();
        assertThat(r.data().ash()).isZero(); // nextFloat ~1, no ash
        assertThat(FirepitSim.light(FirepitData.FRESH, queue(), ASH).data().lit()).isFalse();
    }

    @Test
    void lazyReplayEqualsTickByTick() {
        List<Optional<Fuel>> fuels = queue(PINE, OAK, PINE, OAK);
        FirepitSim.Result lit = litOn(fuels);
        List<Optional<Fuel>> rest = fuels.subList(lit.consumed(), fuels.size());
        FirepitSim.Cooking beef = new FirepitSim.Cooking(1f, HeatState.cold(0), 200f);
        for (boolean rain : new boolean[] {false, true}) {
            FirepitData start = FirepitSim.intakeAir(lit.data().withRaining(rain), 300);
            FirepitSim.Result once = FirepitSim.advance(start, 0, 9000, rest, beef, ASH, DEVICE, HEAT);

            FirepitData d = start;
            int used = 0;
            FirepitSim.Cooking cooking = beef;
            FirepitSim.Cooked cooked = null;
            for (long t = 0; t < 9000; t++) {
                FirepitSim.Result step = FirepitSim.advance(d, t, t + 1, rest.subList(used, rest.size()), cooking, ASH,
                    DEVICE, HEAT);
                d = step.data();
                used += step.consumed();
                if (step.cooked() != null && cooked == null) {
                    if (step.cooked().converted()) {
                        cooked = step.cooked();
                        cooking = null;
                    } else {
                        cooking = new FirepitSim.Cooking(1f, step.cooked().heat(), 200f);
                    }
                }
            }
            assertThat(once.data()).as("rain " + rain).isEqualTo(d);
            assertThat(once.consumed()).isEqualTo(used);
            assertThat(once.cooked()).isEqualTo(cooked);
        }
    }

    @Test
    void burnAndTemperatureMatchFirepitRulesOverTwoHours() {
        List<Optional<Fuel>> fuels = queue(OAK, PINE, OAK);
        FirepitSim.Result lit = litOn(fuels);
        List<Fuel> rest = List.of(PINE, OAK);
        for (boolean rain : new boolean[] {false, true}) {
            FirepitData start = lit.data().withRaining(rain);
            FirepitSim.Result sim = FirepitSim.advance(start, 0, 2000, queue(PINE, OAK), null, ASH, DEVICE, HEAT);
            FirepitRules.Advance rules = FirepitRules.advance(start.heat(), 2000, rain, rest, DEVICE);
            assertThat(sim.data().heat().burnTicks()).isEqualTo(rules.state().burnTicks());
            assertThat(sim.data().heat().burnTemperature()).isEqualTo(rules.state().burnTemperature());
            assertThat(sim.data().temperature()).isCloseTo(rules.state().temperature(), within(0.01f));
            assertThat(sim.consumed()).isEqualTo(rules.consumed());
        }
    }

    @Test
    void oakFirepitCooksRawBeefOnTick202() {
        // device +1 C per tick toward 728; beef heats 2.8/tick capped at the device and cools 0.8/tick, so the item sits
        // at the device temperature; the recipe tests the temperature before the tick's heating: t - 1.8 >= 200 at t = 202
        FirepitSim.Result lit = litOn(queue(OAK));
        FirepitSim.Cooking beef = new FirepitSim.Cooking(1f, HeatState.cold(0), 200f);
        FirepitSim.Result r = FirepitSim.advance(lit.data(), 0, 1000, queue(), beef, ASH, DEVICE, HEAT);
        assertThat(r.cooked().converted()).isTrue();
        assertThat(r.cooked().convertedAt()).isEqualTo(202);
        assertThat(r.cooked().temperatureBefore()).isCloseTo(200.2f, within(1e-3f));
        assertThat(r.cooked().heat()).isEqualTo(new HeatState(202f, 202));

        FirepitSim.Result early = FirepitSim.advance(lit.data(), 0, 201, queue(), beef, ASH, DEVICE, HEAT);
        assertThat(early.cooked().converted()).isFalse();
        assertThat(early.cooked().heat().temperature()).isEqualTo(201f);
    }

    @Test
    void anItemWithoutARecipeOnlyHeatsUpAndAnUnheatableOneIsIgnored() {
        FirepitSim.Result lit = litOn(queue(OAK));
        FirepitSim.Result r = FirepitSim.advance(lit.data(), 0, 2000, queue(), new FirepitSim.Cooking(1f, HeatState.cold(0),
            Float.NaN), ASH, DEVICE, HEAT);
        assertThat(r.cooked().converted()).isFalse();
        assertThat(r.cooked().heat().temperature()).isEqualTo(728f);
        assertThat(FirepitSim.advance(lit.data(), 0, 10, queue(), new FirepitSim.Cooking(0f, HeatState.cold(0), 200f), ASH,
            DEVICE, HEAT).cooked()).isNull();
    }

    @Test
    void runningOutOfFuelExtinguishesAndTheFirepitCoolsToZero() {
        FirepitSim.Result lit = litOn(queue(PINE));
        FirepitSim.Result r = FirepitSim.advance(lit.data(), 0, 1_000_000, queue(), null, ASH, DEVICE, HEAT);
        assertThat(r.data().lit()).isFalse();
        assertThat(r.data().heat()).isEqualTo(FirepitState.COLD);
        assertThat(r.data().dirtiness()).isZero();
        assertThat(r.data().smokeLevel()).isZero();
        assertThat(FirepitSim.ticksUntilOut(lit.data(), queue())).isEqualTo(1250);
        assertThat(FirepitSim.ticksUntilOut(lit.data().withRaining(true), queue(OAK))).isEqualTo((1250 + 2250) / 2);
        assertThat(FirepitSim.ticksUntilOut(FirepitData.FRESH, queue(OAK))).isEqualTo(-1);
        // still lit one tick before, out at the burn-out tick
        assertThat(FirepitSim.advance(lit.data(), 0, 1249, queue(), null, ASH, DEVICE, HEAT).data().lit()).isTrue();
        assertThat(FirepitSim.advance(lit.data(), 0, 1250, queue(), null, ASH, DEVICE, HEAT).data().lit()).isFalse();
    }

    @Test
    void rainBurnsTwiceAsFastAndCapsTheTargetAt300Below() {
        FirepitSim.Result lit = litOn(queue(OAK));
        FirepitSim.Result r = FirepitSim.advance(lit.data().withRaining(true), 0, 1000, queue(), null, ASH, DEVICE, HEAT);
        assertThat(r.data().heat().burnTicks()).isEqualTo(2250 - 2000);
        assertThat(r.data().temperature()).isEqualTo(728f - 300f);
    }

    @Test
    void smokeFollowsTheDirtinessEvery20Ticks() {
        FirepitSim.Result lit = litOn(queue(PINE)); // dirtiness 0.4
        FirepitSim.Result r = FirepitSim.advance(lit.data(), 0, 19, queue(), null, ASH, DEVICE, HEAT);
        assertThat(r.data().smokeLevel()).isZero(); // no update yet
        r = FirepitSim.advance(lit.data(), 0, 20, queue(), null, ASH, DEVICE, HEAT);
        assertThat(r.data().dirtiness()).isEqualTo(0.99f * (1f - 0.6f));
        assertThat(r.data().smokeLevel()).isEqualTo(2); // ceil(0.396 x 4)
        assertThat(FirepitSim.smokeLevel(0f)).isZero();
        assertThat(FirepitSim.smokeLevel(1.4f)).isEqualTo(4);
        assertThat(FirepitSim.smokeLevel(0.01f)).isEqualTo(1);
    }

    @Test
    void ashIsAHalfChancePerItemCappedAtEight() {
        List<Optional<Fuel>> many = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            many.add(Optional.of(PINE));
        }
        FirepitSim.Result lit = litOn(many);
        FirepitSim.Result r = FirepitSim.advance(lit.data(), 0, 100_000, many.subList(1, 12), null, ASH, DEVICE, HEAT);
        assertThat(r.consumed()).isEqualTo(11);
        assertThat(r.data().ash()).isEqualTo(FirepitData.MAX_ASH);

        Random seeded = new Random(42);
        int expected = 0;
        Random replay = new Random(42);
        for (int i = 0; i < 4; i++) {
            if (replay.nextFloat() < 0.5f) {
                expected++;
            }
        }
        FirepitSim.Result first = FirepitSim.light(FirepitData.FRESH, queue(PINE, PINE, PINE, PINE), seeded);
        FirepitSim.Result all = FirepitSim.advance(first.data(), 0, 100_000, queue(PINE, PINE, PINE), null, seeded, DEVICE,
            HEAT);
        assertThat(all.data().ash()).isEqualTo(expected);
    }

    @Test
    void extinguishKeepsTheTemperatureAndAirIntakeIsCapped() {
        FirepitSim.Result lit = litOn(queue(PINE));
        FirepitData hot = FirepitSim.advance(FirepitSim.intakeAir(lit.data(), 5000), 0, 100, queue(), null, ASH, DEVICE,
            HEAT).data();
        assertThat(hot.heat().airTicks()).isEqualTo(FirepitState.MAX_AIR_TICKS - 100);
        FirepitData out = FirepitSim.extinguish(hot);
        assertThat(out.lit()).isFalse();
        assertThat(out.heat()).isEqualTo(new FirepitState(0, 0f, hot.temperature(), 0));
        assertThat(out.dirtiness()).isZero();
        assertThat(FirepitSim.extinguish(out)).isSameAs(out);
    }

    @Test
    void codecRoundTrips() {
        FirepitData d = new FirepitData(new FirepitState(12, 600f, 432.5f, 7), true, 3, 0.7f, 5, 1250, true);
        assertThat(FirepitData.CODEC.decode(FirepitData.CODEC.encode(d))).isEqualTo(d);
        assertThat(FirepitData.CODEC.decode(FirepitData.CODEC.encode(FirepitData.FRESH))).isEqualTo(FirepitData.FRESH);
    }

    @Test
    void slotLimitsAreTfcs() {
        assertThat(FirepitSim.slotLimit(FirepitSim.SLOT_OUTPUT_1)).isEqualTo(64);
        assertThat(FirepitSim.slotLimit(FirepitSim.SLOT_OUTPUT_2)).isEqualTo(64);
        for (int s = 0; s <= FirepitSim.SLOT_ITEM_INPUT; s++) {
            assertThat(FirepitSim.slotLimit(s)).isEqualTo(1);
        }
    }
}
