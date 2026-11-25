/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.effect;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/** PAR-DATA-07: the bootstrap datapack that the jar ships holds TFC's five damage types with their death message ids. */
class DamageTypeDatapackTest {
    @Test
    void theGeneratedDatapackHoldsTfcsFiveDamageTypes() throws Exception {
        Path data = Path.of(System.getProperty("tfp.contentData", "../content/generated/content/data"));
        Path dir = data.resolveSibling("datapack").resolve("tfp_seasonal_biomes/data/tfp/damage_type");
        for (String name : List.of("dehydration", "grill", "lobster_trap", "pluck", "pot")) {
            Path file = dir.resolve(name + ".json");
            assertThat(Files.isRegularFile(file)).as(name).isTrue();
            var json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            assertThat(json.get("message_id").getAsString()).isEqualTo("tfc." + name);
        }
    }
}
