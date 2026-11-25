/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/client/BarSystem.java @ c3b4d9536 (EGG)
 */
package dev.tfp.paper.itembar.egg;

import dev.tfp.core.itembar.EggBar;
import dev.tfp.paper.itembar.BarStack;
import dev.tfp.paper.tooltip.feature.FeatureData;
import java.util.function.LongSupplier;

/**
 * The egg bar ({@link EggBar}) over the packet view of a stack (PAR-FAUNA-23): the egg component is the item's
 * {@code tfp:egg} record ({@link FeatureData#egg}, written by the nest box and the ground laying), read once per stack and
 * memoised for the visible, width and colour calls.
 */
public final class EggBars {
    private EggBars() {
    }

    /** @param today the current total calendar day (TFC {@code Calendars.CLIENT.getTotalCalendarDays()}) */
    public static EggBar<BarStack> create(LongSupplier today) {
        return new EggBar<>(stack -> stack.memo("egg.record", s -> FeatureData.egg(s.item().values())
                .map(e -> new EggBar.Incubation(e.fertilized(), e.hatchDay()))), today);
    }
}
