/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/player/ChiselMode.java @ c3b4d9536
 */
package dev.tfp.core.chisel;

/**
 * The chisel modes. TFC orders them by priority (lowest first), and cycling moves to the next mode, wrapping from the last to the
 * first. The constants are declared in that priority order, so {@link #ordinal()} is the cycle position. The priorities match TFC's
 * registered values: smooth 0, slab 100, stair 200.
 *
 * <p>Pure and thread-safe. The {@link #id()} is the recipe's {@code mode} key, for example {@code tfc:slab}.
 */
public enum ChiselMode {
    /** Smoothing: the recipe result is placed as it is. */
    SMOOTH("tfc:smooth", 0),
    /** Slab: the result is placed as a slab, on the half the player hit. */
    SLAB("tfc:slab", 100),
    /** Stair: the result is placed as a stair, oriented by the platform's placement rules. */
    STAIR("tfc:stair", 200);

    private final String id;
    private final int priority;

    ChiselMode(String id, int priority) {
        this.id = id;
        this.priority = priority;
    }

    /** The recipe key for this mode, such as {@code tfc:smooth}. */
    public String id() {
        return id;
    }

    /** TFC's sort priority. Lower values come first in the cycle. */
    public int priority() {
        return priority;
    }

    /** The mode after this one in the cycle, wrapping from the last mode to the first. */
    public ChiselMode next() {
        ChiselMode[] modes = values();
        return modes[(ordinal() + 1) % modes.length];
    }

    /**
     * The mode with the given recipe key.
     *
     * @throws IllegalArgumentException if no mode has that id
     */
    public static ChiselMode fromId(String id) {
        for (ChiselMode mode : values()) {
            if (mode.id.equals(id)) {
                return mode;
            }
        }
        throw new IllegalArgumentException("unknown chisel mode: " + id);
    }
}
