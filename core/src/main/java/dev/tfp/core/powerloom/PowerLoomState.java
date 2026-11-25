/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/rotation/PowerLoomBlockEntity.java @ c3b4d9536 (progressTicker),
 * file: src/main/java/net/dries007/tfc/common/blockentities/LoomBlockEntity.java @ c3b4d9536 (progress, lastPushed,
 *       needsProgressUpdate, saveAdditional)
 */
package dev.tfp.core.powerloom;

import dev.tfp.core.craft.CraftOutput;
import dev.tfp.core.craft.LoomRecipe;
import dev.tfp.core.craft.LoomRules;
import dev.tfp.core.machine.PowerLoomRules;
import dev.tfp.core.persist.VersionedCodec;

/**
 * The power loom's own state (PAR-MECH-08), beside its two slots (the yarn and the finished cloth). Nothing ticks: the state
 * is caught up to the tick it is looked at ({@link PowerLoomCatchUp}), at the rotation speed the last look saw.
 *
 * <p>The recipe is not part of the state, but its two numbers are, so the catch-up stays pure: {@code steps} (0 for no recipe)
 * and {@code inputCount} are written whenever the yarn slot changes, with {@code slotCount} the yarn in the slot.
 *
 * @param progress   TFC {@code progress}: steps done, up to {@code steps}
 * @param lastPushed the tick of the last push
 * @param pending    TFC {@code needsProgressUpdate}: a push whose step is not counted yet
 * @param ticker     TFC {@code progressTicker}: rotation gathered since the last push
 * @param speed      the signed speed in radians per tick the network gave at the last look; 0 for none
 * @param steps      TFC {@code recipe.getStepCount()}, 0 when the slot holds no recipe
 * @param inputCount TFC {@code recipe.getInputCount()}
 * @param slotCount  the yarn count in the recipe slot
 * @param pushes     pushes made since the block was placed, so a look can tell that the weave sound is due
 */
public record PowerLoomState(int progress, long lastPushed, boolean pending, float ticker, float speed, int steps,
                             int inputCount, int slotCount, int pushes) {
    /** A new power loom: nothing woven, no network. */
    public static final PowerLoomState FRESH = new PowerLoomState(0, 0L, false, 0f, 0f, 0, 0, 0, 0);

    public static final VersionedCodec<PowerLoomState> CODEC = VersionedCodec.<PowerLoomState>builder("power_loom", 1)
        .build((s, out) -> out.putInt("progress", s.progress()).putLong("pushed", s.lastPushed())
            .putBoolean("pending", s.pending()).putFloat("ticker", s.ticker()).putFloat("speed", s.speed())
            .putInt("steps", s.steps()).putInt("count", s.inputCount()).putInt("slot", s.slotCount())
            .putInt("pushes", s.pushes()),
            in -> new PowerLoomState(Math.max(0, in.getInt("progress", 0)), in.getLong("pushed", 0L),
                in.getBoolean("pending", false), in.getFloat("ticker", 0f), in.getFloat("speed", 0f),
                Math.max(0, in.getInt("steps", 0)), Math.max(0, in.getInt("count", 0)), Math.max(0, in.getInt("slot", 0)),
                in.getInt("pushes", 0)));

    /** The state with the recipe numbers of the yarn slot now ({@code steps} 0 for none). Progress resets with the recipe. */
    public PowerLoomState withRecipe(int steps, int inputCount, int slotCount) {
        return new PowerLoomState(progress, lastPushed, pending, ticker, speed, steps, inputCount, slotCount, pushes);
    }

    /** TFC {@code clearRecipe}: progress back to 0 and no step waiting (the push time and the ticker stay). */
    public PowerLoomState cleared() {
        return new PowerLoomState(0, lastPushed, false, ticker, speed, steps, inputCount, slotCount, pushes);
    }

    public PowerLoomState withSpeed(float next) {
        return new PowerLoomState(progress, lastPushed, pending, ticker, next, steps, inputCount, slotCount, pushes);
    }

    /** Whether looking at this loom at the next tick can change anything: it turns, or a step is waiting. */
    public boolean active() {
        return speed != 0f || pending;
    }

    /** The loom as the shared weave rules read it. */
    PowerLoomRules.State rules() {
        return new PowerLoomRules.State(ticker, new LoomRules.State(progress, pending, lastPushed));
    }

    PowerLoomState with(PowerLoomRules.State rules, int morePushes) {
        return new PowerLoomState(rules.loom().progress(), rules.loom().lastPushed(), rules.loom().pending(),
            rules.progressTicker(), speed, steps, inputCount, slotCount, pushes + morePushes);
    }

    /** The recipe stand-in the weave rules need: only its two counts matter to them. */
    LoomRecipe recipe() {
        return steps <= 0 || inputCount <= 0 ? null
            : new LoomRecipe("tfp:power_loom", "tfp:yarn", inputCount, new CraftOutput("tfp:cloth", 1), steps, "");
    }
}
