/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/BarrelBlockEntity.java @ c3b4d9536 (saveAdditional /
 * loadAdditional: sealedTick, recipeTick; the tank; tickPouring for the tap clock), src/main/java/net/dries007/tfc/common/blocks/devices/BarrelBlock.java
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
 * @param rainTick   PAR-PROC-35: the tick the rain fill was last settled ({@link BarrelRain#settle}), or
 *                   {@link BarrelRules#NO_TICK} before the first settle
 * @param rainSeen   PAR-PROC-35: whether it rained at the block above at {@code rainTick}
 * @param pourTick   PAR-PROC-38: the tick the tap was last settled ({@link BarrelTap#settle}), or
 *                   {@link BarrelRules#NO_TICK} before the first settle
 * @param pourSeen   PAR-PROC-38: whether the tap could pour at {@code pourTick}
 * @param powered    PAR-PROC-42: TFC's {@code POWERED} property: the last redstone signal the barrel acted on
 *                   ({@link BarrelRedstone})
 */
public record BarrelData(FluidAmount fluid, boolean sealed, long sealedTick, long recipeTick, long rainTick,
        boolean rainSeen, long pourTick, boolean pourSeen, boolean powered) {
    public static final BarrelData FRESH = new BarrelData(FluidAmount.EMPTY, false, 0L, BarrelRules.NO_TICK,
        BarrelRules.NO_TICK, false, BarrelRules.NO_TICK, false, false);

    public static final VersionedCodec<BarrelData> CODEC = VersionedCodec.<BarrelData>builder("barrel", 1)
        .build((s, out) -> {
            out.putString("fluid", s.fluid().fluid());
            out.putInt("amount", s.fluid().amount());
            out.putBoolean("sealed", s.sealed());
            out.putLong("sealed_tick", s.sealedTick());
            out.putLong("recipe_tick", s.recipeTick());
            out.putLong("rain_tick", s.rainTick());
            out.putBoolean("rain_seen", s.rainSeen());
            out.putLong("pour_tick", s.pourTick());
            out.putBoolean("pour_seen", s.pourSeen());
            out.putBoolean("powered", s.powered());
        }, in -> {
            int amount = in.getInt("amount", 0);
            String id = in.getString("fluid", "");
            FluidAmount fluid = amount <= 0 || id.isEmpty() ? FluidAmount.EMPTY : new FluidAmount(id, amount);
            return new BarrelData(fluid, in.getBoolean("sealed", false), in.getLong("sealed_tick", 0L),
                in.getLong("recipe_tick", BarrelRules.NO_TICK), in.getLong("rain_tick", BarrelRules.NO_TICK),
                in.getBoolean("rain_seen", false), in.getLong("pour_tick", BarrelRules.NO_TICK),
                in.getBoolean("pour_seen", false), in.getBoolean("powered", false));
        });

    public BarrelData {
        Objects.requireNonNull(fluid, "fluid");
    }

    public BarrelData withFluid(FluidAmount next) {
        return new BarrelData(next, sealed, sealedTick, recipeTick, rainTick, rainSeen, pourTick, pourSeen, powered);
    }

    public BarrelData withRecipeTick(long tick) {
        return new BarrelData(fluid, sealed, sealedTick, tick, rainTick, rainSeen, pourTick, pourSeen, powered);
    }

    /** TFC {@code onSeal}: the seal tick, and the recipe tick when a recipe matches. */
    public BarrelData sealedAt(long now, boolean recipeMatches) {
        return new BarrelData(fluid, true, now, recipeMatches ? now : recipeTick, rainTick, rainSeen, pourTick, pourSeen, powered);
    }

    /** The rain clock after a settle at {@code tick} that saw rain ({@code seen}) or not. */
    public BarrelData withRain(long tick, boolean seen) {
        return new BarrelData(fluid, sealed, sealedTick, recipeTick, tick, seen, pourTick, pourSeen, powered);
    }

    /** PAR-PROC-38: the tap clock after a settle at {@code tick} that saw the tap open ({@code seen}) or not. */
    public BarrelData withPour(long tick, boolean seen) {
        return new BarrelData(fluid, sealed, sealedTick, recipeTick, rainTick, rainSeen, tick, seen, powered);
    }

    /** TFC {@code onUnseal}: both ticks cleared. */
    public BarrelData unsealed() {
        return new BarrelData(fluid, false, 0L, BarrelRules.NO_TICK, rainTick, rainSeen, pourTick, pourSeen, powered);
    }

    /** PAR-PROC-42: the recorded redstone signal after the barrel acted on {@code signal}. */
    public BarrelData withPowered(boolean signal) {
        return new BarrelData(fluid, sealed, sealedTick, recipeTick, rainTick, rainSeen, pourTick, pourSeen, signal);
    }
}
