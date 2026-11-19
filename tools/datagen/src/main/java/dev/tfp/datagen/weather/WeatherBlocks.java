/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/SnowPileBlock.java @ c3b4d9536 (a SnowLayerBlock: LAYERS 1..8),
 *       src/main/java/net/dries007/tfc/common/blocks/IcePileBlock.java @ c3b4d9536 (an IceBlock),
 *       src/main/java/net/dries007/tfc/common/blocks/SeaIceBlock.java @ c3b4d9536 (an IceBlock),
 *       src/main/java/net/dries007/tfc/common/blocks/IcicleBlock.java and ThinSpikeBlock.java @ c3b4d9536 (TIP, canSurvive),
 *       src/main/java/net/dries007/tfc/common/blocks/TFCBlocks.java @ c3b4d9536 (SEA_ICE, SNOW_PILE, ICE_PILE, ICICLE),
 *       src/main/resources/assets/tfc/blockstates/{snow_pile,ice_pile,sea_ice,icicle}.json, models/block/{sea_ice,thin_spike,
 *       thin_spike_tip,icicle,icicle_tip}.json and textures/block/sea_ice.png @ c3b4d9536 (copied),
 *       src/main/resources/data/tfc/loot_table/blocks/{snow_pile,ice_pile,sea_ice}.json @ c3b4d9536 (the drops, silk touch),
 *       src/generated/resources/data/minecraft/tags/block/mineable/{shovel,pickaxe}.json @ c3b4d9536 (the tools)
 */
package dev.tfp.datagen.weather;

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
import dev.tfp.datagen.hitbox.TfcShapes;
import dev.tfp.datagen.hitbox.VanillaCandidates;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * PAR-CLIM-04: TFC's weather blocks as CraftEngine blocks ({@code craftengine/tfp/configuration/weather/*.yml}). The
 * plugin's {@code SnowService} places them through {@code WorldBlocks} (the pile's covered block stays in the chunk data).
 *
 * <ul>
 *   <li>{@code tfp:snow_pile}: {@code layers} 1..8 (vanilla {@code SnowLayerBlock.LAYERS}). TFC draws vanilla's
 *       {@code snow_height<2n>} models; every layer count sits on the carrier the selector picks for its outline
 *       ({@link HitboxDemands}, hitbox-only, the model an {@code entity_renderer} display). Strength 0.1, snow sounds, the
 *       shovel tag, push reaction destroy, silk-touch loot through the loot runtime.</li>
 *   <li>{@code tfp:ice_pile} and {@code tfp:sea_ice}: translucent full cubes drawn on CraftEngine's leaves pool with
 *       vanilla's ice texture and TFC's {@code sea_ice} texture. Strength 0.5, glass sounds, friction 0.98, the pickaxe tag,
 *       silk-touch loot.</li>
 *   <li>{@code tfp:icicle}: {@code tip} (TFC {@code ThinSpikeBlock.TIP}, default false) with TFC's {@code icicle_tip} and
 *       {@code icicle} models on the selector's carriers; strength 0.4, glass sounds, the pickaxe tag, push reaction
 *       destroy, no loot. TFC's {@code canSurvive} (a sturdy ceiling above, or another icicle) is CraftEngine's
 *       {@code sturdy_base_block} facing up and stackable. TFC's fluid property is not kept (ledger).</li>
 * </ul>
 */
