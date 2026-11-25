/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/GroundcoverBlock.java @ c3b4d9536 (shapes, settings),
 *       src/main/java/net/dries007/tfc/common/blocks/GroundcoverBlockType.java @ c3b4d9536 (types and shapes),
 *       src/main/java/net/dries007/tfc/common/blocks/rock/LooseRockBlock.java @ c3b4d9536 (COUNT),
 *       src/main/java/net/dries007/tfc/common/blocks/rock/Rock.java @ c3b4d9536 (LOOSE, MOSSY_LOOSE),
 *       src/main/java/net/dries007/tfc/common/blocks/TFCBlocks.java @ c3b4d9536 (GROUNDCOVER, SMALL_ORES),
 *       src/main/resources/assets/tfc/blockstates/{rock/loose,rock/mossy_loose,groundcover,ore}/*.json, models/block/groundcover,
 *       models/block/rock/loose, models/item/{groundcover,ore,rock/mossy_loose} @ c3b4d9536 (copied),
 *       src/main/resources/data/tfc/loot_table/blocks/{rock/loose,rock/mossy_loose,groundcover,ore}/*.json @ c3b4d9536 (drops)
 */
package dev.tfp.datagen.groundcover;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.tfp.datagen.DatagenException;
import dev.tfp.datagen.Json;
import dev.tfp.datagen.LootTableExport;
import dev.tfp.datagen.OutputTree;
import dev.tfp.datagen.TfcCheckout;
import dev.tfp.datagen.Yaml;
import dev.tfp.datagen.crop.CropBlocks;
import dev.tfp.datagen.device.DeviceSound;
import dev.tfp.datagen.device.DeviceYaml;
import dev.tfp.datagen.hitbox.HitboxAllocation;
import dev.tfp.datagen.hitbox.HitboxDemands;
import dev.tfp.datagen.visuals.VersionFamily;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PAR-WORLD-31: TFC's groundcover blocks as CraftEngine blocks ({@code craftengine/tfp/configuration/groundcover/}):
 * <ul>
 *   <li>{@code tfp:rock/loose/<rock>} and {@code tfp:rock/mossy_loose/<rock>} with TFC's {@code count} (1 to 3), hitbox-only on
 *   one shared carrier per count; the look is TFC's model as an item display (126 looks do not fit the button-like
 *   carriers);</li>
 *   <li>{@code tfp:ore/small_<ore>} and {@code tfp:groundcover/<type>}: TFC's model list (random y turn, several models for
 *   driftwood and sticks) on a carrier of their outline where the allocator has one free, else hitbox-only with the first
 *   model as an item display.</li>
 * </ul>
 * Settings from TFC: strength 0.05 / 0, no collision (the carriers have none), push reaction destroy; sounds stone (loose
 * rocks), nether ore (small ores), nether wart (groundcover). The drops are TFC's tables, run by the Paper loot runtime
 * (LootTableExport). Block items for the small ores, the mossy loose rocks and the groundcover types without a vanilla item;
 * the plain loose rock items stay in {@code drops.yml}. The ore deposits are cubes and come from the AssetPipeline.
 */
public final class GroundcoverCeBlocks {
    static final String BLOCKS = "src/main/java/net/dries007/tfc/common/blocks/";
    static final String TYPE_SOURCE = BLOCKS + "GroundcoverBlockType.java";
    static final String COVER_SOURCE = BLOCKS + "GroundcoverBlock.java";
    static final String ROCK_SOURCE = BLOCKS + "rock/Rock.java";
    static final String LOOSE_SOURCE = BLOCKS + "rock/LooseRockBlock.java";
    static final String TFC_BLOCKS = BLOCKS + "TFCBlocks.java";
    static final String BLOCKSTATES = TfcCheckout.ASSETS + "blockstates/";
    static final String LOOT = TfcCheckout.MAIN + "data/" + Json.FROM_NS + "/loot_table/blocks/";
    static final String LANG = TfcCheckout.ASSETS + "lang/";
    static final String PACK = "craftengine/tfp/";
    public static final String OUTPUT_DIR = PACK + "configuration/groundcover/";
    public static final String LOOSE_OUTPUT = OUTPUT_DIR + "loose_rocks.yml";
    public static final String SMALL_ORE_OUTPUT = OUTPUT_DIR + "small_ores.yml";
    public static final String GROUNDCOVER_OUTPUT = OUTPUT_DIR + "groundcover.yml";
    static final String LANG_NS = "tfp_groundcover";
    static final String LANG_RP = PACK + "resourcepack/assets/" + LANG_NS + "/lang/";
    /** TFC {@code strength(0.05F, 0.0F)} of every groundcover block. */
    public static final BigDecimal HARDNESS = new BigDecimal("0.05");
    public static final BigDecimal RESISTANCE = BigDecimal.ZERO;
    /** Vanilla {@code SoundType.NETHER_ORE} (volume and pitch 1.0): its own events, as plain ids. */
    static final Map<String, Object> NETHER_ORE = netherOre();
    /** CraftEngine version keys of the two families ({@link VersionFamily}): 26.x and 1.21.x. */
    static final String NEW_FAMILY = "$$>=26.1";
    static final String OLD_FAMILY = "$$<26.1";

