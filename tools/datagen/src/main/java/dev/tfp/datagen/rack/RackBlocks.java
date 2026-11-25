/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/wood/Wood.java @ c3b4d9536 (TOOL_RACK, SHELF, BOOKSHELF, LECTERN)
 * src/main/java/net/dries007/tfc/common/blocks/wood/ToolRackBlock.java, ShelfBlock.java, BookshelfBlock.java, TFCLecternBlock.java @ c3b4d9536,
 * assets/tfc/blockstates/wood/{tool_rack,shelf,bookshelf,lectern}/**, assets/tfc/models/{block,item}/wood/** (copied),
 * src/generated/resources/data/minecraft/tags/block/mineable/axe.json, data/tfc/loot_table/blocks/wood/** and assets/tfc/lang/*.json
 */
package dev.tfp.datagen.rack;

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
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PAR-DECO-05: the tool racks, shelves, bookshelves and lecterns of the 20 woods ({@code wood/tool_rack/<wood>},
 * {@code wood/shelf/<wood>}, {@code wood/bookshelf/<wood>}, {@code wood/lectern/<wood>}) as CraftEngine device blocks, one
 * {@code configuration/rack/<wood>.yml} per wood.
 *
 * <ul>
 *   <li>Each block draws TFC's blockstate models as item displays (a multipart bookshelf: its base and the six slot models, empty
 *       or occupied, for each of its 4 x 64 states), turned like the blockstate's {@code y}.</li>
 *   <li>Hitboxes: the tool rack is hitbox-only on the open {@code oak_trapdoor} the wood logic blocks of PAR-DECO-02 already
 *       draw empty (the board sits on the facing's far edge, 3 px against TFC's 2); the shelf on the closed top trapdoor; the
 *       lectern on vanilla's own lectern state (its real outline, no book); the bookshelf on the barrier (full cube).</li>
 *   <li>States: {@code facing} for all, plus {@code slot_<n>_occupied} (6) for the bookshelf, as vanilla's chiseled bookshelf;
 *       nothing is waterlogged (ledger). The block has the {@code tfp:device} behaviour: the slots, the clicks and the placement
 *       are the plugin's {@code rack} module.</li>
 *   <li>TFC's strength, wood sounds, the axe tag, the plain self drop and names; the item is the block item.</li>
 * </ul>
 */
public final class RackBlocks {
    public static final List<String> KINDS = List.of("tool_rack", "shelf", "bookshelf", "lectern");
    public static final List<String> FACINGS = List.of("north", "east", "south", "west");
    public static final int BOOK_SLOTS = 6;
    /** The invisible full-collision carrier of the bookshelf (the chests use it too). */
    public static final String BARRIER = "minecraft:barrier";
    /** The open trapdoor (the board on one edge) of PAR-DECO-02's shared twin carries the tool rack. */
    public static final String RACK_CARRIER = "minecraft:oak_trapdoor[facing=%s,half=bottom,open=true,powered=false,waterlogged=false]";
    /** The closed top trapdoor (a plate under the ceiling of the block) carries the shelf. */
    public static final String SHELF_CARRIER = "minecraft:oak_trapdoor[facing=%s,half=top,open=false,powered=false,waterlogged=false]";
    /** Vanilla's lectern state, never in a TFC world, carries the lectern with its real outline. */
    public static final String LECTERN_CARRIER = "minecraft:lectern[facing=%s,has_book=false,powered=false]";

    static final String PACK = "craftengine/tfp/";
    public static final String CONFIG = PACK + "configuration/rack/";
    static final String RP = PACK + "resourcepack/assets/tfp/";
    static final String LANG_RP = PACK + "resourcepack/assets/tfp_rack/lang/";
    static final String BLOCKSTATES = TfcCheckout.ASSETS + "blockstates/wood/";
    static final String MODELS = TfcCheckout.ASSETS + "models/";
    static final String TEXTURES = TfcCheckout.ASSETS + "textures/";
    static final String LANG = TfcCheckout.ASSETS + "lang/";
    static final String WOOD_JAVA = "src/main/java/net/dries007/tfc/common/blocks/wood/Wood.java";
    static final String MINEABLE = TfcCheckout.GENERATED + "data/minecraft/tags/block/mineable/";
    private static final Map<String, String> CONSTANTS = Map.of("tool_rack", "TOOL_RACK", "shelf", "SHELF", "bookshelf", "BOOKSHELF",
        "lectern", "LECTERN");
    private static final Pattern STRENGTH = Pattern.compile(
        "^ {8}(TOOL_RACK|SHELF|BOOKSHELF|LECTERN)\\(wood -> new [A-Za-z]+\\(properties\\(wood\\)[^\\n]*?\\.strength\\(([0-9.]+)[fF](?:, ?([0-9.]+)[fF])?\\)",
        Pattern.MULTILINE);
    private static final String HEADER = "Generated by tools/datagen from TerraFirmaCraft @ " + TfcCheckout.PINNED_COMMIT.substring(0, 9)
        + ". Do not edit: fix the generator and regenerate.\nPAR-DECO-05: tool racks, shelves, bookshelves and lecterns.";

