/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/devices/BloomeryBlock.java @ c3b4d9536 (FACING, LIT, OPEN and their
 *       defaults, the state order),
 *       src/main/java/net/dries007/tfc/common/blocks/BloomBlock.java @ c3b4d9536 (LAYERS, default 1),
 *       src/main/java/net/dries007/tfc/common/blocks/MoltenBlock.java @ c3b4d9536 (LAYERS, LIT, the state order),
 *       src/main/java/net/dries007/tfc/common/blocks/TFCBlocks.java @ c3b4d9536 (BLOOMERY: strength(3), SoundType.METAL,
 *       litBlockEmission(15); BLOOM: strength(3F, 6F); MOLTEN: strength(-1.0F, 3600000.0F), litBlockEmission(15)),
 *       src/main/resources/assets/tfc/blockstates/{bloomery,bloom,molten}.json, models/block/{bloomery,bloom,molten}/*,
 *       models/item/bloomery.json @ c3b4d9536 (copied),
 *       src/main/resources/data/tfc/loot_table/blocks/bloomery.json @ c3b4d9536 (the block drops itself),
 *       src/main/resources/assets/tfc/lang/*.json @ c3b4d9536 (block.tfc.bloomery)
 */
package dev.tfp.datagen.bloomery;

import com.google.gson.JsonObject;
import dev.tfp.datagen.DatagenException;
import dev.tfp.datagen.Json;
import dev.tfp.datagen.OutputTree;
import dev.tfp.datagen.TfcCheckout;
import dev.tfp.datagen.crop.CropBlocks;
import dev.tfp.datagen.device.DeviceAppearance;
import dev.tfp.datagen.device.DeviceBlockSpec;
import dev.tfp.datagen.device.DeviceLoot;
import dev.tfp.datagen.device.DeviceProperty;
import dev.tfp.datagen.device.DeviceSound;
import dev.tfp.datagen.device.DeviceYaml;
import dev.tfp.datagen.hitbox.HitboxAllocation;
import dev.tfp.datagen.hitbox.HitboxDemands;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * PAR-METAL-14: the bloomery, the bloom and the molten stack block as the CraftEngine blocks {@code tfp:bloomery},
 * {@code tfp:bloom} and {@code tfp:molten} ({@code configuration/bloomery/*.yml}).
 *
 * <ul>
 *   <li>Bloomery: TFC's {@code facing} (four), {@code lit} and {@code open} (16 server states). The hitbox is TFC's
 *       {@code getShape} (closed: a 2 px wall; open: the frame with the side posts) on a transparent carrier per facing and
 *       open; the look is TFC's model of the state ({@code closed_off}, {@code closed_on}, {@code open_off},
 *       {@code open_on}) as an item display turned by the facing. Light 15 while lit. The block keeps a device record (the
 *       items inside, the lit tick); it drops itself.</li>
 *   <li>Bloom: {@code layers} 1 to 8 (8 server states), TFC's {@code SHAPE_BY_LAYER} outline per layer and its height model.
 *       It keeps a device record (the bloom item, its count and the count it was made with) and drops nothing by itself:
 *       the plugin drops one bloom item per break (TFC {@code dropBloom}).</li>
 *   <li>Molten: {@code layers} 1 to 4 and {@code lit} (8 server states), the stack of molten metal inside the chimney,
 *       the 4 px layer outline and light 15 while lit. No behaviour, no loot; TFC's strength -1 (unbreakable) is a very
 *       high hardness here.</li>
 * </ul>
 */
public final class BloomeryBlocks {
    static final String D = "src/main/java/net/dries007/tfc/common/blocks/devices/BloomeryBlock.java";
    static final String BLOOM_SRC = "src/main/java/net/dries007/tfc/common/blocks/BloomBlock.java";
    static final String MOLTEN_SRC = "src/main/java/net/dries007/tfc/common/blocks/MoltenBlock.java";
    static final String TFC_BLOCKS = "src/main/java/net/dries007/tfc/common/blocks/TFCBlocks.java";
    static final String ASSETS = TfcCheckout.ASSETS;
    static final String LANG = ASSETS + "lang/";
    static final String PACK = "craftengine/tfp/";
    static final String RP = PACK + "resourcepack/assets/tfp/";
    static final String LANG_RP = PACK + "resourcepack/assets/tfp_bloomery/lang/";
    public static final String BLOOMERY_OUTPUT = PACK + "configuration/bloomery/bloomery.yml";
    public static final String BLOOM_OUTPUT = PACK + "configuration/bloomery/bloom.yml";
    public static final String MOLTEN_OUTPUT = PACK + "configuration/bloomery/molten.yml";
    public static final String BLOOMERY_ID = HitboxDemands.BLOOMERY_ID;
    public static final String BLOOM_ID = HitboxDemands.BLOOM_ID;
    public static final String MOLTEN_ID = HitboxDemands.MOLTEN_ID;
    public static final String BLOOMERY_LANG_KEY = "block." + Json.TO_NS + ".bloomery";
    /** TFC's {@code BloomBlock.LAYERS} (vanilla layers 1 to 8) and {@code TFCBlockStateProperties.LAYERS_4}. */
    public static final int BLOOM_LAYERS = 8;
    public static final int MOLTEN_LAYERS = 4;
    /** {@code litBlockEmission(15)}. */
    public static final int LIT_LIGHT = 15;
    /** TFC's molten block is {@code strength(-1.0F, 3600000.0F)}: unbreakable; ours takes the resistance as its hardness. */
    public static final String MOLTEN_HARDNESS = "3600000";
    /** TFC's blockstate {@code y} of each facing. */
    static final Map<String, Integer> FACING_Y = Map.of("north", 0, "east", 90, "south", 180, "west", 270);
    /** The block models of the bloom by layers (TFC's blockstate). */
    static final List<String> BLOOM_MODELS = List.of("bloom/bloom_height2", "bloom/bloom_height4", "bloom/bloom_height6",
        "bloom/bloom_height8", "bloom/bloom_height10", "bloom/bloom_height12", "bloom/bloom_height14", "bloom/bloom_block");
    /** The block models of the molten stack by layers (TFC's blockstate): {@code [unlit, lit]}. */
    static final List<String[]> MOLTEN_MODELS = List.of(
        new String[] {"molten/molten_height4", "molten/molten_lit_height4"},
        new String[] {"molten/molten_height8", "molten/molten_lit_height8"},
        new String[] {"molten/molten_height12", "molten/molten_lit_height12"},
        new String[] {"molten/molten_block", "molten/molten_lit_block"});

    private BloomeryBlocks() {
    }

    /** True when the checkout has the bloomery sources (the small test fixtures do not). */
    public static boolean available(TfcCheckout tfc) {
        return tfc.exists(D) && tfc.exists(BLOOM_SRC) && tfc.exists(MOLTEN_SRC) && tfc.exists(ASSETS + "blockstates/bloomery.json");
    }

    /** Writes the entries, models, textures and names. Returns the number of server states (16 + 8 + 8). */
    public static int run(TfcCheckout tfc, OutputTree out) {
        checkSources(tfc);
        Set<String> copied = new TreeSet<>();
        for (String m : List.of("bloomery/closed_off", "bloomery/closed_on", "bloomery/open_off", "bloomery/open_on",
            "bloomery/inventory")) {
            CropBlocks.copyModel(tfc, out, Json.FROM_NS + ":block/" + m, copied);
        }
        for (String m : BLOOM_MODELS) {
            CropBlocks.copyModel(tfc, out, Json.FROM_NS + ":block/" + m, copied);
        }
        for (String[] pair : MOLTEN_MODELS) {
            for (String m : pair) {
                CropBlocks.copyModel(tfc, out, Json.FROM_NS + ":block/" + m, copied);
            }
        }
        copyItemModel(tfc, out, copied);
        DeviceBlockSpec bloomery = bloomery(tfc);
        DeviceBlockSpec bloom = bloom();
        DeviceBlockSpec molten = molten();
        out.put(BLOOMERY_OUTPUT, DeviceYaml.writeLiteral(bloomery));
        out.put(BLOOM_OUTPUT, DeviceYaml.writeLiteral(bloom));
        out.put(MOLTEN_OUTPUT, DeviceYaml.writeLiteral(molten));
        writeLang(tfc, out);
        return (int) (bloomery.stateCount() + bloom.stateCount() + molten.stateCount());
    }

    /** TFC's states and registrations the generator depends on. */
    static void checkSources(TfcCheckout tfc) {
        String b = tfc.readString(D).replaceAll("\\s+", "");
        for (String needle : List.of(
            "registerDefaultState(getStateDefinition().any().setValue(FACING,Direction.NORTH).setValue(LIT,false).setValue(OPEN,false));",
            "super.createBlockStateDefinition(builder.add(FACING).add(LIT).add(OPEN));",
            "DirectionPropertyFACING=BlockStateProperties.HORIZONTAL_FACING;")) {
            if (!b.contains(needle)) {
                throw new DatagenException(D + " no longer contains \"" + needle + "\": re-check the bloomery entry");
            }
        }
        String bloom = tfc.readString(BLOOM_SRC).replaceAll("\\s+", "");
        for (String needle : List.of("registerDefaultState(getStateDefinition().any().setValue(LAYERS,1));",
            "builder.add(LAYERS);", "IntegerPropertyLAYERS=BlockStateProperties.LAYERS;")) {
            if (!bloom.contains(needle)) {
                throw new DatagenException(BLOOM_SRC + " no longer contains \"" + needle + "\": re-check the bloom entry");
            }
        }
        String molten = tfc.readString(MOLTEN_SRC).replaceAll("\\s+", "");
        for (String needle : List.of("registerDefaultState(getStateDefinition().any().setValue(LAYERS,1).setValue(LIT,false));",
            "builder.add(LAYERS,LIT);", "IntegerPropertyLAYERS=TFCBlockStateProperties.LAYERS_4;")) {
            if (!molten.contains(needle)) {
                throw new DatagenException(MOLTEN_SRC + " no longer contains \"" + needle + "\": re-check the molten entry");
            }
        }
        String blocks = tfc.readString(TFC_BLOCKS).replaceAll("\\s+", "");
        for (String needle : List.of(
            "BLOOM=registerNoItem(\"bloom\",()->newBloomBlock(ExtendedProperties.of().mapColor(MapColor.STONE)"
                + ".requiresCorrectToolForDrops().strength(3F,6F).noOcclusion()",
            "MOLTEN=registerNoItem(\"molten\",()->newMoltenBlock(ExtendedProperties.of().mapColor(MapColor.STONE)"
                + ".requiresCorrectToolForDrops().strength(-1.0F,3600000.0F).noOcclusion().lightLevel(litBlockEmission(15))")) {
            if (!blocks.contains(needle)) {
                throw new DatagenException(TFC_BLOCKS + " no longer registers " + needle);
            }
        }
        for (String bs : List.of("bloomery", "bloom", "molten")) {
            if (tfc.readString(ASSETS + "blockstates/" + bs + ".json").contains("\"multipart\"")) {
                throw new DatagenException(bs + " blockstate is now multipart: re-check the display parts");
            }
        }
    }

    // ---- the specs -----------------------------------------------------------------------------------------

    static String bloomeryAppearance(String facing, boolean open, boolean lit) {
        return facing + (open ? "_open" : "_closed") + (lit ? "_on" : "_off");
    }

    static String bloomeryModel(boolean open, boolean lit) {
        return Json.TO_NS + ":block/bloomery/" + (open ? "open" : "closed") + (lit ? "_on" : "_off");
    }

    static DeviceBlockSpec bloomery(TfcCheckout tfc) {
        List<String> facings = List.of("north", "east", "south", "west");
        DeviceBlockSpec.Builder b = DeviceBlockSpec.builder(BLOOMERY_ID)
            .property(DeviceProperty.names("facing", facings, "north"))
            .property(DeviceProperty.bool("lit", false))
            .property(DeviceProperty.bool("open", false));
        Map<String, String> hidden = new TreeMap<>();
        Set<String> items = new TreeSet<>();
        for (String f : facings) {
            for (boolean open : new boolean[] {false, true}) {
                HitboxAllocation.Assignment a = carrier(BLOOMERY_ID, HitboxDemands.bloomeryOutline(f, open));
                hide(b, hidden, a);
                for (boolean lit : new boolean[] {false, true}) {
                    b.appearance(DeviceAppearance.rendered(bloomeryAppearance(f, open, lit),
                        DeviceAppearance.Carrier.state(a.state()), List.of(part(b, items, bloomeryModel(open, lit), FACING_Y.get(f)))));
                }
            }
        }
        return b.everyState(s -> bloomeryAppearance(s.get("facing"), s.get("open").equals("true"), s.get("lit").equals("true")),
                s -> s.get("lit").equals("true") ? LIT_LIGHT : 0)
            .settings(DeviceBlockSpec.Settings.fromTfc(tfc, "bloomery"))
            .loot(DeviceLoot.fromTfc(tfc, "bloomery"))
            .deviceBehaviour()
            .blockItem(BLOOMERY_LANG_KEY)
            .build();
    }

    static DeviceBlockSpec bloom() {
        DeviceBlockSpec.Builder b = DeviceBlockSpec.builder(BLOOM_ID).property(DeviceProperty.range("layers", 1, BLOOM_LAYERS, 1));
        Map<String, String> hidden = new TreeMap<>();
        Set<String> items = new TreeSet<>();
        for (int layers = 1; layers <= BLOOM_LAYERS; layers++) {
            HitboxAllocation.Assignment a = carrier(BLOOM_ID, HitboxDemands.bloomLayers(layers));
            hide(b, hidden, a);
            b.appearance(DeviceAppearance.rendered(HitboxDemands.bloomLayers(layers), DeviceAppearance.Carrier.state(a.state()),
                List.of(part(b, items, Json.TO_NS + ":block/" + BLOOM_MODELS.get(layers - 1), 0))));
        }
        return b.everyState(s -> HitboxDemands.bloomLayers(Integer.parseInt(s.get("layers"))), null)
            // TFC: strength(3F, 6F), the default (stone) sound, mined with a pickaxe
            .settings(DeviceBlockSpec.Settings.of("3", "6", DeviceSound.STONE, "pickaxe"))
            .deviceBehaviour()
            .build();
    }

    static DeviceBlockSpec molten() {
        DeviceBlockSpec.Builder b = DeviceBlockSpec.builder(MOLTEN_ID)
            .property(DeviceProperty.range("layers", 1, MOLTEN_LAYERS, 1))
            .property(DeviceProperty.bool("lit", false));
        Map<String, String> hidden = new TreeMap<>();
        Set<String> items = new TreeSet<>();
        for (int layers = 1; layers <= MOLTEN_LAYERS; layers++) {
            HitboxAllocation.Assignment a = carrier(MOLTEN_ID, HitboxDemands.bloomLayers(layers));
            hide(b, hidden, a);
            for (boolean lit : new boolean[] {false, true}) {
                b.appearance(DeviceAppearance.rendered(moltenAppearance(layers, lit), DeviceAppearance.Carrier.state(a.state()),
                    List.of(part(b, items, Json.TO_NS + ":block/" + MOLTEN_MODELS.get(layers - 1)[lit ? 1 : 0], 0))));
            }
        }
        return b.everyState(s -> moltenAppearance(Integer.parseInt(s.get("layers")), s.get("lit").equals("true")),
                s -> s.get("lit").equals("true") ? LIT_LIGHT : 0)
            .settings(DeviceBlockSpec.Settings.of(MOLTEN_HARDNESS, MOLTEN_HARDNESS, DeviceSound.STONE, "pickaxe"))
            .build();
    }

    static String moltenAppearance(int layers, boolean lit) {
        return HitboxDemands.bloomLayers(layers) + (lit ? "_on" : "_off");
    }

    private static HitboxAllocation.Assignment carrier(String id, String appearance) {
        HitboxAllocation.Assignment a = HitboxAllocation.agreed(id, appearance);
        if (a.mode() != HitboxAllocation.Mode.HITBOX_ONLY) {
            throw new DatagenException(id + "#" + appearance + " got a " + a.mode() + " carrier; BloomeryBlocks draws item displays");
        }
        return a;
    }

    private static void hide(DeviceBlockSpec.Builder b, Map<String, String> hidden, HitboxAllocation.Assignment a) {
        if (a.hideTarget() != null && !a.pool() && hidden.putIfAbsent(a.state(), a.hideTarget()) == null) {
            b.stateMapping(a.state(), a.hideTarget());
        }
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

    /** TFC's item model of the bloomery: the inventory block model with the off texture; the textures are copied. */
    static void copyItemModel(TfcCheckout tfc, OutputTree out, Set<String> copied) {
        String src = ASSETS + "models/item/bloomery.json";
        JsonObject m = Json.parse(tfc.readString(src), src).getAsJsonObject();
        for (Map.Entry<String, com.google.gson.JsonElement> t : m.getAsJsonObject("textures").entrySet()) {
            String ref = t.getValue().getAsString();
            if (!ref.startsWith(Json.FROM_NS + ":")) {
                continue;
            }
            String png = TfcCheckout.ASSETS + "textures/" + ref.substring(Json.FROM_NS.length() + 1) + ".png";
            if (!tfc.exists(png)) {
                throw new DatagenException(src + " uses missing texture " + ref);
            }
            out.put(RP + "textures/" + ref.substring(Json.FROM_NS.length() + 1) + ".png", tfc.read(png));
        }
        out.put(RP + "models/item/bloomery.json", Json.write(Json.rename(m)));
    }

    /** {@code block.tfp.bloomery} in every TFC locale that names it (English must). */
    static void writeLang(TfcCheckout tfc, OutputTree out) {
        for (String f : tfc.list(LANG)) {
            if (!f.endsWith(".json")) {
                continue;
            }
            String locale = f.substring(LANG.length(), f.length() - ".json".length());
            JsonObject src = Json.parse(tfc.readString(f), f).getAsJsonObject();
            String tfcKey = "block." + Json.FROM_NS + ".bloomery";
            if (src.has(tfcKey)) {
                JsonObject o = new JsonObject();
                o.addProperty(BLOOMERY_LANG_KEY, src.get(tfcKey).getAsString());
                out.put(LANG_RP + locale + ".json", Json.write(o));
            } else if (locale.equals("en_us")) {
                throw new DatagenException("TFC has no English name " + tfcKey);
            }
        }
    }
}
