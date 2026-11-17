/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/FireboxBlock.java @ c3b4d9536 (LIT, default false),
 *       src/main/java/net/dries007/tfc/common/blocks/TFCBlocks.java @ c3b4d9536 (FIREBOX: strength(6f), SoundType.COPPER,
 *       lightLevel(litBlockEmission(15))),
 *       src/main/resources/assets/tfc/blockstates/firebox.json, models/block/firebox_{off,on}.json, models/item/firebox.json
 *       and textures/block/devices/firebox/*.png(.mcmeta) @ c3b4d9536 (copied),
 *       src/main/resources/data/tfc/loot_table/blocks/firebox.json @ c3b4d9536 (the drop),
 *       src/generated/resources/data/minecraft/tags/block/mineable/pickaxe.json @ c3b4d9536 (the tool),
 *       src/main/resources/assets/tfc/lang/*.json @ c3b4d9536 (block.tfc.firebox)
 */
package dev.tfp.datagen.firebox;

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
import dev.tfp.datagen.device.DeviceSound;
import dev.tfp.datagen.device.DeviceYaml;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PAR-HEAT-12: the firebox as the CraftEngine block {@code tfp:firebox}
 * ({@code craftengine/tfp/configuration/firebox/firebox.yml}). The plugin's {@code FireboxDevice} runs it.
 *
 * <ul>
 *   <li>The state property {@code lit} (TFC {@code FireboxBlock.LIT}, default false); 2 states, light 15 when lit (TFC
 *       {@code litBlockEmission(15)}).</li>
 *   <li>Two appearances, as TFC's blockstate draws them: {@code unlit} ({@code firebox_off}) and {@code lit}
 *       ({@code firebox_on}, the animated side texture). TFC's firebox is a full cube column, so both take a state of
 *       CraftEngine's {@code solid} auto_state pool with our copy of TFC's model.</li>
 *   <li>The {@code tfp:device} behaviour, TFC's settings (strength 6, the pickaxe tag; metal sounds for TFC's copper
 *       ones), the loot (the firebox itself under survives_explosion) and the block item with TFC's item model.</li>
 * </ul>
 *
 * <p>Not run by {@code Datagen} yet: a full cube on a CE pool must be declared in {@code HitboxDemands.FULL_CUBE_POOLS}
 * (GeneratedCarrierTest), which is outside PAR-HEAT-12's files. PAR-HEAT-31 declares it and adds the run line.
 */
public final class FireboxBlock {
    public static final String ID = Json.TO_NS + ":firebox";
    static final String BLOCK = "firebox";
    static final String SOURCE = "src/main/java/net/dries007/tfc/common/blocks/FireboxBlock.java";
    static final String TFC_BLOCKS = "src/main/java/net/dries007/tfc/common/blocks/TFCBlocks.java";
    static final String BLOCKSTATE = TfcCheckout.ASSETS + "blockstates/firebox.json";
    static final String MODELS = TfcCheckout.ASSETS + "models/";
    static final String TEXTURES = TfcCheckout.ASSETS + "textures/";
    static final String PICKAXE = TfcCheckout.GENERATED + "data/minecraft/tags/block/mineable/pickaxe.json";
    static final String LANG = TfcCheckout.ASSETS + "lang/";
    static final String PACK = "craftengine/tfp/";
    public static final String OUTPUT = PACK + "configuration/firebox/firebox.yml";
    static final String RP = PACK + "resourcepack/assets/tfp/";
    static final String LANG_RP = PACK + "resourcepack/assets/tfp_firebox/lang/";
    /** TFC {@code FireboxBlock.LIT} (the name FireboxDevice writes). */
    public static final String LIT = "lit";
    public static final String LIT_LOOK = "lit";
    public static final String UNLIT_LOOK = "unlit";
    public static final String LANG_KEY = "block." + Json.TO_NS + "." + BLOCK;
    /** CraftEngine's full-cube auto_state pool. */
    static final String POOL = "solid";
    /** TFC {@code litBlockEmission(15)}. */
    static final int LIT_LIGHT = 15;
    /**
     * TFC's {@code SoundType.COPPER} has no mapping in the shared device sound table yet ({@code DeviceSound},
     * {@code RenderedSound}); vanilla's metal set stands in until follow-up PAR-HEAT-31 adds it (ledger divergence).
     */
    static final DeviceSound SOUND = DeviceSound.METAL;
    private static final Pattern TEXTURE = Pattern.compile(Json.FROM_NS + ":(block/[a-z0-9_/]+)");
    private static final Pattern REGISTRATION = Pattern.compile(
        "register\\(\"firebox\", \\(\\) -> new FireboxBlock\\(ExtendedProperties\\.of\\(\\)\\.mapColor\\(MapColor\\.COLOR_BROWN\\)"
            + "\\.sound\\(SoundType\\.COPPER\\)\\.strength\\(([0-9.]+)f\\)\\.lightLevel\\(litBlockEmission\\(15\\)\\)");

    private FireboxBlock() {
    }

    /** True when the checkout has the firebox (the small test fixtures do not). */
    public static boolean available(TfcCheckout tfc) {
        return tfc.exists(SOURCE) && tfc.exists(MODELS + "block/firebox_on.json");
    }

