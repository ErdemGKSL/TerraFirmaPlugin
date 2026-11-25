/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/entities/ai/predator/PackLeaderSensor.java @ c3b4d9536 (doTick, setAlpha),
 * ai/predator/PackPredatorAi.java (initHuntActivity, isAlphaPassive, getAttackTarget, alertOthers, initCoreActivity),
 * ai/predator/PackPredator.java (finalizeSpawn, doHurtTarget, tick), ai/predator/PredatorBehaviors.java (listenToAlpha),
 * same commit
 */
package dev.tfp.core.predator;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * TFC's pack rules for wolves, direwolves and hyenas: respect, the alpha, the pack's shared target, the pack alert and
 * the night howl. Pure; the caller makes the random draws in TFC's order and applies the results.
 */
public final class PackRules {

    /** {@code PackPredator.finalizeSpawn}: {@code setRespect(random.nextInt(10))}. */
    public static final int INITIAL_RESPECT_BOUND = 10;
    /** {@code PackPredator.doHurtTarget}: a kill adds {@code random.nextInt(3)} respect. */
    public static final int KILL_RESPECT_BOUND = 3;
    /** {@code PackLeaderSensor}: {@code super(120)}, the scan period in ticks. */
    public static final int SENSOR_TICKS = 120;
    /** {@code PackLeaderSensor}: the bounding box inflated by 32 on each axis. */
    public static final double SENSOR_RANGE = 32.0;
    /** {@code listenToAlpha}: {@code new WalkTarget(new EntityTracker(alpha, false), 1.1f, 6)}. */
    public static final float LISTEN_SPEED = 1.1f;
    public static final int LISTEN_CLOSE_ENOUGH = 6;
    /** {@code PackPredator.tick}: the howl window ({@code time > 18000 && time < 19000}) and its reset ({@code time > 19000}). */
    public static final long HOWL_FROM = 18000;
    public static final long HOWL_TO = 19000;
    /** {@code random.nextInt(10) == 0} on each tick in the window. */
    public static final int HOWL_ONE_IN = 10;
    /** {@code getSoundVolume() * 1.2f}. */
    public static final float HOWL_VOLUME = 1.2f;

    private PackRules() {
    }

    /** One pack predator of the same type seen by the sensor. */
    public record Member(int respect, boolean baby) {
    }

    /**
     * The sensor's result.
     *
     * @param alpha    the index of the alpha in the nearby list, or {@code -1} when the scanning predator is its own alpha
     * @param respects the respect of each nearby member after the sensor's equal-respect nudges, in list order
     */
    public record Pack(int alpha, List<Integer> respects) {
    }

    /**
     * {@code PackLeaderSensor.setAlpha}. The scanning predator starts as the alpha with its own respect as the maximum.
     * Each adult member in turn: if its respect (read before the nudge) equals the running maximum it is nudged by one
     * (down when above 0, else up) so that two predators never share the top; if that read respect is above the maximum it
     * becomes the alpha. Babies are skipped and keep their respect.
     *
     * @param selfRespect the scanning predator's respect
     * @param nearby      the other living predators of the same type within 32 blocks, in scan order
     * @return the alpha and the nudged respects
     */
    public static Pack chooseAlpha(int selfRespect, List<Member> nearby) {
        int maxRespect = selfRespect;
        int alpha = -1;
        final Integer[] out = new Integer[nearby.size()];
        for (int i = 0; i < nearby.size(); i++) {
            final Member m = nearby.get(i);
            final int respect = m.respect();
            out[i] = respect;
            if (m.baby()) {
                continue;
            }
            if (respect == maxRespect) {
                out[i] = respect > 0 ? respect - 1 : respect + 1;
            }
            if (respect > maxRespect) {
                maxRespect = respect;
                alpha = i;
            }
        }
        return new Pack(alpha, List.of(out));
    }

    /**
     * {@code PackPredatorAi.getAttackTarget}: the alpha chooses its own target ({@link PredatorRules#attackTarget}); any
     * other member takes the alpha's {@code ATTACK_TARGET}, with no pacified or baby check.
     */
    public static <T> Optional<T> attackTarget(boolean isAlpha, Supplier<Optional<T>> own, Optional<T> alphaTarget) {
        return isAlpha ? own.get() : alphaTarget;
    }

