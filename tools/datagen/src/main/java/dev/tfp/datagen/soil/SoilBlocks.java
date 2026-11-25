/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/soil/SoilBlockType.java @ c3b4d9536 (the Variant constants and the
 *       GRASS, CLAY_GRASS, ROOTED_DIRT, COARSE_DIRT and MUD properties: map colour, strength, sound, instrument,
 *       requiresCorrectToolForDrops),
 *       src/main/java/net/dries007/tfc/common/blocks/TFCBlocks.java @ c3b4d9536 (PEAT, PEAT_GRASS, RED/PINK/WHITE_KAOLIN_CLAY,
 *       KAOLIN_CLAY_GRASS properties),
 *       src/main/java/net/dries007/tfc/common/blocks/soil/ConnectedGrassBlock.java @ c3b4d9536 (the SNOWY property),
 *       src/main/java/net/dries007/tfc/client/TFCColors.java @ c3b4d9536 (getGrassColor: the item tint is the grass
 *       colormap at 10 C and 250 mm),
 *       src/main/resources/assets/tfc/blockstates/{grass,clay_grass,coarse_dirt,mud,rooted_dirt}/*.json, peat.json,
 *       peat_grass.json, kaolin_clay_grass.json, {red,pink,white}_kaolin_clay.json and the block and item models and
 *       textures they name @ c3b4d9536 (copied),
 *       src/main/resources/assets/tfc/models/block/{grass_top,grass_snowy_top}.json @ c3b4d9536 (the textures of the shared
 *       grass look),
 *       src/main/resources/assets/tfc/sounds.json (block.peat.*) and sounds/block/peat/*.ogg @ c3b4d9536 (copied),
 *       src/main/resources/assets/tfc/lang/*.json @ c3b4d9536 (the block names),
 *       src/main/resources/data/tfc/loot_table/blocks/** @ c3b4d9536 (the loot of each block)
 */
package dev.tfp.datagen.soil;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.tfp.core.climate.SeasonalColour;
import dev.tfp.datagen.CeBlockStates;
import dev.tfp.datagen.CeBlockStates.ModelRef;
import dev.tfp.datagen.DatagenException;
import dev.tfp.datagen.Json;
import dev.tfp.datagen.OutputTree;
import dev.tfp.datagen.TfcCheckout;
import dev.tfp.datagen.Yaml;
import dev.tfp.datagen.visuals.HideTargets;
import dev.tfp.datagen.visuals.VisualDemand;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;

/**
 * PAR-WORLD-04: the soil blocks the terrain needs beside the dirt and clay of the asset pipeline, as CraftEngine blocks
 * ({@code craftengine/tfp/configuration/soil/<soil>_set.yml}, {@code soil/peat.yml}, {@code soil/kaolin.yml}).
 *
 * <ul>
 *   <li>Per soil of {@code SoilBlockType.Variant}: {@code tfp:grass/<soil>}, {@code tfp:clay_grass/<soil>},
 *       {@code tfp:coarse_dirt/<soil>}, {@code tfp:mud/<soil>}, {@code tfp:rooted_dirt/<soil>}.</li>
 *   <li>{@code tfp:peat}, {@code tfp:peat_grass}, {@code tfp:red_kaolin_clay}, {@code tfp:pink_kaolin_clay},
 *       {@code tfp:white_kaolin_clay}, {@code tfp:kaolin_clay_grass}.</li>
 * </ul>
 *
 * <p><b>Full cubes</b> (coarse dirt, mud, rooted dirt, peat, the kaolin clays) take TFC's cube model and one
 * visual of the allocator each (their {@link VisualDemand} is appended to the pipeline's).
 *
 * <p><b>Grass blocks</b> (TFC {@code ConnectedGrassBlock}): the vanilla client tints only {@code minecraft:grass_block}
 * with the biome grass colour (the seasonal colour carrier), and that block has two states. So every grass block here has
 * one property, {@code snowy}, and all of them share the two vanilla states {@link #GRASS_STATES} with one shared model
 * each ({@link #SHARED_MODEL}, {@link #SHARED_SNOWY_MODEL}; the model of a shared explicit state must be identical in
 * every block, or CraftEngine aborts the block). The shared look is TFC's grass with all four sides connected: TFC's
 * {@code grass_top} texture on the top and the sides (tinted), TFC's snow on a snowy block, and the dirt of
 * {@link #SHARED_SOIL} below. It names no soil on the visible faces, so it serves every soil, clay, peat and kaolin. The
 * top turns at random like TFC's (four y rotations). TFC's {@code north/east/south/west} connection properties are not
 * kept: nothing could draw them. The block items keep TFC's per-soil item model, tinted with TFC's default grass colour.
 *
 * <p>Settings are TFC's, read from the Java sources (a drifted registration fails the run). Loot is TFC's table through
 * the {@code tfp:loot_table/*} templates. Names come from TFC's lang files ({@code assets/tfp_soil/lang}; the grass
 * names are the asset pipeline's). Peat keeps TFC's own sounds ({@code tfp_soil:block.peat.*}).
 */