public final class WeatherBlocks {
    public static final String SNOW_PILE = HitboxDemands.SNOW_PILE_ID;
    public static final String ICE_PILE = HitboxDemands.ICE_PILE_ID;
    public static final String SEA_ICE = HitboxDemands.SEA_ICE_ID;
    public static final String ICICLE = HitboxDemands.ICICLE_ID;
    /** The property names the plugin writes. */
    public static final String LAYERS = "layers";
    public static final String TIP = "tip";
    static final String B = "src/main/java/net/dries007/tfc/common/blocks/";
    static final String BLOCKS = B + "TFCBlocks.java";
    static final String BLOCKSTATES = TfcCheckout.ASSETS + "blockstates/";
    static final String MODELS = TfcCheckout.ASSETS + "models/";
    static final String TEXTURES = TfcCheckout.ASSETS + "textures/";
    static final String PACK = "craftengine/tfp/";
    public static final String OUTPUT_DIR = PACK + "configuration/weather/";
    static final String RP = PACK + "resourcepack/assets/tfp/";
    /** The half turn every block-model item display needs to match the block (ForgeDevices.OVERLAY_ROTATION). */
    static final String RENDER_ROTATION = "0,180,0";
    static final String VANILLA_ICE_TEXTURE = "minecraft:block/ice";
    /** The block's settings and TFC's registration text that proves them. */
    private static final String SNOW_REGISTER = "register(\"snow_pile\", () -> new SnowPileBlock(ExtendedProperties.of(Blocks.SNOW)"
        + ".randomTicks().blockEntity(TFCBlockEntities.PILE).cloneItem(Blocks.SNOW)));";
    private static final String ICE_REGISTER = "register(\"ice_pile\", () -> new IcePileBlock(ExtendedProperties.of(Blocks.ICE)"
        + ".randomTicks().blockEntity(TFCBlockEntities.PILE).cloneItem(Blocks.ICE)));";
    private static final String SEA_REGISTER = "register(\"sea_ice\", () -> new SeaIceBlock(Properties.ofFullCopy(Blocks.ICE)"
        + ".isValidSpawn(TFCBlocks::onlyColdMobs)));";
    private static final String ICICLE_REGISTER = "register(\"icicle\", () -> new IcicleBlock(Properties.of().mapColor(MapColor.ICE)"
        + ".pushReaction(PushReaction.DESTROY).noLootTable().strength(0.4f).sound(SoundType.GLASS).noOcclusion()));";
    /** Vanilla {@code SoundType.SNOW} and {@code SoundType.GLASS} (volume 1, pitch 1: plain ids in CraftEngine). */
    static final Map<String, String> SNOW_SOUNDS = sounds("snow");
    static final Map<String, String> GLASS_SOUNDS = sounds("glass");
    /** Vanilla {@code Blocks.SNOW} and {@code Blocks.ICE} strength (TFC copies both). */
    static final String SNOW_STRENGTH = "0.1";
    static final String ICE_STRENGTH = "0.5";
    static final String ICICLE_STRENGTH = "0.4";
    static final String ICE_FRICTION = "0.98";
    /** The longest run of stacked icicles (TFC's {@code ThinSpikeBlock} has no limit; the weather caps them well below). */
    static final int ICICLE_MAX_HEIGHT = 16;

    private WeatherBlocks() {
    }

    private static Map<String, String> sounds(String group) {
        Map<String, String> m = new LinkedHashMap<>();
        for (String e : List.of("break", "step", "place", "hit", "fall")) {
            m.put(e, "minecraft:block." + group + "." + e);
        }
        return java.util.Collections.unmodifiableMap(m);
    }

    /** True when the checkout has the four blocks (the small test fixtures do not). */
    public static boolean available(TfcCheckout tfc) {
        return tfc.exists(B + "SnowPileBlock.java") && tfc.exists(B + "IcicleBlock.java")
            && tfc.exists(BLOCKSTATES + "snow_pile.json");
    }

    /** The display-only item that draws {@code model} on a hitbox-only carrier ({@code tfp:render/snow_pile/height4}). */
    public static String renderItem(String model) {
        return Json.TO_NS + ":render/" + model.substring(model.indexOf(":block/") + ":block/".length());
    }

    /** Vanilla's snow model of {@code layers}, as our model that parents it. */
    public static String snowModel(int layers) {
        return "tfp:block/snow_pile/height" + (2 * layers);
    }

    /** Writes the four entries, their models, textures and the three loot tables. Returns the number of server states. */
    public static int run(TfcCheckout tfc, OutputTree out) {
        checkSources(tfc);
        checkBlockstates(tfc);
        copyModels(tfc, out);
        DeviceBlockSpec snow = snowPileSpec(tfc);
        DeviceBlockSpec ice = iceSpec(tfc, ICE_PILE);
        DeviceBlockSpec sea = iceSpec(tfc, SEA_ICE);
        DeviceBlockSpec icicle = icicleSpec(tfc);
        out.put(OUTPUT_DIR + "snow_pile.yml", Yaml.write(DeviceYaml.HEADER, snowPileYaml(snow)));
        out.put(OUTPUT_DIR + "ice_pile.yml", Yaml.write(DeviceYaml.HEADER, iceYaml(ice)));
        out.put(OUTPUT_DIR + "sea_ice.yml", Yaml.write(DeviceYaml.HEADER, iceYaml(sea)));
        out.put(OUTPUT_DIR + "icicle.yml", Yaml.write(DeviceYaml.HEADER, icicleYaml(icicle)));
        for (String block : List.of("snow_pile", "ice_pile", "sea_ice")) {
            String src = TfcCheckout.MAIN + "data/" + Json.FROM_NS + "/loot_table/blocks/" + block + ".json";
            LootTableExport.delegateBlock(out, block, Json.rename(Json.parse(tfc.readString(src), src)), named -> { });
        }
        return (int) (snow.stateCount() + ice.stateCount() + sea.stateCount() + icicle.stateCount());
    }

