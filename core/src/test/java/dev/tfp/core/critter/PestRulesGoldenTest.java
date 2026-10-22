/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/util/Helpers.java @ c3b4d9536 (tickInfestation at line 573, choosePest at lines 615 to 641),
 * src/main/java/net/dries007/tfc/common/entities/prey/Pest.java (DRAG_TIME and EAT_TIME, the move control, finalizeSpawn),
 * src/main/java/net/dries007/tfc/ForgeEventHandler.java (lines 1190 to 1221 and 1445 to 1467),
 * src/main/java/net/dries007/tfc/common/blockentities/ComposterBlockEntity.java (line 67)
 */
package dev.tfp.core.critter;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Hand-traced cases for the pest rules, each checked against the TFC line it follows. The draws come from {@link ScriptedRandom},
 * so the test also checks the order and bounds of every draw.
 */
class PestRulesGoldenTest {

    /** {@code Helpers.tickInfestation} line 573: level 0 returns at once, no draw. Levels clamp to [0, 5]. */
    @Test
    void levelZeroAndClampingDrawNothing() {
        ScriptedRandom rand = new ScriptedRandom(new int[0], new float[0]);
        assertThat(PestInfestation.roll(0, 0, 20f, 300f, rand)).isEmpty();
        assertThat(rand.log).isEmpty();
        assertThat(PestInfestation.clampLevel(7)).isEqualTo(5);
        assertThat(PestInfestation.clampLevel(-2)).isZero();
    }

    /** {@code tickInfestation}: the gate is one in {@code 120 - 20 * level}, so 1 in 100 at level 1 and 1 in 20 at level 5. */
    @Test
    void levelGateBoundsAreTfcs() {
        ScriptedRandom one = new ScriptedRandom(new int[] {0, 5}, new float[0]);
        assertThat(PestInfestation.rollsThisTick(1, one)).isTrue();
        assertThat(PestInfestation.rollsThisTick(1, one)).isFalse();
        assertThat(one.log).containsExactly("i:100", "i:100");

        ScriptedRandom three = new ScriptedRandom(new int[] {0}, new float[0]);
        assertThat(PestInfestation.rollsThisTick(3, three)).isTrue();
        assertThat(three.log).containsExactly("i:60");

        ScriptedRandom five = new ScriptedRandom(new int[] {0}, new float[0]);
        assertThat(PestInfestation.rollsThisTick(5, five)).isTrue();
        assertThat(five.log).containsExactly("i:20");
    }

    /** {@code tickInfestation} and {@code Mth.clampedMap(count, 0, 8, 0, 1)}: chance 1 - count / 8, clamped. */
    @Test
    void nearbyPestsLowerTheChanceLinearly() {
        assertThat(PestInfestation.spawnChance(0)).isEqualTo(1f);
        assertThat(PestInfestation.spawnChance(4)).isEqualTo(0.5f);
        assertThat(PestInfestation.spawnChance(8)).isZero();
        assertThat(PestInfestation.spawnChance(12)).isZero();
    }

    /** {@code ForgeEventHandler} line 1221 and 1453 to 1467: food counts cap at 5; the open path honours the config, the click does not. */
    @Test
    void containerFoodLevels() {
        assertThat(PestInfestation.openLevel(true, 7)).isEqualTo(5);
        assertThat(PestInfestation.openLevel(false, 3)).isZero();
        assertThat(PestInfestation.clickLevel(3)).isEqualTo(3);
        assertThat(PestInfestation.clickLevel(9)).isEqualTo(5);
    }

    /** {@code ComposterBlockEntity} line 69: a rotten composter rolls at level 5; {@code PestFeastBehavior} lines 46 to 50 roll 1 to 5. */
    @Test
    void composterAndFeastLevels() {
        assertThat(PestInfestation.ROTTEN_COMPOST_LEVEL).isEqualTo(5);
        assertThat(PestInfestation.FEAST_LEVELS).containsExactly(1, 2, 3, 4, 5);
    }

