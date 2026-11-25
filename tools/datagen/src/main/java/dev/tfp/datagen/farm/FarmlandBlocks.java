/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/soil/SoilBlockType.java @ c3b4d9536 (the Variant constants and the
 *       FARMLAND properties: strength 1.3, gravel sound, map colour, view blocking and suffocating),
 *       src/main/java/net/dries007/tfc/common/blocks/soil/FarmlandBlock.java @ c3b4d9536 (SHAPE, the dirt it turns into),
 *       src/main/resources/assets/tfc/blockstates/farmland/*.json, models/block/farmland/*.json, models/item/farmland/*.json
 *       and textures/block/farmland/*.png @ c3b4d9536 (copied),
 *       src/main/resources/data/tfc/loot_table/blocks/farmland/*.json @ c3b4d9536 (the dirt drop),
 *       src/generated/resources/data/minecraft/tags/block/mineable/shovel.json @ c3b4d9536 (the tool),
 *       src/main/resources/assets/tfc/lang/*.json @ c3b4d9536 (block.tfc.farmland.*)
 */
package dev.tfp.datagen.farm;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
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
import dev.tfp.datagen.hitbox.VanillaCandidates;
import dev.tfp.datagen.soil.SoilBlocks;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PAR-FARM-26: the farmland of every soil as the CraftEngine blocks {@code tfp:farmland/<soil>}
 * ({@code craftengine/tfp/configuration/farm/farmland.yml}). The plugin's {@code FarmlandDevice} (PAR-FARM-01, kind
 * {@code tfp:farmland}) runs them, and {@code SoilListener} (PAR-GATHER-05) tills dirt to them.
 *
 * <ul>
 *   <li>One state per block and one appearance, {@code base}: TFC's farmland has no block state property (its blockstate
 *       is a single variant, and the hydration is read from the climate and the block entity, never drawn), so there is no
 *       dry or wet look to add. The carrier is the one the hitbox selector picks for TFC's outline
 *       ({@code tfc:farmland}, 16 x 15 x 16). A carrier served with our model draws TFC's model of the soil
 *       ({@code block/template_farmland} with the soil's dirt and top texture); a hitbox-only carrier is drawn empty with
 *       the model as an {@code entity_renderer} item display ({@code tfp:render/farmland/<soil>}).</li>
 *   <li>Settings are TFC's (strength 1.3, gravel sounds, shovel tag, view blocking and suffocating as TFC; not an
 *       occluding cube), the {@code tfp:device} behaviour, the loot (TFC's table: the soil's dirt) and a block item with
 *       TFC's item model.</li>
 * </ul>
 */
public final class FarmlandBlocks {
    static final String SOIL_SOURCE = "src/main/java/net/dries007/tfc/common/blocks/soil/SoilBlockType.java";
    static final String SOURCE = "src/main/java/net/dries007/tfc/common/blocks/soil/FarmlandBlock.java";
    static final String BLOCKSTATES = TfcCheckout.ASSETS + "blockstates/farmland/";
    static final String MODELS = TfcCheckout.ASSETS + "models/";
    static final String TEXTURES = TfcCheckout.ASSETS + "textures/";
    static final String LOOT = TfcCheckout.MAIN + "data/" + Json.FROM_NS + "/loot_table/blocks/farmland/";
    static final String LANG = TfcCheckout.ASSETS + "lang/";
    static final String SHOVEL = TfcCheckout.GENERATED + "data/minecraft/tags/block/mineable/shovel.json";
    static final String PACK = "craftengine/tfp/";
    public static final String OUTPUT = PACK + "configuration/farm/farmland.yml";
    static final String RP = PACK + "resourcepack/assets/tfp/";
    static final String LANG_RP = PACK + "resourcepack/assets/tfp_farmland/lang/";
    /** The half turn every block-model item display needs to match the block (see ForgeDevices.OVERLAY_ROTATION). */
    static final String RENDER_ROTATION = "0,180,0";
    /** TFC's farmland strength and vanilla's DIRT map colour id. */
    static final BigDecimal STRENGTH = new BigDecimal("1.3");
    static final int MAP_COLOR_DIRT = 10;
    private static final Pattern FARMLAND_LINE = Pattern.compile("(?m)^\\s*FARMLAND\\(\\(self, variant\\) -> new FarmlandBlock\\((.*)$");
    private static final Pattern TEXTURE = Pattern.compile(Json.FROM_NS + ":(block/[a-z0-9_/]+)");

