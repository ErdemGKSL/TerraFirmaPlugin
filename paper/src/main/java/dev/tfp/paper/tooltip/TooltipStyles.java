/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.tooltip;

import java.util.Objects;

/**
 * The tooltip styles TFC leaves to each player's client config, as one server-wide choice (PAR-INFRA-05, NOT-PARITY
 * NP-CLIENT-04).
 *
 * @param heat       TFC {@code heatTooltipStyle}
 * @param foodExpiry TFC {@code foodExpiryTooltipStyle}
 * @param timeDelta  TFC {@code timeDeltaTooltipStyle}
 */
public record TooltipStyles(HeatDisplayStyle heat, FoodExpiryStyle foodExpiry, TimeDeltaStyle timeDelta) {
    /** TFC's client config defaults. */
    public static final TooltipStyles TFC_DEFAULTS =
            new TooltipStyles(HeatDisplayStyle.COLOR, FoodExpiryStyle.BOTH, TimeDeltaStyle.DAYS_MONTHS_YEARS);

    public TooltipStyles {
        Objects.requireNonNull(heat, "heat");
        Objects.requireNonNull(foodExpiry, "foodExpiry");
        Objects.requireNonNull(timeDelta, "timeDelta");
    }
}
