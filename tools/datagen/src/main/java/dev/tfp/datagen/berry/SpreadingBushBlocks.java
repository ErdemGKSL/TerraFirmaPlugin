/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/plant/fruit/FruitBlocks.java @ c3b4d9536 (SpreadingBush, createCranberry),
 *       src/main/java/net/dries007/tfc/common/blocks/plant/fruit/{SpreadingBushBlock,SpreadingCaneBlock,DeadCaneBlock,
 *       WaterloggedBerryBushBlock,SeasonalPlantBlock}.java @ c3b4d9536 (properties, getShape),
 *       src/main/java/net/dries007/tfc/common/blocks/TFCBlocks.java @ c3b4d9536 (the registrations),
 *       src/main/resources/assets/tfc/blockstates/plant/{blackberry,raspberry,blueberry,elderberry}_bush{,_cane}.json,
 *       cranberry_bush.json and dead_cane.json, models/block/plant/*, models/item/plant/*_bush.json, textures/block/berry_bush/*.png
 *       @ c3b4d9536 (copied), data/tfc/loot_table/blocks/plant/*.json @ c3b4d9536 (the drops)
 */
package dev.tfp.datagen.berry;

import com.google.gson.JsonObject;
import dev.tfp.datagen.DatagenException;
import dev.tfp.datagen.Json;
import dev.tfp.datagen.LootTableExport;
import dev.tfp.datagen.OutputTree;
import dev.tfp.datagen.TfcCheckout;
import dev.tfp.datagen.Yaml;
import dev.tfp.datagen.crop.CropBlocks;
import dev.tfp.datagen.device.DeviceAppearance;
import dev.tfp.datagen.device.DeviceBlockSpec;
import dev.tfp.datagen.device.DeviceProperty;
import dev.tfp.datagen.device.DeviceSound;
import dev.tfp.datagen.device.DeviceYaml;
import dev.tfp.datagen.hitbox.HitboxDemands;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;

/**
 * PAR-FARM-15: the spreading berry bushes as CraftEngine blocks ({@code craftengine/tfp/configuration/berry/spreading/*.yml}):
 * {@code tfp:spreading_bush/<blackberry|raspberry|blueberry|elderberry>} (lifecycle, stage), their canes
 * {@code <species>_cane} (lifecycle, stage, facing), {@code tfp:spreading_bush/cranberry}, {@code tfp:spreading_bush/dead}
 * (stage) and {@code tfp:spreading_bush/dead_cane} (stage, facing). Like {@link BerryBlocks}: TFC's models as item displays
 * on hitbox-only carriers; the canes have their own outline rows (TFC's {@code CANE_*} boxes, no collision).
 */
public final class SpreadingBushBlocks {
    static final List<String> FACINGS = HitboxDemands.FACINGS;
    static final String BUSH_PATTERN = "bush_*";
    static final String OUT = BerryBlocks.OUTPUT_DIR + "spreading/";

    private SpreadingBushBlocks() {
    }

    static String id(String name) {
        return HitboxDemands.SPREADING_PREFIX + name;
    }

    /** TFC's {@code SpreadingBush} constants. */
    static List<String> species(TfcCheckout tfc) {
        String src = tfc.readString(BerryBlocks.FRUIT_BLOCKS);
        int start = src.indexOf("public enum SpreadingBush");
        List<String> out = new java.util.ArrayList<>();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("^\\s+([A-Z_]+)\\(Food\\.", java.util.regex.Pattern.MULTILINE)
            .matcher(src.substring(start, src.indexOf("public enum StationaryBush", start)));
        while (m.find()) {
            out.add(m.group(1).toLowerCase(java.util.Locale.ROOT));
        }
        return out;
    }

    /**
     * One block: the states are the product of {@code props}; each state draws TFC's {@code blockstate} model(s) on the carrier of
     * the outline pattern {@code pattern(state)}; the appearance is named {@code <pattern prefix>_<model>_<y>}.
     */
    static BerryBlocks.Built block(TfcCheckout tfc, String id, String blockstate, List<DeviceProperty> props,
            Function<Map<String, String>, String> pattern, String itemLangKey) {
        String src = BerryBlocks.BLOCKSTATES + blockstate + ".json";
        JsonObject bs = Json.parse(tfc.readString(src), src).getAsJsonObject();
        DeviceBlockSpec.Builder b = DeviceBlockSpec.builder(id).properties(props)
            .settings(DeviceBlockSpec.Settings.of(BerryBlocks.STRENGTH, BerryBlocks.STRENGTH, DeviceSound.STONE, "hoe"))
            .deviceBehaviour();
        if (itemLangKey != null) {
            b.blockItem(itemLangKey);
        }
        Map<String, DeviceAppearance> apps = new LinkedHashMap<>();
        Map<String, String> byState = new LinkedHashMap<>();
        Set<String> models = new TreeSet<>();
        Set<String> renderItems = new LinkedHashSet<>();
        Map<String, String> hidden = new TreeMap<>();
        for (Map<String, String> state : DeviceBlockSpec.combinations(props)) {
            CropBlocks.Part part = CropBlocks.look(bs, src, state).getFirst();
            String pat = pattern.apply(state);
            String model = part.model();
            String name = pat.substring(0, pat.length() - 1) + model.substring(model.lastIndexOf('/') + 1) + "_" + part.y();
            byState.put(state.toString(), name);
            if (!apps.containsKey(name)) {
                models.add(model);
                apps.put(name, BerryBlocks.appearance(id, name, pat, model, part.y(), b, renderItems, hidden));
            }
        }
        apps.values().forEach(b::appearance);
        b.everyState(s -> byState.get(s.toString()), null);
        return new BerryBlocks.Built(b.build(), models);
    }

