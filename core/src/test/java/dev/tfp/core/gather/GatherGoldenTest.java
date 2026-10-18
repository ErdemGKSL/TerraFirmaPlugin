/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.gather;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/**
 * M3-GATH-01 golden values: the pickup and break yields of TFC's groundcover pieces and loose rocks at the pinned commit
 * (c3b4d9536).
 *
 * <p>The table below was produced by a scratch oracle that reads TFC's own data, not this code: the loot tables of
 * {@code src/main/resources/data/tfc/loot_table/blocks/groundcover/*}, {@code rock/loose/*} and {@code rock/mossy_loose/*}
 * (each loose rock count taken from its {@code set_count} functions whose {@code block_state_property count} condition
 * matches, default 1; {@code explosion_decay} is not a break rule and is left out), and the absence of
 * {@code requiresCorrectToolForDrops} in {@code GroundcoverBlock}, {@code LooseRockBlock}, {@code GroundcoverBlockType} and the
 * {@code LOOSE} and {@code MOSSY_LOOSE} declarations of {@code Rock.java}. Format of a row:
 * {@code G <TYPE> -> <item> x1} for groundcover, {@code L [MOSSY_]<ROCK> x<count> -> <item> x<count>} for loose rocks.
 */
class GatherGoldenTest {

    // from TFC at c3b4d9536: 17 groundcover rows, 126 loose rock rows (21 rocks x 2 kinds x 3 counts)
    private static final String GOLDEN = """
        G BONE -> minecraft:bone x1
        G CLAM -> tfc:groundcover/clam x1
        G DRIFTWOOD -> tfc:groundcover/driftwood x1
        G FEATHER -> minecraft:feather x1
        G FLINT -> minecraft:flint x1
        G GUANO -> tfc:groundcover/guano x1
        G HUMUS -> tfc:groundcover/humus x1
        G MOLLUSK -> tfc:groundcover/mollusk x1
        G MUSSEL -> tfc:groundcover/mussel x1
        G OBSIDIAN_SHARD -> tfc:groundcover/obsidian_shard x1
        G PINECONE -> tfc:groundcover/pinecone x1
        G PUMICE -> tfc:groundcover/pumice x1
        G ROTTEN_FLESH -> minecraft:rotten_flesh x1
        G SALT_LICK -> tfc:powder/salt x1
        G SEA_URCHIN -> tfc:groundcover/sea_urchin x1
        G SEAWEED -> tfc:groundcover/seaweed x1
        G STICK -> minecraft:stick x1
        L ANDESITE x1 -> tfc:rock/loose/andesite x1
        L ANDESITE x2 -> tfc:rock/loose/andesite x2
        L ANDESITE x3 -> tfc:rock/loose/andesite x3
        L BASALT x1 -> tfc:rock/loose/basalt x1
        L BASALT x2 -> tfc:rock/loose/basalt x2
        L BASALT x3 -> tfc:rock/loose/basalt x3
        L CHALK x1 -> tfc:rock/loose/chalk x1
        L CHALK x2 -> tfc:rock/loose/chalk x2
        L CHALK x3 -> tfc:rock/loose/chalk x3
        L CHERT x1 -> tfc:rock/loose/chert x1
        L CHERT x2 -> tfc:rock/loose/chert x2
        L CHERT x3 -> tfc:rock/loose/chert x3
        L CLAYSTONE x1 -> tfc:rock/loose/claystone x1
        L CLAYSTONE x2 -> tfc:rock/loose/claystone x2
        L CLAYSTONE x3 -> tfc:rock/loose/claystone x3
        L CONGLOMERATE x1 -> tfc:rock/loose/conglomerate x1
        L CONGLOMERATE x2 -> tfc:rock/loose/conglomerate x2
        L CONGLOMERATE x3 -> tfc:rock/loose/conglomerate x3
        L DACITE x1 -> tfc:rock/loose/dacite x1
        L DACITE x2 -> tfc:rock/loose/dacite x2
        L DACITE x3 -> tfc:rock/loose/dacite x3
        L DIORITE x1 -> tfc:rock/loose/diorite x1
        L DIORITE x2 -> tfc:rock/loose/diorite x2
        L DIORITE x3 -> tfc:rock/loose/diorite x3
        L DOLOMITE x1 -> tfc:rock/loose/dolomite x1
        L DOLOMITE x2 -> tfc:rock/loose/dolomite x2
        L DOLOMITE x3 -> tfc:rock/loose/dolomite x3
        L GABBRO x1 -> tfc:rock/loose/gabbro x1
        L GABBRO x2 -> tfc:rock/loose/gabbro x2
        L GABBRO x3 -> tfc:rock/loose/gabbro x3
        L GNEISS x1 -> tfc:rock/loose/gneiss x1
        L GNEISS x2 -> tfc:rock/loose/gneiss x2
        L GNEISS x3 -> tfc:rock/loose/gneiss x3
        L GRANITE x1 -> tfc:rock/loose/granite x1
        L GRANITE x2 -> tfc:rock/loose/granite x2
        L GRANITE x3 -> tfc:rock/loose/granite x3
        L LIMESTONE x1 -> tfc:rock/loose/limestone x1
        L LIMESTONE x2 -> tfc:rock/loose/limestone x2
        L LIMESTONE x3 -> tfc:rock/loose/limestone x3
        L MARBLE x1 -> tfc:rock/loose/marble x1
        L MARBLE x2 -> tfc:rock/loose/marble x2
        L MARBLE x3 -> tfc:rock/loose/marble x3
        L PHYLLITE x1 -> tfc:rock/loose/phyllite x1
        L PHYLLITE x2 -> tfc:rock/loose/phyllite x2
        L PHYLLITE x3 -> tfc:rock/loose/phyllite x3
        L QUARTZITE x1 -> tfc:rock/loose/quartzite x1
        L QUARTZITE x2 -> tfc:rock/loose/quartzite x2
        L QUARTZITE x3 -> tfc:rock/loose/quartzite x3
        L RHYOLITE x1 -> tfc:rock/loose/rhyolite x1
        L RHYOLITE x2 -> tfc:rock/loose/rhyolite x2
        L RHYOLITE x3 -> tfc:rock/loose/rhyolite x3
        L SCHIST x1 -> tfc:rock/loose/schist x1
        L SCHIST x2 -> tfc:rock/loose/schist x2
        L SCHIST x3 -> tfc:rock/loose/schist x3
        L SHALE x1 -> tfc:rock/loose/shale x1
        L SHALE x2 -> tfc:rock/loose/shale x2
        L SHALE x3 -> tfc:rock/loose/shale x3
        L SLATE x1 -> tfc:rock/loose/slate x1
        L SLATE x2 -> tfc:rock/loose/slate x2
        L SLATE x3 -> tfc:rock/loose/slate x3
        L TUFF x1 -> tfc:rock/loose/tuff x1
        L TUFF x2 -> tfc:rock/loose/tuff x2
        L TUFF x3 -> tfc:rock/loose/tuff x3
        L MOSSY_ANDESITE x1 -> tfc:rock/mossy_loose/andesite x1
        L MOSSY_ANDESITE x2 -> tfc:rock/mossy_loose/andesite x2
        L MOSSY_ANDESITE x3 -> tfc:rock/mossy_loose/andesite x3
        L MOSSY_BASALT x1 -> tfc:rock/mossy_loose/basalt x1
        L MOSSY_BASALT x2 -> tfc:rock/mossy_loose/basalt x2
        L MOSSY_BASALT x3 -> tfc:rock/mossy_loose/basalt x3
        L MOSSY_CHALK x1 -> tfc:rock/mossy_loose/chalk x1
        L MOSSY_CHALK x2 -> tfc:rock/mossy_loose/chalk x2
        L MOSSY_CHALK x3 -> tfc:rock/mossy_loose/chalk x3
        L MOSSY_CHERT x1 -> tfc:rock/mossy_loose/chert x1
        L MOSSY_CHERT x2 -> tfc:rock/mossy_loose/chert x2
        L MOSSY_CHERT x3 -> tfc:rock/mossy_loose/chert x3
        L MOSSY_CLAYSTONE x1 -> tfc:rock/mossy_loose/claystone x1
        L MOSSY_CLAYSTONE x2 -> tfc:rock/mossy_loose/claystone x2
        L MOSSY_CLAYSTONE x3 -> tfc:rock/mossy_loose/claystone x3
        L MOSSY_CONGLOMERATE x1 -> tfc:rock/mossy_loose/conglomerate x1
        L MOSSY_CONGLOMERATE x2 -> tfc:rock/mossy_loose/conglomerate x2
        L MOSSY_CONGLOMERATE x3 -> tfc:rock/mossy_loose/conglomerate x3
        L MOSSY_DACITE x1 -> tfc:rock/mossy_loose/dacite x1
        L MOSSY_DACITE x2 -> tfc:rock/mossy_loose/dacite x2
        L MOSSY_DACITE x3 -> tfc:rock/mossy_loose/dacite x3
        L MOSSY_DIORITE x1 -> tfc:rock/mossy_loose/diorite x1
        L MOSSY_DIORITE x2 -> tfc:rock/mossy_loose/diorite x2
        L MOSSY_DIORITE x3 -> tfc:rock/mossy_loose/diorite x3
        L MOSSY_DOLOMITE x1 -> tfc:rock/mossy_loose/dolomite x1
        L MOSSY_DOLOMITE x2 -> tfc:rock/mossy_loose/dolomite x2
        L MOSSY_DOLOMITE x3 -> tfc:rock/mossy_loose/dolomite x3
        L MOSSY_GABBRO x1 -> tfc:rock/mossy_loose/gabbro x1
        L MOSSY_GABBRO x2 -> tfc:rock/mossy_loose/gabbro x2
        L MOSSY_GABBRO x3 -> tfc:rock/mossy_loose/gabbro x3
        L MOSSY_GNEISS x1 -> tfc:rock/mossy_loose/gneiss x1
        L MOSSY_GNEISS x2 -> tfc:rock/mossy_loose/gneiss x2
        L MOSSY_GNEISS x3 -> tfc:rock/mossy_loose/gneiss x3
        L MOSSY_GRANITE x1 -> tfc:rock/mossy_loose/granite x1
        L MOSSY_GRANITE x2 -> tfc:rock/mossy_loose/granite x2
        L MOSSY_GRANITE x3 -> tfc:rock/mossy_loose/granite x3
        L MOSSY_LIMESTONE x1 -> tfc:rock/mossy_loose/limestone x1
        L MOSSY_LIMESTONE x2 -> tfc:rock/mossy_loose/limestone x2
        L MOSSY_LIMESTONE x3 -> tfc:rock/mossy_loose/limestone x3
        L MOSSY_MARBLE x1 -> tfc:rock/mossy_loose/marble x1
        L MOSSY_MARBLE x2 -> tfc:rock/mossy_loose/marble x2
        L MOSSY_MARBLE x3 -> tfc:rock/mossy_loose/marble x3
        L MOSSY_PHYLLITE x1 -> tfc:rock/mossy_loose/phyllite x1
        L MOSSY_PHYLLITE x2 -> tfc:rock/mossy_loose/phyllite x2
        L MOSSY_PHYLLITE x3 -> tfc:rock/mossy_loose/phyllite x3
        L MOSSY_QUARTZITE x1 -> tfc:rock/mossy_loose/quartzite x1
        L MOSSY_QUARTZITE x2 -> tfc:rock/mossy_loose/quartzite x2
        L MOSSY_QUARTZITE x3 -> tfc:rock/mossy_loose/quartzite x3
        L MOSSY_RHYOLITE x1 -> tfc:rock/mossy_loose/rhyolite x1
        L MOSSY_RHYOLITE x2 -> tfc:rock/mossy_loose/rhyolite x2
        L MOSSY_RHYOLITE x3 -> tfc:rock/mossy_loose/rhyolite x3
        L MOSSY_SCHIST x1 -> tfc:rock/mossy_loose/schist x1
        L MOSSY_SCHIST x2 -> tfc:rock/mossy_loose/schist x2
        L MOSSY_SCHIST x3 -> tfc:rock/mossy_loose/schist x3
        L MOSSY_SHALE x1 -> tfc:rock/mossy_loose/shale x1
        L MOSSY_SHALE x2 -> tfc:rock/mossy_loose/shale x2
        L MOSSY_SHALE x3 -> tfc:rock/mossy_loose/shale x3
        L MOSSY_SLATE x1 -> tfc:rock/mossy_loose/slate x1
        L MOSSY_SLATE x2 -> tfc:rock/mossy_loose/slate x2
        L MOSSY_SLATE x3 -> tfc:rock/mossy_loose/slate x3
        L MOSSY_TUFF x1 -> tfc:rock/mossy_loose/tuff x1
        L MOSSY_TUFF x2 -> tfc:rock/mossy_loose/tuff x2
        L MOSSY_TUFF x3 -> tfc:rock/mossy_loose/tuff x3
        """;

