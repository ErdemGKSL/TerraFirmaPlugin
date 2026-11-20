/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/crop/Crop.java @ c3b4d9536 (the 29 crops, their stages and block families),
 *       src/main/java/net/dries007/tfc/common/blocks/crop/{CropBlock,DoubleCropBlock,ClimbingCropBlock,SpreadingCropBlock}.java
 *       @ c3b4d9536 (properties, getShape),
 *       src/main/java/net/dries007/tfc/common/blocks/crop/{DeadCropBlock,DeadDoubleCropBlock,DeadClimbingCropBlock}.java
 *       @ c3b4d9536 (MATURE, PART, STICK; getShape),
 *       src/main/java/net/dries007/tfc/common/blocks/crop/{WildCropBlock,WildDoubleCropBlock,WildSpreadingCropBlock}.java
 *       @ c3b4d9536 (MATURE, PART, sides; getShape),
 *       src/main/java/net/dries007/tfc/common/items/SeedItem.java and TFCItems.java @ c3b4d9536 (the seeds),
 *       src/main/resources/assets/tfc/blockstates/{crop,dead_crop,wild_crop}/*.json and models/block/{crop,dead_crop,wild_crop}/*.json
 *       @ c3b4d9536 (copied), models/item/seeds/*.json and textures/item/seeds/*.png @ c3b4d9536 (copied),
 *       src/main/resources/data/tfc/loot_table/blocks/{crop,dead_crop,wild_crop}/*.json @ c3b4d9536 (the drops)
 */
