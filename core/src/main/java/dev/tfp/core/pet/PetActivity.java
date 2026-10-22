/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/entities/ai/TFCBrain.java @ c3b4d9536 (IDLE_AT_HOME, SIT, FOLLOW, HUNT activities) and
 * src/main/java/net/dries007/tfc/common/entities/ai/pet/TamableAi.java (beginIdle)
 */
package dev.tfp.core.pet;

/**
 * The brain activities a pet command selects. TFC keeps these as Minecraft {@code Activity} objects; the core keeps only
 * their identity, and the glue maps each value to the matching activity on Paper.
 */
public enum PetActivity {
    /** TFC {@code TFCBrain.IDLE_AT_HOME}: hang around the home position. */
    IDLE_AT_HOME,
    /** Vanilla {@code Activity.IDLE}: the pet is idle away from home. */
    IDLE,
    /** TFC {@code TFCBrain.SIT}: sit for a period of time. */
    SIT,
    /** TFC {@code TFCBrain.FOLLOW}: follow the owner but do not join combat. */
    FOLLOW,
    /** TFC {@code TFCBrain.HUNT}: follow the owner and join combat. */
    HUNT
}
