/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/BowlBlock.java @ c3b4d9536 (SHAPE, DROP),
 *       src/main/java/net/dries007/tfc/common/blocks/TFCBlocks.java @ c3b4d9536 (WOODEN_BOWL, CERAMIC_BOWL: strength(0.3f),
 *       SoundType.WOOD / STONE, noOcclusion; the wooden bowl has no item of its own),
 *       src/main/java/net/dries007/tfc/client/render/blockentity/BowlBlockEntityRenderer.java @ c3b4d9536 (the powder textures),
 *       src/main/resources/assets/tfc/blockstates/{wooden_bowl,ceramic/bowl}.json, models/block/{template_bowl,wooden_bowl,
 *       ceramic/bowl}.json, models/item/ceramic/bowl.json, textures/block/{wooden_bowl,ceramic_bowl}.png,
 *       textures/block/powder/*.png, textures/item/ceramic/bowl.png @ c3b4d9536 (copied),
 *       src/main/resources/data/tfc/loot_table/blocks/{wooden_bowl,ceramic/bowl}.json @ c3b4d9536 (the vanilla bowl / itself),
 *       src/generated/resources/data/minecraft/tags/block/mineable/*.json @ c3b4d9536 (the tools),
 *       src/main/resources/assets/tfc/lang/*.json @ c3b4d9536 (block.tfc.ceramic.bowl)
 */
