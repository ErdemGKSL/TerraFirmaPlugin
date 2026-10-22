/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.tfp.core.metal.MetalTier;
import dev.tfp.core.tool.ArmorRules;
import dev.tfp.core.tool.ArmorSlot;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.SortedMap;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/** M4-TOOL-02 armor slice: metal armor, horse armor and unfinished parts from TFC's TFCArmorMaterials and Metal. */
class ArmorGenerationTest {
    private static final List<String> PIECES = List.of("helmet", "chestplate", "greaves", "boots");
    private static final Map<String, ArmorSlot> SLOTS = Map.of("head", ArmorSlot.HEAD, "chest", ArmorSlot.CHEST,
        "legs", ArmorSlot.LEGS, "feet", ArmorSlot.FEET, "body", ArmorSlot.BODY);
    private static SortedMap<String, byte[]> cached;

    private static synchronized SortedMap<String, byte[]> files() {
        Path path = Path.of(System.getProperty("tfc.checkout", "/opt/tfc"));
        Assumptions.assumeTrue(Files.isDirectory(path.resolve(".git")), "no TFC checkout at " + path);
        Assumptions.assumeTrue(TfcCheckout.PINNED_COMMIT.equals(TfcCheckout.resolveHead(path)), "not at the pinned commit");
        if (cached == null) {
            cached = Datagen.generate(path, TfcCheckout.PINNED_COMMIT).tree().files();
        }
        return cached;
    }

