/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/entities/TFCEntities.java @ c3b4d9536
 * (the living registrations: id, sized, mob category, clientTrackingRange)
 */
package dev.tfp.core.animal;

import dev.tfp.core.livestock.LivestockSpecies;
import java.util.Locale;
import java.util.Optional;

/**
 * One TFC living entity type and the vanilla mob that carries it (the BetterModel carrier). TFC's hitbox, category and
 * tracking range are kept for review and for later spawn rules; the carrier's own hitbox is what the server uses.
 *
 * @param id            TFC entity id, {@code tfc:<name>}
 * @param width         TFC {@code sized} width
 * @param height        TFC {@code sized} height
 * @param category      TFC {@code MobCategory} name
 * @param trackingRange TFC {@code clientTrackingRange} in chunks (5, the builder default, when TFC sets none)
 * @param carrier       vanilla entity type key, such as {@code minecraft:cow}
 * @param carrierWidth  the carrier's vanilla hitbox width
 * @param carrierHeight the carrier's vanilla hitbox height
 * @param flagged       a hitbox delta above {@link CarrierTable#DELTA_LIMIT}, reviewed in {@code note}
 * @param model         our BetterModel model id, or null until the species model exists (M5-ENT-02)
 * @param fallbackModel the model drawn while {@code model} is null, or null to show the carrier itself
 * @param note          why this carrier, or null
 */
public record Carrier(String id, float width, float height, String category, int trackingRange, String carrier,
        float carrierWidth, float carrierHeight, boolean flagged, String model, String fallbackModel, String note) {

    /** TFC width minus carrier width. */
    public float deltaWidth() {
        return width - carrierWidth;
    }

    /** TFC height minus carrier height. */
    public float deltaHeight() {
        return height - carrierHeight;
    }

    /** The model to attach: {@link #model}, else {@link #fallbackModel}; empty to show the carrier itself. */
    public Optional<String> modelOrFallback() {
        return Optional.ofNullable(model != null ? model : fallbackModel);
    }

    /** The part of the id after {@code tfc:}. */
    public String name() {
        return id.substring(id.indexOf(':') + 1);
    }

    /** Whether TFC's class for this species extends {@code WildAnimal} (gender and baby flag, no size). */
    public boolean wildAnimal() {
        return WildAnimalRoll.isWildAnimal(id);
    }

    /** The livestock rules of this species ({@link LivestockSpecies}); empty for TFC's wild animals. */
    public Optional<LivestockSpecies> livestock() {
        try {
            return Optional.of(LivestockSpecies.valueOf(name().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
