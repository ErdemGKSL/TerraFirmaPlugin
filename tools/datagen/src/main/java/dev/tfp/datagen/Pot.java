/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/devices/PotBlock.java @ c3b4d9536 (the class declaration; its state is inherited from FirepitBlock),
 *       src/main/resources/assets/tfc/blockstates/pot.json @ c3b4d9536 (the multipart parts of the lit x axis states),
 *       src/main/java/net/dries007/tfc/common/blocks/TFCBlocks.java @ c3b4d9536 (strength, sound, lightLevel(litBlockEmission(15))),
 *       src/main/resources/data/tfc/loot_table/blocks/pot.json @ c3b4d9536 (wood ash and the ceramic pot),
 *       src/main/resources/assets/tfc/models/block/firepit_pot.json, firepit_lit_low.json, firepit_unlit.json and their
 *       textures @ c3b4d9536 (the parts of the pot blockstate, merged per lit value, PAR-HEAT-37)
 */
package dev.tfp.datagen;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.tfp.datagen.device.DeviceAppearance;
import dev.tfp.datagen.device.DeviceBlockSpec;
import dev.tfp.datagen.device.DeviceLoot;
import dev.tfp.datagen.device.DeviceProperty;
import dev.tfp.datagen.device.DeviceSpecs;
import dev.tfp.datagen.device.DeviceYaml;
import dev.tfp.datagen.hitbox.HitboxAllocation;
import dev.tfp.datagen.hitbox.HitboxDemands;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * M3-HEAT-03 (block data slice): the TFC pot as one CraftEngine block entry, {@code tfp:pot}. {@code PotBlock} extends
 * {@code FirepitBlock} and adds no state of its own, so its states are the firepit's: {@code lit}, {@code smoke_level} (0 to 4)
 * and {@code axis}, 2 x 5 x 2 = 20 server states. The properties are read through {@link Firepit#inheritedProps}, which
 * refuses a pot that stops extending the firepit or declares its own state.
 *
 * <p>TFC's pot blockstate {@code pot.json} is a multipart: an axis part for the pot model {@code firepit_pot} and a lit part
 * for the flame ({@code firepit_lit_low} or {@code firepit_unlit}), with no smoke level. {@link Firepit#checkMultipart} checks
 * that every state matches a part and that only {@code lit} and {@code axis} are named.
 *
 * <p>PAR-HEAT-37: the look is the grill's ({@link Grill}): a carrier state takes one model, so {@link #models} merges the
 * parts of each lit value into one model ({@code tfp:block/pot_lit}, {@code tfp:block/pot_unlit}); every (lit, axis)
 * appearance is hitbox-only on the carrier the selector agreed for TFC's pot outline, drawn empty, and the merged model is an
 * {@code entity_renderer} item display turned for axis z as the blockstate does. TFC's fluid and soup inside the pot (its
 * block entity renderer) is not drawn: the pot screen shows the contents (ledger divergence). The entry carries TFC's
 * settings, loot (wood ash and the ceramic pot) and lit light.
 *
 * <p>The outputs are {@code craftengine/tfp/devices/pot.yml}, the literal spec outside the CraftEngine
 * {@code configuration/} folder, and {@link #LOADED_OUTPUT} (PAR-HEAT-25), the entry CraftEngine loads: the same spec
 * through the device template, which adds the {@code tfp:device} behaviour, so the placed block routes to the pot device
 * kind ({@code paper/pot}); plus the two merged models with their TFC textures. The pot's behaviour (the boiling, the soups
 * and jams, the fluid transfer, the block entity, the placed block) is not in this entry.
 */
final class Pot {
    static final String SOURCE = "src/main/java/net/dries007/tfc/common/blocks/devices/PotBlock.java";
    static final String CLASS = "PotBlock";
    static final String BLOCKSTATE = TfcCheckout.ASSETS + "blockstates/pot.json";
    static final String OUTPUT = AssetPipeline.PACK + "devices/pot.yml";
    /** PAR-HEAT-25: where the loaded entry goes: the pot spec through the device template, the entry CraftEngine loads. */
    static final String LOADED_OUTPUT = AssetPipeline.PACK + "configuration/devices/pot.yml";
    static final String BLOCK_ID = Json.TO_NS + ":pot";

    private Pot() {
    }

    /** Writes the pot entry and its models. Returns the number of server states. Fails when TFC's sources do not match. */
    static int run(TfcCheckout tfc, OutputTree out) {
        List<DeviceProperty> props = props(tfc);
        List<Map<String, String>> states = DeviceBlockSpec.combinations(props);
        Firepit.checkMultipart(tfc, BLOCKSTATE, states);
        DeviceBlockSpec spec = spec(tfc, props);
        out.put(OUTPUT, DeviceYaml.writeLiteral(spec));
        out.put(LOADED_OUTPUT, DeviceYaml.writeTemplated(Firepit.loaded(spec))); // PAR-HEAT-25: the entry CraftEngine loads
        writeModels(tfc, out);
        return states.size();
    }

    /** PAR-HEAT-25: the loaded entry: the pot spec with the {@code tfp:device} behaviour (template {@code tfp:block/device}). */
    static String loadedYaml(TfcCheckout tfc) {
        return DeviceYaml.writeTemplated(Firepit.loaded(spec(tfc, props(tfc))));
    }

    /** The pot's block state properties: the firepit's, in {@code builder.add} order. */
    static List<DeviceProperty> props(TfcCheckout tfc) {
        return Firepit.inheritedProps(tfc, SOURCE, CLASS);
    }

    /**
     * PAR-HEAT-37: the pot drawn with its merged models: four appearances ({@code lit_x} ... {@code unlit_z}), each on the
     * hitbox-only carrier the hitbox selector agreed on (drawn empty), with one item display of the merged model of its lit
     * value ({@link #renderItem}) turned per axis ({@link Grill#renderRotation}), and TFC's settings, loot and lit light.
     */
    static DeviceBlockSpec spec(TfcCheckout tfc, List<DeviceProperty> props) {
        Map<String, String> hides = new TreeMap<>();
        for (boolean lit : new boolean[] {true, false}) {
            for (String axis : HitboxDemands.FIREPIT_AXES) {
                HitboxAllocation.Assignment a = carrier(lit, axis);
                if (a.hideTarget() != null) {
                    hides.put(a.state(), a.hideTarget());
                }
            }
        }
        return DeviceSpecs.firepitRendered(BLOCK_ID, props,
            (lit, axis) -> DeviceAppearance.Carrier.state(carrier(lit, axis).state()), Pot::renderItem,
            HitboxDemands::potModel, Grill::renderRotation, DeviceBlockSpec.Settings.fromTfc(tfc, "pot"),
            DeviceLoot.fromTfc(tfc, "pot"), Firepit.litLight(tfc, "pot"), hides);
    }

    /** PAR-HEAT-37: the display-only item of the merged model of a lit value ({@code tfp:render/pot_lit}). */
    static String renderItem(boolean lit) {
        String model = HitboxDemands.potModel(lit);
        return Json.TO_NS + ":render/" + model.substring(model.indexOf(":block/") + ":block/".length());
    }

    /** PAR-HEAT-37: the carrier of the pot's (lit, axis) appearance; must be hitbox-only (the look is the display). */
    static HitboxAllocation.Assignment carrier(boolean lit, String axis) {
        String app = HitboxDemands.firepitAppearance(lit, axis);
        HitboxAllocation.Assignment a = HitboxAllocation.agreed(BLOCK_ID, app);
        if (a.mode() != HitboxAllocation.Mode.HITBOX_ONLY) {
            throw new DatagenException(BLOCK_ID + "#" + app + ": the carrier is " + a.mode()
                + ", the pot draws its model as an item display over an empty carrier");
        }
        return a;
    }

    /**
     * PAR-HEAT-37: the TFC models of the axis-x parts of the blockstate that apply to a lit value, in part order
     * ({@code block/firepit_pot}, then {@code block/firepit_lit_low} or {@code block/firepit_unlit}; no namespace).
     */
    static List<String> parts(TfcCheckout tfc, boolean lit) {
        JsonObject root = JsonParser.parseString(tfc.readString(BLOCKSTATE)).getAsJsonObject();
        List<String> out = new ArrayList<>();
        for (JsonElement part : root.getAsJsonArray("multipart")) {
            JsonObject p = part.getAsJsonObject();
            JsonObject when = p.has("when") ? p.getAsJsonObject("when") : new JsonObject();
            if (when.has("axis") && !when.get("axis").getAsString().equals("x")) {
                continue;
            }
            if (when.has("lit") && when.get("lit").getAsBoolean() != lit) {
                continue;
            }
            JsonObject apply = p.getAsJsonObject("apply");
            if (apply.has("y") && apply.get("y").getAsInt() != 0) {
                throw new DatagenException(BLOCKSTATE + ": an axis-x part is turned, the merged model cannot be");
            }
            String model = apply.get("model").getAsString();
            if (!model.startsWith(Json.FROM_NS + ":")) {
                throw new DatagenException(BLOCKSTATE + ": part model " + model + " is not a TFC model");
            }
            out.add(model.substring(Json.FROM_NS.length() + 1));
        }
        return out;
    }

    /**
     * PAR-HEAT-37: the merged model of a lit value: the elements of every part in part order, the textures of every part
     * (the first part's {@code particle} wins), the first part's parent; renamed to {@code tfp:}. Returns null when the
     * checkout has no model of a part (small fixtures). A texture key two parts bind differently is renamed apart.
     */
    static JsonObject merged(TfcCheckout tfc, boolean lit) {
        JsonObject textures = new JsonObject();
        JsonArray elements = new JsonArray();
        String parent = null;
        int index = 0;
        for (String part : parts(tfc, lit)) {
            String src = TfcCheckout.ASSETS + "models/" + part + ".json";
            if (!tfc.exists(src)) {
                return null;
            }
            JsonObject json = Json.parse(tfc.readString(src), src).getAsJsonObject();
            if (json.has("loader")) {
                throw new DatagenException(src + ": model loader " + json.get("loader") + " has no converter");
            }
            if (parent == null && json.has("parent")) {
                parent = json.get("parent").getAsString();
            }
            Map<String, String> renamed = new LinkedHashMap<>();
            if (json.has("textures")) {
                for (Map.Entry<String, JsonElement> t : json.getAsJsonObject("textures").entrySet()) {
                    String key = t.getKey();
                    JsonElement prev = textures.get(key);
                    if (prev != null && !key.equals("particle") && !prev.equals(t.getValue())) {
                        renamed.put(key, "p" + index + "_" + key); // the same key bound to another texture: rename apart
                        key = renamed.get(key);
                    }
                    if (textures.get(key) == null) {
                        textures.add(key, t.getValue());
                    }
                }
            }
            if (json.has("elements")) {
                for (JsonElement e : json.getAsJsonArray("elements")) {
                    elements.add(renamed.isEmpty() ? e : JsonParser.parseString(retexture(e.toString(), renamed)));
                }
            }
            index++;
        }
        JsonObject model = new JsonObject();
        if (parent != null) {
            model.addProperty("parent", parent);
        }
        model.add("textures", textures);
        model.add("elements", elements);
        return Json.rename(model).getAsJsonObject();
    }

    /** An element's JSON with the face textures {@code #key} replaced by {@code #renamed(key)}. */
    private static String retexture(String json, Map<String, String> renamed) {
        String out = json;
        for (Map.Entry<String, String> r : renamed.entrySet()) {
            out = out.replace("\"texture\":\"#" + r.getKey() + "\"", "\"texture\":\"#" + r.getValue() + "\"");
        }
        return out;
    }

    /** PAR-HEAT-37: the merged models by output path ({@code pot_lit}, {@code pot_unlit}); empty in small fixtures. */
    static Map<String, JsonObject> models(TfcCheckout tfc) {
        Map<String, JsonObject> out = new LinkedHashMap<>();
        for (boolean lit : new boolean[] {true, false}) {
            JsonObject m = merged(tfc, lit);
            if (m != null) {
                String id = HitboxDemands.potModel(lit);
                out.put(AssetPipeline.RP + "models/" + id.substring(id.indexOf(':') + 1) + ".json", m);
            }
        }
        return out;
    }

    /** Writes the merged models and the TFC textures they name (with their {@code .mcmeta}); vanilla textures stay vanilla. */
    private static void writeModels(TfcCheckout tfc, OutputTree out) {
        for (Map.Entry<String, JsonObject> e : models(tfc).entrySet()) {
            out.put(e.getKey(), Json.write(e.getValue()));
            for (Map.Entry<String, JsonElement> t : e.getValue().getAsJsonObject("textures").entrySet()) {
                String ref = t.getValue().getAsString();
                if (!ref.startsWith(Json.TO_NS + ":")) {
                    continue;
                }
                String path = ref.substring(Json.TO_NS.length() + 1);
                String src = TfcCheckout.ASSETS + "textures/" + path + ".png";
                if (!tfc.exists(src)) {
                    throw new DatagenException("the pot model uses tfc:" + path + ", which the checkout does not have");
                }
                out.put(AssetPipeline.RP + "textures/" + path + ".png", tfc.read(src));
                if (tfc.exists(src + ".mcmeta")) {
                    out.put(AssetPipeline.RP + "textures/" + path + ".png.mcmeta", tfc.read(src + ".mcmeta"));
                }
            }
        }
    }
}
