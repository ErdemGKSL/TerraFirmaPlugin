/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/LobsterTrapBlockEntity.java @ c3b4d9536, common/blocks/LobsterTrapBlock.java,
 * common/entities/ai/EnterLobsterTrapGoal.java
 */
package dev.tfp.core.fishing;

import dev.tfp.core.hunt.HuntDrops;
import dev.tfp.core.hunt.HuntKill;
import dev.tfp.core.hunt.HuntYield;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.DoubleSupplier;

/**
 * The lobster trap, TFC {@code LobsterTrapBlockEntity}: bait, a six-slot catch, the junk roll and the trap state. The trap has no timer:
 * a mob swims in when it is baited and catches the mob on arrival, so every rule here is an event, not a tick.
 *
 * <p>The slots hold one item each (TFC {@code getSlotStackLimit} is 1), so the contents are an item count. The item identities are
 * the caller's.
 */
public final class LobsterTrap {

    /** The catch slots of the trap. */
    public static final int CATCH_SLOTS = 6;
    /** The bait units one bait item adds. */
    public static final int BAIT_PER_ITEM = 3;
    /** The chance that a catch is junk rather than the mob's loot, TFC {@code JUNK_CHANCE} (a float). */
    public static final float JUNK_CHANCE = 0.1f;
    /** The species of {@code tfc:caught_in_lobster_traps}. */
    public static final Set<String> CAUGHT_SPECIES = Set.of("isopod", "lobster", "crayfish", "horseshoe_crab");

    private LobsterTrap() {
    }

    /** The state of the trap block, TFC {@code LobsterTrapBlock.TrapState}. */
    public enum State {
        /** Empty and unbaited: bait can be put in. */
        OPEN,
        /** Baited and with a free slot: animals swim in. */
        BAITED,
        /** Holding items with no bait, or with every slot full. */
        CLOSED
    }

    /**
     * The contents of the trap.
     *
     * @param bait     the bait units left, 0 to {@link #BAIT_PER_ITEM}
     * @param occupied the occupied catch slots, 0 to {@link #CATCH_SLOTS}
     */
    public record Contents(int bait, int occupied) {

        public Contents {
            if (bait < 0 || occupied < 0 || occupied > CATCH_SLOTS) {
                throw new IllegalArgumentException("bait " + bait + ", occupied " + occupied);
            }
        }

        /** An empty trap. */
        public static Contents empty() {
            return new Contents(0, 0);
        }
    }

    /**
     * The outcome of one catch.
     *
     * @param contents the contents after the catch
     * @param inserted the items put in the slots, each count as the units that fit
     * @param lost     the units that did not fit a slot and are lost, as TFC discards the insert remainder
     */
    public record Catch(Contents contents, List<HuntYield> inserted, int lost) {}

    /**
     * The outcome of taking the contents.
     *
     * @param contents    the contents after the take
     * @param items       the occupied slots that were emptied
     * @param baitCleared whether unused bait was cleared (only when no item was taken)
     */
    public record Take(Contents contents, int items, boolean baitCleared) {}

    /**
     * The state of the trap, TFC {@code updateTrapState}: closed when it holds items and either has no bait or no open slot; baited
     * when it has bait; open otherwise.
     *
     * @param contents the contents
     * @return the trap state
     */
    public static State state(Contents contents) {
        boolean hasItems = contents.occupied() > 0;
        boolean hasBait = contents.bait() > 0;
        boolean hasOpenSlot = contents.occupied() < CATCH_SLOTS;
        if (hasItems && (!hasBait || !hasOpenSlot)) {
            return State.CLOSED;
        }
        return hasBait ? State.BAITED : State.OPEN;
    }

    /**
     * Baits an open, empty trap, TFC {@code tryBait}: a lobster bait item adds {@link #BAIT_PER_ITEM} bait. Any other case returns the
     * contents unchanged, and the caller keeps the item.
     *
     * @param contents     the contents
     * @param lobsterBait  whether the item is in {@code tfc:lobster_bait}
     * @return the contents after the bait
     */
    public static Contents bait(Contents contents, boolean lobsterBait) {
        if (!lobsterBait || contents.bait() > 0 || contents.occupied() > 0) {
            return contents;
        }
        return new Contents(BAIT_PER_ITEM, 0);
    }

    /**
     * Whether the trap catches a mob that swims in, TFC {@code canCatch}: baited, with a free slot.
     *
     * @param contents the contents
     * @return whether the trap can catch
     */
    public static boolean canCatch(Contents contents) {
        return state(contents) == State.BAITED && contents.occupied() < CATCH_SLOTS;
    }

    /**
     * Catches one mob, TFC {@code catchEntity}. The junk roll is read first: below {@link #JUNK_CHANCE} the catch is one item of
     * {@code junkItems} (picked by a second draw), otherwise the mob's loot is rolled from its species table with the trap's kill, which
     * counts as a player kill. The bait drops by one unit, and the caller discards the mob.
     *
     * @param contents   the contents, with {@link #canCatch} true
     * @param species    the species of the mob, one of {@link #CAUGHT_SPECIES}
     * @param trapKill   the kill by the trap: a kill that counts as a player kill ({@code damageCountsAsPlayerKill})
     * @param junkItems  the items of {@code tfc:lobster_trap_junk}, in tag order
     * @param rng        a source of uniform draws in [0, 1)
     * @return the outcome
     * @throws IllegalStateException    if the trap cannot catch
     * @throws IllegalArgumentException if the species is not caught by traps, or the kill does not count as a player kill
     */
    public static Catch catchMob(Contents contents, String species, HuntKill trapKill, List<String> junkItems, DoubleSupplier rng) {
        if (!canCatch(contents)) {
            throw new IllegalStateException("the trap cannot catch: " + contents);
        }
        if (!CAUGHT_SPECIES.contains(species)) {
            throw new IllegalArgumentException("not caught by lobster traps: " + species);
        }
        if (!trapKill.damageCountsAsPlayerKill()) {
            throw new IllegalArgumentException("the trap kill must count as a player kill");
        }
        List<HuntYield> units = new ArrayList<>();
        if (rng.getAsDouble() < JUNK_CHANCE) {
            if (!junkItems.isEmpty()) {
                int index = (int) Math.floor(clampDraw(rng.getAsDouble()) * junkItems.size());
                units.add(new HuntYield(junkItems.get(Math.min(index, junkItems.size() - 1)), 1));
            }
        } else {
            units.addAll(HuntDrops.yields(species, trapKill, rng));
        }

        int free = CATCH_SLOTS - contents.occupied();
        List<HuntYield> inserted = new ArrayList<>();
        int lost = 0;
        for (HuntYield unit : units) {
            int take = Math.min(unit.count(), free);
            if (take > 0) {
                inserted.add(new HuntYield(unit.item(), take));
            }
            free -= take;
            lost += unit.count() - take;
        }
        int occupied = CATCH_SLOTS - free;
        return new Catch(new Contents(Math.max(0, contents.bait() - 1), occupied), inserted, lost);
    }

    /**
     * Takes the contents, TFC {@code takeContents}: every item leaves the trap; with none to take, unused bait is cleared.
     *
     * @param contents the contents
     * @return the outcome
     */
    public static Take take(Contents contents) {
        if (contents.occupied() > 0) {
            return new Take(new Contents(contents.bait(), 0), contents.occupied(), false);
        }
        if (contents.bait() > 0) {
            return new Take(Contents.empty(), 0, true);
        }
        return new Take(contents, 0, false);
    }

    private static double clampDraw(double draw) {
        return Math.min(Math.max(draw, 0d), 0.999_999_999_999d);
    }
}
