/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/SluiceBlockEntity.java @ c3b4d9536 (ticksRemaining,
 * loadAdditional, saveAdditional)
 */
package dev.tfp.core.sluice;

import dev.tfp.core.pan.SluiceRules;
import dev.tfp.core.persist.DataMap;
import dev.tfp.core.persist.VersionedCodec;
import java.util.ArrayList;
import java.util.List;

/**
 * The sluice device's own state (PAR-PROC-13), beside its 32 soil slots: the production countdown, whether the sluice was
 * fully structured (TFC {@code State.BOTH}) when it was last looked at, and the deposits produced but not yet rolled into
 * loot. Nothing ticks; {@link SluiceRules#advance} runs the gap since the last access with the stored {@code working}
 * flag, and the plugin re-reads the water on every neighbour change.
 *
 * @param ticksRemaining TFC {@code ticksRemaining}
 * @param working        whether input and output hold the same usable fluid
 * @param pending        deposit ids whose loot table has to be rolled at the output (bounded by the soil slots)
 */
public record SluiceState(int ticksRemaining, boolean working, List<String> pending) {
    public static final int MAX_PENDING = 256;
    /** A sluice nobody looked at yet: idle with a full countdown (TFC starts at 0, which also means one tick). */
    public static final SluiceState FRESH = new SluiceState(SluiceRules.DEFAULT_CYCLE_TICKS, false, List.of());

    public static final VersionedCodec<SluiceState> CODEC = VersionedCodec.<SluiceState>builder("sluice", 1)
        .build((s, out) -> {
            List<DataMap> ids = new ArrayList<>();
            for (String id : s.pending()) {
                ids.add(new DataMap().putString("id", id));
            }
            out.putInt("remaining", s.ticksRemaining()).putBoolean("working", s.working()).putList("pending", ids);
        }, in -> {
            List<String> pending = new ArrayList<>();
            for (DataMap e : in.getList("pending")) {
                pending.add(e.getString("id", ""));
            }
            return new SluiceState(in.getInt("remaining", SluiceRules.DEFAULT_CYCLE_TICKS), in.getBoolean("working", false),
                pending);
        });

    public SluiceState {
        pending = List.copyOf(pending.size() > MAX_PENDING ? pending.subList(0, MAX_PENDING) : pending);
    }

    public SluiceState withWorking(boolean next) {
        return new SluiceState(ticksRemaining, next, pending);
    }

    public SluiceState withRemaining(int next) {
        return new SluiceState(next, working, pending);
    }

    public SluiceState withPending(List<String> next) {
        return new SluiceState(ticksRemaining, working, next);
    }

    /** The state with {@code more} appended to the pending deposits. */
    public SluiceState produced(List<String> more) {
        if (more.isEmpty()) {
            return this;
        }
        List<String> all = new ArrayList<>(pending);
        all.addAll(more);
        return withPending(all);
    }
}
