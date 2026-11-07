/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/effect/ExhaustedEffect.java @ c3b4d9536 (applyEffectTick),
 * src/main/java/net/dries007/tfc/common/effect/TFCEffects.java @ c3b4d9536 (OVERBURDENED attribute modifiers),
 * src/main/java/net/dries007/tfc/common/player/PlayerInfo.java @ c3b4d9536 (PASSIVE_EXHAUSTION_PER_TICK, addExhaustion),
 * src/main/java/net/dries007/tfc/ForgeEventHandler.java @ c3b4d9536 (onPlayerTick, onLivingJump),
 * src/main/java/net/dries007/tfc/util/Helpers.java @ c3b4d9536 (getOverburdened, getExhausted)
 */
package dev.tfp.core.item;

import dev.tfp.core.calendar.CalendarTicks;

/**
 * The numbers of TFC's exhausted and overburdened effects. TFC checks the inventory every
 * {@value #CHECK_PERIOD_TICKS} ticks (outside creative and spectator, while {@code enableOverburdening}) and gives
 * {@link Burden#ONE} the exhausted effect, {@link Burden#MORE_THAN_ONE} the overburdened one, each for
 * {@value #EFFECT_DURATION_TICKS} ticks.
 */
public final class BurdenEffects {
    /** {@code level.getGameTime() % 20 == 0}. */
    public static final int CHECK_PERIOD_TICKS = 20;
    /** {@code new MobEffectInstance(..., 25, 0, false, false)}. */
    public static final int EFFECT_DURATION_TICKS = 25;

    /** {@code PlayerInfo.MAX_HUNGER}. */
    static final int MAX_HUNGER = 20;
    /** {@code PlayerInfo.EXHAUSTION_PER_HUNGER}. */
    static final float EXHAUSTION_PER_HUNGER = 4;
    /** {@code PlayerInfo.EXHAUSTION_MULTIPLIER}: every vanilla exhaustion source is scaled by it. */
    public static final float EXHAUSTION_MULTIPLIER = 0.4f;
    /** {@code PlayerInfo.PASSIVE_EXHAUSTION_PER_TICK}. */
    public static final float PASSIVE_EXHAUSTION_PER_TICK = MAX_HUNGER * EXHAUSTION_PER_HUNGER
            / (2.5f * CalendarTicks.CALENDAR_TICKS_IN_DAY * EXHAUSTION_MULTIPLIER);
    /** {@code passiveExhaustionModifier} default. */
    public static final float DEFAULT_PASSIVE_EXHAUSTION_MODIFIER = 1.0f;

    /**
     * {@code TFCEffects.OVERBURDENED}: movement speed (and NeoForge swim speed) {@code -7.5}
     * {@code ADD_MULTIPLIED_TOTAL}, which takes the speed to 0 (vanilla clamps the attribute at 0).
     */
    public static final double OVERBURDENED_SPEED_MODIFIER = -7.5;

    private BurdenEffects() {
    }

    /**
     * {@code ExhaustedEffect.applyEffectTick} (once every 20 ticks of the effect): the food exhaustion it causes,
     * {@code PASSIVE_EXHAUSTION_PER_TICK * 20 * passiveExhaustionModifier * 0.25}, before
     * {@code PlayerInfo.addExhaustion} scales it.
     */
    public static float exhaustedCause(float passiveExhaustionModifier) {
        return PASSIVE_EXHAUSTION_PER_TICK * 20 * passiveExhaustionModifier * 0.25f;
    }

    /**
     * The exhaustion that reaches the food data per application: {@link #exhaustedCause} through
     * {@code PlayerInfo.addExhaustion} ({@code EXHAUSTION_MULTIPLIER * exhaustion}).
     */
    public static float exhaustedFoodExhaustion(float passiveExhaustionModifier) {
        return EXHAUSTION_MULTIPLIER * exhaustedCause(passiveExhaustionModifier);
    }

    /** Whether the burden slows the subject to a stop and cancels its jumps (overburdened). */
    public static boolean immobilises(Burden burden) {
        return burden == Burden.MORE_THAN_ONE;
    }

    /** Whether the burden drains food (exhausted). */
    public static boolean exhausts(Burden burden) {
        return burden == Burden.ONE;
    }
}
