/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/generated/resources/data/tfc/tags/entity_type/{universal,cold,desert,tropical}_pests.json,
 * src/generated/resources/data/tfc/tfc/fauna/{isopod,lobster,crayfish,horseshoe_crab}.json,
 * src/generated/resources/data/tfc/tags/entity_type/caught_in_lobster_traps.json,
 * src/main/resources/data/tfc/worldgen/biome/*.json (the spawners), src/main/java/net/dries007/tfc/common/entities/TFCEntities.java
 * @ c3b4d9536 (golden values only)
 */
package dev.tfp.core.critter;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * Golden tests for the pest and critter table. {@code critter_rules.tsv} is exported from TFC at c3b4d9536 by a scratch
 * script (kept outside the repository): one row per species, eight in all, four pests and four water critters. The header
 * records the file counts the export saw: four pest tag files plus four critter fauna files make eight, the row count.
 *
 * <p>Golden spot checks: the biome spawner files list the critters with the weights below (for example
 * {@code coastal_dunes.json}, {@code water_ambient}: isopod 1, lobster 3, horseshoe crab 1). The pests have no spawner entries
 * at all; they come only from infestations.
 */
class CritterTableGoldenTest {
    private static final String RESOURCE = "/dev/tfp/core/critter/critter_rules.tsv";
    private static final int ROWS = 8;
    private static final int ENTITY_REGISTRATIONS = 8;
    private static final int PEST_TAG_FILES = 4;
    private static final int CRITTER_FAUNA_FILES = 4;
    private static final int BIOME_FILES = 125;

    record Row(String species, String kind, String category, String flavour, String group, int groupIndex, int groupSize,
               float width, float height, boolean faunaFile, boolean trapTag, int spawnEntries, int spawnWeight) {
    }

    @Test
    void fileAndRowCountsMatchTfc() throws IOException {
        Map<String, String> header = header();
        assertThat(Integer.parseInt(header.get("entity_registrations"))).isEqualTo(ENTITY_REGISTRATIONS);
        assertThat(Integer.parseInt(header.get("pest_tag_files"))).isEqualTo(PEST_TAG_FILES);
        assertThat(Integer.parseInt(header.get("critter_fauna_files"))).isEqualTo(CRITTER_FAUNA_FILES);
        assertThat(Integer.parseInt(header.get("pest_fauna_files"))).isZero();
        assertThat(Integer.parseInt(header.get("biome_files"))).isEqualTo(BIOME_FILES);
        List<Row> rows = rows();
        assertThat(rows).hasSize(ROWS);
        assertThat(rows.size()).isEqualTo(PEST_TAG_FILES + CRITTER_FAUNA_FILES);
    }

    @Test
    void namesAndKindsMatchTheEnums() throws IOException {
        List<String> critters = rows().stream().filter(r -> r.kind().equals("critter")).map(Row::species).toList();
        List<String> pests = rows().stream().filter(r -> r.kind().equals("pest")).map(Row::species).toList();
        assertThat(critters).containsExactly("isopod", "lobster", "crayfish", "horseshoe_crab");
        assertThat(pests).containsExactly("rat", "lemming", "mongoose", "jerboa");
        assertThat(CritterSpecies.values()).extracting(Enum::name)
            .containsExactlyElementsOf(critters.stream().map(s -> s.toUpperCase(java.util.Locale.ROOT)).toList());
        assertThat(PestSpecies.values()).extracting(PestSpecies::id).containsExactlyElementsOf(pests);
    }

    @Test
    void waterFlavourMatchesTheFactoryAndTheFluidRule() throws IOException {
        for (Row row : rows()) {
            if (!row.kind().equals("critter")) {
                continue;
            }
            CritterSpecies species = CritterSpecies.valueOf(row.species().toUpperCase(java.util.Locale.ROOT));
            assertThat(row.flavour()).isEqualTo(species.salty() ? "salty" : "fresh");
            assertThat(row.category()).isEqualTo("water_ambient");
            assertThat(species.canSpawnIn(CritterFluid.SALT_SOURCE)).isEqualTo(species.salty());
            assertThat(species.canSpawnIn(CritterFluid.FRESH_SOURCE)).isEqualTo(!species.salty());
            assertThat(row.faunaFile()).isTrue();
            assertThat(row.trapTag()).isTrue();
        }
    }

