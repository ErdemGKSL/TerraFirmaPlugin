/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/LobsterTrapBlock.java @ c3b4d9536 (FACING, TRAP, FLUID, TrapState),
 *       src/main/java/net/dries007/tfc/common/blocks/TFCBlocks.java @ c3b4d9536 (LOBSTER_TRAP: strength(3f), SoundType.METAL),
 *       src/main/resources/assets/tfc/blockstates/lobster_trap.json, models/block/lobster_trap{,_bait,_closed}.json,
 *       models/item/lobster_trap.json and textures/block/lobster_trap.png @ c3b4d9536 (copied),
 *       src/main/resources/data/tfc/loot_table/blocks/lobster_trap.json @ c3b4d9536 (the drop),
 *       src/generated/resources/data/minecraft/tags/block/mineable/pickaxe.json @ c3b4d9536 (the tool),
 *       src/main/resources/assets/tfc/lang/*.json @ c3b4d9536 (block.tfc.lobster_trap)
 */
package dev.tfp.datagen.lobstertrap;

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
import dev.tfp.datagen.device.DeviceProperty;
import dev.tfp.datagen.device.DeviceYaml;
import dev.tfp.datagen.hitbox.HitboxAllocation;
import dev.tfp.datagen.hitbox.HitboxDemands;
import dev.tfp.datagen.hitbox.VanillaCandidates;
import dev.tfp.datagen.rotation.RotationBlocks;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PAR-FAUNA-36: the lobster trap as the CraftEngine block {@code tfp:lobster_trap}
 * ({@code craftengine/tfp/configuration/lobstertrap/lobster_trap.yml}). The plugin's {@code LobsterTrapDevice}
 * (PAR-FAUNA-19) runs it and reads and writes the {@code trap} property.
 *
 * <ul>
 *   <li>Properties as TFC's {@code LobsterTrapBlock}: {@code facing} (4), {@code trap} ({@code open}, {@code baited},
 *       {@code closed}) and {@code fluid} ({@code empty}, {@code water}: TFC's {@code FluidProperty WATER}, which the plugin
 *       carries as a property because a CraftEngine block cannot be fluid-logged, as the pipe's {@code fluid}). 24 states.</li>
 *   <li>One appearance per (facing, trap) on the carrier the hitbox selector picks for TFC's full-block outline
 *       ({@link HitboxDemands#LOBSTER_TRAP_ROW}); every one is hitbox-only (drawn empty) on one shared carrier and TFC's
 *       open, bait or closed model is an item display turned as TFC's blockstate turns it.</li>
 *   <li>PAR-FAUNA-41: the {@code fluid=water} states take their own appearances ({@code north_open_wet}) on the waterlogged
 *       carrier the selector picks for {@link HitboxDemands#LOBSTER_TRAP_WET_ROW} (CraftEngine's
 *       {@code waterlogged_tintable_leaves} pool, one shared pool state drawn with an element-less model), so the client
 *       draws water around TFC's model, with the same item displays.</li>
 *   <li>The {@code tfp:device} behaviour, TFC's settings (strength 3, metal sounds, the pickaxe tag), the loot (the trap
 *       itself under survives_explosion) and the block item with TFC's item model.</li>
 * </ul>
 */
public final class LobsterTrapBlock {
    public static final String ID = HitboxDemands.LOBSTER_TRAP_ID;
    static final String BLOCK = "lobster_trap";
    static final String SOURCE = "src/main/java/net/dries007/tfc/common/blocks/LobsterTrapBlock.java";
    static final String BLOCKSTATE = TfcCheckout.ASSETS + "blockstates/lobster_trap.json";
    static final String MODELS = TfcCheckout.ASSETS + "models/";
    static final String TEXTURES = TfcCheckout.ASSETS + "textures/";
    static final String LOOT = TfcCheckout.MAIN + "data/" + Json.FROM_NS + "/loot_table/blocks/lobster_trap.json";
    static final String LANG = TfcCheckout.ASSETS + "lang/";
    static final String PACK = "craftengine/tfp/";
    public static final String OUTPUT = PACK + "configuration/lobstertrap/lobster_trap.yml";
    static final String RP = PACK + "resourcepack/assets/tfp/";
    static final String LANG_RP = PACK + "resourcepack/assets/tfp_lobstertrap/lang/";
    public static final String LANG_KEY = "block." + Json.TO_NS + "." + BLOCK;
    /** TFC's model of each trap state ({@code TrapState} to {@code models/block/<name>.json}). */
    public static final Map<String, String> MODEL_OF = Map.of("open", "lobster_trap", "baited", "lobster_trap_bait",
        "closed", "lobster_trap_closed");
    static final String FLUID = "fluid";
    /** PAR-FAUNA-41: the share key of the wet appearances' pool state (all twelve on one state). */
    static final String WET_SHARE = BLOCK;
    static final String WET_MODEL_FILE = "models/block/" + BLOCK + "_water.json";
    /** TFC's particle texture of the trap model, also the wet carrier's (PAR-FAUNA-41). */
    static final String TFC_PARTICLE = "block/lobster_trap";
    private static final Pattern TEXTURE = Pattern.compile(Json.FROM_NS + ":(block/[a-z0-9_/]+)");
    private static final Pattern VARIANT = Pattern.compile("facing=(\\w+),trap=(\\w+)");

    private LobsterTrapBlock() {
    }

    /** True when the checkout has the lobster trap (the small test fixtures do not). */
    public static boolean available(TfcCheckout tfc) {
        return tfc.exists(SOURCE) && tfc.exists(MODELS + "block/lobster_trap.json") && tfc.exists(BLOCKSTATE);
    }

    /** Writes the entry, its models, texture and names. Returns the number of server states (24). */
    public static int run(TfcCheckout tfc, OutputTree out) {
        Map<String, Integer> turns = checkBlockstate(tfc);
        checkLoot(tfc);
        copyModels(tfc, out);
        DeviceBlockSpec spec = spec(tfc, turns);
        out.put(OUTPUT, Yaml.write(DeviceYaml.HEADER, DeviceYaml.templated(spec)));
        writeLang(tfc, out);
        return (int) spec.stateCount();
    }

    /** The item display of a trap state's model ({@code tfp:render/lobster_trap/open}). */
    public static String renderItem(String trap) {
        return Json.TO_NS + ":render/" + BLOCK + "/" + trap;
    }

    /** The model id of a trap state ({@code tfp:block/lobster_trap_bait}). */
    public static String model(String trap) {
        return Json.TO_NS + ":block/" + MODEL_OF.get(trap);
    }

    /**
     * TFC's blockstate: one variant per (facing, trap), the model of the trap state turned about y by the facing. Returns
     * the y turn per facing (the same for every trap state).
     */
    static Map<String, Integer> checkBlockstate(TfcCheckout tfc) {
        JsonObject variants = Json.parse(tfc.readString(BLOCKSTATE), BLOCKSTATE).getAsJsonObject().getAsJsonObject("variants");
        Map<String, Integer> turns = new TreeMap<>();
        if (variants == null || variants.size() != HitboxDemands.FACINGS.size() * MODEL_OF.size()) {
            throw new DatagenException(BLOCKSTATE + ": not one variant per facing and trap");
        }
        for (Map.Entry<String, JsonElement> e : variants.entrySet()) {
            Matcher m = VARIANT.matcher(e.getKey());
            if (!m.matches() || !HitboxDemands.FACINGS.contains(m.group(1)) || !MODEL_OF.containsKey(m.group(2))) {
                throw new DatagenException(BLOCKSTATE + ": unexpected variant " + e.getKey());
            }
            JsonObject v = e.getValue().getAsJsonObject();
            String want = Json.FROM_NS + ":block/" + MODEL_OF.get(m.group(2));
            int y = v.has("y") ? v.get("y").getAsInt() : 0;
            if (!want.equals(v.get("model").getAsString()) || v.has("x") || v.has("uvlock")
                || v.size() != (v.has("y") ? 2 : 1)) {
                throw new DatagenException(BLOCKSTATE + ": " + e.getKey() + " is no longer " + want + " turned about y");
            }
            Integer old = turns.put(m.group(1), y);
            if (old != null && old != y) {
                throw new DatagenException(BLOCKSTATE + ": facing " + m.group(1) + " turns differently per trap state");
            }
        }
        return turns;
    }

    /** TFC's table: one pool, one roll under survives_explosion, one item entry dropping the trap. */
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
            throw new DatagenException(LOOT + ": the entry is not the trap itself");
        }
    }

    /** The carrier of one (facing, trap) appearance; hitbox-only, all twelve on the one shared carrier. */
    static HitboxAllocation.Assignment carrier(String facing, String trap) {
        String app = HitboxDemands.lobsterTrapAppearance(facing, trap);
        HitboxAllocation.Assignment a = HitboxAllocation.agreed(ID, app);
        if (a.mode() != HitboxAllocation.Mode.HITBOX_ONLY) {
            throw new DatagenException(ID + "#" + app + ": the carrier is " + a.mode()
                + ", the trap draws TFC's models as an item display over an empty carrier");
        }
        String shared = HitboxAllocation.agreed(ID, HitboxDemands.lobsterTrapAppearance(HitboxDemands.FACINGS.getFirst(),
            HitboxDemands.LOBSTER_TRAP_STATES.getFirst())).state();
        if (!a.state().equals(shared)) {
            throw new DatagenException(ID + "#" + app + ": carrier " + a.state() + " is not the shared carrier " + shared);
        }
        return a;
    }

    /** PAR-FAUNA-41: the carrier of one wet (facing, trap) appearance: a fluid pool, all twelve agreeing on it. */
    static HitboxAllocation.Assignment wetCarrier(String facing, String trap) {
        String app = HitboxDemands.lobsterTrapWetAppearance(facing, trap);
        HitboxAllocation.Assignment a = HitboxAllocation.agreed(ID, app);
        if (a.mode() != HitboxAllocation.Mode.POOL) {
            throw new DatagenException(ID + "#" + app + ": the carrier is " + a.mode() + " on " + a.state()
                + ", the wet trap needs a waterlogged auto_state pool (the client draws the water)");
        }
        String shared = HitboxAllocation.agreed(ID, HitboxDemands.lobsterTrapWetAppearance(HitboxDemands.FACINGS.getFirst(),
            HitboxDemands.LOBSTER_TRAP_STATES.getFirst())).state();
        if (!a.state().equals(shared)) {
            throw new DatagenException(ID + "#" + app + ": carrier " + a.state() + " is not the shared wet carrier " + shared);
        }
        return a;
    }

    static DeviceBlockSpec spec(TfcCheckout tfc, Map<String, Integer> turns) {
        DeviceBlockSpec.Builder b = DeviceBlockSpec.builder(ID)
            .property(DeviceProperty.names("facing", HitboxDemands.FACINGS, "north"))
            .property(DeviceProperty.names("trap", HitboxDemands.LOBSTER_TRAP_STATES, "open"))
            .property(DeviceProperty.names(FLUID, List.of("empty", "water"), "empty"));
        for (String t : HitboxDemands.LOBSTER_TRAP_STATES) {
            b.renderItem(renderItem(t), model(t));
        }
        Map<String, String> hides = new TreeMap<>();
        for (String f : HitboxDemands.FACINGS) {
            for (String t : HitboxDemands.LOBSTER_TRAP_STATES) {
                HitboxAllocation.Assignment a = carrier(f, t);
                b.appearance(DeviceAppearance.rendered(HitboxDemands.lobsterTrapAppearance(f, t),
                    DeviceAppearance.Carrier.state(a.state()),
                    List.of(DeviceAppearance.Renderer.item(renderItem(t)).rotated(RotationBlocks.rotation(0, turns.get(f))))));
                if (a.hideTarget() != null) {
                    hides.put(a.state(), a.hideTarget());
                }
                HitboxAllocation.Assignment w = wetCarrier(f, t);
                b.appearance(DeviceAppearance.modelled(HitboxDemands.lobsterTrapWetAppearance(f, t),
                    DeviceAppearance.Carrier.pool(VanillaCandidates.poolName(w.state()), WET_SHARE),
                    HitboxDemands.LOBSTER_TRAP_WET_MODEL)
                    .withOverlay(List.of(DeviceAppearance.Renderer.item(renderItem(t)).rotated(RotationBlocks.rotation(0, turns.get(f))))));
            }
        }
        b.everyState(s -> "water".equals(s.get(FLUID)) ? HitboxDemands.lobsterTrapWetAppearance(s.get("facing"), s.get("trap"))
                : HitboxDemands.lobsterTrapAppearance(s.get("facing"), s.get("trap")), null)
            .settings(DeviceBlockSpec.Settings.fromTfc(tfc, BLOCK))
            .loot(DeviceLoot.self())
            .deviceBehaviour()
            .blockItem(LANG_KEY);
        hides.forEach(b::stateMapping);
        return b.build();
    }

    /** TFC's block models (namespace renamed), the textures they name, and the item model. */
    static void copyModels(TfcCheckout tfc, OutputTree out) {
        TreeSet<String> textures = new TreeSet<>();
        for (String name : new TreeSet<>(MODEL_OF.values())) {
            String src = MODELS + "block/" + name + ".json";
            String text = tfc.readString(src);
            if (name.equals(BLOCK)) {
                JsonObject t = Json.parse(text, src).getAsJsonObject().getAsJsonObject("textures");
                if (t == null || !t.has("particle") || !(Json.FROM_NS + ":" + TFC_PARTICLE).equals(t.get("particle").getAsString())) {
                    throw new DatagenException(src + ": the particle texture is no longer " + TFC_PARTICLE);
                }
            }
            Matcher m = TEXTURE.matcher(text);
            while (m.find()) {
                textures.add(m.group(1));
            }
            JsonObject model = Json.parse(text, src).getAsJsonObject();
            model.remove("__comment__");
            out.put(RP + "models/block/" + name + ".json", Json.write(Json.rename(model)));
        }
        if (textures.isEmpty()) {
            throw new DatagenException(MODELS + "block/lobster_trap*.json name no block textures");
        }
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
        // PAR-FAUNA-41: the wet carrier's model: no elements (the look is the item display), TFC's particle texture
        String particle = Json.TO_NS + ":" + TFC_PARTICLE;
        JsonObject wet = new JsonObject();
        JsonObject wetTextures = new JsonObject();
        wetTextures.addProperty("particle", particle);
        wet.add("textures", wetTextures);
        out.put(RP + WET_MODEL_FILE, Json.write(wet));
        String isrc = MODELS + "item/" + BLOCK + ".json";
        JsonObject item = Json.parse(tfc.readString(isrc), isrc).getAsJsonObject();
        if (!(Json.FROM_NS + ":block/" + BLOCK).equals(item.get("parent").getAsString())) {
            throw new DatagenException(isrc + " is no longer the block model as parent");
        }
        item.remove("__comment__");
        out.put(RP + "models/item/" + BLOCK + ".json", Json.write(Json.rename(item)));
    }

    /** {@code block.tfp.lobster_trap} in every TFC locale that names the trap (English must). */
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
            Map<String, String> keys = new LinkedHashMap<>();
            keys.put(LANG_KEY, src.get(tfcKey).getAsString());
            JsonObject o = new JsonObject();
            keys.forEach(o::addProperty);
            out.put(LANG_RP + locale + ".json", Json.write(o));
        }
    }
}