public final class SoilBlocks {
    static final String SOIL_SOURCE = "src/main/java/net/dries007/tfc/common/blocks/soil/SoilBlockType.java";
    static final String BLOCKS_SOURCE = "src/main/java/net/dries007/tfc/common/blocks/TFCBlocks.java";
    static final String GRASS_SOURCE = "src/main/java/net/dries007/tfc/common/blocks/soil/ConnectedGrassBlock.java";
    static final String COLORS_SOURCE = "src/main/java/net/dries007/tfc/client/TFCColors.java";
    static final String BLOCKSTATES = TfcCheckout.ASSETS + "blockstates/";
    static final String MODELS = TfcCheckout.ASSETS + "models/";
    static final String TEXTURES = TfcCheckout.ASSETS + "textures/";
    static final String LANG = TfcCheckout.ASSETS + "lang/";
    static final String SOUNDS = TfcCheckout.ASSETS + "sounds.json";
    static final String LOOT = TfcCheckout.MAIN + "data/" + Json.FROM_NS + "/loot_table/blocks/";
    static final String COLORMAP = TEXTURES + "colormap/grass.png";
    static final String PACK = "craftengine/tfp/";
    public static final String OUTPUT_DIR = PACK + "configuration/soil/";
    static final String RP = PACK + "resourcepack/assets/" + Json.TO_NS + "/";
    /** The namespace of this generator's lang and sound files (the {@code tfp} ones belong to the asset pipeline). */
    static final String SOIL_NS = "tfp_soil";
    static final String SOIL_RP = PACK + "resourcepack/assets/" + SOIL_NS + "/";

    /** The per-soil block types, in emission order. */
    public static final List<String> GRASS_TYPES = List.of("grass", "clay_grass");
    public static final List<String> CUBE_TYPES = List.of("coarse_dirt", "mud", "rooted_dirt");
    /** The single grass blocks and the single full cubes. */
    public static final List<String> SINGLE_GRASS = List.of("peat_grass", "kaolin_clay_grass");
    public static final List<String> SINGLE_CUBES = List.of("peat", "red_kaolin_clay", "pink_kaolin_clay", "white_kaolin_clay");

    /** ConnectedGrassBlock.SNOWY, the one property kept. */
    public static final String SNOWY = "snowy";
    /**
     * The two vanilla states every grass block is drawn on, by {@code snowy}. They are {@link HideTargets#KEEP_VANILLA}:
     * never allocated to another block and never mapped away.
     */
    public static final Map<Boolean, String> GRASS_STATES = Map.of(false, keptGrassState(false), true, keptGrassState(true));
    /** The soil whose dirt is the bottom face and the break particle of the shared grass look. */
    public static final String SHARED_SOIL = "alfisol";
    public static final String SHARED_MODEL = Json.TO_NS + ":block/grass/shared";
    public static final String SHARED_SNOWY_MODEL = Json.TO_NS + ":block/grass/shared_snowy";
    /** TFC's {@code getGrassColor(null, tintIndex)}: the colormap at these default values. */
    static final float DEFAULT_TEMPERATURE = 10f;
    static final float DEFAULT_GROUNDWATER = 250f;

    /** Vanilla {@code MapColor} ids of the colours TFC gives these blocks. */
    static final Map<String, Integer> MAP_COLORS = Map.of(
        "GRASS", 1, "DIRT", 10, "COLOR_PINK", 20, "COLOR_RED", 28, "TERRACOTTA_WHITE", 36, "TERRACOTTA_BLACK", 51);
    /** The vanilla sound groups ({@code block.<name>.*}) of the vanilla {@code SoundType}s TFC uses here. */
    static final Map<String, String> VANILLA_SOUNDS = Map.of(
        "SoundType.GRASS", "grass", "SoundType.GRAVEL", "gravel", "SoundType.MUD", "mud",
        "SoundType.ROOTED_DIRT", "rooted_dirt", "SoundType.MUDDY_MANGROVE_ROOTS", "muddy_mangrove_roots");
    static final String PEAT_SOUND = "TFCSounds.PEAT";
    static final List<String> SOUND_KINDS = List.of("break", "step", "place", "hit", "fall");

    /** The vanilla parents of TFC's soil cube models (fluvisol and podzol have their own top texture). */
    static final Set<String> FULL_CUBE_PARENTS = Set.of("block/cube_all", "block/cube_column");

    private static final Pattern VARIANT_ENUM = Pattern.compile("enum Variant implements RegistrySoilVariant\\s*\\{([^;]*);");
    private static final Pattern STRENGTH = Pattern.compile("\\.strength\\((\\d+(?:\\.\\d+)?)[fF]?\\)");
    private static final Pattern MAP_COLOR = Pattern.compile("MapColor\\.([A-Z_]+)");
    private static final Pattern SOUND = Pattern.compile("\\.sound\\(((?:SoundType|TFCSounds)\\.[A-Z_]+)\\)");
    private static final Pattern INSTRUMENT = Pattern.compile("\\.instrument\\(NoteBlockInstrument\\.([A-Z_]+)\\)");

    /**
     * TFC's block properties of one block.
     *
     * @param sound       the Java constant, a key of {@link #VANILLA_SOUNDS} or {@link #PEAT_SOUND}
     * @param instrument  the note block instrument, lower case ({@code harp} is vanilla's default)
     * @param correctTool {@code requiresCorrectToolForDrops}
     */
    public record Props(BigDecimal strength, int mapColor, String sound, String instrument, boolean correctTool,
                        boolean randomTicks) {
    }