    private static List<String> rows() {
        return GOLDEN.lines().map(String::strip).filter(l -> !l.isEmpty()).toList();
    }

    private static GatherPiece pieceOf(String row) {
        String key = row.substring(0, row.indexOf(" -> ")).strip();
        if (key.startsWith("G ")) {
            return new GatherPiece.Groundcover(GroundcoverType.valueOf(key.substring(2)));
        }
        String[] parts = key.substring(2).split(" x");
        String name = parts[0];
        boolean mossy = name.startsWith("MOSSY_");
        LooseRockType rock = LooseRockType.valueOf(mossy ? name.substring("MOSSY_".length()) : name);
        return new GatherPiece.LooseRock(rock, mossy, Integer.parseInt(parts[1]));
    }

    private static List<GatherYield> expectedOf(String row) {
        String out = row.substring(row.indexOf(" -> ") + 4).strip();
        int x = out.lastIndexOf(" x");
        return List.of(new GatherYield(out.substring(0, x), Integer.parseInt(out.substring(x + 2))));
    }

    @Test
    void goldenTableCoversEveryTfcGroundcoverAndLooseRock() {
        List<String> rows = rows();
        assertThat(rows).hasSize(17 + 126);
        Set<String> groundcover = new TreeSet<>();
        Set<String> loose = new TreeSet<>();
        for (String row : rows) {
            if (row.startsWith("G ")) {
                groundcover.add(row.substring(2, row.indexOf(" ->")));
            } else {
                loose.add(row.substring(2, row.indexOf(" x")));
            }
        }
        assertThat(groundcover).hasSize(GroundcoverType.values().length);
        assertThat(loose).hasSize(LooseRockType.values().length * 2);
    }

