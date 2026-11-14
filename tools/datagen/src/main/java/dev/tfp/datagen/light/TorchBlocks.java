/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/TFCBlocks.java @ c3b4d9536 (TORCH, WALL_TORCH, DEAD_TORCH,
 *       DEAD_WALL_TORCH: noCollission, instabreak, lightLevel 14, SoundType.WOOD, PushReaction.DESTROY, dropsLike,
 *       cloneItem),
 *       src/main/java/net/dries007/tfc/common/items/TFCItems.java @ c3b4d9536 (TORCH, DEAD_TORCH: standing and wall
 *       block items, Direction.DOWN),
 *       src/main/java/net/dries007/tfc/config/ServerConfig.java @ c3b4d9536 (torchTicks 86400),
 *       src/main/resources/assets/tfc/blockstates/{torch,wall_torch,dead_torch,dead_wall_torch}.json,
 *       models/block/dead_torch.json, models/block/dead_wall_torch.json, models/item/{torch,dead_torch}.json and
 *       textures/block/torch_off.png @ c3b4d9536 (copied),
 *       src/main/resources/data/tfc/loot_table/blocks/{torch,dead_torch}.json @ c3b4d9536 (shipped for the loot runtime),
 *       src/main/resources/assets/tfc/lang/*.json @ c3b4d9536 (block.tfc.torch, block.tfc.dead_torch)
 */
package dev.tfp.datagen.light;

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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * PAR-HEAT-10: TFC's four torch blocks and two torch items as CraftEngine content
 * ({@value #OUTPUT}); the plugin's {@code paper/light} runs them (the lit torches are {@code tfp:device} blocks holding
 * the counter, the dead ones plain blocks).
 *
 * <ul>
 *   <li>{@code tfp:torch} / {@code tfp:dead_torch}: standing, CraftEngine's {@code sturdy_base_block} (centre support
 *       below, TFC {@code TorchBlock.canSurvive}) and {@code liquid_flowable_block} (water washes them away with their
 *       drop, as vanilla).</li>
 *   <li>{@code tfp:wall_torch} / {@code tfp:dead_wall_torch}: {@code facing} north/east/south/west,
 *       {@code directional_attached_block} (a sturdy face behind) and {@code liquid_flowable_block}.</li>
 *   <li>Settings: TFC's {@code instabreak} (hardness and resistance 0), wood sounds, {@code PushReaction.DESTROY}, light
 *       14 on the lit ones, pick-block {@code tfp:torch} (TFC {@code cloneItem(TORCH)} on the dead ones).</li>
 *   <li>Loot: TFC's tables shipped for the loot runtime (the torch drops itself, or with {@code burnt_out} a stick or wood
 *       ash at 25 %; the dead torch a stick at 50 %); the wall blocks get their standing block's table (TFC
 *       {@code dropsLike}). No CraftEngine {@code loot:}.</li>
 *   <li>Items: {@code tfp:torch} and {@code tfp:dead_torch} place the wall block on a side face and the standing block
 *       otherwise (TFC {@code StandingAndWallBlockItem}, CraftEngine {@code wall_block_item} then {@code block_item}).</li>
 * </ul>
 *
 * The carriers come in as arguments ({@link Carriers}): the hitbox carrier selector chooses them (PAR-INFRA-13). Wiring
 * this generator into {@code Datagen} needs the torch demands in {@code hitbox/HitboxDemands} and the ids in
 * {@code GeneratedCarrierTest}, which PAR-HEAT-21 owns.
 */
