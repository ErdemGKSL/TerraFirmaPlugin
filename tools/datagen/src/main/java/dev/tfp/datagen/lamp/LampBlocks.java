/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/util/Metal.java @ c3b4d9536 (LAMP: ALL part type, strength 4/10, SoundType.LANTERN,
 *       light 15 when lit, stacks to 1),
 *       src/main/java/net/dries007/tfc/common/blocks/devices/LampBlock.java @ c3b4d9536 (LIT, HANGING),
 *       src/main/resources/assets/tfc/blockstates/metal/lamp/*.json, models/block/lamp.json, models/block/lamp_hanging.json,
 *       models/block/metal/lamp/*.json, models/item/metal/lamp/*.json, textures/block/lamp*.png, textures/item/metal/lamp/*.png
 *       @ c3b4d9536 (copied),
 *       src/generated/resources/data/tfc/loot_table/blocks/metal/lamp/*.json @ c3b4d9536 (the lamp drops itself, with its fuel),
 *       src/main/resources/assets/tfc/lang/*.json @ c3b4d9536 (block.tfc.metal.lamp.*)
 */
package dev.tfp.datagen.lamp;

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
import dev.tfp.datagen.device.DeviceProperty;
import dev.tfp.datagen.device.DeviceSound;
import dev.tfp.datagen.device.DeviceYaml;
import dev.tfp.datagen.hitbox.HitboxAllocation;
import dev.tfp.datagen.hitbox.HitboxDemands;
import dev.tfp.datagen.rendered.RenderedSound;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * PAR-METAL-19: the nine metal lamps as the CraftEngine blocks {@code tfp:metal/lamp/<metal>}
 * ({@code configuration/lamp/lamp.yml}).
 *
 * <ul>
 *   <li>States: TFC's {@code hanging} and {@code lit} (four per lamp). The lit state gives light 15 (TFC
 *       {@code lightLevel(state -> LIT ? 15 : 0)}).</li>
 *   <li>Hitbox: {@code LampBlock.SHAPE} and {@code HANGING_SHAPE} on transparent carriers shared by the nine metals
 *       ({@code TfcShapes.lampKey}). Look: TFC's four block models of the metal ({@code <metal>_off}, {@code _on},
 *       {@code _hanging_off}, {@code _hanging_on}) as one item display each.</li>
 *   <li>TFC's settings (strength 4 and 10, {@code SoundType.LANTERN}, pickaxe), loot (the lamp itself; the fuel travels in
 *       the saved device record) and a block item that stacks to one with TFC's flat item model.</li>
 * </ul>
 */
public final class LampBlocks {
    static final String METAL_SRC = "src/main/java/net/dries007/tfc/util/Metal.java";
    static final String BLOCK_SRC = "src/main/java/net/dries007/tfc/common/blocks/devices/LampBlock.java";
    static final String ASSETS = TfcCheckout.ASSETS;
    static final String LANG = ASSETS + "lang/";
    static final String PACK = "craftengine/tfp/";
    public static final String OUTPUT = PACK + "configuration/lamp/lamp.yml";
    static final String RP = PACK + "resourcepack/assets/tfp/";
    static final String LANG_RP = PACK + "resourcepack/assets/tfp_lamp/lang/";
    /** The appearances in order: standing off, standing on, hanging off, hanging on. */
    public static final List<String> APPEARANCES = List.of("off", "on", "hanging_off", "hanging_on");
    private static final List<String> SOUND_EVENTS = List.of("break", "step", "place", "hit", "fall");
    /** Vanilla {@code SoundType.LANTERN}: volume 1.0, pitch 1.0. */
    private static final RenderedSound LANTERN_SOUND = RenderedSound.UNIT;
    private static final Pattern LAMP_TYPE = Pattern.compile("LAMP\\(PartType\\.ALL, metal -> new LampBlock\\(ExtendedProperties"
        + "\\.of\\(\\)\\.mapColor\\(metal\\.mapColor\\(\\)\\)\\.noOcclusion\\(\\)\\.sound\\(SoundType\\.LANTERN\\)\\.strength\\(4, 10\\)"
        + "\\.randomTicks\\(\\)\\.pushReaction\\(PushReaction\\.DESTROY\\)\\.lightLevel\\(state -> state\\.getValue\\(LampBlock\\.LIT\\) "
        + "\\? 15 : 0\\)\\.blockEntity\\(TFCBlockEntities\\.LAMP\\)\\), \\(block, properties\\) -> new LampBlockItem\\(block, "
        + "properties\\.stacksTo\\(1\\)\\)\\)");

    private LampBlocks() {
    }

    public static String id(String metal) {
        return HitboxDemands.lampId(metal);
    }

    public static String langKey(String metal) {
        return "block." + Json.TO_NS + ".metal.lamp." + metal;
    }

    /** The metals of the lamps, in TFC's order. */
    public static List<String> metals() {
        return HitboxDemands.METAL_ALL;
    }

    static String tfcModel(String metal, String appearance) {
        return Json.FROM_NS + ":block/metal/lamp/" + metal + "_" + appearance;
    }

