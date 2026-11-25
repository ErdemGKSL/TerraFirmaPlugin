/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/TFCBlocks.java @ c3b4d9536 (PUMPKIN, MELON, ROTTEN_PUMPKIN, ROTTEN_MELON),
 *       src/main/java/net/dries007/tfc/common/blocks/crop/TFCPumpkinBlock.java @ c3b4d9536,
 *       src/main/resources/assets/tfc/blockstates/{pumpkin,melon,rotten_pumpkin,rotten_melon}.json, models/block/*, models/item/{pumpkin,melon}.json,
 *       textures/block/crop/rotten_*, textures/item/food/{pumpkin,melon}.png @ c3b4d9536 (copied),
 *       src/main/resources/data/tfc/loot_table/blocks/rotten_{pumpkin,melon}.json @ c3b4d9536 (the drops)
 */
package dev.tfp.datagen.crop;

import com.google.gson.JsonObject;
import dev.tfp.datagen.DatagenException;
import dev.tfp.datagen.Json;
import dev.tfp.datagen.LootTableExport;
import dev.tfp.datagen.OutputTree;
import dev.tfp.datagen.TfcCheckout;
import dev.tfp.datagen.Yaml;
import dev.tfp.datagen.device.DeviceAppearance;
import dev.tfp.datagen.device.DeviceBlockSpec;
import dev.tfp.datagen.device.DeviceSound;
import dev.tfp.datagen.device.DeviceYaml;
import dev.tfp.datagen.hitbox.HitboxDemands;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * PAR-FARM-23: the fruit blocks of the spreading crops as CraftEngine blocks
 * ({@code craftengine/tfp/configuration/crop/fruit.yml}): {@code tfp:fruit/pumpkin}, {@code tfp:fruit/melon} (block items that
 * place them) and {@code tfp:fruit/rotten_pumpkin}, {@code tfp:fruit/rotten_melon}. Full opaque cubes with TFC's models on CraftEngine's
 * solid pool, strength 1.0, wood sounds, push reaction destroy, the axe tag. The device kind {@code tfp:fruit} of PAR-FARM-05 runs the
 * fresh ones. The drops: the rotten ones TFC's table (the fruit item rotten), the fresh ones the fruit item.
 */
public final class FruitBlocks {
    public static final String FILE = CropBlocks.OUTPUT_DIR + "fruit.yml";
    static final String LANG_RP = CropBlocks.PACK + "resourcepack/assets/tfp_fruit/lang/";
    static final String STRENGTH = "1.0";
    public static final List<String> FRUITS = List.of("pumpkin", "melon");

    private FruitBlocks() {
    }

    public static String id(String name) {
        return HitboxDemands.FRUIT_BLOCK_PREFIX + name;
    }

    static Map<String, String> woodSounds() {
        Map<String, String> m = new LinkedHashMap<>();
        for (String k : List.of("break", "step", "place", "hit", "fall")) {
            m.put(k, "minecraft:block.wood." + k);
        }
        return m;
    }