    /**
     * {@code Helpers.tickInfestation} at level 5 with no pests nearby: the gate draws {@code nextInt(20)}, the chance draw
     * {@code nextFloat} passes against chance 1, then the climate pick: a 20 degree, 300 mm container with a 0.5 climate
     * draw is past the dry test and past 12 degrees, so it is a tropical pick (mongoose), one draw from the one-member tag.
     */
    @Test
    void levelFiveTropicalPick() {
        ScriptedRandom rand = new ScriptedRandom(new int[] {0, 0}, new float[] {0.99f, 0.5f});
        assertThat(PestInfestation.roll(5, 0, 20f, 300f, rand)).contains(PestSpecies.MONGOOSE);
        assertThat(rand.log).containsExactly("i:20", "f", "f", "i:1");
    }

    /**
     * {@code tickInfestation} with eight pests nearby: the chance is zero, so a nextFloat of 0.01 is over it and nothing spawns;
     * the climate pick draws nothing.
     */
    @Test
    void crowdedContainerSpawnsNothingAfterTheChanceDraw() {
        ScriptedRandom rand = new ScriptedRandom(new int[] {0}, new float[] {0.01f});
        assertThat(PestInfestation.roll(1, 8, 20f, 300f, rand)).isEmpty();
        assertThat(rand.log).containsExactly("i:100", "f");
    }

    /** {@code Helpers.choosePest} line 620: at or below -12 degrees nothing spawns, and no draw is made. */
    @Test
    void arcticContainerSpawnsNothing() {
        ScriptedRandom rand = new ScriptedRandom(new int[0], new float[0]);
        assertThat(PestClimate.choose(rand, -12f, 300f)).isEmpty();
        assertThat(PestClimate.choose(rand, -13f, 300f)).isEmpty();
        assertThat(rand.log).isEmpty();
    }

    /** {@code Helpers.choosePest} line 626: below -3 degrees (and above -12) it is a cold pick: one draw, the lemming. */
    @Test
    void coldClimateIsLemming() {
        ScriptedRandom rand = new ScriptedRandom(new int[] {0}, new float[0]);
        assertThat(PestClimate.choose(rand, -5f, 300f)).contains(PestSpecies.LEMMING);
        assertThat(rand.log).containsExactly("i:1");
    }

    /** {@code Helpers.choosePest} line 620 is strict: exactly -3 degrees is not cold, so the climate draw happens. */
    @Test
    void minusThreeIsNotCold() {
        ScriptedRandom rand = new ScriptedRandom(new int[] {0}, new float[] {0.9f});
        assertThat(PestClimate.choose(rand, -3f, 300f)).contains(PestSpecies.RAT);
        assertThat(rand.log).containsExactly("f", "i:1");
    }

    /** {@code Helpers.choosePest} line 628: the 0.7 draw is {@code nextFloat() <= 0.7}, compared as a double. */
    @Test
    void climateDrawBoundaryIsInclusive() {
        ScriptedRandom passes = new ScriptedRandom(new int[] {0}, new float[] {0.7f});
        assertThat(PestClimate.choose(passes, 5f, 100f)).contains(PestSpecies.JERBOA);
        ScriptedRandom fails = new ScriptedRandom(new int[] {0}, new float[] {0.71f});
        assertThat(PestClimate.choose(fails, 5f, 100f)).contains(PestSpecies.RAT);
        assertThat(fails.log).containsExactly("f", "i:1");
    }

    /** {@code Helpers.choosePest} lines 630 to 634: rainfall under 160 is desert, even when hot; 160 exactly is not dry. */
    @Test
    void dryRainfallIsDesertAndTheLimitIsStrict() {
        ScriptedRandom dry = new ScriptedRandom(new int[] {0}, new float[] {0.5f});
        assertThat(PestClimate.choose(dry, 20f, 100f)).contains(PestSpecies.JERBOA);
        ScriptedRandom edge = new ScriptedRandom(new int[] {0, 0}, new float[] {0.5f});
        assertThat(PestClimate.choose(edge, 20f, 160f)).contains(PestSpecies.MONGOOSE);
    }

    /** {@code Helpers.choosePest} lines 635 to 637: a wet container above 12 degrees is tropical; exactly 12 is not. */
    @Test
    void tropicalNeedsMoreThanTwelveDegrees() {
        ScriptedRandom hot = new ScriptedRandom(new int[] {0, 0}, new float[] {0.5f});
        assertThat(PestClimate.choose(hot, 12.5f, 200f)).contains(PestSpecies.MONGOOSE);
        ScriptedRandom edge = new ScriptedRandom(new int[] {0}, new float[] {0.5f});
        assertThat(PestClimate.choose(edge, 12f, 200f)).contains(PestSpecies.RAT);
    }