    private SoilBlocks() {
    }

    /** The {@link HideTargets#KEEP_VANILLA} grass block state with this {@code snowy} value (the visuals own the literal). */
    private static String keptGrassState(boolean snowy) {
        List<String> found = HideTargets.KEEP_VANILLA.stream().filter(st -> st.endsWith("[snowy=" + snowy + "]")).toList();
        if (found.size() != 1) {
            throw new IllegalStateException("HideTargets.KEEP_VANILLA has no single snowy=" + snowy + " grass state: "
                + HideTargets.KEEP_VANILLA);
        }
        return found.get(0);
    }

    /** True when the checkout has the soil block set (the small test fixtures do not). */
    public static boolean available(TfcCheckout tfc) {
        return tfc.exists(SOIL_SOURCE) && tfc.exists(BLOCKS_SOURCE) && tfc.exists(BLOCKSTATES + "peat_grass.json")
            && tfc.exists(MODELS + "item/grass_inv.json");
    }

    /** The soils, lower case, in the order of TFC's {@code SoilBlockType.Variant}. */
    public static List<String> variants(TfcCheckout tfc) {
        Matcher m = VARIANT_ENUM.matcher(tfc.readString(SOIL_SOURCE));
        if (!m.find()) {
            throw new DatagenException(SOIL_SOURCE + " has no Variant enum");
        }
        List<String> out = new ArrayList<>();
        for (String name : m.group(1).split(",")) {
            String n = name.strip();
            if (!n.matches("[A-Z_]+")) {
                throw new DatagenException(SOIL_SOURCE + ": unexpected Variant constant \"" + n + "\"");
            }
            out.add(n.toLowerCase(Locale.ROOT));
        }
        return List.copyOf(out);
    }

    /** Every block id (tfp path) this generator emits, in emission order. */
    public static List<String> blockIds(TfcCheckout tfc) {
        List<String> ids = new ArrayList<>();
        for (String soil : variants(tfc)) {
            GRASS_TYPES.forEach(t -> ids.add(t + "/" + soil));
            CUBE_TYPES.forEach(t -> ids.add(t + "/" + soil));
        }
        ids.addAll(List.of("peat", "peat_grass"));
        ids.addAll(List.of("red_kaolin_clay", "pink_kaolin_clay", "white_kaolin_clay", "kaolin_clay_grass"));
        return List.copyOf(ids);
    }

    /** Whether a block id of {@link #blockIds} is a grass block (drawn on {@link #GRASS_STATES}). */
    public static boolean isGrass(String id) {
        return SINGLE_GRASS.contains(id) || GRASS_TYPES.contains(type(id));
    }

    private static String type(String id) {
        int slash = id.indexOf('/');
        return slash < 0 ? id : id.substring(0, slash);
    }

    /**
     * Writes the blocks, their items, models, textures, names and sounds, and returns {@code demand} followed by the
     * appearance demand of the full cubes (the grass blocks take no allocated visual).
     */
    public static List<VisualDemand> run(TfcCheckout tfc, OutputTree out, List<VisualDemand> demand) {
        checkGrassSource(tfc);
        List<VisualDemand> all = new ArrayList<>(demand);
        SoilExtraBlocks.Hidden hidden = new SoilExtraBlocks.Hidden();
        writeSharedGrassModels(tfc, out);
        int tint = defaultGrassColour(tfc);
        for (String soil : variants(tfc)) {
            Map<String, Object> blocks = new LinkedHashMap<>();
            Map<String, Object> items = new LinkedHashMap<>();
            for (String t : GRASS_TYPES) {
                grass(tfc, out, t + "/" + soil, props(tfc, t + "/" + soil), tint, blocks, items);
            }
            for (String t : CUBE_TYPES) {
                all.add(cube(tfc, out, t + "/" + soil, props(tfc, t + "/" + soil), blocks, items));
            }
            // PAR-WORLD-42: duff, clay duff, grass path and muddy roots of the soil
            all.addAll(SoilExtraBlocks.write(tfc, out, soil, blocks, items, hidden));
            write(out, soil + "_set", blocks, items);
        }
        Map<String, Object> blocks = new LinkedHashMap<>();
        Map<String, Object> items = new LinkedHashMap<>();
        all.add(cube(tfc, out, "peat", props(tfc, "peat"), blocks, items));
        grass(tfc, out, "peat_grass", props(tfc, "peat_grass"), tint, blocks, items);
        write(out, "peat", blocks, items);
        blocks = new LinkedHashMap<>();
        items = new LinkedHashMap<>();
        for (String id : List.of("red_kaolin_clay", "pink_kaolin_clay", "white_kaolin_clay")) {
            all.add(cube(tfc, out, id, props(tfc, id), blocks, items));
        }
        grass(tfc, out, "kaolin_clay_grass", props(tfc, "kaolin_clay_grass"), tint, blocks, items);
        write(out, "kaolin", blocks, items);
        Map<String, Object> mappings = SoilExtraBlocks.mappings(hidden);
        if (!mappings.isEmpty()) {
            Map<String, Object> root = new LinkedHashMap<>();
            root.put("block_state_mappings", mappings);
            out.put(OUTPUT_DIR + "grass_path_carriers.yml", Yaml.write("Generated by tools/datagen (dev.tfp.datagen.soil.SoilExtraBlocks)"
                + " from TerraFirmaCraft @ " + TfcCheckout.PINNED_COMMIT.substring(0, 9) + ". Do not edit: fix the generator and "
                + "regenerate.", root));
        }
        writePeatSounds(tfc, out);
        writeLang(tfc, out);
        return List.copyOf(all);
    }

