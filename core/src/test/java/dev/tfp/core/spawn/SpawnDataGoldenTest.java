/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.spawn;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

/**
 * M5-ENT-03 golden: the exported TFC spawn data has the row and file counts the export recorded at c3b4d9536, and every row
 * parses into the core types.
 */
class SpawnDataGoldenTest {

    @Test
    void rowCountMatchesTheExportedBiomeEntries() {
        SpawnTsv tsv = SpawnTsv.load();
        // 125 biome JSON files, 5 664 spawner entries (5 039 tfc, 625 minecraft) at c3b4d9536
        assertThat(tsv.rows()).hasSize(tsv.headerInt("spawn_entries")).hasSize(5664);
        assertThat(tsv.headerInt("biome_files")).isEqualTo(125);
        assertThat(tsv.rows().stream().map(r -> r.entry().biome()).collect(Collectors.toSet())).hasSize(125);
        assertThat(tsv.rows().stream().filter(r -> r.entry().isTfc())).hasSize(5039);
    }

    @Test
    void speciesCountMatchesTheFaunaFiles() {
        SpawnTsv tsv = SpawnTsv.load();
        Set<String> spawned = tsv.rows().stream().filter(r -> r.entry().isTfc()).map(r -> r.entry().type())
                .collect(Collectors.toSet());
        // 77 fauna JSON files; tfc:mule is bred, not spawned, so it has no biome entry
        assertThat(spawned).hasSize(76);
        assertThat(spawned.size() + tsv.headerInt("fauna_unspawned")).isEqualTo(tsv.headerInt("fauna_files")).isEqualTo(77);
        assertThat(tsv.rules()).hasSize(spawned.size());
        assertThat(tsv.rules().keySet()).containsExactlyInAnyOrderElementsOf(spawned);
    }

    @Test
    void everyTfcRowHasAGateAndEveryVanillaRowDoesNot() {
        SpawnTsv tsv = SpawnTsv.load();
        assertThat(tsv.rows()).allSatisfy(r -> {
            if (r.entry().isTfc()) {
                assertThat(r.rule()).isNotNull();
            } else {
                assertThat(r.rule()).isNull();
                assertThat(r.entry().type()).startsWith("minecraft:");
            }
        });
    }

    @Test
    void knownGatesParseAsExported() {
        SpawnTsv tsv = SpawnTsv.load();
        FaunaRule croc = tsv.rules().get("tfc:crocodile");
        assertThat(croc.distanceBelowSeaLevel()).isZero();
        assertThat(croc.minTemperature()).isEqualTo(15f);
        assertThat(croc.maxTemperature()).isEqualTo(Float.POSITIVE_INFINITY);
        assertThat(tsv.rules().get("tfc:panda").fuzzy()).isTrue();
        assertThat(tsv.rules().get("tfc:octopoteuthis").maxBrightness()).isZero();
        assertThat(tsv.rules().get("tfc:salmon").months()).hasSize(6);
        assertThat(tsv.rules().get("tfc:dolphin").chance()).isEqualTo(10);
    }
}
