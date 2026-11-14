/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/TFCTorchBlock.java @ c3b4d9536 (onRandomTick, useItemOn, setPlacedBy),
 *       src/main/java/net/dries007/tfc/common/blocks/TFCWallTorchBlock.java @ c3b4d9536 (randomTick, useItemOn),
 *       src/main/java/net/dries007/tfc/common/blocks/DeadTorchBlock.java @ c3b4d9536,
 *       src/main/java/net/dries007/tfc/common/items/TorchItem.java @ c3b4d9536 (useOn, onEntityItemUpdate),
 *       src/main/java/net/dries007/tfc/ForgeEventHandler.java @ c3b4d9536 (onFireStart, onFireStop: the torch branches),
 *       src/main/java/net/dries007/tfc/common/blocks/TFCBlocks.java @ c3b4d9536 (TORCH, WALL_TORCH, DEAD_TORCH,
 *       DEAD_WALL_TORCH)
 */
package dev.tfp.core.light;

import java.util.Optional;
import java.util.function.IntUnaryOperator;

/**
 * The torch state machine of TFC, pure and lazy. Four blocks: the lit {@link Kind#TORCH} and {@link Kind#WALL_TORCH}
 * carry the calendar tick they were placed or lit at (TFC's {@code TickCounterBlockEntity}); the
 * {@link Kind#DEAD_TORCH} and {@link Kind#DEAD_WALL_TORCH} carry nothing. A wall torch keeps its facing through every
 * swap ({@code withPropertiesOf(state)}).
 *
 * <ul>
 *   <li>Burnout ({@code onRandomTick}): a lit torch whose elapsed time is above {@code torchTicks} becomes the dead kind.
 *       TFC checks on random ticks; the answer depends only on the elapsed time, so {@link #afterCatchUp} at any moment
 *       gives what a stepped check gives ({@link LightSourceRules#burnedOut}).</li>
 *   <li>Fire start ({@code onFireStart}, weak or strong alike): a lit torch resets its counter; a dead torch becomes the
 *       lit kind with a fresh counter. Both cancel the event.</li>
 *   <li>Douse ({@code onFireStop}): a lit torch becomes the dead kind; a dead one is left alone.</li>
 *   <li>Use ({@code useItemOn}): a lit torch (the wall torch asks the standing torch) always consumes the click; holding an
 *       item of {@code #tfc:can_be_lit_on_torch} it takes one and gives a torch. A dead torch has no use.</li>
 * </ul>
 */
public final class TorchRules {
    /** TFC {@code TFCBlocks.TORCH} and friends, by their port ids. */
    public enum Kind {
        TORCH("tfp:torch", true, false),
        WALL_TORCH("tfp:wall_torch", true, true),
        DEAD_TORCH("tfp:dead_torch", false, false),
        DEAD_WALL_TORCH("tfp:dead_wall_torch", false, true);

        private final String id;
        private final boolean lit;
        private final boolean wall;

        Kind(String id, boolean lit, boolean wall) {
            this.id = id;
            this.lit = lit;
            this.wall = wall;
        }

        /** The CraftEngine block id. */
        public String id() {
            return id;
        }

        /** Whether it is a lit torch (has a tick counter and emits light 14). */
        public boolean lit() {
            return lit;
        }

        /** Whether it hangs on a wall (has a horizontal {@code facing}). */
        public boolean wall() {
            return wall;
        }

        /** The dead kind of the same shape: TFC {@code DEAD_TORCH} / {@code DEAD_WALL_TORCH.withPropertiesOf(state)}. */
        public Kind dead() {
            return wall ? DEAD_WALL_TORCH : DEAD_TORCH;
        }

        /** The lit kind of the same shape: TFC {@code TORCH} / {@code WALL_TORCH.withPropertiesOf(state)}. */
        public Kind relit() {
            return wall ? WALL_TORCH : TORCH;
        }

        /** The light it emits (TFC {@code lightLevel(14)} on the lit torches, none on the dead ones). */
        public int light() {
            return LightSourceRules.torchLight(lit);
        }

        /** The kind of a block id, or empty for any other block. */
        public static Optional<Kind> byId(String id) {
            for (Kind k : values()) {
                if (k.id.equals(id)) {
                    return Optional.of(k);
                }
            }
            return Optional.empty();
        }
    }