public final class TorchBlocks {
    public static final String TORCH = "tfp:torch";
    public static final String WALL_TORCH = "tfp:wall_torch";
    public static final String DEAD_TORCH = "tfp:dead_torch";
    public static final String DEAD_WALL_TORCH = "tfp:dead_wall_torch";
    public static final List<String> BLOCKS = List.of(TORCH, WALL_TORCH, DEAD_TORCH, DEAD_WALL_TORCH);
    /** The appearance of a standing torch. */
    public static final String STANDING = "standing";
    /** The wall facings in TFC's blockstate order; the appearance of a wall torch is its facing. */
    public static final List<String> FACINGS = List.of("north", "east", "south", "west");
    /** TFC {@code lightLevel(state -> 14)} on the lit torches. */
    public static final int LIGHT = 14;
    /** TFC {@code torchTicks} default. */
    public static final int TORCH_TICKS = 86_400;

    static final String BLOCKS_SRC = "src/main/java/net/dries007/tfc/common/blocks/TFCBlocks.java";
    static final String ITEMS_SRC = "src/main/java/net/dries007/tfc/common/items/TFCItems.java";
    static final String CONFIG_SRC = "src/main/java/net/dries007/tfc/config/ServerConfig.java";
    static final String ASSETS = TfcCheckout.ASSETS;
    static final String LANG = ASSETS + "lang/";
    static final String PACK = "craftengine/tfp/";
    public static final String OUTPUT = PACK + "configuration/light/torch.yml";
    static final String RP = PACK + "resourcepack/assets/tfp/";
    static final String LANG_RP = PACK + "resourcepack/assets/tfp_torch/lang/";
    static final String LOOT = "data/" + Json.TO_NS + "/loot_table/blocks/";

    /** How a carrier is drawn: its own vanilla look, our block model on it, or empty with an item display. */
    public enum Look {
        VANILLA, MODEL, RENDERED
    }

    /** The selector's carrier of one appearance. */
    public record Carrier(String state, Look look) {
        public Carrier {
            Objects.requireNonNull(state, "state");
            Objects.requireNonNull(look, "look");
        }
    }

    /** The carrier of {@code appearance} ({@link #STANDING} or a facing) of {@code blockId}. */
    @FunctionalInterface
    public interface Carriers {
        Carrier of(String blockId, String appearance);
    }

    private TorchBlocks() {
    }

    /** True when the checkout has the torches (the small fixtures do not). */
    public static boolean available(TfcCheckout tfc) {
        return tfc.exists(BLOCKS_SRC) && tfc.exists(ASSETS + "models/block/dead_torch.json");
    }

    /** Writes the entry, models, texture, loot and names. Returns the number of server states (10). */
    public static int run(TfcCheckout tfc, OutputTree out, Carriers carriers) {
        checkSource(tfc);
        out.put(OUTPUT, Yaml.write(DeviceYaml.HEADER, yaml(carriers)));
        copyAssets(tfc, out);
        delegateLoot(tfc, out);
        writeLang(tfc, out);
        return 2 * (1 + FACINGS.size());
    }