    @Test
    void pestGroupsMatchTheTagFiles() throws IOException {
        for (Row row : rows()) {
            if (!row.kind().equals("pest")) {
                continue;
            }
            PestSpecies species = PestSpecies.valueOf(row.species().toUpperCase(java.util.Locale.ROOT));
            PestGroup group = PestGroup.valueOf(row.group().toUpperCase(java.util.Locale.ROOT));
            assertThat(group.members()).containsExactly(species);
            assertThat(row.groupIndex()).isZero();
            assertThat(row.groupSize()).isEqualTo(group.members().size());
            assertThat(row.faunaFile()).isFalse();
            assertThat(row.trapTag()).isFalse();
            assertThat(row.spawnEntries()).isZero();
        }
    }

    /**
     * The spawner weights and counts, summed over every biome file, at c3b4d9536: isopod 34 entries of weight 34, lobster 34
     * entries of weight 98, crayfish 14 entries of weight 70, horseshoe crab 34 entries of weight 34. Pests: none.
     */
    @Test
    void spawnerEntriesAndWeightsMatchTfc() throws IOException {
        Map<String, Row> byName = rows().stream().collect(Collectors.toMap(Row::species, r -> r));
        assertThat(byName.get("isopod").spawnEntries()).isEqualTo(34);
        assertThat(byName.get("isopod").spawnWeight()).isEqualTo(34);
        assertThat(byName.get("lobster").spawnEntries()).isEqualTo(34);
        assertThat(byName.get("lobster").spawnWeight()).isEqualTo(98);
        assertThat(byName.get("crayfish").spawnEntries()).isEqualTo(14);
        assertThat(byName.get("crayfish").spawnWeight()).isEqualTo(70);
        assertThat(byName.get("horseshoe_crab").spawnEntries()).isEqualTo(34);
        assertThat(byName.get("horseshoe_crab").spawnWeight()).isEqualTo(34);
        assertThat(byName.get("rat").spawnEntries()).isZero();
    }

    /** Entity sizes from {@code TFCEntities}: critters 0.5 by 0.3; rat and lemming 0.4 by 0.3; mongoose 0.6 by 0.4; jerboa 0.4 by 0.4. */
    @Test
    void entitySizesMatchTfc() throws IOException {
        Map<String, Row> byName = rows().stream().collect(Collectors.toMap(Row::species, r -> r));
        assertThat(byName.get("lobster").width()).isEqualTo(0.5f);
        assertThat(byName.get("lobster").height()).isEqualTo(0.3f);
        assertThat(byName.get("rat").width()).isEqualTo(0.4f);
        assertThat(byName.get("rat").height()).isEqualTo(0.3f);
        assertThat(byName.get("mongoose").width()).isEqualTo(0.6f);
        assertThat(byName.get("mongoose").height()).isEqualTo(0.4f);
        assertThat(byName.get("lemming").width()).isEqualTo(0.4f);
        assertThat(byName.get("lemming").height()).isEqualTo(0.3f);
        assertThat(byName.get("jerboa").width()).isEqualTo(0.4f);
        assertThat(byName.get("jerboa").height()).isEqualTo(0.4f);
    }

    private static Map<String, String> header() throws IOException {
        Map<String, String> out = new java.util.LinkedHashMap<>();
        for (String line : text().split("\n")) {
            if (line.startsWith("#")) {
                for (String pair : line.substring(1).trim().split("\\s+")) {
                    if (pair.contains("=")) {
                        out.put(pair.substring(0, pair.indexOf('=')), pair.substring(pair.indexOf('=') + 1));
                    }
                }
            }
        }
        return out;
    }

    private static List<Row> rows() throws IOException {
        List<Row> rows = new ArrayList<>();
        for (String line : text().split("\n")) {
            if (line.isBlank() || line.startsWith("#") || line.startsWith("species\t")) {
                continue;
            }
            String[] c = line.split("\t");
            rows.add(new Row(c[0], c[1], c[2], c[3], c[4], Integer.parseInt(c[5]), Integer.parseInt(c[6]),
                Float.parseFloat(c[7]), Float.parseFloat(c[8]), c[9].equals("1"), c[10].equals("1"),
                Integer.parseInt(c[11]), Integer.parseInt(c[12])));
        }
        return rows;
    }

    private static String text() throws IOException {
        try (InputStream in = CritterTableGoldenTest.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("missing " + RESOURCE);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