    /**
     * The carrier of a demand in one version family: both profiles of the family must agree (the output does not depend
     * on the profile).
     */
    static HitboxAllocation.Assignment agreed(String blockId, String appearance, VersionFamily family) {
        HitboxAllocation.Assignment first = null;
        for (HitboxAllocation.Result r : HitboxAllocation.standard().values()) {
            if (r.family() != family) {
                continue;
            }
            HitboxAllocation.Assignment a = r.get(blockId, appearance);
            if (first == null) {
                first = a;
            } else if (!first.state().equals(a.state()) || first.mode() != a.mode()
                || !java.util.Objects.equals(first.hideTarget(), a.hideTarget())) {
                throw new DatagenException("hitbox carrier of " + blockId + "#" + appearance + " differs between the profiles of "
                    + family.id());
            }
        }
        if (first == null) {
            throw new DatagenException("no hitbox allocation for " + family.id());
        }
        return first;
    }

    /** TFC {@code TFCBlockStateProperties.WATER}: the {@code fluid} property of every groundcover block. */
    public static final List<String> FLUIDS = List.of("empty", "water", "salt_water");

    /** The {@code fluid} property (TFC {@code GroundcoverBlock.FLUID}, {@code LooseRockBlock}: empty, water, salt water). */
    static Map<String, Object> fluidProperty() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", "string");
        m.put("values", FLUIDS);
        m.put("default", "empty");
        return m;
    }

    /**
     * PAR-WORLD-58: the variant of one {@code fluid} value: the dry appearance when empty, else the waterlogged one with
     * CraftEngine's water fluid state (CE knows only water: salt water draws and flows as fresh water, ledger divergence).
     */
    static Map<String, Object> variant(String appearance, String fluid) {
        Map<String, Object> v = new LinkedHashMap<>();
        if (fluid.equals("empty")) {
            v.put("appearance", appearance);
            return v;
        }
        v.put("appearance", HitboxDemands.wet(appearance));
        v.put("settings", Map.of("fluid_state", "water"));
        return v;
    }

    /**
     * TFC {@code canSurvive}: the block below must be sturdy on its top face (a loose rock of count 1 needs a centre
     * support, two or three a full one; ours takes either for every count, ledger divergence).
     */
    static Map<String, Object> support(boolean looseRock) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", "sturdy_base_block");
        m.put("direction", "down");
        m.put("support_types", looseRock ? List.of("full", "center") : List.of("full"));
        return m;
    }

    /** The item display turn of a model drawn unrotated (CropBlocks.rotation(0)). */
    static final String RENDER_ROTATION = CropBlocks.rotation(0);
    /** TFC's groundcover types with a vanilla item (no block item of their own: the drop is the vanilla item). */
    public static final Set<String> VANILLA_ITEM_TYPES = Set.of("bone", "feather", "flint", "rotten_flesh", "salt_lick", "stick");

    private static final Pattern TYPE = Pattern.compile("^\\s+([A-Z_]+)\\(GroundcoverBlock\\.([A-Z_]+)(, ([^)]+\\)?))?\\)[,;]",
        Pattern.MULTILINE);

    private GroundcoverCeBlocks() {
    }

    private static Map<String, Object> netherOre() {
        Map<String, Object> m = new LinkedHashMap<>();
        for (String event : List.of("break", "step", "place", "hit", "fall")) {
            m.put(event, "minecraft:block.nether_ore." + event);
        }
        return java.util.Collections.unmodifiableMap(m);
    }

    /** True when the checkout has the groundcover (the small test fixtures do not). */
    public static boolean available(TfcCheckout tfc) {
        return tfc.exists(TYPE_SOURCE) && tfc.exists(BLOCKSTATES + "rock/loose/granite.json");
    }

    /** The counts of one run. */
    public record Counts(int blocks, int states, int modelCarriers, int hitboxOnly, int familySplit) {
    }

    /** Writes the three files, the models, item models, loot tables and names. */
    public static Counts run(TfcCheckout tfc, OutputTree out) {
        checkSources(tfc);
        Writer w = new Writer(tfc, out);
        Map<String, Object> loose = w.file();
        for (String kind : HitboxDemands.LOOSE_KINDS) {
            for (String rock : HitboxDemands.LOOSE_ROCKS) {
                w.looseRock(loose, kind, rock);
            }
        }
        out.put(LOOSE_OUTPUT, Yaml.write(DeviceYaml.HEADER, w.finish(loose)));
        Map<String, Object> ores = w.file();
        for (String ore : HitboxDemands.SMALL_ORES) {
            w.single(ores, HitboxDemands.smallOreId(ore), "ore/small_" + ore, NETHER_ORE, true);
        }
        out.put(SMALL_ORE_OUTPUT, Yaml.write(DeviceYaml.HEADER, w.finish(ores)));
        Map<String, Object> covers = w.file();
        for (String type : HitboxDemands.GROUNDCOVER_TYPES.keySet()) {
            w.single(covers, HitboxDemands.groundcoverId(type), "groundcover/" + type, DeviceSound.NETHER_WART.yaml(),
                !VANILLA_ITEM_TYPES.contains(type));
        }
        covers.put("block_state_mappings", w.mappings());
        out.put(GROUNDCOVER_OUTPUT, Yaml.write(DeviceYaml.HEADER, w.finish(covers)));
        writeLang(tfc, out);
        return new Counts(w.blocks, w.states, w.modelCarriers, w.hitboxOnly, w.familySplit);
    }

    /** TFC's types, rocks, ores, settings and properties the generator depends on. */
    static void checkSources(TfcCheckout tfc) {
        Map<String, String> types = types(tfc);
        if (!types.equals(HitboxDemands.GROUNDCOVER_TYPES)) {
            throw new DatagenException(TYPE_SOURCE + " lists " + types + " but HitboxDemands.GROUNDCOVER_TYPES is "
                + HitboxDemands.GROUNDCOVER_TYPES);
        }
        Set<String> vanilla = new TreeSet<>();
        Matcher m = TYPE.matcher(tfc.readString(TYPE_SOURCE));
        while (m.find()) {
            if (m.group(4) != null) {
                vanilla.add(m.group(1).toLowerCase(java.util.Locale.ROOT));
            }
        }
        if (!vanilla.equals(new TreeSet<>(VANILLA_ITEM_TYPES))) {
            throw new DatagenException(TYPE_SOURCE + ": the types with a vanilla item are " + vanilla);
        }
        Set<String> rocks = names(tfc, BLOCKSTATES + "rock/loose/", "");
        if (!rocks.equals(new TreeSet<>(HitboxDemands.LOOSE_ROCKS))) {
            throw new DatagenException("TFC's loose rocks are " + rocks + ", HitboxDemands.LOOSE_ROCKS differs");
        }
        Set<String> ores = names(tfc, BLOCKSTATES + "ore/", "small_");
        if (!ores.equals(new TreeSet<>(HitboxDemands.SMALL_ORES))) {
            throw new DatagenException("TFC's small ores are " + ores + ", HitboxDemands.SMALL_ORES differs");
        }
        require(tfc, ROCK_SOURCE, "LOOSE((rock, self) -> new LooseRockBlock(properties(rock).strength(0.05f, 0.0f).noCollission()), false),",
            "MOSSY_LOOSE((rock, self) -> new LooseRockBlock(properties(rock).strength(0.05f, 0.0f).noCollission()), false),",
            ".sound(SoundType.STONE)");
        require(tfc, LOOSE_SOURCE, "COUNT = TFCBlockStateProperties.COUNT_1_3;", "setValue(COUNT, 1)");
        require(tfc, COVER_SOURCE, "this(ExtendedProperties.of(MapColor.PLANT).strength(0.05F, 0.0F).sound(SoundType.NETHER_WART)"
            + ".noCollission().pushReaction(PushReaction.DESTROY).cloneItem(cover.getVanillaItem()), cover.getShape());");
        require(tfc, TFC_BLOCKS, "GroundcoverBlock.looseOre(Properties.of().mapColor(MapColor.GRASS).strength(0.05F, 0.0F)"
            + ".sound(SoundType.NETHER_ORE).noCollission().pushReaction(PushReaction.DESTROY))");
    }

    /** TFC {@code GroundcoverBlockType}: lower-case name to lower-case shape, in enum order. */
    static Map<String, String> types(TfcCheckout tfc) {
        Map<String, String> out = new LinkedHashMap<>();
        Matcher m = TYPE.matcher(tfc.readString(TYPE_SOURCE));
        while (m.find()) {
            out.put(m.group(1).toLowerCase(java.util.Locale.ROOT), m.group(2).toLowerCase(java.util.Locale.ROOT));
        }
        return out;
    }

    private static Set<String> names(TfcCheckout tfc, String dir, String prefix) {
        Set<String> out = new TreeSet<>();
        for (String f : tfc.list(dir)) {
            String name = f.substring(dir.length());
            if (!name.contains("/") && name.startsWith(prefix) && name.endsWith(".json")) {
                out.add(name.substring(prefix.length(), name.length() - ".json".length()));
            }
        }
        return out;
    }

    private static void require(TfcCheckout tfc, String file, String... needles) {
        String src = tfc.readString(file);
        for (String n : needles) {
            if (!src.contains(n)) {
                throw new DatagenException(file + " no longer contains \"" + n + "\": re-check GroundcoverCeBlocks");
            }
        }
    }

    /** TFC's variant list of one blockstate key, as CraftEngine {@code models} maps (renamed, the y turn kept). */
    static List<Map<String, Object>> models(JsonObject blockstate, String source, String key) {
        JsonElement v = blockstate.getAsJsonObject("variants").get(key);
        if (v == null) {
            throw new DatagenException(source + " has no variant " + key);
        }
        JsonArray list = v.isJsonArray() ? v.getAsJsonArray() : new JsonArray();
        if (!v.isJsonArray()) {
            list.add(v);
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (JsonElement e : list) {
            JsonObject o = e.getAsJsonObject();
            for (String k : o.keySet()) {
                if (!k.equals("model") && !k.equals("y")) {
                    throw new DatagenException(source + " variant " + key + " sets " + k + " (only model and y are carried)");
                }
            }
            Map<String, Object> model = new LinkedHashMap<>();
            model.put("path", Json.renameId(o.get("model").getAsString()));
            if (o.has("y")) {
                model.put("y", o.get("y").getAsInt());
            }
            out.add(model);
        }
        return out;
    }

    /** {@code tfp:render/<path under block/>}, the display-only item of a model. */
    public static String renderItem(String model) {
        return Json.TO_NS + ":render/" + model.substring(model.indexOf(":block/") + ":block/".length());
    }

    /** The settings of every groundcover block. */
    static Map<String, Object> settings(Map<String, Object> sounds) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("hardness", HARDNESS);
        m.put("resistance", RESISTANCE);
        m.put("sounds", new LinkedHashMap<>(sounds));
        m.put("push_reaction", "destroy");
        m.put("can_occlude", false);
        m.put("is_suffocating", false);
        m.put("is_view_blocking", false);
        return m;
    }

    /** Builds the entries; carriers' hide mappings are written once over the three files. */
    private static final class Writer {
        final TfcCheckout tfc;
        final OutputTree out;
        final Set<String> copied = new TreeSet<>();
        final Map<String, String> hidden121 = new TreeMap<>();
        final Map<String, String> hidden26 = new TreeMap<>();
        int familySplit;
        int blocks;
        int states;
        int modelCarriers;
        int hitboxOnly;

        Writer(TfcCheckout tfc, OutputTree out) {
            this.tfc = tfc;
            this.out = out;
        }

        Map<String, Object> file() {
            Map<String, Object> root = new LinkedHashMap<>();
            root.put("blocks", new LinkedHashMap<String, Object>());
            root.put("items", new LinkedHashMap<String, Object>());
            return root;
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> finish(Map<String, Object> root) {
            root.entrySet().removeIf(e -> ((Map<String, Object>) e.getValue()).isEmpty() && !e.getKey().equals("blocks"));
            return root;
        }

        JsonObject blockstate(String path) {
            String src = BLOCKSTATES + path + ".json";
            return Json.parse(tfc.readString(src), src).getAsJsonObject();
        }

        /** One appearance: the model list on a model carrier, or the first model as an item display on a hitbox-only one. */
        @SuppressWarnings("unchecked")
        Map<String, Object> appearance(Map<String, Object> root, String id, String name, List<Map<String, Object>> models) {
            for (Map<String, Object> model : models) {
                CropBlocks.copyModel(tfc, out, Json.FROM_NS + ((String) model.get("path")).substring(Json.TO_NS.length()), copied);
            }
            Map<String, Object> old = appearance(root, agreed(id, name, VersionFamily.V1_21), models, hidden121);
            Map<String, Object> current = appearance(root, agreed(id, name, VersionFamily.V26), models, hidden26);
            if (old.equals(current)) {
                return current;
            }
            // the families' carriers differ (26.x has newer vanilla states): a CraftEngine version selector
            Map<String, Object> selector = new LinkedHashMap<>();
            selector.put(NEW_FAMILY, current);
            selector.put("$$fallback", old);
            familySplit++;
            return selector;
        }

        @SuppressWarnings("unchecked")
        private Map<String, Object> appearance(Map<String, Object> root, HitboxAllocation.Assignment a,
                List<Map<String, Object>> models, Map<String, String> hidden) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("state", a.state());
            switch (a.mode()) {
                case MODEL -> {
                    m.put("models", models);
                    modelCarriers++;
                }
                case HITBOX_ONLY -> {
                    String model = (String) models.getFirst().get("path");
                    String item = renderItem(model);
                    Map<String, Object> items = (Map<String, Object>) root.get("items");
                    if (!items.containsKey(item)) {
                        Map<String, Object> call = new LinkedHashMap<>();
                        call.put("template", "tfp:item/render");
                        call.put("arguments", new LinkedHashMap<>(Map.of("render_model", model)));
                        items.put(item, call);
                    }
                    Map<String, Object> renderer = new LinkedHashMap<>();
                    renderer.put("item", item);
                    renderer.put("rotation", RENDER_ROTATION);
                    m.put("transparent", true);
                    m.put("entity_renderer", List.of(renderer));
                    hitboxOnly++;
                }
                default -> throw new DatagenException(a.demand().id() + " got a " + a.mode() + " carrier; GroundcoverCeBlocks "
                    + "draws a model or item displays");
            }
            if (a.hideTarget() != null && !a.pool()) {
                String prev = hidden.putIfAbsent(a.state(), a.hideTarget());
                if (prev != null && !prev.equals(a.hideTarget())) {
                    throw new DatagenException(a.state() + " hidden as both " + prev + " and " + a.hideTarget());
                }
            }
            return m;
        }

        /**
         * The hide mappings of every carrier used: those both families share as plain keys, the others under
         * {@code $$>=26.1} or {@code $$<26.1}.
         */
        Map<String, Object> mappings() {
            Map<String, Object> shared = new TreeMap<>();
            Map<String, Object> onlyNew = new TreeMap<>();
            Map<String, Object> onlyOld = new TreeMap<>();
            hidden26.forEach((k, v) -> (v.equals(hidden121.get(k)) ? shared : onlyNew).put(k, v));
            hidden121.forEach((k, v) -> {
                if (!v.equals(hidden26.get(k))) {
                    onlyOld.put(k, v);
                }
            });
            Map<String, Object> result = new LinkedHashMap<>(shared);
            if (!onlyNew.isEmpty()) {
                result.put(NEW_FAMILY, onlyNew);
            }
            if (!onlyOld.isEmpty()) {
                result.put(OLD_FAMILY, onlyOld);
            }
            return result;
        }

        @SuppressWarnings("unchecked")
        void looseRock(Map<String, Object> root, String kind, String rock) {
            String id = HitboxDemands.looseRockId(kind, rock);
            String path = "rock/" + kind + "/" + rock;
            JsonObject bs = blockstate(path);
            Map<String, Object> property = new LinkedHashMap<>();
            property.put("type", "int");
            property.put("range", "1~" + HitboxDemands.LOOSE_MAX);
            property.put("default", 1);
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("count", property);
            props.put("fluid", fluidProperty());
            Map<String, Object> appearances = new LinkedHashMap<>();
            Map<String, Object> variants = new LinkedHashMap<>();
            for (int count = 1; count <= HitboxDemands.LOOSE_MAX; count++) {
                String name = HitboxDemands.looseAppearance(count);
                List<Map<String, Object>> models = models(bs, BLOCKSTATES + path + ".json", "count=" + count);
                appearances.put(name, appearance(root, id, name, models));
                appearances.put(HitboxDemands.wet(name), appearance(root, id, HitboxDemands.wet(name), models));
                for (String fluid : FLUIDS) {
                    variants.put("count=" + count + ",fluid=" + fluid, variant(name, fluid));
                }
            }
            Map<String, Object> st = new LinkedHashMap<>();
            st.put("properties", props);
            st.put("appearances", appearances);
            st.put("variants", variants);
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("settings", settings(DeviceSound.STONE.yaml()));
            entry.put("behavior", support(true));
            entry.put("states", st);
            ((Map<String, Object>) root.get("blocks")).put(id, entry);
            loot(path);
            if (kind.equals("mossy_loose")) {
                blockItem(root, path, "block." + Json.TO_NS + "." + path.replace('/', '.'));
            }
            blocks++;
            states += HitboxDemands.LOOSE_MAX * FLUIDS.size();
        }

        @SuppressWarnings("unchecked")
        void single(Map<String, Object> root, String id, String path, Map<String, Object> sound, boolean blockItem) {
            JsonObject bs = blockstate(path);
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("settings", settings(sound));
            Map<String, Object> variants = new LinkedHashMap<>();
            for (String fluid : FLUIDS) {
                variants.put("fluid=" + fluid, variant(HitboxDemands.GROUNDCOVER_APPEARANCE, fluid));
            }
            Map<String, Object> st = new LinkedHashMap<>();
            st.put("properties", Map.of("fluid", fluidProperty()));
            List<Map<String, Object>> models = models(bs, BLOCKSTATES + path + ".json", "");
            Map<String, Object> appearances = new LinkedHashMap<>();
            appearances.put(HitboxDemands.GROUNDCOVER_APPEARANCE, appearance(root, id, HitboxDemands.GROUNDCOVER_APPEARANCE, models));
            String wet = HitboxDemands.wet(HitboxDemands.GROUNDCOVER_APPEARANCE);
            appearances.put(wet, appearance(root, id, wet, models));
            st.put("appearances", appearances);
            st.put("variants", variants);
            entry.put("behavior", support(false));
            entry.put("states", st);
            ((Map<String, Object>) root.get("blocks")).put(id, entry);
            loot(path);
            if (blockItem) {
                // TFC names the small ore item ({@code item.tfc.ore.small_<ore>}), a groundcover item by its block
                String kind = path.startsWith("ore/") ? "item." : "block.";
                blockItem(root, path, kind + Json.TO_NS + "." + path.replace('/', '.'));
            }
            blocks++;
            states += FLUIDS.size();
        }

        void loot(String path) {
            String src = LOOT + path + ".json";
            LootTableExport.delegateBlock(out, path, Json.rename(Json.parse(tfc.readString(src), src)), named -> { });
        }

        @SuppressWarnings("unchecked")
        void blockItem(Map<String, Object> root, String path, String langKey) {
            CropBlocks.copyModel(tfc, out, Json.FROM_NS + ":item/" + path, copied);
            Map<String, Object> call = new LinkedHashMap<>();
            call.put("template", "tfp:item/block_item");
            call.put("arguments", new LinkedHashMap<>(Map.of("item_lang_key", langKey)));
            ((Map<String, Object>) root.get("items")).put(Json.TO_NS + ":" + path, call);
        }
    }

    /** The groundcover block names ({@code block.tfp.groundcover.<type>}) in every TFC locale that has them. */
    private static void writeLang(TfcCheckout tfc, OutputTree out) {
        for (String f : tfc.list(LANG)) {
            if (!f.endsWith(".json")) {
                continue;
            }
            String locale = f.substring(LANG.length(), f.length() - ".json".length());
            JsonObject src = Json.parse(tfc.readString(f), f).getAsJsonObject();
            TreeMap<String, String> keys = new TreeMap<>();
            for (String type : HitboxDemands.GROUNDCOVER_TYPES.keySet()) {
                String key = "block." + Json.FROM_NS + ".groundcover." + type;
                if (src.has(key)) {
                    keys.put("block." + Json.TO_NS + ".groundcover." + type, src.get(key).getAsString());
                } else if (locale.equals("en_us")) {
                    throw new DatagenException("TFC has no English name " + key);
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