    /** {@code Helpers.choosePest} line 640: a failed 0.7 draw in a temperate wet climate falls through to a universal pick. */
    @Test
    void failedClimateDrawFallsThroughToUniversal() {
        ScriptedRandom rand = new ScriptedRandom(new int[] {0}, new float[] {0.9f});
        assertThat(PestClimate.choose(rand, 20f, 300f)).contains(PestSpecies.RAT);
        assertThat(rand.log).containsExactly("f", "i:1");
    }

    /** {@code Pest.java} lines 103 to 140: the held-item counter, from -1, climbs by one a tick; the item clears past 400. */
    @Test
    void feastCountsAndClearsAtFourHundredAndOne() {
        assertThat(PestFeast.tick(-1, false)).isEqualTo(new PestFeast.Step(-1, false));
        assertThat(PestFeast.tick(-1, true)).isEqualTo(new PestFeast.Step(0, false));
        assertThat(PestFeast.tick(399, true)).isEqualTo(new PestFeast.Step(400, false));
        assertThat(PestFeast.tick(400, true)).isEqualTo(new PestFeast.Step(401, true));
        assertThat(PestFeast.tick(401, false)).isEqualTo(new PestFeast.Step(-1, false));
    }

    /** {@code Pest.java} line 62: the pest moves when idle or after the feast (counter over 400), not while it is dragging. */
    @Test
    void movementIsBlockedDuringTheFeast() {
        assertThat(PestFeast.canMove(-1)).isTrue();
        assertThat(PestFeast.canMove(0)).isFalse();
        assertThat(PestFeast.canMove(400)).isFalse();
        assertThat(PestFeast.canMove(401)).isTrue();
        assertThat(PestFeast.END_TICKS).isEqualTo(400);
    }

    /** {@code Pest.java} lines 162 and 176 to 178: a persistent pest is never discarded, and the age test passes after 3600. */
    @Test
    void discardNeedsAgeAndAnUnlucky1In500Roll() {
        ScriptedRandom young = new ScriptedRandom(new int[0], new float[0]);
        assertThat(PestLifetime.discardsThisTick(3600, false, young)).isFalse();
        assertThat(young.log).isEmpty();

        ScriptedRandom hit = new ScriptedRandom(new int[] {0}, new float[0]);
        assertThat(PestLifetime.discardsThisTick(3601, false, hit)).isTrue();
        assertThat(hit.log).containsExactly("i:500");

        ScriptedRandom persistentHit = new ScriptedRandom(new int[] {0}, new float[0]);
        assertThat(PestLifetime.discardsThisTick(3601, true, persistentHit)).isFalse();
        assertThat(persistentHit.log).containsExactly("i:500");

        ScriptedRandom miss = new ScriptedRandom(new int[] {7}, new float[0]);
        assertThat(PestLifetime.discardsThisTick(9000, false, miss)).isFalse();
        assertThat(PestLifetime.removesWhenFarAway(true)).isFalse();
        assertThat(PestLifetime.removesWhenFarAway(false)).isTrue();
    }

    /** {@code Pest.java} lines 185 to 193: the Pak name roll (nextInt 1000) then the baby roll (nextFloat below 0.2f). */
    @Test
    void appearanceRollsNameThenBaby() {
        ScriptedRandom rand = new ScriptedRandom(new int[] {0}, new float[] {0.19f});
        assertThat(PestAppearance.roll(rand)).isEqualTo(new PestAppearance(true, true));
        assertThat(rand.log).containsExactly("i:1000", "f");

        ScriptedRandom plain = new ScriptedRandom(new int[] {999}, new float[] {0.2f});
        assertThat(PestAppearance.roll(plain)).isEqualTo(new PestAppearance(false, false));
    }

    /** {@code Helpers.choosePest} and the tag files: a species list is served as the tag file orders it. */
    @Test
    void groupsHoldTheirTagMembers() {
        assertThat(PestGroup.UNIVERSAL.members()).containsExactly(PestSpecies.RAT);
        assertThat(PestGroup.COLD.members()).containsExactly(PestSpecies.LEMMING);
        assertThat(PestGroup.DESERT.members()).containsExactly(PestSpecies.JERBOA);
        assertThat(PestGroup.TROPICAL.members()).containsExactly(PestSpecies.MONGOOSE);
        assertThat(PestSpecies.values()).hasSize(4);
    }
}