    static String model(String metal, String appearance) {
        return Json.TO_NS + ":block/metal/lamp/" + metal + "_" + appearance;
    }

    static String renderItem(String metal, String appearance) {
        return Json.TO_NS + ":render/metal/lamp/" + metal + "_" + appearance;
    }

    /** True when the checkout has the lamps (the small test fixtures do not). */
    public static boolean available(TfcCheckout tfc) {
        return tfc.exists(BLOCK_SRC) && tfc.exists(ASSETS + "blockstates/metal/lamp/copper.json");
    }

    /** Writes the entries, models, textures and names. Returns the number of server states (9 lamps x 4). */
    public static int run(TfcCheckout tfc, OutputTree out) {
        checkSource(tfc);
        Map<String, Object> blocks = new LinkedHashMap<>();
        Map<String, Object> items = new LinkedHashMap<>();
        Map<String, Object> mappings = new TreeMap<>();
        Set<String> copied = new TreeSet<>();
        int states = 0;
        for (String metal : metals()) {
            checkBlockstate(tfc, metal);
            copyModels(tfc, out, metal, copied);
            DeviceBlockSpec spec = spec(metal);
            states += (int) spec.stateCount();
            merge(yaml(spec), blocks, items, mappings);
        }
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("blocks", blocks);
        root.put("items", items);
        if (!mappings.isEmpty()) {
            root.put("block_state_mappings", mappings);
        }
        out.put(OUTPUT, Yaml.write(DeviceYaml.HEADER, root));
        writeLang(tfc, out);
        return states;
    }

    // ---- TFC's source ---------------------------------------------------------------------------------

    /** The lamp's registration in {@code Metal.java} (settings, light, stack size) and its class. */
    static void checkSource(TfcCheckout tfc) {
        String metal = tfc.readString(METAL_SRC).replaceAll("\\s+", " ");
        if (!LAMP_TYPE.matcher(metal).find()) {
            throw new DatagenException(METAL_SRC + " no longer registers the lamp as LampBlocks expects (strength 4/10, lantern sound, "
                + "light 15 when lit, stacks to 1): re-check the lamp entry");
        }
        String src = tfc.readString(BLOCK_SRC).replaceAll("\\s+", " ");
        for (String needle : List.of("public class LampBlock extends ExtendedBlock implements EntityBlockExtension",
                "BooleanProperty LIT = BlockStateProperties.LIT;", "BooleanProperty HANGING = BlockStateProperties.HANGING;",
                "registerDefaultState(getStateDefinition().any().setValue(HANGING, false).setValue(LIT, false));")) {
            if (!src.contains(needle)) {
                throw new DatagenException(BLOCK_SRC + " no longer contains \"" + needle + "\": re-check the lamp entry");
            }
        }
    }

    /** TFC's blockstate of a lamp: the four variants name the four models, with no turn. */
    static void checkBlockstate(TfcCheckout tfc, String metal) {
        String src = ASSETS + "blockstates/metal/lamp/" + metal + ".json";
        JsonObject variants = Json.parse(tfc.readString(src), src).getAsJsonObject().getAsJsonObject("variants");
        Map<String, String> want = Map.of("hanging=false,lit=false", "off", "hanging=false,lit=true", "on",
            "hanging=true,lit=false", "hanging_off", "hanging=true,lit=true", "hanging_on");
        if (variants == null || variants.size() != want.size()) {
            throw new DatagenException(src + " does not have exactly the four variants hanging x lit");
        }
        for (Map.Entry<String, String> e : want.entrySet()) {
            if (!variants.has(e.getKey()) || !variants.getAsJsonObject(e.getKey()).get("model").getAsString()
                    .equals(tfcModel(metal, e.getValue())) || variants.getAsJsonObject(e.getKey()).has("y")) {
                throw new DatagenException(src + " variant " + e.getKey() + " is not the plain model " + tfcModel(metal, e.getValue()));
            }
        }
    }

    // ---- the spec ------------------------------------------------------------------------------------------

    static DeviceBlockSpec spec(String metal) {
        String id = id(metal);
        DeviceBlockSpec.Builder b = DeviceBlockSpec.builder(id)
            .property(DeviceProperty.bool("hanging", false))
            .property(DeviceProperty.bool("lit", false));
        Map<String, String> hidden = new TreeMap<>();
        for (boolean hanging : new boolean[] {false, true}) {
            for (boolean lit : new boolean[] {false, true}) {
                String name = HitboxDemands.lampAppearance(hanging, lit);
                HitboxAllocation.Assignment a = HitboxAllocation.agreed(id, name);
                if (a.mode() != HitboxAllocation.Mode.HITBOX_ONLY) {
                    throw new DatagenException(id + "#" + name + " got a " + a.mode() + " carrier; LampBlocks draws item displays");
                }
                if (a.hideTarget() != null && !a.pool() && hidden.putIfAbsent(a.state(), a.hideTarget()) == null) {
                    b.stateMapping(a.state(), a.hideTarget());
                }
                String item = renderItem(metal, name);
                b.renderItem(item, model(metal, name));
                b.appearance(DeviceAppearance.rendered(name, DeviceAppearance.Carrier.state(a.state()),
                    List.of(DeviceAppearance.Renderer.item(item))));
            }
        }
        return b.everyState(s -> HitboxDemands.lampAppearance("true".equals(s.get("hanging")), "true".equals(s.get("lit"))),
                s -> "true".equals(s.get("lit")) ? 15 : 0)
            .settings(new DeviceBlockSpec.Settings(new BigDecimal("4.0"), new BigDecimal("10.0"), DeviceSound.METAL,
                "minecraft:mineable/pickaxe"))
            .loot(DeviceLoot.self())
            .deviceBehaviour()
            .blockItem(langKey(metal))
            .build();
    }

