/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/player/PlayerInfo.java @ c3b4d9536 (tick (regeneration block), lazily evaluated; ForgeEventHandler.onLivingHurt)
 */
package dev.tfp.core.health;

/**
 * A player's health under TFC's regeneration, evaluated lazily from the player tick (as {@code ThirstState}). Nothing
 * ticks: {@link #advance} counts the regeneration steps in the gap ({@code tick % 10 == 0}) and applies them at once.
 *
 * <p><b>Divergence.</b> TFC re-reads food and thirst at each step. A lazy jump uses the food and thirst given to
 * {@link #advance} for the whole gap, so the two differ only when those change inside a gap. Healing stops at the max
 * health in both, because {@code heal} clamps.
 *
 * @param health   the vanilla HP, 0..max
 * @param lastTick the player tick this state was evaluated at
 */
public record HealthState(float health, long lastTick) {
    public HealthState {
        if (!(health >= 0) || Float.isInfinite(health)) {
            throw new IllegalArgumentException("health must be >= 0 and finite: " + health);
        }
    }

    /** A player at {@code maxHealth} at {@code tick}. */
    public static HealthState full(float maxHealth, long tick) {
        return new HealthState(maxHealth, tick);
    }

    /**
     * The state at player tick {@code now}. The regeneration steps are the ticks in {@code (lastTick, now]} that are
     * multiples of the interval. A step heals only while the player is alive, hurt, and fed. A {@code now} before
     * {@link #lastTick} changes nothing.
     */
    public HealthState advance(long now, float food, float thirst, float maxHealth, HealthConfig config) {
        if (now <= lastTick) {
            return this;
        }
        long interval = config.regenIntervalTicks();
        long steps = Math.floorDiv(now, interval) - Math.floorDiv(lastTick, interval);
        float next = health;
        boolean hurt = health > 0 && health < maxHealth;
        if (steps > 0 && hurt && HealthRegen.canRegenerate(food, thirst, config)) {
            next = Math.min(maxHealth, health + (float) steps * HealthRegen.healPerStep(food, thirst, config));
        }
        return new HealthState(next, now);
    }
}
