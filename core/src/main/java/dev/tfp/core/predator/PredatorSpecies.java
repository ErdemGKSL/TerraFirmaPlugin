/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/entities/predator/Predator.java @ c3b4d9536 (createAttributes,
 * createBearAttributes, createBear), predator/FelinePredator.java (createAttributes, createCougar/Lion/Sabertooth/Tiger),
 * predator/AmphibiousPredator.java (createAttributes, createCrocodile), ai/predator/PackPredator.java (createWolf,
 * createHyena, createDirewolf), TFCEntities.java (the predator registrations and their attribute suppliers), same commit
 */
package dev.tfp.core.predator;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * TFC's 12 predators: which brain family each one uses, whether it hunts by day, its registered attributes, its sound id
 * and the pack flags. Immutable, thread-safe.
 */
public final class PredatorSpecies {

    /** The brain family: {@code PredatorAi}, {@code FelinePredator} (same brain, climbing), {@code AmphibiousPredatorAi}, {@code PackPredatorAi}. */
    public enum Kind {
        BEAR, FELINE, AMPHIBIOUS, PACK
    }

    /**
     * The attribute values TFC registers. {@code 0.3F} speed is kept as the float literal widened to double, as vanilla
     * stores it.
     */
    public record Attributes(double maxHealth, double movementSpeed, double attackKnockback, double attackDamage,
                             double knockbackResistance, double stepHeight) {
    }

    /**
     * One predator.
     *
     * @param id       the species id ({@code tfc:wolf})
     * @param kind     the brain family
     * @param diurnal  hunts by day (bears) instead of by night
     * @param attributes the registered attributes
     * @param sound    the TFC sound entity id ({@code entity.<sound>.attack})
     * @param tamable  a pack predator that can be fed to a dog (the wolf)
     * @param howling  a pack predator that howls at night (wolf, direwolf)
     */
    public record Profile(String id, Kind kind, boolean diurnal, Attributes attributes, String sound, boolean tamable,
                          boolean howling) {
        public boolean pack() {
            return kind == Kind.PACK;
        }
    }

    /** {@code Predator.createAttributes}: 40 health, 0.3 speed, knockback 1, damage 6, step 1. */
    public static final Attributes PREDATOR = new Attributes(40, 0.3f, 1, 6, 0, 1.0f);
    /** {@code Predator.createBearAttributes}: as {@link #PREDATOR} with knockback resistance 0.8. */
    public static final Attributes BEAR = new Attributes(40, 0.3f, 1, 6, 0.8, 1.0f);
    /** {@code FelinePredator.createAttributes}: 30 health, damage 7, knockback resistance 0.25. */
    public static final Attributes FELINE = new Attributes(30, 0.3f, 1, 7, 0.25, 1.0f);
    /** {@code AmphibiousPredator.createAttributes}: knockback 0, knockback resistance 0.90. */
    public static final Attributes AMPHIBIOUS = new Attributes(40, 0.3f, 0, 6, 0.90, 1.0f);

    private static final Map<String, Profile> PROFILES = Map.ofEntries(
        bear("tfc:polar_bear"), bear("tfc:grizzly_bear"), bear("tfc:black_bear"),
        feline("tfc:cougar", "cougar"), feline("tfc:panther", "cougar"), feline("tfc:lion", "lion"),
        feline("tfc:sabertooth", "sabertooth"), feline("tfc:tiger", "tiger"),
        Map.entry("tfc:crocodile", new Profile("tfc:crocodile", Kind.AMPHIBIOUS, false, AMPHIBIOUS, "crocodile", false, false)),
        // createWolf(TFC_WOLF, tamable, howling), createHyena(HYENA, false, false), createDirewolf(DOG, false, howling)
        Map.entry("tfc:wolf", new Profile("tfc:wolf", Kind.PACK, false, PREDATOR, "tfc_wolf", true, true)),
        Map.entry("tfc:hyena", new Profile("tfc:hyena", Kind.PACK, false, PREDATOR, "hyena", false, false)),
        Map.entry("tfc:direwolf", new Profile("tfc:direwolf", Kind.PACK, false, PREDATOR, "dog", false, true))
    );

    private PredatorSpecies() {
    }

    private static Map.Entry<String, Profile> bear(String id) {
        // createBear: diurnal, TFCSounds.BEAR
        return Map.entry(id, new Profile(id, Kind.BEAR, true, BEAR, "bear", false, false));
    }

    private static Map.Entry<String, Profile> feline(String id, String sound) {
        // new FelinePredator(type, level, false, 36, 0.8, 1.5, sounds): nocturnal
        return Map.entry(id, new Profile(id, Kind.FELINE, false, FELINE, sound, false, false));
    }

    /** The 12 predator ids. */
    public static Set<String> ids() {
        return PROFILES.keySet();
    }

    /** The profile of {@code id}, if it is a predator. */
    public static Optional<Profile> profile(String id) {
        return Optional.ofNullable(PROFILES.get(id));
    }
}
