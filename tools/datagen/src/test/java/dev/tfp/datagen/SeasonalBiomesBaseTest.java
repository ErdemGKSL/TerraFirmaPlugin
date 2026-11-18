/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.SortedMap;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/** PAR-WORLD-02: the datapack base biome {@code tfp:<biome>} of every TFC biome, next to its seasonal variants. */
class SeasonalBiomesBaseTest {
    private static final String PIN = TfcCheckout.PINNED_COMMIT;
    private static final String BASE_DIR = SeasonalBiomes.OUT + "data/tfp/worldgen/biome/";

    private static final String TFC_BIOME = """
        {"has_precipitation": true, "temperature": 0.5, "downfall": 0.5,
         "effects": {"fog_color": 12638463, "sky_color": 8709887, "water_color": 4159204, "water_fog_color": 329011},
         "carvers": {"air": ["tfc:cave"], "liquid": []},
         "features": ["#tfc:in_biome/erosion"],
         "spawners": {
           "monster": [{"type": "minecraft:zombie", "weight": 95, "minCount": 4, "maxCount": 4}],
           "creature": [{"type": "tfc:cow", "weight": 1, "minCount": 1, "maxCount": 2}],
           "water_ambient": [{"type": "tfc:cod", "weight": 1, "minCount": 1, "maxCount": 2}]
         },
         "creature_spawn_probability": 0.1,
         "spawn_costs": {"tfc:octopoteuthis": {"energy_budget": 0.12, "charge": 1.0}}}
        """;

    @Test
    void baseKeepsClimateEffectsAndVanillaMonstersOnly() {
        JsonObject tfc = JsonParser.parseString(TFC_BIOME).getAsJsonObject();
        JsonObject b = SeasonalBiomes.base(tfc, tfc.getAsJsonObject("effects"));

        assertThat(b.get("temperature").getAsFloat()).isEqualTo(0.5f);
        assertThat(b.get("downfall").getAsFloat()).isEqualTo(0.5f);
        assertThat(b.get("has_precipitation").getAsBoolean()).isTrue();
        // the TFC biome's own effects (no seasonal grass or foliage colour: the packet layer sends the variant for that)
        assertThat(b.getAsJsonObject("effects")).isEqualTo(tfc.getAsJsonObject("effects"));
        assertThat(b.getAsJsonObject("effects").has("grass_color")).isFalse();
        // no terrain: our generator places everything, and tfc: features and carvers do not exist on the server
        assertThat(b.getAsJsonArray("carvers")).isEmpty();
        assertThat(b.getAsJsonArray("features")).hasSize(SeasonalBiomes.FEATURE_STEPS)
            .allSatisfy(step -> assertThat(step.getAsJsonArray()).isEmpty());
        // TFC's vanilla monsters stay; its own animals, fish and costs are not server entities
        JsonObject spawners = b.getAsJsonObject("spawners");
        assertThat(spawners.keySet()).containsExactly("monster");
        assertThat(spawners.getAsJsonArray("monster").get(0).getAsJsonObject().get("type").getAsString())
            .isEqualTo("minecraft:zombie");
        assertThat(b.getAsJsonObject("spawn_costs").size()).isZero();
        assertThat(b.toString()).doesNotContain("tfc:");
    }

    @Test
    void realCheckoutWritesOneBaseBiomePerTfcBiome() {
        Path checkout = Path.of(System.getProperty("tfc.checkout", "/opt/tfc"));
        Assumptions.assumeTrue(Files.isDirectory(checkout.resolve(".git")), "no TFC checkout at " + checkout);
        Assumptions.assumeTrue(PIN.equals(TfcCheckout.resolveHead(checkout)), "TFC checkout is not at the pinned commit");
        TfcCheckout tfc = TfcCheckout.open(checkout, PIN);
        OutputTree out = new OutputTree();
        int biomes = SeasonalBiomes.write(tfc, out);
        SortedMap<String, byte[]> files = out.files();

        long bases = files.keySet().stream()
            .filter(p -> p.startsWith(BASE_DIR) && !p.startsWith(BASE_DIR + "seasonal/")).count();
        assertThat(biomes).isEqualTo(125);
        assertThat(bases).isEqualTo(biomes);
        for (Map.Entry<String, byte[]> e : files.entrySet()) {
            if (e.getKey().startsWith(BASE_DIR) && !e.getKey().startsWith(BASE_DIR + "seasonal/")) {
                assertThat(new String(e.getValue(), StandardCharsets.UTF_8)).as(e.getKey()).doesNotContain("tfc:");
            }
        }
        JsonObject plains = JsonParser.parseString(new String(files.get(BASE_DIR + "plains.json"), StandardCharsets.UTF_8))
            .getAsJsonObject();
        assertThat(plains.getAsJsonObject("effects").get("sky_color").getAsInt()).isEqualTo(8709887);
        assertThat(plains.getAsJsonObject("spawners").getAsJsonArray("monster")).hasSize(5);
    }
}
