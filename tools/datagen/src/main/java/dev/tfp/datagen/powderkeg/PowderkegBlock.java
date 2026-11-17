/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/devices/PowderkegBlock.java @ c3b4d9536 (LIT, defaults false),
 *       src/main/java/net/dries007/tfc/common/blocks/devices/SealableDeviceBlock.java @ c3b4d9536 (SEALED),
 *       src/main/java/net/dries007/tfc/common/blocks/TFCBlocks.java @ c3b4d9536 (POWDERKEG: strength(2.5f), SoundType.WOOD),
 *       src/main/resources/assets/tfc/blockstates/powderkeg.json, models/block/powderkeg{,_sealed,_lit}.json,
 *       models/item/powderkeg.json and textures/block/devices/powderkeg/*.png(.mcmeta) @ c3b4d9536 (copied),
 *       src/main/resources/data/tfc/loot_table/blocks/powderkeg.json @ c3b4d9536 (the drop),
 *       src/generated/resources/data/minecraft/tags/block/mineable/axe.json @ c3b4d9536 (the tool),
 *       src/main/resources/assets/tfc/lang/*.json @ c3b4d9536 (block.tfc.powderkeg)
 */
package dev.tfp.datagen.powderkeg;

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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PAR-PROC-39: the powderkeg as the CraftEngine block {@code tfp:powderkeg}
 * ({@code craftengine/tfp/configuration/powderkeg/powderkeg.yml}). The plugin's {@code PowderkegDevice} (PAR-PROC-18) runs it.
 *
 * <ul>
 *   <li>The state properties {@code sealed} (TFC {@code SealableDeviceBlock.SEALED}) and {@code lit} (TFC
 *       {@code PowderkegBlock.LIT}), both default false; the device writes both. TFC's {@code powered} is no state (the
 *       device reads the redstone signal itself). 4 states.</li>
 *   <li>Three appearances, as TFC's blockstate draws them: {@code unsealed} (TFC's {@code powderkeg} model, also for a lit
 *       unsealed keg), {@code sealed} ({@code powderkeg_sealed}) and {@code lit} ({@code powderkeg_lit}, sealed and lit,
 *       with the animated {@code powderkeg_outside_lit} texture). Each takes the carrier the hitbox selector picks for TFC's
 *       sealed outline ({@code HitboxDemands.POWDERKEG_ROW}; the hollow unsealed row's composter carrier culled the floor
 *       under the keg). A carrier the selector serves with our model draws it; a hitbox-only carrier would be drawn empty
 *       with the model as an {@code entity_renderer} item display ({@code tfp:render/powderkeg/<appearance>}).</li>
 *   <li>The {@code tfp:device} behaviour, TFC's settings (strength 2.5, wood sounds, the axe tag), the loot (the keg
 *       itself under survives_explosion: both of TFC's alternatives drop it; the sealed branch's contents copy is a ledger
 *       divergence of PAR-PROC-18) and the block item with TFC's item model (without its {@code tfc:sealed} override).</li>
 * </ul>
 */
public final class PowderkegBlock {
    public static final String ID = HitboxDemands.POWDERKEG_ID;
    static final String BLOCK = "powderkeg";
    static final String SOURCE = "src/main/java/net/dries007/tfc/common/blocks/devices/PowderkegBlock.java";
    static final String SEALABLE = "src/main/java/net/dries007/tfc/common/blocks/devices/SealableDeviceBlock.java";
    static final String BLOCKSTATE = TfcCheckout.ASSETS + "blockstates/powderkeg.json";
    static final String MODELS = TfcCheckout.ASSETS + "models/";
    static final String TEXTURES = TfcCheckout.ASSETS + "textures/";
    static final String LOOT = TfcCheckout.MAIN + "data/" + Json.FROM_NS + "/loot_table/blocks/powderkeg.json";
    static final String LANG = TfcCheckout.ASSETS + "lang/";
    static final String PACK = "craftengine/tfp/";
    public static final String OUTPUT = PACK + "configuration/powderkeg/powderkeg.yml";
    static final String RP = PACK + "resourcepack/assets/tfp/";
    static final String LANG_RP = PACK + "resourcepack/assets/tfp_powderkeg/lang/";
    /** TFC {@code SealableDeviceBlock.SEALED} and {@code PowderkegBlock.LIT} (the names PowderkegDevice writes). */
    public static final String SEALED = "sealed";
    public static final String LIT = "lit";
    public static final String LANG_KEY = "block." + Json.TO_NS + "." + BLOCK;
    /** The appearances in TFC's model order. */
    public static final List<String> APPEARANCES = List.of(HitboxDemands.POWDERKEG_UNSEALED, HitboxDemands.POWDERKEG_SEALED,
        HitboxDemands.POWDERKEG_LIT);
    /** The half turn every block-model item display needs to match the block (see ForgeDevices.OVERLAY_ROTATION). */
    static final String RENDER_ROTATION = "0,180,0";
    private static final Pattern TEXTURE = Pattern.compile(Json.FROM_NS + ":(block/[a-z0-9_/]+)");

