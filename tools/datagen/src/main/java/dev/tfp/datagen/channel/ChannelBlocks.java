/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/devices/ChannelBlock.java @ c3b4d9536 (EAST, SOUTH, WEST, NORTH, DOWN,
 *       TRIGGERED, WITH_METAL, their defaults),
 *       src/main/java/net/dries007/tfc/common/blocks/devices/MoldTableBlock.java @ c3b4d9536 (EAST, SOUTH, WEST, NORTH),
 *       src/main/java/net/dries007/tfc/common/blocks/TFCBlocks.java @ c3b4d9536 (CHANNEL: strength(3), SoundType.METAL,
 *       lightLevel 10 with metal; MOLD_TABLE: strength(3), SoundType.METAL),
 *       src/main/resources/assets/tfc/blockstates/{channel,mold_table}.json, models/block/{channel_base,channel_bottom,
 *       channel_connection,channel_stop,mold_table_base,mold_table_stop}.json, models/item/{channel,mold_table}.json,
 *       textures/block/{crucible_like,crucible_top_like}.png @ c3b4d9536 (copied), molten_still.png @ c3b4d9536 (tinted),
 *       src/main/resources/data/tfc/loot_table/blocks/{channel,mold_table}.json @ c3b4d9536 (the block drops itself),
 *       src/main/resources/assets/tfc/lang/*.json @ c3b4d9536 (block.tfc.channel, block.tfc.mold_table)
 */
package dev.tfp.datagen.channel;

