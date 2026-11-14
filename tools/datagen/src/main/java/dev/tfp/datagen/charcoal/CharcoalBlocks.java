/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/devices/LogPileBlock.java @ c3b4d9536 (AXIS, COUNT, the default
 *       state axis x / count 1),
 *       src/main/java/net/dries007/tfc/common/blocks/devices/BurningLogPileBlock.java @ c3b4d9536 (AXIS, COUNT, default),
 *       src/main/java/net/dries007/tfc/common/blocks/CharcoalPileBlock.java @ c3b4d9536 (LAYERS),
 *       src/main/java/net/dries007/tfc/common/blocks/TFCBlocks.java @ c3b4d9536 (LOG_PILE: strength(0.6F), SoundType.WOOD,
 *       flammable(60, 30); BURNING_LOG_PILE: strength(0.6F), SoundType.WOOD, flammableLikeLogs, noOcclusion; CHARCOAL_PILE:
 *       strength(0.2F), TFCSounds.CHARCOAL, PushReaction.DESTROY; all registerNoItem),
 *       src/main/java/net/dries007/tfc/common/blocks/ExtendedProperties.java @ c3b4d9536 (flammableLikeLogs = flammable(5, 5)),
 *       src/main/resources/assets/tfc/blockstates/log_pile.json, burning_log_pile.json, charcoal_pile.json,
 *       models/block/log_pile/*.json, models/block/charcoal_pile/*.json and textures/block/log_pile_front.png,
 *       log_pile_side.png, molten_lit.png, charcoal_pile.png @ c3b4d9536 (copied),
 *       src/main/resources/data/tfc/loot_table/blocks/charcoal_pile.json @ c3b4d9536 (one charcoal per break),
 *       src/generated/resources/data/minecraft/tags/block/mineable/axe.json, shovel.json @ c3b4d9536 (the tools)
 */
