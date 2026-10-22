/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.tooltip;

import dev.tfp.core.clock.ClockSnapshot;
import java.util.Objects;

/**
 * The per-packet facts every {@link TooltipContributor} shares (PAR-INFRA-05): the viewer's client language and one
 * reading of the global clock, taken once per packet so every line of every stack in it agrees.
 *
 * @param locale the client language code, such as {@code tr_tr}
 * @param clock  the clock reading the lazy rules (heat, food decay) evaluate against
 */
public record TooltipContext(String locale, ClockSnapshot clock) {
    public TooltipContext {
        Objects.requireNonNull(locale, "locale");
        Objects.requireNonNull(clock, "clock");
    }
}