    private RackBlocks() {
    }

    public static boolean available(TfcCheckout tfc) {
        return tfc.exists(WOOD_JAVA) && KINDS.stream().noneMatch(k -> tfc.list(BLOCKSTATES + k + "/").isEmpty())
            && tfc.exists(MODELS + "block/tool_rack.json");
    }

    /** The woods that have a tool rack blockstate. */
    public static List<String> woods(TfcCheckout tfc) {
        String dir = BLOCKSTATES + "tool_rack/";
        TreeSet<String> woods = new TreeSet<>();
        for (String f : tfc.list(dir)) {
            if (f.endsWith(".json")) {
                woods.add(f.substring(dir.length(), f.length() - ".json".length()));
            }
        }
        return new ArrayList<>(woods);
    }

    /** {@code wood/tool_rack/oak}. */
    public static String path(String kind, String wood) {
        return "wood/" + kind + "/" + wood;
    }

    static String id(String path) {
        return Json.TO_NS + ":" + path;
    }

    static String langKey(String path) {
        return "block." + Json.TO_NS + "." + path.replace('/', '.');
    }

    /** The number of blocks written: four per wood. */
    public static int run(TfcCheckout tfc, OutputTree out) {
        List<String> woods = woods(tfc);
        Map<String, Strength> strengths = strengths(tfc);
        int count = 0;
        for (String wood : woods) {
            Map<String, Object> blocks = new LinkedHashMap<>();
            Map<String, Object> items = new LinkedHashMap<>();
            for (String kind : KINDS) {
                DeviceBlockSpec spec = spec(tfc, out, kind, wood, strengths.get(kind));
                Map<String, Object> entry = DeviceYaml.templated(spec);
                merge(entry, blocks, items);
                count++;
            }
            Map<String, Object> root = new LinkedHashMap<>();
            root.put("blocks", blocks);
            root.put("items", items);
            out.put(CONFIG + wood + ".yml", Yaml.write(HEADER, root));
        }
        writeLang(tfc, out, woods);
        return count;
    }

    @SuppressWarnings("unchecked")
    private static void merge(Map<String, Object> entry, Map<String, Object> blocks, Map<String, Object> items) {
        blocks.putAll((Map<String, Object>) entry.get("blocks"));
        items.putAll((Map<String, Object>) entry.get("items"));
    }

    // ---- blockstates ------------------------------------------------------------------------------------

    /** One model of a blockstate with its turn about y. */
    record Part(String model, int y) {
    }

    /** The models a blockstate draws for one state (a variant, or every multipart entry whose condition holds). */
    static List<Part> parts(TfcCheckout tfc, String kind, String wood, Map<String, String> state) {
        String src = BLOCKSTATES + kind + "/" + wood + ".json";
        JsonObject root = Json.parse(tfc.readString(src), src).getAsJsonObject();
        List<Part> out = new ArrayList<>();
        if (root.has("variants")) {
            for (Map.Entry<String, JsonElement> v : root.getAsJsonObject("variants").entrySet()) {
                if (matchesKey(v.getKey(), state)) {
                    out.add(part(v.getValue().getAsJsonObject(), src));
                }
            }
        } else {
            for (JsonElement e : root.getAsJsonArray("multipart")) {
                JsonObject entry = e.getAsJsonObject();
                if (!entry.has("when") || matches(entry.getAsJsonObject("when"), state)) {
                    out.add(part(entry.getAsJsonObject("apply"), src));
                }
            }
        }
        if (out.isEmpty()) {
            throw new DatagenException(src + " draws nothing for " + state);
        }
        return out;
    }

    private static boolean matchesKey(String key, Map<String, String> state) {
        for (String pair : key.split(",")) {
            int eq = pair.indexOf('=');
            if (!pair.substring(eq + 1).equals(state.get(pair.substring(0, eq)))) {
                return false;
            }
        }
        return true;
    }

