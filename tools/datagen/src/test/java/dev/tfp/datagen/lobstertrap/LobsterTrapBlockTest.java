/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen.lobstertrap;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonObject;
import dev.tfp.datagen.Json;
import dev.tfp.datagen.OutputTree;
import dev.tfp.datagen.TfcCheckout;
import dev.tfp.datagen.hitbox.HitboxAllocation;
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

/** PAR-FAUNA-36: the tfp:lobster_trap entry on the real checkout. */
class LobsterTrapBlockTest {
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
        assertThat(LobsterTrapBlock.available(tfc)).isTrue();
        out = new OutputTree();
        states = LobsterTrapBlock.run(tfc, out);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> root() {
        return new Yaml().load(new String(out.get(LobsterTrapBlock.OUTPUT), StandardCharsets.UTF_8));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> block() {
        return (Map<String, Object>) ((Map<String, Object>) root().get("blocks")).get(LobsterTrapBlock.ID);
    }

    @Test
    @SuppressWarnings("unchecked")
    void facingTrapAndFluidGiveTwentyFourStatesWithTfcsSettingsAndLoot() {
        assertThat(states).isEqualTo(24);
        Map<String, Object> b = block();
        assertThat(b.get("template")).isEqualTo("tfp:block/device_rendered");
        Map<String, Object> args = (Map<String, Object>) b.get("arguments");
        // TFCBlocks: strength(3f), SoundType.METAL; generated mineable/pickaxe.json
        assertThat(args.get("device_hardness")).isEqualTo(3.0);
        assertThat(args.get("device_resistance")).isEqualTo(3.0);
        assertThat(args.get("device_tool_tag")).isEqualTo("minecraft:mineable/pickaxe");
        assertThat(((Map<String, Object>) b.get("loot")).get("template")).isEqualTo("tfp:loot_table/self");
        Map<String, Object> props = (Map<String, Object>) ((Map<String, Object>) b.get("states")).get("properties");
        assertThat(props.keySet()).containsExactly("facing", "trap", "fluid");
        assertThat(props.get("trap").toString()).contains("open", "baited", "closed");
        assertThat(props.get("fluid").toString()).contains("empty", "water");
    }

    @Test
    @SuppressWarnings("unchecked")
    void everyFacingTrapAppearanceIsHitboxOnlyOnTheSelectorsCarrierWithATurnedModel() {
        Map<String, Object> st = (Map<String, Object>) block().get("states");
        Map<String, Object> apps = (Map<String, Object>) st.get("appearances");
        assertThat(apps).hasSize(24); // PAR-FAUNA-41: 12 dry + 12 wet
        Map<String, Object> items = (Map<String, Object>) root().get("items");
        String shared = null;
        for (String f : HitboxDemands.FACINGS) {
            for (String t : HitboxDemands.LOBSTER_TRAP_STATES) {
                String name = HitboxDemands.lobsterTrapAppearance(f, t);
                HitboxAllocation.Assignment a = HitboxAllocation.agreed(LobsterTrapBlock.ID, name);
                assertThat(a.demand().tfcKey()).isEqualTo(HitboxDemands.LOBSTER_TRAP_ROW);
                assertThat(a.mode()).as(name).isEqualTo(HitboxAllocation.Mode.HITBOX_ONLY);
                Map<String, Object> app = (Map<String, Object>) apps.get(name);
                assertThat(app.get("state")).as(name).isEqualTo(a.state());
                assertThat(app).containsEntry("transparent", true).doesNotContainKey("model");
                List<Map<String, Object>> r = (List<Map<String, Object>>) app.get("entity_renderer");
                assertThat(r).hasSize(1);
                assertThat(r.getFirst().get("item")).isEqualTo(LobsterTrapBlock.renderItem(t));
                shared = shared == null ? a.state() : shared;
                assertThat(a.state()).as("one shared carrier").isEqualTo(shared);
            }
        }
        for (String t : HitboxDemands.LOBSTER_TRAP_STATES) {
            Map<String, Object> render = (Map<String, Object>) items.get(LobsterTrapBlock.renderItem(t));
            assertThat(((Map<String, Object>) render.get("arguments")).get("render_model")).isEqualTo(LobsterTrapBlock.model(t));
        }
        // PAR-FAUNA-41: fluid=water states take the wet appearance, fluid=empty the dry one
        Map<String, Object> variants = (Map<String, Object>) st.get("variants");
        assertThat(variants).hasSize(24);
        assertThat(((Map<String, Object>) variants.get("facing=east,trap=baited,fluid=water")).get("appearance"))
            .isEqualTo("east_baited_wet");
        assertThat(((Map<String, Object>) variants.get("facing=east,trap=baited,fluid=empty")).get("appearance"))
            .isEqualTo("east_baited");
        assertThat(((Map<String, Object>) items.get(LobsterTrapBlock.ID)).get("template")).isEqualTo("tfp:item/block_item");
    }

    @Test
    @SuppressWarnings("unchecked")
    void everyWetAppearanceSharesTheWaterloggedLeavesPoolStateWithAnEmptyModelAndTheTurnedDisplay() {
        Map<String, Object> apps = (Map<String, Object>) ((Map<String, Object>) block().get("states")).get("appearances");
        for (String f : HitboxDemands.FACINGS) {
            for (String t : HitboxDemands.LOBSTER_TRAP_STATES) {
                String name = HitboxDemands.lobsterTrapWetAppearance(f, t);
                Map<String, Object> app = (Map<String, Object>) apps.get(name);
                assertThat(app).as(name).doesNotContainKey("state").doesNotContainKey("transparent");
                assertThat((Map<String, Object>) app.get("auto_state")).as(name)
                    .containsEntry("type", "waterlogged_tintable_leaves").containsEntry("id", "lobster_trap");
                assertThat(app.get("model")).as(name).isEqualTo(HitboxDemands.LOBSTER_TRAP_WET_MODEL);
                List<Map<String, Object>> r = (List<Map<String, Object>>) app.get("entity_renderer");
                assertThat(r).hasSize(1);
                assertThat(r.getFirst().get("item")).isEqualTo(LobsterTrapBlock.renderItem(t));
                Map<String, Object> dry = (Map<String, Object>) apps.get(HitboxDemands.lobsterTrapAppearance(f, t));
                assertThat(r.getFirst().get("rotation")).as(name + " turned as the dry one")
                    .isEqualTo(((List<Map<String, Object>>) dry.get("entity_renderer")).getFirst().get("rotation"));
            }
        }
        JsonObject empty = Json.parse(new String(out.get(RP + "models/block/lobster_trap_water.json"), StandardCharsets.UTF_8),
            "m").getAsJsonObject();
        assertThat(empty.has("elements")).isFalse();
        assertThat(empty.getAsJsonObject("textures").get("particle").getAsString()).isEqualTo("tfp:block/lobster_trap");
    }

    @Test
    void modelsTexturesAndNamesAreTfcs() {
        for (String name : List.of("lobster_trap", "lobster_trap_bait", "lobster_trap_closed")) {
            assertThat(out.contains(RP + "models/block/" + name + ".json")).as(name).isTrue();
        }
        JsonObject model = Json.parse(new String(out.get(RP + "models/block/lobster_trap.json"), StandardCharsets.UTF_8), "m")
            .getAsJsonObject();
        assertThat(model.getAsJsonObject("textures").get("0").getAsString()).isEqualTo("tfp:block/lobster_trap");
        assertThat(out.contains(RP + "textures/block/lobster_trap.png")).isTrue();
        assertThat(out.contains(RP + "textures/block/jar/cranberry.png")).as("the bait model's texture").isTrue();
        assertThat(new String(out.get(RP + "models/item/lobster_trap.json"), StandardCharsets.UTF_8))
            .contains("tfp:block/lobster_trap");
        JsonObject lang = Json.parse(new String(out.get(LobsterTrapBlock.LANG_RP + "en_us.json"), StandardCharsets.UTF_8), "l")
            .getAsJsonObject();
        assertThat(lang.get("block.tfp.lobster_trap").getAsString()).isEqualTo("Lobster Trap");
    }
}
