/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/entities/ai/predator/PackPredator.java @ c3b4d9536 (isFood, mobInteract,
 * familiarity), src/main/java/net/dries007/tfc/common/entities/ai/predator/PackPredatorAi.java (initHuntActivity: the
 * temptation passive and FollowTemptation), src/main/java/net/dries007/tfc/common/entities/ai/predator/PredatorBehaviors.java
 * (becomePassiveIf)
 */
package dev.tfp.core.predator;

import dev.tfp.core.livestock.LivestockRules;

/**
 * Taming a pack predator by feeding (TFC {@code PackPredator}): a tamable one (the wolf) fed {@code tfc:dog_food} that is
 * not rotten gains 0.1 familiarity at most once per calendar day, and above 0.99 it becomes a dog of its gender. While a
 * player holds that food within 10 blocks the pack hunt activity turns passive for 400 ticks and follows the player.
 * Pure functions of the stored values and the calendar tick; nothing here ticks.
 */
public final class PackTaming {
    /** {@code PackPredator.mobInteract}: {@code addFamiliarity(0.1f)}. */
    public static final float FEED_FAMILIARITY = 0.1f;
    /** {@code PackPredator.mobInteract}: the animal converts to a dog when familiarity is above this. */
    public static final float CONVERT_ABOVE = 0.99f;
    /** {@code PackPredator.mobInteract}: {@code nextFeedTime = ticks + ICalendar.TICKS_IN_DAY}. */
    public static final long FEED_INTERVAL_TICKS = LivestockRules.TICKS_IN_DAY;
    /** {@code PackPredatorAi.initHuntActivity}: {@code becomePassiveIf(TEMPTING_PLAYER present, 400)}. */
    public static final int TEMPT_PASSIVE_TICKS = 400;
    /** {@code PackPredatorAi.initHuntActivity}: {@code FollowTemptation(e -> e.isBaby() ? 1.5f : 1.2f)}. */
    public static final float FOLLOW_SPEED_ADULT = 1.2f;
    public static final float FOLLOW_SPEED_BABY = 1.5f;
    /** {@code PackPredator.nextFeedTime} default: {@code Long.MIN_VALUE}, so the first feed is always accepted. */
    public static final long NEVER_FED = Long.MIN_VALUE;

    /**
     * The stored taming values of one pack predator.
     *
     * @param familiarity {@code DATA_FAMILIARITY}, clamped to [0, 1] ({@code setFamiliarity})
     * @param nextFeed    {@code nextFeedTime}: the first tick a feed is accepted again, after this one
     */
    public record State(float familiarity, long nextFeed) {
        public State {
            familiarity = Math.max(0f, Math.min(1f, familiarity));
        }

        /** A wild predator: no familiarity, never fed. */
        public static final State FRESH = new State(0f, NEVER_FED);
    }

    /**
     * The result of a feed.
     *
     * @param state    the state after the feed (unchanged when the feed was too soon)
     * @param accepted {@code true} if the food was eaten (familiarity added, one item used)
     * @param converts {@code true} if the predator becomes a dog now
     */
    public record Feed(State state, boolean accepted, boolean converts) {
    }

    private PackTaming() {
    }

    /**
     * {@code PackPredator.isFood}: not rotten, tamable, and in {@code #tfc:dog_food}.
     */
    public static boolean isFood(boolean tamable, boolean rotten, boolean dogFood) {
        return !rotten && tamable && dogFood;
    }

    /**
     * {@code PackPredator.mobInteract} on the server with food in hand: when {@code now > nextFeed} the familiarity rises
     * by 0.1 (clamped), the next feed is a calendar day away, and above 0.99 the predator converts. A feed before that
     * changes nothing and uses nothing.
     *
     * @param now the calendar tick ({@code Calendars.SERVER.getTicks()}, the player-time clock the livestock use)
     */
    public static Feed feed(State state, long now) {
        if (!(now > state.nextFeed())) {
            return new Feed(state, false, false);
        }
        final State next = new State(state.familiarity() + FEED_FAMILIARITY, now + FEED_INTERVAL_TICKS);
        return new Feed(next, true, next.familiarity() > CONVERT_ABOVE);
    }

    /**
     * {@code becomePassiveIf(tempting player present, 400)} (the second passive trigger of the hunt activity): while the
     * predator is not already pacified, the target is dropped and {@code PACIFIED} is set for 400 ticks. A predator that is
     * already pacified keeps its own expiry.
     */
    public static PredatorMemory becomePassiveTempted(PredatorMemory memory, long now, boolean tempted) {
        if (!tempted || memory.pacified(now)) {
            return memory;
        }
        return new PredatorMemory(now + TEMPT_PASSIVE_TICKS, memory.huntedRecentlyUntil(), memory.wakeupUntil(),
            memory.cantReachSince(), false, memory.sleeping());
    }

    /** {@code FollowTemptation}'s walk speed: 1.5 for a baby, 1.2 for an adult. */
    public static float followSpeed(boolean baby) {
        return baby ? FOLLOW_SPEED_BABY : FOLLOW_SPEED_ADULT;
    }
}
