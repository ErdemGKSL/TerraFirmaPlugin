/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/wood/Wood.java @ c3b4d9536 (BlockType SAPLING, POTTED_SAPLING, TWIG,
 *       FALLEN_LEAVES: strength, sound, noCollission; the conifer flag),
 *       src/main/java/net/dries007/tfc/common/blocks/wood/FallenLeavesBlock.java @ c3b4d9536 (LAYERS 1..8),
 *       src/main/java/net/dries007/tfc/client/ClientEventHandler.java and TFCColors.java @ c3b4d9536 (the fallen leaves'
 *       colour: 0xcf7d13 for conifers, else the foliage colormap at its default climate 10 degrees, 250 mm),
 *       src/main/resources/assets/tfc/{blockstates,models}/wood/{sapling,potted_sapling,fallen_leaves,twig},
 *       models/block/groundcover/{fallen_leaves_height*,twig}.json and data/tfc/loot_table/blocks/wood/* @ c3b4d9536
 */
package dev.tfp.datagen.wood;

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
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import javax.imageio.ImageIO;

/**
 * PAR-WORLD-44: TFC's wood plant blocks, four per wood, one file {@code configuration/wood/plants/<wood>.yml} each:
 * <ul>
 *   <li>{@code tfp:wood/sapling/<wood>}: TFC's cross model as an item display on a hitbox-only, no-collision carrier
 *       (PAR-WORLD-53 option a); strength 0, grass sounds, hoe; a block item. The growth stage is not modelled.</li>
 *   <li>{@code tfp:wood/potted_sapling/<wood>}: TFC's potted model ({@code flower_pot_cross}) as an item display; vanilla's
 *       potted sapling settings (instant break, stone sounds); no item, the loot gives the sapling and a flower pot.</li>
 *   <li>{@code tfp:wood/fallen_leaves/<wood>[layers=1..8]}: TFC's height models (2 to 14 px, then the dense leaves cube)
 *       as item displays; the grey leaves texture baked with TFC's fallen leaves colour (a display takes no tint);
 *       strength 0.05/0, crop sounds, hoe; a block item.</li>
 *   <li>{@code tfp:wood/twig/<wood>}: every wood shares one tripwire pool state with the oak twig model (PAR-WORLD-53);
 *       strength 0.05/0, wood sounds, axe; a block item.</li>
 * </ul>
 * The loot is TFC's tables, delegated to the Paper runtime. Carriers and the pool come from {@link HitboxDemands} and
 * {@link HitboxAllocation}.
 */
public final class WoodPlantBlocks {
    static final String PACK = "craftengine/tfp/";
    public static final String OUTPUT_DIR = PACK + "configuration/wood/plants/";
    static final String RP = PACK + "resourcepack/assets/tfp/";
    static final String WOOD_JAVA = "src/main/java/net/dries007/tfc/common/blocks/wood/Wood.java";
    static final String BLOCKSTATES = TfcCheckout.ASSETS + "blockstates/wood/";
    static final String MODELS = TfcCheckout.ASSETS + "models/";
    static final String TEXTURES = TfcCheckout.ASSETS + "textures/";
    static final String RENDER_ROTATION = "0,180,0";
    public static final String LAYERS = "layers";
    public static final int MAX_LAYERS = 8;
    /** TFC {@code blockColor(0xcf7d13)}: the conifers' fallen needles. */
    static final int CONIFER_COLOR = 0xcf7d13;
    /** {@code TFCColors.getClimateColor(FOLIAGE_COLORS_CACHE, 10f, 250f)}: the default climate, as colormap x and y. */
    static final int FOLIAGE_X = 255 - Math.clamp((int) ((10f + 20f) * 255f / 50f), 0, 255);
    static final int FOLIAGE_Y = 255 - Math.clamp((int) (250f * 255f / 500f), 0, 255);
    /** The one twig look every wood shares (TFC draws each wood's own log textures). */
    static final String TWIG_LOOK_WOOD = "oak";

