/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/util/loot/AnimalYieldProvider.java, NotPredatedCondition.java @ c3b4d9536
 */
package dev.tfp.core.hunt;

import dev.tfp.core.worldgen.Mth;

/**
 * The facts of one kill that the animal loot rules read: who dealt the killing blow, whether a player last damaged the animal,
 * and the animal's sex, genetic size and familiarity. Immutable.
 *
 * @param killer                  the attacking entity, classified for the predator check
 * @param killedByPlayer          whether a player last damaged the animal (TFC {@code LAST_DAMAGE_PLAYER} present)
 * @param damageCountsAsPlayerKill whether the damage source carries {@code tfc:counts_as_player_kill}
 * @param male                    the animal's sex
 * @param geneticSize             the animal's genetic size, TFC {@code getGeneticSize()} (0 to 32 in practice)
 * @param familiarity             the animal's familiarity in [0, 1]
 * @param playerAttackDamage      the last-damaging player's attack damage attribute, read only when {@code killedByPlayer}
 */
public record HuntKill(
    Killer killer,
    boolean killedByPlayer,
    boolean damageCountsAsPlayerKill,
    boolean male,
    float geneticSize,
    float familiarity,
    float playerAttackDamage
) {

    /** The classes of the attacking entity that the predator check tells apart. */
    public enum Killer {
        /** No attacking entity, such as a fall or starvation. */
        NONE,
        /** A player. */
        PLAYER,
        /** An entity in the {@code tfc:land_predators} tag. */
        LAND_PREDATOR,
        /** An entity in the {@code tfc:ocean_predators} tag. */
        OCEAN_PREDATOR,
        /** Any other entity. */
        OTHER;

        /** Whether the attacker is a predator, which spoils the drops with a {@code tfc:not_predated} condition. */
        public boolean predated() {
            return this == LAND_PREDATOR || this == OCEAN_PREDATOR;
        }
    }

    /**
     * The size scale of the yield in [0, 1], TFC {@code AnimalYieldProvider}: the genetic size, plus the player's attack damage
     * mapped to 1..8 when a player killed the animal, plus the familiarity mapped to 1..8 above 0.5, then mapped from the size
     * range 1..46 to [0, 1].
     */
    public float sizeScale() {
        float adjustedSize = geneticSize;
        if (killedByPlayer) {
            adjustedSize += Mth.clampedMap(playerAttackDamage, 0f, 16f, 1f, 8f);
        }
        if (familiarity > 0.5f) {
            adjustedSize += Mth.clampedMap(familiarity, 0.5f, 1f, 1f, 8f);
        }
        return Mth.clampedMap(adjustedSize, 1f, 46f, 0f, 1f);
    }
}
