/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/plant/fruit/FruitBlocks.java @ c3b4d9536 (StationaryBush, the registrations),
 *       src/main/java/net/dries007/tfc/common/blocks/plant/fruit/{StationaryBerryBushBlock,SeasonalPlantBlock,DeadBerryBushBlock}.java
 *       @ c3b4d9536 (LIFECYCLE, STAGE, getShape, getCollisionShape),
 *       src/main/java/net/dries007/tfc/common/blocks/TFCBlocks.java @ c3b4d9536 (DEAD_BERRY_BUSH),
 *       src/main/resources/assets/tfc/blockstates/plant/{snowberry,bunchberry,gooseberry,cloudberry,strawberry,wintergreen_berry}_bush.json
 *       and dead_berry_bush.json, models/block/plant/*bush*.json, models/item/plant/*_bush.json, textures/block/berry_bush/*.png
 *       @ c3b4d9536 (copied), data/tfc/loot_table/blocks/plant/{*_bush,dead_berry_bush}.json @ c3b4d9536 (the drops)
 */
package dev.tfp.datagen.berry;

import com.google.gson.JsonObject;
import dev.tfp.datagen.DatagenException;
import dev.tfp.datagen.Json;
import dev.tfp.datagen.LootTableExport;
import dev.tfp.datagen.OutputTree;
import dev.tfp.datagen.TfcCheckout;
import dev.tfp.datagen.Yaml;
import dev.tfp.datagen.crop.CropBlocks;
import dev.tfp.datagen.device.DeviceAppearance;
import dev.tfp.datagen.device.DeviceBlockSpec;
import dev.tfp.datagen.device.DeviceProperty;
import dev.tfp.datagen.device.DeviceSound;
import dev.tfp.datagen.device.DeviceYaml;
import dev.tfp.datagen.hitbox.HitboxAllocation;
import dev.tfp.datagen.hitbox.HitboxDemands;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * PAR-FARM-14: TFC's six stationary berry bushes and the dead berry bush as CraftEngine blocks
 * ({@code craftengine/tfp/configuration/berry/<species>.yml}, {@code dead.yml}): {@code tfp:berry_bush/<species>} with
 * TFC's {@code lifecycle} (dormant, healthy, flowering, fruiting) and {@code stage} (0 to 2), and
 * {@code tfp:berry_bush/dead} with {@code stage}. The device kind {@code tfp:berry_bush} of PAR-FARM-06 runs them.
 *
 * <p>Every state draws TFC's blockstate model as an item display on the hitbox carrier of its outline (the bushes'
 * {@code HALF_PLANT}, the dead bush's {@code PLANT_SHAPE} or full cube at stage 2; no collision). Settings: strength 0.6,
 * sweet berry bush sounds, the bushes flammable like leaves, the dead one {@code flammable(120, 90)}. The block item of each
 * species places the bush; the drops are TFC's tables (the item renamed to the block item).
 */
public final class BerryBlocks {
    static final String P = "src/main/java/net/dries007/tfc/common/blocks/plant/fruit/";
    static final String FRUIT_BLOCKS = P + "FruitBlocks.java";
    static final String BLOCKSTATES = TfcCheckout.ASSETS + "blockstates/plant/";
    static final String MODELS_ITEM = TfcCheckout.ASSETS + "models/item/plant/";
    static final String LOOT = TfcCheckout.MAIN + "data/" + Json.FROM_NS + "/loot_table/blocks/plant/";
    static final String LANG = TfcCheckout.ASSETS + "lang/";
    static final String PACK = "craftengine/tfp/";
    public static final String OUTPUT_DIR = PACK + "configuration/berry/";
    static final String RP = PACK + "resourcepack/assets/" + Json.TO_NS + "/";
    static final String LANG_NS = "tfp_berry";
    static final String LANG_RP = PACK + "resourcepack/assets/" + LANG_NS + "/lang/";
    public static final String STRENGTH = "0.6";
    /** TFC {@code flammableLikeLeaves()}: flammable(60, 30); the dead bush {@code flammable(120, 90)} (flammability, spread). */
    public static final int[] LIVING_FIRE = {60, 30};
    public static final int[] DEAD_FIRE = {120, 90};
    /** Vanilla {@code SoundType.SWEET_BERRY_BUSH}: break and place their own events, step, hit and fall are grass. */
    static final Map<String, String> SOUNDS = sounds();
    public static final List<String> LIFECYCLES = List.of("dormant", "healthy", "flowering", "fruiting");
    static final int STAGES = 3;

