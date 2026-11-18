/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/devices/GrillBlock.java @ c3b4d9536 (the class declaration; its state is inherited from FirepitBlock),
 *       src/main/resources/assets/tfc/blockstates/grill.json @ c3b4d9536 (the multipart parts of the lit x axis states),
 *       src/main/resources/assets/tfc/models/block/firepit_grill.json @ c3b4d9536 (the grill model, merged over the fire),
 *       src/main/java/net/dries007/tfc/common/blocks/TFCBlocks.java @ c3b4d9536 (strength, sound, lightLevel(litBlockEmission(15))),
 *       src/main/resources/data/tfc/loot_table/blocks/grill.json @ c3b4d9536 (wood ash and the wrought iron grill)
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
 * M3-HEAT-03 (block data slice): the TFC grill as one CraftEngine block entry, {@code tfp:grill}. {@code GrillBlock} extends
 * {@code FirepitBlock} and adds no state of its own, so its states are the firepit's: {@code lit}, {@code smoke_level} (0 to 4)
 * and {@code axis}, 2 x 5 x 2 = 20 server states. The properties are read through {@link Firepit#inheritedProps}, which
 * refuses a grill that stops extending the firepit or declares its own state.
 *
 * <p>TFC's grill blockstate {@code grill.json} is a multipart: an axis part for the grill model
 * {@code firepit_grill} and a lit part for the flame ({@code firepit_lit_low} or {@code firepit_unlit}), with no smoke
 * level. {@link Firepit#checkMultipart} checks that every state matches a part and that only {@code lit} and {@code axis}
 * are named. PAR-HEAT-24: a carrier state takes one model, so {@link #models} merges the parts of each lit value into one
 * model ({@code tfp:block/grill_lit}, {@code tfp:block/grill_unlit}: the elements of both, the textures of both, the
 * grill's particle). No free vanilla state scores 0.5 or more for TFC's grill outline with a model (the grill's old
 * vanilla-look carrier {@code snow[layers=5]} has no free hide target), so the selector gives every (lit, axis) appearance
 * a hitbox-only carrier, drawn empty, and the look is an {@code entity_renderer} item display of the merged model
 * ({@link #spec}), turned for axis z as the blockstate does. The entry carries TFC's settings, loot (wood ash and
 * {@code tfp:wrought_iron_grill}) and lit light.
 *
 * <p>The outputs are {@code craftengine/tfp/devices/grill.yml}, the literal spec outside the CraftEngine
 * {@code configuration/} folder, {@link #LOADED_OUTPUT} ({@link #loadedYaml}, PAR-HEAT-08), the entry CraftEngine
 * loads: the same spec through the device template {@code tfp:block/device}, which adds the {@code tfp:device} behaviour,
 * so the placed block routes to the grill device kind ({@code paper/grill}), written by {@link #run} since PAR-HEAT-24,
 * and the two merged models with their TFC textures.
 */
final class Grill {
    static final String SOURCE = "src/main/java/net/dries007/tfc/common/blocks/devices/GrillBlock.java";
    static final String CLASS = "GrillBlock";
    static final String BLOCKSTATE = TfcCheckout.ASSETS + "blockstates/grill.json";
    static final String OUTPUT = AssetPipeline.PACK + "devices/grill.yml";
    static final String BLOCK_ID = Json.TO_NS + ":grill";
    /** PAR-HEAT-08: where the loaded entry ({@link #loadedYaml}) goes; {@link #run} writes it since PAR-HEAT-24. */
    static final String LOADED_OUTPUT = AssetPipeline.PACK + "configuration/devices/grill.yml";

    private Grill() {
    }

    /** Writes the grill entry and its models. Returns the number of server states. Fails when TFC's sources do not match. */
    static int run(TfcCheckout tfc, OutputTree out) {
        List<DeviceProperty> props = props(tfc);
        List<Map<String, String>> states = DeviceBlockSpec.combinations(props);
        Firepit.checkMultipart(tfc, BLOCKSTATE, states);
        DeviceBlockSpec spec = spec(tfc, props);
        out.put(OUTPUT, DeviceYaml.writeLiteral(spec));
        out.put(LOADED_OUTPUT, DeviceYaml.writeTemplated(Firepit.loaded(spec))); // PAR-HEAT-24: the entry CraftEngine loads
        writeModels(tfc, out);
        return states.size();
    }

    /**
     * PAR-HEAT-08: the grill spec with the {@code tfp:device} behaviour (template {@code tfp:block/device}), so the block
     * routes to the grill device kind. States, carriers, TFC's settings, loot and lit light are the spec's.
     */
    static String loadedYaml(TfcCheckout tfc) {
        return DeviceYaml.writeTemplated(Firepit.loaded(spec(tfc, props(tfc))));
    }

    /** The grill's block state properties: the firepit's, in {@code builder.add} order. */
    static List<DeviceProperty> props(TfcCheckout tfc) {
        return Firepit.inheritedProps(tfc, SOURCE, CLASS);
    }

    /**
     * PAR-HEAT-24: the grill drawn with its merged models: four appearances ({@code lit_x} ... {@code unlit_z}), each on the
     * hitbox-only carrier the hitbox selector agreed on (drawn empty), with one item display of the merged model of its lit
     * value ({@link #renderItem}) turned per axis ({@link #renderRotation}), and TFC's settings, loot and lit light. Each
     * (lit, axis) value is its own appearance, so a state change (lighting, burn-out) morphs the display.
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
            (lit, axis) -> DeviceAppearance.Carrier.state(carrier(lit, axis).state()), Grill::renderItem,
            HitboxDemands::grillModel, Grill::renderRotation, DeviceBlockSpec.Settings.fromTfc(tfc, "grill"),
            DeviceLoot.fromTfc(tfc, "grill"), Firepit.litLight(tfc, "grill"), hides);
    }

    /** PAR-HEAT-24: the display-only item of the merged model of a lit value ({@code tfp:render/grill_lit}). */
    static String renderItem(boolean lit) {
        String model = HitboxDemands.grillModel(lit);
        return Json.TO_NS + ":render/" + model.substring(model.indexOf(":block/") + ":block/".length());
    }

    /**
     * PAR-HEAT-24: the display turn of an axis: the item display draws an item model a half turn about y against the same
     * model as a block (ForgeDevices), and TFC's grill.json turns axis z by y 90, so {@code 180 - y}.
     */
    static String renderRotation(String axis) {
        return "0," + (180 - HitboxDemands.firepitRotation(axis)) + ",0";
    }

    /** PAR-HEAT-24: the carrier of the grill's (lit, axis) appearance; must be hitbox-only (the look is the display). */
    static HitboxAllocation.Assignment carrier(boolean lit, String axis) {
        String app = HitboxDemands.firepitAppearance(lit, axis);
        HitboxAllocation.Assignment a = HitboxAllocation.agreed(BLOCK_ID, app);
        if (a.mode() != HitboxAllocation.Mode.HITBOX_ONLY) {
            throw new DatagenException(BLOCK_ID + "#" + app + ": the carrier is " + a.mode()
                + ", the grill draws its model as an item display over an empty carrier");
        }
        return a;
    }

    /**
     * PAR-HEAT-24: the TFC models of the axis-x parts of the blockstate that apply to a lit value, in part order
     * ({@code block/firepit_grill}, then {@code block/firepit_lit_low} or {@code block/firepit_unlit}; no namespace). Fails
     * when a part's model is not a TFC model.
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
     * PAR-HEAT-24: the merged model of a lit value: the elements of every part in part order, the textures of every part
     * (the first part's {@code particle} wins, as a multipart takes its particle from the first part), the first part's
     * parent; renamed to {@code tfp:}. Returns null when the checkout has no model of a part (small fixtures).
     */
    static JsonObject merged(TfcCheckout tfc, boolean lit) {
        JsonObject textures = new JsonObject();
        JsonArray elements = new JsonArray();
        String parent = null;
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
            if (json.has("textures")) {
                for (Map.Entry<String, JsonElement> t : json.getAsJsonObject("textures").entrySet()) {
                    JsonElement prev = textures.get(t.getKey());
                    if (prev == null) {
                        textures.add(t.getKey(), t.getValue());
                    } else if (!t.getKey().equals("particle") && !prev.equals(t.getValue())) {
                        throw new DatagenException(src + ": texture #" + t.getKey() + " differs between the grill's parts");
                    }
                }
            }
            if (json.has("elements")) {
                json.getAsJsonArray("elements").forEach(elements::add);
            }
        }
        JsonObject model = new JsonObject();
        if (parent != null) {
            model.addProperty("parent", parent);
        }
        model.add("textures", textures);
        model.add("elements", elements);
        return Json.rename(model).getAsJsonObject();
    }

    /** PAR-HEAT-24: the merged models by output path ({@code grill_lit}, {@code grill_unlit}); empty in small fixtures. */
    static Map<String, JsonObject> models(TfcCheckout tfc) {
        Map<String, JsonObject> out = new LinkedHashMap<>();
        for (boolean lit : new boolean[] {true, false}) {
            JsonObject m = merged(tfc, lit);
            if (m != null) {
                String id = HitboxDemands.grillModel(lit);
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
                    throw new DatagenException("the grill model uses tfc:" + path + ", which the checkout does not have");
                }
                out.put(AssetPipeline.RP + "textures/" + path + ".png", tfc.read(src));
                if (tfc.exists(src + ".mcmeta")) {
                    out.put(AssetPipeline.RP + "textures/" + path + ".png.mcmeta", tfc.read(src + ".mcmeta"));
                }
            }
        }
    }
}
