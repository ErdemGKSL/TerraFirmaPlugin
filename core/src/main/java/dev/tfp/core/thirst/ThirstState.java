/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/player/PlayerInfo.java @ c3b4d9536 (thirst, temperature, tick)
 */
package dev.tfp.core.thirst;

/**
 * A player's thirst under TFC's rules, evaluated lazily from the player tick ({@code CalendarState.playerTicks}).
 * Immutable. Nothing ticks: the caller says how many passive exhaustion points were spent since {@link #lastTick}
 * ({@code HungerState#pointsSpentTo}), and this state applies one loss per point.
 *
 * <p><b>TFC's model.</b> Every point of exhaustion that reaches 4 (one point of hunger) costs
 * {@code thirstModifier x (1 + heat share)} thirst; the heat share is {@code clampedMap(temperature, 22, 34, 0, 0.4)}.
 * Drinking adds the food's {@code water} and is capped at {@code maxThirst}. Thirst never goes below 0.
 *
 * <p><b>Divergences.</b> TFC samples the temperature at each point; a lazy jump uses the temperature given to
 * {@link #advance} once for the whole gap. TFC's thirst check fires at exhaustion 4 or more, while its food tick
 * spends only above 4; the points here are the spent ones ({@link dev.tfp.core.food.HungerState#pointsSpentTo}), so
 * the two differ only at an exact 4.0 boundary. See the ledger.
 *
 * @param thirst   the thirst level, 0..{@code maxThirst}
 * @param lastTick the player tick this state was evaluated at
 */
public record ThirstState(float thirst, long lastTick) {
    public ThirstState {
        if (!(thirst >= 0) || Float.isInfinite(thirst)) {
            throw new IllegalArgumentException("thirst must be >= 0 and finite: " + thirst);
        }
    }

    /** A player at full thirst at {@code tick}. */
    public static ThirstState full(long tick, ThirstConfig config) {
        return new ThirstState(config.maxThirst(), tick);
    }

    /** TFC {@code getThirstContributionFromTemperature}: the extra share of each point's loss from the heat. */
    public static float temperatureContribution(float temperature, ThirstConfig config) {
        if (!config.temperatureOverheating()) {
            return 0f;
        }
        return clampedMap(temperature, config.lowTemperature(), config.highTemperature(), 0f,
                config.maxTemperatureDecay());
    }

    /** The thirst lost per point of hunger spent at {@code temperature}. */
    public static float lossPerPoint(float temperature, ThirstConfig config) {
        return config.thirstModifier() * (1 + temperatureContribution(temperature, config));
    }

    /**
     * The state at player tick {@code now}: {@code exhaustionPoints} points of hunger spent since {@link #lastTick}
     * each cost {@link #lossPerPoint}, floored at 0. A zero count only moves the tick (a {@code now} before
     * {@link #lastTick} changes nothing).
     */
    public ThirstState advance(long now, long exhaustionPoints, float temperature, ThirstConfig config) {
        if (exhaustionPoints < 0) {
            throw new IllegalArgumentException("exhaustionPoints < 0: " + exhaustionPoints);
        }
        long tick = Math.max(now, lastTick);
        if (exhaustionPoints == 0) {
            return tick == lastTick ? this : new ThirstState(thirst, tick);
        }
        float loss = lossPerPoint(temperature, config);
        double left = thirst - (double) exhaustionPoints * loss;
        return new ThirstState((float) Math.max(0d, left), tick);
    }

    /** TFC {@code addThirst(water)} for a drink or a meal: adds, then caps at {@code maxThirst}. */
    public ThirstState drink(float water, ThirstConfig config) {
        return withThirst(thirst + water, config);
    }

    /** TFC {@code setThirst}: sets the level, clamped to {@code [0, maxThirst]}. */
    public ThirstState withThirst(float value, ThirstConfig config) {
        return new ThirstState(clamp(value, 0f, config.maxThirst()), lastTick);
    }

    /** The effect stage at this thirst level. */
    public ThirstStage stage(ThirstConfig config) {
        return ThirstStage.of(thirst, config);
    }

    /**
     * TFC {@code Drinkable.attemptDrink}: a drink with {@code water} is allowed while the player is not full, or when
     * the drink gives no water, or when the drink may be taken when full.
     */
    public boolean canDrink(float water, boolean mayDrinkWhenFull, ThirstConfig config) {
        return thirst < config.maxThirst() || water == 0 || mayDrinkWhenFull;
    }

    /** {@code Mth.clampedMap} as in Minecraft 1.21.1. */
    static float clampedMap(float value, float fromMin, float fromMax, float toMin, float toMax) {
        float delta = (value - fromMin) / (fromMax - fromMin);
        if (delta < 0) {
            return toMin;
        }
        return delta > 1 ? toMax : toMin + delta * (toMax - toMin);
    }

    private static float clamp(float value, float min, float max) {
        return value < min ? min : Math.min(value, max);
    }
}
