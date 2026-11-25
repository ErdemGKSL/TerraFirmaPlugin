/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.livestockai;

import java.util.ArrayList;
import java.util.List;

/**
 * The layout of the livestock showcase pen ({@code /tfp debug livestock showcase}): one column per species in
 * {@link LivestockRoster#species()} order, wrapped into rows of {@value #COLUMNS}, a female in front and a male
 * {@value #PAIR_GAP} blocks behind her. Pure; the glue spawns the animals at these offsets from the pen corner.
 */
public final class LivestockShowcase {
    public static final int COLUMNS = 6;
    /** Blocks between two species columns (room for a horse or camel hitbox). */
    public static final int SPACING = 4;
    public static final int PAIR_GAP = 3;
    /** Blocks between two rows of species (the pair plus a walkway). */
    public static final int ROW_DEPTH = PAIR_GAP + SPACING;

    /** One animal of the pen: species, gender and block offset from the corner. */
    public record Slot(String species, boolean male, int dx, int dz) {
    }

    private LivestockShowcase() {
    }

    public static List<Slot> layout(List<String> species) {
        List<Slot> out = new ArrayList<>(species.size() * 2);
        for (int i = 0; i < species.size(); i++) {
            int dx = (i % COLUMNS) * SPACING;
            int dz = (i / COLUMNS) * ROW_DEPTH;
            out.add(new Slot(species.get(i), false, dx, dz));
            out.add(new Slot(species.get(i), true, dx, dz + PAIR_GAP));
        }
        return List.copyOf(out);
    }

    /** The pen's footprint in blocks along x and z (exclusive bounds of the offsets plus one). */
    public static int[] size(int speciesCount) {
        int cols = Math.min(COLUMNS, speciesCount);
        int rows = (speciesCount + COLUMNS - 1) / COLUMNS;
        return new int[] {(cols - 1) * SPACING + 1, (rows - 1) * ROW_DEPTH + PAIR_GAP + 1};
    }
}
