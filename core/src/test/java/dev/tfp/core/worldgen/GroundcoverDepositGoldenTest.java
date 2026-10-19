/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.worldgen;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.worldgen.deposit.DepositKind;
import dev.tfp.core.worldgen.deposit.Deposits;
import dev.tfp.core.worldgen.deposit.SoilDisc;
import dev.tfp.core.worldgen.groundcover.Groundcover;
import dev.tfp.core.worldgen.groundcover.LooseRocks;
import dev.tfp.core.worldgen.groundcover.ShoreGroundcover;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/**
 * Checks the placement table {@code groundcover_deposit.tsv}, exported from TFC's worldgen data at c3b4d9536 by a scratch
 * script (outside the repo; the source files are listed in its {@code source} column), against the Java rules of the
 * groundcover, loose rock and deposit packages.
 *
 * <p>Groups: {@code loose_rock} is the three placed loose rock features ({@code placed_feature/*loose_rock*.json});
 * {@code boulder}, {@code groundcover}, {@code ore_deposit} and {@code soil_disc} are the entries of the tags
 * {@code feature/boulders}, {@code feature/shore_decorations}, {@code feature/ore_deposits} and {@code feature/soil_discs};
 * {@code vein_indicator} is the distinct small ore indicators of the TFC vein configs. The table is the golden: the test needs
 * no generated data. A row with no Java rule must be listed in {@code groundcover_deposit_open_gaps.txt}; it gets no rule here.
 */
class GroundcoverDepositGoldenTest {
    static final String TABLE = "dev/tfp/core/worldgen/groundcover_deposit.tsv";

    /** The open gaps: the table ids with no Java rule, one per line, sorted. */
    static final String OPEN_GAPS_TABLE = "dev/tfp/core/worldgen/groundcover_deposit_open_gaps.txt";

    /** TFC's row counts per group at c3b4d9536: the placed loose rock features and the tag entries. */
    static final Map<String, Integer> TFC_FILE_COUNTS = Map.of("loose_rock", 3, "boulder", 6, "groundcover", 7,
        "ore_deposit", 8, "soil_disc", 6, "vein_indicator", 12);

    /** The TFC kind name of each {@link Groundcover.Kind}. Guano is a TFC kind with no Java kind. */
    static final Map<Groundcover.Kind, String> GROUNDCOVER_KINDS = Map.of(
        Groundcover.Kind.LOOSE_ROCK, "loose_rock",
        Groundcover.Kind.MOSSY_LOOSE_ROCK, "mossy_loose_rock",
        Groundcover.Kind.SMALL_ORE, "small_ore",
        Groundcover.Kind.DRIFTWOOD, "driftwood",
        Groundcover.Kind.CLAM, "clam",
        Groundcover.Kind.MOLLUSK, "mollusk",
        Groundcover.Kind.MUSSEL, "mussel",
        Groundcover.Kind.SEAWEED, "seaweed",
        Groundcover.Kind.STICK, "stick");

    /** The TFC kind name of each {@link DepositKind}, from the {@code with} blocks of the soil discs. */
    static final Map<DepositKind, String> DEPOSIT_KINDS = Map.of(
        DepositKind.ORE_DEPOSIT, "deposit",
        DepositKind.CLAY, "clay",
        DepositKind.CLAY_GRASS, "clay_grass",
        DepositKind.PEAT, "peat",
        DepositKind.PEAT_GRASS, "peat_grass");

    /** The TFC kinds that no Java kind covers: the only one is guano, whose shore patch is an open gap. */
    static final Set<String> TFC_KINDS_WITHOUT_JAVA = Set.of("guano");

    private static final String PREFIX = "tfc:";

    private static List<Map<String, String>> rows;
    private static Map<String, Map<String, String>> byId;

    @Test
    void rowCountsMatchTfcFileCounts() throws IOException {
        final Map<String, Integer> counts = new HashMap<>();
        for (Map<String, String> row : table()) {
            counts.merge(row.get("group"), 1, Integer::sum);
        }
        assertThat(counts).isEqualTo(TFC_FILE_COUNTS);
        final Set<String> ids = new HashSet<>();
        for (Map<String, String> row : table()) {
            assertThat(row.get("id")).startsWith(PREFIX);
            assertThat(ids.add(row.get("id"))).as("duplicate id %s", row.get("id")).isTrue();
        }
    }

    @Test
    void shorePatchesMatchJavaRarityClimateAndKind() throws IOException {
        int checked = 0;
        for (ShoreGroundcover.Patch patch : ShoreGroundcover.PATCHES) {
            final Map<String, String> row = row(patch.feature());
            assertThat(row.get("group")).as(patch.feature()).isEqualTo("groundcover");
            assertThat(GROUNDCOVER_KINDS.get(patch.kind())).as(patch.feature()).isEqualTo(row.get("kind"));
            assertThat(patch.rarity()).as("%s rarity", patch.feature()).isEqualTo(Integer.parseInt(row.get("rarity")));
            assertThat(patch.climate() != null).as("%s climate", patch.feature()).isEqualTo("1".equals(row.get("climate_present")));
            checked++;
        }
        assertThat(checked).isEqualTo(6);
    }

    @Test
    void depositsMatchJavaRarityClimateBiomesAndKinds() throws IOException {
        int checked = 0;
        for (Deposits.Feature feature : Deposits.FEATURES) {
            final Map<String, String> row = row(feature.feature());
            assertThat(row.get("group")).as(feature.feature()).isIn("ore_deposit", "soil_disc");
            assertThat(feature.rarity()).as("%s rarity", feature.feature()).isEqualTo(Integer.parseInt(row.get("rarity")));
            assertThat(feature.climate() != null).as("%s climate", feature.feature())
                .isEqualTo("1".equals(row.get("climate_present")));
            assertThat(new TreeSet<>(feature.biomes())).as("%s biomes", feature.feature())
                .isEqualTo(new TreeSet<>(stripped(row.get("biome_lists"), "soil_discs/")));
            assertThat(javaKinds(feature.disc())).as("%s kinds", feature.feature())
                .isEqualTo(new TreeSet<>(split(row.get("with_kinds"))));
            checked++;
        }
        assertThat(checked).isEqualTo(Deposits.FEATURES.size());
    }

