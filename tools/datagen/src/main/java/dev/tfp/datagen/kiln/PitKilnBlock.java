/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/devices/PitKilnBlock.java @ c3b4d9536 (STAGE 0..16, the default state
 *       stage 0, SHAPE_BY_LAYER, getDestroyProgress: the lit stage is not breakable),
 *       src/main/java/net/dries007/tfc/common/blocks/TFCBlocks.java @ c3b4d9536 (PIT_KILN: registerNoItem, MapColor.WOOD,
 *       SoundType.WOOD, strength 0.6, noOcclusion),
 *       src/main/resources/assets/tfc/blockstates/pit_kiln.json and models/block/pitkiln/pitkiln_0..16.json @ c3b4d9536
 */
package dev.tfp.datagen.kiln;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.tfp.datagen.DatagenException;
import dev.tfp.datagen.Json;
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
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PAR-POT-14: the pit kiln as the CraftEngine block {@code tfp:pit_kiln}
 * ({@code craftengine/tfp/configuration/kiln/pit_kiln.yml}). The plugin's {@code PitKilnDevice} (PAR-POT-03, device kind
 * {@code tfp:pit_kiln}) runs it and writes the property {@value #STAGE}.
 *
 * <ul>
 *   <li>{@code stage} 0..16 (TFC {@code PIT_KILN_STAGE}, default 0): 0..7 the thatch slab 1..8 px high, 8..15 the log rows
 *       (4 px wide each, two layers of four), 16 the lit full cube. One appearance per stage with TFC's
 *       {@code pitkiln_<stage>} model on the carrier the selector picks for TFC's outline of that stage
 *       ({@link HitboxDemands}); a stage the selector serves hitbox-only draws the model as an {@code entity_renderer}
 *       item display.</li>
 *   <li>TFC's settings: strength 0.6, {@code SoundType.WOOD}, {@code noOcclusion}, in no mineable tag (TFC's
 *       {@code registerNoItem} block is mined at the default speed); no block item and no loot: the device framework drops
 *       the slots ({@code DROP}). The lit stage is not breakable in survival (TFC {@code getDestroyProgress} 0): the plugin's
 *       {@code PitKilnListener} cancels that break.</li>
 * </ul>
 */
public final class PitKilnBlock {
    public static final String ID = HitboxDemands.PIT_KILN_ID;
    public static final String STAGE = "stage";
    public static final int LIT = HitboxDemands.PIT_KILN_LIT;
    public static final String OUTPUT = "craftengine/tfp/configuration/kiln/pit_kiln.yml";
    static final String BLOCK_SOURCE = "src/main/java/net/dries007/tfc/common/blocks/devices/PitKilnBlock.java";
    static final String BLOCKS = "src/main/java/net/dries007/tfc/common/blocks/TFCBlocks.java";
    static final String BLOCKSTATE = TfcCheckout.ASSETS + "blockstates/pit_kiln.json";
    static final String MODELS = TfcCheckout.ASSETS + "models/";
    static final String TEXTURES = TfcCheckout.ASSETS + "textures/";
    static final String MINEABLE = TfcCheckout.GENERATED + "data/minecraft/tags/block/mineable/";
    static final String LOOT = TfcCheckout.MAIN + "data/tfc/loot_table/blocks/pit_kiln.json";
    static final String RP = "craftengine/tfp/resourcepack/assets/tfp/";
    /** The display turn of a hitbox-only state (the item display renderer is half a turn about y against a block). */
    static final String RENDER_ROTATION = "0,180,0";
    /** TFC's strength(0.6f). */
    static final BigDecimal STRENGTH = new BigDecimal("0.6");
    private static final Pattern REGISTER = Pattern.compile(
        "registerNoItem\\(\"pit_kiln\", \\(\\) -> new PitKilnBlock\\(ExtendedProperties\\.of\\(MapColor\\.WOOD\\)"
            + "\\.sound\\(SoundType\\.WOOD\\)\\.strength\\(0\\.6f\\)\\.noOcclusion\\(\\)\\.blockEntity\\(");

    private PitKilnBlock() {
    }

    /** True when the checkout has the pit kiln (the small test fixtures do not). */
    public static boolean available(TfcCheckout tfc) {
        return tfc.exists(BLOCK_SOURCE) && tfc.exists(BLOCKSTATE);
    }

