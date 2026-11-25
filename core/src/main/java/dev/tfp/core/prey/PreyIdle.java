/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/entities/ai/prey/PreyAi.java @ c3b4d9536 (initCoreActivity,
 * initIdleActivity, createIdleMovementBehaviors); the behaviours it names are vanilla 1.21.1 (Swim, AnimalPanic,
 * SetEntityLookTargetSometimes, LookAtTargetSink, BabyFollowAdult, RandomStroll, SetWalkTargetFromLookTarget, DoNothing,
 * LandRandomPos.getPos) and their literals are kept as TFC passes them
 */
package dev.tfp.core.prey;

import java.util.random.RandomGenerator;

/**
 * The idle and core behaviours of TFC's prey brains ({@code PreyAi}, {@code RammingPreyAi}, {@code PestAi}) as constants
 * and draws. Every draw takes the caller's random source and makes the draws in vanilla's order.
 */
public final class PreyIdle {
    /** {@code new Swim(0.7F)}: the chance per tick to swim up while in water. */
    public static final float SWIM_CHANCE = 0.7f;
    /** Vanilla {@code LivingEntity.jumpInLiquid}: the upward speed added per swim tick. */
    public static final double SWIM_UP = 0.04;

    /** {@code new AnimalPanic<>(2.0F)}: the panic speed (prey and pests; rammers have no panic). */
    public static final float PANIC_SPEED = 2.0f;
    /** {@code AnimalPanic}'s duration: 100 to 120 ticks. */
    public static final int PANIC_MIN = 100;
    public static final int PANIC_MAX = 120;
    /** {@code LandRandomPos.getPos(mob, 5, 4)}: the panic target box. */
    public static final int PANIC_HORIZONTAL = 5;
    public static final int PANIC_VERTICAL = 4;

    /** {@code SetLookTarget.create(EntityType.PLAYER, 6.0F, UniformInt.of(30, 60))}. */
    public static final double LOOK_PLAYER_RANGE = 6.0;
    public static final int LOOK_INTERVAL_MIN = 30;
    public static final int LOOK_INTERVAL_MAX = 60;
    /** {@code new LookAtTargetSink(45, 90)}: how long a look target is held. */
    public static final int LOOK_HOLD_MIN = 45;
    public static final int LOOK_HOLD_MAX = 90;

    /** {@code BabyFollowAdult.create(UniformInt.of(5, 16), 1.25F)}. */
    public static final int FOLLOW_MIN = 5;
    public static final int FOLLOW_MAX = 16;
    public static final float FOLLOW_SPEED = 1.25f;

    /** {@code RandomStroll.stroll(1.0F)}: {@code LandRandomPos.getPos(mob, 10, 7)}. */
    public static final int STROLL_HORIZONTAL = 10;
    public static final int STROLL_VERTICAL = 7;
    public static final float STROLL_SPEED = 1.0f;
    /** {@code SetWalkTargetFromLookTarget.create(1.0F, 3)}: walk to the look target, stopping 3 blocks short. */
    public static final int LOOK_WALK_CLOSE = 3;
    /** {@code new DoNothing(30, 60)}. */
    public static final int WAIT_MIN = 30;
    public static final int WAIT_MAX = 60;

    /** One of {@code createIdleMovementBehaviors}' three equally weighted choices. */
    public enum Movement { STROLL, WALK_TO_LOOK_TARGET, WAIT }

    private PreyIdle() {
    }

    /** {@code RunOne} over three entries of weight 1: one uniform draw. */
    public static Movement movement(RandomGenerator random) {
        return Movement.values()[random.nextInt(3)];
    }

    /** A vanilla {@code Behavior} duration: {@code min + nextInt(max + 1 - min)}. */
    public static int duration(int min, int max, RandomGenerator random) {
        return min + random.nextInt(max + 1 - min);
    }

    /** The panic duration, see {@link #PANIC_MIN}. */
    public static int panicTicks(RandomGenerator random) {
        return duration(PANIC_MIN, PANIC_MAX, random);
    }

    /**
     * Vanilla {@code RandomPos.generateRandomDirection(random, h, v)}: draws x, then y, then z, each uniform in
     * {@code [-h, h]} / {@code [-v, v]}.
     *
     * @return the block offset
     */
    public static PreyFlee.Offset randomOffset(int horizontal, int vertical, RandomGenerator random) {
        final int x = random.nextInt(2 * horizontal + 1) - horizontal;
        final int y = random.nextInt(2 * vertical + 1) - vertical;
        final int z = random.nextInt(2 * horizontal + 1) - horizontal;
        return new PreyFlee.Offset(x, y, z);
    }

    /**
     * {@code BabyFollowAdult}: a baby walks to its nearest visible adult while it is at least 5 and less than 17 blocks
     * away ({@code closerThan(max + 1) && !closerThan(min)}).
     *
     * @param baby       the animal is a baby
     * @param distanceSq the squared distance to the nearest adult of its type
     * @return {@code true} to follow
     */
    public static boolean followsAdult(boolean baby, double distanceSq) {
        final double max = FOLLOW_MAX + 1;
        return baby && distanceSq < max * max && !(distanceSq < FOLLOW_MIN * FOLLOW_MIN);
    }
}
