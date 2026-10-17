/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.integration;

/**
 * The values one player's HUD shows, as an immutable snapshot. Features publish a new snapshot from the
 * player's own thread when a value changes; HUD placeholders read the latest one from any thread
 * (BetterHud evaluates them asynchronously ~20×/s) without touching the world.
 *
 * @param health current TFC health (TFC scale, e.g. 45 of 50)
 * @param maxHealth TFC max health
 * @param thirst 0..100
 * @param hunger 0..20 (vanilla food scale)
 * @param heldTemperature temperature of the held item in °C, 0 if none or cold
 * @param ambientTemperature the player's ambient temperature in °C (M2-CLIM-03), NaN until the first reading
 */
public record HudSnapshot(double health, double maxHealth, double thirst, double hunger, double heldTemperature,
        double ambientTemperature) {
    /** Shown before the first publish. */
    public static final HudSnapshot DEFAULT = new HudSnapshot(50, 50, 100, 20, 0);

    /** A snapshot with no ambient temperature yet. */
    public HudSnapshot(double health, double maxHealth, double thirst, double hunger, double heldTemperature) {
        this(health, maxHealth, thirst, hunger, heldTemperature, Double.NaN);
    }

    public HudSnapshot withThirst(double value) {
        return new HudSnapshot(health, maxHealth, value, hunger, heldTemperature, ambientTemperature);
    }

    public HudSnapshot withHealth(double value, double max) {
        return new HudSnapshot(value, max, thirst, hunger, heldTemperature, ambientTemperature);
    }

    public HudSnapshot withHunger(double value) {
        return new HudSnapshot(health, maxHealth, thirst, value, heldTemperature, ambientTemperature);
    }

    public HudSnapshot withHeldTemperature(double value) {
        return new HudSnapshot(health, maxHealth, thirst, hunger, value, ambientTemperature);
    }

    public HudSnapshot withAmbientTemperature(double value) {
        return new HudSnapshot(health, maxHealth, thirst, hunger, heldTemperature, value);
    }
}
