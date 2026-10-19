/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/entities/livestock/horse/HorseProperties.java (TAMED_FAMILIARITY,
 * OLD_AGE_MODIFIER, tickAnimalData, mobInteract's baby check), src/main/java/net/dries007/tfc/common/entities/livestock/
 * camel/DromedaryCamel.java (createAttributes, isTamed) @ c3b4d9536
 */
package dev.tfp.core.mount;

import dev.tfp.core.livestock.LivestockAge;

/**
 * TFC's mount rules that depend only on familiarity, age and the camel's fixed attributes. Mounting is not gated by
 * familiarity: a non-baby may be mounted at any familiarity. Familiarity only decides whether a horse-family animal is
 * tamed, which gates breeding and the owner.
 */
public final class MountRules {

    /** TFC {@code HorseProperties.TAMED_FAMILIARITY}: a horse-family animal is tamed above this familiarity. */
    public static final float TAMED_FAMILIARITY = 0.15f;

    /**
     * TFC {@code HorseProperties.OLD_AGE_MODIFIER}: an {@code ADD_MULTIPLIED_TOTAL} modifier of -0.5 on the movement
     * speed of an old horse-family mount.
     */
    public static final double OLD_AGE_SPEED_MODIFIER = -0.5;

    /** TFC {@code DromedaryCamel.createAttributes}: max health, set over the vanilla horse base. */
    public static final double DROMEDARY_MAX_HEALTH = 32.0;

    /** TFC {@code DromedaryCamel.createAttributes}: movement speed (a float literal, widened). */
    public static final double DROMEDARY_MOVEMENT_SPEED = 0.1f;

    /** TFC {@code DromedaryCamel.createAttributes}: jump strength (a float literal, widened). */
    public static final double DROMEDARY_JUMP_STRENGTH = 0.42f;

    /** TFC {@code DromedaryCamel.createAttributes}: step height. */
    public static final double DROMEDARY_STEP_HEIGHT = 1.5;

    private MountRules() {
    }

    /** TFC {@code HorseProperties.isTamed}-style check: familiarity strictly above {@link #TAMED_FAMILIARITY}. */
    public static boolean isTamed(float familiarity) {
        return familiarity > TAMED_FAMILIARITY;
    }

    /**
     * TFC {@code mobInteract}: a baby (age {@code CHILD}) is not mounted, and an adult or old animal is.
     */
    public static boolean canMount(LivestockAge age) {
        return age != LivestockAge.CHILD;
    }

    /**
     * The movement speed a horse-family mount has at {@code age}: the base speed, halved once it is old (TFC applies the
     * -0.5 {@code ADD_MULTIPLIED_TOTAL} modifier from {@code tickAnimalData}). Other ages keep the base.
     */
    public static double movementSpeedAt(double baseSpeed, LivestockAge age) {
        return age == LivestockAge.OLD ? baseSpeed * (1.0 + OLD_AGE_SPEED_MODIFIER) : baseSpeed;
    }
}