    @Test
    void groundcoverKindsMatchTfcNames() throws IOException {
        assertThat(GROUNDCOVER_KINDS.keySet()).isEqualTo(EnumSet.allOf(Groundcover.Kind.class));
        final Set<String> tfcKinds = new TreeSet<>();
        for (Map<String, String> row : table()) {
            if (List.of("loose_rock", "groundcover", "vein_indicator").contains(row.get("group"))) {
                for (String kind : row.get("kind").split("\\|")) {
                    tfcKinds.add(kind);
                }
            }
        }
        final Set<String> javaKinds = new TreeSet<>(GROUNDCOVER_KINDS.values());
        assertThat(tfcKinds).containsAll(javaKinds);
        final Set<String> withoutJava = new TreeSet<>(tfcKinds);
        withoutJava.removeAll(javaKinds);
        assertThat(withoutJava).isEqualTo(TFC_KINDS_WITHOUT_JAVA);
    }

    @Test
    void depositKindsMatchTfcNames() throws IOException {
        assertThat(DEPOSIT_KINDS.keySet()).isEqualTo(EnumSet.allOf(DepositKind.class));
        final Set<String> covered = new TreeSet<>();
        for (Deposits.Feature feature : Deposits.FEATURES) {
            covered.addAll(split(row(feature.feature()).get("with_kinds")));
        }
        assertThat(covered).isEqualTo(new TreeSet<>(DEPOSIT_KINDS.values()));
    }

    @Test
    void unsupportedRowsAreOpenGaps() throws IOException {
        final Set<String> covered = new HashSet<>();
        covered.add("tfc:loose_rock");
        covered.add(LooseRocks.FEATURE);
        for (ShoreGroundcover.Patch patch : ShoreGroundcover.PATCHES) {
            covered.add(patch.feature());
        }
        for (Deposits.Feature feature : Deposits.FEATURES) {
            covered.add(feature.feature());
        }
        // The vein indicators: VeinIndicators places the small ore of every vein's indicator (the cave indicators are open,
        // M2-GEN-11), so every vein_indicator row counts as covered here.
        for (Map<String, String> row : table()) {
            if ("vein_indicator".equals(row.get("group"))) {
                covered.add(row.get("id"));
            }
        }
        final Set<String> gaps = new TreeSet<>();
        for (Map<String, String> row : table()) {
            if (!covered.contains(row.get("id"))) {
                gaps.add(row.get("id"));
            }
        }
        assertThat(gaps).isEqualTo(openGaps());
    }

    /** The Java kinds of a disc's replacement map, as TFC names. */
    private static Set<String> javaKinds(SoilDisc disc) {
        final Set<String> out = new TreeSet<>();
        for (DepositKind kind : disc.replace().values()) {
            out.add(DEPOSIT_KINDS.get(kind));
        }
        return out;
    }

    private static List<String> split(String cell) {
        return cell.isEmpty() ? List.of() : List.of(cell.split(";"));
    }

    private static List<String> stripped(String cell, String prefix) {
        final List<String> out = new ArrayList<>();
        for (String item : split(cell)) {
            assertThat(item).startsWith(prefix);
            out.add(item.substring(prefix.length()));
        }
        return out;
    }

    /** The table row of a TFC placed id ({@code tfc:...}). */
    private static Map<String, String> row(String id) throws IOException {
        final Map<String, String> row = byId().get(id);
        assertThat(row).as("table row for %s", id).isNotNull();
        return row;
    }

    private static Map<String, Map<String, String>> byId() throws IOException {
        if (byId == null) {
            final Map<String, Map<String, String>> out = new HashMap<>();
            for (Map<String, String> row : table()) {
                out.put(row.get("id"), row);
            }
            byId = out;
        }
        return byId;
    }

    private static List<Map<String, String>> table() throws IOException {
        if (rows == null) {
            rows = readTable();
        }
        return rows;
    }

    private static Set<String> openGaps() throws IOException {
        try (InputStream in = GroundcoverDepositGoldenTest.class.getClassLoader().getResourceAsStream(OPEN_GAPS_TABLE)) {
            assertThat(in).as("resource %s", OPEN_GAPS_TABLE).isNotNull();
            final Set<String> out = new TreeSet<>();
            for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
                if (!line.isBlank() && !line.startsWith("#")) {
                    out.add(line.trim());
                }
            }
            return out;
        }
    }

    private static List<Map<String, String>> readTable() throws IOException {
        try (InputStream in = GroundcoverDepositGoldenTest.class.getClassLoader().getResourceAsStream(TABLE)) {
            assertThat(in).as("resource %s", TABLE).isNotNull();
            final List<String> lines = List.of(new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n"));
            final String[] header = lines.get(0).split("\t", -1);
            final List<Map<String, String>> out = new ArrayList<>();
            for (String line : lines.subList(1, lines.size())) {
                if (line.isBlank()) {
                    continue;
                }
                final String[] cells = line.split("\t", -1);
                final Map<String, String> row = new HashMap<>();
                for (int i = 0; i < header.length; i++) {
                    row.put(header[i], i < cells.length ? cells[i] : "");
                }
                out.add(row);
            }
            return out;
        }
    }
}