    private PowderkegBlock() {
    }

    /** True when the checkout has the powderkeg (the small test fixtures do not). */
    public static boolean available(TfcCheckout tfc) {
        return tfc.exists(SOURCE) && tfc.exists(MODELS + "block/powderkeg.json");
    }

    /** The display-only item that draws the model of {@code appearance} on a hitbox-only carrier. */
    public static String renderItem(String appearance) {
        return Json.TO_NS + ":render/powderkeg/" + appearance;
    }

    /** Writes the entry, its models, textures and names. Returns the number of server states (4). */
    public static int run(TfcCheckout tfc, OutputTree out) {
        checkSource(tfc);
        checkBlockstate(tfc);
        checkLoot(tfc);
        copyModels(tfc, out);
        DeviceBlockSpec spec = spec(tfc);
        out.put(OUTPUT, Yaml.write(DeviceYaml.HEADER, DeviceYaml.templated(spec)));
        writeLang(tfc, out);
        return (int) spec.stateCount();
    }

    /** TFC's states: SEALED (SealableDeviceBlock) and LIT, both default false. */
    static void checkSource(TfcCheckout tfc) {
        String sealable = tfc.readString(SEALABLE);
        if (!sealable.contains("public static final BooleanProperty SEALED = TFCBlockStateProperties.SEALED;")) {
            throw new DatagenException(SEALABLE + " no longer declares SEALED: re-check the powderkeg entry");
        }
        String keg = tfc.readString(SOURCE).replaceAll("\\s+", " ");
        for (String needle : List.of("public static final BooleanProperty LIT = BlockStateProperties.LIT;",
                "registerDefaultState(getStateDefinition().any().setValue(SEALED, false).setValue(LIT, false));",
                "super.createBlockStateDefinition(builder.add(LIT));")) {
            if (!keg.contains(needle)) {
                throw new DatagenException(SOURCE + " no longer contains \"" + needle + "\": re-check the powderkeg entry");
            }
        }
    }

    /** TFC's blockstate: one variant per lit and sealed, drawing the model {@link #appearance} names, unrotated. */
    static void checkBlockstate(TfcCheckout tfc) {
        JsonObject variants = Json.parse(tfc.readString(BLOCKSTATE), BLOCKSTATE).getAsJsonObject().getAsJsonObject("variants");
        if (variants == null || variants.size() != 4) {
            throw new DatagenException(BLOCKSTATE + ": not one variant per lit and sealed");
        }
        for (boolean lit : new boolean[] {false, true}) {
            for (boolean sealed : new boolean[] {false, true}) {
                String key = "lit=" + lit + ",sealed=" + sealed;
                JsonObject v = variants.getAsJsonObject(key);
                String want = Json.FROM_NS + ":block/" + BLOCK + suffix(appearance(Map.of(SEALED, "" + sealed, LIT, "" + lit)));
                if (v == null || v.size() != 1 || !want.equals(v.get("model").getAsString())) {
                    throw new DatagenException(BLOCKSTATE + ": " + key + " no longer draws " + want + " unrotated");
                }
            }
        }
    }

    /**
     * TFC's table: one pool under survives_explosion, an alternatives entry whose two children both drop the keg (the
     * sealed one also copies the contents: PAR-PROC-18 divergence, the device drops them instead).
     */
    static void checkLoot(TfcCheckout tfc) {
        String self = Json.FROM_NS + ":" + BLOCK;
        JsonObject root = Json.parse(tfc.readString(LOOT), LOOT).getAsJsonObject();
        JsonArray pools = root.getAsJsonArray("pools");
        if (pools == null || pools.size() != 1) {
            throw new DatagenException(LOOT + ": not one pool");
        }
        JsonObject pool = pools.get(0).getAsJsonObject();
        JsonArray conditions = pool.getAsJsonArray("conditions");
        if (pool.get("rolls").getAsDouble() != 1 || conditions == null || conditions.size() != 1
            || !"minecraft:survives_explosion".equals(conditions.get(0).getAsJsonObject().get("condition").getAsString())) {
            throw new DatagenException(LOOT + ": the pool is not one roll under survives_explosion");
        }
        JsonArray entries = pool.getAsJsonArray("entries");
        if (entries.size() != 1 || !"minecraft:alternatives".equals(entries.get(0).getAsJsonObject().get("type").getAsString())) {
            throw new DatagenException(LOOT + ": the entry is not one alternatives entry");
        }
        JsonArray children = entries.get(0).getAsJsonObject().getAsJsonArray("children");
        if (children.size() != 2) {
            throw new DatagenException(LOOT + ": the alternatives no longer have two children");
        }
        for (JsonElement ch : children) {
            JsonObject o = ch.getAsJsonObject();
            if (!"minecraft:item".equals(o.get("type").getAsString()) || !self.equals(o.get("name").getAsString())) {
                throw new DatagenException(LOOT + ": a child does not drop " + self);
            }
        }
    }

