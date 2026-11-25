/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/entities/ai/prey/RammingPreyAi.java @ c3b4d9536 (constants, target
 * conditions, initMemories, attackerHasLeft), ai/prey/PrepareRamNearestTargetTFC.java (calcRamDamageMultiplier,
 * getEdgeOfBlock), ai/prey/RamTargetTFC.java (knockback, ram direction), ai/predator/PredatorAi.java
 * (hasNearbyAttacker), prey/RammingPrey.java (attributes, rammingReach), common/entities/TFCEntities.java (makeBoar ...)
 */
package dev.tfp.core.prey;

import java.util.random.RandomGenerator;

/**
 * The ramming rules of TFC's {@code RammingPrey} (boar, wildebeest, moose, bison) as pure functions. The charge itself, a
 * small state machine over these rules, is {@link RamCharge}.
 */
public final class RamRules {
    /** {@code RAM_PREPARE_TIME = 20}: ticks standing at the start position before the charge. */
    public static final int RAM_PREPARE_TIME = 20;
    /** {@code RAM_MAX_DISTANCE = 9}: the divisor of the damage multiplier. */
    public static final int RAM_MAX_DISTANCE = 9;
    /** {@code SPEED_MULTIPLIER_WHEN_PREPARING_TO_RAM = 1.6F}. */
    public static final float PREPARE_SPEED = 1.6f;
    /** {@code new RamTargetTFC(..., 3.0F, ...)}: the charge speed modifier. */
    public static final float RAM_SPEED = 3.0f;
    public static final float ADULT_RAM_KNOCKBACK_FORCE = 2.5f;
    public static final float BABY_RAM_KNOCKBACK_FORCE = 1.0f;
    /** {@code TIME_BETWEEN_RAMS_MALE = UniformInt.of(600, 1000)}. */
    public static final int MALE_COOLDOWN_MIN = 600;
    public static final int MALE_COOLDOWN_MAX = 1000;
    /** {@code TIME_BETWEEN_RAMS_FEMALE = UniformInt.of(1000, 1600)}. */
    public static final int FEMALE_COOLDOWN_MIN = 1000;
    public static final int FEMALE_COOLDOWN_MAX = 1600;
    /** {@code PrepareRamNearestTargetTFC((rammingPrey) -> 10, ...)}: the cooldown after a prepare that found no ram. */
    public static final int COOLDOWN_ON_FAIL = 10;
    /** {@code RamTargetTFC}'s behaviour duration ({@code TIME_OUT_DURATION = 200}). */
    public static final int RAM_TIMEOUT = 200;
    /** {@code PrepareRamNearestTargetTFC}'s behaviour duration ({@code super(..., 160)}). */
    public static final int PREPARE_TIMEOUT = 160;
    /** {@code RAM_SPEED_FORCE_FACTOR = 1.65F}. */
    public static final float RAM_SPEED_FORCE_FACTOR = 1.65f;
    /** {@code PredatorAi.hasNearbyAttacker}: an attacker closer than 16 blocks ({@code distanceToSqr < 256}). */
    public static final int NEARBY_ATTACKER_SQ = 256;
    /** {@code RammingPreyAi.attackerHasLeft}: the attacker memory is dropped at 20 blocks ({@code < 400} keeps it). */
    public static final int ATTACKER_LEFT_SQ = 400;
    /** {@code RammingPrey.createAttributes}: {@code ATTACK_DAMAGE 6.0} for all four rammers. */
    public static final double ATTACK_DAMAGE = 6.0;

    private RamRules() {
    }

    /**
     * {@code initMemories} / {@code finishRam}: the cooldown until the next ram, {@code UniformInt.sample}, inclusive.
     *
     * @param male   the rammer's gender
     * @param random the draw ({@code nextInt(max - min + 1)})
     * @return the cooldown in ticks
     */
    public static int cooldown(boolean male, RandomGenerator random) {
        final int min = male ? MALE_COOLDOWN_MIN : FEMALE_COOLDOWN_MIN;
        final int max = male ? MALE_COOLDOWN_MAX : FEMALE_COOLDOWN_MAX;
        return min + random.nextInt(max - min + 1);
    }

    /**
     * {@code calcRamDamageMultiplier}: {@code clamp(1.2f * startPos.distSqr(finishPos) / 9, 0.25f, 2.0f)}, float math
     * on whole block coordinates.
     *
     * @return the multiplier of the ram damage and knockback
     */
    public static float damageMultiplier(int sx, int sy, int sz, int tx, int ty, int tz) {
        final double dx = sx - tx;
        final double dy = sy - ty;
        final double dz = sz - tz;
        final float dist = (float) (dx * dx + dy * dy + dz * dz);
        final float v = 1.2f * dist / RAM_MAX_DISTANCE;
        return Math.max(0.25f, Math.min(2.0f, v));
    }

    /**
     * {@code getEdgeOfBlock}: the bottom centre of the target block, moved half a block toward the far side of the
     * target (by the sign of the difference on each axis).
     *
     * @return {x, y, z}
     */
    public static double[] edgeOfBlock(int sx, int sz, int tx, int ty, int tz) {
        final double d1 = 0.5D * Math.signum((double) (tx - sx));
        final double d2 = 0.5D * Math.signum((double) (tz - sz));
        return new double[] {tx + 0.5 + d1, ty, tz + 0.5 + d2};
    }

