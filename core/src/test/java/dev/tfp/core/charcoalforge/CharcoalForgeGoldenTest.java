/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/CharcoalForgeBlockEntity.java @ c3b4d9536 (serverTick,
 *       onCalendarUpdate, onFirstCreation), src/main/java/net/dries007/tfc/common/blockentities/BellowsBlockEntity.java @ c3b4d9536,
 *       src/generated/resources/data/tfc/tfc/fuel/charcoal.json @ c3b4d9536
 */
package dev.tfp.core.charcoalforge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.tfp.core.cooking.FirepitRules;
import dev.tfp.core.fuel.Fuel;
import dev.tfp.core.fuel.FuelTable;
import dev.tfp.core.heat.HeatLevel;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Golden values for the charcoal forge. TFC ships no forge data of its own: the forge's recipes are the shared heating
 * recipes, and its only fuel is the shared charcoal fuel entry. So the golden values are the constants, the charcoal fuel
 * row, and short TFC {@code serverTick} traces worked out from the source at c3b4d9536 with TFC's default settings
 * (deviceHeatingModifier 1, no rain, no air unless stated).
 */
class CharcoalForgeGoldenTest {
    private static final FirepitRules.DeviceConfig DEVICE = FirepitRules.DeviceConfig.DEFAULT;

    @Test
    void constantsMatchTfcSource() {
        assertThat(BellowsRules.BELLOWS_AIR).isEqualTo(200);
        assertThat(BellowsRules.MAX_DEVICE_AIR_TICKS).isEqualTo(600);
        assertThat(BellowsRules.PRESS_INTERVAL_TICKS).isEqualTo(20);
        assertThat(CharcoalForgeRules.FIRST_CREATION_BURN_TICKS).isEqualTo(200);
        assertThat(CharcoalForgeRules.FIRST_CREATION_TEMPERATURE).isEqualTo(500f);
        assertThat(CharcoalForgeRules.MAX_HEAT_LEVEL).isEqualTo(7);
        assertThat(HeatLevel.MAX_VISIBLE).isEqualTo(1600f);
    }

    @Test
    void charcoalFuelMatchesTfcData() {
        // src/generated/resources/data/tfc/tfc/fuel/charcoal.json: duration 1800, temperature 1350.0
        Fuel charcoal = FuelTable.byId("charcoal").orElseThrow();
        assertThat(charcoal.duration()).isEqualTo(1800);
        assertThat(charcoal.temperature()).isEqualTo(1350f);
    }

    @Test
    void heatLevelBoundariesMatchTfcFormula() {
        // level = clamp((int) (t / 1600 * 6) + 1, 1, 7)
        assertThat(CharcoalForgeRules.heatLevel(0f)).isEqualTo(1);
        assertThat(CharcoalForgeRules.heatLevel(266f)).isEqualTo(1);
        assertThat(CharcoalForgeRules.heatLevel(267f)).isEqualTo(2);
        assertThat(CharcoalForgeRules.heatLevel(533f)).isEqualTo(2);
        assertThat(CharcoalForgeRules.heatLevel(534f)).isEqualTo(3);
        assertThat(CharcoalForgeRules.heatLevel(800f)).isEqualTo(4);
        assertThat(CharcoalForgeRules.heatLevel(1066f)).isEqualTo(4);
        assertThat(CharcoalForgeRules.heatLevel(1067f)).isEqualTo(5);
        assertThat(CharcoalForgeRules.heatLevel(1334f)).isEqualTo(6);
        assertThat(CharcoalForgeRules.heatLevel(1599f)).isEqualTo(6);
        assertThat(CharcoalForgeRules.heatLevel(1600f)).isEqualTo(7);
        assertThat(CharcoalForgeRules.heatLevel(2000f)).isEqualTo(7);
    }

    @Test
    void firstCreationTraceCoolsAfterTheFirstBurn() {
        // Lit with 200 burn ticks at 500 C, cold, no air. Each tick the temperature rises 1 towards 500 while burning.
        // At tick 200 the burn runs out, the forge goes out, and that tick's temperature step already targets 0: 199 - 1 = 198.
        CharcoalForgeState state = CharcoalForgeRules.firstCreation();
        for (int tick = 1; tick <= 199; tick++) {
            CharcoalForgeRules.Step step = CharcoalForgeRules.step(state, false, null, DEVICE);
            assertThat(step.heatLevel()).as("heat at tick %d", tick).isEqualTo(1);
            state = step.state();
        }
        assertThat(state.lit()).isTrue();
        assertThat(state.burnTicks()).isEqualTo(1);
        assertThat(state.temperature()).isEqualTo(199f);

        CharcoalForgeRules.Step last = CharcoalForgeRules.step(state, false, null, DEVICE);
        assertThat(last.consumedFuel()).isFalse();
        assertThat(last.heatLevel()).isZero();
        assertThat(last.state()).isEqualTo(new CharcoalForgeState(false, 0, 0f, 198f, 0));

        // The lazy form gives the same state at the same tick.
        CharcoalForgeRules.Advance lazy = CharcoalForgeRules.advance(CharcoalForgeRules.firstCreation(), 200, false, List.of(), DEVICE);
        assertThat(lazy.state()).isEqualTo(new CharcoalForgeState(false, 0, 0f, 198f, 0));
        assertThat(lazy.consumed()).isZero();

        // Then cooling at 1 per tick, so 198 more ticks bring a cold forge to 0.
        CharcoalForgeRules.Advance cooled = CharcoalForgeRules.advance(lazy.state(), 198, false, List.of(), DEVICE);
        assertThat(cooled.state()).isEqualTo(new CharcoalForgeState(false, 0, 0f, 0f, 0));
    }

