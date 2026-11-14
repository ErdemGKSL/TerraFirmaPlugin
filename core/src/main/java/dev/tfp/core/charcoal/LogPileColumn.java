/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/devices/LogPileBlock.java @ c3b4d9536 (insertAndPushUp, dumbInsert,
 *       extractFromTop),
 *       src/main/java/net/dries007/tfc/common/blockentities/LogPileBlockEntity.java @ c3b4d9536 (setAndUpdateSlots,
 *       suckLogsFromAbove, logCount, isEmpty),
 *       src/main/java/net/dries007/tfc/util/Helpers.java @ c3b4d9536 (insertOne, insertAllSlots)
 */
package dev.tfp.core.charcoal;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * The slot moves of a column of stacked log piles, as TFC's {@code insertAndPushUp} and {@code extractFromTop} make them.
 * A column is the clicked pile and the log piles stacked directly on it, bottom first; each pile is its sixteen slots
 * ({@code null} for an empty one), and a slot holds one log. Pure and generic over the log type: the caller reads the
 * piles' records, applies the result to the records and the block states, and turns an emptied pile into air.
 *
 * <p>Order of TFC's side effects kept: every pile a log went into runs {@code setAndUpdateSlots}, which first pulls the
 * logs of the pile above into its empty slots ({@code suckLogsFromAbove}, which updates the pile above the same way) and
 * then removes the pile when it is empty.
 */
public final class LogPileColumn {
    private LogPileColumn() {
    }

    /**
     * The column after an insert.
     *
     * @param piles    the piles bottom first, slot lists of {@link LogPile#SLOTS}; an all-empty pile became air; one more
     *                 than the input when {@code newPileOnTop}
     * @param inserted how many logs left the held stack
     * @param newPileOnTop whether a new pile was placed above the top pile
     */
    public record Insert<T>(List<List<T>> piles, int inserted, boolean newPileOnTop) {
        public Insert {
            piles = copy(piles);
        }
    }

    /**
     * The column after an extraction from its top pile.
     *
     * @param top       the top pile's slots after it; all empty when it became air
     * @param extracted the logs taken, in slot order
     */
    public record Extract<T>(List<T> top, List<T> extracted) {
        public Extract {
            top = java.util.Collections.unmodifiableList(new ArrayList<>(top));
            extracted = List.copyOf(extracted);
        }
    }

    /**
     * TFC {@code insertAndPushUp} from the clicked pile (index 0).
     *
     * @param column      the clicked pile and the piles stacked on it, bottom first, at least one
     * @param airAboveTop whether the block above the top pile is air (a new pile can be placed there)
     * @param log         one log of the held stack (every inserted slot gets this value)
     * @param stack       the held stack's count, {@code >= 1}
     * @param all         TFC's double click: insert as many as fit instead of one
     */
    public static <T> Insert<T> insert(List<List<T>> column, boolean airAboveTop, T log, int stack, boolean all) {
        Objects.requireNonNull(log, "log");
        if (column.isEmpty()) {
            throw new IllegalArgumentException("a column has at least one pile");
        }
        if (stack < 1) {
            throw new IllegalArgumentException("stack must be >= 1: " + stack);
        }
        List<Object[]> piles = arrays(column);
        int[] remaining = {stack};
        boolean newPile = false;
        int i = 0;
        while (true) {
            boolean inserted = dumbInsert(piles.get(i), log, remaining, all);
            if (inserted) {
                update(piles, i);
                if (!all) {
                    break;
                }
            }
            boolean full = count(piles.get(i)) == LogPile.SLOTS;
            if (i == piles.size() - 1) {
                if (airAboveTop && full && remaining[0] > 0) {
                    Object[] above = new Object[LogPile.SLOTS];
                    piles.add(above);
                    newPile = true;
                    dumbInsert(above, log, remaining, all);
                    update(piles, i + 1);
                }
                break;
            }
            if (!full) {
                break;
            }
            i++;
        }
        return new Insert<>(lists(piles), stack - remaining[0], newPile);
    }

    /**
     * TFC {@code extractFromTop}: one log (or, with {@code all}, every log) out of the top pile of the column, first slot
     * first.
     *
     * @param top the top pile's slots
     */
    public static <T> Extract<T> extractFromTop(List<T> top, boolean all) {
        Object[] pile = array(top);
        List<T> out = new ArrayList<>();
        for (int s = 0; s < pile.length; s++) {
            if (pile[s] != null) {
                @SuppressWarnings("unchecked")
                T log = (T) pile[s];
                out.add(log);
                pile[s] = null;
                if (!all) {
                    break;
                }
            }
        }
        @SuppressWarnings("unchecked")
        List<T> after = (List<T>) Arrays.asList(pile);
        return new Extract<>(after, out);
    }

    /** TFC {@code logCount}: the non-empty slots. */
    public static int count(List<?> pile) {
        int n = 0;
        for (Object o : pile) {
            if (o != null) {
                n++;
            }
        }
        return n;
    }

    private static int count(Object[] pile) {
        int n = 0;
        for (Object o : pile) {
            if (o != null) {
                n++;
            }
        }
        return n;
    }

    /** {@code dumbInsert}: {@code insertAllSlots} (one per empty slot) or {@code insertOne} (the first empty slot). */
    private static boolean dumbInsert(Object[] pile, Object log, int[] remaining, boolean all) {
        boolean any = false;
        for (int s = 0; s < pile.length && remaining[0] > 0; s++) {
            if (pile[s] == null) {
                pile[s] = log;
                remaining[0]--;
                any = true;
                if (!all) {
                    break;
                }
            }
        }
        return any;
    }

    /** {@code setAndUpdateSlots} of pile {@code i}: suck from the pile above, recursively; an empty pile is removed. */
    private static void update(List<Object[]> piles, int i) {
        if (i + 1 < piles.size()) {
            Object[] above = piles.get(i + 1);
            if (count(above) > 0) {
                Object[] pile = piles.get(i);
                for (int s = 0; s < above.length; s++) {
                    if (above[s] == null) {
                        continue;
                    }
                    for (int j = 0; j < pile.length; j++) {
                        if (pile[j] == null) {
                            pile[j] = above[s];
                            above[s] = null;
                            break;
                        }
                    }
                }
                update(piles, i + 1);
            }
        }
        // an empty pile becomes air: it stays in the list as all-empty slots for the caller
    }

    private static List<Object[]> arrays(List<? extends List<?>> column) {
        List<Object[]> out = new ArrayList<>(column.size() + 1);
        for (List<?> pile : column) {
            out.add(array(pile));
        }
        return out;
    }

    private static Object[] array(List<?> pile) {
        if (pile.size() != LogPile.SLOTS) {
            throw new IllegalArgumentException("a log pile has " + LogPile.SLOTS + " slots: " + pile.size());
        }
        return pile.toArray(new Object[0]);
    }

    @SuppressWarnings("unchecked")
    private static <T> List<List<T>> lists(List<Object[]> piles) {
        List<List<T>> out = new ArrayList<>(piles.size());
        for (Object[] p : piles) {
            out.add((List<T>) Arrays.asList(p));
        }
        return out;
    }

    private static <T> List<List<T>> copy(List<List<T>> piles) {
        List<List<T>> out = new ArrayList<>(piles.size());
        for (List<T> p : piles) {
            out.add(java.util.Collections.unmodifiableList(new ArrayList<>(p)));
        }
        return java.util.Collections.unmodifiableList(out);
    }
}
