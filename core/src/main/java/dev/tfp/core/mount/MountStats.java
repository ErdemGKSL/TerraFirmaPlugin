/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/entities/livestock/horse/HorseProperties.java @ c3b4d9536 (MIN/MAX constants,
 * generateMaxHealth, generateJumpStrength, generateSpeed, applyGenes) and src/main/java/net/dries007/tfc/common/entities/
 * EntityHelpers.java (createOffspringAttribute)
 */
package dev.tfp.core.mount;

/**
 * The three base stats TFC's horse-family mounts carry (horse, donkey, mule): max health, jump strength and movement
 * speed, as the attribute base values TFC sets at spawn or inherits at birth.
 *
 * <p>TFC's {@code MIN_*} and {@code MAX_*} constants are {@code float} fields, so the ranges here are the float values
 * widened to {@code double}, as TFC passes them to {@code createOffspringAttribute}.
 *
 * @param maxHealth     the max health attribute base, in [{@link #MIN_HEALTH}, {@link #MAX_HEALTH}]
 * @param jumpStrength  the jump strength attribute base, in [{@link #MIN_JUMP_STRENGTH}, {@link #MAX_JUMP_STRENGTH}]
 * @param movementSpeed the movement speed attribute base, in [{@link #MIN_MOVEMENT_SPEED}, {@link #MAX_MOVEMENT_SPEED}]
 */
public record MountStats(double maxHealth, double jumpStrength, double movementSpeed) {

    /** TFC {@code HorseProperties.MIN_HEALTH} = {@code generateMaxHealth(v -> 0)}, 15 + 0 + 0. */
    public static final double MIN_HEALTH = (float) maxHealth(0, 0);

    /** TFC {@code HorseProperties.MAX_HEALTH} = {@code generateMaxHealth(v -> v - 1)}, 15 + 7 + 8. */
    public static final double MAX_HEALTH = (float) maxHealth(7, 8);

    /** TFC {@code HorseProperties.MIN_JUMP_STRENGTH} = {@code generateJumpStrength(() -> 0.0)}, as a float. */
    public static final double MIN_JUMP_STRENGTH = (float) jumpStrength(0.0, 0.0, 0.0);

    /** TFC {@code HorseProperties.MAX_JUMP_STRENGTH} = {@code generateJumpStrength(() -> 1.0)}, as a float. */
    public static final double MAX_JUMP_STRENGTH = (float) jumpStrength(1.0, 1.0, 1.0);

    /** TFC {@code HorseProperties.MIN_MOVEMENT_SPEED} = {@code generateSpeed(() -> 0.0)}, as a float. */
    public static final double MIN_MOVEMENT_SPEED = (float) movementSpeed(0.0, 0.0, 0.0);

    /** TFC {@code HorseProperties.MAX_MOVEMENT_SPEED} = {@code generateSpeed(() -> 1.0)}, as a float. */
    public static final double MAX_MOVEMENT_SPEED = (float) movementSpeed(1.0, 1.0, 1.0);

    /**
     * TFC {@code HorseProperties.generateMaxHealth}: {@code 15 + nextInt(8) + nextInt(9)}, with the two draws passed in.
     *
     * @param first  a value in [0, 8), the draw {@code nextInt(8)}
     * @param second a value in [0, 9), the draw {@code nextInt(9)}
     */
    public static double maxHealth(int first, int second) {
        if (first < 0 || first >= 8 || second < 0 || second >= 9) {
            throw new IllegalArgumentException("max health draws outside [0, 8) and [0, 9): " + first + ", " + second);
        }
        return 15.0 + first + second;
    }

    /**
     * TFC {@code HorseProperties.generateJumpStrength}: {@code 0.4 + s1 * 0.2 + s2 * 0.2 + s3 * 0.2}, with the three
     * {@code nextDouble} draws passed in order.
     */
    public static double jumpStrength(double r1, double r2, double r3) {
        return 0.4 + r1 * 0.2 + r2 * 0.2 + r3 * 0.2;
    }

    /**
     * TFC {@code HorseProperties.generateSpeed}: {@code (0.45 + s1 * 0.3 + s2 * 0.3 + s3 * 0.3) * 0.25}, with the three
     * {@code nextDouble} draws passed in order.
     */
    public static double movementSpeed(double r1, double r2, double r3) {
        return (0.45 + r1 * 0.3 + r2 * 0.3 + r3 * 0.3) * 0.25;
    }

    /**
     * A fresh mount with no parents, as TFC's {@code applyGenes} fallback and {@code generateMaxHealth(getRandom::nextInt)}
     * draw them: max health first ({@code nextInt(8)}, then {@code nextInt(9)}), then jump strength, then speed.
     */
    public static MountStats random(MountRandom random) {
        final int health1 = random.nextInt(8);
        final int health2 = random.nextInt(9);
        final double jump1 = random.nextDouble();
        final double jump2 = random.nextDouble();
        final double jump3 = random.nextDouble();
        final double speed1 = random.nextDouble();
        final double speed2 = random.nextDouble();
        final double speed3 = random.nextDouble();
        return new MountStats(maxHealth(health1, health2), jumpStrength(jump1, jump2, jump3), movementSpeed(speed1, speed2, speed3));
    }

    /**
     * TFC {@code EntityHelpers.createOffspringAttribute}: the foal's value for one attribute, drawn from its two parents.
     * The parents are clamped to [min, max]. The spread is the parents' difference plus 15% of the range on each side,
     * and the centre is their average. The three draws give a bell-shaped offset in [-0.5, 0.5] of the spread.
     * A value past either bound is reflected back inside it once; TFC does not clamp again.
     *
     * @param value1 the first parent's value (TFC's male, {@code *1} genes)
     * @param value2 the second parent's value (TFC's female, {@code *2} genes)
     * @param min    the lower bound, {@code > max} is rejected
     * @param max    the upper bound
     * @param random the draws; exactly three {@code nextDouble} calls, in order
     * @throws IllegalArgumentException if {@code max <= min}
     */
    public static double inheritAttribute(double value1, double value2, double min, double max, MountRandom random) {
        if (max <= min) {
            throw new IllegalArgumentException("Incorrect range for an attribute");
        }
        final double v1 = clamp(value1, min, max);
        final double v2 = clamp(value2, min, max);
        final double wiggleRoom = 0.15 * (max - min);
        final double randomRange = Math.abs(v1 - v2) + wiggleRoom * 2.0;
        final double average = (v1 + v2) / 2.0;
        final double gaussian = (random.nextDouble() + random.nextDouble() + random.nextDouble()) / 3.0 - 0.5;
        final double newValue = average + randomRange * gaussian;
        if (newValue > max) {
            return max - (newValue - max);
        } else if (newValue < min) {
            return min + (min - newValue);
        }
        return newValue;
    }

    private static double clamp(double value, double min, double max) {
        return value < min ? min : (value > max ? max : value);
    }
}