    @Test
    void bellowsAirDoublesTheHeatingStep() {
        // Lit, burn 100 at 500 C, device at 100 C, air 200. The burn reads the air before it counts down (rate 2): 98.
        // The air counts down to 199 before the temperature step, so the air influence is 4 x 199 = 796 capped at 600,
        // and the target is 500 + min(600, 250) = 750. Heating with air moves 2 per tick: 100 + 2 = 102.
        CharcoalForgeState state = new CharcoalForgeState(true, 100, 500f, 100f, 200);
        CharcoalForgeRules.Step step = CharcoalForgeRules.step(state, false, null, DEVICE);
        assertThat(step.state().burnTicks()).isEqualTo(98);
        assertThat(step.state().airTicks()).isEqualTo(199);
        assertThat(step.state().temperature()).isEqualTo(102f);
    }

    @Test
    void rainDoublesTheBurnAndLowersTheTarget() {
        // Lit, burn 100 at 500 C, device cold, raining: the burn drops 2 (rate 2). The target is 500 - 300 = 200, so 0 + 1 = 1.
        CharcoalForgeState state = new CharcoalForgeState(true, 100, 500f, 0f, 0);
        CharcoalForgeRules.Step step = CharcoalForgeRules.step(state, true, null, DEVICE);
        assertThat(step.state().burnTicks()).isEqualTo(98);
        assertThat(step.state().temperature()).isEqualTo(1f);
    }

    @Test
    void extinguishKeepsTheBellowsAirUnlikeTheFirepit() {
        // Lit, burn 1, no fuel, 100 air. The burn ends and the forge goes out, but TFC's forge extinguish keeps the air,
        // so the air counts down to 99 (the firepit clears it). The target is 0 with air, so the device cools with the
        // 0.5 negative delta: 300 - 0.5 = 299.5.
        CharcoalForgeState state = new CharcoalForgeState(true, 1, 500f, 300f, 100);
        CharcoalForgeRules.Step step = CharcoalForgeRules.step(state, false, null, DEVICE);
        assertThat(step.state().lit()).isFalse();
        assertThat(step.state().burnTicks()).isZero();
        assertThat(step.state().burnTemperature()).isZero();
        assertThat(step.state().airTicks()).isEqualTo(99);
        assertThat(step.state().temperature()).isCloseTo(299.5f, within(1e-4f));
        assertThat(step.heatLevel()).isZero();
    }

    @Test
    void fuelRefillTraceUsesTheCharcoalDuration() {
        // Lit, burn 1, next charcoal: the burn ends, the charcoal adds 1800 ticks at 1350 C, and the device heats by 1.
        Fuel charcoal = FuelTable.byId("charcoal").orElseThrow();
        CharcoalForgeState state = new CharcoalForgeState(true, 1, 500f, 0f, 0);
        CharcoalForgeRules.Step step = CharcoalForgeRules.step(state, false, charcoal, DEVICE);
        assertThat(step.consumedFuel()).isTrue();
        assertThat(step.state()).isEqualTo(new CharcoalForgeState(true, 1800, 1350f, 1f, 0));
    }

    @Test
    void catchUpBurnsFuelWithoutMovingTheTemperature() {
        // Lit with 200 burn at 500 C and 800 C device. 1900 ticks: 200 burn, then 1700 into the charcoal, leaving 100 of its 1800.
        Fuel charcoal = FuelTable.byId("charcoal").orElseThrow();
        CharcoalForgeState state = new CharcoalForgeState(true, 200, 500f, 800f, 0);
        CharcoalForgeRules.CatchUp out = CharcoalForgeRules.catchUp(state, 1900, List.of(charcoal));
        assertThat(out.state()).isEqualTo(new CharcoalForgeState(true, 100, 1350f, 800f, 0));
        assertThat(out.consumed()).isEqualTo(1);
        assertThat(out.extinguished()).isFalse();
    }

    @Test
    void catchUpExtinguishesWhenTheFuelRunsOut() {
        // 2100 ticks with no fuel left: 200 of burn, then the forge goes out. The device temperature and air are kept.
        CharcoalForgeState state = new CharcoalForgeState(true, 200, 500f, 800f, 42);
        CharcoalForgeRules.CatchUp out = CharcoalForgeRules.catchUp(state, 2100, List.of());
        assertThat(out.state()).isEqualTo(new CharcoalForgeState(false, 0, 0f, 800f, 42));
        assertThat(out.extinguished()).isTrue();
        assertThat(out.consumed()).isZero();
    }

    @Test
    void catchUpEndingOnTheLastBurnTickLeavesTheForgeLitWithNoBurn() {
        // TFC's FuelBurn returns burn 0 with no spare ticks, so onCalendarUpdate does not extinguish. The forge stays lit.
        CharcoalForgeRules.CatchUp out = CharcoalForgeRules.catchUp(new CharcoalForgeState(true, 200, 500f, 0f, 0), 200, List.of());
        assertThat(out.state()).isEqualTo(new CharcoalForgeState(true, 0, 500f, 0f, 0));
        assertThat(out.extinguished()).isFalse();
    }
}
