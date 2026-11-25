/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/container/ScribingTableContainer.java @ c3b4d9536 (createResult, setItemName,
 * mayPickup)
 */
package dev.tfp.core.scribing;

/** The scribing table's naming rule (PAR-PROC-15). Pure and thread-safe. */
public final class ScribingRules {
    private ScribingRules() {}

    /** What the typed name does to the item. */
    public enum Kind {
        /** No result: nothing to change. */
        NONE,
        /** The result is the item without its custom name. */
        CLEAR,
        /** The result is the item named {@link Rename#name()}. */
        SET
    }

    /**
     * @param kind the change
     * @param name the new name for {@link Kind#SET}, else {@code null}
     */
    public record Rename(Kind kind, String name) {
        public static final Rename NONE = new Rename(Kind.NONE, null);
        public static final Rename CLEAR = new Rename(Kind.CLEAR, null);

        public static Rename set(String name) {
            return new Rename(Kind.SET, name);
        }
    }

    /**
     * TFC {@code createResult}: a blank text clears a custom name (and gives no result for an item without one); a text that
     * differs from the item's current display name sets it; the same text gives no result.
     *
     * @param hasCustomName whether the input has a custom name
     * @param hoverName     the input's display name as plain text
     * @param typed         the text field, {@code null} before anything was typed
     */
    public static Rename rename(boolean hasCustomName, String hoverName, String typed) {
        if (typed == null || typed.isBlank()) {
            return hasCustomName ? Rename.CLEAR : Rename.NONE;
        }
        return typed.equals(hoverName) ? Rename.NONE : Rename.set(typed);
    }

    /** TFC {@code mayPickup}: creative mode, or an ink in the second slot. */
    public static boolean mayPickup(boolean creative, boolean inkInSlot) {
        return creative || inkInSlot;
    }
}
