/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/rock/LooseRockBlock.java @ c3b4d9536 (useItemOn stacking, COUNT 1 to 3),
 *       src/main/java/net/dries007/tfc/common/blocks/GroundcoverBlock.java @ c3b4d9536
 */
package dev.tfp.core.gather;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Block ids to {@link GatherPiece}s and the stacking rule of loose rocks (PAR-GATHER-01): {@code tfp:rock/loose/<rock>[count]},
 * {@code tfp:rock/mossy_loose/<rock>[count]} and {@code tfp:groundcover/<type>}. Pure.
 */
public final class GatherPieces {
    static final String LOOSE = "tfp:rock/loose/";
    static final String MOSSY_LOOSE = "tfp:rock/mossy_loose/";
    static final String GROUNDCOVER = "tfp:groundcover/";

    private GatherPieces() {
    }

    /** The piece a block state is, empty for every other block. A missing or invalid {@code count} reads as 1. */
    public static Optional<GatherPiece> of(String blockId, Map<String, String> properties) {
        boolean mossy = blockId.startsWith(MOSSY_LOOSE);
        if (mossy || blockId.startsWith(LOOSE)) {
            String name = blockId.substring((mossy ? MOSSY_LOOSE : LOOSE).length());
            for (LooseRockType rock : LooseRockType.values()) {
                if (rock.serializedName().equals(name)) {
                    return Optional.of(new GatherPiece.LooseRock(rock, mossy, count(properties)));
                }
            }
            return Optional.empty();
        }
        if (blockId.startsWith(GROUNDCOVER)) {
            String name = blockId.substring(GROUNDCOVER.length());
            for (GroundcoverType type : GroundcoverType.values()) {
                if (type.name().toLowerCase(Locale.ROOT).equals(name)) {
                    return Optional.of(new GatherPiece.Groundcover(type));
                }
            }
        }
        return Optional.empty();
    }

    private static int count(Map<String, String> properties) {
        try {
            int c = Integer.parseInt(properties.getOrDefault("count", "1"));
            return c >= 1 && c <= GatherPiece.LooseRock.MAX_COUNT ? c : 1;
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    /** The item id (our namespace) a loose rock block is placed from, {@code tfp:rock/loose/<rock>} or the mossy one. */
    public static String looseItem(GatherPiece.LooseRock rock) {
        return (rock.mossy() ? rock.rock().mossyItemId() : rock.rock().itemId()).replaceFirst("^tfc:", "tfp:");
    }

    /**
     * {@code LooseRockBlock.useItemOn}: the count after adding one more rock from the held item, or empty when it does not
     * stack (the held item is not this rock's item, or the block already has 3).
     */
    public static Optional<Integer> stack(GatherPiece.LooseRock block, String heldItemId) {
        if (block.count() >= GatherPiece.LooseRock.MAX_COUNT || !looseItem(block).equals(heldItemId)) {
            return Optional.empty();
        }
        return Optional.of(block.count() + 1);
    }

    /** The item id of a yield in our namespace: {@code tfc:} becomes {@code tfp:}, vanilla ids stay. */
    public static String ourItem(GatherYield yield) {
        return yield.itemId().replaceFirst("^tfc:", "tfp:");
    }
}