    private FarmlandBlocks() {
    }

    /** True when the checkout has the farmland (the small test fixtures do not). */
    public static boolean available(TfcCheckout tfc) {
        return tfc.exists(SOIL_SOURCE) && tfc.exists(SOURCE) && tfc.exists(BLOCKSTATES + "alfisol.json");
    }

    /** The display-only item that draws the model of {@code soil} on a hitbox-only carrier. */
    public static String renderItem(String soil) {
        return Json.TO_NS + ":render/farmland/" + soil;
    }

    /** The lang key of the farmland of {@code soil}. */
    public static String langKey(String soil) {
        return "block." + Json.TO_NS + ".farmland." + soil;
    }

    /** Writes the entries, models, textures and names. Returns the number of blocks (one state each). */
    public static int run(TfcCheckout tfc, OutputTree out) {
        List<String> soils = SoilBlocks.variants(tfc);
        if (!new java.util.TreeSet<>(soils).equals(new java.util.TreeSet<>(HitboxDemands.FARMLAND_SOILS))) {
            throw new DatagenException("TFC's soils " + soils + " differ from HitboxDemands.FARMLAND_SOILS "
                + HitboxDemands.FARMLAND_SOILS + ": update the demands");
        }
        checkSource(tfc);
        Map<String, Object> blocks = new LinkedHashMap<>();
        Map<String, Object> items = new LinkedHashMap<>();
        Map<String, Object> mappings = new LinkedHashMap<>();
        for (String soil : HitboxDemands.FARMLAND_SOILS) {
            checkBlockstate(tfc, soil);
            DeviceBlockSpec spec = spec(tfc, soil);
            Map<String, Object> one = DeviceYaml.literal(spec);
            @SuppressWarnings("unchecked")
            Map<String, Object> entry = (Map<String, Object>) ((Map<String, Object>) one.get("blocks")).get(spec.id());
            Map<String, Object> block = new LinkedHashMap<>();
            block.put("settings", settings());
            block.put("loot", entry.get("loot"));
            block.put("behavior", entry.get("behavior"));
            block.put("states", entry.get("states"));
            blocks.put(spec.id(), block);
            @SuppressWarnings("unchecked")
            Map<String, Object> its = (Map<String, Object>) one.get("items");
            items.putAll(its);
            Object m = one.get("block_state_mappings");
            if (m != null) {
                @SuppressWarnings("unchecked")
                Map<String, Object> mm = (Map<String, Object>) m;
                mappings.putAll(mm);
            }
            copyModels(tfc, out, soil);
        }
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("blocks", blocks);
        root.put("items", items);
        if (!mappings.isEmpty()) {
            root.put("block_state_mappings", mappings);
        }
        out.put(OUTPUT, Yaml.write(DeviceYaml.HEADER, root));
        writeLang(tfc, out);
        return blocks.size();
    }

