/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/LoomBlockEntity.java @ c3b4d9536 (onRightClick, tick, progress,
 * lastPushed, needsProgressUpdate, saveAdditional)
 */
package dev.tfp.core.loom;

import dev.tfp.core.persist.VersionedCodec;

/**
 * The loom's own state (PAR-PROC-06), beside its two slots (the yarn and the finished cloth).
 *
 * <p>TFC counts a push in its tick: a push sets {@code needsProgressUpdate} and {@code lastPushed}; the first tick at least
 * {@link LoomRules#PUSH_TICKS} later adds one to the progress. Nothing ticks here: {@code pending} stands for
 * {@code needsProgressUpdate} and {@link LoomRules#settle} adds the step lazily when the loom is next looked at.
 *
 * @param progress   TFC {@code progress}: pushes finished, up to the recipe's steps
 * @param lastPushed the player tick of the last push, {@link #NEVER} before the first
 * @param pending    a push whose step has not been counted yet
 */
public record LoomState(int progress, long lastPushed, boolean pending) {
    public static final long NEVER = Long.MIN_VALUE / 2;
    public static final LoomState FRESH = new LoomState(0, NEVER, false);

    public static final VersionedCodec<LoomState> CODEC = VersionedCodec.<LoomState>builder("loom", 1)
        .build((s, out) -> out.putInt("progress", s.progress()).putLong("pushed", s.lastPushed())
            .putBoolean("pending", s.pending()),
            in -> new LoomState(Math.max(0, in.getInt("progress", 0)), in.getLong("pushed", NEVER),
                in.getBoolean("pending", false)));

    /** TFC {@code clearRecipe}: progress back to zero (the push time stays, as in TFC). */
    public LoomState cleared() {
        return new LoomState(0, lastPushed, false);
    }
}