package dev.tfp.datagen.bowl;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.tfp.datagen.DatagenException;
import dev.tfp.datagen.Json;
import dev.tfp.datagen.OutputTree;
import dev.tfp.datagen.TfcCheckout;
import dev.tfp.datagen.Yaml;
import dev.tfp.datagen.crop.CropBlocks;
import dev.tfp.datagen.device.DeviceAppearance;
import dev.tfp.datagen.device.DeviceBlockSpec;
import dev.tfp.datagen.device.DeviceLoot;
import dev.tfp.datagen.device.DeviceYaml;
import dev.tfp.datagen.hitbox.HitboxAllocation;
import dev.tfp.datagen.hitbox.HitboxDemands;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * PAR-POT-09: the wooden and the ceramic bowl as the CraftEngine blocks {@code tfp:wooden_bowl} and {@code tfp:ceramic/bowl}
 * ({@code configuration/bowl/bowl.yml}).
 *
 * <ul>
 *   <li>One state each. TFC's blockstate draws {@code block/wooden_bowl} and {@code block/ceramic/bowl} (a 12x3x12 ring of
 *       four boards and a floor on {@code template_bowl}); here that model sits on the carrier the hitbox selector picks for
 *       TFC's {@code BowlBlock.SHAPE} ({@code tfc:ceramic/bowl}).</li>
 *   <li>TFC's settings (strength 0.3, wood / stone sounds, axe / pickaxe), loot (the wooden bowl drops the vanilla bowl, the
 *       ceramic one itself) and the block item {@code tfp:ceramic/bowl} (TFC's flat item model). The wooden bowl has no item:
 *       the vanilla bowl places it.</li>
 *   <li>The powder layer of TFC's {@code BowlBlockEntityRenderer}: one display-only item {@code tfp:render/powder/<name>} per
 *       texture of {@code textures/block/powder/} (a flat quad 8x8 px on the floor of the block, which the plugin raises by
 *       the powder count).</li>
 * </ul>
 */
public final class BowlBlocks {
    static final String BLOCK_SRC = "src/main/java/net/dries007/tfc/common/blocks/BowlBlock.java";
    static final String ASSETS = TfcCheckout.ASSETS;
    static final String LANG = ASSETS + "lang/";
    static final String PACK = "craftengine/tfp/";
    public static final String OUTPUT = PACK + "configuration/bowl/bowl.yml";
    static final String RP = PACK + "resourcepack/assets/tfp/";
    static final String LANG_RP = PACK + "resourcepack/assets/tfp_bowl/lang/";
    static final String POWDER_TEXTURES = ASSETS + "textures/block/powder/";
    public static final String CERAMIC = HitboxDemands.CERAMIC_BOWL_ID;
    public static final String WOODEN = HitboxDemands.WOODEN_BOWL_ID;
    public static final String LANG_KEY = "block." + Json.TO_NS + ".ceramic.bowl";
    /** The render item of a powder texture; the plugin's {@code PaperBowlEffects.RENDER_PREFIX} adds the texture name. */
    public static final String RENDER_PREFIX = Json.TO_NS + ":render/powder/";

    private BowlBlocks() {
    }

    /** True when the checkout has the bowls (the small test fixtures do not). */
    public static boolean available(TfcCheckout tfc) {
        return tfc.exists(BLOCK_SRC) && tfc.exists(ASSETS + "blockstates/wooden_bowl.json")
            && tfc.exists(ASSETS + "models/block/template_bowl.json");
    }

    /** Writes the entries, models, textures and names. Returns the number of server states (2). */
    public static int run(TfcCheckout tfc, OutputTree out) {
        checkSource(tfc);
        Set<String> copied = new TreeSet<>();
        CropBlocks.copyModel(tfc, out, Json.FROM_NS + ":block/wooden_bowl", copied);
        CropBlocks.copyModel(tfc, out, Json.FROM_NS + ":block/ceramic/bowl", copied);
        copyItemModel(tfc, out);
        List<String> powders = copyPowders(tfc, out);
        Map<String, Object> blocks = new LinkedHashMap<>();
        Map<String, Object> items = new LinkedHashMap<>();
        Map<String, Object> mappings = new TreeMap<>();
        for (boolean wooden : new boolean[] {false, true}) {
            DeviceBlockSpec spec = spec(tfc, wooden, powders);
            merge(DeviceYaml.templated(spec), blocks, items, mappings);
        }
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("blocks", blocks);
        root.put("items", items);
        if (!mappings.isEmpty()) {
            root.put("block_state_mappings", mappings);
        }
        out.put(OUTPUT, Yaml.write(DeviceYaml.HEADER, root));
        writeLang(tfc, out);
        return 2;
    }

    // ---- TFC's source -----------------------------------------------------------------------------------

    static void checkSource(TfcCheckout tfc) {
        String src = tfc.readString(BLOCK_SRC).replaceAll("\\s+", " ");
        for (String needle : List.of("public static final VoxelShape SHAPE = box(2, 0, 2, 14, 3, 14);",
                "super(properties, InventoryRemoveBehavior.DROP, SHAPE);",
                "if (held.isEmpty() && hand == InteractionHand.MAIN_HAND)",
                "player.isShiftKeyDown() ? 16 : 1",
                "Helpers.isItem(held, TFCTags.Items.BOWL_POWDERS)",
                "Helpers.isItem(held, TFCTags.Items.CAN_BE_SALTED)")) {
            if (!src.contains(needle)) {
                throw new DatagenException(BLOCK_SRC + " no longer contains \"" + needle + "\": re-check the bowl entry");
            }
        }
        for (String f : List.of("wooden_bowl", "ceramic/bowl")) {
            String bs = ASSETS + "blockstates/" + f + ".json";
            String want = "\"model\": \"" + Json.FROM_NS + ":block/" + f + "\"";
            if (!tfc.readString(bs).contains(want)) {
                throw new DatagenException(bs + " no longer draws " + want);
            }
        }
    }

    // ---- the specs ----------------------------------------------------------------------------------------

    static DeviceBlockSpec spec(TfcCheckout tfc, boolean wooden, List<String> powders) {
        String id = wooden ? WOODEN : CERAMIC;
        HitboxAllocation.Assignment a = HitboxAllocation.agreed(id, HitboxDemands.BOWL_APPEARANCE);
        if (a.mode() != HitboxAllocation.Mode.MODEL) {
            throw new DatagenException(id + " got a " + a.mode() + " carrier; BowlBlocks writes a model");
        }
        DeviceBlockSpec.Builder b = DeviceBlockSpec.builder(id)
            .appearance(DeviceAppearance.modelled(HitboxDemands.BOWL_APPEARANCE, DeviceAppearance.Carrier.state(a.state()),
                wooden ? HitboxDemands.WOODEN_BOWL_MODEL : HitboxDemands.CERAMIC_BOWL_MODEL))
            .everyState(s -> HitboxDemands.BOWL_APPEARANCE, null)
            .settings(DeviceBlockSpec.Settings.fromTfc(tfc, wooden ? "wooden_bowl" : "ceramic/bowl"))
            .loot(DeviceLoot.fromTfc(tfc, wooden ? "wooden_bowl" : "ceramic/bowl"))
            .deviceBehaviour();
        if (a.hideTarget() != null) {
            b.stateMapping(a.state(), a.hideTarget());
        }
        if (!wooden) {
            b.blockItem(LANG_KEY);
            for (String p : powders) {
                b.renderItem(RENDER_PREFIX + p, Json.TO_NS + ":block/powder_layer/" + p);
            }
        }
        return b.build();
    }

    @SuppressWarnings("unchecked")
    private static void merge(Map<String, Object> one, Map<String, Object> blocks, Map<String, Object> items,
            Map<String, Object> mappings) {
        blocks.putAll((Map<String, Object>) one.get("blocks"));
        items.putAll((Map<String, Object>) one.getOrDefault("items", Map.of()));
        for (Map.Entry<String, Object> e : ((Map<String, Object>) one.getOrDefault("block_state_mappings", Map.of())).entrySet()) {
            Object old = mappings.putIfAbsent(e.getKey(), e.getValue());
            if (old != null && !old.equals(e.getValue())) {
                throw new DatagenException("bowl carrier " + e.getKey() + " hidden as " + old + " and " + e.getValue());
            }
        }
    }

    // ---- models, textures, names --------------------------------------------------------------------------

    /** TFC's flat item model of the ceramic bowl ({@code item/generated} over {@code textures/item/ceramic/bowl}). */
    static void copyItemModel(TfcCheckout tfc, OutputTree out) {
        String src = ASSETS + "models/item/ceramic/bowl.json";
        JsonObject item = Json.parse(tfc.readString(src), src).getAsJsonObject();
        String tex = item.getAsJsonObject("textures").get("layer0").getAsString();
        String png = ASSETS + "textures/" + tex.substring(Json.FROM_NS.length() + 1) + ".png";
        if (!tfc.exists(png)) {
            throw new DatagenException(src + " uses missing texture " + tex);
        }
        out.put(RP + "textures/" + tex.substring(Json.FROM_NS.length() + 1) + ".png", tfc.read(png));
        out.put(RP + "models/item/ceramic/bowl.json", Json.write(Json.rename(item)));
    }

    /**
     * The powder textures of {@code BowlBlockEntityRenderer} and one flat model per texture. Returns the texture names in
     * order. TFC lists a texture for every powder item; the checkout's folder is the list.
     */
    static List<String> copyPowders(TfcCheckout tfc, OutputTree out) {
        TreeSet<String> names = new TreeSet<>();
        for (String f : tfc.list(POWDER_TEXTURES)) {
            if (f.endsWith(".png")) {
                names.add(f.substring(POWDER_TEXTURES.length(), f.length() - ".png".length()));
            }
        }
        if (!names.contains("salt")) {
            throw new DatagenException("TFC has no bowl powder texture salt (the renderer's fallback)");
        }
        for (String n : names) {
            out.put(RP + "textures/block/powder/" + n + ".png", tfc.read(POWDER_TEXTURES + n + ".png"));
            out.put(RP + "models/block/powder_layer/" + n + ".json", Json.write(layer(n)));
        }
        return List.copyOf(names);
    }

    /**
     * The powder layer: a quad on the floor of the block, 8x8 px from 4 to 12, drawn by {@code renderTexturedFace} on the
     * whole texture. The plugin raises it by the count.
     */
    static JsonObject layer(String name) {
        String tex = Json.TO_NS + ":block/powder/" + name;
        JsonObject m = new JsonObject();
        m.addProperty("ambientocclusion", false);
        JsonObject textures = new JsonObject();
        textures.addProperty("0", tex);
        textures.addProperty("particle", tex);
        m.add("textures", textures);
        JsonObject element = new JsonObject();
        element.add("from", array(4, 0, 4));
        element.add("to", array(12, 0, 12));
        JsonObject face = new JsonObject();
        face.add("uv", array(0, 0, 16, 16));
        face.addProperty("texture", "#0");
        JsonObject faces = new JsonObject();
        faces.add("up", face);
        element.add("faces", faces);
        JsonArray elements = new JsonArray();
        elements.add(element);
        m.add("elements", elements);
        return m;
    }

    private static JsonArray array(double... v) {
        JsonArray a = new JsonArray();
        for (double d : v) {
            a.add(d);
        }
        return a;
    }

    /** {@code block.tfp.ceramic.bowl} in every TFC locale that names it (English must). */
    static void writeLang(TfcCheckout tfc, OutputTree out) {
        String tfcKey = "block." + Json.FROM_NS + ".ceramic.bowl";
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
            JsonObject o = new JsonObject();
            o.addProperty(LANG_KEY, src.get(tfcKey).getAsString());
            out.put(LANG_RP + locale + ".json", Json.write(o));
        }
    }
}
