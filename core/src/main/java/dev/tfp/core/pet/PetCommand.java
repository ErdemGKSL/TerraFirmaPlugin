/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/entities/livestock/pet/TamableMammal.java (enum Command) @ c3b4d9536
 */
package dev.tfp.core.pet;

/**
 * The pet command set of TFC's {@code TamableMammal.Command}, in TFC's order. The ordinal is the saved value (the NBT
 * {@code command} int) and the codec's byte, so the order must not change.
 */
public enum PetCommand {
    /** Hang around near home. Activity {@link PetActivity#IDLE_AT_HOME}. */
    RELAX(PetActivity.IDLE_AT_HOME),
    /**
     * Set a new home at the player's position. It is not a standing state: the stored command becomes {@link #RELAX}.
     * It has no activity of its own, but its stored result does, see {@link PetRules#handle}.
     */
    HOME(null),
    /** Sit for a period of time. Activity {@link PetActivity#SIT}. */
    SIT(PetActivity.SIT),
    /** Follow the owner but do not join combat. Activity {@link PetActivity#FOLLOW}. */
    FOLLOW(PetActivity.FOLLOW),
    /** Follow the owner and join combat. Activity {@link PetActivity#HUNT}. */
    HUNT(PetActivity.HUNT);

    private final PetActivity activity;

    PetCommand(PetActivity activity) {
        this.activity = activity;
    }

    /**
     * The activity this command selects, or {@code null} for {@link #HOME}, which has none (TFC {@code Command.HOME(null)}).
     */
    public PetActivity activity() {
        return activity;
    }

    /**
     * The command with the given saved ordinal (TFC {@code Command.valueOf(int)}).
     *
     * @throws IllegalArgumentException if the ordinal is not a command
     */
    public static PetCommand fromOrdinal(int id) {
        final PetCommand[] values = values();
        if (id < 0 || id >= values.length) {
            throw new IllegalArgumentException("unknown pet command ordinal: " + id);
        }
        return values[id];
    }
}
