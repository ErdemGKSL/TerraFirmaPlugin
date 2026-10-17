/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Placeholder for TFC's rock lookup (M2-GEN-08). TFC maps a region rock id and a layer to a rock through the datapack rock
 * settings, which are not ported yet. The categories of the 21 raw rocks are TFC's (tools/datagen TfcTables, @ c3b4d9536),
 * and the supertype of a point is TFC's (ChooseRocks: ocean, volcanic, land, uplift). The choice inside a category is ours.
 */
package dev.tfp.core.worldgen.chunk;

import java.util.List;

/**
 * The raw rock that a region rock id and a layer give until the datapack rock settings are ported. The names are the raw
 * rock ids {@code tfp:rock/raw/<name>} of the content, in the order of {@link #names()}.
 */
public final class PlaceholderRocks {
    /** The supertype bits of a region rock id: the lower two bits, as {@code ChooseRocks.TYPE_BITS}. */
    private static final int TYPE_BITS = 2;

    private static final String[] NAMES = {
        // Ocean (sedimentary)
        "shale", "claystone", "limestone", "conglomerate", "dolomite", "chert", "chalk", "tuff",
        // Volcanic (igneous extrusive)
        "rhyolite", "basalt", "andesite", "dacite",
        // Land (igneous intrusive)
        "granite", "diorite", "gabbro",
        // Uplift (metamorphic)
        "quartzite", "slate", "phyllite", "schist", "gneiss", "marble",
    };
    /** The first index of each supertype in {@link #NAMES}: ocean, volcanic, land, uplift. */
    private static final int[] OFFSET = {0, 8, 12, 15};
    private static final int[] SIZE = {8, 4, 3, 6};

    static {
        if (OFFSET.length != SIZE.length || OFFSET[3] + SIZE[3] != NAMES.length) {
            throw new AssertionError("placeholder rock table is inconsistent");
        }
    }

    private PlaceholderRocks() {}

    /** @return The 21 raw rock names, in index order. */
    public static List<String> names() {
        return List.of(NAMES);
    }

    /** @return The number of raw rocks: the size of {@link #names()}. */
    public static int count() {
        return NAMES.length;
    }

    /**
     * @param index an index of {@link #names()}
     * @return Whether the rock is mafic, as TFC's rock settings mark it: basalt and gabbro. Used for the beach sand colour.
     */
    public static boolean isMafic(int index) {
        final String name = NAMES[index];
        return name.equals("basalt") || name.equals("gabbro");
    }

    /**
     * The index in {@link #names()} of the rock at a region rock id and a rock layer. The supertype (lower two bits) picks the
     * category; the upper bits and the layer pick the rock inside it, so the layers of one point differ.
     *
     * @param rock  The region rock id ({@code Region.Point.rock}).
     * @param layer The rock layer, 0 at the surface ({@code ChunkDataGenerator.RockSample.layer}).
     */
    public static int index(int rock, int layer) {
        final int type = rock & ((1 << TYPE_BITS) - 1);
        return OFFSET[type] + Math.floorMod((rock >>> TYPE_BITS) + layer, SIZE[type]);
    }
}