    /** The render item of a hitbox-only look ({@code tfp:render/pit_kiln/pitkiln_3}). */
    public static String renderItem(String model) {
        return Json.TO_NS + ":render/" + model.substring(model.indexOf(":block/") + ":block/".length());
    }

    /** Writes the entry, the 17 models and their textures. Returns the number of server states (17). */
    public static int run(TfcCheckout tfc, OutputTree out) {
        checkSource(tfc);
        checkBlockstate(tfc);
        copyModels(tfc, out);
        DeviceBlockSpec spec = spec();
        out.put(OUTPUT, Yaml.write(DeviceYaml.HEADER, yaml(spec)));
        return (int) spec.stateCount();
    }

    /** TFC's property, default, shapes, registration, tool tags (none) and loot (none). */
    static void checkSource(TfcCheckout tfc) {
        String src = tfc.readString(BLOCK_SOURCE);
        for (String needle : List.of("public static final IntegerProperty STAGE = TFCBlockStateProperties.PIT_KILN_STAGE;",
                "public static final int LIT = 16;", "registerDefaultState(getStateDefinition().any().setValue(STAGE, 0));",
                "builder.add(STAGE);", "super(properties, InventoryRemoveBehavior.DROP);",
                "return state.getValue(STAGE) == LIT ? 0F : super.getDestroyProgress(state, player, level, pos);")) {
            if (!src.contains(needle)) {
                throw new DatagenException(BLOCK_SOURCE + " no longer contains \"" + needle + "\": re-check the pit kiln entry");
            }
        }
        if (!tfc.readString("src/main/java/net/dries007/tfc/common/blocks/TFCBlockStateProperties.java")
                .contains("PIT_KILN_STAGE = IntegerProperty.create(\"stage\", 0, 16);")) {
            throw new DatagenException("TFCBlockStateProperties.PIT_KILN_STAGE is no longer stage 0..16");
        }
        if (!REGISTER.matcher(tfc.readString(BLOCKS)).find()) {
            throw new DatagenException(BLOCKS + ": the pit_kiln registration no longer matches " + REGISTER.pattern());
        }
        for (String t : List.of("axe", "hoe", "pickaxe", "shovel")) {
            if (tfc.readString(MINEABLE + t + ".json").contains("\"tfc:pit_kiln\"")) {
                throw new DatagenException("tfc:pit_kiln is now in minecraft:mineable/" + t + ": add the tool to PitKilnBlock");
            }
        }
        if (tfc.exists(LOOT)) {
            throw new DatagenException(LOOT + " now exists: PitKilnBlock writes no loot");
        }
    }

    /** TFC's blockstate: one variant per stage, the model {@code pitkiln_<stage>}, unrotated. */
    static void checkBlockstate(TfcCheckout tfc) {
        JsonObject variants = Json.parse(tfc.readString(BLOCKSTATE), BLOCKSTATE).getAsJsonObject().getAsJsonObject("variants");
        Map<String, JsonObject> want = new TreeMap<>();
        for (int stage = 0; stage <= LIT; stage++) {
            JsonObject v = new JsonObject();
            v.addProperty("model", Json.FROM_NS + ":block/pitkiln/pitkiln_" + stage);
            want.put("stage=" + stage, v);
        }
        Map<String, JsonObject> got = new TreeMap<>();
        if (variants != null) {
            variants.entrySet().forEach(e -> got.put(e.getKey(), e.getValue().getAsJsonObject()));
        }
        if (!got.equals(want)) {
            throw new DatagenException(BLOCKSTATE + " no longer draws pitkiln_<stage> unrotated: " + got.keySet());
        }
    }