    /**
     * {@code RamTargetTFC.start}: the direction passed to {@code knockback}, {@code normalize(block - ramTarget)} on the
     * horizontal plane. Vanilla {@code knockback(strength, x, z)} pushes along {@code -(x, z)}, i.e. away from the rammer.
     *
     * @return {x, z}, or {0, 0} for a zero vector (vanilla's normalize of zero)
     */
    public static double[] ramDirection(int bx, int bz, double rx, double rz) {
        final double x = bx - rx;
        final double z = bz - rz;
        final double len = Math.sqrt(x * x + z * z);
        return len < 1.0E-4 ? new double[] {0, 0} : new double[] {x / len, z / len};
    }

    /**
     * The knockback strength of {@code RamTargetTFC.tick}:
     * {@code (clamp(speed * 1.65F, 0.2F, 3.0F) + 0.25F * (speedAmp - slowAmp)) * (blocked ? 0.5F : 1F)
     * * (multiplier * 0.6F) * force}.
     *
     * @param speed      the rammer's current speed ({@code getSpeed()})
     * @param speedLevel the speed effect level (amplifier + 1), 0 without
     * @param slowLevel  the slowness effect level (amplifier + 1), 0 without
     * @param blocked    the target blocks with a shield
     * @param multiplier the damage multiplier of the charge
     * @param baby       the rammer is a baby (force 1.0 instead of 2.5)
     * @return the strength
     */
    public static double knockback(float speed, int speedLevel, int slowLevel, boolean blocked, float multiplier, boolean baby) {
        final float f = 0.25F * (float) (speedLevel - slowLevel);
        final float f1 = Math.max(0.2F, Math.min(3.0F, speed * RAM_SPEED_FORCE_FACTOR)) + f;
        final float f2 = blocked ? 0.5F : 1.0F;
        final float f3 = multiplier * 0.6f;
        return (double) (f1 * f2 * f3) * (baby ? BABY_RAM_KNOCKBACK_FORCE : ADULT_RAM_KNOCKBACK_FORCE);
    }

    /** {@code ATTACK_DAMAGE * getAttackDamageMultiplier()}. */
    public static float damage(float multiplier) {
        return (float) ATTACK_DAMAGE * multiplier;
    }

    /**
     * The facts the ram target conditions read of a candidate.
     *
     * @param rammingPrey      the candidate is a {@code RammingPrey} (boar, wildebeest, moose, bison)
     * @param notRammed        the candidate is in {@code tfc:not_rammed_by_rammers}
     * @param male             the candidate's gender (only read when it is a rammer)
     * @param baby             the candidate is a baby
     * @param healthFraction   health over max health
     * @param creativeOrSpectator a player in creative or spectator ({@code forCombat} excludes them)
     */
    public record Candidate(boolean rammingPrey, boolean notRammed, boolean male, boolean baby, float healthFraction,
                            boolean creativeOrSpectator) {
    }

    /**
     * {@code RAM_TARGET_CONDITIONS} (everyone but adult males) and {@code RAM_TARGET_CONDITIONS_ADULT_MALE}.
     *
     * <p>An adult male rams anything that is not a rammer, and a rammer only when it is an adult male at 70 % health or
     * more (a rival), unless it is in {@code not_rammed_by_rammers}. Others ram anything that is neither a rammer nor in
     * {@code not_rammed_by_rammers}. Creative and spectator players are never targets ({@code forCombat}).
     *
     * @param adultMale the rammer is a male and not a baby
     * @param c         the candidate
     * @return {@code true} if {@code c} may be rammed
     */
    public static boolean canRam(boolean adultMale, Candidate c) {
        if (c.creativeOrSpectator()) {
            return false;
        }
        if (adultMale) {
            return !(c.rammingPrey() && !c.notRammed() && (!c.male() || c.baby() || c.healthFraction() < 0.7f));
        }
        return !c.rammingPrey() && !c.notRammed();
    }

    /** {@code PredatorAi.hasNearbyAttacker}: erases the ram cooldown. */
    public static boolean hasNearbyAttacker(double attackerDistanceSq) {
        return attackerDistanceSq < NEARBY_ATTACKER_SQ;
    }

    /** {@code attackerHasLeft}: erases the attacker memory. */
    public static boolean attackerHasLeft(double attackerDistanceSq) {
        return !(attackerDistanceSq < ATTACKER_LEFT_SQ);
    }

    /**
     * The four rammers and their {@code rammingReach} (the inflation of the hit box), from {@code TFCEntities}.
     *
     * @param species the species id
     * @return the reach in blocks, or -1 if {@code species} does not ram
     */
    public static double rammingReach(String species) {
        return switch (species) {
            case "tfc:boar", "tfc:wildebeest" -> 0.1;
            case "tfc:moose", "tfc:bison" -> 0.75;
            default -> -1;
        };
    }
}
