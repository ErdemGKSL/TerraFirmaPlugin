/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/entities/prey/TFCFrog.java @ c3b4d9536 (finalizeSpawn, mobInteract,
 * canMate, beenLongEnoughToMate, fedRecently, isFood, getBreedOffspring), prey/TFCFrogAi.java (initIdleActivity),
 * livestock/TFCAnimalProperties.java (READY_TO_MATE_FAMILIARITY), same commit
 */
package dev.tfp.core.amphibian;

/**
 * TFC's frog: a vanilla frog with TFC's variant by climate, a gender, familiarity gained by feeding {@code #tfc:frog_food}
 * (raw fish, spider eyes; not rotten) at most once a day, and TFC's mating conditions. TFC never puts a fed frog in love
 * ({@code mobInteract} returns before vanilla's feeding), so frogs do not breed; the conditions are kept for the record.
 */
public final class FrogRules {
    private FrogRules() {
    }

    /** {@code #tfc:frog_food}. */
    public static final String FOOD_TAG = "tfc:frog_food";
    /** {@code mobInteract}: the familiarity gained by one feeding. */
    public static final float FEED_FAMILIARITY = 0.1f;
    /** {@code ICalendar.TICKS_IN_DAY}: the wait between feedings. */
    public static final long TICKS_IN_DAY = 24000L;
    /** {@code TFCAnimalProperties.READY_TO_MATE_FAMILIARITY}. */
    public static final float READY_TO_MATE_FAMILIARITY = 0.3f;
    /** {@code beenLongEnoughToMate}: 12 days. */
    public static final long MATE_WAIT_TICKS = TICKS_IN_DAY * 12;
    /** {@code getBreedOffspring}: {@code LONG_JUMP_COOLDOWN_TICKS = UniformInt.of(100, 140)} on the new frog. */
    public static final int OFFSPRING_JUMP_COOLDOWN_MIN = 100;
    public static final int OFFSPRING_JUMP_COOLDOWN_MAX = 140;
    /** {@code TFCFrogAi}: tempt at 1.25, breed at 1.0, look at players within 6, find land within 6 at 1.0. */
    public static final float TEMPT_SPEED = 1.25f;
    public static final float BREED_SPEED = 1.0f;
    public static final float LOOK_RANGE = 6.0f;
    public static final int FIND_LAND_RANGE = 6;
    public static final float FIND_LAND_SPEED = 1.0f;

    /** The vanilla frog variants. */
    public enum Variant {
        TEMPERATE, WARM, COLD
    }

    /**
     * {@code finalizeSpawn}: below 0 degrees average sea-level temperature cold, above 18 warm, else temperate.
     *
     * @param averageSeaLevelTemperature {@code ChunkData.getAverageSeaLevelTemp} at the frog
     */
    public static Variant variant(float averageSeaLevelTemperature) {
        if (averageSeaLevelTemperature < 0) {
            return Variant.COLD;
        }
        return averageSeaLevelTemperature > 18 ? Variant.WARM : Variant.TEMPERATE;
    }

    /**
     * The familiarity and next-feed time of one frog.
     *
     * @param familiarity the familiarity (no clamp in TFC)
     * @param nextFeed    the calendar tick after which it eats again ({@link Long#MIN_VALUE} at first)
     * @param lastMated   the calendar tick it last mated ({@link Long#MIN_VALUE} at first)
     */
    public record State(float familiarity, long nextFeed, long lastMated) {
        public static final State FRESH = new State(0f, Long.MIN_VALUE, Long.MIN_VALUE);

        /**
         * {@code mobInteract} with frog food: past the next-feed time the frog gains 0.1 familiarity, waits a day and eats
         * one item; before it, nothing happens (the interaction still succeeds and keeps the item).
         *
         * @param now the calendar ticks
         * @return the new state, the same instance if it did not eat
         */
        public State feed(long now) {
            if (now > nextFeed) {
                return new State(familiarity + FEED_FAMILIARITY, now + TICKS_IN_DAY, lastMated);
            }
            return this;
        }

        /** {@code fedRecently}. */
        public boolean fedRecently(long now) {
            return now < nextFeed;
        }

        /** {@code beenLongEnoughToMate}: overflow-safe for the initial {@link Long#MIN_VALUE}. */
        public boolean beenLongEnoughToMate(long now) {
            return lastMated == Long.MIN_VALUE || now - lastMated > MATE_WAIT_TICKS;
        }
    }

    /**
     * {@code canMate}: opposite genders, both waited 12 days since mating, both above 0.3 familiarity, both fed within the
     * day.
     */
    public static boolean canMate(boolean male, State self, boolean otherMale, State other, long now) {
        return male != otherMale && self.beenLongEnoughToMate(now) && other.beenLongEnoughToMate(now)
                && self.familiarity() > READY_TO_MATE_FAMILIARITY && other.familiarity() > READY_TO_MATE_FAMILIARITY
                && self.fedRecently(now) && other.fedRecently(now);
    }
}