    /** TFC's properties, defaults and registrations of the four blocks. */
    static void checkSources(TfcCheckout tfc) {
        String spike = tfc.readString(B + "ThinSpikeBlock.java").replaceAll("\\s+", " ");
        for (String n : List.of("public static final BooleanProperty TIP = TFCBlockStateProperties.TIP;",
                "registerDefaultState(getStateDefinition().any().setValue(TIP, false)",
                "return aboveState.getBlock() == this || aboveState.isFaceSturdy(level, abovePos, Direction.DOWN);",
                "return state.getValue(TIP) ? TIP_SHAPE : PILLAR_SHAPE;")) {
            require(spike, B + "ThinSpikeBlock.java", n);
        }
        String blocks = tfc.readString(BLOCKS).replaceAll("\\s+", " ");
        for (String n : List.of(SNOW_REGISTER, ICE_REGISTER, SEA_REGISTER, ICICLE_REGISTER)) {
            require(blocks, BLOCKS, n);
        }
        String snow = tfc.readString(B + "SnowPileBlock.java");
        require(snow, B + "SnowPileBlock.java", "public class SnowPileBlock extends SnowLayerBlock");
    }

    private static void require(String src, String file, String needle) {
        if (!src.contains(needle)) {
            throw new DatagenException(file + " no longer contains \"" + needle + "\": re-check the weather blocks");
        }
    }

    /** TFC's blockstates: the models this generator draws, unrotated. */
    static void checkBlockstates(TfcCheckout tfc) {
        Map<String, String> snow = new TreeMap<>();
        for (int layers = 1; layers <= TfcShapes.SNOW_PILE_MAX; layers++) {
            snow.put("layers=" + layers, layers == 8 ? "minecraft:block/snow_block" : "minecraft:block/snow_height" + 2 * layers);
        }
        checkVariants(tfc, "snow_pile", snow);
        checkVariants(tfc, "ice_pile", Map.of("", "minecraft:block/ice"));
        checkVariants(tfc, "sea_ice", Map.of("", Json.FROM_NS + ":block/sea_ice"));
        checkVariants(tfc, "icicle", Map.of("tip=true", Json.FROM_NS + ":block/icicle_tip",
            "tip=false", Json.FROM_NS + ":block/icicle"));
    }

    private static void checkVariants(TfcCheckout tfc, String block, Map<String, String> want) {
        String src = BLOCKSTATES + block + ".json";
        JsonObject variants = Json.parse(tfc.readString(src), src).getAsJsonObject().getAsJsonObject("variants");
        Map<String, String> got = new TreeMap<>();
        if (variants != null) {
            for (Map.Entry<String, JsonElement> e : variants.entrySet()) {
                JsonObject v = e.getValue().getAsJsonObject();
                if (v.size() != 1) {
                    throw new DatagenException(src + ": variant " + e.getKey() + " is turned or weighted now");
                }
                got.put(e.getKey(), v.get("model").getAsString());
            }
        }
        if (!got.equals(new TreeMap<>(want))) {
            throw new DatagenException(src + " no longer draws the models WeatherBlocks writes: " + got);
        }
    }

    /** One appearance: our model on the carrier or pool the selector picks, or a display on a hitbox-only carrier. */
    private static DeviceAppearance appearance(String id, String name, String model, DeviceBlockSpec.Builder b,
            Set<String> renderItems) {
        String pool = HitboxDemands.charcoalPitPool(id, name);
        if (pool != null) {
            return DeviceAppearance.modelled(name, DeviceAppearance.Carrier.pool(pool), model);
        }
        HitboxAllocation.Assignment a = HitboxAllocation.agreed(id, name);
        return switch (a.mode()) {
            case MODEL -> DeviceAppearance.modelled(name, DeviceAppearance.Carrier.state(a.state()), model);
            case POOL -> DeviceAppearance.modelled(name, DeviceAppearance.Carrier.pool(VanillaCandidates.poolName(a.state())),
                model);
            case HITBOX_ONLY -> {
                String item = renderItem(model);
                if (renderItems.add(item)) {
                    b.renderItem(item, model);
                }
                yield DeviceAppearance.rendered(name, DeviceAppearance.Carrier.state(a.state()),
                    List.of(DeviceAppearance.Renderer.item(item).rotated(RENDER_ROTATION)));
            }
            case VANILLA -> throw new DatagenException(id + "#" + name + " got a vanilla look; WeatherBlocks draws TFC's models");
        };
    }

