/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/util/loot/NotPredatedCondition.java, IsMaleCondition.java and the loot conditions of
 * src/generated/resources/data/tfc/loot_table/entities/*.json @ c3b4d9536
 */
package dev.tfp.core.hunt;

import java.util.function.DoubleSupplier;

/**
 * One loot condition of an animal drop: TFC's {@code tfc:not_predated}, {@code tfc:is_male}, {@code minecraft:killed_by_player},
 * the {@code any_of} of killed-by-player or a counts-as-player-kill damage source, and {@code minecraft:random_chance}.
 *
 * <p>Only {@link Kind#RANDOM_CHANCE} reads the random source: it draws once and passes when the draw is below {@link #chance()}.
 *
 * @param kind   which condition this is
 * @param chance the probability of a {@link Kind#RANDOM_CHANCE} condition, 0 otherwise
 */
public record HuntCondition(Kind kind, float chance) {

    /** The drop is not taken by a land or ocean predator; a player, another entity or no attacker is fine. */
    public static final HuntCondition NOT_PREDATED = new HuntCondition(Kind.NOT_PREDATED, 0f);
    /** The animal is male (TFC {@code TFCAnimalProperties.isMale}). */
    public static final HuntCondition IS_MALE = new HuntCondition(Kind.IS_MALE, 0f);
    /** A player was the last to damage the animal. */
    public static final HuntCondition KILLED_BY_PLAYER = new HuntCondition(Kind.KILLED_BY_PLAYER, 0f);
    /** A player killed the animal, or the killing damage source counts as a player kill ({@code tfc:counts_as_player_kill}). */
    public static final HuntCondition PLAYER_KILL = new HuntCondition(Kind.PLAYER_KILL, 0f);

    /** The condition kinds, named as the TFC loot condition they port. */
    public enum Kind {
        NOT_PREDATED,
        IS_MALE,
        KILLED_BY_PLAYER,
        PLAYER_KILL,
        RANDOM_CHANCE
    }

    /**
     * A {@code minecraft:random_chance} condition.
     *
     * @param chance the probability in [0, 1] that the condition passes
     */
    public static HuntCondition randomChance(float chance) {
        return new HuntCondition(Kind.RANDOM_CHANCE, chance);
    }

    /**
     * Tests this condition for one kill.
     *
     * @param kill the kill and the animal's state
     * @param rng  a source of uniform draws in [0, 1), read once by {@link Kind#RANDOM_CHANCE} only
     * @return whether the condition passes
     */
    public boolean test(HuntKill kill, DoubleSupplier rng) {
        return switch (kind) {
            case NOT_PREDATED -> !kill.killer().predated();
            case IS_MALE -> kill.male();
            case KILLED_BY_PLAYER -> kill.killedByPlayer();
            case PLAYER_KILL -> kill.killedByPlayer() || kill.damageCountsAsPlayerKill();
            case RANDOM_CHANCE -> rng.getAsDouble() < chance;
        };
    }
}
