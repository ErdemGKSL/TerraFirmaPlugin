/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen.material;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.tfp.datagen.Datagen;
import dev.tfp.datagen.OutputTree;
import dev.tfp.datagen.TfcCheckout;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;

/** PAR-PROC-19: the TFCItems registry, the material items and the coverage gate, on the real pinned checkout. */
class MaterialItemsTest {
    private static TfcCheckout tfc;
    private static List<TfcItemRegistry.Registration> regs;
    private static Datagen.Result full;
    private static Map<String, Object> config;

    @BeforeAll
    @SuppressWarnings("unchecked")
    static void generate() {
        Path path = Path.of(System.getProperty("tfc.checkout", "/opt/tfc"));
        Assumptions.assumeTrue(Files.isDirectory(path.resolve(".git")), "no TFC checkout at " + path);
        try {
            tfc = TfcCheckout.open(path, TfcCheckout.PINNED_COMMIT);
        } catch (RuntimeException e) {
            Assumptions.abort("TFC checkout is not at the pinned commit: " + e.getMessage());
        }
        assertThat(MaterialItems.available(tfc)).isTrue();
        regs = TfcItemRegistry.registrations(tfc);
        full = Datagen.generate(tfc.root(), TfcCheckout.PINNED_COMMIT);
        config = new HashMap<>();
        LoaderOptions opts = new LoaderOptions();
        opts.setAllowDuplicateKeys(false);
        for (var e : full.tree().files().entrySet()) {
            if (e.getKey().startsWith(MaterialItems.DIR)) {
                String text = new String(e.getValue(), StandardCharsets.UTF_8);
                Map<String, Object> root = new Yaml(opts).load(text);
                assertThat(root.keySet()).as(e.getKey()).containsExactly("items");
                config.putAll((Map<String, Object>) root.get("items"));
            }
        }
    }

    private static TfcItemRegistry.Registration reg(String field) {
        return regs.stream().filter(r -> r.field().equals(field)).findFirst().orElseThrow();
    }

    /** Counts hand-checked against TFCItems and the enums at c3b4d9536. */
    @Test
    void registryCountsMatchTfc() {
        assertThat(reg("ORES").ids()).hasSize(20).contains("ore/halite", "ore/bituminous_coal", "ore/ruby")
            .doesNotContain("ore/small_hematite"); // small ores are TFCBlocks block items
        assertThat(reg("GRADED_ORES").ids()).hasSize(12 * 3).contains("ore/rich_hematite", "ore/poor_native_copper");
        assertThat(reg("GEMS").ids()).hasSize(9).contains("gem/ruby", "gem/pyrite");
        assertThat(reg("POWDERS").ids()).containsExactly("powder/charcoal", "powder/coke", "powder/flux", "powder/kaolinite",
            "powder/lime", "powder/salt", "powder/soda_ash", "powder/wood_ash");
        assertThat(reg("ORE_POWDERS").ids()).hasSize(4 + 9 + 12).doesNotContain("powder/gypsum", "powder/halite");
        assertThat(reg("HIDES").ids()).hasSize(5 * 3);
        assertThat(reg("WINDMILL_BLADES").ids()).hasSize(16);
        assertThat(reg("HANGING_SIGNS").ids()).hasSize(20 * 9);
        assertThat(reg("FOOD").ids()).hasSize(182);
        // FluidId: salt + spring water, the simple fluids, 16 dyes and every metal; the exact bucket registrations are not in it
        assertThat(reg("FLUID_BUCKETS").ids()).contains("bucket/salt_water", "bucket/red_dye", "bucket/metal/bronze")
            .doesNotContain("bucket/cod", "bucket/arctic_char");
        long eggModels = TfcItemRegistry.models(tfc).stream().filter(m -> m.startsWith("spawn_egg/")).count();
        List<String> eggs = regs.stream().filter(r -> r.factory().equals("spawn_egg")).flatMap(r -> r.ids().stream()).toList();
        assertThat(eggs).hasSize(83).hasSize((int) eggModels).contains("spawn_egg/cow", "spawn_egg/leopard_seal", "spawn_egg/bluegill");
        assertThat(TfcItemRegistry.ids(regs)).hasSize(1618).doesNotHaveDuplicates();
    }