    /** The registrations and the config default this generator writes, read from TFC so a change there fails here. */
    static void checkSource(TfcCheckout tfc) {
        String blocks = tfc.readString(BLOCKS_SRC).replaceAll("\\s+", " ");
        Map<String, List<String>> needles = new LinkedHashMap<>();
        needles.put("TORCH = registerNoItem(\"torch\", () -> new TFCTorchBlock(", List.of("noCollission()", "instabreak()",
            "lightLevel(state -> " + LIGHT + ")", "sound(SoundType.WOOD)", "blockEntity(TFCBlockEntities.TICK_COUNTER)",
            "pushReaction(PushReaction.DESTROY)"));
        needles.put("WALL_TORCH = registerNoItem(\"wall_torch\", () -> new TFCWallTorchBlock(", List.of("noCollission()",
            "instabreak()", "lightLevel(state -> " + LIGHT + ")", "sound(SoundType.WOOD)", "dropsLike(TORCH)",
            "blockEntity(TFCBlockEntities.TICK_COUNTER)", "pushReaction(PushReaction.DESTROY)"));
        needles.put("DEAD_TORCH = registerNoItem(\"dead_torch\", () -> new DeadTorchBlock(", List.of("noCollission()",
            "instabreak()", "cloneItem(TFCBlocks.TORCH)", "sound(SoundType.WOOD)", "pushReaction(PushReaction.DESTROY)"));
        needles.put("DEAD_WALL_TORCH = registerNoItem(\"dead_wall_torch\", () -> new DeadWallTorchBlock(", List.of(
            "noCollission()", "instabreak()", "sound(SoundType.WOOD)", "cloneItem(TFCBlocks.TORCH)", "dropsLike(DEAD_TORCH)",
            "pushReaction(PushReaction.DESTROY)"));
        for (Map.Entry<String, List<String>> e : needles.entrySet()) {
            int at = blocks.indexOf(e.getKey());
            if (at < 0) {
                throw new DatagenException(BLOCKS_SRC + " no longer registers \"" + e.getKey() + "\"");
            }
            String line = blocks.substring(at, blocks.indexOf(';', at));
            for (String n : e.getValue()) {
                if (!line.contains(n)) {
                    throw new DatagenException(BLOCKS_SRC + ": " + e.getKey() + " no longer has " + n);
                }
            }
        }
        String items = tfc.readString(ITEMS_SRC).replaceAll("\\s+", " ");
        for (String n : List.of(
                "TORCH = register(\"torch\", () -> new TorchItem(TFCBlocks.TORCH.get(), TFCBlocks.WALL_TORCH.get(), new Properties()));",
                "DEAD_TORCH = register(\"dead_torch\", () -> new StandingAndWallBlockItem(TFCBlocks.DEAD_TORCH.get(), "
                    + "TFCBlocks.DEAD_WALL_TORCH.get(), new Properties(), Direction.DOWN));")) {
            if (!items.contains(n)) {
                throw new DatagenException(ITEMS_SRC + " no longer contains \"" + n + "\"");
            }
        }
        String config = tfc.readString(CONFIG_SRC);
        if (!config.contains(".define(\"torchTicks\", " + TORCH_TICKS + ", -1, Integer.MAX_VALUE)")) {
            throw new DatagenException(CONFIG_SRC + ": the torchTicks default is no longer " + TORCH_TICKS);
        }
    }

    /** {@code blocks} and {@code items} of the four torch blocks. */
    static Map<String, Object> yaml(Carriers carriers) {
        Map<String, Object> blocks = new LinkedHashMap<>();
        blocks.put(TORCH, standing(TORCH, true, carriers));
        blocks.put(WALL_TORCH, wall(WALL_TORCH, true, carriers));
        blocks.put(DEAD_TORCH, standing(DEAD_TORCH, false, carriers));
        blocks.put(DEAD_WALL_TORCH, wall(DEAD_WALL_TORCH, false, carriers));
        Map<String, Object> items = new LinkedHashMap<>();
        items.put(TORCH, item(TORCH, WALL_TORCH, TORCH));
        items.put(DEAD_TORCH, item(DEAD_TORCH, DEAD_WALL_TORCH, DEAD_TORCH));
        for (String block : List.of(DEAD_TORCH, DEAD_WALL_TORCH)) {
            for (String app : block.equals(DEAD_TORCH) ? List.of(STANDING) : FACINGS) {
                if (carriers.of(block, app).look() == Look.RENDERED) {
                    Map<String, Object> render = new LinkedHashMap<>();
                    render.put("template", "tfp:item/render");
                    render.put("arguments", Map.of("render_model", model(block)));
                    items.putIfAbsent(renderItem(block), render);
                }
            }
        }
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("blocks", blocks);
        root.put("items", items);
        return root;
    }

    /** The block model of a torch block ({@code minecraft:block/torch}'s look on the lit ones). */
    static String model(String block) {
        return switch (block) {
            case TORCH -> "minecraft:block/torch";
            case WALL_TORCH -> "minecraft:block/wall_torch";
            case DEAD_TORCH -> Json.TO_NS + ":block/dead_torch";
            case DEAD_WALL_TORCH -> Json.TO_NS + ":block/dead_wall_torch";
            default -> throw new DatagenException("not a torch block: " + block);
        };
    }

