/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/entities/aquatic/AquaticCritter.java @ c3b4d9536 (registerGoals, CritterEscapeGoal)
 */
package dev.tfp.core.critter;

/**
 * The goals of a water critter, as numbers and predicates. A critter flees from players only once it has been hurt, avoids ocean
 * predators always, and otherwise swims about at random. Pure: the caller reads the world.
 */
public final class CritterEscape {
    /** {@code CritterEscapeGoal(this, Player.class, 8.0F, 2.0D, 2.0D)}: the avoid distance for players. */
    public static final float PLAYER_DISTANCE = 8.0f;
    /** The walk and sprint speed modifier of the player escape. */
    public static final double PLAYER_SPEED = 2.0;
    /** {@code AvoidEntityGoal(this, WaterAnimal.class, 8f, 5f, 5.4f, ocean predators)}: the avoid distance. */
    public static final float PREDATOR_DISTANCE = 8.0f;
    /** The walk speed modifier of the predator avoid. */
    public static final double PREDATOR_WALK_SPEED = 5.0;
    /** The sprint speed modifier of the predator avoid. */
    public static final double PREDATOR_SPRINT_SPEED = 5.4;
    /** {@code RandomSwimmingGoal(this, 1.0F, 30)}: the interval and speed of the random swim. */
    public static final int RANDOM_SWIM_INTERVAL = 30;
    /** The squared distance past which a finished escape path still continues: {@code 7 * 7}. */
    public static final double CONTINUE_DISTANCE_SQ = 49.0;
    /** Vanilla keeps {@code lastHurtByMob} for this many ticks after the hit. */
    public static final int HURT_MEMORY_TICKS = 100;

    private CritterEscape() {
    }

    /**
     * {@code CritterEscapeGoal.shouldEscape}: the critter was hurt by a living mob, and the memory has not expired.
     *
     * @param ticksSinceHurt ticks since the last hit by a living entity, or -1 if it was never hit
     */
    public static boolean shouldEscape(long ticksSinceHurt) {
        return ticksSinceHurt >= 0 && ticksSinceHurt <= HURT_MEMORY_TICKS;
    }

    /**
     * {@code CritterEscapeGoal.canContinueToUse}: the avoided player is still known and either the path is not done or the player
     * is still within seven blocks.
     */
    public static boolean continues(boolean pathDone, double distanceSq) {
        return !pathDone || distanceSq > CONTINUE_DISTANCE_SQ;
    }
}