    /** The selector's hide mapping of every explicit carrier the block uses (each once). */
    private static void hideMappings(DeviceBlockSpec.Builder b, String id, List<String> appearances) {
        Map<String, String> hidden = new TreeMap<>();
        for (String name : appearances) {
            if (HitboxDemands.charcoalPitPool(id, name) != null) {
                continue;
            }
            HitboxAllocation.Assignment a = HitboxAllocation.agreed(id, name);
            if (a.hideTarget() != null && !a.pool() && hidden.putIfAbsent(a.state(), a.hideTarget()) == null) {
                b.stateMapping(a.state(), a.hideTarget());
            }
        }
    }

    /**
     * The settings. The sound is a placeholder here ({@link DeviceBlockSpec.Settings} knows only the sounds of the other
     * devices); the YAML writers replace it with vanilla's snow or glass events.
     */
    private static DeviceBlockSpec.Settings settings(String strength, String tool) {
        return DeviceBlockSpec.Settings.of(strength, strength, DeviceSound.STONE, tool);
    }

    static DeviceBlockSpec snowPileSpec(TfcCheckout tfc) {
        requireTag(tfc, "shovel", "snow_pile");
        DeviceBlockSpec.Builder b = DeviceBlockSpec.builder(SNOW_PILE)
            .property(DeviceProperty.range(LAYERS, 1, TfcShapes.SNOW_PILE_MAX, 1));
        Set<String> renderItems = new TreeSet<>();
        List<String> names = new java.util.ArrayList<>();
        for (int layers = 1; layers <= TfcShapes.SNOW_PILE_MAX; layers++) {
            String name = HitboxDemands.snowPileAppearance(layers);
            names.add(name);
            b.appearance(appearance(SNOW_PILE, name, snowModel(layers), b, renderItems));
        }
        b.everyState(s -> HitboxDemands.snowPileAppearance(Integer.parseInt(s.get(LAYERS))), null)
            .settings(settings(SNOW_STRENGTH, "shovel"));
        hideMappings(b, SNOW_PILE, names);
        return b.build();
    }

    static DeviceBlockSpec iceSpec(TfcCheckout tfc, String id) {
        requireTag(tfc, "pickaxe", id.substring(id.indexOf(':') + 1));
        DeviceBlockSpec.Builder b = DeviceBlockSpec.builder(id);
        b.appearance(appearance(id, HitboxDemands.ICE_APPEARANCE, HitboxDemands.iceModel(id), b, new TreeSet<>()));
        b.everyState(s -> HitboxDemands.ICE_APPEARANCE, null).settings(settings(ICE_STRENGTH, "pickaxe"));
        hideMappings(b, id, List.of(HitboxDemands.ICE_APPEARANCE));
        return b.build();
    }

    static DeviceBlockSpec icicleSpec(TfcCheckout tfc) {
        requireTag(tfc, "pickaxe", "icicle");
        DeviceBlockSpec.Builder b = DeviceBlockSpec.builder(ICICLE).property(DeviceProperty.bool(TIP, false));
        Set<String> renderItems = new TreeSet<>();
        List<String> names = new java.util.ArrayList<>();
        for (boolean tip : new boolean[] {true, false}) {
            String name = HitboxDemands.icicleAppearance(tip);
            names.add(name);
            b.appearance(appearance(ICICLE, name, HitboxDemands.icicleModel(tip), b, renderItems));
        }
        b.everyState(s -> HitboxDemands.icicleAppearance(Boolean.parseBoolean(s.get(TIP))), null)
            .settings(settings(ICICLE_STRENGTH, "pickaxe"));
        hideMappings(b, ICICLE, names);
        return b.build();
    }

