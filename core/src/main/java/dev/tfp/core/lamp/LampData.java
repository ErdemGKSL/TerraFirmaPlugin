/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/LampBlockEntity.java @ c3b4d9536 (tank, the tick counter),
 *       src/main/java/net/dries007/tfc/common/blocks/devices/LampBlock.java @ c3b4d9536 (LIT)
 */
package dev.tfp.core.lamp;

import dev.tfp.core.persist.VersionedCodec;
import dev.tfp.core.vessel.FluidAmount;
import java.util.Objects;

/**
 * A lamp's state: its fuel tank, whether it is lit, and the burn ticks not yet turned into fuel.
 *
 * @param tank  the fuel ({@link LampRules#CAPACITY} mB at most)
 * @param lit   TFC {@code LampBlock.LIT}
 * @param carry TFC's tick counter since the last check that used fuel: ticks burnt that did not yet add up to one mB
 */
public record LampData(FluidAmount tank, boolean lit, long carry) {
    public static final LampData FRESH = new LampData(FluidAmount.EMPTY, false, 0);

    public static final VersionedCodec<LampData> CODEC = VersionedCodec.<LampData>builder("lamp", 1)
        .build((d, out) -> out.putString("fluid", d.tank().fluid()).putInt("amount", d.tank().amount())
            .putBoolean("lit", d.lit()).putLong("carry", d.carry()),
            in -> new LampData(new FluidAmount(in.getString("fluid", ""), in.getInt("amount", 0)),
                in.getBoolean("lit", false), in.getLong("carry", 0)));

    public LampData {
        Objects.requireNonNull(tank, "tank");
        if (tank.amount() > LampRules.CAPACITY) {
            throw new IllegalArgumentException("fuel above the lamp's capacity: " + tank);
        }
        if (carry < 0) {
            throw new IllegalArgumentException("carry < 0: " + carry);
        }
    }

    public LampData withTank(FluidAmount next) {
        return new LampData(next, lit, carry);
    }

    public LampData withLit(boolean next) {
        return new LampData(tank, next, 0);
    }
}
