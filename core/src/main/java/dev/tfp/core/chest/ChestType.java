/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.chest;

import java.util.Locale;

/** PAR-DECO-04: vanilla {@code ChestType}, the {@code type} property of a chest: one chest or the left or right half of two. */
public enum ChestType {
    SINGLE, LEFT, RIGHT;

    /** Vanilla {@code ChestType.getOpposite}: the type of the other half. */
    public ChestType opposite() {
        return switch (this) {
            case SINGLE -> SINGLE;
            case LEFT -> RIGHT;
            case RIGHT -> LEFT;
        };
    }

    /** The property value, {@code single}. */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static ChestType of(String id) {
        return switch (id) {
            case "single" -> SINGLE;
            case "left" -> LEFT;
            case "right" -> RIGHT;
            default -> throw new IllegalArgumentException("not a chest type: " + id);
        };
    }
}
