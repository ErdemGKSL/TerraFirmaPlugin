/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/wood/ScribingTableBlock.java and SewingTableBlock.java @ c3b4d9536
 *       (FACING default north, the shapes, the placement facing),
 *       src/main/java/net/dries007/tfc/common/blocks/wood/Wood.java @ c3b4d9536 (SCRIBING_TABLE, SEWING_TABLE: strength(2.5F),
 *       the wood sound),
 *       src/main/resources/assets/tfc/blockstates/wood/{scribing,sewing}_table/*.json, models/block/{scribing,sewing}_table.json,
 *       models/block/wood/{scribing,sewing}_table/*.json, models/item/wood/{scribing,sewing}_table/*.json and the textures they
 *       name @ c3b4d9536 (copied),
 *       src/main/resources/data/tfc/loot_table/blocks/wood/{scribing,sewing}_table/*.json @ c3b4d9536 (the drop),
 *       src/generated/resources/data/minecraft/tags/block/mineable/axe.json @ c3b4d9536 (the tool),
 *       src/main/resources/assets/tfc/lang/*.json @ c3b4d9536 (block.tfc.wood.{scribing,sewing}_table.*)
 */
package dev.tfp.datagen.tables;

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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PAR-PROC-49: the scribing tables and sewing tables as CraftEngine blocks {@code tfp:wood/scribing_table/<wood>} and
 * {@code tfp:wood/sewing_table/<wood>}, one per TFC wood, in {@code craftengine/tfp/configuration/tables/tables.yml}.
 * The plugins' {@code ScribingListener} (PAR-PROC-15) and sewing device (PAR-PROC-07) use them.
 *
 * <ul>
 *   <li>TFC's {@code facing} (the four horizontal directions, default north). 4 states per wood and kind.</li>
 *   <li>Carriers: the hitbox selector serves TFC's outlines hitbox-only ({@link HitboxDemands}: one per facing for the scribing
 *       table, whose side boards turn with it, one for the sewing table), shared by every wood; the look is TFC's block model
 *       of that wood as an {@code entity_renderer} item display turned by the blockstate's {@code y}.</li>
 *   <li>No block behaviour: the tables open screens through the plugin's listeners. TFC's settings (strength 2.5, wood sounds,
 *       the axe tag), the loot (the table itself) and the block item with TFC's item model.</li>
 * </ul>
 */
public final class TableBlocks {
    /** One kind of table. */
    record Kind(String name, String wood, String needle) {
        String id(String w) {
            return Json.TO_NS + ":wood/" + name + "/" + w;
        }

        String langKey(String w) {
            return "block." + Json.TO_NS + ".wood." + name + "." + w;
        }
    }

    static final Kind SCRIBING = new Kind("scribing_table", "SCRIBING_TABLE",
        "SCRIBING_TABLE\\(wood -> new ScribingTableBlock\\(properties\\(wood\\)\\.noOcclusion\\(\\)\\.strength\\(([0-9.]+)F\\)");
    static final Kind SEWING = new Kind("sewing_table", "SEWING_TABLE",
        "SEWING_TABLE\\(wood -> new SewingTableBlock\\(properties\\(wood\\)\\.noOcclusion\\(\\)\\.strength\\(([0-9.]+)F\\)");
    static final List<Kind> KINDS = List.of(SCRIBING, SEWING);
    static final String SOURCE = "src/main/java/net/dries007/tfc/common/blocks/wood/";
    static final String WOOD = SOURCE + "Wood.java";
    static final String MODELS = TfcCheckout.ASSETS + "models/";
    static final String TEXTURES = TfcCheckout.ASSETS + "textures/";
    static final String MINEABLE = TfcCheckout.GENERATED + "data/minecraft/tags/block/mineable/";
    static final String LANG = TfcCheckout.ASSETS + "lang/";
    static final String PACK = "craftengine/tfp/";
    public static final String OUTPUT = PACK + "configuration/tables/tables.yml";
    static final String RP = PACK + "resourcepack/assets/tfp/";
    static final String LANG_RP = PACK + "resourcepack/assets/tfp_tables/lang/";
    public static final String FACING = "facing";
    public static final List<String> FACINGS = HitboxDemands.FACINGS;
    private static final Pattern WOOD_SOUND = Pattern.compile(
        "private static ExtendedProperties properties\\(RegistryWood wood\\)\\s*\\{\\s*return ExtendedProperties\\.of\\(wood\\.woodColor\\(\\)\\)\\.sound\\(SoundType\\.(\\w+)\\)");
    private static final Pattern TEXTURE = Pattern.compile("\"" + Json.FROM_NS + ":(block/[a-z0-9_/]+)\"");

    private TableBlocks() {
    }

    /** True when the checkout has the tables (the small test fixtures do not). */
    public static boolean available(TfcCheckout tfc) {
        return tfc.exists(SOURCE + "ScribingTableBlock.java") && tfc.exists(SOURCE + "SewingTableBlock.java")
            && tfc.exists(MODELS + "block/scribing_table.json") && tfc.exists(WOOD);
    }