    /** Writes the entry, its models, textures and names. Returns the number of server states (2). */
    public static int run(TfcCheckout tfc, OutputTree out) {
        checkSource(tfc);
        checkBlockstate(tfc);
        copyModels(tfc, out);
        DeviceBlockSpec spec = spec(tfc);
        out.put(OUTPUT, Yaml.write(DeviceYaml.HEADER, DeviceYaml.templated(spec)));
        writeLang(tfc, out);
        return (int) spec.stateCount();
    }

    /** TFC's state: LIT, default false. */
    static void checkSource(TfcCheckout tfc) {
        String box = tfc.readString(SOURCE).replaceAll("\\s+", " ");
        for (String needle : List.of("public static final BooleanProperty LIT = BlockStateProperties.LIT;",
                "registerDefaultState(defaultBlockState().setValue(LIT, false));",
                "super.createBlockStateDefinition(builder.add(LIT));")) {
            if (!box.contains(needle)) {
                throw new DatagenException(SOURCE + " no longer contains \"" + needle + "\": re-check the firebox entry");
            }
        }
    }

    /** TFC's blockstate: {@code lit=true} draws firebox_on, {@code lit=false} firebox_off, unrotated. */
    static void checkBlockstate(TfcCheckout tfc) {
        JsonObject variants = Json.parse(tfc.readString(BLOCKSTATE), BLOCKSTATE).getAsJsonObject().getAsJsonObject("variants");
        if (variants == null || variants.size() != 2) {
            throw new DatagenException(BLOCKSTATE + ": not one variant per lit");
        }
        for (boolean lit : new boolean[] {false, true}) {
            JsonObject v = variants.getAsJsonObject("lit=" + lit);
            String want = Json.FROM_NS + ":block/" + tfcModel(lit);
            if (v == null || v.size() != 1 || !want.equals(v.get("model").getAsString())) {
                throw new DatagenException(BLOCKSTATE + ": lit=" + lit + " no longer draws " + want + " unrotated");
            }
        }
    }

    /** TFC's model name of a lit value. */
    static String tfcModel(boolean lit) {
        return BLOCK + (lit ? "_on" : "_off");
    }

    /** Our model id of an appearance. */
    public static String model(String look) {
        return Json.TO_NS + ":block/" + tfcModel(LIT_LOOK.equals(look));
    }

    /** TFC's settings: strength 6 (both), copper sounds, the pickaxe tag. */
    static DeviceBlockSpec.Settings settings(TfcCheckout tfc) {
        String blocks = tfc.readString(TFC_BLOCKS).replaceAll("\\s+", " ");
        Matcher m = REGISTRATION.matcher(blocks);
        if (!m.find()) {
            throw new DatagenException(TFC_BLOCKS + ": the firebox registration changed (map colour, sound, strength or light)");
        }
        JsonElement tag = Json.parse(tfc.readString(PICKAXE), PICKAXE);
        boolean pickaxe = false;
        for (JsonElement v : tag.getAsJsonObject().getAsJsonArray("values")) {
            pickaxe |= v.isJsonPrimitive() && v.getAsString().equals(Json.FROM_NS + ":" + BLOCK);
        }
        if (!pickaxe) {
            throw new DatagenException(PICKAXE + " no longer holds tfc:firebox");
        }
        return DeviceBlockSpec.Settings.of(m.group(1), m.group(1), SOUND, "pickaxe");
    }

    static DeviceBlockSpec spec(TfcCheckout tfc) {
        return DeviceBlockSpec.builder(ID)
            .property(DeviceProperty.bool(LIT, false))
            .appearance(DeviceAppearance.modelled(UNLIT_LOOK, DeviceAppearance.Carrier.pool(POOL), model(UNLIT_LOOK)))
            .appearance(DeviceAppearance.modelled(LIT_LOOK, DeviceAppearance.Carrier.pool(POOL), model(LIT_LOOK)))
            .everyState(s -> "true".equals(s.get(LIT)) ? LIT_LOOK : UNLIT_LOOK, s -> "true".equals(s.get(LIT)) ? LIT_LIGHT : 0)
            .settings(settings(tfc))
            .loot(DeviceLoot.fromTfc(tfc, BLOCK))
            .deviceBehaviour()
            .blockItem(LANG_KEY)
            .build();
    }

    /** TFC's two block models and item model (renamed), and their textures. */
    static void copyModels(TfcCheckout tfc, OutputTree out) {
        TreeSet<String> textures = new TreeSet<>();
        for (boolean lit : new boolean[] {false, true}) {
            String path = "block/" + tfcModel(lit);
            String src = MODELS + path + ".json";
            JsonObject m = Json.parse(tfc.readString(src), src).getAsJsonObject();
            JsonObject t = m.getAsJsonObject("textures");
            if (t == null || !"block/cube_column".equals(m.get("parent").getAsString())) {
                throw new DatagenException(src + " is no longer a textured cube column");
            }
            for (Map.Entry<String, JsonElement> e : t.entrySet()) {
                Matcher x = TEXTURE.matcher(e.getValue().getAsString());
                if (!x.matches()) {
                    throw new DatagenException(src + ": texture " + e.getValue() + " is not a tfc:block texture");
                }
                textures.add(x.group(1));
            }
            m.remove("__comment__");
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
        if (!(Json.FROM_NS + ":block/" + tfcModel(false)).equals(item.get("parent").getAsString())) {
            throw new DatagenException(src + " is no longer the unlit block model as parent");
        }
        item.remove("__comment__");
        out.put(RP + "models/item/" + BLOCK + ".json", Json.write(Json.rename(item)));
    }

    /** {@code block.tfp.firebox} in every TFC locale that names the firebox (English must). */
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
