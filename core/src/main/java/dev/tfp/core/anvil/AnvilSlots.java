/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/AnvilBlockEntity.java @ c3b4d9536 (SLOT_*, SLOTS_BY_HAND_*,
 * getSlotStackLimit),
 * file: src/main/java/net/dries007/tfc/common/blocks/devices/AnvilBlock.java @ c3b4d9536 (interactWithAnvil)
 */
package dev.tfp.core.anvil;

/**
 * The anvil's four slots and the order the hand interactions try them (PAR-METAL-03). Pure; any thread.
 */
public final class AnvilSlots {
    public static final int INPUT_MAIN = 0;
    public static final int INPUT_SECOND = 1;
    public static final int HAMMER = 2;
    public static final int CATALYST = 3;
    public static final int COUNT = 4;

    /** TFC {@code SLOTS_BY_HAND_EXTRACT}: an empty hand while sneaking takes the first non-empty of these. */
    public static final int[] BY_HAND_EXTRACT = {INPUT_MAIN, INPUT_SECOND};
    /** TFC {@code SLOTS_BY_HAND_INSERT}: a held item while sneaking goes into the first of these that takes any of it. */
    public static final int[] BY_HAND_INSERT = {CATALYST, INPUT_MAIN, INPUT_SECOND};

    private AnvilSlots() {
    }

    /** TFC {@code getSlotStackLimit}: the flux slot holds 64, the others 1. */
    public static int limit(int slot) {
        return slot == CATALYST ? 64 : 1;
    }
}