    public static String id(String kind, String wood) {
        return Json.TO_NS + ":wood/" + kind + "/" + wood;
    }

    /** The block model of {@code wood} (TFC's, copied). */
    public static String model(String kind, String wood) {
        return Json.TO_NS + ":block/wood/" + kind + "/" + wood;
    }

    /** The display-only item that draws {@link #model}. */
    public static String renderItem(String kind, String wood) {
        return Json.TO_NS + ":render/wood/" + kind + "/" + wood;
    }

    /** The display turn of TFC's blockstate {@code y} (see SluiceBlock.rotation). */
    static String rotation(int y) {
        return "0," + Math.floorMod(180 - y, 360) + ",0";
    }

    /** Writes the entries, models, textures and names. Returns the number of server states (4 per wood and kind). */
    public static int run(TfcCheckout tfc, OutputTree out) {
        checkSources(tfc);
        List<String> woods = woods(tfc);
        Map<String, Object> blocks = new LinkedHashMap<>();
        Map<String, Object> items = new LinkedHashMap<>();
        Map<String, Object> mappings = new TreeMap<>();
        int states = 0;
        for (Kind kind : KINDS) {
            String strength = strength(tfc, kind);
            for (String wood : woods) {
                String tool = tool(tfc, kind, wood);
                Map<String, Integer> turns = checkBlockstate(tfc, kind, wood);
                copyModels(tfc, out, kind, wood);
                DeviceBlockSpec spec = spec(kind, wood, strength, tool, turns);
                states += (int) spec.stateCount();
                merge(DeviceYaml.templated(spec), blocks, items, mappings);
            }
            String baseSrc = MODELS + "block/" + kind.name() + ".json";
            String base = tfc.readString(baseSrc);
            out.put(RP + "models/block/" + kind.name() + ".json", Json.write(Json.rename(Json.parse(base, baseSrc))));
            copyTextures(tfc, out, base, baseSrc);
        }
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
                throw new DatagenException("table carrier " + e.getKey() + " hidden as " + old + " and " + e.getValue());
            }
        }
    }

    /** TFC's state: FACING default north, one property; the placement facing the plugin's listeners write. */
    static void checkSources(TfcCheckout tfc) {
        for (String file : List.of("ScribingTableBlock.java", "SewingTableBlock.java")) {
            String src = tfc.readString(SOURCE + file).replaceAll("\\s+", " ");
            for (String needle : List.of("registerDefaultState(getStateDefinition().any().setValue(FACING, Direction.NORTH));",
                    "builder.add(FACING);")) {
                if (!src.contains(needle)) {
                    throw new DatagenException(SOURCE + file + " no longer contains \"" + needle + "\": re-check the table entries");
                }
            }
        }
    }

    /** TFC's woods: {@link HitboxDemands#BARREL_WOODS} and the blockstates of both kinds. */
    static List<String> woods(TfcCheckout tfc) {
        List<String> woods = HitboxDemands.BARREL_WOODS;
        for (Kind kind : KINDS) {
            String dir = TfcCheckout.ASSETS + "blockstates/wood/" + kind.name() + "/";
            TreeSet<String> states = new TreeSet<>();
            for (String f : tfc.list(dir)) {
                if (f.endsWith(".json")) {
                    states.add(f.substring(dir.length(), f.length() - ".json".length()));
                }
            }
            if (!states.equals(new TreeSet<>(woods))) {
                throw new DatagenException(dir + " holds " + states + ", not the woods " + woods);
            }
        }
        return woods;
    }

    /** TFC's hardness of {@code kind} (one value); the wood block sound must be wood. */
    static String strength(TfcCheckout tfc, Kind kind) {
        String src = tfc.readString(WOOD);
        Matcher m = Pattern.compile(kind.needle()).matcher(src);
        if (!m.find()) {
            throw new DatagenException(WOOD + ": the " + kind.name() + " registration no longer matches " + kind.needle());
        }
        Matcher s = WOOD_SOUND.matcher(src);
        if (!s.find() || !s.group(1).equals("WOOD")) {
            throw new DatagenException(WOOD + ": the wood block properties no longer use SoundType.WOOD");
        }
        return m.group(1);
    }

    /** TFC's mineable tag of the table of {@code wood} (exactly one). */
    static String tool(TfcCheckout tfc, Kind kind, String wood) {
        String block = Json.FROM_NS + ":wood/" + kind.name() + "/" + wood;
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

    /** TFC's variants: every facing draws the one model of the wood, turned about y. */
    static Map<String, Integer> checkBlockstate(TfcCheckout tfc, Kind kind, String wood) {
        String src = TfcCheckout.ASSETS + "blockstates/wood/" + kind.name() + "/" + wood + ".json";
        JsonObject variants = Json.parse(tfc.readString(src), src).getAsJsonObject().getAsJsonObject("variants");
        if (variants == null || variants.size() != FACINGS.size()) {
            throw new DatagenException(src + ": not one variant per facing");
        }
        Map<String, Integer> turns = new LinkedHashMap<>();
        for (String facing : FACINGS) {
            JsonObject v = variants.getAsJsonObject("facing=" + facing);
            String want = Json.FROM_NS + ":block/wood/" + kind.name() + "/" + wood;
            if (v == null || !want.equals(v.get("model").getAsString()) || v.has("x")) {
                throw new DatagenException(src + ": facing=" + facing + " no longer draws " + want + " turned about y only");
            }
            turns.put(facing, v.has("y") ? v.get("y").getAsInt() : 0);
        }
        return turns;
    }

    /** The demand of one appearance: the scribing table has one per facing, the sewing table one for all. */
    static String appearance(Kind kind, String facing) {
        return kind == SCRIBING ? facing : HitboxDemands.SEWING_APPEARANCE;
    }

    static DeviceBlockSpec spec(Kind kind, String wood, String strength, String tool, Map<String, Integer> turns) {
        String id = kind.id(wood);
        DeviceBlockSpec.Builder b = DeviceBlockSpec.builder(id).property(DeviceProperty.names(FACING, FACINGS, "north"));
        Map<String, String> hidden = new LinkedHashMap<>();
        for (String facing : FACINGS) {
            HitboxAllocation.Assignment a = carrier(id, appearance(kind, facing));
            b.appearance(DeviceAppearance.rendered(facing, DeviceAppearance.Carrier.state(a.state()),
                List.of(DeviceAppearance.Renderer.item(renderItem(kind.name(), wood)).rotated(rotation(turns.get(facing))))));
            if (a.hideTarget() != null) {
                hidden.putIfAbsent(a.state(), a.hideTarget());
            }
        }
        b.everyState(s -> s.get(FACING), null)
            .settings(DeviceBlockSpec.Settings.of(strength, strength, DeviceSound.WOOD, tool))
            .loot(DeviceLoot.self())
            .blockItem(kind.langKey(wood))
            .renderItem(renderItem(kind.name(), wood), model(kind.name(), wood));
        hidden.forEach(b::stateMapping);
        return b.build();
    }

    /** The carrier of one appearance; the tables' are hitbox-only (the look is the item display). */
    static HitboxAllocation.Assignment carrier(String id, String appearance) {
        HitboxAllocation.Assignment a = HitboxAllocation.agreed(id, appearance);
        if (a.mode() != HitboxAllocation.Mode.HITBOX_ONLY) {
            throw new DatagenException("the table carrier of " + id + "#" + appearance + " is " + a.mode()
                + ", TableBlocks draws a transparent carrier");
        }
        return a;
    }

    /** TFC's per-wood block model and item model, renamed; and the textures they name. */
    static void copyModels(TfcCheckout tfc, OutputTree out, Kind kind, String wood) {
        String path = "block/wood/" + kind.name() + "/" + wood;
        String src = MODELS + path + ".json";
        String text = tfc.readString(src);
        JsonObject m = Json.parse(text, src).getAsJsonObject();
        if (!(Json.FROM_NS + ":block/" + kind.name()).equals(m.get("parent").getAsString())) {
            throw new DatagenException(src + ": parent is not tfc:block/" + kind.name());
        }
        out.put(RP + "models/" + path + ".json", Json.write(Json.rename(m)));
        copyTextures(tfc, out, text, src);
        String isrc = MODELS + "item/wood/" + kind.name() + "/" + wood + ".json";
        JsonObject item = Json.parse(tfc.readString(isrc), isrc).getAsJsonObject();
        if (!(Json.FROM_NS + ":block/wood/" + kind.name() + "/" + wood).equals(item.get("parent").getAsString())) {
            throw new DatagenException(isrc + " is no longer the block model as parent");
        }
        out.put(RP + "models/item/wood/" + kind.name() + "/" + wood + ".json", Json.write(Json.rename(item)));
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

    /** {@code block.tfp.wood.<kind>.<wood>} in every TFC locale that names them (English must name all). */
    static void writeLang(TfcCheckout tfc, OutputTree out, List<String> woods) {
        for (String f : tfc.list(LANG)) {
            if (!f.endsWith(".json")) {
                continue;
            }
            String locale = f.substring(LANG.length(), f.length() - ".json".length());
            JsonObject src = Json.parse(tfc.readString(f), f).getAsJsonObject();
            Map<String, String> keys = new TreeMap<>();
            for (Kind kind : KINDS) {
                for (String wood : woods) {
                    String tfcKey = "block." + Json.FROM_NS + ".wood." + kind.name() + "." + wood;
                    if (src.has(tfcKey)) {
                        keys.put(kind.langKey(wood), src.get(tfcKey).getAsString());
                    } else if (locale.equals("en_us")) {
                        throw new DatagenException("TFC has no English name " + tfcKey);
                    }
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
