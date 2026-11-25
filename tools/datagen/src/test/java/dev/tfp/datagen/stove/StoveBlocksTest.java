/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen.stove;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.datagen.OutputTree;
import dev.tfp.datagen.TfcCheckout;
import dev.tfp.datagen.hitbox.HitboxDemands;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/** PAR-HEAT-31: the tfp:stove and tfp:stove_pot entries on the real checkout. */
class StoveBlocksTest {
    private static final String RP = "craftengine/tfp/resourcepack/assets/tfp/";
    private static OutputTree out;
    private static int states;

    @BeforeAll
    static void generate() {
        Path path = Path.of(System.getProperty("tfc.checkout", "/opt/tfc"));
        Assumptions.assumeTrue(Files.isDirectory(path.resolve(".git")), "no TFC checkout at " + path);
        TfcCheckout tfc;
        try {
            tfc = TfcCheckout.open(path, TfcCheckout.PINNED_COMMIT);
        } catch (RuntimeException e) {
            Assumptions.abort("TFC checkout is not at the pinned commit: " + e.getMessage());
            return;
        }
        assertThat(StoveBlocks.available(tfc)).isTrue();
        out = new OutputTree();
        states = StoveBlocks.run(tfc, out);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> block(String file, String id) {
        Map<String, Object> y = new Yaml().load(new String(out.get(file), StandardCharsets.UTF_8));
        return (Map<String, Object>) ((Map<String, Object>) y.get("blocks")).get(id);
    }

    @Test
    @SuppressWarnings("unchecked")
    void stateIsFacingLitSmokeAndAxis() {
        assertThat(states).isEqualTo(4 * 2 * 5 * 2);
        for (String[] e : new String[][] {{StoveBlocks.STOVE_OUTPUT, StoveBlocks.STOVE}, {StoveBlocks.STOVE_POT_OUTPUT, StoveBlocks.STOVE_POT}}) {
            Map<String, Object> b = block(e[0], e[1]);
            assertThat(b.get("template")).isEqualTo("tfp:block/device_rendered");
            Map<String, Object> st = (Map<String, Object>) b.get("states");
            Map<String, Object> props = (Map<String, Object>) st.get("properties");
            assertThat(props.keySet()).containsExactly("facing", "lit", "smoke_level", "axis");
            Map<String, Object> looks = (Map<String, Object>) st.get("appearances");
            assertThat(looks).hasSize(8).containsKeys("lit_north", "lit_east", "lit_south", "lit_west", "unlit_north",
                "unlit_east", "unlit_south", "unlit_west");
            Map<String, Object> args = (Map<String, Object>) b.get("arguments");
            // TFCBlocks: strength(3), SoundType.METAL, litBlockEmission(15)
            assertThat(args.get("device_hardness")).isEqualTo(3.0);
            Map<String, Object> variants = (Map<String, Object>) st.get("variants");
            assertThat(variants).hasSize(80);
            Map<String, Object> lit = (Map<String, Object>) variants.get("facing=north,lit=true,smoke_level=0,axis=x");
            assertThat(lit).as(variants.keySet().iterator().next()).isNotNull();
            assertThat(((Map<String, Object>) lit.get("settings")).get("luminance")).isEqualTo(15);
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void everyAppearanceIsADisplayOfTfcsModelTurnedByFacingOnAHitboxOnlyCarrier() {
        Map<String, Object> st = (Map<String, Object>) block(StoveBlocks.STOVE_OUTPUT, StoveBlocks.STOVE).get("states");
        Map<String, Object> looks = (Map<String, Object>) st.get("appearances");
        // TFC stove.json: east is unturned, north y 270, south y 90, west y 180 -> the display turn is 180 - y
        Map<String, String> turn = Map.of("east", "0,180,0", "north", "0,-90,0", "south", "0,90,0", "west", "0,0,0");
        for (Map.Entry<String, String> t : turn.entrySet()) {
            String yaml = String.valueOf(looks.get("lit_" + t.getKey()));
            assertThat(yaml).contains(t.getValue()).contains("tfp:render/stove_lit");
            assertThat(String.valueOf(looks.get("unlit_" + t.getKey()))).contains("tfp:render/stove_unlit");
        }
        Map<String, Object> pot = (Map<String, Object>) block(StoveBlocks.STOVE_POT_OUTPUT, StoveBlocks.STOVE_POT).get("states");
        assertThat(String.valueOf(((Map<String, Object>) pot.get("appearances")).get("lit_north")))
            .contains("tfp:render/stove_pot_lit");
        assertThat(HitboxDemands.all().stream().filter(d -> d.blockId().equals("tfp:stove")).count()).isEqualTo(8);
    }

    @Test
    void modelsTexturesAndNamesAreWritten() {
        for (String m : List.of("block/stove_lit", "block/stove_unlit", "block/stove_pot_lit", "block/stove_pot_unlit",
                "item/stove")) {
            assertThat(out.get(RP + "models/" + m + ".json")).as(m).isNotNull();
        }
        String pot = new String(out.get(RP + "models/block/stove_pot_lit.json"), StandardCharsets.UTF_8);
        // the merged model holds the pot parts and the lit stove's elements, and its textures are ours
        assertThat(pot).contains("PotNorth").contains("tfp:block/devices/stove/top").contains("tfp:block/ceramic/large_vessel/side")
            .doesNotContain("tfc:");
        assertThat(out.get(RP + "textures/block/devices/stove/front.png")).isNotNull();
        assertThat(out.get(RP + "textures/item/stove.png")).isNotNull();
        String lang = new String(out.get("craftengine/tfp/resourcepack/assets/tfp_stove/lang/en_us.json"), StandardCharsets.UTF_8);
        assertThat(lang).contains("\"block.tfp.stove\"").contains("Stove").contains("\"block.tfp.stove_pot\"");
    }
}
