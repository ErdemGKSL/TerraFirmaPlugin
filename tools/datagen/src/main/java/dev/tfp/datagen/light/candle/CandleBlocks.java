/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/TFCBlocks.java @ c3b4d9536 (CANDLE, DYED_CANDLE, CANDLE_CAKE,
 *       DYED_CANDLE_CAKES, JACK_O_LANTERN: strength, sound, light),
 *       src/main/java/net/dries007/tfc/config/ServerConfig.java @ c3b4d9536 (candleTicks 288000, jackOLanternTicks 108000),
 *       src/main/resources/assets/tfc/blockstates/{candle,candle_cake,jack_o_lantern}.json and
 *       blockstates/{candle,candle_cake}/*.json @ c3b4d9536 (the vanilla models per state),
 *       src/main/resources/assets/tfc/models/item/{candle,jack_o_lantern}.json and models/item/candle/*.json @ c3b4d9536
 *       (copied),
 *       src/main/resources/data/tfc/loot_table/blocks/{candle,candle_cake,jack_o_lantern}.json and
 *       loot_table/blocks/{candle,candle_cake}/*.json @ c3b4d9536 (shipped for the loot runtime),
 *       src/main/resources/assets/tfc/lang/*.json @ c3b4d9536 (block.tfc.candle*, block.tfc.jack_o_lantern)
 */
