/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/entities/misc/Seat.java @ c3b4d9536 (sit, getSittingEntity),
 *       src/main/java/net/dries007/tfc/common/blockentities/NestBoxBlockEntity.java @ c3b4d9536 (serverTick: the sitter)
 */
package dev.tfp.core.nestbox;

import dev.tfp.core.persist.VersionedCodec;

import java.util.Objects;

/**
 * PAR-FAUNA-07: one nest box's state: who sits in it. TFC finds the sitter by looking for a {@code Seat} entity at the
 * block; here the box stores the hen's id and the tick she sat down, and the lay is evaluated lazily from that tick
 * ({@link dev.tfp.core.livestock.products.NestRules#advance}). Immutable, any thread.
 *
 * <p>{@code seatedTick} is the first tick whose nest checks have not been rolled yet. The hen sits down at tick {@code t}:
 * the first check is the grid point at or after {@code t}. After a callback rolled the checks up to {@code now},
 * {@link #checkedThrough} moves it past them, so a later callback never rolls the same check twice (TFC rolls each
 * 30-tick check once).
 *
 * @param sitter     the hen's entity id, empty when nobody sits
 * @param seatedTick the first tick not yet examined, {@link #NOT_SEATED} when nobody sits
 */
public record NestBoxState(String sitter, long seatedTick) {
    public static final long NOT_SEATED = -1L;
    /** A new nest box: empty. */
    public static final NestBoxState FRESH = new NestBoxState("", NOT_SEATED);

    public static final VersionedCodec<NestBoxState> CODEC = VersionedCodec.<NestBoxState>builder("nestbox", 1)
        .build((s, out) -> out.putString("sitter", s.sitter()).putLong("seated", s.seatedTick()),
            in -> new NestBoxState(in.getString("sitter", ""), in.getLong("seated", NOT_SEATED)));

    public NestBoxState {
        Objects.requireNonNull(sitter, "sitter");
        if (sitter.isEmpty() != (seatedTick == NOT_SEATED)) {
            throw new IllegalArgumentException("a sitter has a seated tick and no sitter none: " + sitter + ", " + seatedTick);
        }
    }

    public boolean occupied() {
        return !sitter.isEmpty();
    }

    /** {@code hen} sits down at {@code now} (TFC {@code Seat.sit}). */
    public NestBoxState seat(String hen, long now) {
        if (hen.isEmpty()) {
            throw new IllegalArgumentException("a sitter needs an id");
        }
        return new NestBoxState(hen, now);
    }

    /** The checks through {@code now} were rolled without an event: the next one to roll is the first grid point after it. */
    public NestBoxState checkedThrough(long now) {
        if (!occupied()) {
            return this;
        }
        long next = Math.floorDiv(now, NestBoxRules.CHECK_INTERVAL) * NestBoxRules.CHECK_INTERVAL + 1;
        return new NestBoxState(sitter, Math.max(seatedTick, next));
    }

    /** The hen is gone (TFC {@code stopRiding}). */
    public NestBoxState vacated() {
        return FRESH;
    }
}