    /** The block is in exactly the {@code minecraft:mineable/<tool>} tag TFC lists it in. */
    private static void requireTag(TfcCheckout tfc, String tool, String block) {
        String src = TfcCheckout.GENERATED + "data/minecraft/tags/block/mineable/" + tool + ".json";
        for (JsonElement v : Json.parse(tfc.readString(src), src).getAsJsonObject().getAsJsonArray("values")) {
            if (v.isJsonPrimitive() && v.getAsString().equals(Json.FROM_NS + ":" + block)) {
                return;
            }
        }
        throw new DatagenException(Json.FROM_NS + ":" + block + " is no longer in minecraft:mineable/" + tool);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> blockOf(Map<String, Object> root, DeviceBlockSpec spec) {
        return (Map<String, Object>) ((Map<String, Object>) root.get("blocks")).get(spec.id());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> settingsOf(Map<String, Object> block, Map<String, String> sounds) {
        Map<String, Object> settings = (Map<String, Object>) block.get("settings");
        settings.put("sounds", new LinkedHashMap<>(sounds));
        return settings;
    }

    /** The literal snow pile entry: snow sounds, push reaction destroy. */
    static Map<String, Object> snowPileYaml(DeviceBlockSpec spec) {
        Map<String, Object> root = DeviceYaml.literal(spec);
        Map<String, Object> settings = settingsOf(blockOf(root, spec), SNOW_SOUNDS);
        settings.put("push_reaction", "destroy");
        return root;
    }

    /** The literal ice entry: glass sounds, ice friction, not occluding (TFC copies vanilla ice), one state. */
    @SuppressWarnings("unchecked")
    static Map<String, Object> iceYaml(DeviceBlockSpec spec) {
        Map<String, Object> root = DeviceYaml.literal(spec);
        Map<String, Object> block = blockOf(root, spec);
        Map<String, Object> settings = settingsOf(block, GLASS_SOUNDS);
        settings.put("friction", new BigDecimal(ICE_FRICTION));
        settings.put("can_occlude", false);
        singleState(block);
        return root;
    }

    /** The literal icicle entry: glass sounds, push reaction destroy, not occluding, CraftEngine's ceiling support. */
    @SuppressWarnings("unchecked")
    static Map<String, Object> icicleYaml(DeviceBlockSpec spec) {
        Map<String, Object> root = DeviceYaml.literal(spec);
        Map<String, Object> blocks = (Map<String, Object>) root.get("blocks");
        Map<String, Object> block = blockOf(root, spec);
        Map<String, Object> settings = settingsOf(block, GLASS_SOUNDS);
        settings.put("push_reaction", "destroy");
        settings.put("can_occlude", false);
        Map<String, Object> sturdy = new LinkedHashMap<>();
        sturdy.put("type", "sturdy_base_block");
        sturdy.put("direction", "up");
        sturdy.put("support_types", List.of("full"));
        sturdy.put("stackable", true);
        sturdy.put("max_height", ICICLE_MAX_HEIGHT);
        Map<String, Object> ordered = new LinkedHashMap<>();
        ordered.put("settings", settings);
        ordered.put("behavior", sturdy);
        ordered.put("states", block.get("states"));
        blocks.put(spec.id(), ordered);
        return root;
    }

    /** A block without properties is single-state: no properties and no variants. */
    @SuppressWarnings("unchecked")
    private static void singleState(Map<String, Object> block) {
        Map<String, Object> states = (Map<String, Object>) block.get("states");
        states.remove("properties");
        states.remove("variants");
    }

    /** TFC's sea ice and icicle models (renamed), the models of vanilla's snow heights and ice pile, the sea ice texture. */
    static void copyModels(TfcCheckout tfc, OutputTree out) {
        for (String m : List.of("sea_ice", "thin_spike", "thin_spike_tip", "icicle", "icicle_tip")) {
            String src = MODELS + "block/" + m + ".json";
            JsonObject model = Json.parse(tfc.readString(src), src).getAsJsonObject();
            model.remove("__comment__");
            if (m.startsWith("icicle")) {
                String tex = model.getAsJsonObject("textures").get("0").getAsString();
                if (!tex.equals(VANILLA_ICE_TEXTURE)) {
                    throw new DatagenException(src + " no longer draws vanilla's ice texture: " + tex);
                }
            }
            out.put(RP + "models/block/" + m + ".json", Json.write(Json.rename(model)));
        }
        String png = TEXTURES + "block/sea_ice.png";
        if (!tfc.exists(png)) {
            throw new DatagenException("TFC has no texture block/sea_ice");
        }
        out.put(RP + "textures/block/sea_ice.png", tfc.read(png));
        JsonObject ice = new JsonObject();
        ice.addProperty("parent", "minecraft:block/cube_all");
        JsonObject t = new JsonObject();
        t.addProperty("all", VANILLA_ICE_TEXTURE);
        ice.add("textures", t);
        out.put(RP + "models/block/ice_pile.json", Json.write(ice));
        for (int layers = 1; layers <= TfcShapes.SNOW_PILE_MAX; layers++) {
            JsonObject snow = new JsonObject();
            snow.addProperty("parent", layers == 8 ? "minecraft:block/snow_block" : "minecraft:block/snow_height" + 2 * layers);
            String path = snowModel(layers);
            out.put(RP + "models/" + path.substring(path.indexOf(':') + 1) + ".json", Json.write(snow));
        }
    }
}
