/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/devices/NestBoxBlock.java @ c3b4d9536 (SHAPE, no state properties),
 *       src/main/java/net/dries007/tfc/common/blocks/TFCBlocks.java @ c3b4d9536 (NEST_BOX: strength(3f), TFCSounds.THATCH),
 *       src/main/resources/assets/tfc/blockstates/nest_box.json, models/block/nest_box.json, models/item/nest_box.json
 *       and textures/block/thatch.png @ c3b4d9536 (copied),
 *       src/main/resources/data/tfc/loot_table/blocks/nest_box.json @ c3b4d9536 (the drop),
 *       src/generated/resources/data/minecraft/tags/block/mineable/axe.json @ c3b4d9536 (the tool),
 *       src/main/resources/assets/tfc/lang/*.json @ c3b4d9536 (block.tfc.nest_box)
 */
package dev.tfp.datagen.nestbox;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.tfp.datagen.DatagenException;
import dev.tfp.datagen.Json;
import dev.tfp.datagen.OutputTree;
import dev.tfp.datagen.TfcCheckout;
import dev.tfp.datagen.Yaml;
import dev.tfp.datagen.device.DeviceAppearance;
import dev.tfp.datagen.device.DeviceBlockSpec;
import dev.tfp.datagen.device.DeviceLoot;
import dev.tfp.datagen.device.DeviceYaml;
import dev.tfp.datagen.hitbox.HitboxAllocation;
import dev.tfp.datagen.hitbox.HitboxDemands;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PAR-FAUNA-27: the nest box as the CraftEngine block {@code tfp:nest_box}
 * ({@code craftengine/tfp/configuration/nestbox/nest_box.yml}). The plugin's {@code NestBoxDevice} (PAR-FAUNA-07) runs it.
 *
 * <ul>
 *   <li>TFC's {@code NestBoxBlock} has no state property (the hen and egg state live in the block entity, which the device
 *       keeps in the plugin's data), so the block is single-state: one appearance {@code default}, no variants (an empty
 *       variant key would not parse as a state).</li>
 *   <li>The appearance takes the carrier the hitbox selector picks for TFC's outline {@code tfc:nest_box} (box
 *       2,0,2..14,5,14, {@link HitboxDemands#NEST_BOX_ROW}). A carrier the selector serves with our model draws TFC's
 *       {@code block/nest_box} (thatch texture); a hitbox-only carrier is drawn empty and the model is an
 *       {@code entity_renderer} item display ({@link #RENDER_ITEM}).</li>
 *   <li>The {@code tfp:device} behaviour, TFC's settings (strength 3, thatch sounds, the axe tag), the loot (the box itself
 *       under survives_explosion) and the block item with TFC's item model.</li>
 * </ul>
 */
public final class NestBoxBlock {
    public static final String ID = HitboxDemands.NEST_BOX_ID;
    public static final String APPEARANCE = HitboxDemands.NEST_BOX_APPEARANCE;
    static final String BLOCK = "nest_box";
    static final String SOURCE = "src/main/java/net/dries007/tfc/common/blocks/devices/NestBoxBlock.java";
    static final String BLOCKSTATE = TfcCheckout.ASSETS + "blockstates/nest_box.json";
    static final String MODELS = TfcCheckout.ASSETS + "models/";
    static final String TEXTURES = TfcCheckout.ASSETS + "textures/";
    static final String LOOT = TfcCheckout.MAIN + "data/" + Json.FROM_NS + "/loot_table/blocks/nest_box.json";
    static final String LANG = TfcCheckout.ASSETS + "lang/";
    static final String PACK = "craftengine/tfp/";
    public static final String OUTPUT = PACK + "configuration/nestbox/nest_box.yml";
    static final String RP = PACK + "resourcepack/assets/tfp/";
    static final String LANG_RP = PACK + "resourcepack/assets/tfp_nestbox/lang/";
    public static final String LANG_KEY = "block." + Json.TO_NS + "." + BLOCK;
    /** The display-only item that draws TFC's model on a hitbox-only carrier ({@code entity_renderer}). */
    public static final String RENDER_ITEM = Json.TO_NS + ":render/nest_box";
    /** The half turn every block-model item display needs to match the block (see ForgeDevices.OVERLAY_ROTATION). */
    static final String RENDER_ROTATION = "0,180,0";
    private static final Pattern TEXTURE = Pattern.compile(Json.FROM_NS + ":(block/[a-z0-9_/]+)");

    private NestBoxBlock() {
    }

    /** True when the checkout has the nest box (the small test fixtures do not). */
    public static boolean available(TfcCheckout tfc) {
        return tfc.exists(SOURCE) && tfc.exists(MODELS + "block/nest_box.json");
    }

    /** Writes the entry, its model, texture and names. Returns the number of server states (1). */
    public static int run(TfcCheckout tfc, OutputTree out) {
        checkBlockstate(tfc);
        checkLoot(tfc);
        copyModels(tfc, out);
        DeviceBlockSpec spec = spec(tfc);
        out.put(OUTPUT, Yaml.write(DeviceYaml.HEADER, entry(spec)));
        writeLang(tfc, out);
        return (int) spec.stateCount();
    }

    /** TFC's blockstate: one unrotated variant drawing {@code tfc:block/nest_box}. */
    static void checkBlockstate(TfcCheckout tfc) {
        JsonObject variants = Json.parse(tfc.readString(BLOCKSTATE), BLOCKSTATE).getAsJsonObject().getAsJsonObject("variants");
        JsonObject v = variants == null ? null : variants.getAsJsonObject("");
        if (variants.size() != 1 || v == null || v.size() != 1
            || !(Json.FROM_NS + ":block/" + BLOCK).equals(v.get("model").getAsString())) {
            throw new DatagenException(BLOCKSTATE + ": no longer one unrotated variant of tfc:block/" + BLOCK);
        }
    }

    /** TFC's table: one pool, one roll under survives_explosion, one item entry dropping the box. */
    static void checkLoot(TfcCheckout tfc) {
        JsonObject root = Json.parse(tfc.readString(LOOT), LOOT).getAsJsonObject();
        JsonArray pools = root.getAsJsonArray("pools");
        if (pools == null || pools.size() != 1) {
            throw new DatagenException(LOOT + ": not one pool");
        }
        JsonObject pool = pools.get(0).getAsJsonObject();
        JsonArray conditions = pool.getAsJsonArray("conditions");
        JsonArray entries = pool.getAsJsonArray("entries");
        if (pool.get("rolls").getAsDouble() != 1 || conditions == null || conditions.size() != 1
            || !"minecraft:survives_explosion".equals(conditions.get(0).getAsJsonObject().get("condition").getAsString())) {
            throw new DatagenException(LOOT + ": the pool is not one roll under survives_explosion");
        }
        JsonObject entry = entries.size() == 1 ? entries.get(0).getAsJsonObject() : null;
        if (entry == null || !"minecraft:item".equals(entry.get("type").getAsString())
            || !(Json.FROM_NS + ":" + BLOCK).equals(entry.get("name").getAsString()) || entry.size() != 2) {
            throw new DatagenException(LOOT + ": the entry is not the box itself");
        }
    }

    static DeviceBlockSpec spec(TfcCheckout tfc) {
        HitboxAllocation.Assignment a = HitboxAllocation.agreed(ID, APPEARANCE);
        DeviceBlockSpec.Builder b = DeviceBlockSpec.builder(ID);
        DeviceAppearance.Carrier carrier = DeviceAppearance.Carrier.state(a.state());
        switch (a.mode()) {
            case MODEL -> b.appearance(DeviceAppearance.modelled(APPEARANCE, carrier, HitboxDemands.NEST_BOX_MODEL));
            case HITBOX_ONLY -> {
                b.appearance(DeviceAppearance.rendered(APPEARANCE, carrier,
                    List.of(DeviceAppearance.Renderer.item(RENDER_ITEM).rotated(RENDER_ROTATION))));
                b.renderItem(RENDER_ITEM, HitboxDemands.NEST_BOX_MODEL);
            }
            default -> throw new DatagenException("the nest box carrier is " + a.mode()
                + ", NestBoxBlock writes a model or a transparent carrier");
        }
        b.everyState(s -> APPEARANCE, null)
            .settings(DeviceBlockSpec.Settings.fromTfc(tfc, BLOCK))
            .loot(DeviceLoot.self())
            .deviceBehaviour()
            .blockItem(LANG_KEY);
        if (a.hideTarget() != null) {
            b.stateMapping(a.state(), a.hideTarget());
        }
        return b.build();
    }

    /** The templated entry without the states' properties and variants (single-state block, like the scraping block). */
    @SuppressWarnings("unchecked")
    static Map<String, Object> entry(DeviceBlockSpec spec) {
        Map<String, Object> root = DeviceYaml.templated(spec);
        Map<String, Object> block = (Map<String, Object>) ((Map<String, Object>) root.get("blocks")).get(spec.id());
        Map<String, Object> states = (Map<String, Object>) block.get("states");
        states.remove("properties");
        states.remove("variants");
        return root;
    }

    /** TFC's block and item models (namespace renamed) and the textures the block model names. */
    static void copyModels(TfcCheckout tfc, OutputTree out) {
        String src = MODELS + "block/" + BLOCK + ".json";
        String text = tfc.readString(src);
        Matcher m = TEXTURE.matcher(text);
        TreeSet<String> textures = new TreeSet<>();
        while (m.find()) {
            textures.add(m.group(1));
        }
        if (textures.isEmpty()) {
            throw new DatagenException(src + " names no block textures");
        }
        out.put(RP + "models/block/" + BLOCK + ".json", Json.write(Json.rename(Json.parse(text, src).getAsJsonObject())));
        for (String t : textures) {
            String png = TEXTURES + t + ".png";
            if (!tfc.exists(png)) {
                throw new DatagenException("TFC has no texture " + t);
            }
            out.put(RP + "textures/" + t + ".png", tfc.read(png));
            if (tfc.exists(png + ".mcmeta")) {
                out.put(RP + "textures/" + t + ".png.mcmeta", tfc.read(png + ".mcmeta"));
            }
        }
        String isrc = MODELS + "item/" + BLOCK + ".json";
        JsonObject item = Json.parse(tfc.readString(isrc), isrc).getAsJsonObject();
        if (!(Json.FROM_NS + ":block/" + BLOCK).equals(item.get("parent").getAsString())) {
            throw new DatagenException(isrc + " is no longer the block model as parent");
        }
        item.remove("__comment__");
        out.put(RP + "models/item/" + BLOCK + ".json", Json.write(Json.rename(item)));
    }

    /** {@code block.tfp.nest_box} in every TFC locale that names the nest box (English must). */
    static void writeLang(TfcCheckout tfc, OutputTree out) {
        String tfcKey = "block." + Json.FROM_NS + "." + BLOCK;
        for (String f : tfc.list(LANG)) {
            if (!f.endsWith(".json")) {
                continue;
            }
            String locale = f.substring(LANG.length(), f.length() - ".json".length());
            JsonObject src = Json.parse(tfc.readString(f), f).getAsJsonObject();
            if (!src.has(tfcKey)) {
                if (locale.equals("en_us")) {
                    throw new DatagenException("TFC has no English name " + tfcKey);
                }
                continue;
            }
            Map<String, String> keys = new TreeMap<>();
            keys.put(LANG_KEY, src.get(tfcKey).getAsString());
            JsonObject o = new JsonObject();
            keys.forEach(o::addProperty);
            out.put(LANG_RP + locale + ".json", Json.write(o));
        }
    }
}