package dev.tfp.datagen.charcoal;

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
import dev.tfp.datagen.device.DeviceYaml;
import dev.tfp.datagen.hitbox.HitboxAllocation;
import dev.tfp.datagen.hitbox.HitboxDemands;
import dev.tfp.datagen.hitbox.TfcShapes;
import dev.tfp.datagen.hitbox.VanillaCandidates;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PAR-PROC-37: the log pile, the burning log pile and the charcoal pile as CraftEngine blocks
 * ({@code craftengine/tfp/configuration/charcoal/*.yml}). The plugin's {@code CharcoalPitDevice} (PAR-PROC-10) runs the two
 * log piles (device kinds {@code tfp:log_pile} and {@code tfp:burning_log_pile}) and writes every property of the three.
 *
 * <ul>
 *   <li>{@code tfp:log_pile} and {@code tfp:burning_log_pile}: {@code axis} x|z (TFC {@code HORIZONTAL_AXIS}, default x) and
 *       {@code count} 1..16 (TFC {@code COUNT_1_16}, default 1), 32 states each, each drawn on the carrier the selector
 *       picks for TFC's outline of that state ({@link HitboxDemands}) with TFC's model ({@code log_pile_<count>}, or
 *       {@code log_pile_<count>_burning}), turned y 90 for axis x like TFC's blockstates. A state the selector serves
 *       hitbox-only draws the model as an {@code entity_renderer} item display. TFC's settings (strength 0.6, wood, the
 *       axe tag) and flammability as CraftEngine's burn settings; no loot (TFC drops the logs through the block entity,
 *       here through the device) and no block item (TFC {@code registerNoItem}). TFC gives the burning pile no light.</li>
 *   <li>{@code tfp:charcoal_pile}: {@code layers} 1..8 with TFC's {@code charcoal_height<2n>} / {@code charcoal_block}
 *       models, strength 0.2, {@code tfp:block.charcoal.*} sounds, the shovel tag, push reaction destroy (CE
 *       {@code popped}) and TFC's loot (one {@code minecraft:charcoal} per break, {@code survives_explosion}); no device
 *       behaviour (the plugin's charcoal listener handles its layers), so it is written literally.</li>
 * </ul>
 */
public final class CharcoalBlocks {
    public static final String LOG_PILE = HitboxDemands.LOG_PILE_ID;
    public static final String BURNING = HitboxDemands.BURNING_LOG_PILE_ID;
    public static final String CHARCOAL_PILE = HitboxDemands.CHARCOAL_PILE_ID;
    static final String DEVICES = "src/main/java/net/dries007/tfc/common/blocks/devices/";
    static final String LOG_PILE_SOURCE = DEVICES + "LogPileBlock.java";
    static final String BURNING_SOURCE = DEVICES + "BurningLogPileBlock.java";
    static final String CHARCOAL_SOURCE = "src/main/java/net/dries007/tfc/common/blocks/CharcoalPileBlock.java";
    static final String BLOCKS = "src/main/java/net/dries007/tfc/common/blocks/TFCBlocks.java";
    static final String EXTENDED = "src/main/java/net/dries007/tfc/common/blocks/ExtendedProperties.java";
    static final String BLOCKSTATES = TfcCheckout.ASSETS + "blockstates/";
    static final String MODELS = TfcCheckout.ASSETS + "models/";
    static final String TEXTURES = TfcCheckout.ASSETS + "textures/";
    static final String PACK = "craftengine/tfp/";
    public static final String OUTPUT_DIR = PACK + "configuration/charcoal/";
    public static final String LOG_PILE_OUTPUT = OUTPUT_DIR + "log_pile.yml";
    public static final String BURNING_OUTPUT = OUTPUT_DIR + "burning_log_pile.yml";
    public static final String CHARCOAL_OUTPUT = OUTPUT_DIR + "charcoal_pile.yml";
    static final String RP = PACK + "resourcepack/assets/tfp/";
    /** CharcoalPitDevice.AXIS / COUNT / LAYERS (the plugin writes them). */
    public static final String AXIS = "axis";
    public static final String COUNT = "count";
    public static final String LAYERS = "layers";
    /**
     * The display turn of a hitbox-only state: the item display renderer draws a block model half a turn about y against
     * the block (ForgeDevices.OVERLAY_ROTATION), and a blockstate {@code y: 90} is a quarter turn clockwise seen from
     * above, which is -90 degrees about +y for the display.
     */
    static String renderRotation(String axis) {
        return "0," + (180 - HitboxDemands.logPileRotation(axis)) + ",0";
    }

    static final String CHARCOAL_RENDER_ROTATION = "0,180,0";
    /** CraftEngine's push reaction of TFC {@code PushReaction.DESTROY}. */
    static final String PUSH_DESTROY = "popped";

    private static final Pattern LOG_PILE_REGISTER = Pattern.compile(
        "registerNoItem\\(\"log_pile\", \\(\\) -> new LogPileBlock\\(ExtendedProperties\\.of\\(MapColor\\.WOOD\\)"
            + "\\.strength\\(0\\.6F\\)\\.sound\\(SoundType\\.WOOD\\)\\.flammable\\((\\d+), (\\d+)\\)");
    private static final Pattern BURNING_REGISTER = Pattern.compile(
        "registerNoItem\\(\"burning_log_pile\", \\(\\) -> new BurningLogPileBlock\\(ExtendedProperties\\.of\\(MapColor\\.WOOD\\)"
            + "\\.randomTicks\\(\\)\\.strength\\(0\\.6F\\)\\.sound\\(SoundType\\.WOOD\\)\\.flammableLikeLogs\\(\\)[^;]*\\.noOcclusion\\(\\)\\)\\);");
    private static final Pattern LIKE_LOGS = Pattern.compile(
        "public ExtendedProperties flammableLikeLogs\\(\\) \\{ return flammable\\((\\d+), (\\d+)\\)");
    private static final String CHARCOAL_REGISTER = "registerNoItem(\"charcoal_pile\", () -> new CharcoalPileBlock("
        + "Properties.of().mapColor(MapColor.COLOR_BLACK).strength(0.2F).sound(TFCSounds.CHARCOAL).pushReaction(PushReaction.DESTROY)";

    /** TFC's {@code flammable(flammability, fireSpreadSpeed)}: vanilla burn odds and ignite odds. */
    record Flammable(int flammability, int fireSpreadSpeed) {
    }

    private CharcoalBlocks() {
    }

    /** True when the checkout has the three blocks (the small test fixtures do not). */
    public static boolean available(TfcCheckout tfc) {
        return tfc.exists(LOG_PILE_SOURCE) && tfc.exists(CHARCOAL_SOURCE) && tfc.exists(BLOCKSTATES + "log_pile.json");
    }

    /** The render item of a hitbox-only look ({@code tfp:render/log_pile/log_pile_1}). */
    public static String renderItem(String model) {
        return Json.TO_NS + ":render/" + model.substring(model.indexOf(":block/") + ":block/".length());
    }

    /** Writes the three entries, their models and textures. Returns the number of server states (32 + 32 + 8). */
    public static int run(TfcCheckout tfc, OutputTree out) {
        checkSources(tfc);
        checkBlockstates(tfc);
        Flammable logFire = logPileFlammable(tfc);
        Flammable burningFire = burningFlammable(tfc);
        checkPools();
        copyModels(tfc, out);
        DeviceBlockSpec log = logPileSpec(tfc, LOG_PILE, false);
        DeviceBlockSpec burning = logPileSpec(tfc, BURNING, true);
        DeviceBlockSpec charcoal = charcoalSpec(tfc);
        out.put(LOG_PILE_OUTPUT, Yaml.write(DeviceYaml.HEADER, logPileYaml(log, logFire)));
        out.put(BURNING_OUTPUT, Yaml.write(DeviceYaml.HEADER, logPileYaml(burning, burningFire)));
        out.put(CHARCOAL_OUTPUT, Yaml.write(DeviceYaml.HEADER, charcoalYaml(charcoal)));
        return (int) (log.stateCount() + burning.stateCount() + charcoal.stateCount());
    }

    /**
     * The pool appearances (HitboxDemands.POOLS) are TFC's full cubes, except the nearly full log piles: the eight-layer
     * charcoal pile and the 16-log pile occlude (solid pool), the burning pile is noOcclusion (leaves pool).
     */
    static void checkPools() {
        Map<String, TfcShapes.Row> rows = TfcShapes.load();
        for (String axis : TfcShapes.LOG_PILE_AXES) {
            TfcShapes.Row full = rows.get(TfcShapes.logPileKey(axis, TfcShapes.LOG_PILE_MAX));
            TfcShapes.Row burning = rows.get(TfcShapes.burningKey(axis, 1));
            if (!full.selection().equals(dev.tfp.datagen.hitbox.Shape.FULL) || full.seeThrough()
                || !burning.selection().equals(dev.tfp.datagen.hitbox.Shape.FULL) || !burning.seeThrough()) {
                throw new DatagenException("the log pile pool rows are no longer TFC's full cubes");
            }
        }
        TfcShapes.Row charcoal = rows.get(HitboxDemands.charcoalPileKey(HitboxDemands.CHARCOAL_PILE_MAX));
        if (!charcoal.selection().equals(dev.tfp.datagen.hitbox.Shape.FULL)) {
            throw new DatagenException("the eight-layer charcoal pile is no longer TFC's full outline");
        }
    }

    /** TFC's properties and defaults of the three blocks. */
    static void checkSources(TfcCheckout tfc) {
        require(tfc, LOG_PILE_SOURCE, "public static final EnumProperty<Direction.Axis> AXIS = HORIZONTAL_AXIS;",
            "public static final IntegerProperty COUNT = TFCBlockStateProperties.COUNT_1_16;",
            "registerDefaultState(getStateDefinition().any().setValue(AXIS, Direction.Axis.X).setValue(COUNT, 1));",
            "super.createBlockStateDefinition(builder.add(AXIS).add(COUNT));");
        require(tfc, BURNING_SOURCE, "public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;",
            "public static final IntegerProperty COUNT = TFCBlockStateProperties.COUNT_1_16;",
            "registerDefaultState(getStateDefinition().any().setValue(AXIS, Direction.Axis.X).setValue(COUNT, 1));",
            "super.createBlockStateDefinition(builder.add(AXIS).add(COUNT));");
        require(tfc, CHARCOAL_SOURCE, "public static final IntegerProperty LAYERS = BlockStateProperties.LAYERS;",
            "super.createBlockStateDefinition(builder.add(LAYERS));");
        require(tfc, "src/main/java/net/dries007/tfc/common/blocks/TFCBlockStateProperties.java",
            "COUNT_1_16 = IntegerProperty.create(\"count\", 1, " + TfcShapes.LOG_PILE_MAX + ");");
        if (tfc.readString(CHARCOAL_SOURCE).contains("registerDefaultState")) {
            throw new DatagenException(CHARCOAL_SOURCE + " now sets a default state: re-check the charcoal pile default");
        }
        require(tfc, BLOCKS, CHARCOAL_REGISTER);
        if (tfc.readString(BLOCKS).matches("(?s).*register(NoItem)?\\(\"burning_log_pile\"[^;]*lightLevel.*")) {
            throw new DatagenException(BLOCKS + ": the burning log pile now emits light: add it to CharcoalBlocks");
        }
    }

    private static void require(TfcCheckout tfc, String file, String... needles) {
        String src = tfc.readString(file);
        for (String n : needles) {
            if (!src.contains(n)) {
                throw new DatagenException(file + " no longer contains \"" + n + "\": re-check the charcoal pit blocks");
            }
        }
    }

    /** TFC's blockstates: one variant per state, the model this generator draws, turned exactly as it turns it. */
    static void checkBlockstates(TfcCheckout tfc) {
        for (boolean burning : new boolean[] {false, true}) {
            Map<String, JsonObject> want = new TreeMap<>();
            for (String axis : TfcShapes.LOG_PILE_AXES) {
                for (int count = 1; count <= TfcShapes.LOG_PILE_MAX; count++) {
                    JsonObject v = new JsonObject();
                    v.addProperty("model", Json.FROM_NS + ":block/log_pile/log_pile_" + count + (burning ? "_burning" : ""));
                    if (HitboxDemands.logPileRotation(axis) != 0) {
                        v.addProperty("y", HitboxDemands.logPileRotation(axis));
                    }
                    want.put("axis=" + axis + ",count=" + count, v);
                }
            }
            checkVariants(tfc, burning ? "burning_log_pile" : "log_pile", want);
        }
        Map<String, JsonObject> want = new TreeMap<>();
        for (int layers = 1; layers <= HitboxDemands.CHARCOAL_PILE_MAX; layers++) {
            JsonObject v = new JsonObject();
            v.addProperty("model", Json.FROM_NS + HitboxDemands.charcoalPileModel(layers).substring(Json.TO_NS.length()));
            want.put("layers=" + layers, v);
        }
        checkVariants(tfc, "charcoal_pile", want);
    }

    private static void checkVariants(TfcCheckout tfc, String block, Map<String, JsonObject> want) {
        String src = BLOCKSTATES + block + ".json";
        JsonObject variants = Json.parse(tfc.readString(src), src).getAsJsonObject().getAsJsonObject("variants");
        Map<String, JsonObject> got = new TreeMap<>();
        if (variants != null) {
            variants.entrySet().forEach(e -> got.put(e.getKey(), e.getValue().getAsJsonObject()));
        }
        if (!got.equals(want)) {
            throw new DatagenException(src + " no longer draws the models CharcoalBlocks writes: " + got.keySet());
        }
    }

    /** TFC {@code flammable(60, 30)} of the log pile. */
    static Flammable logPileFlammable(TfcCheckout tfc) {
        Matcher m = LOG_PILE_REGISTER.matcher(tfc.readString(BLOCKS));
        if (!m.find()) {
            throw new DatagenException(BLOCKS + ": the log pile registration no longer matches " + LOG_PILE_REGISTER.pattern());
        }
        return new Flammable(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)));
    }

    /** TFC {@code flammableLikeLogs()} of the burning log pile ({@code flammable(5, 5)}). */
    static Flammable burningFlammable(TfcCheckout tfc) {
        if (!BURNING_REGISTER.matcher(tfc.readString(BLOCKS)).find()) {
            throw new DatagenException(BLOCKS + ": the burning log pile registration no longer matches "
                + BURNING_REGISTER.pattern());
        }
        Matcher m = LIKE_LOGS.matcher(tfc.readString(EXTENDED));
        if (!m.find()) {
            throw new DatagenException(EXTENDED + ": flammableLikeLogs no longer matches " + LIKE_LOGS.pattern());
        }
        return new Flammable(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)));
    }

    /** The appearance of one log pile state: our model on the selector's carrier, or a display on a hitbox-only one. */
    static DeviceAppearance logPileAppearance(String id, String axis, int count, boolean burning, DeviceBlockSpec.Builder b,
            Set<String> renderItems) {
        String name = HitboxDemands.logPileAppearance(axis, count);
        String model = HitboxDemands.logPileModel(count, burning);
        return appearance(id, name, model, renderRotation(axis), b, renderItems);
    }

    private static DeviceAppearance appearance(String id, String name, String model, String rotation, DeviceBlockSpec.Builder b,
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
                    List.of(DeviceAppearance.Renderer.item(item).rotated(rotation)));
            }
            case VANILLA -> throw new DatagenException(id + "#" + name + " got a vanilla look; CharcoalBlocks draws TFC's models");
        };
    }

    static DeviceBlockSpec logPileSpec(TfcCheckout tfc, String id, boolean burning) {
        DeviceBlockSpec.Builder b = DeviceBlockSpec.builder(id)
            .property(DeviceProperty.names(AXIS, TfcShapes.LOG_PILE_AXES, "x"))
            .property(DeviceProperty.range(COUNT, 1, TfcShapes.LOG_PILE_MAX, 1));
        Set<String> renderItems = new TreeSet<>();
        for (String axis : TfcShapes.LOG_PILE_AXES) {
            for (int count = 1; count <= TfcShapes.LOG_PILE_MAX; count++) {
                b.appearance(logPileAppearance(id, axis, count, burning, b, renderItems));
            }
        }
        b.everyState(s -> HitboxDemands.logPileAppearance(s.get(AXIS), Integer.parseInt(s.get(COUNT))), null)
            .settings(DeviceBlockSpec.Settings.fromTfc(tfc, id.substring(id.indexOf(':') + 1)))
            .deviceBehaviour();
        hideMappings(b, id, TfcShapes.LOG_PILE_AXES.stream()
            .flatMap(axis -> java.util.stream.IntStream.rangeClosed(1, TfcShapes.LOG_PILE_MAX)
                .mapToObj(c -> HitboxDemands.logPileAppearance(axis, c)))
            .toList());
        return b.build();
    }

    static DeviceBlockSpec charcoalSpec(TfcCheckout tfc) {
        DeviceBlockSpec.Builder b = DeviceBlockSpec.builder(CHARCOAL_PILE)
            .property(DeviceProperty.range(LAYERS, 1, HitboxDemands.CHARCOAL_PILE_MAX, 1));
        Set<String> renderItems = new TreeSet<>();
        List<String> names = new java.util.ArrayList<>();
        for (int layers = 1; layers <= HitboxDemands.CHARCOAL_PILE_MAX; layers++) {
            String name = HitboxDemands.charcoalPileAppearance(layers);
            names.add(name);
            b.appearance(appearance(CHARCOAL_PILE, name, HitboxDemands.charcoalPileModel(layers), CHARCOAL_RENDER_ROTATION, b,
                renderItems));
        }
        b.everyState(s -> HitboxDemands.charcoalPileAppearance(Integer.parseInt(s.get(LAYERS))), null)
            .settings(DeviceBlockSpec.Settings.fromTfc(tfc, "charcoal_pile"))
            .loot(DeviceLoot.fromTfc(tfc, "charcoal_pile"));
        hideMappings(b, CHARCOAL_PILE, names);
        return b.build();
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
     * The templated device entry, plus TFC's flammability as CraftEngine burn settings (deep-merged with the template's)
     * and the y 90 turn of TFC's blockstate on every axis-x model.
     */
    @SuppressWarnings("unchecked")
    static Map<String, Object> logPileYaml(DeviceBlockSpec spec, Flammable fire) {
        Map<String, Object> root = DeviceYaml.templated(spec);
        Map<String, Object> blocks = (Map<String, Object>) root.get("blocks");
        Map<String, Object> block = (Map<String, Object>) blocks.get(spec.id());
        Map<String, Object> settings = new LinkedHashMap<>();
        settings.put("burnable", true);
        // NeoForge: getFireSpreadSpeed is the vanilla ignite odds (CE burn_chance), getFlammability the burn odds
        // (CE fire_spread_chance), as ComposterBlock writes them.
        settings.put("burn_chance", fire.fireSpreadSpeed());
        settings.put("fire_spread_chance", fire.flammability());
        if (spec.id().equals(BURNING)) {
            settings.put("can_occlude", false); // TFC noOcclusion()
        }
        Map<String, Object> ordered = new LinkedHashMap<>();
        block.forEach((k, v) -> {
            ordered.put(k, v);
            if (k.equals("arguments")) {
                ordered.put("settings", settings);
            }
        });
        Map<String, Object> appearances = (Map<String, Object>) ((Map<String, Object>) ordered.get("states")).get("appearances");
        for (Map.Entry<String, Object> e : appearances.entrySet()) {
            Map<String, Object> app = (Map<String, Object>) e.getValue();
            String axis = e.getKey().substring(0, e.getKey().indexOf('_'));
            int y = HitboxDemands.logPileRotation(axis);
            if (app.get("model") instanceof String model && y != 0) {
                Map<String, Object> turned = new LinkedHashMap<>();
                turned.put("path", model);
                turned.put("y", y);
                app.put("model", turned);
            }
        }
        blocks.put(spec.id(), ordered);
        return root;
    }

    /** The literal charcoal pile entry with TFC's push reaction. */
    @SuppressWarnings("unchecked")
    static Map<String, Object> charcoalYaml(DeviceBlockSpec spec) {
        Map<String, Object> root = DeviceYaml.literal(spec);
        Map<String, Object> block = (Map<String, Object>) ((Map<String, Object>) root.get("blocks")).get(spec.id());
        ((Map<String, Object>) block.get("settings")).put("push_reaction", PUSH_DESTROY);
        return root;
    }

    /** TFC's log pile and charcoal pile models (renamed) and their textures. */
    static void copyModels(TfcCheckout tfc, OutputTree out) {
        Set<String> textures = new TreeSet<>();
        for (int count = 1; count <= TfcShapes.LOG_PILE_MAX; count++) {
            for (boolean burning : new boolean[] {false, true}) {
                String path = HitboxDemands.logPileModel(count, burning);
                JsonObject m = model(tfc, path);
                if (burning && (!m.has("parent") || m.has("elements")
                    || !m.get("parent").getAsString().equals(Json.FROM_NS + ":block/log_pile/log_pile_" + count))) {
                    throw new DatagenException(path + " no longer only retextures log_pile_" + count);
                }
                put(out, path, m, textures);
            }
        }
        for (int layers = 1; layers <= HitboxDemands.CHARCOAL_PILE_MAX; layers++) {
            String path = HitboxDemands.charcoalPileModel(layers);
            put(out, path, model(tfc, path), textures);
        }
        for (String t : textures) {
            String png = TEXTURES + t + ".png";
            if (!tfc.exists(png)) {
                throw new DatagenException("a charcoal pit model names tfc:" + t + ", which TFC has no texture for");
            }
            out.put(RP + "textures/" + t + ".png", tfc.read(png));
            if (tfc.exists(png + ".mcmeta")) {
                out.put(RP + "textures/" + t + ".png.mcmeta", tfc.read(png + ".mcmeta"));
            }
        }
    }

    private static JsonObject model(TfcCheckout tfc, String path) {
        String src = MODELS + path.substring(path.indexOf(':') + 1) + ".json";
        return Json.parse(tfc.readString(src), src).getAsJsonObject();
    }

    private static void put(OutputTree out, String path, JsonObject model, Set<String> textures) {
        JsonObject t = model.getAsJsonObject("textures");
        if (t != null) {
            for (Map.Entry<String, JsonElement> e : t.entrySet()) {
                String v = e.getValue().getAsString();
                if (v.startsWith(Json.FROM_NS + ":block/")) {
                    textures.add(v.substring(v.indexOf(':') + 1));
                }
            }
        }
        out.put(RP + "models/" + path.substring(path.indexOf(':') + 1) + ".json", Json.write(Json.rename(model)));
    }
}
