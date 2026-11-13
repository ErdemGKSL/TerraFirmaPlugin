/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.weather;

import dev.tfp.core.world.BlockPos;
import java.util.Objects;

/**
 * One precipitation decision (PAR-CLIM-02): what falls at {@code pos} at the calendar reading with sequence
 * {@code clockSequence}. Immutable, so a reader on any thread sees a complete sample.
 *
 * @param clockSequence the {@code ClockSnapshot.sequence()} the decision was made from (orders samples)
 * @param calendarTicks the calendar tick of that reading
 * @param pos           the sampled position
 * @param type          the decision of {@link PrecipitationType#decide}
 */
public record PrecipitationSample(long clockSequence, long calendarTicks, BlockPos pos, PrecipitationType type) {
    public PrecipitationSample {
        Objects.requireNonNull(pos, "pos");
        Objects.requireNonNull(type, "type");
    }

    /** Vanilla {@code Level.isRainingAt} as TFC's mixin makes it: true only for rain (snow is not "raining"). */
    public boolean isRaining() {
        return type == PrecipitationType.RAIN;
    }

    public boolean isSnowing() {
        return type == PrecipitationType.SNOW;
    }

    /** Rain or snow. */
    public boolean isPrecipitating() {
        return type != PrecipitationType.NONE;
    }

    /** True if this sample was made from a strictly later clock reading than {@code other} (or there is none). */
    public boolean isNewerThan(PrecipitationSample other) {
        return other == null || clockSequence > other.clockSequence;
    }
}
