/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/LampBlockEntity.java @ c3b4d9536 (checkHasRanOut)
 */
package dev.tfp.core.lamp;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.vessel.FluidAmount;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** PAR-METAL-19: {@link LampRules} against TFC's {@code checkHasRanOut}, and one lazy span against many small ones. */
class LampRulesTest {
    private static final String OIL = "tfp:olive_oil";

    private static LampData lit(String fluid, int amount) {
        return new LampData(new FluidAmount(fluid, amount), true, 0);
    }

    @Test
    void aSpanUsesWholeMillibucketsAndDropsTheRemainder() {
        // olive oil burns 7200 ticks per mB: 15000 ticks use 2 mB and the 600 left over are dropped (counter reset)
        LampData d = LampRules.advance(lit(OIL, 10), "copper", 15_000);
        assertThat(d.tank()).isEqualTo(new FluidAmount(OIL, 8));
        assertThat(d.carry()).isZero();
        assertThat(d.lit()).isTrue();
    }

    @Test
    void aShortSpanUsesNothingAndKeepsTheTicks() {
        LampData d = LampRules.advance(lit("tfp:tallow", 10), "copper", 2399);
        assertThat(d.tank().amount()).isEqualTo(10);
        assertThat(d.carry()).isEqualTo(2399);
        d = LampRules.advance(d, "copper", 1);
        assertThat(d.tank().amount()).isEqualTo(9);
        assertThat(d.carry()).isZero();
    }

    @Test
    void theLampGoesOutWhenTheTankRunsDryAndItsCounterResets() {
        LampData d = LampRules.advance(lit("tfp:canola_oil", 3), "bronze", 3 * 6000);
        assertThat(d.lit()).isFalse();
        assertThat(d.tank().isEmpty()).isTrue();
        LampData more = LampRules.advance(lit("tfp:canola_oil", 3), "bronze", 1_000_000);
        assertThat(more.lit()).isFalse();
        assertThat(more.tank().isEmpty()).isTrue();
    }

    @Test
    void aLitLampWithNoFuelOrANonFuelGoesOut() {
        assertThat(LampRules.advance(new LampData(FluidAmount.EMPTY, true, 0), "copper", 1).lit()).isFalse();
        assertThat(LampRules.advance(lit("tfp:water", 5), "copper", 1).lit()).isFalse();
        // lava only burns in red and blue steel lamps
        assertThat(LampRules.advance(lit("minecraft:lava", 5), "copper", 1).lit()).isFalse();
    }

    @Test
    void lavaNeverRunsOut() {
        LampData d = LampRules.advance(lit("minecraft:lava", 250), "red_steel", 100_000_000L);
        assertThat(d.lit()).isTrue();
        assertThat(d.tank().amount()).isEqualTo(250);
        assertThat(LampRules.ticksUntilOut(d, "red_steel")).isEmpty();
    }

    @Test
    void anUnlitLampDoesNotBurn() {
        LampData d = new LampData(new FluidAmount(OIL, 250), false, 0);
        assertThat(LampRules.advance(d, "copper", 1_000_000)).isEqualTo(d);
    }

    @Test
    void aFullLampOfOliveOilBurnsTwentyFiveDays() {
        // 250 mB x 7200 ticks = 1 800 000 ticks = 75 days of 24000 ticks
        assertThat(LampRules.ticksUntilOut(lit(OIL, 250), "steel")).hasValue(1_800_000L);
        // one check after 1 799 999 ticks uses 249 mB and drops the 7199 ticks left over (TFC resets the counter)
        LampData d = LampRules.advance(lit(OIL, 250), "steel", 1_799_999);
        assertThat(d.lit()).isTrue();
        assertThat(d.tank().amount()).isEqualTo(1);
        assertThat(LampRules.advance(lit(OIL, 250), "steel", 1_800_000).lit()).isFalse();
    }

    @Test
    void oneLazySpanEqualsTfcsCheckAtEveryTick() {
        Random r = new Random(19);
        for (int run = 0; run < 40; run++) {
            String fluid = new String[] {OIL, "tfp:tallow", "tfp:canola_oil"}[r.nextInt(3)];
            int amount = 1 + r.nextInt(4);
            int span = r.nextInt(60_000);
            LampData stepped = lit(fluid, amount);
            for (int t = 0; t < span; t++) {
                stepped = LampRules.advance(stepped, "copper", 1);
            }
            LampData lazy = LampRules.advance(lit(fluid, amount), "copper", span);
            assertThat(lazy.tank()).as(fluid + " " + amount + " " + span).isEqualTo(stepped.tank());
            assertThat(lazy.lit()).isEqualTo(stepped.lit());
        }
    }

    @Test
    void lightingNeedsAFuelAndStartsTheCounterAtZero() {
        assertThat(LampRules.light(new LampData(FluidAmount.EMPTY, false, 0), "copper").lit()).isFalse();
        assertThat(LampRules.light(new LampData(new FluidAmount("tfp:water", 5), false, 0), "copper").lit()).isFalse();
        LampData d = LampRules.light(new LampData(new FluidAmount(OIL, 5), false, 77), "copper");
        assertThat(d.lit()).isTrue();
        assertThat(d.carry()).isZero();
    }

    @Test
    void quenchingPutsALitLampOut() {
        assertThat(LampRules.quench(lit(OIL, 5)).lit()).isFalse();
        assertThat(LampRules.quench(lit(OIL, 5)).tank().amount()).isEqualTo(5);
    }

    @Test
    void fillingWithLavaLightsAnEligibleLampForAPlayerOnly() {
        LampData lava = new LampData(new FluidAmount("minecraft:lava", 100), false, 0);
        assertThat(LampRules.afterFill(lava, "blue_steel", true).lit()).isTrue();
        assertThat(LampRules.afterFill(lava, "blue_steel", false).lit()).isFalse();
        assertThat(LampRules.afterFill(lava, "copper", true).lit()).isFalse();
        LampData oil = new LampData(new FluidAmount(OIL, 100), false, 0);
        assertThat(LampRules.afterFill(oil, "blue_steel", true).lit()).isFalse();
    }

    @Test
    void acceptsOnlyFuelsOfTheLamp() {
        assertThat(LampRules.accepts(OIL, "copper")).isTrue();
        assertThat(LampRules.accepts("minecraft:lava", "copper")).isFalse();
        assertThat(LampRules.accepts("minecraft:lava", "red_steel")).isTrue();
        assertThat(LampRules.accepts("tfp:vinegar", "red_steel")).isFalse();
    }

    @Test
    void theCodecRoundTrips() {
        LampData d = new LampData(new FluidAmount(OIL, 120), true, 4321);
        assertThat(LampData.CODEC.decode(LampData.CODEC.encode(d))).isEqualTo(d);
        assertThat(LampData.CODEC.decode(LampData.CODEC.encode(LampData.FRESH))).isEqualTo(LampData.FRESH);
    }
}