    /** The spec: one appearance per stage on the selector's carrier. */
    static DeviceBlockSpec spec() {
        DeviceBlockSpec.Builder b = DeviceBlockSpec.builder(ID).property(DeviceProperty.range(STAGE, 0, LIT, 0));
        Set<String> renderItems = new TreeSet<>();
        Map<String, String> hidden = new TreeMap<>();
        for (int stage = 0; stage <= LIT; stage++) {
            String name = HitboxDemands.pitKilnAppearance(stage);
            String model = HitboxDemands.pitKilnModel(stage);
            String pool = HitboxDemands.charcoalPitPool(ID, name);
            if (pool != null) { // stages 10..16: the leaves pool, the selector's own pick (see HitboxDemands.POOLS)
                b.appearance(DeviceAppearance.modelled(name, DeviceAppearance.Carrier.pool(pool), model));
                continue;
            }
            HitboxAllocation.Assignment a = HitboxAllocation.agreed(ID, name);
            switch (a.mode()) {
                case MODEL -> b.appearance(DeviceAppearance.modelled(name, DeviceAppearance.Carrier.state(a.state()), model));
                case POOL -> throw new DatagenException(ID + "#" + name + " got a pool the declared POOLS do not name");
                case HITBOX_ONLY -> {
                    String item = renderItem(model);
                    if (renderItems.add(item)) {
                        b.renderItem(item, model);
                    }
                    b.appearance(DeviceAppearance.rendered(name, DeviceAppearance.Carrier.state(a.state()),
                        List.of(DeviceAppearance.Renderer.item(item).rotated(RENDER_ROTATION))));
                }
                case VANILLA -> throw new DatagenException(ID + "#" + name + " got a vanilla look; PitKilnBlock draws TFC's models");
            }
            if (a.hideTarget() != null && !a.pool() && hidden.putIfAbsent(a.state(), a.hideTarget()) == null) {
                b.stateMapping(a.state(), a.hideTarget());
            }
        }
        return b.everyState(s -> HitboxDemands.pitKilnAppearance(Integer.parseInt(s.get(STAGE))), null).deviceBehaviour().build();
    }

    /** The literal entry with TFC's settings first. */
    @SuppressWarnings("unchecked")
    static Map<String, Object> yaml(DeviceBlockSpec spec) {
        Map<String, Object> root = DeviceYaml.literal(spec);
        Map<String, Object> blocks = (Map<String, Object>) root.get("blocks");
        Map<String, Object> literal = (Map<String, Object>) blocks.get(spec.id());
        Map<String, Object> block = new LinkedHashMap<>();
        block.put("settings", settings());
        block.putAll(literal);
        blocks.put(spec.id(), block);
        return root;
    }

    /** TFC {@code strength(0.6f)}, {@code SoundType.WOOD}, {@code noOcclusion}. */
    static Map<String, Object> settings() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("hardness", STRENGTH);
        m.put("resistance", STRENGTH);
        m.put("sounds", DeviceSound.ofJava("SoundType.WOOD").yaml());
        m.put("can_occlude", false);
        m.put("is_suffocating", false);
        m.put("is_view_blocking", false);
        return m;
    }

    /** TFC's 17 models (renamed) and their textures. */
    static void copyModels(TfcCheckout tfc, OutputTree out) {
        Set<String> textures = new TreeSet<>();
        for (int stage = 0; stage <= LIT; stage++) {
            String path = HitboxDemands.pitKilnModel(stage);
            String src = MODELS + "block/pitkiln/pitkiln_" + stage + ".json";
            JsonObject m = Json.parse(tfc.readString(src), src).getAsJsonObject();
            JsonObject t = m.getAsJsonObject("textures");
            if (t == null) {
                throw new DatagenException(src + " names no textures");
            }
            for (Map.Entry<String, JsonElement> e : t.entrySet()) {
                Matcher x = Pattern.compile("tfc:(block/[a-z0-9_]+)").matcher(e.getValue().getAsString());
                if (!x.matches()) {
                    throw new DatagenException(src + ": texture " + e.getValue() + " is not a tfc:block texture");
                }
                textures.add(x.group(1));
            }
            out.put(RP + "models/" + path.substring(path.indexOf(':') + 1) + ".json", Json.write(Json.rename(m)));
        }
        for (String t : textures) {
            String png = TEXTURES + t + ".png";
            if (!tfc.exists(png)) {
                throw new DatagenException("a pit kiln model names tfc:" + t + ", which TFC has no texture for");
            }
            out.put(RP + "textures/" + t + ".png", tfc.read(png));
            if (tfc.exists(png + ".mcmeta")) {
                out.put(RP + "textures/" + t + ".png.mcmeta", tfc.read(png + ".mcmeta"));
            }
        }
    }
}
