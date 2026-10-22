/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.tooltip;

import static dev.tfp.paper.tooltip.NbtStacks.TEXT;
import static dev.tfp.paper.tooltip.NbtStacks.clock;
import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.heat.HeatConfig;
import dev.tfp.core.heat.HeatState;
import dev.tfp.core.heat.ItemHeatDefinitions;
import dev.tfp.paper.heat.HeatPdc;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * PAR-INFRA-05: the refresher's pure decision. A view is resent when a stack's rendered heat key (level, stars, suffix)
 * changes between two runs, or when a stack first seen changed within the last period; not otherwise.
 */
class TooltipRefresherTest {
    private static final String COPPER = "tfp:metal/ingot/copper";
    /** Copper ingot item_heat: capacity 2.857143, forging 648, welding 864. */
    private static final float CAPACITY = 2.857143f;
    private static final ItemHeatDefinitions DEFS = new ItemHeatDefinitions(
            Map.of(COPPER, new ItemHeatDefinitions.HeatData(CAPACITY, 648f, 864f)));
    private static final long PERIOD = TooltipRefresher.PERIOD_TICKS;

    private static TooltipPipeline pipeline() {
        TooltipPipeline p = new TooltipPipeline(() -> clock(0L), null);
        p.add(new HeatTooltipContributor(() -> DEFS, HeatConfig.DEFAULT, HeatDisplayStyle.COLOR, TEXT));
        return p;
    }

    /** A copper ingot whose heat record says {@code temperature} at {@code tick}. */
    private static TooltipItem ingot(float temperature, long tick) {
        return new TooltipItem(COPPER, "minecraft:copper_ingot", 1, TooltipData.of(Map.of(HeatPdc.KEY,
                Map.of("tfp:v", 1, "tfp:temperature", temperature, "tfp:tick", tick))));
    }

    @Test
    void crossingTheBrightRedToDarkRedBoundaryResendsOnce() {
        TooltipPipeline p = pipeline();
        TooltipRefresher.RefreshState state = new TooltipRefresher.RefreshState(PERIOD);
        // Freshly stamped records: the key one period earlier is the same, so a first sight needs no resend.
        assertThat(state.observe(1, Map.of(0, ingot(731f, 1000L)), clock(1000L), p)).isFalse();
        assertThat(state.observe(1, Map.of(0, ingot(729f, 1020L)), clock(1020L), p)).as("730: Dark Red٭٭٭٭").isTrue();
        assertThat(state.observe(1, Map.of(0, ingot(728f, 1040L)), clock(1040L), p)).isFalse();
    }

    @Test
    void eachStarStepResendsAndTemperaturesInsideAStepDoNot() {
        TooltipPipeline p = pipeline();
        TooltipRefresher.RefreshState state = new TooltipRefresher.RefreshState(PERIOD);
        // Bright Red [730, 930): steps at 770, 810, 850, 890
        assertThat(state.observe(1, Map.of(0, ingot(900f, 0L)), clock(0L), p)).isFalse();
        assertThat(state.observe(1, Map.of(0, ingot(895f, 20L)), clock(20L), p)).isFalse();
        assertThat(state.observe(1, Map.of(0, ingot(889f, 40L)), clock(40L), p)).as("4 -> 3 stars").isTrue();
        assertThat(state.observe(1, Map.of(0, ingot(860f, 60L)), clock(60L), p))
                .as("3 stars, Can Weld -> Can Work").isTrue();
        assertThat(state.observe(1, Map.of(0, ingot(852f, 80L)), clock(80L), p)).isFalse();
    }

    @Test
    void aStackFirstSeenRightAfterItsKeyChangedIsResent() {
        TooltipPipeline p = pipeline();
        HeatState record = new HeatState(735f, 0L);
        long crossed = 0L;
        while (record.temperatureAt(crossed, CAPACITY, HeatConfig.DEFAULT) >= 730f) {
            crossed++;
        }
        // The stack was sent (as Bright Red) within the last period, and is Dark Red now.
        TooltipRefresher.RefreshState state = new TooltipRefresher.RefreshState(PERIOD);
        assertThat(state.observe(7, Map.of(3, ingot(735f, 0L)), clock(crossed), p)).isTrue();
        // A stack whose key has not changed for a while: no resend on first sight.
        TooltipRefresher.RefreshState other = new TooltipRefresher.RefreshState(PERIOD);
        assertThat(other.observe(7, Map.of(3, ingot(735f, 0L)), clock(crossed + 2 * PERIOD), p)).isFalse();
    }

    @Test
    void aNewViewComparesAgainstThePreviousPeriodNotTheOldKeys() {
        TooltipPipeline p = pipeline();
        TooltipRefresher.RefreshState state = new TooltipRefresher.RefreshState(PERIOD);
        assertThat(state.observe(1, Map.of(0, ingot(900f, 0L)), clock(0L), p)).isFalse();
        // Another view (a chest opened): slot 0 holds something else now, sent in full at the open.
        assertThat(state.observe(2, Map.of(0, ingot(500f, 20L)), clock(20L), p)).isFalse();
    }

    @Test
    void anItemCoolingOutOfSightIsResentAndAColdOneNever() {
        TooltipPipeline p = pipeline();
        TooltipRefresher.RefreshState state = new TooltipRefresher.RefreshState(PERIOD);
        assertThat(state.observe(1, Map.of(0, ingot(1f, 0L)), clock(0L), p)).isFalse();
        assertThat(state.observe(1, Map.of(0, ingot(0f, 20L)), clock(20L), p)).as("Warming line removed").isTrue();
        assertThat(state.observe(1, Map.of(0, ingot(0f, 40L)), clock(40L), p)).isFalse();
        assertThat(state.observe(1, Map.of(), clock(60L), p)).isFalse();
    }

    @Test
    void theShiftedClockMovesBothClocksAndStopsAtZero() {
        assertThat(TooltipRefresher.RefreshState.shifted(clock(100L), -20L).playerTicks()).isEqualTo(80L);
        assertThat(TooltipRefresher.RefreshState.shifted(clock(100L), -20L).calendarTicks()).isEqualTo(80L);
        assertThat(TooltipRefresher.RefreshState.shifted(clock(5L), -20L).playerTicks()).isZero();
    }
}