package dev.tfp.datagen.light.candle;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.tfp.datagen.DatagenException;
import dev.tfp.datagen.Json;
import dev.tfp.datagen.LootTableExport;
import dev.tfp.datagen.OutputTree;
import dev.tfp.datagen.TfcCheckout;
import dev.tfp.datagen.Yaml;
import dev.tfp.datagen.device.DeviceSound;
import dev.tfp.datagen.device.DeviceYaml;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * PAR-HEAT-11: TFC's 17 candles, 17 candle cakes and the jack-o'-lantern as CraftEngine content ({@value #OUTPUT}); the
 * plugin's {@code paper/light/candle} runs them (all are {@code tfp:device} blocks holding the counter).
 *
 * <ul>
 *   <li>Candles ({@code tfp:candle}, {@code tfp:candle/<color>}): {@code candles} 1-4, {@code lit}, {@code waterlogged}
 *       (CraftEngine's hardcoded waterlogging, {@code fluid_state: water} on the waterlogged variants);
 *       {@code stackable_block} on {@code candles} with the candle's own item (vanilla {@code CandleBlock.canBeReplaced}),
 *       {@code sturdy_base_block} centre support (vanilla {@code canSupportCenter}); strength 0.1, candle sounds, light
 *       {@code 3 * candles + 2} while lit (TFC {@code LIGHTING_SCALE}).</li>
 *   <li>Candle cakes ({@code tfp:candle_cake}, {@code tfp:candle_cake/<color>}): {@code lit}; full face support below
 *       (TFC {@code isFaceSturdy(UP)}); strength 0.5, wool sounds, light 3 while lit; the plain one copies vanilla's
 *       candle cake (push reaction destroy), the dyed ones do not. No item (TFC {@code registerNoItem}).</li>
 *   <li>Jack-o'-lantern: {@code facing}; strength 1.0, wood sounds, light 15.</li>
 *   <li>Loot: TFC's tables shipped for the loot runtime. No CraftEngine {@code loot:}.</li>
 *   <li>Looks: TFC draws vanilla's models for all of them. The carriers come in as arguments ({@link Carriers}): the
 *       hitbox carrier selector chooses them; a carrier with {@link Look#MODEL} gets TFC's blockstate model.</li>
 * </ul>
 *
 * Wiring this generator into {@code Datagen} needs the demands in {@code hitbox/HitboxDemands} and the ids in
 * {@code GeneratedCarrierTest}, which PAR-HEAT-26 owns.
 */
public final class CandleBlocks {
    public static final String CANDLE = "tfp:candle";
    public static final String CANDLE_CAKE = "tfp:candle_cake";
    public static final String JACK_O_LANTERN = "tfp:jack_o_lantern";
    /** Vanilla {@code DyeColor} order (TFC {@code Helpers.mapOf(DyeColor.class, ...)}). */
    public static final List<String> COLORS = List.of("white", "orange", "magenta", "light_blue", "yellow", "lime", "pink",
        "gray", "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black");
    public static final List<String> FACINGS = List.of("north", "east", "south", "west");
    /** TFC {@code candleTicks} default. */
    public static final int CANDLE_TICKS = 288_000;
    /** TFC {@code jackOLanternTicks} default. */
    public static final int JACK_O_LANTERN_TICKS = 108_000;
    /** TFC candle cake {@code litBlockEmission(3)}. */
    public static final int CANDLE_CAKE_LIGHT = 3;
    /** TFC {@code alwaysLit()}. */
    public static final int JACK_O_LANTERN_LIGHT = 15;

    static final String BLOCKS_SRC = "src/main/java/net/dries007/tfc/common/blocks/TFCBlocks.java";
    static final String CONFIG_SRC = "src/main/java/net/dries007/tfc/config/ServerConfig.java";
    static final String ASSETS = TfcCheckout.ASSETS;
    static final String LANG = ASSETS + "lang/";
    static final String PACK = "craftengine/tfp/";
    public static final String OUTPUT = PACK + "configuration/light/candle/candle.yml";
    static final String RP = PACK + "resourcepack/assets/tfp/";
    static final String LANG_RP = PACK + "resourcepack/assets/tfp_candle/lang/";

    /** How a carrier is drawn: its own vanilla look, or TFC's (vanilla) model on it. */
    public enum Look {
        VANILLA, MODEL
    }

    /** The selector's carrier of one appearance. */
    public record Carrier(String state, Look look) {
        public Carrier {
            Objects.requireNonNull(state, "state");
            Objects.requireNonNull(look, "look");
        }
    }

    /**
     * The carrier of {@code appearance} of {@code blockId}: {@code candles=N,lit=B,waterlogged=W} for a candle,
     * {@code lit=B} for a candle cake, the facing for the jack-o'-lantern.
     */
    @FunctionalInterface
    public interface Carriers {
        Carrier of(String blockId, String appearance);
    }

    private CandleBlocks() {
    }

    /** Every candle block id: the plain one, then the dyed ones. */
    public static List<String> candles() {
        return withColors(CANDLE);
    }

    /** Every candle cake block id, in the order of {@link #candles()}. */
    public static List<String> candleCakes() {
        return withColors(CANDLE_CAKE);
    }

    private static List<String> withColors(String base) {
        List<String> ids = new ArrayList<>();
        ids.add(base);
        COLORS.forEach(c -> ids.add(base + "/" + c));
        return List.copyOf(ids);
    }

    /** Every block this generator writes. */
    public static List<String> blocks() {
        List<String> ids = new ArrayList<>(candles());
        ids.addAll(candleCakes());
        ids.add(JACK_O_LANTERN);
        return List.copyOf(ids);
    }

    /** True when the checkout has the candles (the small fixtures do not). */
    public static boolean available(TfcCheckout tfc) {
        return tfc.exists(BLOCKS_SRC) && tfc.exists(ASSETS + "blockstates/candle.json");
    }

    /** Writes the entries, item models, loot and names. Returns the number of server states (17 * 16 + 17 * 2 + 4). */
    public static int run(TfcCheckout tfc, OutputTree out, Carriers carriers) {
        checkSource(tfc);
        out.put(OUTPUT, Yaml.write(DeviceYaml.HEADER, yaml(tfc, carriers)));
        copyItemModels(tfc, out);
        delegateLoot(tfc, out);
        writeLang(tfc, out);
        return candles().size() * 16 + candleCakes().size() * 2 + FACINGS.size();
    }

    /** The registrations and the config defaults this generator writes, read from TFC so a change there fails here. */
    static void checkSource(TfcCheckout tfc) {
        String blocks = tfc.readString(BLOCKS_SRC).replaceAll("\\s+", " ");
        Map<String, List<String>> needles = new LinkedHashMap<>();
        needles.put("JACK_O_LANTERN = register(\"jack_o_lantern\", () -> new JackOLanternBlock(", List.of("strength(1.0F)",
            "sound(SoundType.WOOD)", "lightLevel(alwaysLit())", "blockEntity(TFCBlockEntities.TICK_COUNTER)",
            "() -> Blocks.CARVED_PUMPKIN"));
        needles.put("CANDLE_CAKE = registerNoItem(\"candle_cake\", () -> new TFCCandleCakeBlock(", List.of("strength(0.5f)",
            "sound(SoundType.WOOL)", "lightLevel(litBlockEmission(" + CANDLE_CAKE_LIGHT + "))",
            "blockEntity(TFCBlockEntities.TICK_COUNTER)"));
        needles.put("CANDLE = register(\"candle\", () -> new TFCCandleBlock(", List.of("strength(0.1F)",
            "sound(SoundType.CANDLE)", "lightLevel(TFCCandleBlock.LIGHTING_SCALE)", "blockEntity(TFCBlockEntities.TICK_COUNTER)",
            "new CandleBlockItem(new Item.Properties(), b, TFCBlocks.CANDLE_CAKE)"));
        needles.put("registerNoItem(\"candle_cake/\" + color.getName(), () -> new TFCCandleCakeBlock(", List.of(
            "strength(0.5F)", "sound(SoundType.WOOL)", "lightLevel(litBlockEmission(" + CANDLE_CAKE_LIGHT + "))"));
        needles.put("register(\"candle/\" + color.getName(), () -> new TFCCandleBlock(", List.of("strength(0.1F)",
            "sound(SoundType.CANDLE)", "lightLevel(TFCCandleBlock.LIGHTING_SCALE)",
            "TFCBlocks.DYED_CANDLE_CAKES.get(color)"));
        for (Map.Entry<String, List<String>> e : needles.entrySet()) {
            int at = blocks.indexOf(e.getKey());
            if (at < 0) {
                throw new DatagenException(BLOCKS_SRC + " no longer registers \"" + e.getKey() + "\"");
            }
            int end = blocks.indexOf(';', at);
            String line = blocks.substring(at, end < 0 ? blocks.length() : end);
            for (String n : e.getValue()) {
                if (!line.contains(n)) {
                    throw new DatagenException(BLOCKS_SRC + ": " + e.getKey() + " no longer has " + n);
                }
            }
        }
        String config = tfc.readString(CONFIG_SRC);
        if (!config.contains(".define(\"candleTicks\", " + CANDLE_TICKS + ", -1, Integer.MAX_VALUE)")) {
            throw new DatagenException(CONFIG_SRC + ": the candleTicks default is no longer " + CANDLE_TICKS);
        }
        if (!config.contains(".define(\"jackOLanternTicks\", " + JACK_O_LANTERN_TICKS + ", -1, Integer.MAX_VALUE)")) {
            throw new DatagenException(CONFIG_SRC + ": the jackOLanternTicks default is no longer " + JACK_O_LANTERN_TICKS);
        }
    }

    /** {@code blocks} and {@code items}. */
    static Map<String, Object> yaml(TfcCheckout tfc, Carriers carriers) {
        Map<String, Object> blocks = new LinkedHashMap<>();
        Map<String, Object> items = new LinkedHashMap<>();
        for (String id : candles()) {
            blocks.put(id, candle(tfc, id, carriers));
            items.put(id, item(id));
        }
        for (String id : candleCakes()) {
            blocks.put(id, candleCake(tfc, id, carriers));
        }
        blocks.put(JACK_O_LANTERN, jack(tfc, carriers));
        items.put(JACK_O_LANTERN, item(JACK_O_LANTERN));
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("blocks", blocks);
        root.put("items", items);
        return root;
    }

    /** The path of {@code id} ({@code tfp:candle/red} → {@code candle/red}). */
    static String path(String id) {
        return id.substring(id.indexOf(':') + 1);
    }

    /** TFC's blockstate variants of {@code id}: the variant key to its {@code model} and {@code y}. */
    static Map<String, JsonObject> variants(TfcCheckout tfc, String id) {
        String src = ASSETS + "blockstates/" + path(id) + ".json";
        JsonObject v = Json.parse(tfc.readString(src), src).getAsJsonObject().getAsJsonObject("variants");
        if (v == null) {
            throw new DatagenException(src + " has no variants");
        }
        Map<String, JsonObject> out = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> e : v.entrySet()) {
            JsonObject m = e.getValue().getAsJsonObject();
            String model = m.get("model").getAsString();
            if (!model.startsWith("minecraft:")) {
                throw new DatagenException(src + " no longer draws vanilla's models: " + model);
            }
            out.put(e.getKey(), m);
        }
        return out;
    }

    private static Map<String, Object> appearance(String block, String app, JsonObject variant, Carriers carriers) {
        Carrier c = carriers.of(block, app);
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("state", c.state());
        if (c.look() == Look.MODEL) {
            int y = variant.has("y") ? variant.get("y").getAsInt() : 0;
            if (y != 0) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("path", variant.get("model").getAsString());
                m.put("y", y);
                a.put("model", m);
            } else {
                a.put("model", variant.get("model").getAsString());
            }
        }
        return a;
    }

    private static Map<String, Object> settings(String hardness, Map<String, Object> sounds, String item) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("hardness", new BigDecimal(hardness));
        s.put("resistance", new BigDecimal(hardness));
        s.put("sounds", sounds);
        s.put("push_reaction", "destroy");
        if (item != null) {
            s.put("item", item);
        }
        return s;
    }

    private static Map<String, Object> bool(boolean def) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("type", "boolean");
        p.put("default", def);
        return p;
    }

    private static Map<String, Object> support(String type) {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("type", "sturdy_base_block");
        b.put("direction", "down");
        b.put("support_types", List.of(type));
        return b;
    }

    /**
     * Vanilla {@code SoundType.CANDLE}, {@code WOOL} and {@code WOOD}: all (1.0, 1.0), so the plain ids, whose CraftEngine
     * defaults play exactly that ({@link DeviceSound#plainIds}).
     */
    static Map<String, Object> vanilla(String group) {
        String p = "minecraft:block." + group + ".";
        Map<String, Object> m = new LinkedHashMap<>();
        for (String e : List.of("break", "step", "place", "hit", "fall")) {
            m.put(e, p + e);
        }
        return m;
    }

    private static Map<String, Object> candle(TfcCheckout tfc, String id, Carriers carriers) {
        Map<String, JsonObject> tfcVariants = variants(tfc, id);
        Map<String, Object> block = new LinkedHashMap<>();
        block.put("settings", settings("0.1", vanilla("candle"), id));
        Map<String, Object> stack = new LinkedHashMap<>();
        stack.put("type", "stackable_block");
        stack.put("property", "candles");
        stack.put("items", List.of(id));
        block.put("behavior", List.of(Map.of("type", "tfp:device"), stack, support("center")));
        Map<String, Object> props = new LinkedHashMap<>();
        Map<String, Object> candles = new LinkedHashMap<>();
        candles.put("type", "int");
        candles.put("range", "1~4");
        candles.put("default", 1);
        props.put("candles", candles);
        props.put("lit", bool(false));
        props.put("waterlogged", bool(false));
        Map<String, Object> appearances = new LinkedHashMap<>();
        Map<String, Object> variants = new LinkedHashMap<>();
        for (int n = 1; n <= 4; n++) {
            for (boolean lit : new boolean[] {false, true}) {
                JsonObject v = tfcVariants.get("candles=" + n + ",lit=" + lit);
                if (v == null) {
                    throw new DatagenException("TFC's " + id + " blockstate has no candles=" + n + ",lit=" + lit);
                }
                for (boolean wet : new boolean[] {false, true}) {
                    String key = "candles=" + n + ",lit=" + lit + ",waterlogged=" + wet;
                    appearances.put(key, appearance(id, key, v, carriers));
                    Map<String, Object> s = new LinkedHashMap<>();
                    s.put("luminance", lit ? 3 * n + 2 : 0);
                    if (wet) {
                        s.put("fluid_state", "water");
                    }
                    Map<String, Object> variant = new LinkedHashMap<>();
                    variant.put("appearance", key);
                    variant.put("settings", s);
                    variants.put(key, variant);
                }
            }
        }
        block.put("states", states(props, appearances, variants));
        return block;
    }

    private static Map<String, Object> candleCake(TfcCheckout tfc, String id, Carriers carriers) {
        Map<String, JsonObject> tfcVariants = variants(tfc, id);
        Map<String, Object> block = new LinkedHashMap<>();
        Map<String, Object> s = settings("0.5", vanilla("wool"), null);
        if (!id.equals(CANDLE_CAKE)) {
            s.put("push_reaction", "normal"); // TFC: ExtendedProperties.of(MapColor.SAND), no copy of the candle cake
        }
        block.put("settings", s);
        block.put("behavior", List.of(Map.of("type", "tfp:device"), support("full")));
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("lit", bool(false));
        Map<String, Object> appearances = new LinkedHashMap<>();
        Map<String, Object> variants = new LinkedHashMap<>();
        for (boolean lit : new boolean[] {false, true}) {
            String key = "lit=" + lit;
            JsonObject v = tfcVariants.get(key);
            if (v == null) {
                throw new DatagenException("TFC's " + id + " blockstate has no " + key);
            }
            appearances.put(key, appearance(id, key, v, carriers));
            Map<String, Object> variant = new LinkedHashMap<>();
            variant.put("appearance", key);
            variant.put("settings", Map.of("luminance", lit ? CANDLE_CAKE_LIGHT : 0));
            variants.put(key, variant);
        }
        block.put("states", states(props, appearances, variants));
        return block;
    }

    private static Map<String, Object> jack(TfcCheckout tfc, Carriers carriers) {
        Map<String, JsonObject> tfcVariants = variants(tfc, JACK_O_LANTERN);
        Map<String, Object> block = new LinkedHashMap<>();
        Map<String, Object> s = settings("1.0", DeviceSound.WOOD.yaml(), JACK_O_LANTERN);
        s.put("push_reaction", "normal");
        s.put("luminance", JACK_O_LANTERN_LIGHT);
        block.put("settings", s);
        block.put("behavior", List.of(Map.of("type", "tfp:device")));
        Map<String, Object> facing = new LinkedHashMap<>();
        facing.put("type", "horizontal_direction");
        facing.put("default", "north");
        Map<String, Object> appearances = new LinkedHashMap<>();
        Map<String, Object> variants = new LinkedHashMap<>();
        for (String f : FACINGS) {
            JsonObject v = tfcVariants.get("facing=" + f);
            if (v == null) {
                throw new DatagenException("TFC's jack_o_lantern blockstate has no facing=" + f);
            }
            appearances.put(f, appearance(JACK_O_LANTERN, f, v, carriers));
            variants.put("facing=" + f, Map.of("appearance", f));
        }
        block.put("states", states(Map.of("facing", facing), appearances, variants));
        return block;
    }

    private static Map<String, Object> states(Map<String, Object> props, Map<String, Object> appearances,
                                              Map<String, Object> variants) {
        Map<String, Object> states = new LinkedHashMap<>();
        states.put("properties", props);
        states.put("appearances", appearances);
        states.put("variants", variants);
        return states;
    }

    private static Map<String, Object> item(String id) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("material", "paper");
        item.put("data", Map.of("item_name", "<!i><lang:block." + Json.TO_NS + "." + path(id).replace('/', '.') + ">"));
        item.put("model", Json.TO_NS + ":item/" + path(id));
        item.put("behavior", List.of(Map.of("type", "block_item", "block", id)));
        return item;
    }

    /** TFC's item models (vanilla parents), renamed. */
    static void copyItemModels(TfcCheckout tfc, OutputTree out) {
        List<String> ids = new ArrayList<>(candles());
        ids.add(JACK_O_LANTERN);
        for (String id : ids) {
            String src = ASSETS + "models/item/" + path(id) + ".json";
            JsonObject model = Json.parse(tfc.readString(src), src).getAsJsonObject();
            out.put(RP + "models/item/" + path(id) + ".json", Json.write(Json.rename(model)));
        }
    }

    /** TFC's block tables (each candle drops 1-4 of itself, a candle cake its candle, the jack a carved pumpkin). */
    static void delegateLoot(TfcCheckout tfc, OutputTree out) {
        for (String id : blocks()) {
            String src = TfcCheckout.MAIN + "data/" + Json.FROM_NS + "/loot_table/blocks/" + path(id) + ".json";
            JsonElement table = Json.rename(Json.parse(tfc.readString(src), src));
            LootTableExport.delegateBlock(out, path(id), table, named -> { });
        }
    }

    /** The block names in every TFC locale that has them. */
    static void writeLang(TfcCheckout tfc, OutputTree out) {
        for (String f : tfc.list(LANG)) {
            if (!f.endsWith(".json")) {
                continue;
            }
            String locale = f.substring(LANG.length(), f.length() - ".json".length());
            JsonObject src = Json.parse(tfc.readString(f), f).getAsJsonObject();
            JsonObject o = new JsonObject();
            for (String block : blocks()) {
                String key = path(block).replace('/', '.');
                if (src.has("block." + Json.FROM_NS + "." + key)) {
                    o.addProperty("block." + Json.TO_NS + "." + key, src.get("block." + Json.FROM_NS + "." + key).getAsString());
                } else if (locale.equals("en_us")) {
                    throw new DatagenException("TFC has no English name block.tfc." + key);
                }
            }
            if (o.size() > 0) {
                out.put(LANG_RP + locale + ".json", Json.write(o));
            }
        }
    }
}
