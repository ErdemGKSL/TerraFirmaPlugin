/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/rotation/CreativeRotationBlockEntity.java @ c3b4d9536
 *       (saveAdditional/loadAdditional "step")
 */
package dev.tfp.paper.rotation;

import dev.tfp.core.persist.VersionedCodec;
import dev.tfp.core.rotation.CreativeMotor;

/**
 * The device record state of a rotation block: the creative motor's speed step (TFC's {@code step} tag); 0 for every other
 * block. TFC's {@code invalid} flag is not stored: a block whose network action failed is broken four ticks later, and a
 * restart before that re-adds it, which fails again the same way.
 *
 * @param step {@code -MAX_STEPS..MAX_STEPS}
 */
public record RotationState(int step) {
    public static final RotationState FRESH = new RotationState(0);
    public static final VersionedCodec<RotationState> CODEC = VersionedCodec.<RotationState>builder("rotation", 1)
        .build((v, out) -> out.putInt("step", v.step()), in -> new RotationState(in.getInt("step", 0)));

    public RotationState {
        step = CreativeMotor.clamp(step);
    }
}
