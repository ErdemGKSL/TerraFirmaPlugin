/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen.anvil;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonObject;
import dev.tfp.core.anvil.AnvilTiers;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/** PAR-METAL-03: the anvil entries on the real checkout. */
class AnvilBlocksTest {
    private static OutputTree out;
    private static int states;
    private static TfcCheckout tfc;

    @BeforeAll
    static void generate() {
        Path path = Path.of(System.getProperty("tfc.checkout", "/opt/tfc"));
        Assumptions.assumeTrue(Files.isDirectory(path.resolve(".git")), "no TFC checkout at " + path);
        try {
            tfc = TfcCheckout.open(path, TfcCheckout.PINNED_COMMIT);
        } catch (RuntimeException e) {
            Assumptions.abort("TFC checkout is not at the pinned commit: " + e.getMessage());
            return;
        }
        assertThat(AnvilBlocks.available(tfc)).isTrue();
        out = new OutputTree();
        states = AnvilBlocks.run(tfc, out);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> root() {
        return new Yaml().load(new String(out.get(AnvilBlocks.OUTPUT), StandardCharsets.UTF_8));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> block(String id) {
        return (Map<String, Object>) ((Map<String, Object>) root().get("blocks")).get(id);
    }

    @Test
    void everyMetalAndRockHasABlockAndItsItems() {
        assertThat(states).isEqualTo(9 * 4 + 7);
        @SuppressWarnings("unchecked")
        Map<String, Object> blocks = (Map<String, Object>) root().get("blocks");
        @SuppressWarnings("unchecked")
        Map<String, Object> items = (Map<String, Object>) root().get("items");
        assertThat(blocks).hasSize(16);
        for (String m : HitboxDemands.ANVIL_METALS) {
            assertThat(blocks).containsKey("tfp:metal/anvil/" + m);
            assertThat(items).containsKeys("tfp:metal/anvil/" + m, "tfp:render/metal/anvil/" + m);
        }
        for (String r : HitboxDemands.ANVIL_ROCKS) {
            assertThat(blocks).containsKey("tfp:rock/anvil/" + r);
            assertThat(items).containsKeys("tfp:rock/anvil/" + r, "tfp:render/rock/anvil/" + r);
        }
    }

    @Test
    void metalListsAgreeWithCoreTiers() {
        assertThat(HitboxDemands.ANVIL_METALS).containsExactlyInAnyOrderElementsOf(AnvilTiers.METAL_LEVELS.keySet());
        assertThat(HitboxDemands.ANVIL_ROCKS).containsExactlyInAnyOrderElementsOf(dev.tfp.core.anvil.AnvilPlacement.IGNEOUS);
    }

    @Test
    void coreTiersAreTfcsToolTierLevels() {
        String src = tfc.readString("src/main/java/net/dries007/tfc/common/TFCTiers.java");
        Matcher m = Pattern.compile("LevelTier (\\w+) = create\\(\"(\\w+)\", [A-Za-z_.]+, (\\d+),").matcher(src);
        int seen = 0;
        while (m.find()) {
            Integer level = AnvilTiers.METAL_LEVELS.get(m.group(2));
            if (level != null) {
                assertThat(level).as(m.group(2)).isEqualTo(Integer.parseInt(m.group(3)));
                seen++;
            }
        }
        assertThat(seen).isEqualTo(9);
    }

    @Test
    @SuppressWarnings("unchecked")
    void metalAnvilHasFacingTfcSettingsAndTheAnvilSound() {
        Map<String, Object> b = block("tfp:metal/anvil/copper");
        assertThat(b.get("template")).isEqualTo("tfp:block/device_rendered");
        Map<String, Object> args = (Map<String, Object>) b.get("arguments");
        assertThat(args.get("device_hardness")).isEqualTo(10.0);
        assertThat(args.get("device_resistance")).isEqualTo(10.0);
        assertThat(args.get("device_tool_tag")).isEqualTo("minecraft:mineable/pickaxe");
        Map<String, Object> brk = (Map<String, Object>) args.get("device_sound_break");
        assertThat(brk).containsEntry("id", "minecraft:block.anvil.break").containsEntry("volume", 0.65).containsEntry("pitch", 0.8);
        Map<String, Object> hit = (Map<String, Object>) args.get("device_sound_hit");
        assertThat(hit).containsEntry("volume", 0.1625).containsEntry("pitch", 0.5);
        assertThat(((Map<String, Object>) b.get("loot")).get("template")).isEqualTo("tfp:loot_table/self");
        assertThat(((Map<String, Object>) b.get("settings")).get("correct_tools"))
            .isEqualTo(List.of("#minecraft:pickaxes", "#tfp:tools/pickaxe"));
        Map<String, Object> st = (Map<String, Object>) b.get("states");
        Map<String, Object> facing = (Map<String, Object>) ((Map<String, Object>) st.get("properties")).get("facing");
        assertThat(facing).containsEntry("default", "north");
        assertThat((List<String>) facing.get("values")).containsExactly("north", "east", "south", "west");
    }

    @Test
    @SuppressWarnings("unchecked")
    void metalDisplaysAreTurnedByTfcsBlockstateY() {
        // TFC blockstate: north y 90, east 180, south 270, west none; the display turn is 180 - y
        Map<String, Object> apps = (Map<String, Object>) ((Map<String, Object>) block("tfp:metal/anvil/steel").get("states"))
            .get("appearances");
        Map<String, String> want = Map.of("north", "0,90,0", "east", "0,0,0", "south", "0,270,0", "west", "0,180,0");
        for (Map.Entry<String, String> e : want.entrySet()) {
            Map<String, Object> app = (Map<String, Object>) apps.get(e.getKey());
            assertThat(app).containsEntry("transparent", true);
            List<Map<String, Object>> r = (List<Map<String, Object>>) app.get("entity_renderer");
            assertThat(r).hasSize(1);
            assertThat(r.getFirst()).containsEntry("item", "tfp:render/metal/anvil/steel").containsEntry("rotation", e.getValue());
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void carriersAreTheSelectorsAndShareByOutline() {
        for (String m : HitboxDemands.ANVIL_METALS) {
            Map<String, Object> apps = (Map<String, Object>) ((Map<String, Object>) block("tfp:metal/anvil/" + m).get("states"))
                .get("appearances");
            for (String f : AnvilBlocks.FACINGS) {
                String carrier = HitboxAllocation.agreed("tfp:metal/anvil/" + m, f).state();
                assertThat(((Map<String, Object>) apps.get(f)).get("state")).isEqualTo(carrier);
            }
        }
        String rock = HitboxAllocation.agreed("tfp:rock/anvil/granite", "base").state();
        for (String r : HitboxDemands.ANVIL_ROCKS) {
            assertThat(HitboxAllocation.agreed("tfp:rock/anvil/" + r, "base").state()).isEqualTo(rock);
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void rockAnvilDropsOneToFourLooseRocksAndHasNoProperty() {
        Map<String, Object> b = block("tfp:rock/anvil/basalt");
        Map<String, Object> args = (Map<String, Object>) b.get("arguments");
        assertThat(args.get("device_hardness")).isEqualTo(2.0);
        assertThat(args.get("device_resistance")).isEqualTo(10.0);
        Map<String, Object> loot = (Map<String, Object>) b.get("loot");
        assertThat(loot.get("template")).isEqualTo("tfp:loot_table/item_count");
        assertThat((Map<String, Object>) loot.get("arguments")).containsEntry("loot_item", "tfp:rock/loose/basalt")
            .containsEntry("loot_count_min", 1).containsEntry("loot_count_max", 4);
        Map<String, Object> st = (Map<String, Object>) b.get("states");
        assertThat((Map<String, Object>) st.get("properties")).isEmpty();
        assertThat(((Map<String, Object>) st.get("variants")).keySet()).containsExactly("");
    }

    @Test
    void modelsTexturesAndNamesAreCopied() {
        for (String m : HitboxDemands.ANVIL_METALS) {
            assertThat(out.contains(AnvilBlocks.RP + "models/block/metal/anvil/" + m + ".json")).isTrue();
            assertThat(out.contains(AnvilBlocks.RP + "models/item/metal/anvil/" + m + ".json")).isTrue();
            assertThat(out.contains(AnvilBlocks.RP + "textures/block/metal/smooth/" + m + ".png")).isTrue();
        }
        assertThat(out.contains(AnvilBlocks.RP + "models/block/anvil.json")).isTrue();
        assertThat(out.contains(AnvilBlocks.RP + "models/block/anvil_inventory.json")).isTrue();
        JsonObject m = Json.parse(new String(out.get(AnvilBlocks.RP + "models/block/metal/anvil/copper.json"),
            StandardCharsets.UTF_8), "copper").getAsJsonObject();
        assertThat(m.get("parent").getAsString()).isEqualTo("tfp:block/anvil");
        JsonObject lang = Json.parse(new String(out.get(AnvilBlocks.LANG_RP + "en_us.json"), StandardCharsets.UTF_8), "lang")
            .getAsJsonObject();
        assertThat(lang.get("block.tfp.metal.anvil.copper").getAsString()).isEqualTo("Copper Anvil");
        assertThat(lang.get("block.tfp.metal.anvil.red_steel").getAsString()).isEqualTo("Red Steel Anvil");
        assertThat(lang.keySet()).hasSize(9);
    }

    @Test
    void turnsAndSounds() {
        assertThat(AnvilBlocks.rotation(0)).isEqualTo("0,180,0");
        assertThat(AnvilBlocks.rotation(90)).isEqualTo("0,90,0");
        assertThat(AnvilBlocks.rotation(180)).isEqualTo("0,0,0");
        assertThat(AnvilBlocks.rotation(270)).isEqualTo("0,270,0");
        assertThat(AnvilBlocks.axis("east")).isEqualTo("x");
        assertThat(AnvilBlocks.axis("north")).isEqualTo("z");
    }
}
