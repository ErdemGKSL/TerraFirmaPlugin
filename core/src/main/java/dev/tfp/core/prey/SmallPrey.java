/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/entities/ai/TFCAvoidEntityGoal.java @ c3b4d9536 (canUse), prey/TFCFox.java
 * (registerGoals), prey/TFCRabbit.java (registerGoals), prey/TFCPanda.java (PandaAvoidGoal), prey/TFCArmadillo.java
 * (isScaredBy, hurt), prey/TFCArmadilloAi.java (initRetreatActivity), ai/prey/ScareSensor.java (defaults), same commit
 */
package dev.tfp.core.prey;

import java.util.List;
import java.util.Optional;

/**
 * The fox, rabbit, panda and armadillo: vanilla carriers that keep their own AI, with TFC's changes.
 *
 * <p>Fox, rabbit and panda get {@code TFCAvoidEntityGoal}: they run from the nearest land predator within 8 blocks,
 * unless that predator stands still or sneaks out of water; at the far speed beyond 7 blocks, the near speed closer.
 * The armadillo rolls up when a land predator that is awake comes within 7 blocks across and 2 up or down, and flees from
 * land predators and rammers at speed 2.
 */
public final class SmallPrey {
    /** {@code TFCAvoidEntityGoal(..., 8f, ...)} for all three. */
    public static final double AVOID_RANGE = 8.0;
    /** {@code AvoidEntityGoal}: inflate the box by 3 up and down. */
    public static final double AVOID_VERTICAL = 3.0;
    /** {@code AvoidEntityGoal.tick}: the near speed applies under 7 blocks ({@code distanceToSqr < 49}). */
    public static final double NEAR_SQ = 49.0;
    /** {@code TFCAvoidEntityGoal.canUse}: a threat moving slower than this (and not in water) is ignored. */
    public static final double STILL_SPEED = 0.01;
    /** {@code DefaultRandomPos.getPosAway(mob, 16, 7, ...)}. */
    public static final int AWAY_HORIZONTAL = 16;
    public static final int AWAY_VERTICAL = 7;

    /** {@code ScareSensor()} defaults: 7 across, 2 up or down, 80 ticks of memory. */
    public static final int SCARE_HORIZONTAL = 7;
    public static final int SCARE_VERTICAL = 2;
    public static final int SCARE_MEMORY_TICKS = 80;

    /**
     * TFC's {@code TFCArmadillo.hurt} takes 1 before vanilla's {@code (amount - 1) / 2}: in vanilla terms, half a point
     * less than the vanilla result while scared.
     */
    public static final float ARMADILLO_EXTRA_REDUCTION = 0.5f;

    /** The far and near speeds of one species' avoid goal. */
    public record Speeds(double far, double near) {
        /** {@code AvoidEntityGoal.tick}: near speed under 7 blocks. */
        public double at(double distanceSq) {
            return distanceSq < NEAR_SQ ? near : far;
        }
    }

    /** {@code TFCFox}: 1.6 far, 1.4 near (TFC passes them in this order). */
    public static final Speeds FOX = new Speeds(1.6, 1.4);
    /** {@code TFCRabbit}: 2.2 both. */
    public static final Speeds RABBIT = new Speeds(2.2, 2.2);
    /** {@code PandaAvoidGoal}: 2 both, only while worried and able to act. */
    public static final Speeds PANDA = new Speeds(2.0, 2.0);

    /**
     * One land predator near a small prey animal.
     *
     * @param at         its position
     * @param speed      the length of its motion vector
     * @param sneaking   it steps carefully ({@code isSteppingCarefully})
     * @param inWater    it is in water
     * @param sleeping   it sleeps (only the armadillo reads this)
     */
    public record Threat(Position at, double speed, boolean sneaking, boolean inWater, boolean sleeping) {
    }

    private SmallPrey() {
    }

    /** Fox, rabbit and panda speeds by species id. */
    public static Optional<Speeds> avoidSpeeds(String species) {
        return Optional.ofNullable(switch (species) {
            case "tfc:fox" -> FOX;
            case "tfc:rabbit" -> RABBIT;
            case "tfc:panda" -> PANDA;
            default -> null;
        });
    }

    /**
     * {@code TFCAvoidEntityGoal.canUse} up to the path: the nearest land predator within {@link #AVOID_RANGE}
     * (three-dimensional, {@code TargetingConditions.range}), then no flight if it stands still or sneaks out of water.
     *
     * @param self       the animal's position
     * @param predators  the land predators in the inflated box
     * @return the threat to run from, or empty
     */
    public static Optional<Threat> avoid(Position self, List<Threat> predators) {
        Threat best = null;
        double bestSq = Double.POSITIVE_INFINITY;
        for (Threat t : predators) {
            final double d = self.distanceSq(t.at());
            if (d <= AVOID_RANGE * AVOID_RANGE && d < bestSq) {
                best = t;
                bestSq = d;
            }
        }
        if (best == null || ((best.sneaking() || best.speed() < STILL_SPEED) && !best.inWater())) {
            return Optional.empty();
        }
        return Optional.of(best);
    }

    /**
     * {@code ScareSensor.checkForMobsNearby} with {@code TFCArmadillo.isScaredBy}: an awake land predator within 7 blocks
     * across and 2 up or down ({@code closerThan(e, 7, 2)}: horizontal squared distance below 49 and vertical below 2).
     *
     * @param self      the armadillo
     * @param predators the land predators near it
     * @return {@code true} to set {@code DANGER_DETECTED_RECENTLY} for 80 ticks
     */
    public static boolean armadilloScared(Position self, List<Threat> predators) {
        for (Threat t : predators) {
            final double dx = t.at().x() - self.x();
            final double dz = t.at().z() - self.z();
            if (!t.sleeping() && dx * dx + dz * dz < SCARE_HORIZONTAL * SCARE_HORIZONTAL
                && Math.abs(t.at().y() - self.y()) < SCARE_VERTICAL) {
                return true;
            }
        }
        return false;
    }

    /**
     * The armadillo's damage while scared, from the vanilla-reduced amount: TFC's {@code (a - 1 - 1) / 2} is vanilla's
     * {@code (a - 1) / 2} minus a half.
     *
     * @param vanillaReduced the damage after vanilla's own reduction
     * @return the TFC damage, never below zero
     */
    public static double armadilloDamage(double vanillaReduced) {
        return Math.max(0.0, vanillaReduced - ARMADILLO_EXTRA_REDUCTION);
    }
}