    @Test
    void everyGoldenRowMatchesTfcInBothToolStates() {
        for (String row : rows()) {
            GatherPiece piece = pieceOf(row);
            List<GatherYield> expected = expectedOf(row);
            assertThat(GatherRules.drops(piece, false)).as(row).isEqualTo(expected);
            assertThat(GatherRules.drops(piece, true)).as(row).isEqualTo(expected);
            assertThat(GatherRules.breakDrops(piece, false, false)).as(row).isEqualTo(expected);
            assertThat(GatherRules.breakDrops(piece, true, false)).as(row).isEqualTo(expected);
        }
    }

    @Test
    void everyGoldenRowIsPickedUpInSurvivalAndNotInCreative() {
        for (String row : rows()) {
            GatherPiece piece = pieceOf(row);
            assertThat(GatherRules.pickup(piece, false)).as(row).isEqualTo(expectedOf(row));
            assertThat(GatherRules.pickup(piece, true)).as(row).isEmpty();
            assertThat(GatherRules.breakDrops(piece, true, true)).as(row).isEmpty();
        }
    }

    @Test
    void noGatherPieceRequiresACorrectTool() {
        for (String row : rows()) {
            assertThat(GatherRules.requiresCorrectTool(pieceOf(row))).as(row).isFalse();
        }
    }

    @Test
    void looseRockCountOutsideOneToThreeIsRejected() {
        assertThatThrownBy(() -> new GatherPiece.LooseRock(LooseRockType.GRANITE, false, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GatherPiece.LooseRock(LooseRockType.GRANITE, false, 4)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void yieldsAreStableAcrossRepeatedCalls() {
        // The rules have no random part (no explosion decay here), so repeated calls with the same input give the same list.
        GatherPiece piece = new GatherPiece.LooseRock(LooseRockType.SHALE, true, 3);
        List<GatherYield> first = GatherRules.drops(piece, true);
        for (int i = 0; i < 100; i++) {
            assertThat(GatherRules.drops(piece, true)).isEqualTo(first);
        }
    }
}
