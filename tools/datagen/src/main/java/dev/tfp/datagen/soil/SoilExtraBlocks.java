/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/soil/SoilBlockType.java @ c3b4d9536 (DUFF, CLAY_DUFF, GRASS_PATH,
 *       MUDDY_ROOTS), src/main/java/net/dries007/tfc/common/blocks/soil/{ConnectedDuffBlock,PathBlock}.java @ c3b4d9536,
 *       src/main/resources/assets/tfc/blockstates/{duff,clay_duff,grass_path,muddy_roots}/*.json, models/block/{duff,clay_duff,
 *       grass_path,muddy_roots}, models/block/{duff_top,grass_path}.json, models/item/{duff,clay_duff,grass_path,muddy_roots}
 *       @ c3b4d9536 (copied), data/tfc/loot_table/blocks/{duff,clay_duff,grass_path,muddy_roots}/*.json @ c3b4d9536 (drops)
 */
package dev.tfp.datagen.soil;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.tfp.datagen.CeBlockStates;
import dev.tfp.datagen.CeBlockStates.ModelRef;
import dev.tfp.datagen.DatagenException;
import dev.tfp.datagen.Json;
import dev.tfp.datagen.OutputTree;
import dev.tfp.datagen.TfcCheckout;
import dev.tfp.datagen.hitbox.HitboxAllocation;
import dev.tfp.datagen.hitbox.HitboxDemands;
import dev.tfp.datagen.visuals.VersionFamily;
import dev.tfp.datagen.visuals.VisualDemand;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * PAR-WORLD-42: the soil blocks PAR-WORLD-04 left out, per soil variant, written into the soil files of {@link SoilBlocks}:
 * <ul>
 *   <li>{@code tfp:duff/<soil>} and {@code tfp:clay_duff/<soil>} ({@code ConnectedDuffBlock}): a {@code snowy} property and
 *   one full-cube visual per snowy value from the visual allocator, TFC's connected look (the duff or snow top on every side,
 *   the soil's dirt or clay below, the top's four random turns). The per-side connections are not kept, as for the grass.</li>
 *   <li>{@code tfp:grass_path/<soil>} ({@code PathBlock}, 15/16 high): TFC's model list on a carrier of the farmland outline
 *   where one is free, else hitbox-only with the model as an item display.</li>
 *   <li>{@code tfp:muddy_roots/<soil>} ({@code RotatedPillarBlock}, vanilla muddy mangrove roots, strength 4): an {@code axis}
 *   property, one visual per axis (TFC's cube_column turned as its blockstate).</li>
 * </ul>
 */
public final class SoilExtraBlocks {
    public static final List<String> DUFF_TYPES = List.of("duff", "clay_duff");
    public static final String GRASS_PATH = "grass_path";
    public static final String MUDDY_ROOTS = "muddy_roots";
    static final String SNOWY = SoilBlocks.SNOWY;
    /** TFC {@code MUDDY_ROOTS}: {@code ofFullCopy(Blocks.MUDDY_MANGROVE_ROOTS).strength(4f)} (map colour PODZOL = 34). */
    static final String MUDDY_ROOTS_REGISTRATION = "MUDDY_ROOTS((self, variant) -> new RotatedPillarBlock(BlockBehaviour.Properties"
        + ".ofFullCopy(Blocks.MUDDY_MANGROVE_ROOTS).strength(4f)));";
    static final SoilBlocks.Props MUDDY_ROOTS_PROPS = new SoilBlocks.Props(new BigDecimal("4"), 34,
        "SoundType.MUDDY_MANGROVE_ROOTS", "harp", false, false);
    static final String NEW_FAMILY = "$$>=26.1";
    static final String OLD_FAMILY = "$$<26.1";

    private SoilExtraBlocks() {
    }

    /** The block ids of one soil, in write order. */
    public static List<String> ids(String soil) {
        List<String> ids = new ArrayList<>();
        DUFF_TYPES.forEach(t -> ids.add(t + "/" + soil));
        ids.add(GRASS_PATH + "/" + soil);
        ids.add(MUDDY_ROOTS + "/" + soil);
        return ids;
    }

    /** Every block id over the soil variants. */
    public static List<String> ids(TfcCheckout tfc) {
        List<String> ids = new ArrayList<>();
        SoilBlocks.variants(tfc).forEach(s -> ids.addAll(ids(s)));
        return ids;
    }

    /** The hide mappings of the grass path carriers, collected over the soils (see {@link #mappings}). */
    static final class Hidden {
        final Map<String, String> old = new TreeMap<>();
        final Map<String, String> current = new TreeMap<>();
    }

    /** Writes the blocks of one soil into {@code blocks} and {@code items}; returns the visual demand of its cubes. */
    static List<VisualDemand> write(TfcCheckout tfc, OutputTree out, String soil, Map<String, Object> blocks,
            Map<String, Object> items, Hidden hidden) {
        if (!tfc.readString(SoilBlocks.SOIL_SOURCE).contains(MUDDY_ROOTS_REGISTRATION)) {
            throw new DatagenException(SoilBlocks.SOIL_SOURCE + " no longer registers " + MUDDY_ROOTS_REGISTRATION);
        }
        List<VisualDemand> demand = new ArrayList<>();
        for (String t : DUFF_TYPES) {
            demand.addAll(duff(tfc, out, t + "/" + soil, blocks, items));
        }
        grassPath(tfc, out, soil, blocks, items, hidden);
        demand.addAll(muddyRoots(tfc, out, soil, blocks, items));
        return demand;
    }

    private static void item(TfcCheckout tfc, OutputTree out, String id, Map<String, Object> items) {
        SoilBlocks.copyModel(tfc, out, "item/" + id);
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("template", "tfp:item/block_item");
        item.put("arguments", Map.of("item_lang_key", SoilBlocks.langKey(id)));
        items.put(Json.TO_NS + ":" + id, item);
    }

    private static JsonObject blockstate(TfcCheckout tfc, String id) {
        String src = SoilBlocks.BLOCKSTATES + id + ".json";
        return Json.parse(tfc.readString(src), src).getAsJsonObject();
    }

    private static String texture(TfcCheckout tfc, String model, String key) {
        String src = SoilBlocks.MODELS + model + ".json";
        JsonObject textures = Json.parse(tfc.readString(src), src).getAsJsonObject().getAsJsonObject("textures");
        if (textures == null || !textures.has(key)) {
            throw new DatagenException(src + " has no texture " + key);
        }
        return textures.get(key).getAsString();
    }

    /** The connected duff model of a soil: the duff (or snow) top on five faces, the soil below. */
    static String duffModel(String id, boolean snowy) {
        return Json.TO_NS + ":block/" + id + "/connected" + (snowy ? "_snowy" : "");
    }

    static List<VisualDemand> duff(TfcCheckout tfc, OutputTree out, String id, Map<String, Object> blocks,
            Map<String, Object> items) {
        checkDuffBlockstate(tfc, id);
        String below = texture(tfc, "block/" + id + "/bottom", "texture");
        String top = texture(tfc, "block/duff_top", "overlay");
        String snow = texture(tfc, "block/duff_snowy_top", "overlay");
        for (String t : List.of(below, top, snow)) {
            SoilBlocks.copyTexture(tfc, out, t);
        }
        List<VisualDemand> demand = new ArrayList<>();
        Map<String, Object> variants = new LinkedHashMap<>();
        for (boolean snowy : new boolean[] {false, true}) {
            String model = duffModel(id, snowy);
            out.put(SoilBlocks.RP + "models/" + model.substring(Json.TO_NS.length() + ":".length()) + ".json",
                Json.write(SoilBlocks.sharedModel(below, snowy ? snow : top, false)));
            List<ModelRef> turns = new ArrayList<>();
            for (int y : new int[] {90, 0, 180, 270}) { // TFC's order of the four top turns
                turns.add(new ModelRef(model, 0, y, false, 1));
            }
            String name = SNOWY + "_" + snowy;
            demand.add(new VisualDemand(id, name, turns));
            variants.put(SNOWY + "=" + snowy, Map.of("appearance", name));
        }
        Map<String, Object> snowy = new LinkedHashMap<>();
        snowy.put("type", "boolean");
        snowy.put("default", false);
        Map<String, Object> states = new LinkedHashMap<>();
        states.put("properties", Map.of(SNOWY, snowy));
        states.put("appearances", Map.of("template", CeBlockStates.visualTemplate(id)));
        states.put("variants", variants);
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("settings", SoilBlocks.settings(SoilBlocks.props(tfc, id)));
        entry.put("loot", SoilBlocks.loot(tfc, id));
        entry.put("states", states);
        blocks.put(Json.TO_NS + ":" + id, entry);
        item(tfc, out, id, items);
        return demand;
    }

    /** TFC's connected duff blockstate: bottom, top, side, snowy top and side; snowy and the four sides. */
    static void checkDuffBlockstate(TfcCheckout tfc, String id) {
        JsonObject bs = blockstate(tfc, id);
        if (!bs.has("multipart")) {
            throw new DatagenException(id + " is no longer a multipart blockstate");
        }
        Set<String> models = new TreeSet<>();
        Set<String> properties = new TreeSet<>();
        for (JsonElement part : bs.getAsJsonArray("multipart")) {
            JsonObject p = part.getAsJsonObject();
            if (p.has("when")) {
                properties.addAll(p.getAsJsonObject("when").keySet());
            }
            JsonElement apply = p.get("apply");
            JsonArray list = apply.isJsonArray() ? apply.getAsJsonArray() : new JsonArray();
            if (!apply.isJsonArray()) {
                list.add(apply);
            }
            list.forEach(a -> models.add(a.getAsJsonObject().get("model").getAsString()));
        }
        Set<String> expected = new TreeSet<>();
        for (String part : List.of("bottom", "top", "side", "snowy_top", "snowy_side")) {
            expected.add(Json.FROM_NS + ":block/" + id + "/" + part);
        }
        if (!models.equals(expected) || !properties.equals(Set.of(SNOWY, "north", "east", "south", "west"))) {
            throw new DatagenException(id + " is no longer TFC's connected duff (models " + models + ")");
        }
    }

    /** TFC's variant list of the single-state blockstate, as CraftEngine {@code models} maps. */
    static List<Map<String, Object>> pathModels(TfcCheckout tfc, String id) {
        JsonElement v = blockstate(tfc, id).getAsJsonObject("variants").get("");
        if (v == null || !v.isJsonArray()) {
            throw new DatagenException(id + " no longer draws a list of turned models");
        }
        List<Map<String, Object>> models = new ArrayList<>();
        for (JsonElement e : v.getAsJsonArray()) {
            JsonObject o = e.getAsJsonObject();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("path", Json.renameId(o.get("model").getAsString()));
            if (o.has("y")) {
                m.put("y", o.get("y").getAsInt());
            }
            models.add(m);
        }
        return models;
    }

    /** The carrier of a grass path in one version family (both profiles of the family must agree). */
    static HitboxAllocation.Assignment carrier(String id, VersionFamily family) {
        HitboxAllocation.Assignment first = null;
        for (HitboxAllocation.Result r : HitboxAllocation.standard().values()) {
            if (r.family() != family) {
                continue;
            }
            HitboxAllocation.Assignment a = r.get(id, HitboxDemands.GRASS_PATH_APPEARANCE);
            if (first == null) {
                first = a;
            } else if (!first.state().equals(a.state()) || first.mode() != a.mode()
                || !Objects.equals(first.hideTarget(), a.hideTarget())) {
                throw new DatagenException("hitbox carrier of " + id + " differs between the profiles of " + family.id());
            }
        }
        return Objects.requireNonNull(first, "no hitbox allocation");
    }

    private static Map<String, Object> pathLook(HitboxAllocation.Assignment a, List<Map<String, Object>> models,
            Map<String, Object> items, Map<String, String> hidden) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("state", a.state());
        switch (a.mode()) {
            case MODEL -> m.put("models", models);
            case POOL -> { // an auto_state pool of the outline: CraftEngine gives each soil its own state
                m.remove("state");
                m.put("auto_state", dev.tfp.datagen.hitbox.VanillaCandidates.poolName(a.state()));
                m.put("models", models);
            }
            case HITBOX_ONLY -> {
                String model = (String) models.getFirst().get("path");
                String item = Json.TO_NS + ":render/" + model.substring(model.indexOf(":block/") + ":block/".length());
                Map<String, Object> call = new LinkedHashMap<>();
                call.put("template", "tfp:item/render");
                call.put("arguments", new LinkedHashMap<>(Map.of("render_model", model)));
                items.putIfAbsent(item, call);
                Map<String, Object> renderer = new LinkedHashMap<>();
                renderer.put("item", item);
                renderer.put("rotation", "0,180,0");
                m.put("transparent", true);
                m.put("entity_renderer", List.of(renderer));
            }
            default -> throw new DatagenException(a.demand().id() + " got a " + a.mode() + " carrier");
        }
        if (a.hideTarget() != null && !a.pool()) {
            String prev = hidden.putIfAbsent(a.state(), a.hideTarget());
            if (prev != null && !prev.equals(a.hideTarget())) {
                throw new DatagenException(a.state() + " hidden as both " + prev + " and " + a.hideTarget());
            }
        }
        return m;
    }

    static void grassPath(TfcCheckout tfc, OutputTree out, String soil, Map<String, Object> blocks, Map<String, Object> items,
            Hidden hidden) {
        String id = GRASS_PATH + "/" + soil;
        String ceId = Json.TO_NS + ":" + id;
        List<Map<String, Object>> models = pathModels(tfc, id);
        for (Map<String, Object> m : models) {
            SoilBlocks.copyModel(tfc, out, ((String) m.get("path")).substring(Json.TO_NS.length() + 1));
        }
        Map<String, Object> old = pathLook(carrier(ceId, VersionFamily.V1_21), models, items, hidden.old);
        Map<String, Object> current = pathLook(carrier(ceId, VersionFamily.V26), models, items, hidden.current);
        Map<String, Object> look = current;
        if (!old.equals(current)) {
            look = new LinkedHashMap<>();
            look.put(NEW_FAMILY, current);
            look.put("$$fallback", old);
        }
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("settings", SoilBlocks.settings(SoilBlocks.props(tfc, id)));
        entry.put("loot", SoilBlocks.loot(tfc, id));
        entry.put("state", look);
        blocks.put(ceId, entry);
        item(tfc, out, id, items);
    }

    /** The hide mappings of the grass path carriers: shared ones plain, the others under the family keys. */
    static Map<String, Object> mappings(Hidden hidden) {
        Map<String, Object> shared = new TreeMap<>();
        Map<String, Object> onlyNew = new TreeMap<>();
        Map<String, Object> onlyOld = new TreeMap<>();
        hidden.current.forEach((k, v) -> (v.equals(hidden.old.get(k)) ? shared : onlyNew).put(k, v));
        hidden.old.forEach((k, v) -> {
            if (!v.equals(hidden.current.get(k))) {
                onlyOld.put(k, v);
            }
        });
        Map<String, Object> result = new LinkedHashMap<>(shared);
        if (!onlyNew.isEmpty()) {
            result.put(NEW_FAMILY, onlyNew);
        }
        if (!onlyOld.isEmpty()) {
            result.put(OLD_FAMILY, onlyOld);
        }
        return result;
    }

    static List<VisualDemand> muddyRoots(TfcCheckout tfc, OutputTree out, String soil, Map<String, Object> blocks,
            Map<String, Object> items) {
        String id = MUDDY_ROOTS + "/" + soil;
        JsonObject variants = blockstate(tfc, id).getAsJsonObject("variants");
        if (variants == null || !variants.keySet().equals(Set.of("axis=x", "axis=y", "axis=z"))) {
            throw new DatagenException(id + " is no longer an axis blockstate");
        }
        List<VisualDemand> demand = new ArrayList<>();
        Map<String, Object> ceVariants = new LinkedHashMap<>();
        for (String axis : List.of("x", "y", "z")) {
            JsonObject v = variants.getAsJsonObject("axis=" + axis);
            String model = v.get("model").getAsString();
            SoilBlocks.copyModel(tfc, out, model.substring(model.indexOf(':') + 1));
            int x = v.has("x") ? v.get("x").getAsInt() : 0;
            int y = v.has("y") ? v.get("y").getAsInt() : 0;
            String name = "axis_" + axis;
            demand.add(new VisualDemand(id, name, List.of(new ModelRef(Json.renameId(model), x, y, false, 1))));
            ceVariants.put("axis=" + axis, Map.of("appearance", name));
        }
        Map<String, Object> axis = new LinkedHashMap<>();
        axis.put("type", "axis");
        axis.put("default", "y");
        Map<String, Object> states = new LinkedHashMap<>();
        states.put("properties", Map.of("axis", axis));
        states.put("appearances", Map.of("template", CeBlockStates.visualTemplate(id)));
        states.put("variants", ceVariants);
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("settings", SoilBlocks.settings(MUDDY_ROOTS_PROPS));
        entry.put("loot", SoilBlocks.loot(tfc, id));
        entry.put("states", states);
        blocks.put(Json.TO_NS + ":" + id, entry);
        item(tfc, out, id, items);
        return demand;
    }
}
