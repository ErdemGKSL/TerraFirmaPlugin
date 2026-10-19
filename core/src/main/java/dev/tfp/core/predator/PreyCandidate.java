/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/entities/ai/predator/PredatorAi.java (getAttackTarget, isPacified) @ c3b4d9536
 */
package dev.tfp.core.predator;

import dev.tfp.core.prey.Sighting;

/**
 * One visible living entity as a predator's attack choice reads it: the shared {@link Sighting} (type, player flags,
 * position) plus whether the entity stands in water, which TFC's {@code getAttackTarget} checks for prey.
 *
 * @param sighting the sighted entity
 * @param inWater  the entity is in water ({@code isInWater}, not a bubble column)
 */
public record PreyCandidate(Sighting sighting, boolean inWater) {

    /**
     * TFC's {@code NEAREST_VISIBLE_ATTACKABLE_PLAYER} candidate: a player who is not in creative or spectator mode.
     *
     * @return {@code true} if a predator may attack this sighting as a player
     */
    public boolean attackablePlayer() {
        return sighting.player() && !sighting.creativeOrSpectator();
    }

    /**
     * TFC's prey candidate: a {@code hunted_by_land_predators} type that is not in water.
     *
     * @return {@code true} if a predator may hunt this sighting as prey
     */
    public boolean huntablePrey() {
        return !inWater && HuntedPrey.isHuntedByLandPredator(sighting.type());
    }
}
