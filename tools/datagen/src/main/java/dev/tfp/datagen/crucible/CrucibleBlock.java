/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/devices/CrucibleBlock.java @ c3b4d9536 (EAST, SOUTH, WEST, NORTH,
 *       default false),
 *       src/main/java/net/dries007/tfc/common/blocks/TFCBlocks.java @ c3b4d9536 (CRUCIBLE: strength(3), SoundType.METAL),
 *       src/main/resources/assets/tfc/blockstates/crucible.json, models/block/crucible.json, models/item/crucible.json and
 *       textures/block/devices/crucible/*.png @ c3b4d9536 (copied),
 *       src/main/resources/data/tfc/loot_table/blocks/crucible.json @ c3b4d9536 (the block drops itself with its contents),
 *       src/generated/resources/data/minecraft/tags/block/mineable/pickaxe.json @ c3b4d9536 (the tool),
 *       src/main/resources/assets/tfc/lang/*.json @ c3b4d9536 (block.tfc.crucible)
 */
package dev.tfp.datagen.crucible;

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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PAR-METAL-12: the crucible as the CraftEngine block {@code tfp:crucible}
 * ({@code craftengine/tfp/configuration/crucible/crucible.yml}).
 *
 * <ul>
 *   <li>TFC's four state properties {@code east, south, west, north} (the channel connections, default false), 16 server
 *       states. Every state draws TFC's {@code crucible} model on the carrier the hitbox selector picks for TFC's shell
 *       outline ({@code tfc:crucible}, the shape without channel nubs). PAR-METAL-24: a connected side adds TFC's
 *       {@code crucible_connection} model (the blockstate's multipart, turned by {@code y} per side) as an item display
 *       overlay ({@code entity_renderer}) on that state's appearance; a vanilla state holds one model, so the 16 nub
 *       combinations cannot be 16 merged models on the one carrier state. The properties are set by the plugin's
 *       {@code CrucibleConnections} rule.</li>
 *   <li>The {@code tfp:device} behaviour (the plugin's {@code CrucibleDevice}), TFC's settings (strength 3, metal sound,
 *       pickaxe), loot (itself; the record goes onto the dropped item, device {@code SAVE}) and the block item
 *       {@code tfp:crucible}.</li>
 * </ul>
 * The block model, the item model and the two textures are copied from TFC, and the block's name goes to
 * {@code assets/tfp_crucible/lang/<locale>.json}.
 */
public final class CrucibleBlock {
    public static final String ID = Json.TO_NS + ":crucible";
    static final String BLOCK = "crucible";
    static final String SOURCE = "src/main/java/net/dries007/tfc/common/blocks/devices/CrucibleBlock.java";
    static final String BLOCKSTATE = TfcCheckout.ASSETS + "blockstates/crucible.json";
    static final String BLOCK_MODEL = TfcCheckout.ASSETS + "models/block/crucible.json";
    static final String ITEM_MODEL = TfcCheckout.ASSETS + "models/item/crucible.json";
    static final String LANG = TfcCheckout.ASSETS + "lang/";
    static final String PACK = "craftengine/tfp/";
    public static final String OUTPUT = PACK + "configuration/crucible/crucible.yml";
    static final String RP = PACK + "resourcepack/assets/tfp/";
    static final String LANG_RP = PACK + "resourcepack/assets/tfp_crucible/lang/";
    public static final String APPEARANCE = "crucible";
    /** TFC's {@code builder.add(EAST, SOUTH, WEST, NORTH)} order. */
    public static final List<String> PROPERTIES = List.of("east", "south", "west", "north");
    /** TFC's blockstate {@code y} per side (north 270, east 0, south 90, west 180). */
    static final Map<String, Integer> SIDE_Y = Map.of("north", 270, "east", 0, "south", 90, "west", 180);
    public static final String CONNECTION_MODEL = Json.TO_NS + ":block/crucible_connection";
    static final String CONNECTION_SOURCE = TfcCheckout.ASSETS + "models/block/crucible_connection.json";
    public static final String LANG_KEY = "block." + Json.TO_NS + "." + BLOCK;
    private static final Pattern TEXTURE = Pattern.compile("\"(" + Json.FROM_NS + ":block/devices/crucible/[a-z_]+)\"");

    private CrucibleBlock() {
    }

    /** True when the checkout has the crucible (the small test fixtures do not). */
    public static boolean available(TfcCheckout tfc) {
        return tfc.exists(SOURCE) && tfc.exists(BLOCK_MODEL);
    }

    /** Writes the entry, its models, textures and names. Returns the number of server states (16). */
    public static int run(TfcCheckout tfc, OutputTree out) {
        checkSource(tfc);
        copyModels(tfc, out);
        DeviceBlockSpec spec = spec(tfc);
        out.put(OUTPUT, Yaml.write(DeviceYaml.HEADER, DeviceYaml.templated(spec)));
        writeLang(tfc, out);
        return (int) spec.stateCount();
    }

    /** TFC's four boolean states, defaulting to false and added in {@link #PROPERTIES} order. */
    static void checkSource(TfcCheckout tfc) {
        String src = tfc.readString(SOURCE).replaceAll("\\s+", " ");
        for (String needle : List.of(
                "registerDefaultState(this.defaultBlockState().setValue(EAST, false).setValue(SOUTH, false)"
                    + ".setValue(WEST, false).setValue(NORTH, false));",
                "builder.add(EAST, SOUTH, WEST, NORTH);",
                "super(properties, InventoryRemoveBehavior.SAVE);")) {
            if (!src.contains(needle)) {
                throw new DatagenException(SOURCE + " no longer contains \"" + needle + "\": re-check the crucible entry");
            }
        }
        String bs = tfc.readString(BLOCKSTATE);
        for (String needle : List.of("\"model\": \"tfc:block/crucible\"", "\"model\": \"tfc:block/crucible_connection\"")) {
            if (!bs.contains(needle)) {
                throw new DatagenException(BLOCKSTATE + " no longer draws " + needle);
            }
        }
    }

    static DeviceBlockSpec spec(TfcCheckout tfc) {
        HitboxAllocation.Assignment a = HitboxAllocation.agreed(ID, APPEARANCE);
        if (a.mode() != HitboxAllocation.Mode.MODEL) {
            throw new DatagenException("the crucible carrier is " + a.mode() + ", CrucibleBlock writes a model");
        }
        DeviceBlockSpec.Builder b = DeviceBlockSpec.builder(ID);
        for (String p : PROPERTIES) {
            b.property(DeviceProperty.bool(p, false));
        }
        Set<String> items = new LinkedHashSet<>();
        for (int mask = 0; mask < 16; mask++) {
            b.appearance(DeviceAppearance.modelled(appearanceName(mask), DeviceAppearance.Carrier.state(a.state()),
                HitboxDemands.CRUCIBLE_MODEL).withOverlay(nubs(b, items, mask)));
        }
        b.everyState(s -> appearanceName(mask(s)), null)
            .settings(DeviceBlockSpec.Settings.fromTfc(tfc, BLOCK))
            .loot(DeviceLoot.self())
            .deviceBehaviour()
            .blockItem(LANG_KEY);
        if (a.hideTarget() != null) {
            b.stateMapping(a.state(), a.hideTarget());
        }
        return b.build();
    }

    /** Bit {@code i} is {@link #PROPERTIES}{@code [i]}. */
    static int mask(Map<String, String> s) {
        int mask = 0;
        for (int i = 0; i < PROPERTIES.size(); i++) {
            if (s.get(PROPERTIES.get(i)).equals("true")) {
                mask |= 1 << i;
            }
        }
        return mask;
    }

    /** {@code crucible} without connections, else {@code crucible_<sides>} in property order. */
    static String appearanceName(int mask) {
        StringBuilder n = new StringBuilder(APPEARANCE);
        for (int i = 0; i < PROPERTIES.size(); i++) {
            if ((mask & (1 << i)) != 0) {
                n.append('_').append(PROPERTIES.get(i));
            }
        }
        return n.toString();
    }

    /** One nub item display per connected side, turned like TFC's blockstate {@code y}. */
    static List<DeviceAppearance.Renderer> nubs(DeviceBlockSpec.Builder b, Set<String> items, int mask) {
        List<DeviceAppearance.Renderer> r = new ArrayList<>();
        String item = Json.TO_NS + ":render/crucible_connection";
        for (int i = 0; i < PROPERTIES.size(); i++) {
            if ((mask & (1 << i)) == 0) {
                continue;
            }
            if (items.add(item)) {
                b.renderItem(item, CONNECTION_MODEL);
            }
            r.add(DeviceAppearance.Renderer.item(item).rotated(CropBlocks.rotation(SIDE_Y.get(PROPERTIES.get(i)))));
        }
        return r;
    }

    /** TFC's block and item models (namespace renamed) and the textures the block model names. */
    static void copyModels(TfcCheckout tfc, OutputTree out) {
        String block = tfc.readString(BLOCK_MODEL);
        Matcher m = TEXTURE.matcher(block);
        TreeSet<String> textures = new TreeSet<>();
        while (m.find()) {
            textures.add(m.group(1));
        }
        if (textures.isEmpty()) {
            throw new DatagenException(BLOCK_MODEL + " names no crucible texture");
        }
        out.put(RP + "models/block/crucible.json", Json.write(Json.rename(Json.parse(block, BLOCK_MODEL))));
        JsonObject item = Json.parse(tfc.readString(ITEM_MODEL), ITEM_MODEL).getAsJsonObject();
        if (!item.has("parent") || !(Json.FROM_NS + ":block/crucible").equals(item.get("parent").getAsString())) {
            throw new DatagenException(ITEM_MODEL + " is no longer the block model as parent");
        }
        out.put(RP + "models/item/crucible.json", Json.write(Json.rename(item)));
        for (String t : textures) {
            String path = t.substring(t.indexOf(':') + 1);
            out.put(RP + "textures/" + path + ".png", tfc.read(TfcCheckout.ASSETS + "textures/" + path + ".png"));
        }
        String conn = tfc.readString(CONNECTION_SOURCE);
        JsonObject cm = Json.parse(conn, CONNECTION_SOURCE).getAsJsonObject();
        for (Map.Entry<String, com.google.gson.JsonElement> t : cm.getAsJsonObject("textures").entrySet()) {
            String ref = t.getValue().getAsString();
            if (!ref.startsWith(Json.FROM_NS + ":")) {
                continue;
            }
            String path = TfcCheckout.ASSETS + "textures/" + ref.substring(Json.FROM_NS.length() + 1) + ".png";
            if (!tfc.exists(path)) {
                throw new DatagenException(CONNECTION_SOURCE + " uses missing texture " + ref);
            }
            out.put(RP + "textures/" + ref.substring(Json.FROM_NS.length() + 1) + ".png", tfc.read(path));
        }
        out.put(RP + "models/block/crucible_connection.json", Json.write(Json.rename(cm)));
    }

    /** {@code block.tfp.crucible} in every TFC locale that names the crucible (English must). */
    static void writeLang(TfcCheckout tfc, OutputTree out) {
        String tfcKey = "block." + Json.FROM_NS + "." + BLOCK;
        for (String f : tfc.list(LANG)) {
            if (!f.endsWith(".json")) {
                continue;
            }
            String locale = f.substring(LANG.length(), f.length() - ".json".length());
            JsonObject src = Json.parse(tfc.readString(f), f).getAsJsonObject();
            if (!src.has(tfcKey)) {
                if (locale.equals("en_us")) {
                    throw new DatagenException("TFC has no English name " + tfcKey);
                }
                continue;
            }
            Map<String, String> keys = new TreeMap<>();
            keys.put(LANG_KEY, src.get(tfcKey).getAsString());
            JsonObject o = new JsonObject();
            keys.forEach(o::addProperty);
            out.put(LANG_RP + locale + ".json", Json.write(o));
        }
    }
}
