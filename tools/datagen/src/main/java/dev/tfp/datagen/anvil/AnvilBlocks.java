/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/devices/AnvilBlock.java @ c3b4d9536 (FACING, DROP),
 *       src/main/java/net/dries007/tfc/common/blocks/rock/RockAnvilBlock.java @ c3b4d9536 (DROP),
 *       src/main/java/net/dries007/tfc/util/Metal.java @ c3b4d9536 (BlockType.ANVIL: strength 10, SoundType.ANVIL,
 *       requiresCorrectToolForDrops),
 *       src/main/java/net/dries007/tfc/common/blocks/TFCBlocks.java @ c3b4d9536 (ROCK_ANVILS: strength 2 / 10, SoundType.STONE,
 *       requiresCorrectToolForDrops),
 *       src/main/resources/assets/tfc/blockstates/metal/anvil/*.json, models/block/anvil.json, anvil_inventory.json,
 *       models/block/metal/anvil/*.json, models/item/metal/anvil/*.json and textures/block/metal/smooth/*.png @ c3b4d9536
 *       (copied),
 *       src/main/resources/data/tfc/loot_table/blocks/metal/anvil/*.json and rock/anvil/*.json @ c3b4d9536 (the drops),
 *       src/generated/resources/data/minecraft/tags/block/mineable/pickaxe.json @ c3b4d9536 (the tool),
 *       src/main/resources/assets/tfc/lang/*.json @ c3b4d9536 (block.tfc.metal.anvil.*)
 */
