/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/BowlBlockEntity.java @ c3b4d9536 (powderBowlEnableAutomation:
 *       insertAll from UP, extractAll from every other side)
 */
package dev.tfp.paper.bowl;

import dev.tfp.core.device.automation.Face;
import dev.tfp.core.device.automation.PartialSlots;
import dev.tfp.core.device.automation.SideTable;
import dev.tfp.paper.device.StackCodec;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import dev.tfp.paper.device.automation.DeviceAutomation;

/**
 * PAR-POT-15: hopper automation of the two bowl kinds. TFC's side table, the {@code automation.powder-bowl} switch and the
 * after-move settle that redraws the powder layer (a hopper changes the slot without a device handler call).
 */
public final class BowlAutomation {
    private BowlAutomation() {
    }

    /** TFC: every side but the top takes everything out, the top puts everything in. One slot. */
    public static SideTable sides() {
        return SideTable.NONE
            .on(PartialSlots.of(1).insertAll(), Face.UP)
            .on(PartialSlots.of(1).extractAll(), d -> d != Face.UP);
    }

    /** The spec of {@code kindId} ({@link BowlDevices#CERAMIC} or {@link BowlDevices#WOODEN}). */
    public static DeviceAutomation.Spec spec(String kindId, BooleanSupplier enabled, BowlEffects effects, StackCodec stacks) {
        Objects.requireNonNull(effects, "effects");
        Objects.requireNonNull(stacks, "stacks");
        SideTable sides = sides();
        return DeviceAutomation.Spec.of(kindId, state -> sides).withEnabled(enabled)
            .withSettle((record, now, block) -> {
                effects.show(block, stacks.decode(record.slot(0)));
                return record;
            });
    }
}
