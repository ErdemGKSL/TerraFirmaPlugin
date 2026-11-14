/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/LogPileBlockEntity.java @ c3b4d9536 (lastClickTick,
 *       isLastClickPlacement, DOUBLE_CLICK_TICKS, NO_CLICK, checkDoubleClick, saveAdditional, loadAdditional),
 *       src/main/java/net/dries007/tfc/util/InteractionManager.java @ c3b4d9536 (the placement double click, under 6 ticks),
 *       src/main/java/net/dries007/tfc/common/blocks/devices/LogPileBlock.java @ c3b4d9536 (useItemOn)
 */
package dev.tfp.core.charcoal;

import dev.tfp.core.persist.VersionedCodec;

/**
 * The stored state of one log pile besides its sixteen log slots: TFC's click memory, which makes a quick second click
 * move all logs instead of one. A sneak click with a log ("placement") inserts all when it follows a placement click
 * within {@link #PLACEMENT_DOUBLE_CLICK_TICKS} (strictly less); a plain click takes all out of the top pile when it
 * follows a plain click within {@link #DOUBLE_CLICK_TICKS} (inclusive). Ticks are calendar ticks.
 *
 * @param lastClickTick      TFC {@code lastClickTick}
 * @param lastClickPlacement TFC {@code isLastClickPlacement}
 */
public record LogPileData(long lastClickTick, boolean lastClickPlacement) {
    /** TFC {@code DOUBLE_CLICK_TICKS}. */
    public static final int DOUBLE_CLICK_TICKS = 10;
    /** TFC {@code InteractionManager}: {@code currentTick - getLastClickTick() < 6}. */
    public static final int PLACEMENT_DOUBLE_CLICK_TICKS = 6;
    /** TFC {@code NO_CLICK}. */
    public static final long NO_CLICK = Long.MIN_VALUE / 2;

    /**
     * Until a pile is placed (TFC's constructor stamps the calendar tick and {@code placement = true}); a pile that never
     * saw a click cannot double click.
     */
    public static final LogPileData FRESH = new LogPileData(NO_CLICK, true);

    public static final VersionedCodec<LogPileData> CODEC = VersionedCodec.<LogPileData>builder("log_pile", 1)
        .build((s, out) -> out.putLong("tick", s.lastClickTick()).putBoolean("placement", s.lastClickPlacement()),
            in -> new LogPileData(in.getLong("tick", NO_CLICK), in.getBoolean("placement", true)));

    /** A pile placed at {@code now}: TFC's block entity constructor. */
    public static LogPileData placedAt(long now) {
        return new LogPileData(now, true);
    }

    /** InteractionManager: {@code isLastClickPlacement() && currentTick - getLastClickTick() < 6}. */
    public boolean placementDoubleClick(long now) {
        return lastClickPlacement && now - lastClickTick < PLACEMENT_DOUBLE_CLICK_TICKS;
    }

    /**
     * {@code useItemOn}: {@code !isLastClickPlacement() && checkDoubleClick(now)}. TFC's {@code checkDoubleClick} also
     * sets the tick to {@code NO_CLICK}, but {@code setLastClickTick(now)} overwrites it right after, so only the result
     * matters.
     */
    public boolean extractionDoubleClick(long now) {
        return !lastClickPlacement && now - lastClickTick <= DOUBLE_CLICK_TICKS;
    }

    /** {@code setLastClickTick(now); setLastClickPlacement(placement)}. */
    public LogPileData clicked(long now, boolean placement) {
        return new LogPileData(now, placement);
    }
}
