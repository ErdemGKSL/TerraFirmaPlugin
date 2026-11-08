/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/player/PlayerInfo.java @ c3b4d9536 (getIntoxication, addIntoxication),
 * src/main/java/net/dries007/tfc/config/ServerConfig.java @ c3b4d9536 (maxIntoxicationTicks)
 * and src/main/java/net/dries007/tfc/ForgeEventHandler.java @ c3b4d9536 (onServerChat: the slur chance)
 */
package dev.tfp.core.intox;

/**
 * TFC's intoxication clock (PAR-SURV-08). The player stores one number, the player tick it is intoxicated until
 * ({@link #SOBER} when it never drank), so nothing ticks: the level is read from that timestamp whenever it is needed
 * and falls linearly to 0. Pure and thread-safe.
 *
 * <p>TFC: drinking adds the drinkable's {@code intoxication} ticks (4000 for every alcohol, not scaled by the amount
 * drunk, because {@code FoodData.mul} keeps it), counted from now when the player was sober, and the stored tick is
 * capped at now + {@code maxIntoxicationTicks} (36 in-game hours, 43,200 ticks). The level is the remaining ticks over
 * that maximum, in [0, 1]. At a level above 0.2 chat messages are slurred ({@link ChatSlur}).
 */
public final class Intoxication {
    /** TFC's initial {@code intoxicationTick}: never intoxicated. */
    public static final long SOBER = Long.MIN_VALUE;
    /** TFC {@code PLAYER_TICKS_IN_DEFAULT_HOUR}. */
    public static final int PLAYER_TICKS_IN_HOUR = 60 * 20;
    /** TFC's default {@code maxIntoxicationTicks}: 36 hours. */
    public static final int DEFAULT_MAX_TICKS = 36 * PLAYER_TICKS_IN_HOUR;
    /** TFC's config minimum of {@code maxIntoxicationTicks}. */
    public static final int MIN_MAX_TICKS = 1000;
    /** The level above which chat is slurred (TFC {@code onServerChat}). */
    public static final float SLUR_FROM = 0.2f;
    /** The slur chance at full intoxication. */
    public static final float SLUR_AT_FULL = 0.7f;

    private Intoxication() {
    }

    /**
     * TFC {@code addIntoxication}: the stored tick after adding {@code ticks} at player tick {@code now}.
     *
     * @param until    the stored tick ({@link #SOBER} when never intoxicated)
     * @param maxTicks {@code maxIntoxicationTicks}
     */
    public static long add(long until, long now, long ticks, int maxTicks) {
        long tick = until < now ? now : until;
        tick += ticks;
        if (tick > now + maxTicks) {
            tick = now + maxTicks;
        }
        return tick;
    }

    /** TFC {@code getIntoxication}: the remaining ticks over {@code maxTicks}, 0 when sober (not clamped above 1, as TFC). */
    public static float level(long until, long now, int maxTicks) {
        if (until == SOBER) {
            return 0;
        }
        return (float) Math.max(0, until - now) / maxTicks;
    }

    /** The ticks left until sober (0 when sober). */
    public static long remaining(long until, long now) {
        return until == SOBER ? 0 : Math.max(0, until - now);
    }

    /** TFC {@code Mth.clampedMap(level, 0.2f, 1f, 0f, 0.7f)}: the chance per word and per change of a slur. */
    public static float slurChance(float level) {
        float delta = (level - SLUR_FROM) / (1f - SLUR_FROM);
        if (delta < 0f) {
            return 0f;
        }
        return delta > 1f ? SLUR_AT_FULL : 0f + delta * (SLUR_AT_FULL - 0f);
    }
}
