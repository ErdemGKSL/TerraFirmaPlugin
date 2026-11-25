/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/NestBoxBlockEntity.java @ c3b4d9536 (SLOTS, getSlotStackLimit,
 *       isItemValid, serverTick: the 30-tick grid and the hatch scan, Helpers.hasOpenSlot / insertOne)
 */
package dev.tfp.core.nestbox;

import dev.tfp.core.calendar.CalendarTicks;
import dev.tfp.core.livestock.products.EggRules;
import dev.tfp.core.livestock.products.NestRules;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

/**
 * PAR-FAUNA-07: the nest box's own rules, pure. The hen's side (readiness, the 1 in 7 roll, the egg) is
 * {@link NestRules}; this class is the box: four slots of one egg each, what it accepts, where an egg goes, and which
 * eggs hatch. Nothing here is ticked: TFC's {@code serverTick} acts only when {@code gameTime % 30 == 0}, so the box is
 * evaluated on that grid by the callbacks of the device framework, and {@link #delayToCheck} says how long to the next
 * grid point.
 */
public final class NestBoxRules {
    /** TFC {@code NestBoxBlockEntity.SLOTS}. */
    public static final int SLOTS = NestRules.SLOTS;
    /** TFC {@code getSlotStackLimit}: one egg per slot. */
    public static final int SLOT_LIMIT = 1;
    /** TFC {@code level.getGameTime() % 30}: the check grid. */
    public static final long CHECK_INTERVAL = NestRules.CHECK_INTERVAL;

    private NestBoxRules() {
    }

    /**
     * TFC {@code isItemValid}: {@code stack.has(TFCComponents.EGG) && !FoodCapability.isRotten(stack)}. A plain egg has
     * no egg component and is refused; so is a rotten one.
     *
     * @param hasEggComponent whether the stack carries TFC's egg component (our egg record)
     * @param rotten          whether the stack is rotten now
     */
    public static boolean accepts(boolean hasEggComponent, boolean rotten) {
        return hasEggComponent && !rotten;
    }

    /** What the box needs to know of one slot to decide on hatching. */
    public sealed interface Slot permits Empty, Other, Egg {
    }

    /** An empty slot. */
    public record Empty() implements Slot {
    }

    /** A stack that is no egg (cannot be inserted, but a plugin or an old save may leave one). */
    public record Other() implements Slot {
    }

    /**
     * An egg: TFC {@code EggComponent}. A plain egg cannot be in a slot (see {@link #accepts}), so every egg here has the
     * component; an unfertilized one never hatches.
     *
     * @param fertilized TFC {@code EggComponent.fertilized}
     * @param hatchDay   TFC {@code EggComponent.hatchDay}
     * @param rotten     whether the egg is rotten now
     */
    public record Egg(boolean fertilized, long hatchDay, boolean rotten) implements Slot {
    }

    /** TFC {@code Helpers.hasOpenSlot}: some slot is empty. */
    public static boolean hasOpenSlot(List<? extends Slot> slots) {
        return firstFree(slots).isPresent();
    }

    /** TFC {@code Helpers.insertOne}: eggs go into the first empty slot. */
    public static OptionalInt firstFree(List<? extends Slot> slots) {
        for (int i = 0; i < slots.size(); i++) {
            if (slots.get(i) instanceof Empty) {
                return OptionalInt.of(i);
            }
        }
        return OptionalInt.empty();
    }

    /**
     * TFC {@code egg != null && egg.canHatch(stack)} for one slot at calendar tick {@code calendarTicks}: a fertilized egg
     * whose hatch day has come and that is not rotten ({@link EggRules#canHatch}).
     */
    public static boolean hatches(Slot slot, long calendarTicks) {
        return slot instanceof Egg e && e.fertilized() && !e.rotten()
            && e.hatchDay() <= CalendarTicks.totalDays(calendarTicks);
    }

    /** The slots that hatch at {@code calendarTicks}, in TFC's order (slot 0 up). */
    public static List<Integer> hatching(List<? extends Slot> slots, long calendarTicks) {
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < slots.size(); i++) {
            if (hatches(slots.get(i), calendarTicks)) {
                out.add(i);
            }
        }
        return out;
    }

    /**
     * Player ticks from {@code now} to the first check on the 30-tick grid ({@code checkTick >= now + wait}). {@code wait}
     * is the ticks to wait before anything can happen, {@code 0} for "the next check".
     */
    public static long delayToCheck(long now, long wait) {
        return NestRules.firstCheckAtOrAfter(now + Math.max(0L, wait)) - now;
    }

    /**
     * Ticks from {@code now} until the first grid check at which some fertilized, fresh egg of {@code slots} hatches, or
     * {@code -1} when none will. A hatch day already reached gives the next grid check.
     *
     * @param calendarTicks the calendar tick at {@code now}
     */
    public static long delayToHatch(List<? extends Slot> slots, long now, long calendarTicks) {
        long best = -1L;
        for (Slot s : slots) {
            if (s instanceof Egg e && e.fertilized() && !e.rotten()) {
                long wait = Math.max(0L, e.hatchDay() * (long) CalendarTicks.CALENDAR_TICKS_IN_DAY - calendarTicks);
                long delay = delayToCheck(now, wait);
                if (best < 0 || delay < best) {
                    best = delay;
                }
            }
        }
        return best;
    }
}
