/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/entities/ai/prey/PrepareRamNearestTargetTFC.java @ c3b4d9536 (start, tick,
 * stop, chooseRamPosition), ai/prey/RamTargetTFC.java (start, tick, finishRam), ai/prey/RammingPreyAi.java
 * (initRamActivity, the RAM_COOLDOWN_TICKS gate), same commit
 */
package dev.tfp.core.prey;

/**
 * The ram of one {@code RammingPrey} as an immutable state with pure transitions. The cooldown is stored as the absolute
 * tick it ends at (TFC counts {@code RAM_COOLDOWN_TICKS} down every tick; here it is lazy).
 *
 * <ul>
 *   <li>{@link Phase#COOLDOWN}: waits until {@code cooldownUntil}; a nearby attacker erases the wait ({@link #provoked}).</li>
 *   <li>{@link Phase#PREPARING}: {@code PrepareRamNearestTargetTFC}. The start position is the rammer's own block (TFC's
 *       change from vanilla). When the target leaves its block the start and target blocks are re-anchored; once the
 *       rammer has stood on its start block for {@link RamRules#RAM_PREPARE_TIME} ticks the charge begins. A lost target
 *       or the 160-tick timeout fails, with a 10-tick cooldown.</li>
 *   <li>{@link Phase#CHARGING}: {@code RamTargetTFC}. Runs at speed 3 to the far edge of the target's block; a hit or the
 *       arrival ends it with a fresh cooldown.</li>
 * </ul>
 *
 * @param phase        the phase
 * @param cooldownUntil the tick the cooldown ends at (COOLDOWN)
 * @param startedAt    the tick the current phase began (PREPARING, CHARGING)
 * @param reachedAt    the tick the rammer first stood on its start block, or -1 (PREPARING)
 * @param start        the start block {x, y, z} (PREPARING)
 * @param target       the target block {x, y, z} (PREPARING)
 * @param ramTarget    the charge destination {x, y, z} (CHARGING)
 * @param direction    the knockback direction {x, z} (CHARGING)
 * @param multiplier   the damage multiplier (CHARGING)
 */
public record RamCharge(Phase phase, long cooldownUntil, long startedAt, long reachedAt, int[] start, int[] target,
                        double[] ramTarget, double[] direction, float multiplier) {

    public enum Phase { COOLDOWN, PREPARING, CHARGING }

    /** The state after {@code initMemories}: a cooldown drawn at spawn. */
    public static RamCharge cooling(long until) {
        return new RamCharge(Phase.COOLDOWN, until, 0, -1, null, null, null, null, 1f);
    }

    /** Whether the RAM activity may run: no cooldown memory ({@code RAM_COOLDOWN_TICKS} absent). */
    public boolean ready(long now) {
        return phase == Phase.COOLDOWN && now >= cooldownUntil;
    }

    /** {@code EraseMemoryIf(PredatorAi::hasNearbyAttacker, RAM_COOLDOWN_TICKS)}: a nearby attacker erases the cooldown. */
    public RamCharge provoked(long now) {
        return phase == Phase.COOLDOWN && now < cooldownUntil ? cooling(now) : this;
    }

    /** {@code PrepareRamNearestTargetTFC.start} with a found target: start at the rammer's own block. */
    public static RamCharge prepare(long now, int[] preyBlock, int[] targetBlock) {
        return new RamCharge(Phase.PREPARING, 0, now, -1, preyBlock.clone(), targetBlock.clone(), null, null, 1f);
    }

    /** {@code PrepareRamNearestTargetTFC.stop} without a ram target: the 10-tick cooldown. */
    public static RamCharge failed(long now) {
        return cooling(now + RamRules.COOLDOWN_ON_FAIL);
    }

    /**
     * One tick of {@code PrepareRamNearestTargetTFC}.
     *
     * @param now         the tick
     * @param preyBlock   the rammer's block
     * @param targetBlock the target's block, or {@code null} if it is dead or gone
     * @return the next state
     */
    public RamCharge tickPrepare(long now, int[] preyBlock, int[] targetBlock) {
        if (phase != Phase.PREPARING) {
            return this;
        }
        if (targetBlock == null || now - startedAt >= RamRules.PREPARE_TIMEOUT) {
            return failed(now);
        }
        if (!java.util.Arrays.equals(targetBlock, target)) {
            // the target moved: re-anchor at the current block, keep the reached timestamp
            return new RamCharge(Phase.PREPARING, 0, startedAt, reachedAt, preyBlock.clone(), targetBlock.clone(), null, null, 1f);
        }
        if (!java.util.Arrays.equals(preyBlock, start)) {
            return this;
        }
        final long reached = reachedAt < 0 ? now : reachedAt;
        if (now - reached < RamRules.RAM_PREPARE_TIME) {
            return reached == reachedAt ? this
                : new RamCharge(Phase.PREPARING, 0, startedAt, reached, start, target, null, null, 1f);
        }
        final float mult = RamRules.damageMultiplier(preyBlock[0], preyBlock[1], preyBlock[2], target[0], target[1], target[2]);
        final double[] edge = RamRules.edgeOfBlock(preyBlock[0], preyBlock[2], target[0], target[1], target[2]);
        final double[] dir = RamRules.ramDirection(preyBlock[0], preyBlock[2], edge[0], edge[2]);
        return new RamCharge(Phase.CHARGING, 0, now, -1, null, null, edge, dir, mult);
    }

    /**
     * One tick of {@code RamTargetTFC}.
     *
     * @param now      the tick
     * @param hit      an entity matching the ram conditions is inside the inflated hit box (it takes the hit)
     * @param arrived  the walk target is gone (reached or unreachable)
     * @param cooldown the cooldown drawn for {@code finishRam}, see {@link RamRules#cooldown}
     * @return the next state
     */
    public RamCharge tickCharge(long now, boolean hit, boolean arrived, int cooldown) {
        if (phase != Phase.CHARGING) {
            return this;
        }
        if (hit || arrived || now - startedAt >= RamRules.RAM_TIMEOUT) {
            return cooling(now + cooldown);
        }
        return this;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof RamCharge r && phase == r.phase && cooldownUntil == r.cooldownUntil && startedAt == r.startedAt
            && reachedAt == r.reachedAt && java.util.Arrays.equals(start, r.start) && java.util.Arrays.equals(target, r.target)
            && java.util.Arrays.equals(ramTarget, r.ramTarget) && java.util.Arrays.equals(direction, r.direction)
            && Float.compare(multiplier, r.multiplier) == 0;
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(phase, cooldownUntil, startedAt, reachedAt, java.util.Arrays.hashCode(start),
            java.util.Arrays.hashCode(target), java.util.Arrays.hashCode(ramTarget), java.util.Arrays.hashCode(direction), multiplier);
    }

    @Override
    public String toString() {
        return "RamCharge[" + phase + ", cooldownUntil=" + cooldownUntil + ", startedAt=" + startedAt + ", reachedAt=" + reachedAt
            + ", start=" + java.util.Arrays.toString(start) + ", target=" + java.util.Arrays.toString(target)
            + ", ramTarget=" + java.util.Arrays.toString(ramTarget) + ", multiplier=" + multiplier + "]";
    }
}
