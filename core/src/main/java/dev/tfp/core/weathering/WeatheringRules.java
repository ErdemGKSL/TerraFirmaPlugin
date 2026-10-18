/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/IWeatheringBlock.java @ c3b4d9536 (onRandomTick, ageModifier, Age),
 *       src/main/java/net/dries007/tfc/common/blocks/WeatheringBlock.java @ c3b4d9536 (randomTick gate),
 *       src/main/java/net/dries007/tfc/util/registry/RegistryMetal.java @ c3b4d9536 (weathers: resistance != -1)
 */
package dev.tfp.core.weathering;

/**
 * Metal weathering ({@code IWeatheringBlock.onRandomTick}), lazy. Pure: nothing ticks, and the caller passes the random
 * draws and the environment chance in.
 *
 * <p><b>The TFC rule, per random tick.</b> A block that is not {@link Age#OXIDIZED} draws {@code r1}; it continues only if
 * {@code r1 >= 0.95}. It then draws {@code r2} and continues only if {@code r2 <= 1 - resistance}. Then it draws {@code r3}
 * and advances one stage if {@code r3} is below the chance of the stage. TFC tests three paths against the same {@code r3}:
 * the slow random path ({@code ageModifier * 0.05}), the drip path and the climate path (both with the neighbour modifier
 * and only when their own exposure holds). Since all three compare the same draw with a threshold, the chance of an
 * advance is the largest of the thresholds that apply. This port takes that largest one as {@code extraChance}, which
 * the caller computes from the drip and climate terms; with no extra term, only the slow path applies.
 *
 * <p><b>The per-tick probability.</b> A random tick reaches a given block with probability
 * {@code randomTickSpeed / 4096} per game tick (vanilla: each chunk section of 4096 blocks takes {@code randomTickSpeed}
 * random ticks per game tick). For a stage {@code s} and weathering resistance {@code R >= 0}, the stage advances with rate
 * <pre>
 *   lambda(s) = r * p(s),  r = randomTickSpeed / 4096,  p(s) = 0.05 * (1 - R) * max(ageModifier(s) * 0.05, extraChance)
 * </pre>
 * where {@code p(s)} is the advance chance per random tick and {@code r} the random tick rate per game tick.
 * {@code ageModifier} is 0.5 for {@link Age#NONE} and 1 otherwise. A metal that does not weather ({@code R < 0}) never
 * advances, as TFC does not register its weathering blocks.
 *
 * <p><b>The lazy form.</b> {@link #catchUp} takes the elapsed calendar ticks since the block was last weathered and walks
 * the stages. For the current stage it draws an exponential waiting time {@code -ln(1 - u) / lambda(s)} from one uniform
 * draw {@code u}; the stage advances if the waiting time is shorter than the time left, and the walk continues from the
 * new stage with the time left. The expected advance per elapsed time is therefore the stepped expectation in the
 * continuous limit: the first advance from a stage takes {@code 1 / lambda(s)} ticks on average, and the probability that
 * a stage has advanced after {@code T} ticks is {@code 1 - exp(-lambda(s) T)}, which is the stepped value
 * {@code 1 - (1 - p(s))^(r T)} up to terms of order {@code p(s)^2}. The divergence and its revisit condition
 * are in the ledger.
 *
 * <p>Calendar ticks are game ticks: the calendar advances one per game tick, as in TFC.
 */
public final class WeatheringRules {
    /** Vanilla blocks per chunk section, the denominator of the random tick chance. */
    public static final int BLOCKS_PER_SECTION = 4096;
    /** Vanilla {@code randomTickSpeed} default: random ticks per chunk section per game tick. */
    public static final int DEFAULT_RANDOM_TICK_SPEED = 3;
    /** TFC: the gate's first draw must be at least this for a weathering attempt. */
    public static final double GATE_DRAW = 0.95;
    /** TFC: the base chance of a slow random weathering step, before the age modifier. */
    public static final double SLOW_CHANCE = 0.05;
    /** TFC {@code ageModifier()} for {@link Age#NONE}; every other age has 1. */
    public static final double NONE_AGE_MODIFIER = 0.5;
    /** The number of stage steps a block can take from {@link Age#NONE} to {@link Age#OXIDIZED}, and so the draws needed. */
    public static final int MAX_STEPS = 3;

    /** The stage of a weathering metal block, in TFC's order. The ordinal is the stage. */
    public enum Age {
        NONE, EXPOSED, WEATHERED, OXIDIZED;

        /** The next stage, or {@code null} for {@link #OXIDIZED}. */
        public Age next() {
            return this == OXIDIZED ? null : values()[ordinal() + 1];
        }
    }

    private WeatheringRules() {
    }

