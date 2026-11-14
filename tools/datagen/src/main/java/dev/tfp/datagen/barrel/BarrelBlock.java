/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/devices/SealableDeviceBlock.java @ c3b4d9536 (SEALED, default false),
 *       src/main/java/net/dries007/tfc/common/blocks/devices/BarrelBlock.java @ c3b4d9536 (FACING default UP, RACK default false),
 *       src/main/java/net/dries007/tfc/common/blocks/wood/Wood.java @ c3b4d9536 (the woods; BARREL: strength(2.5f), SoundType.WOOD),
 *       src/main/resources/assets/tfc/blockstates/wood/barrel/*.json, models/block/barrel.json, models/block/barrel_sealed.json,
 *       models/block/wood/barrel{,_sealed}/*.json, models/item/wood/barrel/*.json and textures/block/{barrel_hoop,
 *       wood/planks/*, wood/sheet/*}.png @ c3b4d9536 (copied),
 *       src/main/resources/data/tfc/loot_table/blocks/wood/barrel/*.json @ c3b4d9536 (the drop),
 *       src/generated/resources/data/minecraft/tags/block/mineable/axe.json @ c3b4d9536 (the tool),
 *       src/main/resources/assets/tfc/lang/*.json @ c3b4d9536 (block.tfc.wood.barrel.*)
 */