    private static String text(SortedMap<String, byte[]> files, String suffix) {
        return files.entrySet().stream().filter(e -> e.getKey().endsWith(suffix))
            .map(e -> new String(e.getValue(), StandardCharsets.UTF_8)).findFirst().orElseThrow();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> metalYml(SortedMap<String, byte[]> files) {
        return (Map<String, Object>) new Yaml().load(text(files, AssetPipeline.CONFIG + "metal.yml"));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> args(Map<String, Object> items, String id) {
        Map<String, Object> entry = (Map<String, Object>) items.get("tfp:" + id);
        assertThat(entry).as(id).isNotNull();
        return (Map<String, Object>) entry.get("arguments");
    }

    @Test
    void parsesTheArmorMaterialLineFormat() {
        String line = "    public static final Id COPPER = register(\"copper\", TFCSounds.COPPER_EQUIP, "
            + "160, 200, 215, 150, 1, 3, 4, 1, 9, 0f, 0f);\n"
            + "    public static final Id BISMUTH_BRONZE = register(\"bismuth_bronze\", TFCSounds.BISMUTH_BRONZE_EQUIP,  "
            + "250, 288, 311, 240, 1, 4, 4, 1, 9, 0f, 0.05f);\n";
        var m = AssetPipeline.parseArmorMaterials(line);
        assertThat(m.get("copper").durability("head")).isEqualTo(150);
        assertThat(m.get("copper").durability("body")).isEqualTo(215);
        assertThat(m.get("copper").armor("legs")).isEqualTo(3);
        assertThat(m.get("bismuth_bronze").knockback()).isEqualByComparingTo("0.05");
    }

    @Test
    void aMangledArmorMaterialLineFails() {
        String line = "    public static final Id COPPER = register(\"copper\", TFCSounds.COPPER_EQUIP, 160, 200, 215, 150, 1, 3, 4, 9, 0f, 0f);\n";
        assertThatThrownBy(() -> AssetPipeline.parseArmorMaterials(line)).isInstanceOf(DatagenException.class)
            .hasMessageContaining("unexpected armor material line");
        assertThatThrownBy(() -> AssetPipeline.parseArmorMaterials("class TFCArmorMaterials {}"))
            .isInstanceOf(DatagenException.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void emitsEveryArmorPieceHorseArmorAndUnfinishedPart() {
        var files = files();
        Map<String, Object> items = (Map<String, Object>) metalYml(files).get("items");
        int armor = 0;
        int horse = 0;
        int unfinished = 0;
        for (var e : items.entrySet()) {
            String kind = e.getKey().split("/")[1];
            String template = (String) ((Map<String, Object>) e.getValue()).get("template");
            if (PIECES.contains(kind)) {
                assertThat(template).isEqualTo("tfp:item/armor");
                armor++;
            } else if (kind.equals("horse_armor")) {
                assertThat(template).isEqualTo("tfp:item/horse_armor");
                horse++;
            } else if (kind.startsWith("unfinished_")) {
                assertThat(template).isEqualTo("tfp:item/simple");
                unfinished++;
            }
        }
        assertThat(armor).isEqualTo(36);
        assertThat(horse).isEqualTo(9);
        assertThat(unfinished).isEqualTo(36);
    }

    @Test
    @SuppressWarnings("unchecked")
    void goldenValuesForCopperSteelAndBlueSteel() {
        Map<String, Object> items = (Map<String, Object>) metalYml(files()).get("items");
        // durability head/chest/legs/feet, armor head/chest/legs/feet, toughness, knockback, enchantability, rarity
        Object[][] golden = {
            {"copper", new int[] {150, 215, 200, 160}, new int[] {1, 4, 3, 1}, "0", "0", 9, "common"},
            {"steel", new int[] {440, 640, 600, 520}, new int[] {2, 6, 5, 2}, "1", "0", 12, "uncommon"},
            {"blue_steel", new int[] {748, 1088, 960, 860}, new int[] {3, 8, 6, 3}, "3", "0.1", 23, "epic"},
        };
        for (Object[] g : golden) {
            String metal = (String) g[0];
            for (int i = 0; i < 4; i++) {
                Map<String, Object> a = args(items, "metal/" + PIECES.get(i) + "/" + metal);
                assertThat(a.get("durability")).as(metal + " " + PIECES.get(i)).isEqualTo(((int[]) g[1])[i]);
                assertThat(a.get("armor")).as(metal + " " + PIECES.get(i)).isEqualTo(((int[]) g[2])[i]);
                assertThat(new BigDecimal(a.get("toughness").toString())).isEqualByComparingTo((String) g[3]);
                assertThat(new BigDecimal(a.get("knockback").toString())).isEqualByComparingTo((String) g[4]);
                assertThat(a.get("enchantability")).isEqualTo(g[5]);
                assertThat(a.get("rarity")).isEqualTo(g[6]);
                assertThat(a.get("metal")).isEqualTo(metal);
            }
            Map<String, Object> h = args(items, "metal/horse_armor/" + metal);
            assertThat(h.get("slot")).isEqualTo("body");
            assertThat(h.get("durability")).isEqualTo(((int[]) g[1])[1]);
            assertThat(h.get("armor")).isEqualTo(((int[]) g[2])[1]);
        }
    }

    /** Datagen (read from TFC) and core's MetalTier/ArmorRules (golden-tested) must agree on all 9 armor metals. */
    @Test
    @SuppressWarnings("unchecked")
    void matchesCoreArmorRulesForEveryMetal() {
        Map<String, Object> items = (Map<String, Object>) metalYml(files()).get("items");
        int checked = 0;
        for (MetalTier tier : MetalTier.values()) {
            MetalTier.Armor armor = tier.armor();
            String metal = tier.name().toLowerCase(Locale.ROOT);
            for (String kind : List.of("helmet", "chestplate", "greaves", "boots", "horse_armor")) {
                Map<String, Object> a = args(items, "metal/" + kind + "/" + metal);
                ArmorSlot slot = SLOTS.get((String) a.get("slot"));
                assertThat(a.get("durability")).as(metal + " " + kind).isEqualTo(ArmorRules.durability(armor, slot));
                assertThat(a.get("armor")).as(metal + " " + kind).isEqualTo(ArmorRules.defense(armor, slot));
                assertThat(new BigDecimal(a.get("toughness").toString()).floatValue()).isEqualTo(armor.toughness());
                assertThat(new BigDecimal(a.get("knockback").toString()).floatValue()).isEqualTo(armor.knockbackResistance());
                if (!kind.equals("horse_armor")) {
                    assertThat(a.get("enchantability")).isEqualTo(armor.enchantmentValue());
                }
                checked++;
            }
        }
        assertThat(checked).isEqualTo(45);
    }

    /** The M3-SURV-04 lookup: every item of TFC's exported armor damage resistances exists as a CE item. */
    @Test
    @SuppressWarnings("unchecked")
    void everyArmorDamageResistanceItemIsAGeneratedItem() {
        var files = files();
        Map<String, Object> items = (Map<String, Object>) metalYml(files).get("items");
        String root = Path.of(System.getProperty("tfp.repoRoot", "../..")).toString();
        Path dir = Path.of(root, "content/generated/content/data/tfp/tfp/item_damage_resistance");
        Assumptions.assumeTrue(Files.isDirectory(dir), "no exported item_damage_resistance at " + dir);
        List<String> missing = new ArrayList<>();
        int checked = 0;
        try (var list = Files.list(dir)) {
            for (Path f : list.filter(p -> p.getFileName().toString().endsWith("_armor.json")).toList()) {
                String metal = f.getFileName().toString().replace("_armor.json", "");
                if (metal.equals("leather") || metal.equals("chainmail")) {
                    continue; // vanilla armor
                }
                JsonObject o = JsonParser.parseString(Files.readString(f)).getAsJsonObject();
                for (JsonElement i : o.getAsJsonArray("ingredient")) {
                    String id = i.getAsJsonObject().get("item").getAsString();
                    checked++;
                    if (!items.containsKey(id)) {
                        missing.add(id);
                    }
                }
            }
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
        assertThat(checked).isEqualTo(36);
        assertThat(missing).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void everyEquipmentLayerTextureIsInThePack() {
        var files = files();
        Map<String, Object> equipments = (Map<String, Object>) metalYml(files).get("equipments");
        assertThat(equipments).hasSize(9);
        int layers = 0;
        for (var e : equipments.entrySet()) {
            Map<String, Object> eq = (Map<String, Object>) e.getValue();
            assertThat(eq.get("type")).isEqualTo("component");
            for (String layer : AssetPipeline.ARMOR_LAYERS) {
                String tex = (String) eq.get(layer);
                assertThat(tex).as(e.getKey() + " " + layer).isEqualTo(e.getKey());
                String path = AssetPipeline.RP + "textures/" + AssetPipeline.EQUIPMENT_TEXTURES + layer + "/"
                    + tex.substring("tfp:".length()) + ".png";
                assertThat(files).as(path).containsKey(path);
                layers++;
            }
        }
        assertThat(layers).isEqualTo(27);
        // Every armor item model is a plain generated model (the tfc:trim loader is converted).
        assertThat(text(files, AssetPipeline.RP + "models/item/metal/helmet/copper.json"))
            .contains("minecraft:item/generated", "tfp:item/metal/helmet/copper").doesNotContain("loader");
        assertThat(files).containsKey(AssetPipeline.RP + "textures/item/metal/helmet/copper.png");
    }
}
