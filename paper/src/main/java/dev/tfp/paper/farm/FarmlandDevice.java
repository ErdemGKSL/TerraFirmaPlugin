/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/FarmlandBlockEntity.java @ c3b4d9536 (nutrients, additionalWater,
 *       waterTick, saveAdditional/loadAdditional),
 *       src/main/java/net/dries007/tfc/common/blocks/soil/FarmlandBlock.java @ c3b4d9536 (setPlacedBy)
 */
package dev.tfp.paper.farm;

import dev.tfp.core.clock.ClockSnapshot;
import dev.tfp.core.device.DeviceRecord;
import dev.tfp.core.farm.FarmlandState;
import dev.tfp.core.farm.Nutrients;
import dev.tfp.core.persist.VersionedCodec;
import dev.tfp.paper.device.DeviceContext;
import dev.tfp.paper.device.DeviceHandler;
import dev.tfp.paper.device.DeviceKind;
import dev.tfp.paper.device.DeviceResult;
import java.util.function.Supplier;

/**
 * PAR-FARM-01: the farmland device kind {@code tfp:farmland} (covers {@code tfp:farmland/<soil>}). The record holds the
 * nutrients and the water reserve ({@link FarmlandState}); the record's own last-update tick is TFC's water tick. Lazy: a
 * catch-up over any gap grows the reserve in one step ({@link FarmlandState#waterTick}), no ticking. The calendar ticks of the
 * gap come from the clock, taking the calendar rate as constant over it.
 */
public final class FarmlandDevice {
    public static final String ID = "tfp:farmland";

    /** The persisted state: the three nutrients, the reserve and the water tick. */
    public static final VersionedCodec<FarmlandState> CODEC = VersionedCodec.<FarmlandState>builder("farmland", 1)
        .build((s, out) -> out.putFloat("n", s.nutrients().nitrogen())
                .putFloat("p", s.nutrients().phosphorus())
                .putFloat("k", s.nutrients().potassium())
                .putFloat("water", s.additionalWater())
                .putLong("waterTick", s.lastWaterTick()),
            in -> new FarmlandState(new Nutrients(clamp01(in.getFloat("n", 0f)), clamp01(in.getFloat("p", 0f)),
                clamp01(in.getFloat("k", 0f))), Math.max(0f, Math.min(15f, in.getFloat("water", 0f))),
                in.getLong("waterTick", 0L)));

    private FarmlandDevice() {
    }

    /** Whether a block id is one of the farmland blocks. */
    public static boolean kindCovers(String blockId) {
        return blockId.equals(ID) || blockId.startsWith(ID + "/");
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : Math.min(v, 1f);
    }

    /** @param clock the global calendar clock (one snapshot per catch-up) */
    public static DeviceKind<FarmlandState> kind(Supplier<ClockSnapshot> clock) {
        return DeviceKind.builder(ID, CODEC, FarmlandState.fresh(0L))
            .slots(0)
            .removal(DeviceKind.RemoveBehaviour.NOOP)
            .advance((record, from, to) -> {
                ClockSnapshot c = clock.get();
                long calendarTo = c.calendarTicks();
                // calendar ticks of the gap: the global clock now, minus the gap at the calendar rate
                long calendarFrom = Math.max(0L, calendarTo - (long) (dev.tfp.core.berry.BerryClock.CALENDAR_TICK_RATE * (to - from)));
                return record.withState(record.state().waterTick(calendarFrom, calendarTo, to));
            })
            .handler(new Handler())
            .build();
    }

    /** TFC {@code setPlacedBy}: a fresh farmland, its water tick now. */
    private static final class Handler implements DeviceHandler<FarmlandState> {
        @Override
        public DeviceResult<FarmlandState> placed(DeviceContext<FarmlandState> ctx) {
            DeviceRecord<FarmlandState> record = ctx.record().withState(FarmlandState.fresh(ctx.now()));
            return DeviceResult.update(record);
        }
    }
}
