/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/AbstractFirepitBlockEntity.java @ c3b4d9536 (cascadeFuelSlots),
 *       src/main/java/net/dries007/tfc/config/ServerConfig.java @ c3b4d9536 (barrelEnableAutomation,
 *       firePitEnableAutomation, quernEnableAutomation, composterEnableAutomation, powderKegEnableAutomation)
 */
package dev.tfp.core.device.automation;

import dev.tfp.core.device.ItemBlob;
import java.util.ArrayList;
import java.util.List;

/** Pure pieces of device automation shared by the Paper glue: the config switches and the firepit fuel cascade. */
public final class AutomationRules {
    /** Vanilla {@code HopperBlockEntity.MOVE_ITEM_SPEED}: the cooldown after a hopper moved an item. */
    public static final int HOPPER_COOLDOWN = 8;

    private AutomationRules() {
    }

    /**
     * TFC's {@code *EnableAutomation} server config values (all {@code true} in TFC). Read once per hopper move through a
     * supplier; the {@code config.yml} keys ({@link #KEYS}) are optional and default to TFC's value.
     */
    public record Switches(boolean barrel, boolean firePit, boolean quern, boolean composter, boolean powderKeg) {
        public static final Switches TFC_DEFAULTS = new Switches(true, true, true, true, true);
    }

    /** Our {@code config.yml} keys, in the order of {@link Switches}' components. */
    public static final List<String> KEYS = List.of("automation.barrel", "automation.fire-pit", "automation.quern",
        "automation.composter", "automation.powder-keg");

    /**
     * TFC {@code cascadeFuelSlots}: the non-empty fuel slots {@code from..to} move down to the lowest open slots, in
     * order (the firepit, grill and pot after a fuel insertion).
     */
    public static List<ItemBlob> cascadeFuel(List<ItemBlob> slots, int from, int to) {
        List<ItemBlob> next = new ArrayList<>(slots);
        int lowestOpen = from;
        for (int i = from; i <= to; i++) {
            ItemBlob stack = next.get(i);
            if (!stack.isEmpty()) {
                if (i > lowestOpen) {
                    next.set(lowestOpen, stack);
                    next.set(i, ItemBlob.EMPTY);
                }
                lowestOpen++;
            }
        }
        return List.copyOf(next);
    }
}
