/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.integration;

import dev.tfp.core.weather.PrecipitationType;

/**
 * The values one player's HUD shows, as an immutable snapshot. Features publish a new snapshot from the
 * player's own thread when a value changes; HUD placeholders read the latest one from any thread
 * (BetterHud evaluates them asynchronously ~20×/s) without touching the world.
 *
 * @param health current TFC health (TFC scale: 850 max at average nutrition, see HealthDisplay)
 * @param maxHealth TFC max health
 * @param thirst 0..100
 * @param hunger 0..20 (vanilla food scale)
 * @param heldTemperature temperature of the held item in °C, 0 if none or cold
 * @param ambientTemperature the player's ambient temperature in °C (M2-CLIM-03), NaN until the first reading
 * @param precipitation what falls at the player's position (M2-WTH-01), NONE until the first decision
 */
public record HudSnapshot(double health, double maxHealth, double thirst, double hunger, double heldTemperature,
        double ambientTemperature, PrecipitationType precipitation, double air) {
    /** The top of the thirst scale: {@link #thirst} runs 0..this (TFC {@code PlayerInfo.MAX_THIRST}). */
    public static final double MAX_THIRST = 100;

    /** The top of the hunger scale: {@link #hunger} runs 0..this (vanilla food level). */
    public static final double MAX_HUNGER = 20;

    /** The top of the air scale: {@link #air} runs 0..this (vanilla maximum breath, 300 ticks). */
    public static final double MAX_AIR = 300;

    /** Shown before the first publish. */
    public static final HudSnapshot DEFAULT = new HudSnapshot(50, 50, MAX_THIRST, MAX_HUNGER, 0);

    /** A snapshot with no air reading yet (full breath). */
    public HudSnapshot(double health, double maxHealth, double thirst, double hunger, double heldTemperature,
            double ambientTemperature, PrecipitationType precipitation) {
        this(health, maxHealth, thirst, hunger, heldTemperature, ambientTemperature, precipitation, MAX_AIR);
    }

    /** A snapshot with no precipitation decision yet. */
    public HudSnapshot(double health, double maxHealth, double thirst, double hunger, double heldTemperature,
            double ambientTemperature) {
        this(health, maxHealth, thirst, hunger, heldTemperature, ambientTemperature, PrecipitationType.NONE);
    }

    /** A snapshot with no ambient temperature yet. */
    public HudSnapshot(double health, double maxHealth, double thirst, double hunger, double heldTemperature) {
        this(health, maxHealth, thirst, hunger, heldTemperature, Double.NaN);
    }

    public HudSnapshot withThirst(double value) {
        return new HudSnapshot(health, maxHealth, value, hunger, heldTemperature, ambientTemperature, precipitation, air);
    }

    public HudSnapshot withHealth(double value, double max) {
        return new HudSnapshot(value, max, thirst, hunger, heldTemperature, ambientTemperature, precipitation, air);
    }

    public HudSnapshot withHunger(double value) {
        return new HudSnapshot(health, maxHealth, thirst, value, heldTemperature, ambientTemperature, precipitation, air);
    }

    public HudSnapshot withHeldTemperature(double value) {
        return new HudSnapshot(health, maxHealth, thirst, hunger, value, ambientTemperature, precipitation, air);
    }

    public HudSnapshot withAmbientTemperature(double value) {
        return new HudSnapshot(health, maxHealth, thirst, hunger, heldTemperature, value, precipitation, air);
    }

    public HudSnapshot withPrecipitation(PrecipitationType value) {
        return new HudSnapshot(health, maxHealth, thirst, hunger, heldTemperature, ambientTemperature, value, air);
    }

    /** The breath left in ticks, clamped to 0..{@link #MAX_AIR}. */
    public HudSnapshot withAir(double value) {
        return new HudSnapshot(health, maxHealth, thirst, hunger, heldTemperature, ambientTemperature, precipitation,
                Math.clamp(value, 0, MAX_AIR));
    }
}
