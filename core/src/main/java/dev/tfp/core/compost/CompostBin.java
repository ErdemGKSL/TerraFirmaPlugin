/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/ComposterBlockEntity.java @ c3b4d9536 (randomTick and
 * checkAndSetEmpty as the lazy settle, use as add and extract, finishUse, reset, resetCounter, setAndUpdateSlots),
 * src/main/java/net/dries007/tfc/common/blocks/devices/TFCComposterBlock.java @ c3b4d9536 (CompostType)
 *
 * Lazy: TFC ripens the bin on a random tick. Here {@link #settle} ripens it from the time since the last update, so the
 * caller settles at the moment it reads or changes the bin. The rotten infestation is a world effect and is not here.
 */
package dev.tfp.core.compost;

import dev.tfp.core.compost.CompostBin.Outcome;

/**
 * One composter's contents: green and brown amounts, the block-state type, the calendar tick of the last change, and the
 * items in its output slot. Immutable; every change returns a new bin. Every interaction must call {@link #settle} first,
 * as TFC's random ticks would have done by then.
 *
 * @param green      green amount, {@code 0..16}
 * @param brown      brown amount, {@code 0..16}
 * @param type       the block-state type
 * @param lastUpdate the calendar tick of the last addition or reset, or {@link CompostRules#NEVER}
 * @param output     the items in the output slot: 1 compost when {@link Type#READY}, the rotten count when {@link Type#ROTTEN}
 */
public record CompostBin(int green, int brown, Type type, long lastUpdate, int output) {

    /** The block-state type ({@code TFCComposterBlock.CompostType}). */
    public enum Type {
        NORMAL,
        READY,
        ROTTEN
    }

    /** What an addition did. */
    public enum Outcome {
        ADDED_GREEN,
        ADDED_BROWN,
        /** Green offered to a full green amount: nothing taken, nothing reset. */
        FULL_GREEN,
        FULL_BROWN,
        /** Any other offer to a rotten bin: nothing taken. */
        ROTTEN_REJECT,
        POISONED,
        /** The item is not an addition; the block gets the use. */
        PASS
    }

    /**
     * The result of an addition.
     *
     * @param bin      the bin after the addition
     * @param outcome  what happened
     * @param consumed the items taken from the stack (0 for a creative player, a reject or a pass)
     */
    public record Insert(CompostBin bin, Outcome outcome, int consumed) {
    }

    /**
     * The result of an extraction. An extraction always resets the bin, even one that is not ready, as TFC does.
     *
     * @param bin    the reset bin
     * @param compost the compost given out
     * @param rotten  the rotten compost given out
     */
    public record Extraction(CompostBin bin, int compost, int rotten) {
    }

    public CompostBin {
        CompostRules.checkAmount(green, "green");
        CompostRules.checkAmount(brown, "brown");
        if (type == null) {
            throw new IllegalArgumentException("type must not be null");
        }
        if (output < 0) {
            throw new IllegalArgumentException("output must be >= 0: " + output);
        }
    }

    /** A bin that was never filled. */
    public static CompostBin empty() {
        return new CompostBin(0, 0, Type.NORMAL, CompostRules.NEVER, 0);
    }

    /** Whether both amounts are at the maximum. */
    public boolean isFull() {
        return green == CompostRules.MAX_AMOUNT && brown == CompostRules.MAX_AMOUNT;
    }

    /** The block-state stage for this bin. */
    public int stage() {
        return CompostRules.stage(green, brown);
    }

    /**
     * Ripens the bin to {@code now}, as TFC's {@code randomTick} would have. A ready bin whose output was taken is reset first
     * (TFC's {@code checkAndSetEmpty}). A full, non-rotten bin becomes ready once the ticks since its last update are strictly
     * more than {@code readyTicks}. Calling it again at the same {@code now} changes nothing.
     *
     * @param now        the calendar tick
     * @param readyTicks the ready ticks at the bin now ({@link CompostRules#readyTicks})
     * @return the settled bin
     */
    public CompostBin settle(long now, long readyTicks) {
        if (type == Type.READY && output == 0) {
            return reset(now);
        }
        if (type == Type.READY) {
            return this;
        }
        if (isFull() && type != Type.ROTTEN && now - lastUpdate > readyTicks) {
            return new CompostBin(green, brown, Type.READY, lastUpdate, 1);
        }
        return this;
    }

    /**
     * The first calendar tick at which {@link #settle} makes this bin ready, for a full, normal bin with fixed ready ticks.
     * Any other bin returns {@link CompostRules#NOT_RIPENING}.
     *
     * @param readyTicks the ready ticks, held fixed
     * @return the tick, or {@code NOT_RIPENING}
     */
    public long ripenedAt(long readyTicks) {
        if (isFull() && type == Type.NORMAL) {
            return lastUpdate + readyTicks + 1;
        }
        return CompostRules.NOT_RIPENING;
    }

    /**
     * Offers an item to the bin, as TFC's {@code use} does with a held stack. Green goes to green, brown to brown, poison
     * rots the bin, and a rotten bin rejects everything else. A green or brown addition resets the timer to {@code now} and
     * takes the stack unless the player is creative. A poison addition keeps the timer.
     *
     * @param addition the held item's addition
     * @param creative whether the player is in creative mode
     * @param now      the calendar tick
     * @return the result
     */
    public Insert add(CompostAddition addition, boolean creative, long now) {
        final int consumed = creative ? 0 : 1;
        if (addition.kind() == CompostAddition.Kind.GREEN && green <= CompostRules.MAX_AMOUNT) {
            if (green == CompostRules.MAX_AMOUNT) {
                return new Insert(finished(), Outcome.FULL_GREEN, 0);
            }
            final CompostBin next = new CompostBin(Math.min(green + addition.amount(), CompostRules.MAX_AMOUNT), brown, type, now, output);
            return new Insert(next.finished(), Outcome.ADDED_GREEN, consumed);
        }
        if (addition.kind() == CompostAddition.Kind.BROWN && brown <= CompostRules.MAX_AMOUNT) {
            if (brown == CompostRules.MAX_AMOUNT) {
                return new Insert(finished(), Outcome.FULL_BROWN, 0);
            }
            final CompostBin next = new CompostBin(green, Math.min(brown + addition.amount(), CompostRules.MAX_AMOUNT), type, now, output);
            return new Insert(next.finished(), Outcome.ADDED_BROWN, consumed);
        }
        if (type == Type.ROTTEN) {
            return new Insert(finished(), Outcome.ROTTEN_REJECT, 0);
        }
        if (addition.kind() == CompostAddition.Kind.POISON) {
            final CompostBin next = new CompostBin(green, brown, Type.ROTTEN, lastUpdate, output);
            return new Insert(next.finished(), Outcome.POISONED, consumed);
        }
        return new Insert(this, Outcome.PASS, 0);
    }

    /**
     * Empties the bin by a shift-use with an empty hand, as TFC's {@code use}. A full, non-rotten bin gives one compost if it
     * is ready and nothing otherwise; any other bin gives its rotten count from the output slot. Then the bin is reset.
     *
     * @param now the calendar tick
     * @return what was given out and the reset bin
     */
    public Extraction extract(long now) {
        final boolean compostBin = isFull() && type != Type.ROTTEN;
        final int request = compostBin ? 1 : CompostRules.rottenCount(green, brown);
        final int taken = Math.min(request, output);
        return new Extraction(reset(now), compostBin ? taken : 0, compostBin ? 0 : taken);
    }

    /**
     * The output slot changed outside the block (a hopper, or the player). An empty slot in a ready bin resets it, as TFC's
     * {@code setAndUpdateSlots} does.
     *
     * @param count the items left in the output slot
     * @param now   the calendar tick
     * @return the bin
     */
    public CompostBin outputChanged(int count, long now) {
        if (count < 0) {
            throw new IllegalArgumentException("count must be >= 0: " + count);
        }
        if (type == Type.READY && count == 0) {
            return reset(now);
        }
        return new CompostBin(green, brown, type, lastUpdate, count);
    }

    /** The empty bin at {@code now}, as TFC's {@code reset} (which also restarts the timer). */
    public CompostBin reset(long now) {
        return new CompostBin(0, 0, Type.NORMAL, now, 0);
    }

    /** TFC's {@code finishUse} output rule: a rotten bin's output slot holds its rotten count. */
    private CompostBin finished() {
        if (type == Type.ROTTEN) {
            return new CompostBin(green, brown, type, lastUpdate, CompostRules.rottenCount(green, brown));
        }
        return this;
    }
}