    /**
     * Every TFC item model with an {@code item.tfc.*} name is a TFCItems registration, except TFCBlocks' block items with an
     * item name: the small ore pieces (groundcover blocks, PAR-WORLD-09) and the ceramic bowl (PAR-POT-09).
     */
    @Test
    void everyNamedItemModelIsRegistered() {
        JsonObject lang = JsonParser.parseString(tfc.readString(MaterialItems.LANG + "en_us.json")).getAsJsonObject();
        Set<String> registered = new HashSet<>(TfcItemRegistry.ids(regs));
        Set<String> missing = new TreeSet<>();
        for (String m : TfcItemRegistry.models(tfc)) {
            if (lang.has("item.tfc." + m.replace('/', '.')) && !registered.contains(m)) {
                missing.add(m);
            }
        }
        Set<String> blockItems = new TreeSet<>(List.of("ceramic/bowl"));
        for (String ore : List.of("bismuthinite", "cassiterite", "garnierite", "hematite", "limonite", "magnetite", "malachite",
            "native_copper", "native_gold", "native_silver", "sphalerite", "tetrahedrite")) {
            blockItems.add("ore/small_" + ore);
        }
        assertThat(missing).isEqualTo(blockItems);
        assertThat(tfc.readString("src/main/java/net/dries007/tfc/common/blocks/TFCBlocks.java"))
            .contains("register((\"ore/small_\" + type.name())").contains("register(\"ceramic/bowl\"");
    }

    @Test
    void groupsArePlainItems() {
        for (String f : MaterialItems.GROUPS.keySet()) {
            boolean special = f.equals("GEM_SAW") || f.equals("WOOL_CLOTH"); // a tool; a glassworking cloth (PAR-PROC-16)
            assertThat(reg(f).plain()).as(f).isEqualTo(!special);
        }
    }

    /** The done criterion: every TFC item has a CE item or a named owner, and the report says so. */
    @Test
    void coverageHasNoUnownedItem() {
        JsonObject report = JsonParser.parseString(new String(full.tree().get(ItemCoverage.REPORT), StandardCharsets.UTF_8))
            .getAsJsonObject();
        assertThat(report.get("total").getAsInt()).isEqualTo(1618);
        assertThat(report.getAsJsonArray("unowned")).isEmpty();
        assertThat(report.get("generated").getAsInt() + report.get("owned").getAsInt()).isEqualTo(1618);
        assertThat(report.getAsJsonObject("items").size()).isEqualTo(1618);
        assertThat(report.getAsJsonObject("items").get("tfc:gem/ruby").getAsString()).isEqualTo("ce:material/gem.yml");
        assertThat(report.getAsJsonObject("items").get("tfc:ore/rich_hematite").getAsString()).isEqualTo("ce:drops.yml");
        assertThat(report.getAsJsonObject("items").get("tfc:stone/axe/sedimentary").getAsString()).isEqualTo("PAR-TOOL-01");
    }

    /** Every owner rule names an existing parity item and matches at least one TFC item. */
    @Test
    void ownersAreRealParityItems() throws Exception {
        List<ItemCoverage.Coverage> rows = ItemCoverage.coverage(tfc, full.tree());
        Map<String, List<String>> byOwner = ItemCoverage.byOwner(rows);
        Path items = Path.of(System.getProperty("tfp.repoRoot", "../..")).resolve("plans/parity/items");
        Assumptions.assumeTrue(Files.isDirectory(items), "no plans/parity/items at " + items);
        Set<String> files;
        try (var s = Files.walk(items)) {
            files = s.map(p -> p.getFileName().toString()).filter(n -> n.endsWith(".md"))
                .map(n -> n.substring(0, n.length() - 3)).collect(Collectors.toSet());
        }
        for (ItemCoverage.Owner o : ItemCoverage.OWNERS) {
            assertThat(files).as("owner item file of " + o.what()).contains(o.owner());
            // the rule names real TFC items (once the owner generates them, the CE item wins and the rule stays harmless)
            assertThat(rows.stream().anyMatch(r -> o.pattern().matcher(r.id()).matches())).as(o.pattern().pattern()).isTrue();
        }
        assertThat(files).contains(ItemCoverage.MOB_BUCKETS.owner());
        // 4 sea + 16 freshwater mob buckets (TFCItems COD_BUCKET.. and FRESHWATER_FISH_BUCKETS) are mob buckets, not fluids
        assertThat(rows.stream().filter(r -> ItemCoverage.MOB_BUCKETS.pattern().matcher(r.id()).matches())).hasSize(20);
        assertThat(byOwner.values().stream().mapToInt(List::size).sum() + rows.stream().filter(r -> r.ceFile().isPresent()).count())
            .isEqualTo(rows.size());
    }

