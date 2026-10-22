/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/config/FoodExpiryTooltipStyle.java @ c3b4d9536
 * (the switch in addTooltipInfo of src/main/java/net/dries007/tfc/common/component/food/IFood.java)
 */
package dev.tfp.paper.tooltip;

import java.util.Optional;

/**
 * TFC's {@code FoodExpiryTooltipStyle} (the {@code foodExpiryTooltipStyle} client option): what the expiry line of a
 * fresh food says. One server-wide choice (NOT-PARITY NP-CLIENT-04); TFC's default is {@link #BOTH}.
 */
public enum FoodExpiryStyle {
    /** No expiry line. */
    NONE,
    /** "Expires on: 5:00 June 3, 1000". */
    EXPIRY,
    /** "Expires in: 4 day(s)". */
    TIME_LEFT,
    /** "Expires on: 5:00 June 3, 1000 (in 4 day(s))". */
    BOTH;

    /**
     * The expiry text, or empty for {@link #NONE}.
     *
     * @param date the exact moment, from {@link TimeDeltaStyle#timeAndDate}
     * @param left the time left, from {@link TimeDeltaStyle#timeDelta}
     */
    public Optional<String> text(String date, String left, String locale, TooltipText text) {
        return switch (this) {
            case NONE -> Optional.empty();
            case EXPIRY -> Optional.of(text.get(locale, "tooltip.food.expiry_date", date));
            case TIME_LEFT -> Optional.of(text.get(locale, "tooltip.food.expiry_left", left));
            case BOTH -> Optional.of(text.get(locale, "tooltip.food.expiry_date_and_left", date, left));
        };
    }
}