    private static List<DeviceProperty> living(boolean facing) {
        List<DeviceProperty> p = new java.util.ArrayList<>(List.of(
            DeviceProperty.names("lifecycle", BerryBlocks.LIFECYCLES, "dormant"), DeviceProperty.range("stage", 0, 2, 0)));
        if (facing) {
            p.add(DeviceProperty.names("facing", FACINGS, "north"));
        }
        return p;
    }

    private static Function<Map<String, String>, String> bushPattern() {
        return s -> BUSH_PATTERN;
    }

    private static Function<Map<String, String>, String> canePattern() {
        return s -> HitboxDemands.canePattern(s.get("facing"));
    }

    /** Writes the entries, models, textures, loot and names. Returns the number of server states. */
    public static int run(TfcCheckout tfc, OutputTree out) {
        List<String> species = species(tfc);
        if (!species.equals(HitboxDemands.SPREADING_SPECIES)) {
            throw new DatagenException("TFC lists spreading bushes " + species + " but HitboxDemands.SPREADING_SPECIES is "
                + HitboxDemands.SPREADING_SPECIES);
        }
        checkSources(tfc);
        int states = 0;
        Set<String> models = new TreeSet<>();
        List<String> named = new java.util.ArrayList<>();
        for (String s : species) {
            BerryBlocks.Built bush = block(tfc, id(s), s + "_bush", living(false), bushPattern(), "item." + Json.TO_NS + ".spreading_bush." + s);
            BerryBlocks.Built cane = block(tfc, id(s + "_cane"), s + "_bush_cane", living(true), canePattern(), null);
            states += write(out, s, List.of(bush, cane), BerryBlocks.LIVING_FIRE, models);
            loot(tfc, out, "plant/" + s + "_bush", s, id(s));
            loot(tfc, out, "plant/" + s + "_bush_cane", s + "_cane", null);
            copyItem(tfc, out, s, s + "_bush", models);
            named.add(s);
        }
        BerryBlocks.Built cranberry = block(tfc, id("cranberry"), "cranberry_bush", living(false), bushPattern(),
            "item." + Json.TO_NS + ".spreading_bush.cranberry");
        states += write(out, "cranberry", List.of(cranberry), BerryBlocks.LIVING_FIRE, models);
        loot(tfc, out, "plant/cranberry_bush", "cranberry", id("cranberry"));
        copyItem(tfc, out, "cranberry", "cranberry_bush", models);
        named.add("cranberry");
        BerryBlocks.Built dead = block(tfc, id("dead"), "dead_berry_bush", List.of(DeviceProperty.range("stage", 0, 2, 0)),
            bushPattern(), null);
        BerryBlocks.Built deadCane = block(tfc, id("dead_cane"), "dead_cane", List.of(DeviceProperty.range("stage", 0, 2, 0),
            DeviceProperty.names("facing", FACINGS, "north")), bushPattern(), null);
        states += write(out, "dead", List.of(dead, deadCane), BerryBlocks.DEAD_FIRE, models);
        LootTableExport.delegateBlock(out, "spreading_bush/dead", Json.rename(Json.parse(tfc.readString(BerryBlocks.LOOT
            + "dead_berry_bush.json"), "dead_berry_bush")), n -> { });
        loot(tfc, out, "plant/dead_cane", "dead_cane", null);
        Set<String> copied = new TreeSet<>();
        for (String m : models) {
            CropBlocks.copyModel(tfc, out, m, copied);
        }
        writeLang(tfc, out, named);
        return states;
    }

    @SuppressWarnings("unchecked")
    private static int write(OutputTree out, String file, List<BerryBlocks.Built> blocks, int[] fire, Set<String> models) {
        Map<String, Object> allBlocks = new LinkedHashMap<>();
        Map<String, Object> items = new LinkedHashMap<>();
        Map<String, Object> mappings = new TreeMap<>();
        int n = 0;
        for (BerryBlocks.Built b : blocks) {
            n += (int) b.spec().stateCount();
            models.addAll(b.models());
            Map<String, Object> root = BerryBlocks.yaml(b.spec(), fire);
            allBlocks.putAll((Map<String, Object>) root.get("blocks"));
            if (root.get("items") != null) {
                items.putAll((Map<String, Object>) root.get("items"));
            }
            if (root.get("block_state_mappings") != null) {
                mappings.putAll((Map<String, Object>) root.get("block_state_mappings"));
            }
        }
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("blocks", allBlocks);
        if (!items.isEmpty()) {
            root.put("items", items);
        }
        if (!mappings.isEmpty()) {
            root.put("block_state_mappings", mappings);
        }
        out.put(OUT + file + ".yml", Yaml.write(DeviceYaml.HEADER, root));
        return n;
    }