    /** The display item of a hitbox-only torch look. */
    public static String renderItem(String block) {
        return Json.TO_NS + ":render/" + block.substring(block.indexOf(':') + 1);
    }

    /** TFC's wall torch blockstate rotation of {@code facing} (east 0, south 90, west 180, north 270). */
    static int blockstateY(String facing) {
        return switch (facing) {
            case "east" -> 0;
            case "south" -> 90;
            case "west" -> 180;
            case "north" -> 270;
            default -> throw new DatagenException("not a horizontal facing: " + facing);
        };
    }

    private static Map<String, Object> settings(boolean lit) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("hardness", java.math.BigDecimal.ZERO);
        s.put("resistance", java.math.BigDecimal.ZERO);
        s.put("sounds", DeviceSound.WOOD.yaml());
        s.put("push_reaction", "destroy");
        s.put("replaceable", false);
        if (lit) {
            s.put("luminance", LIGHT);
        }
        s.put("item", TORCH);
        return s;
    }

    private static List<Object> behaviours(boolean lit, boolean wall) {
        List<Object> b = new java.util.ArrayList<>();
        if (lit) {
            b.add(Map.of("type", "tfp:device"));
        }
        if (wall) {
            b.add(Map.of("type", "directional_attached_block"));
        } else {
            Map<String, Object> sturdy = new LinkedHashMap<>();
            sturdy.put("type", "sturdy_base_block");
            sturdy.put("direction", "down");
            sturdy.put("support_types", List.of("center"));
            b.add(sturdy);
        }
        b.add(Map.of("type", "liquid_flowable_block"));
        return b;
    }

    private static Map<String, Object> appearance(String block, String app, Carriers carriers, Integer facingY) {
        Carrier c = carriers.of(block, app);
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("state", c.state());
        switch (c.look()) {
            case VANILLA -> {
            }
            case MODEL -> {
                if (facingY != null && facingY != 0) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("path", model(block));
                    m.put("y", facingY);
                    a.put("model", m);
                } else {
                    a.put("model", model(block));
                }
            }
            case RENDERED -> {
                a.put("transparent", true);
                Map<String, Object> r = new LinkedHashMap<>();
                r.put("item", renderItem(block));
                // a block model shown by an item display needs the half turn (see ComposterBlock.RENDER_ROTATION)
                r.put("rotation", "0," + (((facingY == null ? 0 : facingY) + 180) % 360) + ",0");
                a.put("entity_renderer", List.of(r));
            }
            default -> throw new IllegalStateException(c.look().name());
        }
        return a;
    }

    private static Map<String, Object> standing(String id, boolean lit, Carriers carriers) {
        Map<String, Object> block = new LinkedHashMap<>();
        block.put("settings", settings(lit));
        block.put("behavior", behaviours(lit, false));
        block.put("state", appearance(id, STANDING, carriers, null));
        return block;
    }

    private static Map<String, Object> wall(String id, boolean lit, Carriers carriers) {
        Map<String, Object> block = new LinkedHashMap<>();
        block.put("settings", settings(lit));
        block.put("behavior", behaviours(lit, true));
        Map<String, Object> facing = new LinkedHashMap<>();
        facing.put("type", "horizontal_direction");
        facing.put("default", "north");
        Map<String, Object> appearances = new LinkedHashMap<>();
        Map<String, Object> variants = new LinkedHashMap<>();
        for (String f : FACINGS) {
            appearances.put(f, appearance(id, f, carriers, blockstateY(f)));
            variants.put("facing=" + f, Map.of("appearance", f));
        }
        Map<String, Object> states = new LinkedHashMap<>();
        states.put("properties", Map.of("facing", facing));
        states.put("appearances", appearances);
        states.put("variants", variants);
        block.put("states", states);
        return block;
    }

    /** TFC {@code StandingAndWallBlockItem(floor, wall, ..., Direction.DOWN)}: the wall block on a side face. */
    private static Map<String, Object> item(String id, String wallBlock, String floorBlock) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("material", "paper");
        item.put("data", Map.of("item_name", "<!i><lang:block." + Json.TO_NS + "." + path(id) + ">"));
        item.put("model", Json.TO_NS + ":item/" + path(id));
        Map<String, Object> onWall = new LinkedHashMap<>();
        onWall.put("type", "wall_block_item");
        onWall.put("block", wallBlock);
        Map<String, Object> onFloor = new LinkedHashMap<>();
        onFloor.put("type", "block_item");
        onFloor.put("block", floorBlock);
        item.put("behavior", List.of(onWall, onFloor));
        return item;
    }

    private static String path(String id) {
        return id.substring(id.indexOf(':') + 1);
    }

    /** TFC's dead torch block models, the burnt-out texture and both item models, renamed. */
    static void copyAssets(TfcCheckout tfc, OutputTree out) {
        for (String m : List.of("block/dead_torch", "block/dead_wall_torch", "item/torch", "item/dead_torch")) {
            String src = ASSETS + "models/" + m + ".json";
            JsonObject model = Json.parse(tfc.readString(src), src).getAsJsonObject();
            out.put(RP + "models/" + m + ".json", Json.write(Json.rename(model)));
            JsonObject textures = model.getAsJsonObject("textures");
            for (Map.Entry<String, JsonElement> t : textures.entrySet()) {
                String v = t.getValue().getAsString();
                if (!v.startsWith(Json.FROM_NS + ":")) {
                    continue; // minecraft:block/torch: the vanilla texture
                }
                String path = v.substring(v.indexOf(':') + 1);
                String png = ASSETS + "textures/" + path + ".png";
                if (!tfc.exists(png)) {
                    throw new DatagenException(src + " names " + v + ", which TFC has no texture for");
                }
                out.put(RP + "textures/" + path + ".png", tfc.read(png));
            }
        }
        for (String b : List.of("torch", "wall_torch")) {
            String src = ASSETS + "blockstates/" + b + ".json";
            if (!tfc.readString(src).contains("\"minecraft:block/" + b + "\"")) {
                throw new DatagenException(src + " no longer draws vanilla's minecraft:block/" + b);
            }
        }
    }

    /** TFC's torch and dead torch tables; the wall blocks drop like their standing blocks. */
    static void delegateLoot(TfcCheckout tfc, OutputTree out) {
        for (String standing : List.of("torch", "dead_torch")) {
            String src = TfcCheckout.MAIN + "data/" + Json.FROM_NS + "/loot_table/blocks/" + standing + ".json";
            JsonElement table = Json.rename(Json.parse(tfc.readString(src), src));
            for (String id : List.of(standing, standing.replace("torch", "wall_torch"))) {
                LootTableExport.delegateBlock(out, id, table, named -> { });
            }
        }
    }

    /** {@code block.tfp.torch} and {@code block.tfp.dead_torch} (and the wall names) in every TFC locale that has them. */
    static void writeLang(TfcCheckout tfc, OutputTree out) {
        for (String f : tfc.list(LANG)) {
            if (!f.endsWith(".json")) {
                continue;
            }
            String locale = f.substring(LANG.length(), f.length() - ".json".length());
            JsonObject src = Json.parse(tfc.readString(f), f).getAsJsonObject();
            JsonObject o = new JsonObject();
            for (String block : BLOCKS) {
                String key = "block." + Json.FROM_NS + "." + path(block);
                if (src.has(key)) {
                    o.addProperty("block." + Json.TO_NS + "." + path(block), src.get(key).getAsString());
                } else if (locale.equals("en_us")) {
                    throw new DatagenException("TFC has no English name " + key);
                }
            }
            if (o.size() > 0) {
                out.put(LANG_RP + locale + ".json", Json.write(o));
            }
        }
    }
}
