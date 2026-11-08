/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/BarrelBlockEntity.java @ c3b4d9536 (saveAdditional /
 * loadAdditional: sealedTick, recipeTick; the tank), src/main/java/net/dries007/tfc/common/blocks/devices/BarrelBlock.java
 * @ c3b4d9536 (SEALED)
 */
package dev.tfp.core.barrel;

import dev.tfp.core.persist.VersionedCodec;
import dev.tfp.core.vessel.FluidAmount;
import java.util.Objects;

/**
 * The barrel device's own state beside its item slots (PAR-PROC-08): the tank, the seal (TFC keeps it in the block state
 * {@code SEALED}; here the record is the truth and the block state follows it) and TFC's two ticks.
 *
 * @param fluid      the tank
 * @param sealed     whether the barrel is sealed
 * @param sealedTick the tick it was sealed, {@code 0} when unsealed
 * @param recipeTick the tick the current recipe started, or {@link BarrelRules#NO_TICK}
 */
public record BarrelData(FluidAmount fluid, boolean sealed, long sealedTick, long recipeTick) {
    public static final BarrelData FRESH = new BarrelData(FluidAmount.EMPTY, false, 0L, BarrelRules.NO_TICK);

    public static final VersionedCodec<BarrelData> CODEC = VersionedCodec.<BarrelData>builder("barrel", 1)
        .build((s, out) -> {
            out.putString("fluid", s.fluid().fluid());
            out.putInt("amount", s.fluid().amount());
            out.putBoolean("sealed", s.sealed());
            out.putLong("sealed_tick", s.sealedTick());
            out.putLong("recipe_tick", s.recipeTick());
        }, in -> {
            int amount = in.getInt("amount", 0);
            String id = in.getString("fluid", "");
            FluidAmount fluid = amount <= 0 || id.isEmpty() ? FluidAmount.EMPTY : new FluidAmount(id, amount);
            return new BarrelData(fluid, in.getBoolean("sealed", false), in.getLong("sealed_tick", 0L),
                in.getLong("recipe_tick", BarrelRules.NO_TICK));
        });

    public BarrelData {
        Objects.requireNonNull(fluid, "fluid");
    }

    public BarrelData withFluid(FluidAmount next) {
        return new BarrelData(next, sealed, sealedTick, recipeTick);
    }

    public BarrelData withRecipeTick(long tick) {
        return new BarrelData(fluid, sealed, sealedTick, tick);
    }

    /** TFC {@code onSeal}: the seal tick, and the recipe tick when a recipe matches. */
    public BarrelData sealedAt(long now, boolean recipeMatches) {
        return new BarrelData(fluid, true, now, recipeMatches ? now : recipeTick);
    }

    /** TFC {@code onUnseal}: both ticks cleared. */
    public BarrelData unsealed() {
        return new BarrelData(fluid, false, 0L, BarrelRules.NO_TICK);
    }
}
