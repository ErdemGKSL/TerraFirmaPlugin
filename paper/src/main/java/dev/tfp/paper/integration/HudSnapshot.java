/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.integration;

import dev.tfp.core.heat.HeatConfig;
import dev.tfp.core.heat.HeatState;
import dev.tfp.core.weather.PrecipitationType;
import java.util.Objects;
import java.util.function.LongSupplier;

/**
 * The values one player's HUD shows, as an immutable snapshot. Features publish a new snapshot from the
 * player's own thread when a value changes; HUD placeholders read the latest one from any thread
 * (BetterHud evaluates them asynchronously ~20×/s) without touching the world.
 *
 * @param health current TFC health (TFC scale: 850 max at average nutrition, see HealthDisplay)
 * @param maxHealth TFC max health
 * @param thirst 0..100
 * @param hunger 0..20 (vanilla food scale)
 * @param heldHeat the held item's stored heat record (PAR-HUD-11); {@link #heldTemperature()} evaluates it lazily at
 *                 read time, so the value follows the calendar between publishes
 * @param ambientTemperature the player's ambient temperature in °C (M2-CLIM-03), NaN until the first reading
 * @param precipitation what falls at the player's position (M2-WTH-01), NONE until the first decision
 * @param air breath left in ticks, 0..{@link #MAX_AIR}
 * @param armor the player's armour points (vanilla {@code Attribute.ARMOR}, 0..{@link #ARMOR_CAP}); TFC keeps vanilla's
 *              armour attribute and its 20-point display scale ({@link #MAX_ARMOR})
 * @param needsActive PAR-HUD-12: the player's game mode has TFC's needs (survival and adventure); false in creative and
 *                    spectator, where the bars and the held-item popup are hidden (TFC {@code IngameOverlays
 *                    .setupForSurvival}: {@code gameMode.canHurtPlayer()}) and the needs are paused
 * @param northernHemisphere PAR-HUD-16: whether the player stands in the northern hemisphere (TFC
 *                    {@code ClientHelpers.inNorthernHemisphere}); the season text shows the opposite month's name in the
 *                    south. True until the first reading and outside the overworld ({@code HudHemisphere})
 * @param barStates PAR-HUD-19: absorption, the yellow surplus bar, the health glow and the thirst overheat tint (TFC
 *                  {@code IngameOverlays.renderHealthBar} / {@code renderThirst}); {@link BarStates#NONE} until the first
 *                  reading
 */
