/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/entities/aquatic/AmphibiousAnimal.java @ c3b4d9536 (createAttributes,
 * isPlayingDeadEffective, brainProvider), aquatic/TFCTurtle.java, aquatic/Penguin.java, aquatic/LeopardSeal.java
 * (createAttributes, isPlayingDeadEffective, isFood, brainProvider), ai/amphibian/AmphibianAi.java and PinnipedAI.java
 * (getSpeedModifier, getChasingSpeedModifier, getFleeingSpeedModifier), same commit
 */
package dev.tfp.core.amphibian;

import java.util.Locale;
import java.util.Optional;

/**
 * TFC's three {@code AmphibiousAnimal}s: the turtle and the penguin run {@code AmphibianAi}, the leopard seal runs
 * {@code PinnipedAI}. Their registered attributes, whether playing dead gives the regeneration and resistance effects, the
 * food tag and the land/water speed modifiers of their brains.
 *
 * <p>The leopard seal never plays dead: {@code AmphibiousAnimal.hurt} sets {@code PLAY_DEAD_TICKS}, but {@code PinnipedAI}
 * does not register that memory, so the brain drops the write, and {@code LeopardSeal.customServerAiStep} never sets the
 * playing-dead flag. A hit seal retreats ({@link AmphibianActivity#AVOID}) instead.
 */
public enum AmphibianSpecies {
    TURTLE(Brain.AMPHIBIAN, new Attributes(14.0, 1.0, 2.0, 1.0), true, "tfc:turtle_food"),
    PENGUIN(Brain.AMPHIBIAN, new Attributes(8.0, 1.0, 2.0, 1.0), false, "tfc:penguin_food"),
    LEOPARD_SEAL(Brain.PINNIPED, new Attributes(24.0, 1.0, 6.0, 1.0), false, "tfc:seal_food");

    /** The brain: {@code AmphibianAi} (play dead, fight, idle) or {@code PinnipedAI} (avoid, fight, idle). */
    public enum Brain {
        AMPHIBIAN, PINNIPED
    }

    /**
     * The registered attributes ({@code createAttributes}).
     *
     * @param maxHealth     {@code MAX_HEALTH}
     * @param movementSpeed {@code MOVEMENT_SPEED}
     * @param attackDamage  {@code ATTACK_DAMAGE}
     * @param stepHeight    {@code STEP_HEIGHT}
     */
    public record Attributes(double maxHealth, double movementSpeed, double attackDamage, double stepHeight) {
    }

    /** {@code getSpeedModifier}: in water or a bubble column. */
    public static final float WALK_SPEED_WATER = 0.5f;
    /** {@code getSpeedModifier}: on land. */
    public static final float WALK_SPEED_LAND = 0.15f;
    /** {@code getChasingSpeedModifier}: in water. */
    public static final float CHASE_SPEED_WATER = 0.6f;
    /** {@code getChasingSpeedModifier}: on land. */
    public static final float CHASE_SPEED_LAND = 0.17f;
    /** {@code PinnipedAI.getFleeingSpeedModifier}: in water. */
    public static final float FLEE_SPEED_WATER = 0.8f;
    /** {@code PinnipedAI.getFleeingSpeedModifier}: on land. */
    public static final float FLEE_SPEED_LAND = 0.2f;

    private final Brain brain;
    private final Attributes attributes;
    private final boolean playingDeadEffective;
    private final String foodTag;

    AmphibianSpecies(Brain brain, Attributes attributes, boolean playingDeadEffective, String foodTag) {
        this.brain = brain;
        this.attributes = attributes;
        this.playingDeadEffective = playingDeadEffective;
        this.foodTag = foodTag;
    }

    /** The entity id, such as {@code tfc:leopard_seal}. */
    public String id() {
        return "tfc:" + name().toLowerCase(Locale.ROOT);
    }

    /**
     * The species of an entity id.
     *
     * @param id a namespaced id such as {@code tfc:turtle}
     * @return the species, or empty for any other id
     */
    public static Optional<AmphibianSpecies> of(String id) {
        for (AmphibianSpecies s : values()) {
            if (s.id().equals(id)) {
                return Optional.of(s);
            }
        }
        return Optional.empty();
    }

    public Brain brain() {
        return brain;
    }

    public Attributes attributes() {
        return attributes;
    }

    /** {@code isPlayingDeadEffective}: the play-dead behaviour gives regeneration and resistance (turtle only). */
    public boolean playingDeadEffective() {
        return playingDeadEffective;
    }

    /** Whether a hit can make the mob play dead: only the {@code AmphibianAi} brain registers {@code PLAY_DEAD_TICKS}. */
    public boolean playsDead() {
        return brain == Brain.AMPHIBIAN;
    }

    /** The item tag of {@code isFood} (temptation and the heal-by-feeding interaction). */
    public String foodTag() {
        return foodTag;
    }

    /** {@code getSpeedModifier}. */
    public static float walkSpeed(boolean inWaterOrBubble) {
        return inWaterOrBubble ? WALK_SPEED_WATER : WALK_SPEED_LAND;
    }

    /** {@code getChasingSpeedModifier}. */
    public static float chaseSpeed(boolean inWaterOrBubble) {
        return inWaterOrBubble ? CHASE_SPEED_WATER : CHASE_SPEED_LAND;
    }

    /** {@code PinnipedAI.getFleeingSpeedModifier}. */
    public static float fleeSpeed(boolean inWaterOrBubble) {
        return inWaterOrBubble ? FLEE_SPEED_WATER : FLEE_SPEED_LAND;
    }
}