    // ---- the yaml -------------------------------------------------------------------------------------

    /** The templated entry with {@code SoundType.LANTERN}'s events, and an item that stacks to one. */
    @SuppressWarnings("unchecked")
    static Map<String, Object> yaml(DeviceBlockSpec spec) {
        Map<String, Object> root = DeviceYaml.templated(spec);
        Map<String, Object> block = (Map<String, Object>) ((Map<String, Object>) root.get("blocks")).get(spec.id());
        Map<String, Object> args = (Map<String, Object>) block.get("arguments");
        for (String event : SOUND_EVENTS) {
            String key = "device_sound_" + event;
            if (!args.containsKey(key)) {
                throw new DatagenException("the device template has no argument " + key + ": re-check LampBlocks' sounds");
            }
            Map<String, Object> sound = new LinkedHashMap<>();
            sound.put("id", "minecraft:block.lantern." + event);
            sound.put("volume", LANTERN_SOUND.volume(event));
            sound.put("pitch", LANTERN_SOUND.pitch(event));
            args.put(key, sound);
        }
        Map<String, Object> item = (Map<String, Object>) ((Map<String, Object>) root.get("items")).get(spec.id());
        item.put("data", Map.of("max_stack_size", 1)); // TFC properties.stacksTo(1)
        return root;
    }

    @SuppressWarnings("unchecked")
    private static void merge(Map<String, Object> one, Map<String, Object> blocks, Map<String, Object> items,
            Map<String, Object> mappings) {
        blocks.putAll((Map<String, Object>) one.get("blocks"));
        items.putAll((Map<String, Object>) one.getOrDefault("items", Map.of()));
        for (Map.Entry<String, Object> e : ((Map<String, Object>) one.getOrDefault("block_state_mappings", Map.of())).entrySet()) {
            Object old = mappings.putIfAbsent(e.getKey(), e.getValue());
            if (old != null && !old.equals(e.getValue())) {
                throw new DatagenException("lamp carrier " + e.getKey() + " hidden as " + old + " and " + e.getValue());
            }
        }
    }

    // ---- models, textures, names ----------------------------------------------------------------------

    static void copyModels(TfcCheckout tfc, OutputTree out, String metal, Set<String> copied) {
        for (String appearance : APPEARANCES) {
            CropBlocks.copyModel(tfc, out, tfcModel(metal, appearance), copied);
        }
        // the flat item model: TFC's own (item/generated over textures/item/metal/lamp/<metal>)
        String itemSrc = ASSETS + "models/item/metal/lamp/" + metal + ".json";
        JsonObject item = Json.parse(tfc.readString(itemSrc), itemSrc).getAsJsonObject();
        String tex = item.getAsJsonObject("textures").get("layer0").getAsString();
        String png = ASSETS + "textures/" + tex.substring(Json.FROM_NS.length() + 1) + ".png";
        if (!tfc.exists(png)) {
            throw new DatagenException(itemSrc + " uses missing texture " + tex);
        }
        out.put(RP + "textures/" + tex.substring(Json.FROM_NS.length() + 1) + ".png", tfc.read(png));
        out.put(RP + "models/item/metal/lamp/" + metal + ".json", Json.write(Json.rename(item)));
    }

    /** {@code block.tfp.metal.lamp.<metal>} in every TFC locale that names them (English must). */
    static void writeLang(TfcCheckout tfc, OutputTree out) {
        for (String f : tfc.list(LANG)) {
            if (!f.endsWith(".json")) {
                continue;
            }
            String locale = f.substring(LANG.length(), f.length() - ".json".length());
            JsonObject src = Json.parse(tfc.readString(f), f).getAsJsonObject();
            Map<String, String> keys = new TreeMap<>();
            for (String metal : metals()) {
                String tfcKey = "block." + Json.FROM_NS + ".metal.lamp." + metal;
                if (src.has(tfcKey)) {
                    keys.put(langKey(metal), src.get(tfcKey).getAsString());
                } else if (locale.equals("en_us")) {
                    throw new DatagenException("TFC has no English name " + tfcKey);
                }
            }
            if (!keys.isEmpty()) {
                JsonObject o = new JsonObject();
                keys.forEach(o::addProperty);
                out.put(LANG_RP + locale + ".json", Json.write(o));
            }
        }
    }
}