    /** TFC's registration: strength 1.3, gravel sound, DIRT colour, view blocking and suffocating, ticking device. */
    static void checkSource(TfcCheckout tfc) {
        Matcher m = FARMLAND_LINE.matcher(tfc.readString(SOIL_SOURCE));
        if (!m.find()) {
            throw new DatagenException(SOIL_SOURCE + " no longer registers FARMLAND on one line");
        }
        String chain = m.group(1);
        for (String needle : List.of("MapColor.DIRT", ".strength(1.3f)", ".sound(SoundType.GRAVEL)",
            ".isViewBlocking(TFCBlocks::always)", ".isSuffocating(TFCBlocks::always)")) {
            if (!chain.contains(needle)) {
                throw new DatagenException(SOIL_SOURCE + ": FARMLAND no longer has " + needle + ": re-check FarmlandBlocks");
            }
        }
        if (chain.contains("noOcclusion") || chain.contains("lightLevel") || chain.contains("friction")
            || chain.contains("speedFactor") || chain.contains("jumpFactor")) {
            throw new DatagenException(SOIL_SOURCE + ": FARMLAND has a property FarmlandBlocks does not write: " + chain);
        }
        String src = tfc.readString(SOURCE);
        if (!src.contains("public static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 15, 16);")) {
            throw new DatagenException(SOURCE + " no longer has the 16 x 15 x 16 SHAPE: re-check the tfc:farmland row");
        }
        JsonObject tag = Json.parse(tfc.readString(SHOVEL), SHOVEL).getAsJsonObject();
        for (String soil : HitboxDemands.FARMLAND_SOILS) {
            boolean found = false;
            for (JsonElement v : tag.getAsJsonArray("values")) {
                found |= v.isJsonPrimitive() && v.getAsString().equals(Json.FROM_NS + ":farmland/" + soil);
            }
            if (!found) {
                throw new DatagenException(SHOVEL + " no longer lists tfc:farmland/" + soil);
            }
        }
    }

    /** TFC's blockstate: one variant, unrotated, drawing {@code tfc:block/farmland/<soil>}. */
    static void checkBlockstate(TfcCheckout tfc, String soil) {
        String src = BLOCKSTATES + soil + ".json";
        JsonObject variants = Json.parse(tfc.readString(src), src).getAsJsonObject().getAsJsonObject("variants");
        String want = Json.FROM_NS + ":block/farmland/" + soil;
        if (variants == null || variants.size() != 1 || !variants.has("") || !variants.get("").isJsonObject()
            || variants.getAsJsonObject("").size() != 1 || !want.equals(variants.getAsJsonObject("").get("model").getAsString())) {
            throw new DatagenException(src + " is no longer one unrotated variant drawing " + want);
        }
    }

    /** TFC's loot: the soil's dirt under survives_explosion (tfc:dirt/&lt;soil&gt;, renamed). */
    static DeviceLoot loot(TfcCheckout tfc, String soil) {
        DeviceLoot loot = DeviceLoot.fromTfc(tfc, "farmland/" + soil);
        return loot;
    }

    static DeviceBlockSpec spec(TfcCheckout tfc, String soil) {
        String id = HitboxDemands.farmlandId(soil);
        DeviceBlockSpec.Builder b = DeviceBlockSpec.builder(id);
        HitboxAllocation.Assignment a = HitboxAllocation.agreed(id, HitboxDemands.FARMLAND_APPEARANCE);
        String model = HitboxDemands.farmlandModel(soil);
        if (a.pool()) { // the selector served the farmland outline from a CraftEngine auto_state pool
            b.appearance(DeviceAppearance.modelled(HitboxDemands.FARMLAND_APPEARANCE,
                DeviceAppearance.Carrier.pool(VanillaCandidates.poolName(a.state())), model));
            return b.everyState(s -> HitboxDemands.FARMLAND_APPEARANCE, null).loot(loot(tfc, soil)).deviceBehaviour()
                .blockItem(langKey(soil)).build();
        }
        DeviceAppearance.Carrier carrier = DeviceAppearance.Carrier.state(a.state());
        switch (a.mode()) {
            case MODEL -> b.appearance(DeviceAppearance.modelled(HitboxDemands.FARMLAND_APPEARANCE, carrier, model));
            case HITBOX_ONLY -> {
                b.appearance(DeviceAppearance.rendered(HitboxDemands.FARMLAND_APPEARANCE, carrier,
                    List.of(DeviceAppearance.Renderer.item(renderItem(soil)).rotated(RENDER_ROTATION))));
                b.renderItem(renderItem(soil), model);
            }
            default -> throw new DatagenException("the farmland carrier of " + soil + " is " + a.mode()
                + ", FarmlandBlocks writes a model or a transparent carrier");
        }
        if (a.hideTarget() != null) {
            b.stateMapping(a.state(), a.hideTarget());
        }
        return b.everyState(s -> HitboxDemands.FARMLAND_APPEARANCE, null)
            .loot(loot(tfc, soil))
            .deviceBehaviour()
            .blockItem(langKey(soil))
            .build();
    }