    /** One block: a single full cube appearance on the solid pool. */
    static DeviceBlockSpec spec(TfcCheckout tfc, String name, boolean item) {
        String src = CropBlocks.BLOCKSTATES + name + ".json";
        JsonObject bs = Json.parse(tfc.readString(src), src).getAsJsonObject();
        CropBlocks.Part part = CropBlocks.look(bs, src, Map.of()).getFirst();
        if (part.y() != 0) {
            throw new DatagenException(src + " turns the model");
        }
        String block = id(name);
        if (HitboxDemands.charcoalPitPool(block, "base") == null) {
            throw new DatagenException(block + " has no pool in HitboxDemands.POOLS");
        }
        DeviceBlockSpec.Builder b = DeviceBlockSpec.builder(block)
            .appearance(DeviceAppearance.modelled("base", DeviceAppearance.Carrier.pool(HitboxDemands.charcoalPitPool(block, "base")),
                part.model()))
            .everyState(s -> "base", null)
            .settings(DeviceBlockSpec.Settings.of(STRENGTH, STRENGTH, DeviceSound.STONE, "axe"));
        b.deviceBehaviour();
        if (item) {
            b.blockItem("item." + Json.TO_NS + ".fruit." + name);
        }
        return b.build();
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> yaml(DeviceBlockSpec spec) {
        Map<String, Object> root = DeviceYaml.templated(spec);
        Map<String, Object> block = (Map<String, Object>) ((Map<String, Object>) root.get("blocks")).get(spec.id());
        Map<String, Object> args = (Map<String, Object>) block.get("arguments");
        woodSounds().forEach((k, v) -> args.put("device_sound_" + k, v));
        args.put("device_tool_tag", "minecraft:mineable/axe");
        Map<String, Object> settings = new LinkedHashMap<>();
        settings.put("push_reaction", "destroy");
        Map<String, Object> ordered = new LinkedHashMap<>();
        block.forEach((k, v) -> {
            ordered.put(k, v);
            if (k.equals("arguments")) {
                ordered.put("settings", settings);
            }
        });
        ((Map<String, Object>) root.get("blocks")).put(spec.id(), ordered);
        return root;
    }

    /** Writes the entries, models, textures, loot and names. Returns the number of server states (4). */
    @SuppressWarnings("unchecked")
    public static int run(TfcCheckout tfc, OutputTree out) {
        checkSources(tfc);
        Map<String, Object> blocks = new LinkedHashMap<>();
        Map<String, Object> items = new LinkedHashMap<>();
        Set<String> models = new TreeSet<>();
        int states = 0;
        for (String f : FRUITS) {
            for (String name : List.of(f, "rotten_" + f)) {
                DeviceBlockSpec spec = spec(tfc, name, !name.startsWith("rotten_"));
                states += (int) spec.stateCount();
                models.add(spec.appearances().getFirst().model());
                Map<String, Object> root = yaml(spec);
                blocks.putAll((Map<String, Object>) root.get("blocks"));
                if (root.get("items") != null) {
                    items.putAll((Map<String, Object>) root.get("items"));
                }
            }
            rottenLoot(tfc, out, f);
            freshLoot(out, f);
            itemModel(tfc, out, f);
        }
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("blocks", blocks);
        root.put("items", items);
        out.put(FILE, Yaml.write(DeviceYaml.HEADER, root));
        Set<String> copied = new TreeSet<>();
        for (String m : models) {
            CropBlocks.copyModel(tfc, out, m, copied);
        }
        lang(tfc, out);
        return states;
    }

    /** TFC's rotten table with the fruit item renamed to the block item that places it. */
    private static void rottenLoot(TfcCheckout tfc, OutputTree out, String f) {
        String src = CropBlocks.LOOT + "rotten_" + f + ".json";
        String text = tfc.readString(src).replace("\"" + Json.FROM_NS + ":" + f + "\"", "\"" + id(f) + "\"");
        LootTableExport.delegateBlock(out, "fruit/rotten_" + f, Json.rename(Json.parse(text, src)), n -> { });
    }

    /** A fresh fruit drops the fruit item (TFC drops the stack of its block entity; the food state is not carried, ledger). */
    private static void freshLoot(OutputTree out, String f) {
        String json = "{\"type\":\"minecraft:block\",\"pools\":[{\"name\":\"loot_pool\",\"rolls\":1,\"entries\":[{\"type\":\"minecraft:item\","
            + "\"name\":\"" + id(f) + "\"}],\"conditions\":[{\"condition\":\"minecraft:survives_explosion\"}]}]}";
        LootTableExport.delegateBlock(out, "fruit/" + f, Json.parse(json, "fruit/" + f), n -> { });
    }

    private static void itemModel(TfcCheckout tfc, OutputTree out, String f) {
        String src = CropBlocks.MODELS + "item/" + f + ".json";
        JsonObject m = Json.parse(tfc.readString(src), src).getAsJsonObject();
        String ref = m.getAsJsonObject("textures").get("layer0").getAsString();
        String tex = ref.substring(Json.FROM_NS.length() + 1);
        String png = CropBlocks.TEXTURES + tex + ".png";
        if (!m.get("parent").getAsString().equals("item/generated") || !ref.startsWith(Json.FROM_NS + ":") || !tfc.exists(png)) {
            throw new DatagenException(src + " is no longer a generated item with a TFC texture");
        }
        out.put(CropBlocks.RP + "textures/" + tex + ".png", tfc.read(png));
        out.put(CropBlocks.RP + "models/item/fruit/" + f + ".json", Json.write(Json.rename(m)));
    }

    private static void lang(TfcCheckout tfc, OutputTree out) {
        for (String file : tfc.list(CropBlocks.LANG)) {
            if (!file.endsWith(".json")) {
                continue;
            }
            String locale = file.substring(CropBlocks.LANG.length(), file.length() - ".json".length());
            JsonObject src = Json.parse(tfc.readString(file), file).getAsJsonObject();
            TreeMap<String, String> keys = new TreeMap<>();
            for (String f : FRUITS) {
                String tfcKey = "block." + Json.FROM_NS + "." + f;
                if (src.has(tfcKey)) {
                    keys.put("item." + Json.TO_NS + ".fruit." + f, src.get(tfcKey).getAsString());
                } else if (locale.equals("en_us")) {
                    throw new DatagenException("TFC has no English name " + tfcKey);
                }
            }
            if (!keys.isEmpty()) {
                JsonObject o = new JsonObject();
                keys.forEach(o::addProperty);
                out.put(LANG_RP + locale + ".json", Json.write(o));
            }
        }
    }

    static void checkSources(TfcCheckout tfc) {
        String blocks = tfc.readString("src/main/java/net/dries007/tfc/common/blocks/TFCBlocks.java").replaceAll("\\s+", " ");
        for (String needle : List.of(
            "registerNoItem(\"rotten_pumpkin\", () -> new Block(Properties.of().mapColor(MapColor.COLOR_ORANGE).strength(1.0F)"
                + ".sound(SoundType.WOOD).pushReaction(PushReaction.DESTROY)));",
            "registerNoItem(\"rotten_melon\", () -> new Block(Properties.of().mapColor(MapColor.COLOR_GREEN).strength(1.0F)"
                + ".sound(SoundType.WOOD).pushReaction(PushReaction.DESTROY)));",
            "register(\"pumpkin\", () -> new TFCPumpkinBlock(ExtendedProperties.of(MapColor.COLOR_ORANGE).mapColor(MapColor.COLOR_ORANGE)"
                + ".strength(1.0F).sound(SoundType.WOOD)",
            "register(\"melon\", () -> new DecayingBlock(ExtendedProperties.of(MapColor.COLOR_ORANGE).mapColor(MapColor.COLOR_GREEN)"
                + ".strength(1.0F).sound(SoundType.WOOD)")) {
            if (!blocks.contains(needle)) {
                throw new DatagenException("TFCBlocks no longer registers the fruit block " + needle);
            }
        }
    }
}