package dev.tfp.datagen.anvil;

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
import dev.tfp.datagen.rendered.RenderedSound;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PAR-METAL-03: the anvil blocks as CraftEngine blocks ({@code craftengine/tfp/configuration/anvil/anvil.yml}): the nine
 * metal anvils {@code tfp:metal/anvil/<metal>} and the seven rock anvils {@code tfp:rock/anvil/<rock>}.
 *
 * <ul>
 *   <li>The metal anvil has TFC's {@code facing} (the four horizontal directions, default north); the plugin sets it from the
 *       placing player ({@code AnvilBlock.getStateForPlacement}). The rock anvil has no property.</li>
 *   <li>Carriers: the hitbox selector serves TFC's outlines ({@code SHAPE_X} for east and west, {@code SHAPE_Z} for north and
 *       south, and the rock anvil's box) hitbox-only, so every anvil of one outline shares one carrier drawn empty; the look
 *       is TFC's block model of that metal or rock as an {@code entity_renderer} item display, turned by TFC's blockstate
 *       {@code y} per facing ({@link HitboxDemands}).</li>
 *   <li>The {@code tfp:device} behaviour (the plugin's {@code AnvilDevice}), TFC's settings (strength 10 / 10 for metal, 2 / 10
 *       for rock, a pickaxe to drop anything), the sounds of vanilla {@code SoundType.ANVIL} (volume 0.3) for the metal
 *       anvils, TFC's loot (the metal anvil itself; 1 to 4 loose rocks for the rock anvil) and the block item.</li>
 * </ul>
 * The rock anvil's models, item models and names come from the rock families' output. The metal models, the shared
 * {@code anvil} and {@code anvil_inventory} parents, the metal textures and the item models are copied from TFC here, and
 * the metal anvils' names go to {@code assets/tfp_anvil/lang/<locale>.json}.
 */
public final class AnvilBlocks {
    public static final String OUTPUT = "craftengine/tfp/configuration/anvil/anvil.yml";
    public static final String PROPERTY = "facing";
    public static final List<String> FACINGS = List.of("north", "east", "south", "west");
    static final String ANVIL = "src/main/java/net/dries007/tfc/common/blocks/devices/AnvilBlock.java";
    static final String ROCK_ANVIL = "src/main/java/net/dries007/tfc/common/blocks/rock/RockAnvilBlock.java";
    static final String METAL = "src/main/java/net/dries007/tfc/util/Metal.java";
    static final String BLOCKS = "src/main/java/net/dries007/tfc/common/blocks/TFCBlocks.java";
    static final String STATES = TfcCheckout.ASSETS + "blockstates/";
    static final String MODELS = TfcCheckout.ASSETS + "models/";
    static final String TEXTURES = TfcCheckout.ASSETS + "textures/";
    static final String MINEABLE = TfcCheckout.GENERATED + "data/minecraft/tags/block/mineable/";
    static final String LANG = TfcCheckout.ASSETS + "lang/";
    static final String PACK = "craftengine/tfp/";
    static final String RP = PACK + "resourcepack/assets/tfp/";
    static final String LANG_RP = PACK + "resourcepack/assets/tfp_anvil/lang/";
    /** The vanilla {@code SoundType.ANVIL}: volume 0.3, pitch 1.0. */
    static final RenderedSound ANVIL_SOUND = new RenderedSound(new BigDecimal("0.3"), BigDecimal.ONE);
    private static final List<String> SOUND_EVENTS = List.of("break", "step", "place", "hit", "fall");
    private static final Pattern METAL_ANVIL = Pattern.compile(
        "ANVIL\\(PartType\\.ALL, metal -> new AnvilBlock\\(ExtendedProperties\\.of\\(\\)\\.mapColor\\(metal\\.mapColor\\(\\)\\)"
            + "\\.noOcclusion\\(\\)\\.sound\\(SoundType\\.ANVIL\\)\\.strength\\(([0-9.]+), ([0-9.]+)\\)"
            + "\\.requiresCorrectToolForDrops\\(\\)\\.blockEntity\\(TFCBlockEntities\\.ANVIL\\)");
    private static final Pattern ROCK_ANVIL_REGISTER = Pattern.compile(
        "register\\(\"rock/anvil/\" \\+ rock\\.name\\(\\), \\(\\) -> new RockAnvilBlock\\(ExtendedProperties\\.of\\(\\)"
            + "\\.mapColor\\(MapColor\\.STONE\\)\\.sound\\(SoundType\\.STONE\\)\\.strength\\(([0-9.]+), ([0-9.]+)\\)"
            + "\\.requiresCorrectToolForDrops\\(\\)");
    private static final Pattern TEXTURE = Pattern.compile("\"" + Json.FROM_NS + ":(block/[a-z0-9_/]+)\"");

    private AnvilBlocks() {
    }

    /** True when the checkout has the anvils (the small test fixtures do not). */
    public static boolean available(TfcCheckout tfc) {
        return tfc.exists(ANVIL) && tfc.exists(MODELS + "block/anvil.json");
    }

    // ---- names ----------------------------------------------------------------------------------------

    public static String metalId(String metal) {
        return HitboxDemands.anvilId(metal);
    }

    public static String rockId(String rock) {
        return HitboxDemands.rockAnvilId(rock);
    }

    public static String metalLangKey(String metal) {
        return "block." + Json.TO_NS + ".metal.anvil." + metal;
    }

    public static String rockLangKey(String rock) {
        return "block." + Json.TO_NS + ".rock.anvil." + rock;
    }

    public static String metalModel(String metal) {
        return Json.TO_NS + ":block/metal/anvil/" + metal;
    }

    public static String rockModel(String rock) {
        return Json.TO_NS + ":block/rock/anvil/" + rock;
    }

    /** The display-only item that draws {@code metalModel}. */
    public static String metalRenderItem(String metal) {
        return Json.TO_NS + ":render/metal/anvil/" + metal;
    }

    public static String rockRenderItem(String rock) {
        return Json.TO_NS + ":render/rock/anvil/" + rock;
    }

    /** The display turn of TFC's blockstate {@code y} (see CharcoalBlocks.renderRotation): {@code 180 - y} about y. */
    static String rotation(int y) {
        return "0," + Math.floorMod(180 - y, 360) + ",0";
    }

    /** The half turn every block-model item display needs to match the block (no blockstate turn: y = 0 is west). */
    static final String ROCK_ROTATION = "0,180,0";

    // ---- the run --------------------------------------------------------------------------------------

    /** Writes the entries, models, textures and names. Returns the number of server states (9 x 4 + 7). */
    public static int run(TfcCheckout tfc, OutputTree out) {
        checkSource(tfc);
        checkLists(tfc);
        String[] metalStrength = metalStrength(tfc);
        String[] rockStrength = rockStrength(tfc);
        Map<String, Object> blocks = new LinkedHashMap<>();
        Map<String, Object> items = new LinkedHashMap<>();
        Map<String, Object> mappings = new TreeMap<>();
        int states = 0;
        for (String metal : HitboxDemands.ANVIL_METALS) {
            Map<String, Integer> turns = checkMetalBlockstate(tfc, metal);
            copyMetalModels(tfc, out, metal);
            DeviceBlockSpec spec = metalSpec(tfc, metal, metalStrength, turns);
            states += (int) spec.stateCount();
            merge(metalYaml(spec), blocks, items, mappings);
        }
        for (String rock : HitboxDemands.ANVIL_ROCKS) {
            checkRockBlockstate(tfc, rock);
            DeviceBlockSpec spec = rockSpec(tfc, rock, rockStrength);
            states += (int) spec.stateCount();
            merge(rockYaml(spec, rock), blocks, items, mappings);
        }
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("blocks", blocks);
        root.put("items", items);
        if (!mappings.isEmpty()) {
            root.put("block_state_mappings", mappings);
        }
        out.put(OUTPUT, Yaml.write(DeviceYaml.HEADER, root));
        copyParents(tfc, out);
        writeLang(tfc, out);
        return states;
    }

    @SuppressWarnings("unchecked")
    private static void merge(Map<String, Object> one, Map<String, Object> blocks, Map<String, Object> items,
            Map<String, Object> mappings) {
        blocks.putAll((Map<String, Object>) one.get("blocks"));
        items.putAll((Map<String, Object>) one.getOrDefault("items", Map.of()));
        for (Map.Entry<String, Object> e : ((Map<String, Object>) one.getOrDefault("block_state_mappings", Map.of())).entrySet()) {
            Object old = mappings.putIfAbsent(e.getKey(), e.getValue());
            if (old != null && !old.equals(e.getValue())) {
                throw new DatagenException("anvil carrier " + e.getKey() + " hidden as " + old + " and " + e.getValue());
            }
        }
    }

    // ---- TFC's source ---------------------------------------------------------------------------------

    /** TFC's states and behaviour: FACING only, the placement turn, the slots dropped on removal. */
    static void checkSource(TfcCheckout tfc) {
        String src = tfc.readString(ANVIL).replaceAll("\\s+", " ");
        for (String needle : List.of("public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;",
                "super(properties, InventoryRemoveBehavior.DROP);",
                "defaultBlockState().setValue(FACING, context.getHorizontalDirection().getClockWise())",
                "super.createBlockStateDefinition(builder.add(FACING));",
                "private static final VoxelShape SHAPE_X = box(0, 0, 3, 16, 11, 13);",
                "private static final VoxelShape SHAPE_Z = box(3, 0, 0, 13, 11, 16);")) {
            if (!src.contains(needle)) {
                throw new DatagenException(ANVIL + " no longer contains \"" + needle + "\": re-check the anvil entries");
            }
        }
        String rock = tfc.readString(ROCK_ANVIL).replaceAll("\\s+", " ");
        for (String needle : List.of("super(properties, InventoryRemoveBehavior.DROP);", "public int getTier() { return 0; }")) {
            if (!rock.contains(needle)) {
                throw new DatagenException(ROCK_ANVIL + " no longer contains \"" + needle + "\": re-check the anvil entries");
            }
        }
    }

    /** The anvil blockstates are exactly the metals and rocks {@link HitboxDemands} serves. */
    static void checkLists(TfcCheckout tfc) {
        for (String[] kind : new String[][] {{"metal", String.join(",", HitboxDemands.ANVIL_METALS)},
            {"rock", String.join(",", HitboxDemands.ANVIL_ROCKS)}}) {
            String dir = STATES + kind[0] + "/anvil/";
            TreeSet<String> have = new TreeSet<>();
            for (String f : tfc.list(dir)) {
                if (f.endsWith(".json")) {
                    have.add(f.substring(dir.length(), f.length() - ".json".length()));
                }
            }
            TreeSet<String> want = new TreeSet<>(List.of(kind[1].split(",")));
            if (!have.equals(want)) {
                throw new DatagenException(dir + " holds " + have + ", HitboxDemands serves " + want);
            }
        }
    }

    /** {hardness, resistance} of the metal anvil in {@code Metal.java}. */
    static String[] metalStrength(TfcCheckout tfc) {
        Matcher m = METAL_ANVIL.matcher(tfc.readString(METAL));
        if (!m.find()) {
            throw new DatagenException(METAL + ": the anvil block type no longer matches " + METAL_ANVIL.pattern());
        }
        return new String[] {m.group(1), m.group(2)};
    }

    /** {hardness, resistance} of the rock anvil registration. */
    static String[] rockStrength(TfcCheckout tfc) {
        Matcher m = ROCK_ANVIL_REGISTER.matcher(tfc.readString(BLOCKS));
        if (!m.find()) {
            throw new DatagenException(BLOCKS + ": the rock anvil registration no longer matches " + ROCK_ANVIL_REGISTER.pattern());
        }
        return new String[] {m.group(1), m.group(2)};
    }

    /** TFC's mineable tag of {@code block} (must be the pickaxe, which the settings' correct tools name). */
    static void checkPickaxe(TfcCheckout tfc, String block) {
        for (String t : List.of("axe", "hoe", "shovel")) {
            if (tfc.readString(MINEABLE + t + ".json").contains("\"" + Json.FROM_NS + ":" + block + "\"")) {
                throw new DatagenException("tfc:" + block + " is in minecraft:mineable/" + t + ": AnvilBlocks writes the pickaxe");
            }
        }
        String src = MINEABLE + "pickaxe.json";
        for (JsonElement v : Json.parse(tfc.readString(src), src).getAsJsonObject().getAsJsonArray("values")) {
            if (v.isJsonPrimitive() && v.getAsString().equals(Json.FROM_NS + ":" + block)) {
                return;
            }
        }
        throw new DatagenException("tfc:" + block + " is not in minecraft:mineable/pickaxe");
    }

    /** One variant per facing drawing the metal's model turned about y only; returns {@code y} per facing. */
    static Map<String, Integer> checkMetalBlockstate(TfcCheckout tfc, String metal) {
        String src = STATES + "metal/anvil/" + metal + ".json";
        JsonObject variants = Json.parse(tfc.readString(src), src).getAsJsonObject().getAsJsonObject("variants");
        if (variants == null || variants.size() != FACINGS.size()) {
            throw new DatagenException(src + ": not one variant per facing");
        }
        Map<String, Integer> turns = new LinkedHashMap<>();
        for (String facing : FACINGS) {
            JsonObject v = variants.getAsJsonObject("facing=" + facing);
            String want = Json.FROM_NS + ":block/metal/anvil/" + metal;
            if (v == null || !want.equals(v.get("model").getAsString()) || v.has("x")) {
                throw new DatagenException(src + ": facing=" + facing + " no longer draws " + want + " turned about y only");
            }
            turns.put(facing, v.has("y") ? v.get("y").getAsInt() : 0);
        }
        return turns;
    }

    static void checkRockBlockstate(TfcCheckout tfc, String rock) {
        String src = STATES + "rock/anvil/" + rock + ".json";
        JsonObject variants = Json.parse(tfc.readString(src), src).getAsJsonObject().getAsJsonObject("variants");
        String want = Json.FROM_NS + ":block/rock/anvil/" + rock;
        if (variants == null || variants.size() != 1 || !variants.has("")
            || !want.equals(variants.getAsJsonObject("").get("model").getAsString())
            || variants.getAsJsonObject("").size() != 1) {
            throw new DatagenException(src + " is no longer one unturned variant drawing " + want);
        }
    }

    // ---- specs ----------------------------------------------------------------------------------------

    /** The carrier of one appearance; the anvils' is hitbox-only (the look is the item display). */
    static HitboxAllocation.Assignment carrier(String id, String appearance) {
        HitboxAllocation.Assignment a = HitboxAllocation.agreed(id, appearance);
        if (a.mode() != HitboxAllocation.Mode.HITBOX_ONLY) {
            throw new DatagenException("the anvil carrier of " + id + "#" + appearance + " is " + a.mode()
                + ", AnvilBlocks draws a transparent carrier");
        }
        return a;
    }

    static DeviceBlockSpec metalSpec(TfcCheckout tfc, String metal, String[] strength, Map<String, Integer> turns) {
        String id = metalId(metal);
        checkPickaxe(tfc, "metal/anvil/" + metal);
        DeviceBlockSpec.Builder b = DeviceBlockSpec.builder(id).property(DeviceProperty.names(PROPERTY, FACINGS, "north"));
        List<HitboxAllocation.Assignment> carriers = new ArrayList<>();
        for (String facing : FACINGS) {
            HitboxAllocation.Assignment a = carrier(id, facing);
            carriers.add(a);
            b.appearance(DeviceAppearance.rendered(facing, DeviceAppearance.Carrier.state(a.state()),
                List.of(DeviceAppearance.Renderer.item(metalRenderItem(metal)).rotated(rotation(turns.get(facing))))));
        }
        b.everyState(s -> s.get(PROPERTY), null)
            // the sound is replaced by SoundType.ANVIL's in metalYaml; stone only gets the spec through its checks
            .settings(DeviceBlockSpec.Settings.of(strength[0], strength[1], DeviceSound.STONE, "pickaxe"))
            .loot(DeviceLoot.fromTfc(tfc, "metal/anvil/" + metal))
            .deviceBehaviour()
            .blockItem(metalLangKey(metal))
            .renderItem(metalRenderItem(metal), metalModel(metal));
        hide(b, carriers);
        return b.build();
    }

    static DeviceBlockSpec rockSpec(TfcCheckout tfc, String rock, String[] strength) {
        String id = rockId(rock);
        checkPickaxe(tfc, "rock/anvil/" + rock);
        HitboxAllocation.Assignment a = carrier(id, HitboxDemands.ANVIL_ROCK_APPEARANCE);
        DeviceBlockSpec.Builder b = DeviceBlockSpec.builder(id)
            .appearance(DeviceAppearance.rendered(HitboxDemands.ANVIL_ROCK_APPEARANCE, DeviceAppearance.Carrier.state(a.state()),
                List.of(DeviceAppearance.Renderer.item(rockRenderItem(rock)).rotated(ROCK_ROTATION))))
            .everyState(s -> HitboxDemands.ANVIL_ROCK_APPEARANCE, null)
            .settings(DeviceBlockSpec.Settings.of(strength[0], strength[1], DeviceSound.STONE, "pickaxe"))
            .loot(DeviceLoot.self()) // replaced by TFC's loose rocks in rockYaml
            .deviceBehaviour()
            .blockItem(rockLangKey(rock))
            .renderItem(rockRenderItem(rock), rockModel(rock));
        hide(b, List.of(a));
        return b.build();
    }

    private static void hide(DeviceBlockSpec.Builder b, List<HitboxAllocation.Assignment> carriers) {
        Map<String, String> hidden = new LinkedHashMap<>();
        for (HitboxAllocation.Assignment a : carriers) {
            if (a.hideTarget() != null && hidden.putIfAbsent(a.state(), a.hideTarget()) == null) {
                b.stateMapping(a.state(), a.hideTarget());
            }
        }
    }

    /** TFC's {@code AnvilBlock.getShape}: east and west take SHAPE_X, north and south SHAPE_Z. */
    static String axis(String facing) {
        return HitboxDemands.anvilAxis(facing);
    }

    // ---- the yaml -------------------------------------------------------------------------------------

    /** The templated entry with {@code SoundType.ANVIL}'s events and the pickaxe as the correct tool. */
    @SuppressWarnings("unchecked")
    static Map<String, Object> metalYaml(DeviceBlockSpec spec) {
        Map<String, Object> root = DeviceYaml.templated(spec);
        Map<String, Object> block = block(root, spec.id());
        Map<String, Object> args = (Map<String, Object>) block.get("arguments");
        for (String event : SOUND_EVENTS) {
            String key = "device_sound_" + event;
            if (!args.containsKey(key)) {
                throw new DatagenException("the device template has no argument " + key + ": re-check AnvilBlocks' sounds");
            }
            Map<String, Object> sound = new LinkedHashMap<>();
            sound.put("id", "minecraft:block.anvil." + soundEvent(event));
            sound.put("volume", ANVIL_SOUND.volume(event));
            sound.put("pitch", ANVIL_SOUND.pitch(event));
            args.put(key, sound);
        }
        correctTools(block);
        return root;
    }

    /** The rock anvil's entry: stone sounds from the template, TFC's 1 to 4 loose rocks, the pickaxe. */
    @SuppressWarnings("unchecked")
    static Map<String, Object> rockYaml(DeviceBlockSpec spec, String rock) {
        Map<String, Object> root = DeviceYaml.templated(spec);
        Map<String, Object> block = block(root, spec.id());
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("loot_item", Json.TO_NS + ":rock/loose/" + rock);
        args.put("loot_count_min", 1);
        args.put("loot_count_max", 4);
        Map<String, Object> loot = new LinkedHashMap<>();
        loot.put("template", "tfp:loot_table/item_count");
        loot.put("arguments", args);
        block.put("loot", loot);
        correctTools(block);
        return root;
    }

    /** vanilla {@code block.anvil.*}: the break event is {@code break}, the rest as named. */
    private static String soundEvent(String event) {
        return event;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> block(Map<String, Object> root, String id) {
        return (Map<String, Object>) ((Map<String, Object>) root.get("blocks")).get(id);
    }

    /** TFC {@code requiresCorrectToolForDrops}: the pickaxes (vanilla and ours), as the rock blocks' settings. */
    private static void correctTools(Map<String, Object> block) {
        block.put("settings", Map.of("correct_tools", List.of("#minecraft:pickaxes", "#tfp:tools/pickaxe")));
    }

    // ---- models, textures, names ----------------------------------------------------------------------

    /** TFC's per-metal block model, its item model (the block model as parent), and the textures the block model names. */
    static void copyMetalModels(TfcCheckout tfc, OutputTree out, String metal) {
        String blockPath = "block/metal/anvil/" + metal;
        String blockSrc = MODELS + blockPath + ".json";
        String text = tfc.readString(blockSrc);
        JsonObject m = Json.parse(text, blockSrc).getAsJsonObject();
        if (!(Json.FROM_NS + ":block/anvil").equals(m.get("parent").getAsString())) {
            throw new DatagenException(blockSrc + ": parent is no longer tfc:block/anvil");
        }
        out.put(RP + "models/" + blockPath + ".json", Json.write(Json.rename(m)));
        copyTextures(tfc, out, text, blockSrc);
        String itemSrc = MODELS + "item/metal/anvil/" + metal + ".json";
        JsonObject item = Json.parse(tfc.readString(itemSrc), itemSrc).getAsJsonObject();
        if (!(Json.FROM_NS + ":" + blockPath).equals(item.get("parent").getAsString())) {
            throw new DatagenException(itemSrc + " is no longer the block model as parent");
        }
        out.put(RP + "models/item/metal/anvil/" + metal + ".json", Json.write(Json.rename(item)));
    }

    /** The shared parents {@code block/anvil} and {@code block/anvil_inventory}. */
    static void copyParents(TfcCheckout tfc, OutputTree out) {
        for (String path : List.of("block/anvil", "block/anvil_inventory")) {
            String src = MODELS + path + ".json";
            String text = tfc.readString(src);
            out.put(RP + "models/" + path + ".json", Json.write(Json.rename(Json.parse(text, src))));
            copyTextures(tfc, out, text, src);
        }
    }

    private static void copyTextures(TfcCheckout tfc, OutputTree out, String modelText, String source) {
        JsonObject textures = Json.parse(modelText, source).getAsJsonObject().getAsJsonObject("textures");
        if (textures == null) {
            return;
        }
        for (Map.Entry<String, JsonElement> e : textures.entrySet()) {
            Matcher t = TEXTURE.matcher("\"" + e.getValue().getAsString() + "\"");
            if (!t.matches()) {
                continue;
            }
            String png = TEXTURES + t.group(1) + ".png";
            if (!tfc.exists(png)) {
                throw new DatagenException(source + " names " + t.group(1) + ", which TFC has no texture for");
            }
            out.put(RP + "textures/" + t.group(1) + ".png", tfc.read(png));
        }
    }

    /** {@code block.tfp.metal.anvil.<metal>} in every TFC locale that names the anvils (English must name all). */
    static void writeLang(TfcCheckout tfc, OutputTree out) {
        for (String f : tfc.list(LANG)) {
            if (!f.endsWith(".json")) {
                continue;
            }
            String locale = f.substring(LANG.length(), f.length() - ".json".length());
            JsonObject src = Json.parse(tfc.readString(f), f).getAsJsonObject();
            Map<String, String> keys = new TreeMap<>();
            for (String metal : HitboxDemands.ANVIL_METALS) {
                String tfcKey = "block." + Json.FROM_NS + ".metal.anvil." + metal;
                if (src.has(tfcKey)) {
                    keys.put(metalLangKey(metal), src.get(tfcKey).getAsString());
                } else if (locale.equals("en_us")) {
                    throw new DatagenException("TFC has no English name " + tfcKey);
                }
            }
            if (keys.isEmpty()) {
                continue;
            }
            JsonObject o = new JsonObject();
            keys.forEach(o::addProperty);
            out.put(LANG_RP + locale + ".json", Json.write(o));
        }
    }
}
