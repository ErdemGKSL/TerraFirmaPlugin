/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/devices/QuernBlock.java @ c3b4d9536 (HAS_HANDSTONE, default false),
 *       src/main/java/net/dries007/tfc/common/blocks/TFCBlocks.java @ c3b4d9536 (QUERN: strength(0.5F, 2.0F), SoundType.BASALT),
 *       src/main/resources/assets/tfc/blockstates/quern.json, models/block/quern.json, models/item/quern.json and
 *       textures/block/devices/quern/quern_*.png @ c3b4d9536 (copied),
 *       src/main/resources/data/tfc/loot_table/blocks/quern.json @ c3b4d9536 (the drop),
 *       src/generated/resources/data/minecraft/tags/block/mineable/pickaxe.json @ c3b4d9536 (the tool),
 *       src/main/resources/assets/tfc/lang/*.json @ c3b4d9536 (block.tfc.quern)
 */
package dev.tfp.datagen.quern;

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
import dev.tfp.datagen.hitbox.HitboxAllocation;
import dev.tfp.datagen.hitbox.HitboxDemands;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PAR-PROC-04: the quern as the CraftEngine block {@code tfp:quern} ({@code craftengine/tfp/configuration/quern/quern.yml}).
 *
 * <ul>
 *   <li>One state property, {@code has_handstone} (TFC's, default false). Each value is drawn on the hitbox carrier the
 *       selector picks for TFC's outline of that state (PAR-INFRA-13: {@code tfc:quern[has_handstone=false|true]}), both
 *       with TFC's base model {@link HitboxDemands#QUERN_MODEL}. The handstone, the input and the output are item display
 *       entities of the plugin (TFC's block entity renderer), so they are not in the block model.</li>
 *   <li>The {@code tfp:device} behaviour (the plugin's {@code QuernDevice}), TFC's settings (strength 0.5 / 2.0, pickaxe) and
 *       loot (itself), and the block item {@code tfp:quern} with TFC's item model.</li>
 *   <li>Sounds: TFC's {@code SoundType.BASALT} is a (1.0, 1.0) vanilla type the shared device sound table does not list, so
 *       the entry is written through the device template with stone and the five basalt events are put in its place (plain
 *       ids are right for a (1.0, 1.0) type).</li>
 * </ul>
 * The base model, the item model and the three quern textures are copied from TFC (the handstone's are the material item's,
 * PAR-PROC-19), and the block's name goes to {@code assets/tfp_quern/lang/<locale>.json}.
 */
public final class QuernBlock {
    public static final String ID = Json.TO_NS + ":quern";
    static final String BLOCK = "quern";
    static final String SOURCE = "src/main/java/net/dries007/tfc/common/blocks/devices/QuernBlock.java";
    static final String BLOCKS = "src/main/java/net/dries007/tfc/common/blocks/TFCBlocks.java";
    static final String BLOCKSTATE = TfcCheckout.ASSETS + "blockstates/quern.json";
    static final String BLOCK_MODEL = TfcCheckout.ASSETS + "models/block/quern.json";
    static final String ITEM_MODEL = TfcCheckout.ASSETS + "models/item/quern.json";
    static final String MINEABLE = TfcCheckout.GENERATED + "data/minecraft/tags/block/mineable/";
    static final String LANG = TfcCheckout.ASSETS + "lang/";
    static final String PACK = "craftengine/tfp/";
    public static final String OUTPUT = PACK + "configuration/quern/quern.yml";
    static final String RP = PACK + "resourcepack/assets/tfp/";
    static final String LANG_RP = PACK + "resourcepack/assets/tfp_quern/lang/";
    public static final String PROPERTY = "has_handstone";
    public static final String LANG_KEY = "block." + Json.TO_NS + "." + BLOCK;
    private static final Pattern REGISTER = Pattern.compile(
        "register\\(\"quern\", \\(\\) -> new QuernBlock\\(ExtendedProperties\\.of\\(\\)\\.mapColor\\(MapColor\\.STONE\\)"
            + "\\.strength\\(([0-9.]+)F, ([0-9.]+)F\\)\\.sound\\(SoundType\\.(\\w+)\\)");
    private static final Pattern TEXTURE = Pattern.compile("\"(" + Json.FROM_NS + ":block/devices/quern/[a-z_]+)\"");

    private QuernBlock() {
    }

    /** True when the checkout has the quern (the small test fixtures do not). */
    public static boolean available(TfcCheckout tfc) {
        return tfc.exists(SOURCE) && tfc.exists(BLOCK_MODEL);
    }

    /** Writes the entry, its models, textures and names. Returns the number of server states (2). */
    public static int run(TfcCheckout tfc, OutputTree out) {
        checkSource(tfc);
        String[] strength = strength(tfc);
        copyModels(tfc, out);
        DeviceBlockSpec spec = spec(tfc, strength);
        out.put(OUTPUT, Yaml.write(DeviceYaml.HEADER, yaml(spec)));
        writeLang(tfc, out);
        return (int) spec.stateCount();
    }

    /** TFC's state: {@code HAS_HANDSTONE}, default false, added once; the blockstate draws one model for every state. */
    static void checkSource(TfcCheckout tfc) {
        String src = tfc.readString(SOURCE);
        for (String needle : List.of("public static final BooleanProperty HAS_HANDSTONE = TFCBlockStateProperties.HAS_HANDSTONE;",
                "registerDefaultState(getStateDefinition().any().setValue(HAS_HANDSTONE, false));",
                "super.createBlockStateDefinition(builder.add(HAS_HANDSTONE));",
                "super(properties, InventoryRemoveBehavior.DROP);")) {
            if (!src.contains(needle)) {
                throw new DatagenException(SOURCE + " no longer contains \"" + needle + "\": re-check the quern entry");
            }
        }
        JsonObject variants = Json.parse(tfc.readString(BLOCKSTATE), BLOCKSTATE).getAsJsonObject().getAsJsonObject("variants");
        if (variants == null || variants.size() != 1 || !variants.has("")
            || !(Json.FROM_NS + ":block/quern").equals(variants.getAsJsonObject("").get("model").getAsString())) {
            throw new DatagenException(BLOCKSTATE + " is no longer one variant drawing tfc:block/quern");
        }
    }

    /** {hardness, resistance} of TFC's registration; its sound must still be basalt. */
    static String[] strength(TfcCheckout tfc) {
        Matcher m = REGISTER.matcher(tfc.readString(BLOCKS));
        if (!m.find()) {
            throw new DatagenException(BLOCKS + ": the quern registration no longer matches " + REGISTER.pattern());
        }
        if (!m.group(3).equals("BASALT")) {
            throw new DatagenException(BLOCKS + ": the quern sound is SoundType." + m.group(3) + ", QuernBlock writes basalt");
        }
        return new String[] {m.group(1), m.group(2)};
    }

    /** TFC's mineable tag of the quern (exactly one). */
    static String tool(TfcCheckout tfc) {
        String found = null;
        for (String t : List.of("axe", "hoe", "pickaxe", "shovel")) {
            String src = MINEABLE + t + ".json";
            for (JsonElement v : Json.parse(tfc.readString(src), src).getAsJsonObject().getAsJsonArray("values")) {
                if (v.isJsonPrimitive() && v.getAsString().equals(Json.FROM_NS + ":" + BLOCK)) {
                    if (found != null) {
                        throw new DatagenException("tfc:quern is in two mineable tags: " + found + ", " + t);
                    }
                    found = t;
                }
            }
        }
        if (found == null) {
            throw new DatagenException("tfc:quern is in no minecraft:mineable tag");
        }
        return found;
    }

    /** The carrier of one appearance; both must be explicit states with our model. */
    static HitboxAllocation.Assignment carrier(String appearance) {
        HitboxAllocation.Assignment a = HitboxAllocation.agreed(ID, appearance);
        if (a.mode() != HitboxAllocation.Mode.MODEL) {
            throw new DatagenException("the quern carrier of " + appearance + " is " + a.mode() + ", QuernBlock writes a model");
        }
        return a;
    }

    static DeviceBlockSpec spec(TfcCheckout tfc, String[] strength) {
        HitboxAllocation.Assignment base = carrier(HitboxDemands.QUERN_BASE);
        HitboxAllocation.Assignment handstone = carrier(HitboxDemands.QUERN_HANDSTONE);
        DeviceBlockSpec.Builder b = DeviceBlockSpec.builder(ID)
            .property(DeviceProperty.bool(PROPERTY, false))
            .appearance(DeviceAppearance.modelled(HitboxDemands.QUERN_BASE, DeviceAppearance.Carrier.state(base.state()),
                HitboxDemands.QUERN_MODEL))
            .appearance(DeviceAppearance.modelled(HitboxDemands.QUERN_HANDSTONE,
                DeviceAppearance.Carrier.state(handstone.state()), HitboxDemands.QUERN_MODEL))
            .everyState(s -> Boolean.parseBoolean(s.get(PROPERTY)) ? HitboxDemands.QUERN_HANDSTONE : HitboxDemands.QUERN_BASE,
                null)
            .settings(DeviceBlockSpec.Settings.of(strength[0], strength[1], DeviceSound.STONE, tool(tfc)))
            .loot(DeviceLoot.fromTfc(tfc, BLOCK))
            .deviceBehaviour()
            .blockItem(LANG_KEY);
        for (HitboxAllocation.Assignment a : List.of(base, handstone)) {
            if (a.hideTarget() != null) {
                b.stateMapping(a.state(), a.hideTarget());
            }
        }
        return b.build();
    }

    /** The templated entry with TFC's basalt events in place of the template call's stone ones. */
    @SuppressWarnings("unchecked")
    static Map<String, Object> yaml(DeviceBlockSpec spec) {
        Map<String, Object> root = DeviceYaml.templated(spec);
        Map<String, Object> block = (Map<String, Object>) ((Map<String, Object>) root.get("blocks")).get(spec.id());
        Map<String, Object> args = (Map<String, Object>) block.get("arguments");
        for (String event : List.of("break", "step", "place", "hit", "fall")) {
            String key = "device_sound_" + event;
            if (!args.containsKey(key)) {
                throw new DatagenException("the device template has no argument " + key + ": re-check QuernBlock's sounds");
            }
            args.put(key, "minecraft:block.basalt." + event);
        }
        return root;
    }

    /** TFC's base and item models (namespace renamed) and the textures the base model names. */
    static void copyModels(TfcCheckout tfc, OutputTree out) {
        String block = tfc.readString(BLOCK_MODEL);
        Matcher m = TEXTURE.matcher(block);
        java.util.Set<String> textures = new java.util.TreeSet<>();
        while (m.find()) {
            textures.add(m.group(1));
        }
        if (textures.isEmpty()) {
            throw new DatagenException(BLOCK_MODEL + " names no quern texture");
        }
        out.put(RP + "models/block/quern.json", Json.write(Json.rename(Json.parse(block, BLOCK_MODEL))));
        JsonObject item = Json.parse(tfc.readString(ITEM_MODEL), ITEM_MODEL).getAsJsonObject();
        if (!item.has("parent") || !(Json.FROM_NS + ":block/quern").equals(item.get("parent").getAsString())) {
            throw new DatagenException(ITEM_MODEL + " is no longer the block model as parent");
        }
        out.put(RP + "models/item/quern.json", Json.write(Json.rename(item)));
        for (String t : textures) {
            String path = t.substring(t.indexOf(':') + 1);
            out.put(RP + "textures/" + path + ".png", tfc.read(TfcCheckout.ASSETS + "textures/" + path + ".png"));
        }
    }

    /** {@code block.tfp.quern} in every TFC locale that names the quern (English must). */
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