package dev.tfp.datagen.crop;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.tfp.datagen.DatagenException;
import dev.tfp.datagen.Json;
import dev.tfp.datagen.LootTableExport;
import dev.tfp.datagen.OutputTree;
import dev.tfp.datagen.TfcCheckout;
import dev.tfp.datagen.Yaml;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PAR-FARM-03: TFC's 29 crops as CraftEngine blocks ({@code craftengine/tfp/configuration/crop/<crop>.yml}), each with its
 * living block {@code tfp:crop/<crop>}, its dead block {@code tfp:dead_crop/<crop>} and its wild block
 * {@code tfp:wild_crop/<crop>}, and the seed item {@code tfp:seeds/<crop>} that places the living block.
 *
 * <ul>
 *   <li>Properties as TFC's block classes register them: {@code age} (0 to the crop's stage count minus one), {@code part}
 *       (bottom, top) for the double crops, {@code stick} for the climbing ones (green bean, tomato), the four sides of the
 *       spreading crops (pumpkin, melon); the dead blocks {@code mature}, {@code part}, {@code stick}; the wild blocks
 *       {@code mature}, {@code part}, the sides. The flooded rice has no fluid property (ledger).</li>
 *   <li>Every state draws TFC's blockstate model(s) as item displays ({@code entity_renderer}) on the hitbox carrier of its
 *       TFC outline (QUARTER, HALF or FULL, no collision): the selector serves {@code <outline>_*} per block
 *       ({@link HitboxDemands}); the three outlines share one carrier each across all 87 blocks. A multipart state (the
 *       spreading crops' sides) is one display per applying part, turned by the part's {@code y}.</li>
 *   <li>Settings {@code strength(0.4)}, crop sounds, hoe tag, push reaction destroy, flammable(60, 30); the drops are TFC's
 *       tables, evaluated by the loot runtime.</li>
 * </ul>
 */
public final class CropBlocks {
    static final String B = "src/main/java/net/dries007/tfc/common/blocks/crop/";
    static final String CROP_SOURCE = B + "Crop.java";
    static final String BLOCKSTATES = TfcCheckout.ASSETS + "blockstates/";
    static final String MODELS = TfcCheckout.ASSETS + "models/";
    static final String TEXTURES = TfcCheckout.ASSETS + "textures/";
    static final String LOOT = TfcCheckout.MAIN + "data/" + Json.FROM_NS + "/loot_table/blocks/";
    static final String LANG = TfcCheckout.ASSETS + "lang/";
    static final String PACK = "craftengine/tfp/";
    public static final String OUTPUT_DIR = PACK + "configuration/crop/";
    static final String RP = PACK + "resourcepack/assets/" + Json.TO_NS + "/";
    static final String LANG_NS = "tfp_crop";
    static final String LANG_RP = PACK + "resourcepack/assets/" + LANG_NS + "/lang/";
    /** The half turn every block-model item display needs to match the block (ForgeDevices.OVERLAY_ROTATION). */
    static final String RENDER_ROTATION = "0,180,0";
    /** TFC {@code Crop.dead()}: {@code strength(0.4F)}, vanilla {@code SoundType.CROP}, {@code flammable(60, 30)}. */
    static final String STRENGTH = "0.4";
    static final int FLAMMABILITY = 60;
    static final int FIRE_SPREAD_SPEED = 30;
    static final List<String> SIDES = List.of("east", "north", "south", "west");
    static final Map<String, String> CROP_SOUNDS = cropSounds();
    private static final Pattern ENTRY = Pattern.compile("^\\s+([A-Z_]+)\\((.*)$");

    private CropBlocks() {
    }

    private static Map<String, String> cropSounds() {
        // Vanilla SoundType.CROP: break, step (grass), place (crop plant), hit (crop break), fall (grass).
        Map<String, String> m = new LinkedHashMap<>();
        m.put("break", "minecraft:block.crop.break");
        m.put("step", "minecraft:block.grass.step");
        m.put("place", "minecraft:item.crop.plant");
        m.put("hit", "minecraft:block.crop.break");
        m.put("fall", "minecraft:block.grass.fall");
        return java.util.Collections.unmodifiableMap(m);
    }

    /** The block shape family of a crop ({@code Crop.java}'s constructors). */
    public enum Shape {
        /** One block: {@code age}. */
        SINGLE,
        /** One block plus the four sides of a fruit block ({@code SpreadingCropBlock}). */
        SPREADING,
        /** Two blocks high: {@code age} and {@code part}. */
        DOUBLE
    }

    /**
     * One crop of {@code Crop.java}.
     *
     * @param name   the lower-case name
     * @param shape  the block family
     * @param bottom the stage count of the single block (the bottom block of a double crop)
     * @param top    the stages of the top block of a double crop (0 otherwise)
     * @param stick  a climbing crop (a stick on the farmland adds a part above)
     */
    public record CropDef(String name, Shape shape, int bottom, int top, boolean stick) {
        /** The highest {@code age}. */
        public int maxAge() {
            return bottom + top - 1;
        }
    }

    /** True when the checkout has the crops (the small test fixtures do not). */
    public static boolean available(TfcCheckout tfc) {
        return tfc.exists(CROP_SOURCE) && tfc.exists(BLOCKSTATES + "crop/wheat.json");
    }

    /** TFC's crops, in {@code Crop.java} order, from the enum's constructor arguments. */
    public static List<CropDef> crops(TfcCheckout tfc) {
        String src = tfc.readString(CROP_SOURCE);
        List<CropDef> out = new ArrayList<>();
        boolean inEnum = false;
        for (String line : src.split("\n")) {
            if (line.startsWith("public enum Crop")) {
                inEnum = true;
                continue;
            }
            if (!inEnum) {
                continue;
            }
            Matcher m = ENTRY.matcher(line);
            if (!m.matches()) {
                continue;
            }
            String body = m.group(2);
            int comment = body.indexOf("//");
            if (comment >= 0) {
                body = body.substring(0, comment);
            }
            body = body.trim();
            if (!body.endsWith(",") && !body.endsWith(";")) {
                continue;
            }
            body = body.substring(0, body.length() - 1).trim();
            if (!body.endsWith(")")) {
                throw new DatagenException(CROP_SOURCE + ": cannot read the crop line " + line);
            }
            body = body.substring(0, body.length() - 1);
            String[] args = body.split(",");
            for (int i = 0; i < args.length; i++) {
                args[i] = args[i].trim();
            }
            if (args.length < 4) {
                throw new DatagenException(CROP_SOURCE + ": crop " + m.group(1) + " has too few arguments");
            }
            String name = m.group(1).toLowerCase(java.util.Locale.ROOT);
            int n = Integer.parseInt(args[3]);
            if (args.length == 4 || args[4].equals("true")) {
                out.add(new CropDef(name, Shape.SINGLE, n, 0, false)); // default, or flooded (rice)
            } else if (args[4].startsWith("(")) {
                // pickable (two supplier lambdas) or spreading (one): the pickable crops are single blocks
                out.add(new CropDef(name, args.length == 5 ? Shape.SPREADING : Shape.SINGLE, n, 0, false));
            } else {
                out.add(new CropDef(name, Shape.DOUBLE, n, Integer.parseInt(args[4]), args.length > 5 && args[5].equals("true")));
            }
        }
        if (out.isEmpty()) {
            throw new DatagenException(CROP_SOURCE + " lists no crops any more");
        }
        return out;
    }

    /** The properties of a block family: {@code kind} is {@code crop}, {@code dead_crop} or {@code wild_crop}. */
    static List<DeviceProperty> properties(String kind, CropDef c) {
        List<DeviceProperty> p = new ArrayList<>();
        if (kind.equals("crop")) {
            p.add(DeviceProperty.range("age", 0, c.maxAge(), 0));
        } else {
            p.add(DeviceProperty.bool("mature", false));
        }
        if (c.shape() == Shape.DOUBLE) {
            p.add(DeviceProperty.names("part", List.of("bottom", "top"), "bottom"));
        }
        if (c.shape() == Shape.DOUBLE && c.stick() && !kind.equals("wild_crop")) {
            p.add(DeviceProperty.bool("stick", false));
        }
        if (c.shape() == Shape.SPREADING && !kind.equals("dead_crop")) {
            for (String side : SIDES) {
                p.add(DeviceProperty.bool(side, false));
            }
        }
        return p;
    }

    /** TFC's outline of a state: {@code quarter}, {@code half} or {@code full} (see {@code getShape} of the block classes). */
    static String outline(String kind, CropDef c, Map<String, String> state) {
        boolean top = "top".equals(state.get("part"));
        switch (kind) {
            case "crop" -> {
                if (c.shape() == Shape.DOUBLE && top) {
                    return "half";
                }
                int age = Integer.parseInt(state.get("age"));
                float growth = (float) age / (c.bottom() - 1); // CropBlock.getMaxAge, DoubleCropBlock.maxSingleAge
                return growth <= 0.25F ? "quarter" : growth <= 0.5F ? "half" : "full";
            }
            case "dead_crop" -> {
                if (c.shape() == Shape.DOUBLE) {
                    return top ? "half" : "full";
                }
                return state.get("mature").equals("true") ? "full" : "quarter";
            }
            default -> {
                return c.shape() == Shape.DOUBLE && top ? "half" : "full";
            }
        }
    }

    /** One model of a state's look: the model id and the blockstate's {@code y} turn. */
    public record Part(String model, int y) {
    }

    /** The models TFC's blockstate draws for {@code state} (a variants file: one; a multipart file: every applying part). */
    public static List<Part> look(JsonObject blockstate, String source, Map<String, String> state) {
        if (blockstate.has("variants")) {
            JsonObject variants = blockstate.getAsJsonObject("variants");
            List<Map<String, String>> tries = new ArrayList<>();
            tries.add(state);
            if (state.containsKey("part")) {
                Map<String, String> m = new LinkedHashMap<>(state);
                m.put("part", "bottom");
                tries.add(m);
            }
            if (state.containsKey("stick")) {
                Map<String, String> m = new LinkedHashMap<>(state);
                m.put("stick", "false");
                tries.add(m);
                if (state.containsKey("part")) {
                    m = new LinkedHashMap<>(m);
                    m.put("part", "bottom");
                    tries.add(m);
                }
            }
            for (Map<String, String> s : tries) {
                String bestKey = null;
                int bestSize = -1;
                for (String key : variants.keySet()) {
                    Map<String, String> k = keyProps(key);
                    boolean ok = true;
                    for (Map.Entry<String, String> e : k.entrySet()) {
                        if (!e.getValue().equals(s.get(e.getKey()))) {
                            ok = false;
                            break;
                        }
                    }
                    if (ok && k.size() > bestSize) {
                        bestKey = key;
                        bestSize = k.size();
                    }
                }
                if (bestKey != null) {
                    return List.of(part(variants.get(bestKey).getAsJsonObject(), source));
                }
            }
            throw new DatagenException(source + " draws no model for the state " + state);
        }
        List<Part> out = new ArrayList<>();
        for (JsonElement e : blockstate.getAsJsonArray("multipart")) {
            JsonObject o = e.getAsJsonObject();
            boolean ok = true;
            if (o.has("when")) {
                JsonObject when = o.getAsJsonObject("when");
                if (when.has("OR") || when.has("AND")) {
                    throw new DatagenException(source + ": a compound multipart condition is new");
                }
                for (Map.Entry<String, JsonElement> w : when.entrySet()) {
                    if (!w.getValue().getAsString().equals(state.get(w.getKey()))) {
                        ok = false;
                    }
                }
            }
            if (ok) {
                out.add(part(o.getAsJsonObject("apply"), source));
            }
        }
        if (out.isEmpty()) {
            throw new DatagenException(source + " draws no model for the state " + state);
        }
        return out;
    }

    private static Part part(JsonObject apply, String source) {
        if (apply.has("x") || apply.has("uvlock") || apply.has("weight") || apply.has("model") == false) {
            throw new DatagenException(source + ": a variant is turned about x, weighted or has no model");
        }
        int y = apply.has("y") ? apply.get("y").getAsInt() : 0;
        return new Part(apply.get("model").getAsString(), y);
    }

    private static Map<String, String> keyProps(String key) {
        Map<String, String> m = new LinkedHashMap<>();
        if (key.isEmpty()) {
            return m;
        }
        for (String kv : key.split(",")) {
            String[] p = kv.split("=", 2);
            m.put(p[0], p[1]);
        }
        return m;
    }

    /** The appearance token of a model: its path under the family directory, the crop name dropped. */
    static String token(String kind, CropDef c, Part p) {
        String path = p.model().substring(p.model().indexOf(":block/") + ":block/".length()); // crop/wheat_age_3
        String dir = path.substring(0, path.indexOf('/'));
        String base = path.substring(path.indexOf('/') + 1);
        String tail;
        if (base.equals(c.name())) {
            tail = "base";
        } else if (base.startsWith(c.name() + "_")) {
            tail = base.substring(c.name().length() + 1);
        } else {
            tail = base; // stick: shared by the climbing crops
        }
        String prefix = dir.equals(kind) ? "" : dir.equals("dead_crop") ? "dead_" : dir.equals("wild_crop") ? "wild_" : dir + "_";
        return prefix + tail + (p.y() != 0 ? "_y" + p.y() : "");
    }

    /** The display turn of TFC's blockstate {@code y} (see CharcoalBlocks.renderRotation). */
    public static String rotation(int y) {
        return "0," + Math.floorMod(180 - y, 360) + ",0";
    }

    /** The display-only item that draws {@code model}. */
    static String renderItem(String model) {
        return Json.TO_NS + ":render/" + model.substring(model.indexOf(":block/") + ":block/".length());
    }

    /** One block's resolved states: the spec and the models it draws. */
    record Built(DeviceBlockSpec spec, Set<String> models, int states) {
    }

    static Built spec(TfcCheckout tfc, String kind, CropDef c) {
        String id = HitboxDemands.cropId(kind, c.name());
        String src = BLOCKSTATES + kind + "/" + c.name() + ".json";
        JsonObject blockstate = Json.parse(tfc.readString(src), src).getAsJsonObject();
        List<DeviceProperty> props = properties(kind, c);
        DeviceBlockSpec.Builder b = DeviceBlockSpec.builder(id).properties(props);
        Map<String, DeviceAppearance> appearances = new LinkedHashMap<>();
        Map<String, String> byState = new LinkedHashMap<>();
        Set<String> models = new TreeSet<>();
        Set<String> renderItems = new LinkedHashSet<>();
        Map<String, String> hidden = new TreeMap<>();
        for (Map<String, String> state : DeviceBlockSpec.combinations(props)) {
            List<Part> parts = look(blockstate, src, state);
            String shape = outline(kind, c, state);
            List<String> tokens = new ArrayList<>();
            for (Part p : parts) {
                tokens.add(token(kind, c, p));
            }
            String name = shape + "_" + String.join("__", tokens);
            byState.put(state.toString(), name);
            if (appearances.containsKey(name)) {
                continue;
            }
            HitboxAllocation.Assignment a = HitboxAllocation.agreed(id, HitboxDemands.cropAppearancePattern(shape));
            if (a.mode() != HitboxAllocation.Mode.HITBOX_ONLY) {
                throw new DatagenException(id + "#" + name + " got a " + a.mode() + " carrier; CropBlocks draws item displays");
            }
            List<DeviceAppearance.Renderer> renderers = new ArrayList<>();
            for (Part p : parts) {
                models.add(p.model());
                String item = renderItem(p.model());
                if (renderItems.add(item)) {
                    b.renderItem(item, p.model());
                }
                renderers.add(DeviceAppearance.Renderer.item(item).rotated(rotation(p.y())));
            }
            appearances.put(name, DeviceAppearance.rendered(name, DeviceAppearance.Carrier.state(a.state()), renderers));
            if (a.hideTarget() != null && !a.pool() && hidden.putIfAbsent(a.state(), a.hideTarget()) == null) {
                b.stateMapping(a.state(), a.hideTarget());
            }
        }
        appearances.values().forEach(b::appearance);
        b.everyState(s -> byState.get(s.toString()), null)
            .settings(DeviceBlockSpec.Settings.of(STRENGTH, STRENGTH, DeviceSound.STONE, "hoe"));
        DeviceBlockSpec built = b.build();
        return new Built(built, models, (int) built.stateCount());
    }

    /** The literal entry: crop sounds, no mineable tag value other than the hoe, push reaction destroy, flammable(60, 30). */
    @SuppressWarnings("unchecked")
    static Map<String, Object> yaml(DeviceBlockSpec spec) {
        Map<String, Object> root = DeviceYaml.literal(spec);
        Map<String, Object> block = (Map<String, Object>) ((Map<String, Object>) root.get("blocks")).get(spec.id());
        Map<String, Object> settings = (Map<String, Object>) block.get("settings");
        settings.put("sounds", new LinkedHashMap<>(CROP_SOUNDS));
        settings.put("push_reaction", "destroy");
        // A hitbox-only carrier is drawn empty: the server must not treat the block as occluding, suffocating or view-blocking.
        settings.put("can_occlude", false);
        settings.put("is_suffocating", false);
        settings.put("is_view_blocking", false);
        // NeoForge: getFireSpreadSpeed is the vanilla ignite odds (CE burn_chance), getFlammability the burn odds (fire_spread_chance).
        settings.put("burnable", true);
        settings.put("burn_chance", FIRE_SPREAD_SPEED);
        settings.put("fire_spread_chance", FLAMMABILITY);
        return root;
    }

    /** Writes the entries, models, textures, seeds, loot and names. Returns the number of server states. */
    public static int run(TfcCheckout tfc, OutputTree out) {
        checkSources(tfc);
        List<CropDef> crops = crops(tfc);
        List<String> names = crops.stream().map(CropDef::name).toList();
        if (!names.equals(HitboxDemands.CROPS)) {
            throw new DatagenException(CROP_SOURCE + " lists " + names + " but HitboxDemands.CROPS is " + HitboxDemands.CROPS);
        }
        int states = 0;
        Set<String> models = new TreeSet<>();
        Set<String> renderItemsDone = new TreeSet<>();
        for (CropDef c : crops) {
            Map<String, Object> blocks = new LinkedHashMap<>();
            Map<String, Object> items = new LinkedHashMap<>();
            Map<String, Object> mappings = new TreeMap<>();
            for (String kind : HitboxDemands.CROP_KINDS) {
                Built built = spec(tfc, kind, c);
                states += built.states();
                models.addAll(built.models());
                Map<String, Object> root = yaml(built.spec());
                blocks.putAll(cast(root.get("blocks")));
                if (root.get("items") != null) {
                    // a model shared by crops (the climbing crops' stick) has one render item, defined once
                    cast(root.get("items")).forEach((k, v) -> {
                        if (renderItemsDone.add(k)) {
                            items.put(k, v);
                        }
                    });
                }
                if (root.get("block_state_mappings") != null) {
                    mappings.putAll(cast(root.get("block_state_mappings")));
                }
                String loot = LOOT + kind + "/" + c.name() + ".json";
                LootTableExport.delegateBlock(out, kind + "/" + c.name(), Json.rename(Json.parse(tfc.readString(loot), loot)),
                    named -> { });
            }
            items.put(seedId(c.name()), seedEntry(c.name()));
            copySeed(tfc, out, c.name());
            Map<String, Object> root = new LinkedHashMap<>();
            root.put("blocks", blocks);
            root.put("items", items);
            root.put("block_state_mappings", mappings);
            out.put(OUTPUT_DIR + c.name() + ".yml", Yaml.write(DeviceYaml.HEADER, root));
        }
        Set<String> copied = new TreeSet<>();
        for (String model : models) {
            copyModel(tfc, out, model, copied);
        }
        writeLang(tfc, out, names);
        return states;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> cast(Object o) {
        return (Map<String, Object>) o;
    }

    public static String seedId(String crop) {
        return Json.TO_NS + ":seeds/" + crop;
    }

    /** The seed item: TFC's {@code SeedItem} is an {@code ItemNameBlockItem} of the living block. */
    static Map<String, Object> seedEntry(String crop) {
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("material", "paper");
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("item_name", "<!i><lang:item." + Json.TO_NS + ".seeds." + crop + ">");
        e.put("data", data);
        e.put("model", Json.TO_NS + ":item/seeds/" + crop);
        Map<String, Object> behavior = new LinkedHashMap<>();
        behavior.put("type", "block_item");
        behavior.put("block", HitboxDemands.cropId("crop", crop));
        e.put("behavior", behavior);
        return e;
    }

    /** TFC's TFCBlocks and Crop registrations the generator depends on. */
    static void checkSources(TfcCheckout tfc) {
        String crop = tfc.readString(CROP_SOURCE).replaceAll("\\s+", " ");
        for (String needle : List.of("noCollission().randomTicks().strength(0.4F).sound(SoundType.CROP).flammable(60, 30)"
            + ".pushReaction(PushReaction.DESTROY);")) {
            if (!crop.contains(needle)) {
                throw new DatagenException(CROP_SOURCE + " no longer contains \"" + needle + "\": re-check the crop settings");
            }
        }
        String items = tfc.readString("src/main/java/net/dries007/tfc/common/items/TFCItems.java").replaceAll("\\s+", " ");
        if (!items.contains("register(\"seeds/\" + crop.name(), () -> new SeedItem(crop, TFCBlocks.CROPS.get(crop).get(),")) {
            throw new DatagenException("TFCItems no longer registers the seeds as SeedItem(crop, CROPS.get(crop), ...)");
        }
        String seed = tfc.readString("src/main/java/net/dries007/tfc/common/items/SeedItem.java");
        if (!seed.contains("public class SeedItem extends ItemNameBlockItem")) {
            throw new DatagenException("SeedItem is no longer an ItemNameBlockItem");
        }
    }

    /** Copies a block model, its parents (renamed) and their textures; a tinted element is dropped (ledger). */
    public static void copyModel(TfcCheckout tfc, OutputTree out, String model, Set<String> done) {
        if (!model.startsWith(Json.FROM_NS + ":") || !done.add(model)) {
            return;
        }
        String path = model.substring(Json.FROM_NS.length() + 1);
        String src = MODELS + path + ".json";
        if (!tfc.exists(src)) {
            throw new DatagenException("TFC has no model " + model);
        }
        JsonObject m = Json.parse(tfc.readString(src), src).getAsJsonObject();
        dropTinted(m);
        if (m.has("parent")) {
            copyModel(tfc, out, m.get("parent").getAsString(), done);
        }
        if (m.has("overrides") || m.has("loader")) {
            throw new DatagenException(src + " has overrides or a custom loader");
        }
        JsonObject textures = m.has("textures") ? m.getAsJsonObject("textures") : new JsonObject();
        for (Map.Entry<String, JsonElement> t : textures.entrySet()) {
            String ref = t.getValue().getAsString();
            if (!ref.startsWith(Json.FROM_NS + ":")) {
                continue;
            }
            String tex = ref.substring(Json.FROM_NS.length() + 1);
            String png = TEXTURES + tex + ".png";
            if (!tfc.exists(png)) {
                throw new DatagenException(src + " uses missing texture " + ref);
            }
            out.put(RP + "textures/" + tex + ".png", tfc.read(png));
            if (tfc.exists(png + ".mcmeta")) {
                out.put(RP + "textures/" + tex + ".png.mcmeta", tfc.read(png + ".mcmeta"));
            }
        }
        out.put(RP + "models/" + path + ".json", Json.write(Json.rename(m)));
    }

    /**
     * The wild crops' grass overlay elements carry {@code tintindex}; an item display cannot take the biome's grass colour,
     * so those elements (and the texture only they use) are left out (ledger divergence).
     */
    static void dropTinted(JsonObject model) {
        if (!model.has("elements")) {
            return;
        }
        JsonArray kept = new JsonArray();
        boolean dropped = false;
        Set<String> used = new TreeSet<>();
        for (JsonElement e : model.getAsJsonArray("elements")) {
            boolean tinted = false;
            for (Map.Entry<String, JsonElement> f : e.getAsJsonObject().getAsJsonObject("faces").entrySet()) {
                if (f.getValue().getAsJsonObject().has("tintindex")) {
                    tinted = true;
                }
            }
            if (tinted) {
                dropped = true;
                continue;
            }
            kept.add(e);
            for (Map.Entry<String, JsonElement> f : e.getAsJsonObject().getAsJsonObject("faces").entrySet()) {
                String t = f.getValue().getAsJsonObject().get("texture").getAsString();
                if (t.startsWith("#")) {
                    used.add(t.substring(1));
                }
            }
        }
        if (!dropped) {
            return;
        }
        model.add("elements", kept);
        JsonObject textures = model.getAsJsonObject("textures");
        JsonObject keep = new JsonObject();
        for (Map.Entry<String, JsonElement> t : textures.entrySet()) {
            String v = t.getValue().getAsString();
            if (used.contains(t.getKey()) || t.getKey().equals("particle") || v.startsWith("#")) {
                keep.add(t.getKey(), t.getValue());
            }
        }
        model.add("textures", keep);
    }

    private static void copySeed(TfcCheckout tfc, OutputTree out, String crop) {
        String src = MODELS + "item/seeds/" + crop + ".json";
        JsonObject model = Json.parse(tfc.readString(src), src).getAsJsonObject();
        if (!model.get("parent").getAsString().equals("item/generated")) {
            throw new DatagenException(src + " is no longer a generated item model");
        }
        String ref = model.getAsJsonObject("textures").get("layer0").getAsString();
        String tex = ref.substring(Json.FROM_NS.length() + 1);
        String png = TEXTURES + tex + ".png";
        if (!ref.startsWith(Json.FROM_NS + ":") || !tfc.exists(png)) {
            throw new DatagenException(src + " uses missing texture " + ref);
        }
        out.put(RP + "textures/" + tex + ".png", tfc.read(png));
        out.put(RP + "models/item/seeds/" + crop + ".json", Json.write(Json.rename(model)));
    }

    /** The seeds' names, {@code item.tfp.seeds.<crop>}, in every TFC locale that has them (English must have all). */
    private static void writeLang(TfcCheckout tfc, OutputTree out, List<String> names) {
        for (String f : tfc.list(LANG)) {
            if (!f.endsWith(".json")) {
                continue;
            }
            String locale = f.substring(LANG.length(), f.length() - ".json".length());
            JsonObject src = Json.parse(tfc.readString(f), f).getAsJsonObject();
            TreeMap<String, String> keys = new TreeMap<>();
            for (String crop : names) {
                String tfcKey = "item." + Json.FROM_NS + ".seeds." + crop;
                if (src.has(tfcKey)) {
                    keys.put("item." + Json.TO_NS + ".seeds." + crop, src.get(tfcKey).getAsString());
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
