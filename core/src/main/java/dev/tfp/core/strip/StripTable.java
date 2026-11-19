/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/wood/LogBlock.java @ c3b4d9536 (getToolModifiedState, AXE_STRIP),
 *       src/main/java/net/dries007/tfc/common/blocks/wood/Wood.java @ c3b4d9536 (BlockType.stripped: LOG and WOOD only),
 *       src/main/java/net/dries007/tfc/common/blocks/BambooBlock.java @ c3b4d9536 (golden bamboo block strips to vanilla)
 */
package dev.tfp.core.strip;

import dev.tfp.core.worldgen.tree.Wood;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * The single table of what an axe strips among TFC's blocks: for each of the 20 woods the log to the stripped log and the wood
 * to the stripped wood, and the golden bamboo block to vanilla's stripped bamboo block. Stripped blocks themselves do not strip
 * further ({@code LogBlock} built with a null stripped supplier). Pure and immutable.
 */
public final class StripTable {
    private StripTable() {}

    /** TFC's golden bamboo block ({@code tfc:golden_bamboo_block}) as ours. */
    public static final String GOLDEN_BAMBOO_BLOCK = "tfp:golden_bamboo_block";

    /** TFC {@code Blocks.STRIPPED_BAMBOO_BLOCK}. */
    public static final String STRIPPED_BAMBOO_BLOCK = "minecraft:stripped_bamboo_block";

    /**
     * The properties {@code Helpers.copyProperties} carries over: every property both blocks have. Log, wood and their stripped
     * forms all have these two; vanilla's stripped bamboo block only {@code axis}.
     */
    public static final Set<String> COPIED = Set.of("axis", "branch_direction");

    private static final Map<String, String> PAIRS = build();

    private static Map<String, String> build() {
        Map<String, String> m = new LinkedHashMap<>();
        for (Wood wood : Wood.values()) {
            String n = wood.serializedName();
            m.put("tfp:wood/log/" + n, "tfp:wood/stripped_log/" + n);
            m.put("tfp:wood/wood/" + n, "tfp:wood/stripped_wood/" + n);
        }
        m.put(GOLDEN_BAMBOO_BLOCK, STRIPPED_BAMBOO_BLOCK);
        return Map.copyOf(m);
    }

    /** Every (block, stripped block) pair of the table. */
    public static Map<String, String> pairs() {
        return PAIRS;
    }

    /** The block id an axe turns {@code blockId} into, empty when it does not strip. */
    public static Optional<String> stripped(String blockId) {
        return Optional.ofNullable(PAIRS.get(blockId));
    }

    /**
     * TFC {@code Helpers.copyProperties(stripped.defaultBlockState(), state)}: the properties of the source that the stripped
     * block takes over. The stripped bamboo block has no {@code branch_direction}, so it is left out for the bamboo row.
     */
    public static Map<String, String> copyProperties(String strippedId, Map<String, String> source) {
        Map<String, String> out = new TreeMap<>();
        for (Map.Entry<String, String> e : source.entrySet()) {
            if (!COPIED.contains(e.getKey())) {
                continue;
            }
            if (e.getKey().equals("branch_direction") && strippedId.equals(STRIPPED_BAMBOO_BLOCK)) {
                continue;
            }
            out.put(e.getKey(), e.getValue());
        }
        return out;
    }
}
