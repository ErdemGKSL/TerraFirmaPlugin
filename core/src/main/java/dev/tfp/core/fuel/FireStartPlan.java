/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/items/FirestarterItem.java @ c3b4d9536 (onUseTick),
 *       src/main/java/net/dries007/tfc/common/items/FlintAndPyriteItem.java @ c3b4d9536 (useOn),
 *       src/main/java/net/dries007/tfc/util/events/StartFireEvent.java @ c3b4d9536 (startFire)
 */
package dev.tfp.core.fuel;

import java.util.random.RandomGenerator;

/**
 * The random part of one fire start, drawn before the world is touched ("decide async"): the strength, the firepit base
 * chance and the firepit roll. The world part (the event, the ground items, the fire block) is applied later on the
 * region thread with {@link #resolve}.
 *
 * <p>Draw order as TFC: flint and pyrite first draws its strength ({@code level.random.nextFloat() < chance}), then
 * {@code startFire} draws the firepit roll. TFC draws the firepit roll only when a firepit could be built; here it is
 * always drawn, which changes nothing observable (the draws are independent).
 *
 * @param strength    the start's strength
 * @param firepitBase the firepit base chance, or {@link FireStart#NO_FIREPIT}
 * @param firepitRoll the draw compared with the firepit chance, in {@code [0, 1)}
 */
public record FireStartPlan(FireStart.Strength strength, double firepitBase, float firepitRoll) {
    /**
     * The firestarter chance TFC actually ships. TFC's {@code ServerConfig} defines {@code fireStarterChance} (0.5) and
     * {@code flintAndPyriteChance} (0.25) under the same key {@code "fireStarterChance"}; NeoForge's
     * {@code ModConfigSpec.Builder.define} replaces the first spec with the second, so a fresh {@code tfc-server.toml}
     * holds 0.25 and both items read it. The declared 0.5 is never in effect.
     */
    public static final double FIRESTARTER_CHANCE = 0.25;
    /** The chance flint and pyrite reads: the same key {@code fireStarterChance}, shipped default 0.25. */
    public static final double FLINT_AND_PYRITE_CHANCE = 0.25;

    /** TFC {@code FirestarterItem}: always strong, firepit base {@code chance x (raining ? 0.3 : 1)}. */
    public static FireStartPlan firestarter(double chance, boolean raining, RandomGenerator random) {
        return new FireStartPlan(FireStart.Strength.STRONG, FireStart.firestarterChance(chance, raining),
            random.nextFloat());
    }

    /** TFC {@code FlintAndPyriteItem}: strength by a {@code chance} draw, firepit base {@code raining ? 0.3 : chance}. */
    public static FireStartPlan flintAndPyrite(double chance, boolean raining, RandomGenerator random) {
        FireStart.Strength strength = FireStart.flintAndPyriteStrength(random.nextFloat(), chance);
        return new FireStartPlan(strength, FireStart.flintAndPyriteFirepitBase(chance, raining), random.nextFloat());
    }

    /** A start with a fixed strength and firepit base ({@code startFire(..., strength, base)} of other callers). */
    public static FireStartPlan fixed(FireStart.Strength strength, double firepitBase, RandomGenerator random) {
        return new FireStartPlan(strength, firepitBase, random.nextFloat());
    }

    /** {@link FireStart#resolve} with this plan's strength, base and roll. */
    public FireStart.Outcome resolve(boolean cancelled, boolean canSurviveFirepit, int sticks, int kindling,
                                     boolean hasLogs) {
        return FireStart.resolve(cancelled, strength, canSurviveFirepit, firepitBase, sticks, kindling, hasLogs,
            firepitRoll);
    }
}