    /** TFC's table of {@code plant/<x>} as ours of {@code spreading_bush/<ours>}; the block item is renamed to {@code item}. */
    private static void loot(TfcCheckout tfc, OutputTree out, String tfcPath, String ours, String item) {
        String src = BerryBlocks.LOOT.replace("plant/", "") + tfcPath + ".json";
        String text = tfc.readString(src);
        if (item != null) {
            text = text.replace("\"" + Json.FROM_NS + ":" + tfcPath + "\"", "\"" + item + "\"");
        }
        LootTableExport.delegateBlock(out, "spreading_bush/" + ours, Json.rename(Json.parse(text, src)), n -> { });
    }

    private static void copyItem(TfcCheckout tfc, OutputTree out, String ours, String tfcName, Set<String> models) {
        String item = BerryBlocks.MODELS_ITEM + tfcName + ".json";
        JsonObject itemModel = Json.parse(tfc.readString(item), item).getAsJsonObject();
        out.put(BerryBlocks.RP + "models/item/spreading_bush/" + ours + ".json", Json.write(Json.rename(itemModel)));
        models.add(itemModel.get("parent").getAsString());
    }

    static void checkSources(TfcCheckout tfc) {
        String fruit = tfc.readString(BerryBlocks.FRUIT_BLOCKS).replaceAll("\\s+", " ");
        for (String needle : List.of("return new SpreadingBushBlock(ExtendedProperties.of(MapColor.PLANT).strength(0.6f).noOcclusion()"
            + ".randomTicks().sound(SoundType.SWEET_BERRY_BUSH).blockEntity(TFCBlockEntities.BERRY_BUSH).flammableLikeLeaves(),",
            "return new SpreadingCaneBlock(ExtendedProperties.of(MapColor.PLANT).strength(0.6f).noOcclusion().randomTicks()"
                + ".sound(SoundType.SWEET_BERRY_BUSH).blockEntity(TFCBlockEntities.BERRY_BUSH).flammableLikeLeaves(),",
            "return new WaterloggedBerryBushBlock(ExtendedProperties.of(MapColor.PLANT).strength(0.6f).noOcclusion().randomTicks()"
                + ".sound(SoundType.SWEET_BERRY_BUSH).blockEntity(TFCBlockEntities.BERRY_BUSH).flammableLikeLeaves(),")) {
            if (!fruit.contains(needle)) {
                throw new DatagenException(BerryBlocks.FRUIT_BLOCKS + " no longer contains " + needle);
            }
        }
        String blocks = tfc.readString("src/main/java/net/dries007/tfc/common/blocks/TFCBlocks.java").replaceAll("\\s+", " ");
        String dead = "registerNoItem(\"plant/dead_cane\", () -> new DeadCaneBlock(ExtendedProperties.of(MapColor.PLANT).strength(0.6f)"
            + ".noOcclusion().sound(SoundType.SWEET_BERRY_BUSH).randomTicks().blockEntity(TFCBlockEntities.TICK_COUNTER)"
            + ".flammable(120, 90)));";
        if (!blocks.contains(dead)) {
            throw new DatagenException("TFCBlocks no longer registers the dead cane as " + dead);
        }
    }

    private static void writeLang(TfcCheckout tfc, OutputTree out, List<String> names) {
        for (String f : tfc.list(BerryBlocks.LANG)) {
            if (!f.endsWith(".json")) {
                continue;
            }
            String locale = f.substring(BerryBlocks.LANG.length(), f.length() - ".json".length());
            JsonObject src = Json.parse(tfc.readString(f), f).getAsJsonObject();
            TreeMap<String, String> keys = new TreeMap<>();
            for (String s : names) {
                String tfcKey = "block." + Json.FROM_NS + ".plant." + s + "_bush";
                if (src.has(tfcKey)) {
                    keys.put("item." + Json.TO_NS + ".spreading_bush." + s, src.get(tfcKey).getAsString());
                } else if (locale.equals("en_us")) {
                    throw new DatagenException("TFC has no English name " + tfcKey);
                }
            }
            if (!keys.isEmpty()) {
                JsonObject o = new JsonObject();
                keys.forEach(o::addProperty);
                out.put(BerryBlocks.LANG_RP.replace("tfp_berry", "tfp_spreading_bush") + locale + ".json", Json.write(o));
            }
        }
    }
}