    /** A multipart {@code when}: {@code {prop: "a|b"}}, or {@code AND}/{@code OR} of such. */
    private static boolean matches(JsonObject when, Map<String, String> state) {
        for (Map.Entry<String, JsonElement> c : when.entrySet()) {
            switch (c.getKey()) {
                case "AND" -> {
                    for (JsonElement s : c.getValue().getAsJsonArray()) {
                        if (!matches(s.getAsJsonObject(), state)) {
                            return false;
                        }
                    }
                }
                case "OR" -> {
                    boolean any = false;
                    for (JsonElement s : c.getValue().getAsJsonArray()) {
                        any |= matches(s.getAsJsonObject(), state);
                    }
                    if (!any) {
                        return false;
                    }
                }
                default -> {
                    if (!List.of(c.getValue().getAsString().split("\\|")).contains(state.get(c.getKey()))) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private static Part part(JsonObject apply, String src) {
        if (apply.has("x") || apply.has("weight")) {
            throw new DatagenException(src + " uses an x turn or weights, which the displays do not take");
        }
        String model = apply.get("model").getAsString();
        if (!model.startsWith(Json.FROM_NS + ":block/")) {
            throw new DatagenException(src + " names a model outside TFC: " + model);
        }
        return new Part(model.substring(Json.FROM_NS.length() + 1), apply.has("y") ? apply.get("y").getAsInt() : 0);
    }

    /** The item display turn of a model turned {@code y} degrees by a blockstate: the half turn of block-model displays minus it. */
    static String rotation(int y) {
        return "0," + Math.floorMod(180 - y, 360) + ",0";
    }

    /** {@code tfp:render/wood/bookshelf/oak_empty_top_left} for the model {@code block/wood/bookshelf/oak_empty_top_left}. */
    static String renderId(String model) {
        return Json.TO_NS + ":render/" + model.substring("block/".length());
    }

    // ---- the specs --------------------------------------------------------------------------------------

    static DeviceBlockSpec spec(TfcCheckout tfc, OutputTree out, String kind, String wood, Strength strength) {
        String path = path(kind, wood);
        List<DeviceProperty> props = new ArrayList<>();
        props.add(DeviceProperty.names("facing", FACINGS, "north"));
        if (kind.equals("bookshelf")) {
            for (int i = 0; i < BOOK_SLOTS; i++) {
                props.add(DeviceProperty.bool("slot_" + i + "_occupied", false));
            }
        }
        DeviceBlockSpec.Builder b = DeviceBlockSpec.builder(id(path)).properties(props);
        List<Map<String, String>> states = DeviceBlockSpec.combinations(props);
        Set<String> declared = new TreeSet<>();
        Set<String> named = new TreeSet<>();
        for (Map<String, String> state : states) {
            String name = appearanceName(state);
            if (!named.add(name)) {
                continue;
            }
            List<Part> parts = parts(tfc, kind, wood, state);
            List<DeviceAppearance.Renderer> renderers = new ArrayList<>();
            for (Part p : parts) {
                if (declared.add(p.model())) {
                    b.renderItem(renderId(p.model()), id(p.model()));
                    copyModel(tfc, out, p.model());
                }
                renderers.add(DeviceAppearance.Renderer.item(renderId(p.model())).rotated(rotation(p.y())));
            }
            b.appearance(DeviceAppearance.rendered(name, carrier(kind, state.get("facing")), renderers));
        }
        copyModel(tfc, out, "item/" + path);
        return b.everyState(RackBlocks::appearanceName, null)
            .settings(DeviceBlockSpec.Settings.of(strength.hardness().toPlainString(), strength.resistance().toPlainString(),
                DeviceSound.WOOD, tool(tfc, path)))
            .loot(DeviceLoot.fromTfc(tfc, path))
            .deviceBehaviour()
            .blockItem(langKey(path))
            .build();
    }

    /** {@code north} for the one-property kinds, {@code north_010000} for a bookshelf. */
    static String appearanceName(Map<String, String> state) {
        StringBuilder sb = new StringBuilder(state.get("facing"));
        if (state.containsKey("slot_0_occupied")) {
            sb.append('_');
            for (int i = 0; i < BOOK_SLOTS; i++) {
                sb.append(state.get("slot_" + i + "_occupied").equals("true") ? '1' : '0');
            }
        }
        return sb.toString();
    }

    static DeviceAppearance.Carrier carrier(String kind, String facing) {
        return DeviceAppearance.Carrier.state(switch (kind) {
            case "tool_rack" -> RACK_CARRIER.formatted(facing);
            case "shelf" -> SHELF_CARRIER.formatted(facing);
            case "lectern" -> LECTERN_CARRIER.formatted(facing);
            case "bookshelf" -> BARRIER;
            default -> throw new IllegalArgumentException(kind);
        });
    }

    // ---- TFC data ---------------------------------------------------------------------------------------

    /** TFC's hardness and resistance of a block. */
    record Strength(BigDecimal hardness, BigDecimal resistance) {
    }

    /** {@code strength(h)} or {@code strength(h, r)} of the four kinds, read from Wood.java. */
    static Map<String, Strength> strengths(TfcCheckout tfc) {
        Map<String, Strength> found = new TreeMap<>();
        Matcher m = STRENGTH.matcher(tfc.readString(WOOD_JAVA));
        while (m.find()) {
            BigDecimal h = new BigDecimal(m.group(2));
            BigDecimal r = m.group(3) == null ? h : new BigDecimal(m.group(3));
            String kind = CONSTANTS.entrySet().stream().filter(e -> e.getValue().equals(m.group(1))).findFirst().orElseThrow().getKey();
            found.put(kind, new Strength(h, r));
        }
        if (!found.keySet().equals(new TreeSet<>(KINDS))) {
            throw new DatagenException(WOOD_JAVA + ": TOOL_RACK, SHELF, BOOKSHELF and LECTERN no longer match " + STRENGTH.pattern());
        }
        return found;
    }

    /** The one {@code minecraft:mineable/<tool>} tag of TFC's generated data that holds the block. */
    static String tool(TfcCheckout tfc, String path) {
        String found = null;
        for (String t : List.of("axe", "hoe", "pickaxe", "shovel")) {
            String src = MINEABLE + t + ".json";
            for (JsonElement v : Json.parse(tfc.readString(src), src).getAsJsonObject().getAsJsonArray("values")) {
                if (v.isJsonPrimitive() && v.getAsString().equals(Json.FROM_NS + ":" + path)) {
                    if (found != null) {
                        throw new DatagenException("tfc:" + path + " is in two mineable tags: " + found + ", " + t);
                    }
                    found = t;
                }
            }
        }
        if (found == null) {
            throw new DatagenException("tfc:" + path + " is in no minecraft:mineable tag");
        }
        return found;
    }

    /** Copies a TFC model (renamed), its {@code tfc:} parents and their textures; vanilla parents stay references. */
    static void copyModel(TfcCheckout tfc, OutputTree out, String path) {
        String src = MODELS + path + ".json";
        if (!tfc.exists(src)) {
            throw new DatagenException("TFC has no model " + path);
        }
        JsonObject m = Json.rename(Json.parse(tfc.readString(src), src)).getAsJsonObject();
        out.put(RP + "models/" + path + ".json", Json.write(m));
        if (m.has("parent")) {
            String parent = m.get("parent").getAsString();
            if (parent.startsWith(Json.TO_NS + ":")) {
                copyModel(tfc, out, parent.substring(Json.TO_NS.length() + 1));
            }
        }
        JsonObject textures = m.getAsJsonObject("textures");
        if (textures != null) {
            for (Map.Entry<String, JsonElement> t : textures.entrySet()) {
                String v = t.getValue().getAsString();
                if (!v.startsWith(Json.TO_NS + ":")) {
                    continue;
                }
                String rel = v.substring(Json.TO_NS.length() + 1);
                String png = TEXTURES + rel + ".png";
                byte[] baked = RackTextures.tinted(tfc, rel);
                if (baked != null) {
                    out.put(RP + "textures/" + rel + ".png", baked);
                    continue;
                }
                if (!tfc.exists(png)) {
                    throw new DatagenException(src + " names " + v + ", which TFC has no texture for");
                }
                out.put(RP + "textures/" + rel + ".png", tfc.read(png));
                if (tfc.exists(png + ".mcmeta")) {
                    out.put(RP + "textures/" + rel + ".png.mcmeta", tfc.read(png + ".mcmeta"));
                }
            }
        }
    }

    static void writeLang(TfcCheckout tfc, OutputTree out, List<String> woods) {
        List<String> paths = new ArrayList<>();
        for (String w : woods) {
            for (String kind : KINDS) {
                paths.add(path(kind, w));
            }
        }
        for (String f : tfc.list(LANG)) {
            if (!f.endsWith(".json")) {
                continue;
            }
            String locale = f.substring(LANG.length(), f.length() - ".json".length());
            JsonObject src = Json.parse(tfc.readString(f), f).getAsJsonObject();
            Map<String, String> keys = new TreeMap<>();
            for (String path : paths) {
                String tfcKey = "block." + Json.FROM_NS + "." + path.replace('/', '.');
                if (src.has(tfcKey)) {
                    keys.put(langKey(path), src.get(tfcKey).getAsString());
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
