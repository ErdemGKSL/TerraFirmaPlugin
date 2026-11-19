/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.spawn;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonParser;
import dev.tfp.core.animal.CarrierTable;
import dev.tfp.core.spawn.SpawnCategory;
import dev.tfp.core.spawn.SpawnTables;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;

/** PAR-FAUNA-04: the shipped spawn table loads, every TFC species it names has a carrier, and water carriers are skipped. */
class NaturalSpawnTest {
    @Test
    void shippedTableLoadsAndEverySpeciesHasACarrier() throws Exception {
        var file = java.nio.file.Path.of(System.getProperty("tfp.contentData")).resolve("spawn_table.json");
        SpawnTables t = SpawnTables.parse(JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject());
        assertThat(t.biomes()).hasSizeGreaterThan(100);
        assertThat(t.fauna()).hasSize(77);
        CarrierTable carriers = CarrierTable.get();
        var missing = t.biomes().values().stream().flatMap(b -> b.entries().values().stream()).flatMap(java.util.List::stream)
                .map(e -> e.type()).distinct().filter(id -> carriers.find(id).isEmpty()).toList();
        assertThat(missing).as("species without a carrier").isEmpty();
        assertThat(t.biome("plateau").orElseThrow().of(SpawnCategory.CREATURE)).isNotEmpty();
    }

    @Test
    void waterCarriersAreNotSpawnedByTheLandPass() {
        CarrierTable carriers = CarrierTable.get();
        assertThat(NaturalSpawnModule.isWater(carriers.find("tfc:bluegill").orElseThrow())).isTrue();
        assertThat(NaturalSpawnModule.isWater(carriers.find("tfc:cow").orElseThrow())).isFalse();
    }
}