import com.google.gson.JsonArray;
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
import dev.tfp.datagen.device.DeviceYaml;
import dev.tfp.datagen.hitbox.HitboxAllocation;
import dev.tfp.datagen.hitbox.HitboxDemands;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * PAR-METAL-13: the casting channel and the mold table as the CraftEngine blocks {@code tfp:channel}
 * ({@code configuration/channel/channel.yml}) and {@code tfp:mold_table} ({@code mold_table.yml}).
 *
 * <ul>
 *   <li>Channel: TFC's seven boolean states {@code east, south, west, north, down, triggered, with_metal} (128 server states).
 *       The four horizontal connections and {@code down} are set by the shape rule; {@code triggered} remembers the
 *       redstone signal; {@code with_metal} is set while metal flows through (light 10 as TFC). The hitbox is TFC's
 *       {@code SHAPES[connections]} on a transparent carrier; the look is TFC's blockstate as item displays: the base post
 *       ring, the bottom plate when {@code down} is false, a connection arm or a stop cap per side, and, with metal, our
 *       flow models (the metal surface in the basin, in each arm and, with {@code down}, the falling stream).</li>
 *   <li>Mold table: {@code east, south, west, north} (16 states): the base slab and a stop cap per unconnected side. The
 *       mold on it is a display entity of the plugin (TFC's custom model loader draws it).</li>
 *   <li>Both carry the {@code tfp:device} behaviour, TFC's settings (strength 3, metal sound, pickaxe), loot (themselves) and
 *       a block item.</li>
 * </ul>
 */
public final class ChannelBlocks {
    static final String P = "src/main/java/net/dries007/tfc/common/blocks/devices/";
    static final String CHANNEL_SRC = P + "ChannelBlock.java";
    static final String TABLE_SRC = P + "MoldTableBlock.java";
    static final String TFC_BLOCKS = "src/main/java/net/dries007/tfc/common/blocks/TFCBlocks.java";
    static final String ASSETS = TfcCheckout.ASSETS;
    static final String LANG = ASSETS + "lang/";
    static final String PACK = "craftengine/tfp/";
    public static final String CHANNEL_OUTPUT = PACK + "configuration/channel/channel.yml";
    public static final String TABLE_OUTPUT = PACK + "configuration/channel/mold_table.yml";
    static final String RP = PACK + "resourcepack/assets/tfp/";
    static final String LANG_RP = PACK + "resourcepack/assets/tfp_channel/lang/";
    public static final String CHANNEL_ID = HitboxDemands.CHANNEL_ID;
    public static final String TABLE_ID = HitboxDemands.MOLD_TABLE_ID;
    public static final String CHANNEL_LANG_KEY = "block." + Json.TO_NS + ".channel";
    public static final String TABLE_LANG_KEY = "block." + Json.TO_NS + ".mold_table";
    /** TFC's {@code builder.add(EAST, SOUTH, WEST, NORTH, DOWN, TRIGGERED, WITH_METAL)} order. */
    public static final List<String> CHANNEL_PROPERTIES = List.of("east", "south", "west", "north", "down", "triggered",
        "with_metal");
    public static final List<String> TABLE_PROPERTIES = List.of("east", "south", "west", "north");
    /** {@code lightLevel(s -> s.getValue(WITH_METAL) ? 10 : 0)}. */
    public static final int METAL_LIGHT = 10;
    /** TFC's blockstate {@code y} of each side, for the connection arm, the stop cap and the mold table's stop cap. */
    static final Map<String, Integer> SIDE_Y = Map.of("north", 270, "east", 0, "south", 90, "west", 180);
    static final List<String> SIDES = List.of("north", "east", "south", "west");
    /** Bit of a side in the shape table: {@code get2DDataValue} (south 0, west 1, north 2, east 3). */
    static final Map<String, Integer> SIDE_BIT = Map.of("south", 1, "west", 2, "north", 4, "east", 8);

    static final String BASE = Json.TO_NS + ":block/channel_base";
    static final String BOTTOM = Json.TO_NS + ":block/channel_bottom";
    static final String CONNECTION = Json.TO_NS + ":block/channel_connection";
    static final String STOP = Json.TO_NS + ":block/channel_stop";
    static final String TABLE_BASE = Json.TO_NS + ":block/mold_table_base";
    static final String TABLE_STOP = Json.TO_NS + ":block/mold_table_stop";
    /** Our flow models (the molten metal in the basin, in an arm, in the falling stream). */
    static final String FLOW_CENTER = Json.TO_NS + ":block/channel_flow_center";
    static final String FLOW_ARM = Json.TO_NS + ":block/channel_flow_arm";
    static final String FLOW_FALL = Json.TO_NS + ":block/channel_flow_fall";
    /** TFC's greyscale molten texture is tinted by each metal's colour; ours is one fixed molten orange (ledger divergence). */
    static final String MOLTEN_SOURCE = ASSETS + "textures/block/molten_still.png";
    static final String MOLTEN_TEXTURE = Json.TO_NS + ":block/channel_molten";
    /** The tint multiplied into the greyscale: a hot orange. */
    static final int[] MOLTEN_TINT = {255, 122, 30};

    private ChannelBlocks() {
    }

    /** True when the checkout has the channel and the mold table (the small test fixtures do not). */
    public static boolean available(TfcCheckout tfc) {
        return tfc.exists(CHANNEL_SRC) && tfc.exists(TABLE_SRC) && tfc.exists(ASSETS + "blockstates/channel.json");
    }

    /** Writes the entries, models, textures and names. Returns the number of server states (128 + 16). */
    public static int run(TfcCheckout tfc, OutputTree out) {
        checkSources(tfc);
        Set<String> copied = new TreeSet<>();
        for (String m : List.of(BASE, BOTTOM, CONNECTION, STOP, TABLE_STOP)) {
            CropBlocks.copyModel(tfc, out, Json.FROM_NS + m.substring(Json.TO_NS.length()), copied);
        }
        copyTableBase(tfc, out);
        writeFlowModels(tfc, out);
        copyItemModel(tfc, out, "channel");
        copyItemModel(tfc, out, "mold_table");
        DeviceBlockSpec channel = channel(tfc);
        DeviceBlockSpec table = table(tfc);
        out.put(CHANNEL_OUTPUT, Yaml.write(DeviceYaml.HEADER, DeviceYaml.templated(channel)));
        out.put(TABLE_OUTPUT, Yaml.write(DeviceYaml.HEADER, DeviceYaml.templated(table)));
        writeLang(tfc, out);
        return (int) (channel.stateCount() + table.stateCount());
    }

    /** TFC's states and registrations the generator depends on. */
    static void checkSources(TfcCheckout tfc) {
        String c = tfc.readString(CHANNEL_SRC).replaceAll("\\s+", "");
        for (String needle : List.of(
            ".setValue(EAST, false) .setValue(SOUTH, false) .setValue(WEST, false) .setValue(NORTH, false) .setValue(DOWN, false)"
                + " .setValue(TRIGGERED, false) .setValue(WITH_METAL, false));",
            "builder.add(EAST, SOUTH, WEST, NORTH, DOWN, TRIGGERED, WITH_METAL);",
            "if (dir == Direction.UP) continue;",
            "byte maxDistance = dir == Direction.DOWN ? Byte.MAX_VALUE : 1;",
            "if (block instanceof ChannelBlock || block instanceof CrucibleBlock || block instanceof MoldTableBlock)")) {
            if (!c.contains(needle.replaceAll("\\s+", ""))) {
                throw new DatagenException(CHANNEL_SRC + " no longer contains \"" + needle + "\": re-check the channel entry");
            }
        }
        String t = tfc.readString(TABLE_SRC).replaceAll("\\s+", "");
        for (String needle : List.of(
            "this.registerDefaultState(this.defaultBlockState().setValue(EAST, false).setValue(SOUTH, false) .setValue(WEST, false)"
                + " .setValue(NORTH, false));",
            "pBuilder.add(EAST, SOUTH, WEST, NORTH);",
            "neighborState.getBlock() instanceof ChannelBlock")) {
            if (!t.contains(needle.replaceAll("\\s+", ""))) {
                throw new DatagenException(TABLE_SRC + " no longer contains \"" + needle + "\": re-check the mold table entry");
            }
        }
        String blocks = tfc.readString(TFC_BLOCKS).replaceAll("\\s+", "");
        for (String needle : List.of(
            "register(\"channel\", () -> new ChannelBlock(ExtendedProperties.of(MapColor.METAL).strength(3).sound(SoundType.METAL)"
                + ".blockEntity(TFCBlockEntities.CHANNEL).lightLevel(s -> s.getValue(ChannelBlock.WITH_METAL) ? 10 : 0)));",
            "register(\"mold_table\", () -> new MoldTableBlock(ExtendedProperties.of(MapColor.METAL).strength(3).sound(SoundType.METAL)")) {
            if (!blocks.contains(needle.replaceAll("\\s+", ""))) {
                throw new DatagenException(TFC_BLOCKS + " no longer registers " + needle);
            }
        }
        for (String bs : List.of("channel", "mold_table")) {
            String text = tfc.readString(ASSETS + "blockstates/" + bs + ".json");
            if (!text.contains("\"multipart\"")) {
                throw new DatagenException(bs + " blockstate is no longer multipart: re-check the display parts");
            }
        }
    }

    // ---- the specs -----------------------------------------------------------------------------------------

    static String appearanceName(int mask, boolean down, boolean metal) {
        return "m" + mask + (down ? "_down" : "_floor") + (metal ? "_metal" : "");
    }

    /** The bit mask of the horizontal connections of a state ({@code get2DDataValue} bits). */
    static int mask(Map<String, String> s) {
        int mask = 0;
        for (String side : SIDES) {
            if (s.get(side).equals("true")) {
                mask |= SIDE_BIT.get(side);
            }
        }
        return mask;
    }

    static DeviceBlockSpec channel(TfcCheckout tfc) {
        DeviceBlockSpec.Builder b = DeviceBlockSpec.builder(CHANNEL_ID);
        for (String p : CHANNEL_PROPERTIES) {
            b.property(DeviceProperty.bool(p, false));
        }
        Set<String> items = new LinkedHashSet<>();
        java.util.Map<String, String> hidden = new TreeMap<>();
        for (int mask = 0; mask < 16; mask++) {
            HitboxAllocation.Assignment a = carrier(CHANNEL_ID, HitboxDemands.channelPattern(mask));
            if (a.hideTarget() != null && !a.pool() && hidden.putIfAbsent(a.state(), a.hideTarget()) == null) {
                b.stateMapping(a.state(), a.hideTarget());
            }
            for (boolean down : new boolean[] {false, true}) {
                for (boolean metal : new boolean[] {false, true}) {
                    b.appearance(DeviceAppearance.rendered(appearanceName(mask, down, metal),
                        DeviceAppearance.Carrier.state(a.state()), renderers(b, items, mask, down, metal)));
                }
            }
        }
        b.everyState(s -> appearanceName(mask(s), s.get("down").equals("true"), s.get("with_metal").equals("true")),
                s -> s.get("with_metal").equals("true") ? METAL_LIGHT : 0)
            .settings(DeviceBlockSpec.Settings.fromTfc(tfc, "channel"))
            .loot(DeviceLoot.self())
            .deviceBehaviour()
            .blockItem(CHANNEL_LANG_KEY);
        return b.build();
    }

    static DeviceBlockSpec table(TfcCheckout tfc) {
        DeviceBlockSpec.Builder b = DeviceBlockSpec.builder(TABLE_ID);
        for (String p : TABLE_PROPERTIES) {
            b.property(DeviceProperty.bool(p, false));
        }
        HitboxAllocation.Assignment a = carrier(TABLE_ID, HitboxDemands.MOLD_TABLE_APPEARANCE);
        if (a.hideTarget() != null && !a.pool()) {
            b.stateMapping(a.state(), a.hideTarget());
        }
        Set<String> items = new LinkedHashSet<>();
        for (int mask = 0; mask < 16; mask++) {
            List<DeviceAppearance.Renderer> r = new ArrayList<>();
            r.add(part(b, items, TABLE_BASE, 0));
            for (String side : SIDES) {
                if ((mask & SIDE_BIT.get(side)) == 0) {
                    r.add(part(b, items, TABLE_STOP, SIDE_Y.get(side)));
                }
            }
            b.appearance(DeviceAppearance.rendered("m" + mask, DeviceAppearance.Carrier.state(a.state()), r));
        }
        b.everyState(s -> "m" + mask(s), null)
            .settings(DeviceBlockSpec.Settings.fromTfc(tfc, "mold_table"))
            .loot(DeviceLoot.self())
            .deviceBehaviour()
            .blockItem(TABLE_LANG_KEY);
        return b.build();
    }

    private static HitboxAllocation.Assignment carrier(String id, String appearance) {
        HitboxAllocation.Assignment a = HitboxAllocation.agreed(id, appearance);
        if (a.mode() != HitboxAllocation.Mode.HITBOX_ONLY) {
            throw new DatagenException(id + "#" + appearance + " got a " + a.mode() + " carrier; ChannelBlocks draws item displays");
        }
        return a;
    }

    /** TFC's blockstate of the channel: base, bottom without {@code down}, per side a connection or a stop, plus the flow. */
    static List<DeviceAppearance.Renderer> renderers(DeviceBlockSpec.Builder b, Set<String> items, int mask, boolean down,
            boolean metal) {
        List<DeviceAppearance.Renderer> r = new ArrayList<>();
        r.add(part(b, items, BASE, 0));
        if (!down) {
            r.add(part(b, items, BOTTOM, 0));
        }
        for (String side : SIDES) {
            boolean connected = (mask & SIDE_BIT.get(side)) != 0;
            r.add(part(b, items, connected ? CONNECTION : STOP, SIDE_Y.get(side)));
        }
        if (metal) {
            r.add(part(b, items, FLOW_CENTER, 0));
            for (String side : SIDES) {
                if ((mask & SIDE_BIT.get(side)) != 0) {
                    r.add(part(b, items, FLOW_ARM, SIDE_Y.get(side)));
                }
            }
            if (down) {
                r.add(part(b, items, FLOW_FALL, 0));
            }
        }
        return r;
    }

    static String renderItem(String model) {
        return Json.TO_NS + ":render/" + model.substring(model.indexOf(":block/") + ":block/".length());
    }

    private static DeviceAppearance.Renderer part(DeviceBlockSpec.Builder b, Set<String> items, String model, int y) {
        String item = renderItem(model);
        if (items.add(item)) {
            b.renderItem(item, model);
        }
        return DeviceAppearance.Renderer.item(item).rotated(CropBlocks.rotation(y));
    }

    // ---- models -------------------------------------------------------------------------------------------

    /** The mold table base without TFC's custom model loader (the mold on it is a display entity of the plugin). */
    static void copyTableBase(TfcCheckout tfc, OutputTree out) {
        String src = ASSETS + "models/block/mold_table_base.json";
        JsonObject m = Json.parse(tfc.readString(src), src).getAsJsonObject();
        if (!m.has("loader") || !m.get("loader").getAsString().equals("tfc:mold_table")) {
            throw new DatagenException(src + " no longer names the tfc:mold_table loader");
        }
        m.remove("loader");
        copyTextures(tfc, out, m, src);
        out.put(RP + "models/block/mold_table_base.json", Json.write(Json.rename(m)));
    }

    static void copyTextures(TfcCheckout tfc, OutputTree out, JsonObject model, String src) {
        for (Map.Entry<String, com.google.gson.JsonElement> t : model.getAsJsonObject("textures").entrySet()) {
            String ref = t.getValue().getAsString();
            if (!ref.startsWith(Json.FROM_NS + ":")) {
                continue;
            }
            String png = TfcCheckout.ASSETS + "textures/" + ref.substring(Json.FROM_NS.length() + 1) + ".png";
            if (!tfc.exists(png)) {
                throw new DatagenException(src + " uses missing texture " + ref);
            }
            String rp = RP + "textures/" + ref.substring(Json.FROM_NS.length() + 1) + ".png";
            out.put(rp, tfc.read(png));
            if (tfc.exists(png + ".mcmeta")) {
                out.put(rp + ".mcmeta", tfc.read(png + ".mcmeta"));
            }
        }
    }

    /** The molten surface models: 4 px wide basin, the arm toward east, and the stream through the missing bottom plate. */
    static void writeFlowModels(TfcCheckout tfc, OutputTree out) {
        out.put(RP + "textures/block/channel_molten.png", tint(tfc.read(MOLTEN_SOURCE)));
        if (tfc.exists(MOLTEN_SOURCE + ".mcmeta")) {
            out.put(RP + "textures/block/channel_molten.png.mcmeta", tfc.read(MOLTEN_SOURCE + ".mcmeta"));
        }
        // the surface sits 1 px above the bottom plate (TFC's CHANNEL_FLOW_*_SHAPE start at y 1 and end at y 4)
        out.put(RP + "models/block/channel_flow_center.json", flow(6, 2, 6, 10, 3, 10));
        out.put(RP + "models/block/channel_flow_arm.json", flow(10, 2, 6, 16, 3, 10));
        out.put(RP + "models/block/channel_flow_fall.json", flow(6, -16, 6, 10, 2, 10));
    }

    /** The greyscale texture multiplied by {@link #MOLTEN_TINT}, alpha kept; animation strips keep their frames. */
    static byte[] tint(byte[] png) {
        try {
            java.awt.image.BufferedImage in = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(png));
            java.awt.image.BufferedImage outImage = new java.awt.image.BufferedImage(in.getWidth(), in.getHeight(),
                java.awt.image.BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < in.getHeight(); y++) {
                for (int x = 0; x < in.getWidth(); x++) {
                    int argb = in.getRGB(x, y);
                    int r = ((argb >> 16) & 0xFF) * MOLTEN_TINT[0] / 255;
                    int g = ((argb >> 8) & 0xFF) * MOLTEN_TINT[1] / 255;
                    int b = (argb & 0xFF) * MOLTEN_TINT[2] / 255;
                    outImage.setRGB(x, y, (argb & 0xFF000000) | (r << 16) | (g << 8) | b);
                }
            }
            java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(outImage, "png", bytes);
            return bytes.toByteArray();
        } catch (java.io.IOException e) {
            throw new DatagenException("cannot tint " + MOLTEN_SOURCE + ": " + e.getMessage());
        }
    }

    static String flow(double x1, double y1, double z1, double x2, double y2, double z2) {
        JsonObject m = new JsonObject();
        m.addProperty("ambientocclusion", false);
        JsonObject textures = new JsonObject();
        textures.addProperty("0", MOLTEN_TEXTURE);
        textures.addProperty("particle", MOLTEN_TEXTURE);
        m.add("textures", textures);
        JsonObject e = new JsonObject();
        e.add("from", array(x1, y1, z1));
        e.add("to", array(x2, y2, z2));
        JsonObject faces = new JsonObject();
        for (String f : List.of("north", "east", "south", "west", "up", "down")) {
            JsonObject face = new JsonObject();
            face.add("uv", array(0, 0, 16, 16));
            face.addProperty("texture", "#0");
            faces.add(f, face);
        }
        e.add("faces", faces);
        JsonArray elements = new JsonArray();
        elements.add(e);
        m.add("elements", elements);
        return Json.write(m);
    }

    private static JsonArray array(double... v) {
        JsonArray a = new JsonArray();
        for (double d : v) {
            a.add(d);
        }
        return a;
    }

    static void copyItemModel(TfcCheckout tfc, OutputTree out, String name) {
        String src = ASSETS + "models/item/" + name + ".json";
        JsonObject m = Json.parse(tfc.readString(src), src).getAsJsonObject();
        copyTextures(tfc, out, m, src);
        out.put(RP + "models/item/" + name + ".json", Json.write(Json.rename(m)));
    }

    /** {@code block.tfp.channel} and {@code block.tfp.mold_table} in every TFC locale that names them (English must). */
    static void writeLang(TfcCheckout tfc, OutputTree out) {
        for (String f : tfc.list(LANG)) {
            if (!f.endsWith(".json")) {
                continue;
            }
            String locale = f.substring(LANG.length(), f.length() - ".json".length());
            JsonObject src = Json.parse(tfc.readString(f), f).getAsJsonObject();
            Map<String, String> keys = new TreeMap<>();
            for (String name : List.of("channel", "mold_table")) {
                String tfcKey = "block." + Json.FROM_NS + "." + name;
                if (src.has(tfcKey)) {
                    keys.put("block." + Json.TO_NS + "." + name, src.get(tfcKey).getAsString());
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
