/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/entities/livestock/pet/TamableMammal.java (command, sitting, sleeping,
 * the SIT_TIME and HOME memories, the petFlags byte) @ c3b4d9536
 */
package dev.tfp.core.pet;

/**
 * The stored pet state that TFC keeps in synced entity data, brain memories and NBT. Immutable, and holds no derived
 * values. The fields that are movement and path memories (walk target, cannot reach) are glue and are not stored here.
 *
 * @param command  the last command, as TFC's {@code command} field (a {@link PetCommand#HOME} is never stored)
 * @param sitting  TFC {@code isSitting()}
 * @param sleeping TFC {@code isSleeping()}
 * @param sitTick  server calendar tick the last SIT started, or {@link #NO_SIT_TICK} (TFC {@code SIT_TIME} memory)
 * @param home     the home position, or {@code null} if none was set (TFC {@code HOME} memory)
 */
public record PetState(PetCommand command, boolean sitting, boolean sleeping, long sitTick, Home home) {

    /** Sentinel for "no SIT_TIME memory" (TFC erases the memory, so there is no tick to compare). */
    public static final long NO_SIT_TICK = Long.MIN_VALUE;

    /**
     * A block position in a named dimension (TFC {@code GlobalPos}).
     *
     * @param dimension the dimension key, as a string
     */
    public record Home(String dimension, int x, int y, int z) {
        public Home {
            if (dimension == null) {
                throw new IllegalArgumentException("dimension is null");
            }
        }
    }

    public PetState {
        if (command == null) {
            throw new IllegalArgumentException("command is null");
        }
        if (command == PetCommand.HOME) {
            throw new IllegalArgumentException("HOME is not a stored command");
        }
    }

    /** A new pet: TFC's default {@code command = RELAX}, not sitting, not sleeping, no SIT_TIME, no home. */
    public static PetState fresh() {
        return new PetState(PetCommand.RELAX, false, false, NO_SIT_TICK, null);
    }
}