package dev.tfp.datagen.barrel;

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
import dev.tfp.datagen.device.DeviceSound;
import dev.tfp.datagen.device.DeviceYaml;
import dev.tfp.datagen.hitbox.HitboxAllocation;
import dev.tfp.datagen.hitbox.HitboxDemands;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PAR-PROC-32: the upright barrels as CraftEngine blocks {@code tfp:wood/barrel/<wood>}, one per TFC wood, in
 * {@code craftengine/tfp/configuration/barrel/barrel.yml}. The plugin's {@code BarrelDevice} (PAR-PROC-08) runs them.
 *
 * <ul>
 *   <li>The state properties {@code sealed} (TFC {@code SealableDeviceBlock.SEALED}, default false; the device writes it),
 *       and since PAR-PROC-38 {@code facing} (TFC {@code FACING_NOT_DOWN}: up or a side, default up; a string property, so
 *       CraftEngine adds no placement logic of its own: the plugin's {@code BarrelRackListener} sets it as TFC's
 *       {@code getStateForPlacement} does) and {@code rack} (TFC {@code RACK}, default false). TFC's {@code powered} is
 *       not a state yet (the redstone seal is a follow-up). 20 states per wood.</li>
 *   <li>PAR-PROC-38: a barrel on its side takes the carrier of its axis and rack demand (served for the sealed upright
 *       row, {@link HitboxDemands#BARREL_SIDE_BORROWED_ROW}: the same transparent cactus state) and draws TFC's side model ({@code <wood>_side}, {@code <wood>_side_rack}) turned
 *       by TFC's blockstate {@code y} ({@code 180 - y} about the display's y). An upright racked state (TFC has it, nothing
 *       reaches it) draws the upright model.</li>
 *   <li>Carriers: the hitbox selector serves both outlines ({@code tfc:wood/barrel[axis=y,sealed=false|true]}) hitbox-only
 *       ({@link HitboxDemands}): there are 40 per-wood looks and only 16 explicit carriers for the 12x16x12 outline, so the
 *       carrier is drawn empty and the look is an {@code entity_renderer} item display of TFC's block model of that wood
 *       and state ({@code tfp:render/wood/barrel{,_sealed}/<wood>}).</li>
 *   <li>The {@code tfp:device} behaviour through {@code tfp:block/device_rendered}, TFC's settings (strength 2.5, wood
 *       sounds, the axe tag), the loot (the barrel itself, both of TFC's alternatives) and the block item with TFC's item
 *       model (without its {@code tfc:sealed} override: the barrel item never carries contents, ledger).</li>
 * </ul>
 */
public final class BarrelBlock {
    public static final String KIND = Json.TO_NS + ":wood/barrel";
    static final String SOURCE = "src/main/java/net/dries007/tfc/common/blocks/devices/BarrelBlock.java";
    static final String SEALABLE = "src/main/java/net/dries007/tfc/common/blocks/devices/SealableDeviceBlock.java";
    static final String WOOD = "src/main/java/net/dries007/tfc/common/blocks/wood/Wood.java";
    static final String BLOCKSTATES = TfcCheckout.ASSETS + "blockstates/wood/barrel/";
    static final String MODELS = TfcCheckout.ASSETS + "models/";
    static final String TEXTURES = TfcCheckout.ASSETS + "textures/";
    static final String LOOT = TfcCheckout.MAIN + "data/" + Json.FROM_NS + "/loot_table/blocks/wood/barrel/";
    static final String MINEABLE = TfcCheckout.GENERATED + "data/minecraft/tags/block/mineable/";
    static final String LANG = TfcCheckout.ASSETS + "lang/";
    static final String PACK = "craftengine/tfp/";
    public static final String OUTPUT = PACK + "configuration/barrel/barrel.yml";
    static final String RP = PACK + "resourcepack/assets/tfp/";
    static final String LANG_RP = PACK + "resourcepack/assets/tfp_barrel/lang/";
    public static final String PROPERTY = "sealed";
    /** PAR-PROC-38: TFC {@code BarrelBlock.FACING} ({@code FACING_NOT_DOWN}, vanilla's Direction order without down). */
    public static final String FACING = "facing";
    public static final List<String> FACINGS = List.of("up", "north", "south", "west", "east");
    /** PAR-PROC-38: TFC {@code BarrelBlock.RACK}. */
    public static final String RACK = "rack";
    /** The half turn every block-model item display needs to match the block (see ForgeDevices.OVERLAY_ROTATION). */
    static final String RENDER_ROTATION = "0,180,0";
    private static final Pattern WOOD_ENUM = Pattern.compile("public enum Wood implements RegistryWood\\s*\\{(.*?);", Pattern.DOTALL);
    private static final Pattern WOOD_CONSTANT = Pattern.compile("^ {4}([A-Z_]+)\\(", Pattern.MULTILINE);
    private static final Pattern BARREL_LINE = Pattern.compile(
        "BARREL\\(\\(self, wood\\) -> new BarrelBlock\\(properties\\(wood\\)\\.strength\\(([0-9.]+)f\\)");
    private static final Pattern WOOD_SOUND = Pattern.compile(
        "private static ExtendedProperties properties\\(RegistryWood wood\\)\\s*\\{\\s*return ExtendedProperties\\.of\\(wood\\.woodColor\\(\\)\\)\\.sound\\(SoundType\\.(\\w+)\\)");
    private static final Pattern TEXTURE = Pattern.compile("\"" + Json.FROM_NS + ":(block/[a-z0-9_/]+)\"");

    private BarrelBlock() {
    }

    /** True when the checkout has the barrels (the small test fixtures do not). */
    public static boolean available(TfcCheckout tfc) {
        return tfc.exists(SOURCE) && tfc.exists(MODELS + "block/barrel.json");
    }

    public static String id(String wood) {
        return HitboxDemands.barrelId(wood);
    }

    public static String langKey(String wood) {
        return "block." + Json.TO_NS + ".wood.barrel." + wood;
    }

    /** The block model of {@code wood} in one state (TFC's, copied). */
    public static String model(String wood, boolean sealed) {
        return Json.TO_NS + ":block/wood/" + (sealed ? "barrel_sealed/" : "barrel/") + wood;
    }

    /** The display-only item that draws {@link #model}. */
    public static String renderItem(String wood, boolean sealed) {
        return Json.TO_NS + ":render/wood/" + (sealed ? "barrel_sealed/" : "barrel/") + wood;
    }

    /** PAR-PROC-38: TFC's side model suffix ({@code _side}, {@code _side_rack}). */
    static String sideSuffix(boolean rack) {
        return rack ? "_side_rack" : "_side";
    }

    /** PAR-PROC-38: the side block model of {@code wood} (TFC's, copied). */
    public static String sideModel(String wood, boolean sealed, boolean rack) {
        return model(wood, sealed) + sideSuffix(rack);
    }

    /** PAR-PROC-38: the display-only item that draws {@link #sideModel}. */
    public static String sideRenderItem(String wood, boolean sealed, boolean rack) {
        return renderItem(wood, sealed) + sideSuffix(rack);
    }

    /** PAR-PROC-38: the axis of a side facing. */
    public static String axis(String facing) {
        return facing.equals("east") || facing.equals("west") ? "x" : "z";
    }

    /** PAR-PROC-38: the appearance of a barrel on its side ({@code x_east_unsealed}, {@code z_north_sealed_rack}). */
    public static String sideAppearance(String facing, boolean sealed, boolean rack) {
        return axis(facing) + "_" + facing + "_" + (sealed ? "sealed" : "unsealed") + (rack ? "_rack" : "");
    }

    /** PAR-PROC-38: the display turn of TFC's blockstate {@code y} (see CharcoalBlocks.renderRotation). */
    static String sideRotation(int y) {
        return "0," + Math.floorMod(180 - y, 360) + ",0";
    }

    /** Writes the entries, their models, textures and names. Returns the number of server states (20 per wood). */
    public static int run(TfcCheckout tfc, OutputTree out) {
        checkSource(tfc);
        List<String> woods = woods(tfc);
        String strength = strength(tfc);
        String tool = null;
        Map<String, Object> blocks = new LinkedHashMap<>();
        Map<String, Object> items = new LinkedHashMap<>();
        Map<String, Object> mappings = new TreeMap<>();
        int states = 0;
        for (String wood : woods) {
            String t = tool(tfc, wood);
            if (tool != null && !tool.equals(t)) {
                throw new DatagenException("the barrels are in different mineable tags: " + tool + ", " + t);
            }
            tool = t;
            checkLoot(tfc, wood);
            Map<String, Integer> turns = checkBlockstate(tfc, wood);
            copyModels(tfc, out, wood);
            DeviceBlockSpec spec = spec(wood, strength, t, turns);
            states += (int) spec.stateCount();
            merge(DeviceYaml.templated(spec), blocks, items, mappings);
        }
        copyParents(tfc, out);
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("blocks", blocks);
        root.put("items", items);
        if (!mappings.isEmpty()) {
            root.put("block_state_mappings", mappings);
        }
        out.put(OUTPUT, Yaml.write(DeviceYaml.HEADER, root));
        writeLang(tfc, out, woods);
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
                throw new DatagenException("barrel carrier " + e.getKey() + " hidden as " + old + " and " + e.getValue());
            }
        }
    }

    /** TFC's states: SEALED default false (SealableDeviceBlock), FACING default UP and RACK default false (BarrelBlock). */
    static void checkSource(TfcCheckout tfc) {
        String sealable = tfc.readString(SEALABLE);
        for (String needle : List.of("public static final BooleanProperty SEALED = TFCBlockStateProperties.SEALED;",
                "registerDefaultState(getStateDefinition().any().setValue(SEALED, false).setValue(POWERED, false));")) {
            if (!sealable.contains(needle)) {
                throw new DatagenException(SEALABLE + " no longer contains \"" + needle + "\": re-check the barrel entry");
            }
        }
        String barrel = tfc.readString(SOURCE).replaceAll("\\s+", " ");
        for (String needle : List.of(".setValue(SEALED, false) .setValue(FACING, Direction.UP) .setValue(RACK, false)",
                "super.createBlockStateDefinition(builder.add(FACING, RACK));",
                "public static final EnumProperty<Direction> FACING = TFCBlockStateProperties.FACING_NOT_DOWN;")) {
            if (!barrel.contains(needle)) {
                throw new DatagenException(SOURCE + " no longer contains \"" + needle + "\": re-check the barrel entry");
            }
        }
    }

    /** TFC's woods (the {@code Wood} enum), which must equal {@link HitboxDemands#BARREL_WOODS} and the blockstates. */
    static List<String> woods(TfcCheckout tfc) {
        Matcher e = WOOD_ENUM.matcher(tfc.readString(WOOD));
        if (!e.find()) {
            throw new DatagenException(WOOD + ": no Wood enum");
        }
        List<String> woods = new ArrayList<>();
        Matcher c = WOOD_CONSTANT.matcher(e.group(1));
        while (c.find()) {
            woods.add(c.group(1).toLowerCase(java.util.Locale.ROOT));
        }
        if (!woods.equals(HitboxDemands.BARREL_WOODS)) {
            throw new DatagenException("TFC's woods " + woods + " differ from HitboxDemands.BARREL_WOODS");
        }
        TreeSet<String> states = new TreeSet<>();
        for (String f : tfc.list(BLOCKSTATES)) {
            if (f.endsWith(".json")) {
                states.add(f.substring(BLOCKSTATES.length(), f.length() - ".json".length()));
            }
        }
        if (!states.equals(new TreeSet<>(woods))) {
            throw new DatagenException(BLOCKSTATES + " holds " + states + ", not the woods " + woods);
        }
        return woods;
    }

    /** TFC's {@code BARREL} hardness (one value: strength(h) sets both); its sound must be wood. */
    static String strength(TfcCheckout tfc) {
        String src = tfc.readString(WOOD);
        Matcher m = BARREL_LINE.matcher(src);
        if (!m.find()) {
            throw new DatagenException(WOOD + ": the barrel registration no longer matches " + BARREL_LINE.pattern());
        }
        Matcher s = WOOD_SOUND.matcher(src);
        if (!s.find() || !s.group(1).equals("WOOD")) {
            throw new DatagenException(WOOD + ": the wood block properties no longer use SoundType.WOOD");
        }
        return m.group(1);
    }

    /** TFC's mineable tag of the barrel of {@code wood} (exactly one). */
    static String tool(TfcCheckout tfc, String wood) {
        String block = Json.FROM_NS + ":wood/barrel/" + wood;
        String found = null;
        for (String t : List.of("axe", "hoe", "pickaxe", "shovel")) {
            String src = MINEABLE + t + ".json";
            for (JsonElement v : Json.parse(tfc.readString(src), src).getAsJsonObject().getAsJsonArray("values")) {
                if (v.isJsonPrimitive() && v.getAsString().equals(block)) {
                    if (found != null) {
                        throw new DatagenException(block + " is in two mineable tags: " + found + ", " + t);
                    }
                    found = t;
                }
            }
        }
        if (found == null) {
            throw new DatagenException(block + " is in no minecraft:mineable tag");
        }
        return found;
    }

    /**
     * TFC's barrel table: one pool under survives_explosion, an alternatives entry whose every child drops the barrel
     * itself (the sealed child also copies the contents, which the plugin's barrel item does not carry: ledger).
     */
    static void checkLoot(TfcCheckout tfc, String wood) {
        String src = LOOT + wood + ".json";
        String self = Json.FROM_NS + ":wood/barrel/" + wood;
        JsonObject root = Json.parse(tfc.readString(src), src).getAsJsonObject();
        JsonArray pools = root.getAsJsonArray("pools");
        if (pools == null || pools.size() != 1) {
            throw new DatagenException(src + ": not one pool");
        }
        JsonObject pool = pools.get(0).getAsJsonObject();
        JsonArray conditions = pool.getAsJsonArray("conditions");
        if (pool.get("rolls").getAsDouble() != 1 || conditions == null || conditions.size() != 1
            || !"minecraft:survives_explosion".equals(conditions.get(0).getAsJsonObject().get("condition").getAsString())) {
            throw new DatagenException(src + ": the pool is not one roll under survives_explosion");
        }
        JsonArray entries = pool.getAsJsonArray("entries");
        if (entries.size() != 1 || !"minecraft:alternatives".equals(entries.get(0).getAsJsonObject().get("type").getAsString())) {
            throw new DatagenException(src + ": the entry is not one alternatives entry");
        }
        JsonArray children = entries.get(0).getAsJsonObject().getAsJsonArray("children");
        if (children.size() != 2) {
            throw new DatagenException(src + ": the alternatives no longer have two children");
        }
        for (JsonElement ch : children) {
            JsonObject o = ch.getAsJsonObject();
            if (!"minecraft:item".equals(o.get("type").getAsString()) || !self.equals(o.get("name").getAsString())) {
                throw new DatagenException(src + ": a child does not drop " + self);
            }
        }
    }

    /**
     * TFC's upright states draw {@code barrel/<wood>} unsealed and {@code barrel_sealed/<wood>} sealed; PAR-PROC-38: the
     * side states draw {@code <wood>_side} or {@code <wood>_side_rack} turned by one {@code y} per facing (the same for
     * every seal and rack). Returns the side facings' {@code y}.
     */
    static Map<String, Integer> checkBlockstate(TfcCheckout tfc, String wood) {
        String src = BLOCKSTATES + wood + ".json";
        JsonObject variants = Json.parse(tfc.readString(src), src).getAsJsonObject().getAsJsonObject("variants");
        if (variants == null || variants.size() != FACINGS.size() * 4) {
            throw new DatagenException(src + ": not one variant per facing, rack and seal");
        }
        Map<String, Integer> turns = new LinkedHashMap<>();
        for (String facing : FACINGS) {
            for (boolean sealed : new boolean[] {false, true}) {
                for (boolean rack : new boolean[] {false, true}) {
                    String key = "facing=" + facing + ",rack=" + rack + ",sealed=" + sealed;
                    JsonObject v = variants.getAsJsonObject(key);
                    String base = Json.FROM_NS + ":block/wood/" + (sealed ? "barrel_sealed/" : "barrel/") + wood;
                    boolean up = facing.equals("up");
                    String want = up ? base : base + sideSuffix(rack);
                    if (v == null || !want.equals(v.get("model").getAsString()) || v.has("x") || (up && v.has("y"))) {
                        throw new DatagenException(src + ": " + key + " no longer draws " + want
                            + (up ? " unrotated" : " turned about y only"));
                    }
                    if (!up) {
                        int y = v.has("y") ? v.get("y").getAsInt() : 0;
                        Integer old = turns.putIfAbsent(facing, y);
                        if (old != null && old != y) {
                            throw new DatagenException(src + ": facing=" + facing + " turns " + old + " and " + y);
                        }
                    }
                }
            }
        }
        return turns;
    }

    static DeviceBlockSpec spec(String wood, String strength, String tool, Map<String, Integer> turns) {
        String id = id(wood);
        HitboxAllocation.Assignment unsealed = carrier(id, HitboxDemands.BARREL_UNSEALED);
        HitboxAllocation.Assignment sealed = carrier(id, HitboxDemands.BARREL_SEALED);
        List<HitboxAllocation.Assignment> carriers = new ArrayList<>(List.of(unsealed, sealed));
        DeviceBlockSpec.Builder b = DeviceBlockSpec.builder(id)
            .property(DeviceProperty.bool(PROPERTY, false))
            .property(DeviceProperty.names(FACING, FACINGS, "up"))
            .property(DeviceProperty.bool(RACK, false))
            .appearance(DeviceAppearance.rendered(HitboxDemands.BARREL_UNSEALED,
                DeviceAppearance.Carrier.state(unsealed.state()), List.of(renderer(wood, false))))
            .appearance(DeviceAppearance.rendered(HitboxDemands.BARREL_SEALED,
                DeviceAppearance.Carrier.state(sealed.state()), List.of(renderer(wood, true))));
        for (String facing : FACINGS) {
            if (facing.equals("up")) {
                continue;
            }
            for (boolean rack : new boolean[] {false, true}) {
                HitboxAllocation.Assignment side = carrier(id, HitboxDemands.barrelSideDemand(axis(facing), rack));
                carriers.add(side);
                for (boolean s : new boolean[] {false, true}) {
                    b.appearance(DeviceAppearance.rendered(sideAppearance(facing, s, rack),
                        DeviceAppearance.Carrier.state(side.state()), List.of(DeviceAppearance.Renderer
                            .item(sideRenderItem(wood, s, rack)).rotated(sideRotation(turns.get(facing))))));
                }
            }
        }
        b.everyState(BarrelBlock::appearance, null)
            .settings(DeviceBlockSpec.Settings.of(strength, strength, DeviceSound.WOOD, tool))
            .loot(DeviceLoot.self())
            .deviceBehaviour()
            .blockItem(langKey(wood))
            .renderItem(renderItem(wood, false), model(wood, false))
            .renderItem(renderItem(wood, true), model(wood, true));
        for (boolean s : new boolean[] {false, true}) {
            for (boolean rack : new boolean[] {false, true}) {
                b.renderItem(sideRenderItem(wood, s, rack), sideModel(wood, s, rack));
            }
        }
        Map<String, String> hidden = new LinkedHashMap<>();
        for (HitboxAllocation.Assignment a : carriers) {
            if (a.hideTarget() != null && hidden.putIfAbsent(a.state(), a.hideTarget()) == null) {
                b.stateMapping(a.state(), a.hideTarget());
            }
        }
        return b.build();
    }

    /** The appearance of one server state: upright by the seal (also when racked), on its side by facing, seal, rack. */
    static String appearance(Map<String, String> state) {
        boolean sealed = Boolean.parseBoolean(state.get(PROPERTY));
        String facing = state.get(FACING);
        if (facing.equals("up")) {
            return sealed ? HitboxDemands.BARREL_SEALED : HitboxDemands.BARREL_UNSEALED;
        }
        return sideAppearance(facing, sealed, Boolean.parseBoolean(state.get(RACK)));
    }

    private static DeviceAppearance.Renderer renderer(String wood, boolean sealed) {
        return DeviceAppearance.Renderer.item(renderItem(wood, sealed)).rotated(RENDER_ROTATION);
    }

    /** The carrier of one appearance; the barrel's is hitbox-only (the look is the item display). */
    static HitboxAllocation.Assignment carrier(String id, String appearance) {
        HitboxAllocation.Assignment a = HitboxAllocation.agreed(id, appearance);
        if (a.mode() != HitboxAllocation.Mode.HITBOX_ONLY) {
            throw new DatagenException("the barrel carrier of " + id + "#" + appearance + " is " + a.mode()
                + ", BarrelBlock draws a transparent carrier");
        }
        return a;
    }

    /**
     * TFC's per-wood block models (both upright states and, PAR-PROC-38, the four side models) and item model, renamed;
     * and the textures they name.
     */
    static void copyModels(TfcCheckout tfc, OutputTree out, String wood) {
        List<String> paths = new ArrayList<>();
        for (String dir : List.of("block/wood/barrel/", "block/wood/barrel_sealed/")) {
            for (String suffix : List.of("", "_side", "_side_rack")) {
                paths.add(dir + wood + suffix);
            }
        }
        for (String path : paths) {
            String src = MODELS + path + ".json";
            String text = tfc.readString(src);
            JsonObject m = Json.parse(text, src).getAsJsonObject();
            String parent = m.get("parent").getAsString();
            if (!parent.startsWith(Json.FROM_NS + ":") || !PARENTS.contains(parent.substring(parent.indexOf(':') + 1))) {
                throw new DatagenException(src + ": parent " + parent + " is not a barrel base model");
            }
            out.put(RP + "models/" + path + ".json", Json.write(Json.rename(m)));
            copyTextures(tfc, out, text, src);
        }
        String src = MODELS + "item/wood/barrel/" + wood + ".json";
        JsonObject item = Json.parse(tfc.readString(src), src).getAsJsonObject();
        if (!(Json.FROM_NS + ":block/wood/barrel/" + wood).equals(item.get("parent").getAsString())) {
            throw new DatagenException(src + " is no longer the unsealed block model as parent");
        }
        item.remove("overrides");
        out.put(RP + "models/item/wood/barrel/" + wood + ".json", Json.write(Json.rename(item)));
    }

    /** The base models TFC's per-wood models inherit from (PAR-PROC-38: and the four side ones). */
    static final List<String> PARENTS = List.of("block/barrel", "block/barrel_sealed", "block/barrel_side",
        "block/barrel_side_rack", "block/barrel_side_sealed", "block/barrel_side_sealed_rack");

    /** The base models, and their hoop, tap and rack textures. */
    static void copyParents(TfcCheckout tfc, OutputTree out) {
        for (String path : PARENTS) {
            String src = MODELS + path + ".json";
            String text = tfc.readString(src);
            out.put(RP + "models/" + path + ".json", Json.write(Json.rename(Json.parse(text, src))));
            copyTextures(tfc, out, text, src);
        }
    }

    /** The textures of a model's {@code textures} map ({@code tfc:} ids; {@code #refs} skipped). */
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

    /** {@code block.tfp.wood.barrel.<wood>} in every TFC locale that names the barrels (English must name all). */
    static void writeLang(TfcCheckout tfc, OutputTree out, List<String> woods) {
        for (String f : tfc.list(LANG)) {
            if (!f.endsWith(".json")) {
                continue;
            }
            String locale = f.substring(LANG.length(), f.length() - ".json".length());
            JsonObject src = Json.parse(tfc.readString(f), f).getAsJsonObject();
            Map<String, String> keys = new TreeMap<>();
            for (String wood : woods) {
                String tfcKey = "block." + Json.FROM_NS + ".wood.barrel." + wood;
                if (src.has(tfcKey)) {
                    keys.put(langKey(wood), src.get(tfcKey).getAsString());
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
