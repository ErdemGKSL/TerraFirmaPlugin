/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/effect/ThirstEffect.java @ c3b4d9536 (applyEffectTick,
 * shouldApplyEffectTickThisTick)
 */
package dev.tfp.core.thirst;

/**
 * TFC's {@code tfc:thirst} mob effect (from salt water and vinegar) as a lazy value: an amplifier and the ticks left.
 * Immutable. While it lasts, every {@code 50 >> amplifier} ticks of its remaining duration (every tick once that is 0)
 * it takes {@code 0.02 x (amplifier + 1)} thirst, but only while the thirst is above {@value #MIN_THIRST}.
 *
 * <p>Vanilla's effect tick ({@code MobEffectInstance.tickServer}) checks {@code shouldApplyEffectTickThisTick} with the
 * remaining duration before counting it down, so the applications are at the remaining durations {@code d} in
 * {@code [1, duration]} with {@code d % ticker == 0}.
 *
 * @param amplifier the effect amplifier, 0 or more
 * @param remaining the ticks left, 1 or more (an effect with none left is absent)
 */
public record ThirstEffect(int amplifier, int remaining) {
    /** TFC's guard: no drain at or below this thirst. */
    public static final float MIN_THIRST = 0.05f;
    /** TFC's drain per application per amplifier level. */
    public static final float DRAIN_PER_LEVEL = 0.02f;
    /** TFC's base interval, shifted right by the amplifier. */
    public static final int BASE_TICKER = 50;

    public ThirstEffect {
        if (amplifier < 0 || amplifier > 255) {
            throw new IllegalArgumentException("amplifier must be in [0, 255]: " + amplifier);
        }
        if (remaining < 1) {
            throw new IllegalArgumentException("remaining must be >= 1: " + remaining);
        }
    }

    /** The interval in ticks between applications ({@code 50 >> amplifier}); 0 means every tick. */
    public int ticker() {
        return amplifier >= 31 ? 0 : BASE_TICKER >> amplifier;
    }

    /** The thirst taken by one application. */
    public float drainPerApplication() {
        return DRAIN_PER_LEVEL * (amplifier + 1);
    }

    /** The thirst after {@code elapsed} ticks of this effect, and the effect left (null when it ran out). */
    public record Drain(float thirst, ThirstEffect left) {
    }

    /**
     * {@code elapsed} ticks of the effect from {@code thirst}: each application takes {@link #drainPerApplication}
     * while the thirst is above {@link #MIN_THIRST}, floored at 0 ({@code setThirst}). Applications are replayed one by
     * one in float, so the result is TFC's to the bit; at most {@link #remaining} ticks count.
     */
    public Drain advance(float thirst, long elapsed) {
        if (elapsed <= 0) {
            return new Drain(thirst, this);
        }
        int ticks = (int) Math.min(elapsed, remaining);
        int ticker = ticker();
        float step = drainPerApplication();
        float t = thirst;
        for (int i = 0; i < ticks; i++) {
            int duration = remaining - i;
            if ((ticker == 0 || duration % ticker == 0) && t > MIN_THIRST) {
                t = Math.max(0f, t - step);
            }
        }
        int left = remaining - ticks;
        return new Drain(t, left > 0 ? new ThirstEffect(amplifier, left) : null);
    }

    /**
     * Vanilla {@code MobEffectInstance.update} for a new instance of the same effect, without the hidden-effect stack: a
     * higher amplifier replaces this one; the same amplifier keeps the longer duration; a lower amplifier is ignored.
     */
    public ThirstEffect combine(ThirstEffect added) {
        if (added.amplifier > amplifier) {
            return added;
        }
        if (added.amplifier == amplifier && added.remaining > remaining) {
            return added;
        }
        return this;
    }
}
