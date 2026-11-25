/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/entities/TFCEntities.java @ c3b4d9536 (which class each prey registration
 * uses, onEntityAttributeCreation), prey/Prey.java, prey/RammingPrey.java, prey/Pest.java, prey/TFCRabbit.java
 * (createAttributes), livestock/OviparousAnimal.java (createAttributes, used by the winged prey), same commit
 */
package dev.tfp.core.prey;

import java.util.Map;
import java.util.Optional;

/**
 * TFC's prey roster: the brain each species uses and the attributes TFC registers for it.
 */
public final class WildPrey {
    /** The TFC class, which decides the brain. */
    public enum Kind {
        /** {@code Prey} with {@code PreyAi}: deer, caribou, bongo, gazelle. */
        PREY(Avoidance.PREDATORS_PLAYERS_AND_RAMMERS, true),
        /** {@code WingedPrey} (a {@code Prey}): grouse, pheasant, turkey, peafowl. Flutters and takes no fall damage. */
        WINGED(Avoidance.PREDATORS_PLAYERS_AND_RAMMERS, true),
        /** {@code RammingPrey} with {@code RammingPreyAi}: boar, wildebeest, moose, bison. No panic, no fear of players. */
        RAMMING(Avoidance.PREDATORS_ONLY, false),
        /** {@code Pest} with {@code PestAi}: rat, lemming, mongoose, jerboa. */
        PEST(Avoidance.PREDATORS_PLAYERS_AND_RAMMERS, true);

        private final Avoidance avoidance;
        private final boolean panics;

        Kind(Avoidance avoidance, boolean panics) {
            this.avoidance = avoidance;
            this.panics = panics;
        }

        public Avoidance avoidance() {
            return avoidance;
        }

        /** Whether the core activity has {@code AnimalPanic(2.0F)} and {@code Prey.hurt} calls {@code wasHurtBy}. */
        public boolean panics() {
            return panics;
        }
    }

    /**
     * The registered attributes; {@code NaN} where TFC adds none (the carrier's own value stays).
     *
     * @param maxHealth           {@code MAX_HEALTH}
     * @param movementSpeed       {@code MOVEMENT_SPEED}
     * @param knockbackResistance {@code KNOCKBACK_RESISTANCE}
     * @param stepHeight          {@code STEP_HEIGHT}
     */
    public record Attributes(double maxHealth, double movementSpeed, double knockbackResistance, double stepHeight) {
    }

    /** {@code Prey.createAttributes()}: 10 health, 0.3 speed, step 1. */
    public static final Attributes PREY = new Attributes(10, 0.3f, Double.NaN, 1.0f);
    /** {@code OviparousAnimal.createAttributes()}: {@code Chicken.createAttributes()} (the carrier's own) plus step 1.5. */
    public static final Attributes WINGED = new Attributes(Double.NaN, Double.NaN, Double.NaN, 1.5);
    /** {@code RammingPrey.createAttributes()}: boar. */
    public static final Attributes RAMMING = new Attributes(20, 0.16f, 0.8, 1.0f);
    /** {@code RammingPrey.createMediumAttributes()}: wildebeest. */
    public static final Attributes RAMMING_MEDIUM = new Attributes(30, 0.16f, 0.9, 1.0f);
    /** {@code RammingPrey.createLargeAttributes()}: moose, bison. */
    public static final Attributes RAMMING_LARGE = new Attributes(40, 0.12f, 1.0, 1.0f);
    /** {@code Pest.createAttributes()}: 8 health, 0.3 speed. */
    public static final Attributes PEST = new Attributes(8, 0.3f, Double.NaN, Double.NaN);
    /** {@code TFCRabbit.createAttributes()}: 12 health, 0.3 speed (and attack 3, which a rabbit never uses). */
    public static final Attributes RABBIT = new Attributes(12, 0.3f, Double.NaN, Double.NaN);

    private static final Map<String, Kind> KINDS = Map.ofEntries(
        Map.entry("tfc:deer", Kind.PREY), Map.entry("tfc:caribou", Kind.PREY),
        Map.entry("tfc:bongo", Kind.PREY), Map.entry("tfc:gazelle", Kind.PREY),
        Map.entry("tfc:grouse", Kind.WINGED), Map.entry("tfc:pheasant", Kind.WINGED),
        Map.entry("tfc:turkey", Kind.WINGED), Map.entry("tfc:peafowl", Kind.WINGED),
        Map.entry("tfc:boar", Kind.RAMMING), Map.entry("tfc:wildebeest", Kind.RAMMING),
        Map.entry("tfc:moose", Kind.RAMMING), Map.entry("tfc:bison", Kind.RAMMING),
        Map.entry("tfc:rat", Kind.PEST), Map.entry("tfc:lemming", Kind.PEST),
        Map.entry("tfc:mongoose", Kind.PEST), Map.entry("tfc:jerboa", Kind.PEST));

    private WildPrey() {
    }

    /** The prey species ids. */
    public static java.util.Set<String> species() {
        return KINDS.keySet();
    }

    public static Optional<Kind> kind(String species) {
        return Optional.ofNullable(KINDS.get(species));
    }

    /** The attributes TFC registers for {@code species} (prey roster plus the rabbit). */
    public static Optional<Attributes> attributes(String species) {
        return Optional.ofNullable(switch (species) {
            case "tfc:boar" -> RAMMING;
            case "tfc:wildebeest" -> RAMMING_MEDIUM;
            case "tfc:moose", "tfc:bison" -> RAMMING_LARGE;
            case "tfc:rabbit" -> RABBIT;
            default -> kind(species).map(k -> switch (k) {
                case PREY -> PREY;
                case WINGED -> WINGED;
                case PEST -> PEST;
                case RAMMING -> RAMMING;
            }).orElse(null);
        });
    }
}
