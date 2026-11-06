/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/ScrapingBlockEntity.java @ c3b4d9536 (positions, color1,
 * color2, dye, onClicked),
 * file: src/main/java/net/dries007/tfc/common/blocks/devices/ScrapingBlock.java @ c3b4d9536 (WAXED)
 */
package dev.tfp.core.scraping;

import dev.tfp.core.craft.ScrapingRules;
import dev.tfp.core.persist.VersionedCodec;
import java.util.Optional;

/**
 * The state of one scraping surface (the hide or paper lying on a log): the scraped cells, whether it was waxed, and the
 * two dye colours. TFC keeps the mask and colours in {@code ScrapingBlockEntity} and the wax flag as the block state
 * property {@code waxed}; here all four are the device's own state (the block has no visible difference when waxed).
 *
 * @param mask   the scraped cells, bit {@code x + 4 * z} ({@link ScrapingRules})
 * @param waxed  TFC {@code ScrapingBlock.WAXED}: a waxed surface takes dyes and no longer takes the knife
 * @param color1 the dye of the unscraped cells, a vanilla {@code DyeColor} id (0 white .. 15 black), or {@link #NO_COLOR}
 * @param color2 the dye of the scraped cells, or {@link #NO_COLOR}
 * @param look   the item model that draws the surface ({@link ScrapingVisuals#itemModel}), empty for none; TFC's cached
 *               {@code inputTexture}/{@code outputTexture}, set when the item is placed and kept after it is finished
 */
public record ScrapingState(int mask, boolean waxed, int color1, int color2, String look) {
    /** No dye: TFC's {@code null} colour, drawn white ({@code -1}). */
    public static final int NO_COLOR = -1;

    /** Vanilla's 16 dye colours. */
    public static final int DYE_COLORS = 16;

    /** A surface just placed: nothing scraped, not waxed, no dyes. */
    public static final ScrapingState FRESH = new ScrapingState(0, false, NO_COLOR, NO_COLOR, "");

    public static final VersionedCodec<ScrapingState> CODEC = VersionedCodec.<ScrapingState>builder("scraping", 1)
        .build((s, out) -> out.putInt("mask", s.mask()).putBoolean("waxed", s.waxed())
                .putInt("color1", s.color1()).putInt("color2", s.color2()).putString("look", s.look()),
            in -> new ScrapingState(in.requireInt("mask"), in.getBoolean("waxed", false),
                in.getInt("color1", NO_COLOR), in.getInt("color2", NO_COLOR), in.getString("look", "")));

    public ScrapingState {
        if ((mask & ~ScrapingRules.COMPLETE) != 0) throw new IllegalArgumentException("mask must be a 16-bit value: " + mask);
        requireColor(color1);
        requireColor(color2);
        if (look == null) throw new IllegalArgumentException("look must not be null (empty for none)");
    }

    /** The state with the look of a newly placed item. */
    public ScrapingState withLook(String itemModel) {
        return new ScrapingState(mask, waxed, color1, color2, itemModel);
    }

    /** TFC {@code isComplete}: every cell is scraped. */
    public boolean complete() {
        return mask == ScrapingRules.COMPLETE;
    }

    /** The state after waxing (TFC {@code setValue(WAXED, true)}). */
    public ScrapingState waxedState() {
        return new ScrapingState(mask, true, color1, color2, look);
    }

    /**
     * TFC {@code dye(color)}: the first dye colours the unscraped cells, the second the scraped ones; a third is refused.
     *
     * @return the dyed state, empty when both colours are already set
     */
    public Optional<ScrapingState> dye(int color) {
        requireColor(color);
        if (color == NO_COLOR) throw new IllegalArgumentException("dye needs a colour");
        if (color1 == NO_COLOR) return Optional.of(new ScrapingState(mask, waxed, color, color2, look));
        if (color2 == NO_COLOR) return Optional.of(new ScrapingState(mask, waxed, color1, color, look));
        return Optional.empty();
    }

    /**
     * TFC {@code onClicked(hitX, hitZ)}: scrapes the cell under the hit.
     *
     * @return the step (its {@code changed} is TFC's return value) and the new state
     */
    public Scraped scrape(double hitX, double hitZ) {
        ScrapingRules.Step step = ScrapingRules.scrape(mask, hitX, hitZ);
        return new Scraped(step, step.changed() ? new ScrapingState(step.mask(), waxed, color1, color2, look) : this);
    }

    /** One knife click: TFC's outcome and the state after it. */
    public record Scraped(ScrapingRules.Step step, ScrapingState state) {}

    private static void requireColor(int c) {
        if (c != NO_COLOR && (c < 0 || c >= DYE_COLORS)) throw new IllegalArgumentException("dye colour out of range: " + c);
    }
}