    @Test
    @SuppressWarnings("unchecked")
    void everyItemHasAModelTexturesAndNames() {
        OutputTree out = full.tree();
        JsonObject en = JsonParser.parseString(new String(out.get(MaterialItems.LANG_RP + "en_us.json"), StandardCharsets.UTF_8))
            .getAsJsonObject();
        JsonObject tfcEn = JsonParser.parseString(tfc.readString(MaterialItems.LANG + "en_us.json")).getAsJsonObject();
        JsonObject tfpLang = JsonParser.parseString(new String(out.get(MaterialItems.RP + "lang/en_us.json"),
            StandardCharsets.UTF_8)).getAsJsonObject();
        assertThat(config).hasSizeGreaterThan(170);
        for (Map.Entry<String, Object> e : config.entrySet()) {
            String id = e.getKey().substring("tfp:".length());
            Map<String, Object> args = (Map<String, Object>) ((Map<String, Object>) e.getValue()).get("arguments");
            String key = (String) args.get("item_lang_key");
            assertThat(key).isEqualTo("item.tfp." + id.replace('/', '.'));
            // named once: in our lang file, or already in the asset pipeline's (the ore.* names)
            assertThat(en.has(key)).as(id + " named once").isNotEqualTo(tfpLang.has(key));
            JsonObject named = en.has(key) ? en : tfpLang;
            assertThat(named.get(key).getAsString()).as(id).isEqualTo(tfcEn.get(key.replace("item.tfp.", "item.tfc.")).getAsString());
            String model = MaterialItems.RP + "models/item/" + id + ".json";
            assertThat(out.contains(model)).as(model).isTrue();
            JsonObject json = JsonParser.parseString(new String(out.get(model), StandardCharsets.UTF_8)).getAsJsonObject();
            for (var t : json.getAsJsonObject("textures").entrySet()) {
                String ref = t.getValue().getAsString();
                assertThat(ref).startsWith("tfp:");
                assertThat(out.contains(MaterialItems.RP + "textures/" + ref.substring(4) + ".png")).as(ref).isTrue();
            }
        }
        // other locales carry TFC's translations where TFC has them
        JsonObject de = JsonParser.parseString(new String(out.get(MaterialItems.LANG_RP + "de_de.json"), StandardCharsets.UTF_8))
            .getAsJsonObject();
        assertThat(de.get("item.tfp.glue").getAsString()).isEqualTo(JsonParser.parseString(
            tfc.readString(MaterialItems.LANG + "de_de.json")).getAsJsonObject().get("item.tfc.glue").getAsString());
    }

    /** The ore pieces stay with drops.yml; the item-only ores are ours; nothing is defined twice. */
    @Test
    void noIdIsDefinedTwice() {
        Map<String, String> others = CeItemIds.of(full.tree(), MaterialItems.DIR);
        for (String id : config.keySet()) {
            assertThat(others).as(id).doesNotContainKey(id);
        }
        assertThat(config).containsKeys("tfp:ore/halite", "tfp:ore/lignite", "tfp:ore/bituminous_coal")
            .doesNotContainKeys("tfp:ore/rich_hematite", "tfp:ore/ruby");
        assertThat(others).containsKeys("tfp:ore/rich_hematite", "tfp:ore/ruby");
    }