    /** Vanilla {@code SoundType.GRASS} (written as plain ids by {@link #yaml}). */
    static final DeviceSound GRASS = new DeviceSound("minecraft:block.grass.break", "minecraft:block.grass.step",
        "minecraft:block.grass.place", "minecraft:block.grass.hit", "minecraft:block.grass.fall");
    /** Vanilla {@code SoundType.CROP}. */
    static final DeviceSound CROP = new DeviceSound("minecraft:block.crop.break", "minecraft:block.grass.step",
        "minecraft:item.crop.plant", "minecraft:block.grass.hit", "minecraft:block.grass.fall");

    /** TFC's registrations the settings below copy (whitespace-normalised). */
    static final List<String> SOURCE_NEEDLES = List.of(
        "SAPLING((self, wood) -> new TFCSaplingBlock(wood.tree(), ExtendedProperties.of(MapColor.PLANT).noCollission().randomTicks().strength(0).sound(SoundType.GRASS)",
        "POTTED_SAPLING(wood -> new FlowerPotBlock(() -> (FlowerPotBlock) Blocks.FLOWER_POT, wood.getBlock(SAPLING), BlockBehaviour.Properties.ofFullCopy(Blocks.POTTED_ACACIA_SAPLING)))",
        "TWIG(wood -> GroundcoverBlock.twig(ExtendedProperties.of().strength(0.05F, 0.0F).sound(SoundType.WOOD).noCollission()",
        "FALLEN_LEAVES((self, wood) -> new FallenLeavesBlock(ExtendedProperties.of().strength(0.05F, 0.0F).noOcclusion().noCollission().isViewBlocking(TFCBlocks::never).sound(SoundType.CROP)");

    /** What {@link #run} wrote. */
    public record Result(List<String> woods, int blocks, int states, List<String> blockIds) {
    }

    private WoodPlantBlocks() {
    }

    /** True when the checkout has the wood plant blocks (the small test fixtures do not). */
    public static boolean available(TfcCheckout tfc) {
        return tfc.exists(WOOD_JAVA) && tfc.exists(BLOCKSTATES + "fallen_leaves/oak.json")
            && tfc.exists(MODELS + "block/groundcover/twig.json");
    }

    /** The woods, from TFC's sapling blockstates (sorted); they must be {@link HitboxDemands#BARREL_WOODS}. */
    public static List<String> woods(TfcCheckout tfc) {
        List<String> out = new ArrayList<>();
        for (String f : tfc.list(BLOCKSTATES + "sapling/")) {
            if (f.endsWith(".json")) {
                out.add(f.substring(f.lastIndexOf('/') + 1, f.length() - 5));
            }
        }
        out.sort(null);
        if (!out.equals(HitboxDemands.BARREL_WOODS.stream().sorted().toList())) {
            throw new DatagenException("TFC's woods " + out + " are not HitboxDemands.BARREL_WOODS");
        }
        return out;
    }

