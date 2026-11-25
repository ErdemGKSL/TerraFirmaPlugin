/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/LargeVesselBlockEntity.java @ c3b4d9536 (constructor:
 *       sidedInventory, largeVesselEnableAutomation)
 */
package dev.tfp.core.largevessel;

import dev.tfp.core.device.automation.Face;
import dev.tfp.core.device.automation.PartialSlots;
import dev.tfp.core.device.automation.SideTable;
import dev.tfp.core.vessel.LargeVesselRules;

/**
 * PAR-POT-06: the item sides of a large vessel. TFC builds them when {@code largeVesselEnableAutomation} is on: every
 * slot takes items from every face but below, and gives them out through the bottom face. A sealed vessel refuses both
 * ways ({@link LargeVesselRules#canExtract}, {@code acceptsItem}).
 */
public final class LargeVesselSides {
    private LargeVesselSides() {
    }

    public static SideTable table() {
        return SideTable.NONE
            .on(PartialSlots.of(LargeVesselRules.SLOTS).insertAll(), face -> face != Face.DOWN)
            .on(PartialSlots.of(LargeVesselRules.SLOTS).extractAll(), Face.DOWN);
    }
}
