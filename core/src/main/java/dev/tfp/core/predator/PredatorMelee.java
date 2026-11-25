/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/entities/ai/predator/PredatorAi.java @ c3b4d9536 (initFightActivity:
 * SetWalkTargetFromAttackTargetIfTargetOutOfReach 1.15, MeleeAttack 40), ai/predator/AmphibiousPredatorAi.java
 * (getSpeedModifier), ai/predator/AggressiveSwim.java (tick), same commit; with vanilla 1.21.1 Mob.isWithinMeleeAttackRange
 */
package dev.tfp.core.predator;

import java.util.Optional;

/**
 * The fight activity's reach, attack cooldown, chase speed and the aggressive swim lunge. Pure.
 */
public final class PredatorMelee {

    /** Vanilla {@code Mob.DEFAULT_ATTACK_REACH = Math.sqrt(2.04F) - 0.6F}, about 0.828 blocks. */
    public static final double DEFAULT_ATTACK_REACH = Math.sqrt(2.04F) - 0.6F;
    /** {@code MeleeAttack.create(40)}: the {@code ATTACK_COOLING_DOWN} time after a hit. */
    public static final int ATTACK_COOLDOWN_TICKS = 40;
    /** {@code SetWalkTargetFromAttackTargetIfTargetOutOfReach.create(1.15F)}. */
    public static final float CHASE_SPEED = 1.15f;
    /** {@code AmphibiousPredatorAi.getSpeedModifier}: 1.6 in water, else 1.0. */
    public static final float AMPHIBIOUS_WATER_SPEED = 1.6f;
    public static final float AMPHIBIOUS_LAND_SPEED = 1.0f;
    /** {@code new AggressiveSwim(0.8F)}: the jump chance per tick in water. */
    public static final float SWIM_JUMP_CHANCE = 0.8f;
    /** {@code AggressiveSwim.tick}: a lunge only beyond 3 blocks ({@code lengthSqr() > 3f * 3f}). */
    public static final double LUNGE_MIN_DISTANCE_SQ = 3f * 3f;
    /** {@code dist.normalize().scale(0.25f)} and the {@code 0.1} upward push. */
    public static final double LUNGE_SCALE = 0.25f;
    public static final double LUNGE_UP = 0.1;

    private PredatorMelee() {
    }

    /** An axis-aligned box, as vanilla's {@code AABB}. */
    public record Box(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        /** {@code AABB.inflate(x, y, z)}. */
        public Box inflate(double x, double y, double z) {
            return new Box(minX - x, minY - y, minZ - z, maxX + x, maxY + y, maxZ + z);
        }

        /** {@code AABB.intersects}: strict on every axis. */
        public boolean intersects(Box o) {
            return minX < o.maxX && maxX > o.minX && minY < o.maxY && maxY > o.minY && minZ < o.maxZ && maxZ > o.minZ;
        }
    }

    /**
     * Vanilla {@code Mob.isWithinMeleeAttackRange} for a mob that rides nothing: its box inflated horizontally by
     * {@link #DEFAULT_ATTACK_REACH} intersects the target's hitbox.
     */
    public static boolean withinMeleeRange(Box attacker, Box target) {
        return attacker.inflate(DEFAULT_ATTACK_REACH, 0.0, DEFAULT_ATTACK_REACH).intersects(target);
    }

    /**
     * {@code MeleeAttack}: a hit happens when the target is visible, within melee range and the cooldown is over.
     *
     * @param now           the current tick
     * @param coolingUntil  the tick the last hit's cooldown ends ({@code Long.MIN_VALUE} for none)
     * @param visible       the target is in the visible-entities memory
     * @param inRange       {@link #withinMeleeRange}
     */
    public static boolean attacks(long now, long coolingUntil, boolean visible, boolean inRange) {
        return now >= coolingUntil && visible && inRange;
    }

    /** {@code SetWalkTargetFromAttackTargetIfTargetOutOfReach}: chase unless the target is visible and within reach. */
    public static boolean chases(boolean visible, boolean inRange) {
        return !(visible && inRange);
    }

    /** The chase speed: 1.15 for land predators; {@code getSpeedModifier} for the amphibious one. */
    public static float chaseSpeed(PredatorSpecies.Kind kind, boolean inWaterOrBubble) {
        if (kind == PredatorSpecies.Kind.AMPHIBIOUS) {
            return inWaterOrBubble ? AMPHIBIOUS_WATER_SPEED : AMPHIBIOUS_LAND_SPEED;
        }
        return CHASE_SPEED;
    }

    /**
     * {@code AggressiveSwim.tick} after a passing jump draw: with a target more than 3 blocks away and not more than 3
     * blocks above, the velocity gains the normalised horizontal-and-vertical direction scaled by 0.25 on x and z, and 0.1
     * upwards.
     *
     * @return the velocity to add {x, y, z}, or empty
     */
    public static Optional<double[]> lunge(double px, double py, double pz, double tx, double ty, double tz) {
        final double dx = tx - px;
        final double dy = ty - py;
        final double dz = tz - pz;
        final double lengthSq = dx * dx + dy * dy + dz * dz;
        if (!(lengthSq > LUNGE_MIN_DISTANCE_SQ && ty - 3 <= py)) {
            return Optional.empty();
        }
        // Vec3.normalize: zero below 1.0E-4 (never here, the length is above 3)
        final double length = Math.sqrt(lengthSq);
        return Optional.of(new double[] {dx / length * LUNGE_SCALE, LUNGE_UP, dz / length * LUNGE_SCALE});
    }
}
