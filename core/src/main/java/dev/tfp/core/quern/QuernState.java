/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/QuernBlockEntity.java @ c3b4d9536 (recipeTimer, isGrinding,
 * startGrinding: recipeTimer = MANUAL_TICKS, serverTick: finishGrinding once the timer reaches 0)
 */
package dev.tfp.core.quern;

import dev.tfp.core.persist.VersionedCodec;

/**
 * The quern's own state (PAR-PROC-04): when the running grind started, in player ticks, or {@link #IDLE}.
 *
 * <p>TFC counts a {@code recipeTimer} down from {@link QuernRules#TICKS_PER_GRIND} (90) by one per tick and finishes the
 * grind at 0. Here nothing is ticked: the start tick is stored and the grind is finished lazily once
 * {@code now >= start + 90} ({@link #due}), which is the same moment for a manual grind. The axle-driven grind of a
 * rotation network (PAR-MECH-06) will store its progress here instead and use {@link QuernRules#progress}.
 *
 * @param grindStart the player tick the grind started, or {@link #IDLE}
 */
public record QuernState(long grindStart) {
    /** No grind running (TFC {@code recipeTimer == 0}). */
    public static final long IDLE = Long.MIN_VALUE;
    public static final QuernState FRESH = new QuernState(IDLE);

    public static final VersionedCodec<QuernState> CODEC = VersionedCodec.<QuernState>builder("quern", 1)
        .build((s, out) -> out.putLong("start", s.grindStart()), in -> new QuernState(in.getLong("start", IDLE)));

    /** Whether a grind was started and not finished yet (it may be due). */
    public boolean started() {
        return grindStart != IDLE;
    }

    /** The player tick the running grind finishes at; only meaningful when {@link #started()}. */
    public long finishTick() {
        return grindStart + QuernRules.TICKS_PER_GRIND;
    }

    /** TFC {@code isGrinding()} at {@code now}: started and not yet at its finish tick. */
    public boolean grinding(long now) {
        return started() && now < finishTick();
    }

    /** Whether the running grind has reached its finish tick at {@code now} and must be finished. */
    public boolean due(long now) {
        return started() && now >= finishTick();
    }

    /** The manual progress at {@code now}, 0 to 90 (TFC {@code MANUAL_TICKS - recipeTimer}). */
    public double progress(long now) {
        return started() ? QuernRules.manualProgress(Math.max(0, now - grindStart)) : 0;
    }

    /** TFC {@code startGrinding}: the grind starts at {@code now}. */
    public QuernState start(long now) {
        if (now == IDLE) throw new IllegalArgumentException("start tick must not be IDLE");
        return new QuernState(now);
    }

    /** TFC {@code finishGrinding}: {@code recipeTimer = 0}. */
    public QuernState idle() {
        return FRESH;
    }
}