    /** TFC's settings: {@code tfp:settings/sound} (gravel), strength 1.3, the shovel tag, TFC's view and suffocation rules. */
    static Map<String, Object> settings() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("template", "tfp:settings/sound");
        m.put("arguments", Map.of("sound_block_type", "gravel"));
        m.put("hardness", STRENGTH);
        m.put("resistance", STRENGTH);
        m.put("instrument", "harp");
        m.put("map_color", MAP_COLOR_DIRT);
        m.put("is_suffocating", true);
        m.put("is_view_blocking", true);
        m.put("can_occlude", false);
        m.put("tags", List.of("minecraft:mineable/shovel"));
        return m;
    }

    /** TFC's block model and item model of a soil (renamed) and their textures. */
    static void copyModels(TfcCheckout tfc, OutputTree out, String soil) {
        String path = "block/farmland/" + soil;
        String src = MODELS + path + ".json";
        JsonObject m = Json.parse(tfc.readString(src), src).getAsJsonObject();
        if (!"block/template_farmland".equals(m.get("parent").getAsString().replace("minecraft:", ""))
            || m.getAsJsonObject("textures") == null) {
            throw new DatagenException(src + " is no longer vanilla's template_farmland with textures");
        }
        m.remove("__comment__");
        for (Map.Entry<String, JsonElement> e : m.getAsJsonObject("textures").entrySet()) {
            Matcher x = TEXTURE.matcher(e.getValue().getAsString());
            if (!x.matches()) {
                throw new DatagenException(src + ": texture " + e.getValue() + " is not a tfc:block texture");
            }
            String png = TEXTURES + x.group(1) + ".png";
            if (!tfc.exists(png)) {
                throw new DatagenException("TFC has no texture " + x.group(1));
            }
            out.put(RP + "textures/" + x.group(1) + ".png", tfc.read(png));
        }
        out.put(RP + "models/" + path + ".json", Json.write(Json.rename(m)));
        String itemSrc = MODELS + "item/farmland/" + soil + ".json";
        JsonObject item = Json.parse(tfc.readString(itemSrc), itemSrc).getAsJsonObject();
        if (!(Json.FROM_NS + ":" + path).equals(item.get("parent").getAsString())) {
            throw new DatagenException(itemSrc + " is no longer the block model as parent");
        }
        item.remove("__comment__");
        out.put(RP + "models/item/farmland/" + soil + ".json", Json.write(Json.rename(item)));
    }

    /** {@code block.tfp.farmland.<soil>} in every TFC locale that names it (English must). */
    static void writeLang(TfcCheckout tfc, OutputTree out) {
        for (String f : tfc.list(LANG)) {
            if (!f.endsWith(".json")) {
                continue;
            }
            String locale = f.substring(LANG.length(), f.length() - ".json".length());
            JsonObject src = Json.parse(tfc.readString(f), f).getAsJsonObject();
            Map<String, String> keys = new TreeMap<>();
            for (String soil : HitboxDemands.FARMLAND_SOILS) {
                String tfcKey = "block." + Json.FROM_NS + ".farmland." + soil;
                if (src.has(tfcKey)) {
                    keys.put(langKey(soil), src.get(tfcKey).getAsString());
                } else if (locale.equals("en_us")) {
                    throw new DatagenException("TFC has no English name " + tfcKey);
                }
            }
            if (keys.isEmpty()) {
                continue;
            }
            JsonObject o = new JsonObject();
            keys.forEach(o::addProperty);
            out.put(LANG_RP + locale + ".json", Json.write(o));
        }
    }
}