    private BerryBlocks() {
    }

    private static Map<String, String> sounds() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("break", "minecraft:block.sweet_berry_bush.break");
        m.put("step", "minecraft:block.grass.step");
        m.put("place", "minecraft:block.sweet_berry_bush.place");
        m.put("hit", "minecraft:block.grass.hit");
        m.put("fall", "minecraft:block.grass.fall");
        return java.util.Collections.unmodifiableMap(m);
    }

    /** True when the checkout has the bushes (the small test fixtures do not). */
    public static boolean available(TfcCheckout tfc) {
        return tfc.exists(FRUIT_BLOCKS) && tfc.exists(BLOCKSTATES + "snowberry_bush.json");
    }

    /** TFC's {@code StationaryBush} constants, in order, lower case. */
    public static List<String> species(TfcCheckout tfc) {
        String src = tfc.readString(FRUIT_BLOCKS);
        int start = src.indexOf("public enum StationaryBush");
        if (start < 0) {
            throw new DatagenException(FRUIT_BLOCKS + " has no StationaryBush enum any more");
        }
        List<String> out = new ArrayList<>();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("^\\s+([A-Z_]+)\\(Food\\.", java.util.regex.Pattern.MULTILINE)
            .matcher(src.substring(start, src.indexOf("public enum Tree", start)));
        while (m.find()) {
            out.add(m.group(1).toLowerCase(java.util.Locale.ROOT));
        }
        return out;
    }

    /** The model of a bush state: {@code plant/<prefix><species>_bush_<stage>} by TFC's blockstate. */
    static String model(JsonObject blockstate, String source, String key) {
        JsonObject v = blockstate.getAsJsonObject("variants").getAsJsonObject(key);
        if (v == null || v.size() != 1) {
            throw new DatagenException(source + " draws no plain model for " + key);
        }
        return v.get("model").getAsString();
    }

    /** The appearance name of a living state: {@code <lifecycle>_<stage>}. */
    public static String appearance(String lifecycle, int stage) {
        return lifecycle + "_" + stage;
    }

    /** The appearance name of a dead state. */
    public static String deadAppearance(int stage) {
        return "stage_" + stage;
    }

    public static String renderItem(String model) {
        return Json.TO_NS + ":render/" + model.substring(model.indexOf(":block/") + ":block/".length());
    }

    record Built(DeviceBlockSpec spec, Set<String> models) {
    }

    static DeviceAppearance appearance(String id, String name, String pattern, String model, DeviceBlockSpec.Builder b,
            Set<String> renderItems, Map<String, String> hidden) {
        return appearance(id, name, pattern, model, 0, b, renderItems, hidden);
    }

    /** As above, the model turned by TFC's blockstate {@code y}. */
    static DeviceAppearance appearance(String id, String name, String pattern, String model, int y, DeviceBlockSpec.Builder b,
            Set<String> renderItems, Map<String, String> hidden) {
        HitboxAllocation.Assignment a = HitboxAllocation.agreed(id, pattern);
        if (a.mode() != HitboxAllocation.Mode.HITBOX_ONLY) {
            throw new DatagenException(id + "#" + name + " got a " + a.mode() + " carrier; BerryBlocks draws item displays");
        }
        String item = renderItem(model);
        if (renderItems.add(item)) {
            b.renderItem(item, model);
        }
        if (a.hideTarget() != null && !a.pool() && hidden.putIfAbsent(a.state(), a.hideTarget()) == null) {
            b.stateMapping(a.state(), a.hideTarget());
        }
        return DeviceAppearance.rendered(name, DeviceAppearance.Carrier.state(a.state()),
            List.of(DeviceAppearance.Renderer.item(item).rotated(CropBlocks.rotation(y))));
    }

    static Built living(TfcCheckout tfc, String species) {
        String id = HitboxDemands.berryId(species);
        String src = BLOCKSTATES + species + "_bush.json";
        JsonObject blockstate = Json.parse(tfc.readString(src), src).getAsJsonObject();
        DeviceBlockSpec.Builder b = DeviceBlockSpec.builder(id)
            .property(DeviceProperty.names("lifecycle", LIFECYCLES, "dormant"))
            .property(DeviceProperty.range("stage", 0, STAGES - 1, 0));
        Set<String> models = new TreeSet<>();
        Set<String> renderItems = new LinkedHashSet<>();
        Map<String, String> hidden = new TreeMap<>();
        for (String lifecycle : LIFECYCLES) {
            for (int stage = 0; stage < STAGES; stage++) {
                String model = model(blockstate, src, "lifecycle=" + lifecycle + ",stage=" + stage);
                models.add(model);
                b.appearance(appearance(id, appearance(lifecycle, stage), HitboxDemands.BERRY_PATTERN, model, b, renderItems, hidden));
            }
        }
        b.everyState(s -> appearance(s.get("lifecycle"), Integer.parseInt(s.get("stage"))), null)
            .settings(DeviceBlockSpec.Settings.of(STRENGTH, STRENGTH, DeviceSound.STONE, "hoe"))
            .deviceBehaviour()
            .blockItem("item." + Json.TO_NS + ".berry_bush." + species);
        return new Built(b.build(), models);
    }

    static Built dead(TfcCheckout tfc) {
        String id = HitboxDemands.BERRY_DEAD_ID;
        String src = BLOCKSTATES + "dead_berry_bush.json";
        JsonObject blockstate = Json.parse(tfc.readString(src), src).getAsJsonObject();
        DeviceBlockSpec.Builder b = DeviceBlockSpec.builder(id).property(DeviceProperty.range("stage", 0, STAGES - 1, 0));
        Set<String> models = new TreeSet<>();
        Set<String> renderItems = new LinkedHashSet<>();
        Map<String, String> hidden = new TreeMap<>();
        for (int stage = 0; stage < STAGES; stage++) {
            String model = model(blockstate, src, "stage=" + stage);
            models.add(model);
            String name = deadAppearance(stage);
            b.appearance(appearance(id, name, name, model, b, renderItems, hidden));
        }
        b.everyState(s -> deadAppearance(Integer.parseInt(s.get("stage"))), null)
            .settings(DeviceBlockSpec.Settings.of(STRENGTH, STRENGTH, DeviceSound.STONE, "hoe"))
            .deviceBehaviour();
        return new Built(b.build(), models);
    }

    /** The templated entry with TFC's sounds, push reaction, fire odds and the not-occluding look of a hitbox-only block. */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> yaml(DeviceBlockSpec spec, int[] fire) {
        Map<String, Object> root = DeviceYaml.templated(spec);
        Map<String, Object> block = (Map<String, Object>) ((Map<String, Object>) root.get("blocks")).get(spec.id());
        Map<String, Object> args = (Map<String, Object>) block.get("arguments");
        SOUNDS.forEach((k, v) -> args.put("device_sound_" + k, v));
        args.put("device_tool_tag", "minecraft:mineable/hoe");
        Map<String, Object> settings = new LinkedHashMap<>();
        settings.put("push_reaction", "destroy");
        settings.put("can_occlude", false);
        settings.put("is_suffocating", false);
        settings.put("is_view_blocking", false);
        settings.put("burnable", true);
        // NeoForge: getFireSpreadSpeed is the vanilla ignite odds (CE burn_chance), getFlammability the burn odds.
        settings.put("burn_chance", fire[1]);
        settings.put("fire_spread_chance", fire[0]);
        Map<String, Object> ordered = new LinkedHashMap<>();
        block.forEach((k, v) -> {
            ordered.put(k, v);
            if (k.equals("arguments")) {
                ordered.put("settings", settings);
            }
        });
        ((Map<String, Object>) root.get("blocks")).put(spec.id(), ordered);
        return root;
    }

    /** Writes the entries, models, textures, loot and names. Returns the number of server states. */
    public static int run(TfcCheckout tfc, OutputTree out) {
        checkSources(tfc);
        List<String> species = species(tfc);
        if (!species.equals(HitboxDemands.BERRY_SPECIES)) {
            throw new DatagenException(FRUIT_BLOCKS + " lists " + species + " but HitboxDemands.BERRY_SPECIES is "
                + HitboxDemands.BERRY_SPECIES);
        }
        int states = 0;
        Set<String> models = new TreeSet<>();
        for (String s : species) {
            Built built = living(tfc, s);
            states += (int) built.spec().stateCount();
            models.addAll(built.models());
            out.put(OUTPUT_DIR + s + ".yml", Yaml.write(DeviceYaml.HEADER, yaml(built.spec(), LIVING_FIRE)));
            String loot = LOOT + s + "_bush.json";
            String text = tfc.readString(loot).replace("\"" + Json.FROM_NS + ":plant/" + s + "_bush\"",
                "\"" + HitboxDemands.berryId(s) + "\"");
            LootTableExport.delegateBlock(out, "berry_bush/" + s, Json.rename(Json.parse(text, loot)), named -> { });
            String item = MODELS_ITEM + s + "_bush.json";
            JsonObject itemModel = Json.parse(tfc.readString(item), item).getAsJsonObject();
            out.put(RP + "models/item/berry_bush/" + s + ".json", Json.write(Json.rename(itemModel)));
            models.add(itemModel.get("parent").getAsString());
        }
        Built dead = dead(tfc);
        states += (int) dead.spec().stateCount();
        models.addAll(dead.models());
        out.put(OUTPUT_DIR + "dead.yml", Yaml.write(DeviceYaml.HEADER, yaml(dead.spec(), DEAD_FIRE)));
        LootTableExport.delegateBlock(out, "berry_bush/dead", Json.rename(Json.parse(tfc.readString(LOOT + "dead_berry_bush.json"),
            LOOT + "dead_berry_bush.json")), named -> { });
        Set<String> copied = new TreeSet<>();
        for (String m : models) {
            CropBlocks.copyModel(tfc, out, m, copied);
        }
        writeLang(tfc, out, species);
        return states;
    }

    /** TFC's registrations the generator depends on. */
    static void checkSources(TfcCheckout tfc) {
        String fruit = tfc.readString(FRUIT_BLOCKS).replaceAll("\\s+", " ");
        String bush = "new StationaryBerryBushBlock(ExtendedProperties.of(MapColor.PLANT).strength(0.6f).noOcclusion().randomTicks()"
            + ".sound(SoundType.SWEET_BERRY_BUSH).blockEntity(TFCBlockEntities.BERRY_BUSH).flammableLikeLeaves(),";
        if (!fruit.contains(bush)) {
            throw new DatagenException(FRUIT_BLOCKS + " no longer registers the stationary bush with " + bush);
        }
        String blocks = tfc.readString("src/main/java/net/dries007/tfc/common/blocks/TFCBlocks.java").replaceAll("\\s+", " ");
        String dead = "registerNoItem(\"plant/dead_berry_bush\", () -> new DeadBerryBushBlock(ExtendedProperties.of(MapColor.PLANT)"
            + ".strength(0.6f).noOcclusion().sound(SoundType.SWEET_BERRY_BUSH).randomTicks().blockEntity(TFCBlockEntities.TICK_COUNTER)"
            + ".flammable(120, 90)));";
        if (!blocks.contains(dead)) {
            throw new DatagenException("TFCBlocks no longer registers the dead berry bush as " + dead);
        }
    }

    /** The block item names, {@code item.tfp.berry_bush.<species>}, in every TFC locale that has them. */
    private static void writeLang(TfcCheckout tfc, OutputTree out, List<String> species) {
        for (String f : tfc.list(LANG)) {
            if (!f.endsWith(".json")) {
                continue;
            }
            String locale = f.substring(LANG.length(), f.length() - ".json".length());
            JsonObject src = Json.parse(tfc.readString(f), f).getAsJsonObject();
            TreeMap<String, String> keys = new TreeMap<>();
            for (String s : species) {
                String tfcKey = "block." + Json.FROM_NS + ".plant." + s + "_bush";
                if (src.has(tfcKey)) {
                    keys.put("item." + Json.TO_NS + ".berry_bush." + s, src.get(tfcKey).getAsString());
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