    /** Whether TFC's {@code Wood} is a conifer (its fallen leaves are brown needles). */
    static boolean conifer(TfcCheckout tfc, String wood) {
        String src = tfc.readString(WOOD_JAVA);
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\n\\s*" + wood.toUpperCase(java.util.Locale.ROOT)
            + "\\((true|false),").matcher(src);
        if (!m.find()) {
            throw new DatagenException(WOOD_JAVA + " has no Wood." + wood.toUpperCase(java.util.Locale.ROOT));
        }
        return Boolean.parseBoolean(m.group(1));
    }

    public static Result run(TfcCheckout tfc, OutputTree out) {
        checkSources(tfc);
        List<String> woods = woods(tfc);
        copySharedModels(tfc, out);
        int foliage = foliageColor(tfc);
        int states = 0;
        List<String> ids = new ArrayList<>();
        for (String wood : woods) {
            int color = conifer(tfc, wood) ? CONIFER_COLOR : foliage;
            writeAssets(tfc, out, wood, color);
            List<DeviceBlockSpec> specs = List.of(saplingSpec(wood), pottedSpec(wood), fallenLeavesSpec(wood), twigSpec(wood));
            Map<String, Object> root = new LinkedHashMap<>();
            for (DeviceBlockSpec spec : specs) {
                merge(root, yaml(spec));
                states += (int) spec.stateCount();
                ids.add(spec.id());
                String path = spec.id().substring("tfp:".length());
                JsonElement table = Json.rename(Json.parse(tfc.readString(TfcCheckout.MAIN + "data/" + Json.FROM_NS
                    + "/loot_table/blocks/" + path + ".json"), path));
                LootTableExport.delegateBlock(out, path, table, named -> { });
            }
            out.put(OUTPUT_DIR + wood + ".yml", Yaml.write(DeviceYaml.HEADER, root));
        }
        return new Result(woods, ids.size(), states, List.copyOf(ids));
    }

    static void checkSources(TfcCheckout tfc) {
        String src = tfc.readString(WOOD_JAVA).replaceAll("\\s+", " ");
        for (String n : SOURCE_NEEDLES) {
            if (!src.contains(n.replaceAll("\\s+", " "))) {
                throw new DatagenException(WOOD_JAVA + " no longer contains \"" + n + "\": re-check the wood plant blocks");
            }
        }
        String fallen = tfc.readString("src/main/java/net/dries007/tfc/common/blocks/wood/FallenLeavesBlock.java");
        if (!fallen.contains("public static final int MAX_LAYERS = " + MAX_LAYERS + ";")) {
            throw new DatagenException("FallenLeavesBlock.MAX_LAYERS is no longer " + MAX_LAYERS);
        }
    }

    // ---- specs ---------------------------------------------------------------------------------------------

    static DeviceBlockSpec saplingSpec(String wood) {
        String id = HitboxDemands.woodPlantId(HitboxDemands.SAPLING, wood);
        DeviceBlockSpec.Builder b = DeviceBlockSpec.builder(id);
        b.appearance(displayed(b, id, HitboxDemands.WOOD_PLANT_APPEARANCE, HitboxDemands.WOOD_PLANT_APPEARANCE, model("sapling", wood)));
        return b.everyState(s -> HitboxDemands.WOOD_PLANT_APPEARANCE, null)
            .settings(DeviceBlockSpec.Settings.of("0", "0", DeviceSound.WOOD, "hoe"))
            .blockItem(langKey(id)).build();
    }

    static DeviceBlockSpec pottedSpec(String wood) {
        String id = HitboxDemands.woodPlantId(HitboxDemands.POTTED_SAPLING, wood);
        DeviceBlockSpec.Builder b = DeviceBlockSpec.builder(id);
        b.appearance(displayed(b, id, HitboxDemands.WOOD_PLANT_APPEARANCE, HitboxDemands.WOOD_PLANT_APPEARANCE,
            model("potted_sapling", wood)));
        // the tool tag is dropped by yaml(): vanilla's potted plants are in no mineable tag
        return b.everyState(s -> HitboxDemands.WOOD_PLANT_APPEARANCE, null)
            .settings(DeviceBlockSpec.Settings.of("0", "0", DeviceSound.STONE, "pickaxe")).build();
    }

    static DeviceBlockSpec fallenLeavesSpec(String wood) {
        String id = HitboxDemands.woodPlantId(HitboxDemands.FALLEN_LEAVES, wood);
        DeviceBlockSpec.Builder b = DeviceBlockSpec.builder(id).property(DeviceProperty.range(LAYERS, 1, MAX_LAYERS, 1));
        for (int layers = 1; layers <= MAX_LAYERS; layers++) {
            String name = HitboxDemands.fallenLeavesAppearance(layers);
            String pattern = name.substring(0, name.indexOf('_')) + "_*";
            b.appearance(displayed(b, id, name, pattern, fallenLeavesModel(wood, layers)));
        }
        return b.everyState(s -> HitboxDemands.fallenLeavesAppearance(Integer.parseInt(s.get(LAYERS))), null)
            .settings(DeviceBlockSpec.Settings.of("0.05", "0", DeviceSound.WOOD, "hoe"))
            .blockItem(langKey(id)).build();
    }

    static DeviceBlockSpec twigSpec(String wood) {
        String id = HitboxDemands.woodPlantId(HitboxDemands.TWIG, wood);
        String pool = HitboxDemands.charcoalPitPool(id, HitboxDemands.WOOD_PLANT_APPEARANCE);
        if (pool == null) {
            throw new DatagenException(id + " has no pool in HitboxDemands.POOLS");
        }
        DeviceBlockSpec.Builder b = DeviceBlockSpec.builder(id);
        b.appearance(DeviceAppearance.modelled(HitboxDemands.WOOD_PLANT_APPEARANCE,
            DeviceAppearance.Carrier.pool(pool, HitboxDemands.TWIG_SHARE_KEY), model("twig", TWIG_LOOK_WOOD)));
        return b.everyState(s -> HitboxDemands.WOOD_PLANT_APPEARANCE, null)
            .settings(DeviceBlockSpec.Settings.of("0.05", "0", DeviceSound.WOOD, "axe"))
            .blockItem(langKey(id)).build();
    }

    /** The selector's carrier of one demand; a hitbox-only carrier draws {@code model} as an item display. */
    private static DeviceAppearance displayed(DeviceBlockSpec.Builder b, String id, String name, String demand, String model) {
        HitboxAllocation.Assignment a = HitboxAllocation.agreed(id, demand);
        return switch (a.mode()) {
            case HITBOX_ONLY -> {
                String item = renderItem(model);
                b.renderItem(item, model);
                if (a.hideTarget() != null && !a.pool()) {
                    throw new DatagenException(id + "#" + name + ": carrier " + a.state() + " needs a hide mapping; pick a row "
                        + "whose carrier never occurs");
                }
                yield DeviceAppearance.rendered(name, DeviceAppearance.Carrier.state(a.state()),
                    List.of(DeviceAppearance.Renderer.item(item).rotated(RENDER_ROTATION)));
            }
            case MODEL -> DeviceAppearance.modelled(name, DeviceAppearance.Carrier.state(a.state()), model);
            default -> throw new DatagenException(id + "#" + name + " got carrier mode " + a.mode());
        };
    }

    /** The display-only item of a model ({@code tfp:render/wood/sapling/oak}). */
    static String renderItem(String model) {
        return Json.TO_NS + ":render/" + model.substring(model.indexOf(":block/") + ":block/".length());
    }

    static String model(String kind, String wood) {
        return Json.TO_NS + ":block/wood/" + kind + "/" + wood;
    }

    /** Our fallen leaves model of {@code layers}: TFC's height (2 x layers px), the dense leaves cube at 8. */
    static String fallenLeavesModel(String wood, int layers) {
        return Json.TO_NS + ":block/wood/fallen_leaves/" + wood + "_height" + 2 * layers;
    }

    static String langKey(String id) {
        return "block." + id.replace(':', '.').replace('/', '.');
    }

    /** {@link DeviceYaml#literal} with the block sounds and single-state tweaks CraftEngine needs. */
    @SuppressWarnings("unchecked")
    static Map<String, Object> yaml(DeviceBlockSpec spec) {
        Map<String, Object> root = DeviceYaml.literal(spec);
        Map<String, Object> block = (Map<String, Object>) ((Map<String, Object>) root.get("blocks")).get(spec.id());
        Map<String, Object> settings = (Map<String, Object>) block.get("settings");
        settings.put("push_reaction", "destroy");
        // DeviceSound writes only the device sound sets; the grass and crop sets are plain vanilla ids
        String kind = spec.id().split("/")[1];
        DeviceSound sound = kind.equals(HitboxDemands.SAPLING) ? GRASS : kind.equals(HitboxDemands.FALLEN_LEAVES) ? CROP : null;
        if (sound != null) {
            Map<String, Object> sounds = new LinkedHashMap<>();
            sounds.put("break", sound.breakSound());
            sounds.put("step", sound.step());
            sounds.put("place", sound.place());
            sounds.put("hit", sound.hit());
            sounds.put("fall", sound.fall());
            settings.put("sounds", sounds);
        }
        if (spec.id().contains("/" + HitboxDemands.POTTED_SAPLING + "/")) {
            settings.remove("tags");
        }
        if (spec.properties().isEmpty()) {
            Map<String, Object> states = (Map<String, Object>) block.get("states");
            states.remove("properties");
            states.remove("variants");
        }
        return root;
    }

    @SuppressWarnings("unchecked")
    private static void merge(Map<String, Object> into, Map<String, Object> from) {
        for (Map.Entry<String, Object> e : from.entrySet()) {
            Object old = into.get(e.getKey());
            if (old == null) {
                into.put(e.getKey(), new LinkedHashMap<>((Map<String, Object>) e.getValue()));
            } else {
                Map<String, Object> target = (Map<String, Object>) old;
                for (Map.Entry<String, Object> x : ((Map<String, Object>) e.getValue()).entrySet()) {
                    if (target.putIfAbsent(x.getKey(), x.getValue()) != null) {
                        throw new DatagenException("wood plant entry " + e.getKey() + "." + x.getKey() + " twice");
                    }
                }
            }
        }
    }

    // ---- models and textures -------------------------------------------------------------------------------

    /** TFC's groundcover parents (fallen leaves heights, twig), renamed. */
    static void copySharedModels(TfcCheckout tfc, OutputTree out) {
        for (int layers = 1; layers < MAX_LAYERS; layers++) {
            copyModel(tfc, out, "block/groundcover/fallen_leaves_height" + 2 * layers);
        }
        copyModel(tfc, out, "block/groundcover/twig");
    }

    private static void copyModel(TfcCheckout tfc, OutputTree out, String path) {
        String src = MODELS + path + ".json";
        JsonObject m = Json.parse(tfc.readString(src), src).getAsJsonObject();
        m.remove("__comment__");
        out.put(RP + "models/" + path + ".json", Json.write(Json.rename(m)));
    }

    /** Checks TFC's blockstates and writes this wood's models, baked textures and item model. */
    static void writeAssets(TfcCheckout tfc, OutputTree out, String wood, int color) {
        checkBlockstate(tfc, "sapling/" + wood, Map.of("", "tfc:block/wood/sapling/" + wood));
        checkBlockstate(tfc, "potted_sapling/" + wood, Map.of("", "tfc:block/wood/potted_sapling/" + wood));
        Map<String, String> fallen = new TreeMap<>();
        for (int layers = 1; layers <= MAX_LAYERS; layers++) {
            fallen.put("layers=" + layers, layers == MAX_LAYERS ? "tfc:block/wood/leaves/dense_leaves/" + wood
                : "tfc:block/wood/fallen_leaves/" + wood + "_height" + 2 * layers);
        }
        checkBlockstate(tfc, "fallen_leaves/" + wood, fallen);
        // sapling: TFC's cross model (untinted texture, already in the pack)
        copyModel(tfc, out, "block/wood/sapling/" + wood);
        copyModel(tfc, out, "block/wood/potted_sapling/" + wood);
        requireTexture(tfc, out, "block/wood/sapling/" + wood);
        requireTexture(tfc, out, "block/dirt/entisol");
        // fallen leaves: the dense leaves texture with the colour baked in, TFC's height parents, the cube at 8 layers
        String baked = "block/wood/fallen_leaves/" + wood;
        out.put(RP + "textures/" + baked + ".png", tint(tfc.read(TEXTURES + "block/wood/leaves/dense_leaves/" + wood + ".png"), color));
        for (int layers = 1; layers <= MAX_LAYERS; layers++) {
            JsonObject m = new JsonObject();
            m.addProperty("parent", layers == MAX_LAYERS ? "minecraft:block/cube_all"
                : Json.TO_NS + ":block/groundcover/fallen_leaves_height" + 2 * layers);
            JsonObject t = new JsonObject();
            t.addProperty("all", Json.TO_NS + ":" + baked);
            m.add("textures", t);
            String path = fallenLeavesModel(wood, layers);
            out.put(RP + "models/" + path.substring(path.indexOf(':') + 1) + ".json", Json.write(m));
        }
        String bakedItem = "item/wood/fallen_leaves/" + wood;
        out.put(RP + "textures/" + bakedItem + ".png", tint(tfc.read(TEXTURES + "item/groundcover/fallen_leaves.png"), color));
        JsonObject item = new JsonObject();
        item.addProperty("parent", "minecraft:item/generated");
        JsonObject it = new JsonObject();
        it.addProperty("layer0", Json.TO_NS + ":" + bakedItem);
        item.add("textures", it);
        out.put(RP + "models/item/wood/fallen_leaves/" + wood + ".json", Json.write(item));
        // twig: the shared look is the oak twig; its log textures are not in the pack otherwise
        if (wood.equals(TWIG_LOOK_WOOD)) {
            copyModel(tfc, out, "block/wood/twig/" + wood);
            requireTexture(tfc, out, "block/wood/log/" + wood);
            requireTexture(tfc, out, "block/wood/log_top/" + wood);
        }
    }

    /** Copies a TFC texture into the pack unless another generator already wrote it. */
    private static void requireTexture(TfcCheckout tfc, OutputTree out, String path) {
        String src = TEXTURES + path + ".png";
        if (!tfc.exists(src)) {
            throw new DatagenException("TFC has no texture " + path);
        }
        String dst = RP + "textures/" + path + ".png";
        if (!out.contains(dst)) {
            out.put(dst, tfc.read(src));
        }
    }

    private static void checkBlockstate(TfcCheckout tfc, String block, Map<String, String> want) {
        String src = BLOCKSTATES + block + ".json";
        JsonObject variants = Json.parse(tfc.readString(src), src).getAsJsonObject().getAsJsonObject("variants");
        Map<String, String> got = new TreeMap<>();
        for (Map.Entry<String, JsonElement> e : variants.entrySet()) {
            got.put(e.getKey(), e.getValue().getAsJsonObject().get("model").getAsString());
        }
        if (!got.equals(new TreeMap<>(want))) {
            throw new DatagenException(src + " no longer draws the models WoodPlantBlocks writes: " + got);
        }
    }

    /** TFC's default foliage colour: {@code colormap/foliage.png} at the default climate. */
    static int foliageColor(TfcCheckout tfc) {
        BufferedImage map = image(tfc.read(TEXTURES + "colormap/foliage.png"));
        return map.getRGB(FOLIAGE_X, FOLIAGE_Y) & 0xFFFFFF;
    }

    /** The texture multiplied by {@code rgb} (vanilla's tint: each channel times the colour), alpha kept. */
    static byte[] tint(byte[] png, int rgb) {
        BufferedImage src = image(png);
        BufferedImage dst = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        int tr = rgb >> 16 & 0xFF;
        int tg = rgb >> 8 & 0xFF;
        int tb = rgb & 0xFF;
        for (int y = 0; y < src.getHeight(); y++) {
            for (int x = 0; x < src.getWidth(); x++) {
                int p = src.getRGB(x, y);
                int r = (p >> 16 & 0xFF) * tr / 255;
                int g = (p >> 8 & 0xFF) * tg / 255;
                int bl = (p & 0xFF) * tb / 255;
                dst.setRGB(x, y, p & 0xFF000000 | r << 16 | g << 8 | bl);
            }
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try {
            ImageIO.write(dst, "png", bytes);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return bytes.toByteArray();
    }

    private static BufferedImage image(byte[] png) {
        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(png));
            if (img == null) {
                throw new DatagenException("not a PNG");
            }
            return img;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** The ids of every block this generator writes, for callers that must not write them too. */
    public static Set<String> kinds() {
        return Set.of(HitboxDemands.SAPLING, HitboxDemands.POTTED_SAPLING, HitboxDemands.FALLEN_LEAVES, HitboxDemands.TWIG);
    }
}