    /**
     * Whether a metal with this resistance weathers at all. TFC registers weathering blocks only for resistances other than
     * {@code -1}; the port treats any negative value as non-weathering.
     *
     * @param resistance the weathering resistance, in {@code [0, 1]} for a weathering metal
     */
    public static boolean weathers(float resistance) {
        return resistance >= 0f;
    }

    /**
     * TFC {@code ageModifier()}: 0.5 for {@link Age#NONE}, 1 otherwise.
     *
     * @param age the stage
     */
    public static double ageModifier(Age age) {
        return age == Age.NONE ? NONE_AGE_MODIFIER : 1.0;
    }

    /**
     * The chance that a random tick passes TFC's gate: {@code 0.05 * (1 - resistance)}. Zero for a non-weathering metal.
     *
     * @param resistance the weathering resistance, clamped to {@code [0, 1]} when it weathers
     */
    public static double gateChance(float resistance) {
        if (!weathers(resistance)) {
            return 0.0;
        }
        double clamped = Math.min(1.0, Math.max(0.0, resistance));
        return (1.0 - GATE_DRAW) * (1.0 - clamped);
    }

    /**
     * The chance that a gated random tick advances the stage: {@code max(ageModifier * 0.05, extraChance)} for every stage
     * but {@link Age#OXIDIZED}, which never advances.
     *
     * @param age         the stage
     * @param extraChance the drip or climate chance of the current exposure, already including the neighbour modifier; 0
     *                    when neither applies. Clamped to {@code [0, 1]}.
     */
    public static double advanceChance(Age age, double extraChance) {
        if (age == Age.OXIDIZED) {
            return 0.0;
        }
        double extra = Math.min(1.0, Math.max(0.0, extraChance));
        return Math.max(ageModifier(age) * SLOW_CHANCE, extra);
    }

    /**
     * The rate of one stage advance, per game tick: {@code (randomTickSpeed / 4096) * gateChance * advanceChance}.
     *
     * @param age             the stage
     * @param resistance      the weathering resistance
     * @param randomTickSpeed random ticks per section per game tick, {@code >= 0} (vanilla 3)
     * @param extraChance     see {@link #advanceChance}
     */
    public static double rate(Age age, float resistance, int randomTickSpeed, double extraChance) {
        if (randomTickSpeed < 0) {
            throw new IllegalArgumentException("randomTickSpeed must be >= 0: " + randomTickSpeed);
        }
        return (double) randomTickSpeed / BLOCKS_PER_SECTION * gateChance(resistance) * advanceChance(age, extraChance);
    }

    /**
     * The lazy catch-up: the stage after {@code elapsedTicks} calendar ticks, from the stored stage and the given draws.
     * Each step uses one uniform draw {@code u} in {@code [0, 1)} as described in the class Javadoc; the walk stops at the
     * first waiting time that is not shorter than the time left, so zero elapsed time never advances, even for {@code u = 0}.
     * The result is monotone in the elapsed time for fixed draws, and larger draws give stages that are no later.
     *
     * @param stored          the stage stored on the block
     * @param elapsedTicks    calendar ticks since the block was last weathered, {@code >= 0}
     * @param resistance      the weathering resistance; a non-weathering metal never advances
     * @param randomTickSpeed random ticks per section per game tick, {@code >= 0}
     * @param extraChance     see {@link #advanceChance}, held constant over the whole catch-up
     * @param uniforms        at least {@link #MAX_STEPS} uniform draws in {@code [0, 1)}, one per stage step
     * @return the stage after the catch-up, never past {@link Age#OXIDIZED}
     */
    public static Age catchUp(Age stored, long elapsedTicks, float resistance, int randomTickSpeed, double extraChance,
                              double[] uniforms) {
        if (stored == null) {
            throw new IllegalArgumentException("stored stage must not be null");
        }
        if (elapsedTicks < 0) {
            throw new IllegalArgumentException("elapsedTicks must be >= 0: " + elapsedTicks);
        }
        if (uniforms == null || uniforms.length < MAX_STEPS) {
            throw new IllegalArgumentException("need at least " + MAX_STEPS + " uniform draws");
        }
        double remaining = elapsedTicks;
        Age current = stored;
        int step = 0;
        while (current != Age.OXIDIZED) {
            double lambda = rate(current, resistance, randomTickSpeed, extraChance);
            if (lambda <= 0.0) {
                break;
            }
            double u = uniforms[step];
            if (u < 0.0 || u >= 1.0) {
                throw new IllegalArgumentException("uniform draw must be in [0, 1): " + u);
            }
            double wait = -Math.log1p(-u) / lambda;
            if (wait >= remaining) {
                break;
            }
            remaining -= wait;
            current = current.next();
            step++;
        }
        return current;
    }
}
