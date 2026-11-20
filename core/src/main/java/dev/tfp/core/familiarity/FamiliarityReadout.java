/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/client/IngameOverlays.java (renderFamiliarity) @ c3b4d9536
 */
package dev.tfp.core.familiarity;

import dev.tfp.core.livestock.AnimalState;
import dev.tfp.core.livestock.LivestockAge;
import dev.tfp.core.livestock.LivestockRules;
import dev.tfp.core.livestock.LivestockSpecies;
import java.util.Locale;
import java.util.Optional;

/**
 * What TFC's familiarity overlay shows for an animal: the colour band (grey below 0.3, white from 0.3 where familiarity
 * stops decaying, red for an adult at its cap), the percentage text ({@code %.2f} of familiarity times 100) and whether
 * the pregnant line shows. Pure.
 *
 * @param band        the colour band
 * @param familiarity the familiarity at the read tick, in [0, 1]
 * @param pregnant    a fertilized mammal with a pregnancy day
 */
public record FamiliarityReadout(Band band, float familiarity, boolean pregnant) {
    public enum Band {
        GRAY,
        WHITE,
        RED
    }

    /** TFC's threshold: from here familiarity stops decaying ({@code familiarityDecayLimit}). */
    public static final float STABLE = LivestockRules.FAMILIARITY_DECAY_LIMIT;

    /** The readout of an animal, or empty for a species with no familiarity cap (TFC checks {@code getAdultFamiliarityCap() > 0}). */
    public static Optional<FamiliarityReadout> of(AnimalState state, LivestockSpecies species, long playerTicks) {
        if (!(species.familiarityCap() > 0f)) {
            return Optional.empty();
        }
        float f = Math.max(0f, Math.min(1f, LivestockRules.familiarityAt(state, playerTicks)));
        LivestockAge age = LivestockRules.ageAt(state, species, playerTicks);
        Band band;
        if (f >= species.familiarityCap() && age != LivestockAge.CHILD) {
            band = Band.RED;
        } else if (f >= STABLE) {
            band = Band.WHITE;
        } else {
            band = Band.GRAY;
        }
        boolean pregnant = species.kind() == LivestockSpecies.Kind.MAMMAL && state.fertilized() && state.pregnantDay() > 0;
        return Optional.of(new FamiliarityReadout(band, f, pregnant));
    }

    /** TFC's percentage text: {@code String.format("%.2f", familiarity * 100)}. */
    public String percentText() {
        return String.format(Locale.ROOT, "%.2f", familiarity * 100f);
    }
}
