/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/client/model/PlantBlockModel.java @ c3b4d9536 (getModelFromBlockState: the calendar
 *       and the hemisphere the client reads)
 */
package dev.tfp.paper.plant.lifecycle;

import dev.tfp.core.calendar.CalendarTicks;
import dev.tfp.core.climate.Solar;
import dev.tfp.core.clock.ClockSnapshot;
import java.util.UUID;

/**
 * PAR-WORLD-63: what the plant stage packet handler reads on a Netty thread: the current {@link PlantStageRemap} and the
 * viewer's TFC world. Implementations hold only immutable snapshots published through volatile fields or concurrent maps;
 * no method touches the world. No PacketEvents types here (the handler lives in {@code dev.tfp.paper.packet.plantstage}).
 */
public interface PlantStageSource {
    /** A TFC overworld a player is in: its climate, hemisphere scale and minimum build height. */
    record Viewer(UUID world, int minY, float hemisphereScale, PlantStageRemap.ColumnClimate climate) {
        /**
         * The view of a block column at {@code z}: the calendar's fraction of the year and day time, and the hemisphere at
         * {@code z} ({@code ClientHelpers.inNorthernHemisphere} asks the viewer's z; the handler passes the column's, a
         * ledger divergence). Null while the calendar has not published a reading.
         */
        public PlantStageRemap.View view(ClockSnapshot clock, int z) {
            if (clock == null || !clock.isSet()) {
                return null;
            }
            return new PlantStageRemap.View((float) CalendarTicks.fractionOfYear(clock.calendarTicks(), clock.daysInMonth()),
                clock.calendarTicks() % 24000L, Solar.inNorthernHemisphere(z, hemisphereScale));
        }
    }

    /** The remap of the current CraftEngine load, or {@link PlantStageRemap#EMPTY}. */
    PlantStageRemap remap();

    /** The TFC overworld {@code player} is in, or null (another world, not tracked yet, offline). */
    Viewer viewer(UUID player);

    /** The current calendar reading. */
    ClockSnapshot clock();
}
