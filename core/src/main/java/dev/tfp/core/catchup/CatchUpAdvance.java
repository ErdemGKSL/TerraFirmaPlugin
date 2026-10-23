/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/util/calendar/ICalendarTickable.java @ c3b4d9536 (onCalendarUpdate)
 */
package dev.tfp.core.catchup;

import dev.tfp.core.device.CatchUp;
import dev.tfp.core.device.DeviceRecord;
import java.util.Objects;

/**
 * The pure half of a catch-up kind (PAR-INFRA-11): the state of one lazy block, as read from the world, brought up to
 * {@code now} (player ticks). The kind keeps its own timestamp inside {@code S} (a crop's last growth tick, a device
 * record's {@code lastUpdateTick}). Returning a value equal to {@code state} means "nothing changes" and the world is
 * not touched. Pure: no world access, no clock reads; runs on the worker pool.
 *
 * @param <S> the block's lazy state; must have value equality (the commit re-validates against it)
 */
@FunctionalInterface
public interface CatchUpAdvance<S> {
    S advance(S state, long now);

    /** A device record caught up with {@link CatchUp#apply} (the record's own {@code lastUpdateTick} rules). */
    static <S> CatchUpAdvance<DeviceRecord<S>> device(CatchUp.Advance<S> advance) {
        Objects.requireNonNull(advance, "advance");
        return (record, now) -> CatchUp.apply(record, now, advance).record();
    }
}