    /** The appearance of one server state, as TFC's blockstate picks the model. */
    static String appearance(Map<String, String> state) {
        boolean sealed = Boolean.parseBoolean(state.get(SEALED));
        boolean lit = Boolean.parseBoolean(state.get(LIT));
        if (!sealed) {
            return HitboxDemands.POWDERKEG_UNSEALED;
        }
        return lit ? HitboxDemands.POWDERKEG_LIT : HitboxDemands.POWDERKEG_SEALED;
    }

    /** TFC's model suffix of an appearance ({@code ""}, {@code _sealed}, {@code _lit}). */
    static String suffix(String appearance) {
        return appearance.equals(HitboxDemands.POWDERKEG_UNSEALED) ? "" : "_" + appearance;
    }

    static DeviceBlockSpec spec(TfcCheckout tfc) {
        DeviceBlockSpec.Builder b = DeviceBlockSpec.builder(ID)
            .property(DeviceProperty.bool(SEALED, false))
            .property(DeviceProperty.bool(LIT, false));
        Map<String, String> hidden = new LinkedHashMap<>();
        for (String name : APPEARANCES) {
            HitboxAllocation.Assignment a = HitboxAllocation.agreed(ID, name);
            String model = HitboxDemands.powderkegModel(name);
            DeviceAppearance.Carrier carrier = DeviceAppearance.Carrier.state(a.state());
            switch (a.mode()) {
                case MODEL -> b.appearance(DeviceAppearance.modelled(name, carrier, model));
                case HITBOX_ONLY -> {
                    b.appearance(DeviceAppearance.rendered(name, carrier,
                        List.of(DeviceAppearance.Renderer.item(renderItem(name)).rotated(RENDER_ROTATION))));
                    b.renderItem(renderItem(name), model);
                }
                default -> throw new DatagenException("the powderkeg carrier of " + name + " is " + a.mode()
                    + ", PowderkegBlock writes a model or a transparent carrier");
            }
            if (a.hideTarget() != null && hidden.putIfAbsent(a.state(), a.hideTarget()) == null) {
                b.stateMapping(a.state(), a.hideTarget());
            }
        }
        return b.everyState(PowderkegBlock::appearance, null)
            .settings(DeviceBlockSpec.Settings.fromTfc(tfc, BLOCK))
            .loot(DeviceLoot.self())
            .deviceBehaviour()
            .blockItem(LANG_KEY)
            .build();
    }

    /** TFC's three block models and item model (renamed; the item without its sealed override), and their textures. */
    static void copyModels(TfcCheckout tfc, OutputTree out) {
        TreeSet<String> textures = new TreeSet<>();
        for (String name : APPEARANCES) {
            String path = "block/" + BLOCK + suffix(name);
            String src = MODELS + path + ".json";
            String text = tfc.readString(src);
            JsonObject m = Json.parse(text, src).getAsJsonObject();
            JsonObject t = m.getAsJsonObject("textures");
            if (t == null) {
                throw new DatagenException(src + " names no textures");
            }
            for (Map.Entry<String, JsonElement> e : t.entrySet()) {
                Matcher x = TEXTURE.matcher(e.getValue().getAsString());
                if (!x.matches()) {
                    throw new DatagenException(src + ": texture " + e.getValue() + " is not a tfc:block texture");
                }
                textures.add(x.group(1));
            }
            out.put(RP + "models/" + path + ".json", Json.write(Json.rename(m)));
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
        String src = MODELS + "item/" + BLOCK + ".json";
        JsonObject item = Json.parse(tfc.readString(src), src).getAsJsonObject();
        if (!(Json.FROM_NS + ":block/" + BLOCK).equals(item.get("parent").getAsString())) {
            throw new DatagenException(src + " is no longer the unsealed block model as parent");
        }
        item.remove("overrides");
        item.remove("__comment__");
        out.put(RP + "models/item/" + BLOCK + ".json", Json.write(Json.rename(item)));
    }

    /** {@code block.tfp.powderkeg} in every TFC locale that names the powderkeg (English must). */
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