public record HudSnapshot(double health, double maxHealth, double thirst, double hunger, HeldHeat heldHeat,
        double ambientTemperature, PrecipitationType precipitation, double air, double armor, boolean needsActive,
        boolean northernHemisphere, BarStates barStates) {
    public HudSnapshot {
        Objects.requireNonNull(heldHeat, "heldHeat");
        Objects.requireNonNull(barStates, "barStates");
    }

    /** A snapshot without bar states yet ({@link BarStates#NONE}). */
    public HudSnapshot(double health, double maxHealth, double thirst, double hunger, HeldHeat heldHeat,
            double ambientTemperature, PrecipitationType precipitation, double air, double armor, boolean needsActive,
            boolean northernHemisphere) {
        this(health, maxHealth, thirst, hunger, heldHeat, ambientTemperature, precipitation, air, armor, needsActive,
                northernHemisphere, BarStates.NONE);
    }

    /**
     * PAR-HUD-19: the extra states of the health and thirst bars, all published from the player's thread
     * ({@code PlayerNeedsHudService}). TFC {@code IngameOverlays.renderHealthBar}: the fill and the text use
     * {@code health + absorption}, a yellow surplus bar fills by {@code clamp(percent + absorption / 20 - 1, 0, 1)}, and a
     * highlight overlay is drawn while {@code (saturation > 0 && hurt) || hurtTime > 0 || Regeneration}; {@code
     * renderThirst} tints the bar at alpha {@code getThirstContributionFromTemperature() / MAX_TEMPERATURE_THIRST_DECAY}.
     *
     * @param absorption     the absorption on the shown (TFC x 50) scale, 0 or more
     * @param surplus        the yellow surplus bar's fill, 0..1 (TFC's {@code surplusPercent})
     * @param steadyGlow     the part of the glow condition that holds until the next reading: hurt with saturation, or
     *                       under Regeneration
     * @param hurtUntilNanos the {@link System#nanoTime()} at which the last damage's {@code hurtTime} (10 ticks) ends; 0
     *                       when never hurt. Read against the clock at placeholder time ({@link #healthGlow(long)}), so the
     *                       glow ends with no further publish
     * @param thirstOverheat the overheat tint level, 0 (none) .. the number of pre-tinted frames (TFC's alpha, stepped)
     */
    public record BarStates(double absorption, double surplus, boolean steadyGlow, long hurtUntilNanos,
            int thirstOverheat) {
        /** No absorption, no glow, no tint. */
        public static final BarStates NONE = new BarStates(0, 0, false, 0L, 0);

        /** The same states with the hurt time ending at {@code nanos} ({@link System#nanoTime()} scale). */
        public BarStates withHurtUntil(long nanos) {
            return new BarStates(absorption, surplus, steadyGlow, nanos, thirstOverheat);
        }

        /** Whether the hurt time of the last damage still runs at {@code nanos}. */
        public boolean hurtAt(long nanos) {
            return hurtUntilNanos != 0L && nanos - hurtUntilNanos < 0;
        }
    }

    /** PAR-HUD-19: whether the health bar glows at {@code nanos} ({@link System#nanoTime()}): TFC's highlight condition. */
    public boolean healthGlow(long nanos) {
        return barStates.steadyGlow() || barStates.hurtAt(nanos);
    }

    /** PAR-HUD-19: the shown current health, TFC's {@code health + absorption} on the x 50 scale (fill and text). */
    public double healthWithAbsorption() {
        return health + barStates.absorption();
    }

    /** A snapshot of a player in the northern hemisphere (TFC's default, and the hemisphere at z = 0). */
    public HudSnapshot(double health, double maxHealth, double thirst, double hunger, HeldHeat heldHeat,
            double ambientTemperature, PrecipitationType precipitation, double air, double armor, boolean needsActive) {
        this(health, maxHealth, thirst, hunger, heldHeat, ambientTemperature, precipitation, air, armor, needsActive, true);
    }

    /** A snapshot of a player whose game mode has needs (survival or adventure). */
    public HudSnapshot(double health, double maxHealth, double thirst, double hunger, HeldHeat heldHeat,
            double ambientTemperature, PrecipitationType precipitation, double air, double armor) {
        this(health, maxHealth, thirst, hunger, heldHeat, ambientTemperature, precipitation, air, armor, true);
    }

    /**
     * PAR-HUD-11: the held stack's heat record, published from the player's thread whenever the main hand or its heat
     * record changes. The temperature is not precomputed: {@link #temperatureNow} applies TFC's passive cooling rule
     * ({@link HeatState#adjust}, the same as {@code ItemHeatHooks.temperature}) at the calendar tick of the read, so a
     * placeholder evaluated on BetterHud's thread shows the temperature at that moment without touching the ItemStack.
     *
     * @param temperature the stored temperature in °C at {@code stampTick}
     * @param stampTick   the calendar (player) tick the temperature was recorded at
     * @param capacity    the stack's heat capacity; 0 or less means the item cannot be heated (reads as 0 °C), infinite
     *                    means it never cools
     * @param config      the cooling rule's config
     * @param now         the calendar player tick, readable from any thread ({@code ClockSnapshot.playerTicks})
     */
    public record HeldHeat(float temperature, long stampTick, float capacity, HeatConfig config, LongSupplier now) {
        private static final LongSupplier NO_CLOCK = () -> 0L;

        /** Nothing held, or a stack without a heat record or heat data. */
        public static final HeldHeat NONE = new HeldHeat(0f, 0L, 0f, HeatConfig.DEFAULT, NO_CLOCK);

        public HeldHeat {
            Objects.requireNonNull(config, "config");
            Objects.requireNonNull(now, "now");
        }

        /** A temperature that never changes (infinite capacity): debug values and tests. */
        public static HeldHeat fixed(double temperature) {
            return new HeldHeat((float) temperature, 0L, Float.POSITIVE_INFINITY, HeatConfig.DEFAULT, NO_CLOCK);
        }

        /** The stored record of a heatable stack with {@code capacity}, evaluated against {@code now}. */
        public static HeldHeat of(HeatState state, float capacity, HeatConfig config, LongSupplier now) {
            return new HeldHeat(state.temperature(), state.lastTick(), capacity, config, now);
        }

        /** The temperature at calendar tick {@code tick} in °C: cooled from the stamp; 0 when the item cannot be heated. */
        public float temperatureAt(long tick) {
            if (!(capacity > 0f) || !(temperature > 0f)) {
                return 0f;
            }
            return HeatState.adjust(temperature, capacity, tick - stampTick, config);
        }

        /** The temperature now (one read of {@link #now}). */
        public float temperatureNow() {
            return temperatureAt(now.getAsLong());
        }
    }

    /** The top of the thirst scale: {@link #thirst} runs 0..this (TFC {@code PlayerInfo.MAX_THIRST}). */
    public static final double MAX_THIRST = 100;

    /** The top of the hunger scale: {@link #hunger} runs 0..this (vanilla food level). */
    public static final double MAX_HUNGER = 20;

    /** The top of the air scale: {@link #air} runs 0..this (vanilla maximum breath, 300 ticks). */
    public static final double MAX_AIR = 300;

    /** The top of the armour bar: vanilla's armour row shows 20 points (10 icons); more fills the bar completely. */
    public static final double MAX_ARMOR = 20;

    /** The top of the stored armour value: vanilla's {@code generic.armor} attribute maximum. */
    public static final double ARMOR_CAP = 30;

    /** Shown before the first publish. */
    public static final HudSnapshot DEFAULT = new HudSnapshot(50, 50, MAX_THIRST, MAX_HUNGER, 0);

    /** A snapshot with a fixed held temperature ({@link HeldHeat#fixed}). */
    public HudSnapshot(double health, double maxHealth, double thirst, double hunger, double heldTemperature,
            double ambientTemperature, PrecipitationType precipitation, double air, double armor) {
        this(health, maxHealth, thirst, hunger, HeldHeat.fixed(heldTemperature), ambientTemperature, precipitation, air,
                armor);
    }

    /**
     * The held item's temperature in °C evaluated now from {@link #heldHeat} (PAR-HUD-11), or 0 when it is not above
     * 0 °C (TFC {@code ItemTemperatureDisplayStyle.format} shows only positive temperatures; the popup condition
     * {@code tfp_held_temp > 0} then hides it). Any thread.
     */
    public double heldTemperature() {
        float t = heldHeat.temperatureNow();
        return t > 0f ? t : 0;
    }

    /** A snapshot with no armour reading yet (no armour). */
    public HudSnapshot(double health, double maxHealth, double thirst, double hunger, double heldTemperature,
            double ambientTemperature, PrecipitationType precipitation, double air) {
        this(health, maxHealth, thirst, hunger, heldTemperature, ambientTemperature, precipitation, air, 0);
    }

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
        return new HudSnapshot(health, maxHealth, value, hunger, heldHeat, ambientTemperature,
                precipitation, air, armor, needsActive, northernHemisphere, barStates);
    }

    public HudSnapshot withHealth(double value, double max) {
        return new HudSnapshot(value, max, thirst, hunger, heldHeat, ambientTemperature,
                precipitation, air, armor, needsActive, northernHemisphere, barStates);
    }

    public HudSnapshot withHunger(double value) {
        return new HudSnapshot(health, maxHealth, thirst, value, heldHeat, ambientTemperature,
                precipitation, air, armor, needsActive, northernHemisphere, barStates);
    }

    /** A fixed held temperature ({@link HeldHeat#fixed}). */
    public HudSnapshot withHeldTemperature(double value) {
        return withHeldHeat(HeldHeat.fixed(value));
    }

    /** The held stack's heat record, evaluated at each read ({@link #heldTemperature()}). */
    public HudSnapshot withHeldHeat(HeldHeat value) {
        return new HudSnapshot(health, maxHealth, thirst, hunger, value, ambientTemperature, precipitation, air, armor,
                needsActive, northernHemisphere, barStates);
    }

    public HudSnapshot withAmbientTemperature(double value) {
        return new HudSnapshot(health, maxHealth, thirst, hunger, heldHeat, value, precipitation, air, armor,
                needsActive, northernHemisphere, barStates);
    }

    public HudSnapshot withPrecipitation(PrecipitationType value) {
        return new HudSnapshot(health, maxHealth, thirst, hunger, heldHeat, ambientTemperature,
                value, air, armor, needsActive, northernHemisphere, barStates);
    }

    /** The breath left in ticks, clamped to 0..{@link #MAX_AIR}. */
    public HudSnapshot withAir(double value) {
        return new HudSnapshot(health, maxHealth, thirst, hunger, heldHeat, ambientTemperature, precipitation,
                Math.clamp(value, 0, MAX_AIR), armor, needsActive, northernHemisphere, barStates);
    }

    /** The armour points, clamped to 0..{@link #ARMOR_CAP} (NaN reads as 0). */
    public HudSnapshot withArmor(double value) {
        return new HudSnapshot(health, maxHealth, thirst, hunger, heldHeat, ambientTemperature,
                precipitation, air,
                Double.isNaN(value) ? 0 : Math.clamp(value, 0, ARMOR_CAP), needsActive, northernHemisphere, barStates);
    }

    /**
     * PAR-HUD-12: whether the player's game mode has needs. False hides every bar and the held-item popup (the
     * placeholders gate on it, {@code HudValues}) and the values stay as they were.
     */
    public HudSnapshot withNeedsActive(boolean value) {
        return new HudSnapshot(health, maxHealth, thirst, hunger, heldHeat, ambientTemperature, precipitation, air, armor,
                value, northernHemisphere, barStates);
    }

    /**
     * PAR-HUD-16: the hemisphere the player stands in, published from the player's thread ({@code HudHemisphere}); the
     * {@code tfp_season} text reads it per player.
     */
    public HudSnapshot withNorthernHemisphere(boolean value) {
        return value == northernHemisphere ? this : new HudSnapshot(health, maxHealth, thirst, hunger, heldHeat,
                ambientTemperature, precipitation, air, armor, needsActive, value, barStates);
    }

    /** PAR-HUD-19: the bar states, published from the player's thread ({@code PlayerNeedsHudService}). */
    public HudSnapshot withBarStates(BarStates value) {
        Objects.requireNonNull(value, "value");
        return value.equals(barStates) ? this : new HudSnapshot(health, maxHealth, thirst, hunger, heldHeat,
                ambientTemperature, precipitation, air, armor, needsActive, northernHemisphere, value);
    }
}
