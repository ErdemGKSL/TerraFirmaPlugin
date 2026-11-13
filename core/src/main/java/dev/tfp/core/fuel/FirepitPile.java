/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/util/events/StartFireEvent.java @ c3b4d9536 (startFire, the item scan)
 */
package dev.tfp.core.fuel;

import java.util.ArrayList;
import java.util.List;

/**
 * The scan of the items lying where a firepit would stand ({@code StartFireEvent.startFire}): sticks
 * ({@code #tfc:firepit_sticks}), kindling ({@code #tfc:firepit_kindling}) and up to four logs ({@code #tfc:firepit_logs},
 * one per firepit fuel slot), in TFC's order: the stick tag first, then kindling, then logs; a log stack found once four
 * logs are taken is not used. Every used entity is removed when the firepit is built, even a log stack only partly taken.
 */
public final class FirepitPile {
    /** Fuel slots of TFC's {@code AbstractFirepitBlockEntity} (0 to 3). */
    public static final int MAX_LOGS = 4;

    private FirepitPile() {
    }

    /** What one ground item stack is, by the firepit tags (in TFC's test order). */
    public enum Kind {
        STICK,
        KINDLING,
        LOG,
        OTHER
    }

    /** One ground item entity: its kind and stack size. */
    public record Entry(Kind kind, int count) {
    }

    /**
     * The result of a scan.
     *
     * @param sticks   sum of the stick stacks
     * @param kindling sum of the kindling stacks
     * @param logs     for each initial log (at most {@link #MAX_LOGS}), the index of the entry it came from
     * @param used     the indices of the entries removed when the firepit is built, in scan order
     */
    public record Scan(int sticks, int kindling, List<Integer> logs, List<Integer> used) {
        public Scan {
            logs = List.copyOf(logs);
            used = List.copyOf(used);
        }

        public boolean hasLogs() {
            return !logs.isEmpty();
        }
    }

    /** Scans the entries in order, as TFC's loop over {@code getEntitiesOfClass(ItemEntity.class, box)}. */
    public static Scan scan(List<Entry> entries) {
        int sticks = 0;
        int kindling = 0;
        List<Integer> logs = new ArrayList<>();
        List<Integer> used = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            Entry e = entries.get(i);
            if (e.kind() == Kind.STICK) {
                sticks += e.count();
                used.add(i);
            } else if (e.kind() == Kind.KINDLING) {
                kindling += e.count();
                used.add(i);
            } else if (logs.size() < MAX_LOGS && e.kind() == Kind.LOG) {
                for (int n = 0; n < e.count(); n++) {
                    if (logs.size() >= MAX_LOGS) {
                        break;
                    }
                    logs.add(i);
                }
                used.add(i);
            }
        }
        return new Scan(sticks, kindling, logs, used);
    }
}
