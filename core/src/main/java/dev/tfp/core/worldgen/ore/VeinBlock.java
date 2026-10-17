/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.worldgen.ore;

/**
 * One block a vein can write in place of a rock: an entry of a vein's weighted list ({@code blocks[].with[].block}).
 *
 * @param id      the block id, e.g. {@code tfp:ore/rich_malachite/marble}
 * @param kind    what the block is
 * @param mineral the ore or deposit name without the grade ({@code malachite}), or the id's path for {@link Kind#OTHER}
 * @param grade   the ore grade; {@link OreGrade#UNGRADED} for ungraded ores, deposits and other blocks
 * @param vein    the name of the vein that lists this entry ({@code normal_malachite})
 */
public record VeinBlock(String id, Kind kind, String mineral, OreGrade grade, String vein) {
    /** What a vein block is. */
    public enum Kind {
        /** {@code <ns>:ore/<grade?><ore>/<rock>}. */
        ORE,
        /** {@code <ns>:deposit/<ore>/<rock>}: a placer deposit (gravel with ore), placed by cluster veins at a small weight. */
        DEPOSIT,
        /** Any other block. */
        OTHER
    }

    /** Parses an id of a vein entry: the kind, the mineral and the grade come from the path. */
    public static VeinBlock parse(String id, String vein) {
        final int colon = id.indexOf(':');
        final String[] path = id.substring(colon + 1).split("/");
        if (path.length == 3 && path[0].equals("ore")) {
            for (OreGrade grade : OreGrade.values()) {
                if (grade != OreGrade.UNGRADED && path[1].startsWith(grade.prefix())) {
                    return new VeinBlock(id, Kind.ORE, path[1].substring(grade.prefix().length()), grade, vein);
                }
            }
            return new VeinBlock(id, Kind.ORE, path[1], OreGrade.UNGRADED, vein);
        }
        if (path.length == 3 && path[0].equals("deposit")) {
            return new VeinBlock(id, Kind.DEPOSIT, path[1], OreGrade.UNGRADED, vein);
        }
        return new VeinBlock(id, Kind.OTHER, id.substring(colon + 1), OreGrade.UNGRADED, vein);
    }
}