    /** Golden rows read by hand from TFC's item_size JSON (weights) and TFCItems (durabilities). */
    @Test
    @SuppressWarnings("unchecked")
    void goldenRows() {
        record Row(String id, int stack, Integer damage) {
        }
        List<Row> rows = List.of(
            new Row("glue", 32, null), // glue.json light
            new Row("ore/halite", 16, null), // ores.json (tfc:ore_pieces) medium
            new Row("gem/ruby", 64, null), // no item_size: TFC's default
            new Row("powder/salt", 64, null), // powders.json very_light
            new Row("powder/hematite", 64, null),
            new Row("straw", 64, null), // straw.json very_light
            new Row("wool_cloth", 32, null), // wool_cloth.json light
            new Row("jute_fiber", 64, null), // jute.json very_light
            new Row("brass_mechanisms", 32, null), // brass_mechanisms.json light
            new Row("stick_bundle", 16, null), // stick_bundle.json medium
            new Row("empty_jar", 16, null), // empty_jars.json (tfc:foods/empty_jars) medium
            new Row("handstone", 1, 250),
            new Row("spindle", 1, 40),
            new Row("sandpaper", 1, 40),
            new Row("bone_needle", 1, 64),
            new Row("spawn_egg/cow", 64, null));
        for (Row r : rows) {
            Map<String, Object> entry = (Map<String, Object>) config.get("tfp:" + r.id());
            assertThat(entry).as(r.id()).isNotNull();
            assertThat(entry.get("template")).isEqualTo("tfp:item/simple");
            Map<String, Object> data = (Map<String, Object>) entry.get("data");
            assertThat(data.get("max_stack_size")).as(r.id()).isEqualTo(r.stack());
            assertThat(data.get("max_damage")).as(r.id()).isEqualTo(r.damage());
        }
        Map<String, Object> saw = (Map<String, Object>) config.get("tfp:gem_saw");
        assertThat(saw.get("template")).isEqualTo("tfp:item/tool");
        Map<String, Object> args = (Map<String, Object>) saw.get("arguments");
        // TFCTiers.BRONZE = create("bronze", INCORRECT_FOR_IRON_TOOL, 2, 1300, 7.3f, 4.0f, 13)
        assertThat(args).containsEntry("durability", 1300).containsEntry("break_power", 2)
            .containsEntry("mineable_tag", "tfp:mineable/glass_saw").containsEntry("material", "iron_pickaxe");
        assertThat(args.get("speed").toString()).isEqualTo("7.3");
        // GemSawItem: productAttributes(BRONZE, -0.2f, -2.0f): damage -0.2 x 4.0 (0.2 in game), attack speed -2.0 (2.0 in game)
        List<Map<String, Object>> mods = (List<Map<String, Object>>) ((Map<String, Object>) saw.get("data")).get("attribute_modifiers");
        assertThat(mods).hasSize(2);
        assertThat(mods.get(0)).containsEntry("type", "attack_damage").containsEntry("operation", "add_value").containsEntry("slot", "mainhand");
        assertThat(mods.get(0).get("amount").toString()).isEqualTo("-0.8");
        assertThat(mods.get(1)).containsEntry("type", "attack_speed").containsEntry("slot", "mainhand");
        assertThat(mods.get(1).get("amount").toString()).isEqualTo("-2.0");
        // the handstone takes TFC's healthy Blockbench model (the damaged override needs a NeoForge item property)
        JsonObject hand = JsonParser.parseString(new String(full.tree().get(MaterialItems.RP + "models/item/handstone.json"),
            StandardCharsets.UTF_8)).getAsJsonObject();
        assertThat(hand.has("elements")).isTrue();
        assertThat(hand.has("overrides")).isFalse();
    }

    /** TFC's Properties.durability(n) sets DAMAGE 0 with MAX_DAMAGE n: every item with max_damage also carries damage 0. */
    @Test
    @SuppressWarnings("unchecked")
    void durabilityItemsCarryDamageZero() {
        int count = 0;
        for (Map.Entry<String, Object> e : config.entrySet()) {
            Map<String, Object> data = (Map<String, Object>) ((Map<String, Object>) e.getValue()).get("data");
            if (data == null || !data.containsKey("max_damage")) {
                assertThat(data == null ? null : data.get("components")).as(e.getKey()).isNull();
                continue;
            }
            count++;
            assertThat((Map<String, Object>) data.get("components")).as(e.getKey()).containsEntry("minecraft:damage", 0);
        }
        assertThat(count).isGreaterThanOrEqualTo(4);
        for (String id : List.of("tfp:handstone", "tfp:sandpaper", "tfp:spindle", "tfp:bone_needle")) {
            Map<String, Object> data = (Map<String, Object>) ((Map<String, Object>) config.get(id)).get("data");
            assertThat((Map<String, Object>) data.get("components")).as(id).containsExactly(Map.entry("minecraft:damage", 0));
        }
        // the gem saw's iron_pickaxe base already has vanilla's damage 0 prototype (a damageable item)
        Map<String, Object> saw = (Map<String, Object>) config.get("tfp:gem_saw");
        assertThat((Map<String, Object>) saw.get("arguments")).containsEntry("material", "iron_pickaxe");
    }

    /** The named sample of the done criterion (the ce item give list) is in the config. */
    @Test
    void doneCriterionSampleExists() {
        Map<String, String> all = CeItemIds.of(full.tree(), null);
        for (String id : List.of("ore/rich_hematite", "gem/ruby", "powder/salt", "powder/flux", "ore/halite", "jute_fiber",
            "burlap_cloth", "silk_cloth", "wool_cloth", "papyrus", "unrefined_paper", "glue", "soot", "mortar", "sandpaper",
            "straw", "stick_bunch", "blubber", "wool_yarn", "spawn_egg/cow", "handstone", "gem_saw")) {
            assertThat(all).as(id).containsKey("tfp:" + id);
        }
    }

    /** The copy of TfcItemData picks TFC's load order like the food one (foods before soups and salads). */
    @Test
    void itemDataLoadOrder() {
        List<String> full = new TfcItemData(tfc).definitions("item_size").stream().map(TfcItemData.Definition::id).toList();
        assertThat(full.indexOf("tfc:foods")).isLessThan(full.indexOf("tfc:soups")).isLessThan(full.indexOf("tfc:salads"));
    }
}
