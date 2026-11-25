/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/entities/ai/prey/AvoidPredatorsBehavior.java @ c3b4d9536 (isAvoided), and
 * ai/prey/AvoidPredatorAndRammersBehavior.java (create), PreyAi.java, RammingPreyAi.java, PestAi.java,
 * prey/TFCArmadilloAi.java (which form each brain uses), same commit
 */
package dev.tfp.core.prey;

/**
 * Which sightings start a flee, one value per TFC brain that has an {@code AVOID} activity.
 *
 * <ul>
 *   <li>{@link #PREDATORS_PLAYERS_AND_RAMMERS}: {@code AvoidPredatorAndRammersBehavior.create(false)}, used by
 *       {@code PreyAi} (deer family, game birds) and {@code PestAi} (rat, lemming, mongoose, jerboa).</li>
 *   <li>{@link #PREDATORS_ONLY}: {@code AvoidPredatorsBehavior.create(true)}, used by {@code RammingPreyAi} (boar, moose,
 *       wildebeest, bison): players are exempt, so rammers do not fear players (they ram them instead).</li>
 *   <li>{@link #PREDATORS_AND_RAMMERS}: {@code AvoidPredatorAndRammersBehavior.create(true)}, used by
 *       {@code TFCArmadilloAi}.</li>
 * </ul>
 */
public enum Avoidance {
    PREDATORS_PLAYERS_AND_RAMMERS(false, true),
    PREDATORS_ONLY(true, false),
    PREDATORS_AND_RAMMERS(true, true);

    private final boolean playersExempt;
    private final boolean rammers;

    Avoidance(boolean playersExempt, boolean rammers) {
        this.playersExempt = playersExempt;
        this.rammers = rammers;
    }

    /**
     * TFC's predicate for one sighting.
     *
     * <p>{@code isAvoided(true)}: {@code !(entity instanceof Player) && LAND_PREDATORS}. {@code isAvoided(false)}:
     * {@code NO_CREATIVE_OR_SPECTATOR && (Player || LAND_PREDATORS)}. The rammer clause adds
     * {@code !(e instanceof Player) && RAMMING_ANIMALS}.
     *
     * @param s the sighting
     * @return {@code true} if {@code s} starts a flee
     */
    public boolean avoids(Sighting s) {
        final boolean base = playersExempt
            ? !s.player() && s.landPredator()
            : !s.creativeOrSpectator() && (s.player() || s.landPredator());
        return base || (rammers && !s.player() && s.rammingAnimal());
    }
}