    /** What a fire start does to a torch ({@code onFireStart}). */
    public enum FireStart {
        /** Not a torch kind this rule knows: the event goes on. */
        NONE,
        /** A lit torch: the counter is reset to now ({@code resetCounter}), the event is cancelled. */
        RESET,
        /** A dead torch: it becomes the lit kind with the counter at now, the event is cancelled. */
        RELIT;

        /** TFC {@code event.setCanceled(true)}. */
        public boolean handled() {
            return this != NONE;
        }
    }

    /** TFC {@code TorchItem.onEntityItemUpdate}: a dropped torch on a lit-by-torch block waits this many ticks in air. */
    public static final int DROPPED_AGE_ABOVE = 20;
    /** ... and this many when the item is inside the block. */
    public static final int DROPPED_AGE_INSIDE = 160;
    /** ... and then lights it with this chance per tick. */
    public static final float DROPPED_LIGHT_CHANCE = 0.01f;
    /** TFC: at most this many torches of a larger stack go out per tick in water. */
    public static final int EXTINGUISH_BATCH = 5;

    private TorchRules() {
    }

    /**
     * The kind a torch has at {@code now}: a lit torch lit at {@code litTick} is the dead kind once it burnt out
     * ({@link LightSourceRules#burnedOut}{@code (now - litTick, torchTicks)}); a dead torch stays dead.
     *
     * @param torchTicks TFC {@code torchTicks}; {@code -1} (or any value {@code <= 0}) disables burnout
     */
    public static Kind afterCatchUp(Kind kind, long litTick, long now, int torchTicks) {
        if (!kind.lit()) {
            return kind;
        }
        return LightSourceRules.burnedOut(now - litTick, torchTicks) ? kind.dead() : kind;
    }

    /**
     * The first tick at which a torch lit at {@code litTick} is burnt out: {@code litTick + torchTicks + 1}, or
     * {@link LightSourceRules#NEVER} when burnout is off.
     */
    public static long burnoutTick(long litTick, int torchTicks) {
        if (torchTicks <= 0) {
            return LightSourceRules.NEVER;
        }
        return litTick + torchTicks + 1L;
    }

    /** TFC {@code onFireStart}: the torch branches do not test {@code isStrong()}, so any start counts. */
    public static FireStart fireStart(Kind kind) {
        return kind.lit() ? FireStart.RESET : FireStart.RELIT;
    }

    /** TFC {@code onFireStop}: a lit torch is doused into the dead kind; a dead torch is not a fire. */
    public static Optional<Kind> douse(Kind kind) {
        return kind.lit() ? Optional.of(kind.dead()) : Optional.empty();
    }

    /** TFC {@code useItemOn} of a torch: whether the click is consumed (a lit torch: always; a dead one: no use). */
    public static boolean useConsumes(Kind kind) {
        return kind.lit();
    }

    /**
     * TFC {@code useItemOn}: whether a lit torch takes one of the held item and gives a lit torch. {@code canBeLit} is
     * whether the held item is in {@code #tfc:can_be_lit_on_torch} ({@code #c:rods/wooden}).
     */
    public static boolean lightsHeldItem(Kind kind, boolean canBeLit) {
        return kind.lit() && canBeLit;
    }

    /**
     * TFC {@code TorchItem.onEntityItemUpdate} in a fire-extinguishing fluid: the torches of a dropped stack of
     * {@code count} that go out this tick: {@code count > 5 ? 1 + nextInt(5) : count}. Each one is rolled as the torch's
     * loot with {@code burnt_out} set.
     *
     * @param nextInt {@code RandomSource.nextInt(bound)}; called once, and only for a stack above 5
     */
    public static int extinguished(int count, IntUnaryOperator nextInt) {
        if (count < 1) {
            throw new IllegalArgumentException("count must be >= 1: " + count);
        }
        return count > EXTINGUISH_BATCH ? 1 + nextInt.applyAsInt(EXTINGUISH_BATCH) : count;
    }

    /**
     * TFC {@code onEntityItemUpdate} on a {@code #tfc:lit_by_dropped_torch} block: the age a dropped torch needs before
     * its one-in-a-hundred chance per tick to start a strong fire there.
     *
     * @param aboveTheBlock the item's own block is empty and the block below is the one checked
     */
    public static int droppedAgeRequirement(boolean aboveTheBlock) {
        return aboveTheBlock ? DROPPED_AGE_ABOVE : DROPPED_AGE_INSIDE;
    }
}