    /**
     * The first passive trigger of the pack hunt activity: {@code becomePassiveIf(p -> p.getHealth() < 5f ||
     * isAlphaPassive(p), 200)}, only while not already pacified.
     */
    public static boolean passiveTriggered(PredatorMemory memory, long now, float health, boolean alphaPacified) {
        return (health < PredatorRules.PASSIVE_HEALTH || alphaPacified) && !memory.pacified(now);
    }

    /** Applies {@link #passiveTriggered}: the target is dropped and {@code PACIFIED} is set for 200 ticks. */
    public static PredatorMemory becomePassive(PredatorMemory memory, long now, float health, boolean alphaPacified) {
        if (!passiveTriggered(memory, now, health, alphaPacified)) {
            return memory;
        }
        return new PredatorMemory(now + PredatorRules.PASSIVE_TICKS, memory.huntedRecentlyUntil(), memory.wakeupUntil(),
            memory.cantReachSince(), false, memory.sleeping());
    }

    /**
     * {@code PackPredatorAi.alertOthers} for one other pack predator: the attacker (when there is one) becomes its target,
     * {@code HUNTED_RECENTLY} and {@code PACIFIED} are erased, and it wakes. The unreachable and wake-up memories stay.
     */
    public static PredatorMemory alerted(PredatorMemory memory, boolean attackerGiven) {
        return new PredatorMemory(PredatorMemory.NONE, PredatorMemory.NONE, memory.wakeupUntil(), memory.cantReachSince(),
            memory.attacking() || attackerGiven, false);
    }

    /**
     * The wake-up memory of a pack predator. TFC's pack brain has no {@code CountDownCooldownTicks(WAKEUP_TICKS)} in its
     * core activity, so once set by {@code wakeFromDisturbance} the memory never runs out: a woken pack predator never
     * falls asleep again and in its rest activity attacks like a hunter. Kept as TFC does it.
     *
     * @param memory the memory after {@link PredatorRules#wakesFromDisturbance}
     * @param before the memory before it
     * @return the memory with a wake-up that does not expire, when the call set one
     */
    public static PredatorMemory packWakeup(PredatorMemory memory, PredatorMemory before) {
        if (memory.wakeupUntil() == before.wakeupUntil()) {
            return memory;
        }
        return new PredatorMemory(memory.pacifiedUntil(), memory.huntedRecentlyUntil(), Long.MAX_VALUE,
            memory.cantReachSince(), memory.attacking(), memory.sleeping());
    }

    /** {@code PackPredator.doHurtTarget}: a hit that leaves the target dead adds the {@code nextInt(3)} draw. */
    public static int respectAfterHit(int respect, boolean targetDead, int draw) {
        return targetDead ? respect + draw : respect;
    }

    /**
     * {@code PackPredator.tick}: a howling pack predator that has not howled tonight howls when the day time is strictly
     * between 18000 and 19000 and the {@code nextInt(10)} draw is 0. TFC makes the draw only when the earlier terms hold.
     *
     * @param howled  it already howled tonight
     * @param howling its species howls
     * @param dayTime the level day time
     * @param draw    the {@code nextInt(10)} draw
     */
    public static boolean howls(boolean howled, boolean howling, long dayTime, int draw) {
        final long time = dayTime % PredatorSchedule.DAY_LENGTH;
        return !howled && howling && time > HOWL_FROM && time < HOWL_TO && draw == 0;
    }

    /** Whether the howl draw is made at all (so the caller draws in TFC's order). */
    public static boolean drawsHowl(boolean howled, boolean howling, long dayTime) {
        final long time = dayTime % PredatorSchedule.DAY_LENGTH;
        return !howled && howling && time > HOWL_FROM && time < HOWL_TO;
    }

    /** {@code PackPredator.tick}: the howled flag resets once the day time is above 19000. */
    public static boolean howlResets(long dayTime) {
        return dayTime % PredatorSchedule.DAY_LENGTH > HOWL_TO;
    }
}