    private static void write(OutputTree out, String name, Map<String, Object> blocks, Map<String, Object> items) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("blocks", blocks);
        root.put("items", items);
        out.put(OUTPUT_DIR + name + ".yml", Yaml.write("Generated by tools/datagen (dev.tfp.datagen.soil.SoilBlocks) from "
            + "TerraFirmaCraft @ " + TfcCheckout.PINNED_COMMIT.substring(0, 9) + ". Do not edit: fix the generator and "
            + "regenerate.", root));
    }

    // ---- TFC's properties ----------------------------------------------------------------------------------

    /** TFC's properties of a block of {@link #blockIds}, read from its registration. */
    public static Props props(TfcCheckout tfc, String id) {
        String type = type(id);
        boolean perSoil = id.contains("/");
        String file = perSoil ? SOIL_SOURCE : BLOCKS_SOURCE;
        String constant = type.toUpperCase(Locale.ROOT);
        Pattern line = perSoil
            ? Pattern.compile("(?m)^\\s*" + constant + "\\(\\(self, variant\\) -> new (\\w+)\\((.*)$")
            : Pattern.compile("(?m)^\\s*public static final Id<Block> " + constant + " = register\\(\"" + type
                + "\", \\(\\) -> new (\\w+)\\((.*)$");
        Matcher m = line.matcher(tfc.readString(file));
        if (!m.find()) {
            throw new DatagenException(file + " no longer registers " + constant + " on one line");
        }
        String blockClass = m.group(1);
        String chain = m.group(2);
        boolean grass = isGrass(id);
        if (grass != blockClass.equals("ConnectedGrassBlock")) {
            throw new DatagenException(file + ": " + constant + " is now a " + blockClass + ": re-check SoilBlocks");
        }
        if (chain.contains("noOcclusion") || chain.contains("noCollission") || chain.contains("lightLevel")
            || chain.contains("blockEntity") || chain.contains("friction") || chain.contains("jumpFactor")) {
            throw new DatagenException(file + ": " + constant + " has a property SoilBlocks does not write: " + chain);
        }
        // TFC's mud has speedFactor(0.8f) (TFC/vanilla MudBlock). Not written: CraftEngine's speed_factor is predicted by the
        // client from the visual (a full cube here), so a server-side value only rubber-bands (ledger divergence).
        if (chain.contains("speedFactor") && !(type.equals("mud") && chain.contains(".speedFactor(0.8f)"))) {
            throw new DatagenException(file + ": " + constant + " has a speedFactor SoilBlocks does not expect: " + chain);
        }
        Matcher strength = STRENGTH.matcher(chain);
        Matcher color = MAP_COLOR.matcher(chain);
        Matcher sound = SOUND.matcher(chain);
        if (!strength.find() || !color.find() || !sound.find()) {
            throw new DatagenException(file + ": " + constant + " lacks strength, map colour or sound: " + chain);
        }
        Integer mapColor = MAP_COLORS.get(color.group(1));
        if (mapColor == null) {
            throw new DatagenException("unknown MapColor." + color.group(1) + " of " + constant + " (update SoilBlocks)");
        }
        if (!VANILLA_SOUNDS.containsKey(sound.group(1)) && !PEAT_SOUND.equals(sound.group(1))) {
            throw new DatagenException("unknown sound " + sound.group(1) + " of " + constant + " (update SoilBlocks)");
        }
        Matcher instrument = INSTRUMENT.matcher(chain);
        return new Props(new BigDecimal(strength.group(1)), mapColor, sound.group(1),
            instrument.find() ? instrument.group(1).toLowerCase(Locale.ROOT) : "harp",
            chain.contains(".requiresCorrectToolForDrops()"), chain.contains(".randomTicks()"));
    }

    private static void checkGrassSource(TfcCheckout tfc) {
        String src = tfc.readString(GRASS_SOURCE);
        for (String needle : List.of("public static final BooleanProperty SNOWY = BlockStateProperties.SNOWY;",
            "builder.add(NORTH, EAST, SOUTH, WEST, SNOWY);",
            ".setValue(WEST, false).setValue(SNOWY, false));")) {
            if (!src.contains(needle)) {
                throw new DatagenException(GRASS_SOURCE + " no longer contains \"" + needle + "\": re-check the grass states");
            }
        }
        String colors = tfc.readString(COLORS_SOURCE);
        for (String needle : List.of("return getClimateColor(GRASS_COLORS_CACHE, 10f, 250f); // Default values",
            "if (tintIndex == 0 || tintIndex == 1)")) {
            if (!colors.contains(needle)) {
                throw new DatagenException(COLORS_SOURCE + " no longer contains \"" + needle + "\": re-check the grass tint");
            }
        }
    }

    /**
     * The settings of a block: {@code tfp:settings/granular} (sounds, full cube, hardness = resistance = TFC's strength,
     * the shovel tag: every block here is in TFC's {@code mineable/shovel}) plus the keys the template lacks.
     */
    static Map<String, Object> settings(Props p) {
        Map<String, Object> args = new LinkedHashMap<>();
        boolean peat = PEAT_SOUND.equals(p.sound());
        args.put("sound_block_type", peat ? "gravel" : VANILLA_SOUNDS.get(p.sound()));
        args.put("granular_hardness", p.strength());
        args.put("granular_resistance", p.strength());
        args.put("granular_instrument", p.instrument());
        args.put("granular_map_color", p.mapColor());
        Map<String, Object> settings = new LinkedHashMap<>();
        settings.put("template", "tfp:settings/granular");
        settings.put("arguments", args);
        if (peat) {
            Map<String, Object> sounds = new LinkedHashMap<>();
            SOUND_KINDS.forEach(k -> sounds.put(k, SOIL_NS + ":block.peat." + k));
            settings.put("sounds", sounds);
        }
        if (p.correctTool()) {
            // As tfp:settings/rock: vanilla's tag and ours, because the TFC metal tools carry only tfp:tools/<tool>.
            settings.put("correct_tools", List.of("#minecraft:shovels", "#tfp:tools/shovel"));
        }
        return settings;
    }

    // ---- blocks --------------------------------------------------------------------------------------------

    private static VisualDemand cube(TfcCheckout tfc, OutputTree out, String id, Props props, Map<String, Object> blocks,
                                     Map<String, Object> items) {
        String model = cubeModel(tfc, id);
        copyModel(tfc, out, model.substring(Json.TO_NS.length() + 1));
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("settings", settings(props));
        entry.put("loot", loot(tfc, id));
        entry.put("state", Map.of("template", CeBlockStates.visualTemplate(id)));
        blocks.put(Json.TO_NS + ":" + id, entry);
        copyModel(tfc, out, "item/" + id);
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("template", "tfp:item/block_item");
        item.put("arguments", Map.of("item_lang_key", langKey(id)));
        items.put(Json.TO_NS + ":" + id, item);
        return new VisualDemand(id, "", List.of(new ModelRef(model, 0, 0, false, 1)));
    }

    /** The one model of a single-variant TFC blockstate, renamed ({@code tfp:block/...}). */
    static String cubeModel(TfcCheckout tfc, String id) {
        String src = BLOCKSTATES + id + ".json";
        JsonObject bs = Json.parse(tfc.readString(src), src).getAsJsonObject();
        JsonObject variants = bs.has("variants") ? bs.getAsJsonObject("variants") : null;
        if (variants == null || variants.size() != 1 || !variants.has("")) {
            throw new DatagenException(src + " is no longer a single-variant blockstate");
        }
        JsonElement v = variants.get("");
        if (v.isJsonArray()) {
            if (v.getAsJsonArray().size() != 1) {
                throw new DatagenException(src + " now has several models");
            }
            v = v.getAsJsonArray().get(0);
        }
        JsonObject model = v.getAsJsonObject();
        if (model.size() != 1 || !model.has("model")) {
            throw new DatagenException(src + " now turns or weights its model");
        }
        String name = model.get("model").getAsString();
        String parentSrc = MODELS + path(name) + ".json";
        JsonObject json = Json.parse(tfc.readString(parentSrc), parentSrc).getAsJsonObject();
        String parent = json.has("parent") ? json.get("parent").getAsString() : "";
        if (!FULL_CUBE_PARENTS.contains(parent.substring(parent.indexOf(':') + 1))) {
            throw new DatagenException(parentSrc + " is no longer a full opaque cube (parent " + parent + ")");
        }
        return Json.renameId(name);
    }

    private static void grass(TfcCheckout tfc, OutputTree out, String id, Props props, int tint, Map<String, Object> blocks,
                              Map<String, Object> items) {
        checkGrassBlockstate(tfc, id);
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("settings", settings(props));
        entry.put("loot", loot(tfc, id));
        entry.put("states", grassStates());
        blocks.put(Json.TO_NS + ":" + id, entry);
        copyModel(tfc, out, "item/" + id);
        // TFC tints the item's grass faces (tintindex 0) with its default grass colour; the list position is the tintindex.
        Map<String, Object> tintEntry = new LinkedHashMap<>();
        tintEntry.put("type", "minecraft:constant");
        tintEntry.put("value", tint);
        Map<String, Object> model = new LinkedHashMap<>();
        model.put("type", "minecraft:model");
        model.put("path", Json.TO_NS + ":item/" + id);
        model.put("tints", List.of(tintEntry));
        Map<String, Object> behavior = new LinkedHashMap<>();
        behavior.put("type", "block_item");
        behavior.put("block", Json.TO_NS + ":" + id);
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("material", "paper");
        item.put("data", Map.of("item_name", "<!i><lang:" + langKey(id) + ">"));
        item.put("model", model);
        item.put("behavior", behavior);
        items.put(Json.TO_NS + ":" + id, item);
    }

    /** The states section every grass block shares, byte for byte (see the class comment). */
    static Map<String, Object> grassStates() {
        Map<String, Object> snowy = new LinkedHashMap<>();
        snowy.put("type", "boolean");
        snowy.put("default", false);
        Map<String, Object> appearances = new LinkedHashMap<>();
        Map<String, Object> variants = new LinkedHashMap<>();
        for (boolean value : new boolean[] {false, true}) {
            String model = value ? SHARED_SNOWY_MODEL : SHARED_MODEL;
            List<ModelRef> turns = new ArrayList<>();
            for (int y : new int[] {90, 0, 180, 270}) { // TFC's order of the four top rotations
                turns.add(new ModelRef(model, 0, y, false, 1));
            }
            String name = SNOWY + "_" + value;
            appearances.put(name, CeBlockStates.appearance(Map.of("state", GRASS_STATES.get(value)), turns));
            variants.put(SNOWY + "=" + value, Map.of("appearance", name));
        }
        Map<String, Object> states = new LinkedHashMap<>();
        states.put("properties", Map.of(SNOWY, snowy));
        states.put("appearances", appearances);
        states.put("variants", variants);
        return states;
    }

    /**
     * TFC's connected grass blockstate: the bottom, a random turn of the top per {@code snowy}, and per side the top model
     * when connected, else the side model. The shared look draws the connected case of every side.
     */
    static void checkGrassBlockstate(TfcCheckout tfc, String id) {
        String src = BLOCKSTATES + id + ".json";
        JsonObject bs = Json.parse(tfc.readString(src), src).getAsJsonObject();
        if (!bs.has("multipart")) {
            throw new DatagenException(src + " is no longer a multipart blockstate");
        }
        Set<String> models = new TreeSet<>();
        Set<String> properties = new TreeSet<>();
        for (JsonElement part : bs.getAsJsonArray("multipart")) {
            JsonObject p = part.getAsJsonObject();
            if (p.has("when")) {
                properties.addAll(p.getAsJsonObject("when").keySet());
            }
            JsonElement apply = p.get("apply");
            JsonArray list = apply.isJsonArray() ? apply.getAsJsonArray() : new JsonArray();
            if (!apply.isJsonArray()) {
                list.add(apply);
            }
            list.forEach(a -> models.add(a.getAsJsonObject().get("model").getAsString()));
        }
        String base = Json.FROM_NS + ":block/" + id + "/";
        Set<String> expected = new TreeSet<>();
        for (String part : List.of("bottom", "top", "side", "snowy_top", "snowy_side")) {
            expected.add(base + part);
        }
        if (!models.equals(expected) || !properties.equals(Set.of(SNOWY, "north", "east", "south", "west"))) {
            throw new DatagenException(src + " is no longer TFC's connected grass (models " + models + ", properties "
                + properties + ")");
        }
        String top = MODELS + "block/" + id + "/top.json";
        JsonObject json = Json.parse(tfc.readString(top), top).getAsJsonObject();
        if (!(Json.FROM_NS + ":block/grass_top").equals(json.get("parent").getAsString())) {
            throw new DatagenException(top + " no longer draws tfc:block/grass_top");
        }
    }

    /**
     * The two shared grass models: a cube with the dirt of {@link #SHARED_SOIL} below and TFC's grass top texture
     * (tintindex 0, as vanilla's grass block: the client tints it with the biome grass colour) or TFC's snowy top texture
     * (untinted) on the other five faces. The textures are read from TFC's {@code grass_top} and {@code grass_snowy_top}
     * parent models and from the bottom model of the shared soil.
     */
    static void writeSharedGrassModels(TfcCheckout tfc, OutputTree out) {
        String bottomSrc = MODELS + "block/grass/" + SHARED_SOIL + "/bottom.json";
        String dirt = Json.parse(tfc.readString(bottomSrc), bottomSrc).getAsJsonObject().getAsJsonObject("textures")
            .get("texture").getAsString();
        String grass = overlay(tfc, "block/grass_top", true);
        String snow = overlay(tfc, "block/grass_snowy_top", false);
        for (String texture : List.of(dirt, grass, snow)) {
            copyTexture(tfc, out, texture);
        }
        out.put(RP + "models/" + path(SHARED_MODEL) + ".json", Json.write(sharedModel(dirt, grass, true)));
        out.put(RP + "models/" + path(SHARED_SNOWY_MODEL) + ".json", Json.write(sharedModel(dirt, snow, false)));
    }

    /** The {@code overlay} texture of one of TFC's grass face models, whose one face is tinted or not as expected. */
    private static String overlay(TfcCheckout tfc, String model, boolean tinted) {
        String src = MODELS + model + ".json";
        JsonObject json = Json.parse(tfc.readString(src), src).getAsJsonObject();
        JsonArray elements = json.getAsJsonArray("elements");
        JsonObject face = elements.size() == 1 ? elements.get(0).getAsJsonObject().getAsJsonObject("faces").getAsJsonObject("north") : null;
        if (face == null || !"#overlay".equals(face.get("texture").getAsString()) || face.has("tintindex") != tinted) {
            throw new DatagenException(src + " is no longer one " + (tinted ? "tinted" : "untinted") + " overlay face");
        }
        return json.getAsJsonObject("textures").get("overlay").getAsString();
    }

    static JsonObject sharedModel(String dirt, String cover, boolean tinted) {
        JsonObject textures = new JsonObject();
        textures.addProperty("particle", Json.renameId(dirt));
        textures.addProperty("bottom", Json.renameId(dirt));
        textures.addProperty("cover", Json.renameId(cover));
        JsonObject faces = new JsonObject();
        for (String side : List.of("down", "up", "north", "south", "west", "east")) {
            JsonObject face = new JsonObject();
            boolean bottom = side.equals("down");
            face.addProperty("texture", bottom ? "#bottom" : "#cover");
            face.addProperty("cullface", side);
            if (tinted && !bottom) {
                face.addProperty("tintindex", 0);
            }
            faces.add(side, face);
        }
        JsonObject element = new JsonObject();
        element.add("from", triple(0));
        element.add("to", triple(16));
        element.add("faces", faces);
        JsonArray elements = new JsonArray();
        elements.add(element);
        JsonObject model = new JsonObject();
        model.addProperty("parent", "minecraft:block/block");
        model.add("textures", textures);
        model.add("elements", elements);
        return model;
    }

    private static JsonArray triple(int v) {
        JsonArray a = new JsonArray();
        for (int i = 0; i < 3; i++) {
            a.add(v);
        }
        return a;
    }

    /** TFC's {@code getGrassColor(null, 0)}: the grass colormap at the default temperature and groundwater, as RGB. */
    static int defaultGrassColour(TfcCheckout tfc) {
        BufferedImage map;
        try {
            map = ImageIO.read(new ByteArrayInputStream(tfc.read(COLORMAP)));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        if (map == null || map.getWidth() != 256 || map.getHeight() != 256) {
            throw new DatagenException(COLORMAP + " is not a 256x256 image");
        }
        // TFC's cache index is temperatureIndex | rainfallIndex << 8 over the row-major pixels: x = temperature, y = rainfall.
        return map.getRGB(SeasonalColour.temperatureIndex(DEFAULT_TEMPERATURE), SeasonalColour.rainfallIndex(DEFAULT_GROUNDWATER))
            & 0xFFFFFF;
    }

    // ---- loot ----------------------------------------------------------------------------------------------

    /**
     * TFC's block loot table as one of our templates: one pool of one item under {@code survives_explosion}, the block
     * itself, another item, or a uniform count of another item. Any other shape fails the run.
     */
    static Map<String, Object> loot(TfcCheckout tfc, String id) {
        String src = LOOT + id + ".json";
        JsonObject table = Json.rename(Json.parse(tfc.readString(src), src)).getAsJsonObject();
        JsonArray pools = table.getAsJsonArray("pools");
        JsonObject pool = pools != null && pools.size() == 1 ? pools.get(0).getAsJsonObject() : null;
        JsonArray entries = pool == null ? null : pool.getAsJsonArray("entries");
        JsonArray conditions = pool == null ? null : pool.getAsJsonArray("conditions");
        if (entries == null || entries.size() != 1 || pool.get("rolls").getAsInt() != 1 || conditions == null
            || conditions.size() != 1
            || !"minecraft:survives_explosion".equals(conditions.get(0).getAsJsonObject().get("condition").getAsString())) {
            throw new DatagenException(src + " is no longer one item under survives_explosion");
        }
        JsonObject entry = entries.get(0).getAsJsonObject();
        if (!"minecraft:item".equals(entry.get("type").getAsString()) || entry.has("conditions")) {
            throw new DatagenException(src + " is no longer a plain item entry");
        }
        String item = entry.get("name").getAsString();
        Map<String, Object> loot = new LinkedHashMap<>();
        if (!entry.has("functions")) {
            if (item.equals(Json.TO_NS + ":" + id)) {
                loot.put("template", "tfp:loot_table/self");
            } else {
                loot.put("template", "tfp:loot_table/item");
                loot.put("arguments", Map.of("loot_item", item));
            }
            return loot;
        }
        JsonArray functions = entry.getAsJsonArray("functions");
        JsonObject function = functions.size() == 1 ? functions.get(0).getAsJsonObject() : null;
        JsonObject count = function != null && "minecraft:set_count".equals(function.get("function").getAsString())
            && function.size() == 2 && function.get("count").isJsonObject() ? function.getAsJsonObject("count") : null;
        if (count == null || !"minecraft:uniform".equals(count.get("type").getAsString()) || count.size() != 3) {
            throw new DatagenException(src + " is no longer a uniform set_count");
        }
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("loot_item", item);
        args.put("loot_count_min", count.get("min").getAsInt());
        args.put("loot_count_max", count.get("max").getAsInt());
        loot.put("template", "tfp:loot_table/item_count");
        loot.put("arguments", args);
        return loot;
    }

    // ---- models, textures, sounds, names ---------------------------------------------------------------------

    private static String path(String ref) {
        return ref.substring(ref.indexOf(':') + 1);
    }

    /** Copies a TFC model (path below {@code models/}) with its TFC parents and textures, unless the pack has it. */
    static void copyModel(TfcCheckout tfc, OutputTree out, String path) {
        String dst = RP + "models/" + path + ".json";
        if (out.contains(dst)) {
            return;
        }
        String src = MODELS + path + ".json";
        if (!tfc.exists(src)) {
            throw new DatagenException("TFC has no model " + src);
        }
        JsonObject json = Json.parse(tfc.readString(src), src).getAsJsonObject();
        if (json.has("loader")) {
            throw new DatagenException(src + ": model loader " + json.get("loader") + " has no converter");
        }
        out.put(dst, Json.write(Json.rename(json)));
        String prefix = Json.FROM_NS + ":";
        if (json.has("parent") && json.get("parent").getAsString().startsWith(prefix)) {
            copyModel(tfc, out, json.get("parent").getAsString().substring(prefix.length()));
        }
        if (json.has("textures")) {
            for (Map.Entry<String, JsonElement> t : json.getAsJsonObject("textures").entrySet()) {
                copyTexture(tfc, out, t.getValue().getAsString());
            }
        }
    }

    /** Copies a TFC texture ({@code tfc:block/...}) and its {@code .mcmeta}; other namespaces and {@code #refs} are left. */
    static void copyTexture(TfcCheckout tfc, OutputTree out, String ref) {
        String prefix = Json.FROM_NS + ":";
        if (!ref.startsWith(prefix)) {
            return;
        }
        String path = ref.substring(prefix.length());
        String dst = RP + "textures/" + path + ".png";
        if (out.contains(dst)) {
            return;
        }
        String src = TEXTURES + path + ".png";
        if (!tfc.exists(src)) {
            throw new DatagenException("TFC has no texture " + src);
        }
        out.put(dst, tfc.read(src));
        if (tfc.exists(src + ".mcmeta")) {
            out.put(dst + ".mcmeta", tfc.read(src + ".mcmeta"));
        }
    }

    /** TFC's five peat sound events and their oggs under {@code tfp_soil} (subtitles are vanilla's keys). */
    static void writePeatSounds(TfcCheckout tfc, OutputTree out) {
        JsonObject sounds = Json.parse(tfc.readString(SOUNDS), SOUNDS).getAsJsonObject();
        String prefix = Json.FROM_NS + ":";
        JsonObject root = new JsonObject();
        for (String kind : SOUND_KINDS) {
            String event = "block.peat." + kind;
            if (!sounds.has(event)) {
                throw new DatagenException(SOUNDS + " has no " + event);
            }
            JsonObject src = sounds.getAsJsonObject(event);
            JsonArray list = new JsonArray();
            for (JsonElement el : src.getAsJsonArray("sounds")) {
                JsonObject sound = el.isJsonObject() ? el.getAsJsonObject().deepCopy() : null;
                String name = sound == null ? el.getAsString() : sound.get("name").getAsString();
                if (!name.startsWith(prefix)) {
                    throw new DatagenException(SOUNDS + ": " + event + " sound outside tfc: " + name);
                }
                String path = name.substring(prefix.length());
                String ogg = TfcCheckout.ASSETS + "sounds/" + path + ".ogg";
                if (!tfc.exists(ogg)) {
                    throw new DatagenException("missing sound " + ogg);
                }
                out.put(SOIL_RP + "sounds/" + path + ".ogg", tfc.read(ogg));
                if (sound == null) {
                    list.add(SOIL_NS + ":" + path);
                } else {
                    sound.addProperty("name", SOIL_NS + ":" + path);
                    list.add(sound);
                }
            }
            JsonObject copy = new JsonObject();
            if (src.has("subtitle")) {
                copy.add("subtitle", src.get("subtitle"));
            }
            copy.add("sounds", list);
            root.add(event, copy);
        }
        out.put(SOIL_RP + "sounds.json", Json.write(root));
    }

    static String langKey(String id) {
        return "block." + Json.TO_NS + "." + id.replace('/', '.');
    }

    /** Whether the asset pipeline's {@code tfp} lang files already carry the name of a block (its {@code grass} family). */
    static boolean pipelineNames(String id) {
        return id.startsWith("grass/");
    }

    /** The block names in every TFC locale that has them (English must have all). */
    static void writeLang(TfcCheckout tfc, OutputTree out) {
        List<String> ids = new ArrayList<>(blockIds(tfc).stream().filter(id -> !pipelineNames(id)).toList());
        ids.addAll(SoilExtraBlocks.ids(tfc)); // PAR-WORLD-42
        for (String f : tfc.list(LANG)) {
            if (!f.endsWith(".json")) {
                continue;
            }
            String locale = f.substring(LANG.length(), f.length() - ".json".length());
            JsonObject src = Json.parse(tfc.readString(f), f).getAsJsonObject();
            Map<String, String> keys = new TreeMap<>();
            for (String id : ids) {
                String tfcKey = "block." + Json.FROM_NS + "." + id.replace('/', '.');
                if (src.has(tfcKey)) {
                    keys.put(langKey(id), src.get(tfcKey).getAsString());
                } else if (locale.equals("en_us")) {
                    throw new DatagenException("TFC has no English name " + tfcKey);
                }
            }
            if (keys.isEmpty()) {
                continue;
            }
            JsonObject o = new JsonObject();
            keys.forEach(o::addProperty);
            out.put(SOIL_RP + "lang/" + locale + ".json", Json.write(o));
        }
    }
}
