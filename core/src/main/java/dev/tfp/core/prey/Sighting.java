/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.prey;

/**
 * One living entity a prey animal can see, reduced to the facts the flee rules read. The glue fills the flags from the
 * entity: {@code player} is {@code instanceof Player}, {@code creativeOrSpectator} is TFC's
 * {@code EntitySelector.NO_CREATIVE_OR_SPECTATOR} failing, {@code landPredator} is membership of the
 * {@code tfc:land_predators} entity tag, and {@code rammingAnimal} is membership of {@code tfc:ramming_animals}.
 *
 * @param type               the entity type key (for example {@code tfc:boar}); two sightings are the same kind when the keys are equal
 * @param player             the entity is a player
 * @param creativeOrSpectator the player is in creative or spectator mode (never avoided)
 * @param landPredator       the entity type is in {@code tfc:land_predators}
 * @param rammingAnimal      the entity type is in {@code tfc:ramming_animals}
 * @param at                 the entity's current position
 */
public record Sighting(
    String type,
    boolean player,
    boolean creativeOrSpectator,
    boolean landPredator,
    boolean rammingAnimal,
    Position at
) {
    /**
     * TFC's {@code AvoidPredatorsBehavior.isAvoided(false)} OR {@code AvoidPredatorAndRammersBehavior}'s extra clause:
     * a non-creative player or a land predator, or a non-player ramming animal. Prey use the {@code playersExempt = false}
     * form, so players are avoided.
     *
     * @return {@code true} if this sighting starts a flee
     */
    public boolean avoided() {
        return (!creativeOrSpectator && (player || landPredator)) || (!player && rammingAnimal);
    }
}
