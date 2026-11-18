/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/devices/BellowsBlock.java @ c3b4d9536 (createShapeFor, the complete shape at extension 0.875),
 *       src/main/java/net/dries007/tfc/util/Helpers.java @ c3b4d9536 (rotateShape),
 *       src/main/java/net/dries007/tfc/common/blocks/devices/PlacedItemBlock.java @ c3b4d9536 (makeShapes: the union of the occupied quadrants),
 *       src/main/java/net/dries007/tfc/common/blocks/devices/CrucibleBlock.java @ c3b4d9536 (the shape without channel connections),
 *       src/main/java/net/dries007/tfc/common/blocks/devices/TFCComposterBlock.java @ c3b4d9536 (SHAPES: the well per stage),
 *       src/main/java/net/dries007/tfc/common/blocks/devices/LogPileBlock.java @ c3b4d9536 (SHAPES_BY_DIR_BY_COUNT,
 *       BASED_SHAPES_BY_DIR_BY_COUNT, getShapeByDirByCount),
 *       src/main/java/net/dries007/tfc/common/blocks/{TFCTorchBlock,TFCWallTorchBlock,DeadTorchBlock,DeadWallTorchBlock}.java
 *       @ c3b4d9536 (vanilla TorchBlock / WallTorchBlock outlines, noCollission in TFCBlocks),
 *       src/main/java/net/dries007/tfc/common/blocks/{TFCCandleBlock,TFCCandleCakeBlock}.java and
 *       devices/JackOLanternBlock.java @ c3b4d9536 (vanilla CandleBlock outlines; the candle cake SHAPE; a full cube),
 *       the getShape / getCollisionShape choices of the block classes named in SPECS @ c3b4d9536
 */
package dev.tfp.datagen.hitbox;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.tfp.datagen.DatagenException;
import dev.tfp.datagen.Json;
import dev.tfp.datagen.TfcCheckout;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * The TFC shape table ({@code hitbox/tfc-shapes.json}): selection and collision boxes of every TFC block state a
 * generator emits plus one representative state of every pinned shape class, read from the pinned TFC sources.
 *
 * <p>Each {@link Spec} names the TFC class files, the expression that {@code getShape} returns for the state and the one
 * {@code getCollisionShape} returns (or "same" when the class does not override it, or {@code Shapes.empty()} for
 * {@code noCollission()} blocks), plus text the sources must contain so a changed TFC method fails datagen instead of
 * silently keeping an old shape. Shapes that TFC computes in code (bellows, placed items, the crucible) are evaluated
 * by the small ports at the end of this class. The table is generated with {@link #generate} and committed;
 * {@code TfcShapesTest} regenerates it from the checkout and requires it to be equal.
 */
public final class TfcShapes {
    public static final String RESOURCE = "tfc-shapes.json";
    static final String B = "src/main/java/net/dries007/tfc/common/blocks/";
    static final String D = B + "devices/";
    static final String HELPERS = "src/main/java/net/dries007/tfc/util/Helpers.java";
    static final Map<String, String> CLASS_FILES = Map.of(
        "CharcoalPileBlock", B + "CharcoalPileBlock.java",
        "BarrelBlock", D + "BarrelBlock.java",
        "FirepitBlock", D + "FirepitBlock.java");

    /** PAR-PROC-37: TFC {@code HORIZONTAL_AXIS} and {@code COUNT_1_16}: the log pile's axes and its 1..16 logs. */
    public static final List<String> LOG_PILE_AXES = List.of("x", "z");
    public static final int LOG_PILE_MAX = 16;

    /** How the shape-quality ban list treats a class (the owner's object rule). */
    public enum Kind {
        /** Count-stacked pieces lying on the ground (loose rocks, small ores, groundcover): petals, litter, buttons allowed. */
        GROUND,
        /** Every solid object, device or container: no carrier that reads as several separate pieces. */
        OBJECT,
        /** Full-cube outlines (terrain, leaves, thatch). */
        CUBE
    }

    /** One TFC state: its key ({@code tfc:block[props]}), shape class, kind, selection and collision. */
    public record Row(String key, String shapeClass, Kind kind, Shape selection, Shape collision, boolean seeThrough,
            String from) {
        public String block() {
            int i = key.indexOf('[');
            return i < 0 ? key : key.substring(0, i);
        }

        /** The state properties of the key, in key order ({@code facing=north}). */
        public Map<String, String> properties() {
            Map<String, String> m = new LinkedHashMap<>();
            int i = key.indexOf('[');
            if (i >= 0) {
                for (String kv : key.substring(i + 1, key.length() - 1).split(",")) {
                    String[] p = kv.split("=", 2);
                    m.put(p[0], p[1]);
                }
            }
            return m;
        }
    }

    /**
     * A shape source: {@code sel}/{@code col} are expressions over {@code files} (col "same" = the selection), or
     * {@code ported} computes both. {@code needles}: text the files must contain; {@code absent}: text they must not.
     */
    record Spec(String key, String shapeClass, Kind kind, List<String> files, String sel, String col,
            List<String> needles, List<String> absent, Function<TfcShapeExpressions, Shape[]> ported) {
        static Spec of(String key, String cls, Kind kind, List<String> files, String sel, String col, List<String> needles,
                List<String> absent) {
            return new Spec(key, cls, kind, files, sel, col, needles, absent, null);
        }
    }

    /**
     * Full-outline TFC blocks drawn see-through ({@code noOcclusion()} or cutout leaves): an occluding carrier would let
     * players see through neighbouring blocks. Value: the TFCBlocks text that proves it, or null for leaves (always cutout).
     */
    static final Map<String, String> SEE_THROUGH = seeThrough();

    private static Map<String, String> seeThrough() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("tfc:wood/leaves", null);
        m.put("tfc:thatch", "new ThatchBlock(ExtendedProperties.of(MapColor.SAND).strength(0.6F, 0.4F).noOcclusion()");
        m.put("tfc:wattle[woven=false]", "new WattleBlock(ExtendedProperties.of(MapColor.WOOD).strength(0.3F).noOcclusion()");
        m.put("tfc:lobster_trap", "new LobsterTrapBlock(ExtendedProperties.of(MapColor.METAL).strength(3f).sound(SoundType.METAL).noOcclusion()");
        // PAR-PROC-37: the burning log pile (every axis and count) draws TFC's partial log models.
        for (String axis : LOG_PILE_AXES) {
            for (int count = 1; count <= LOG_PILE_MAX; count++) {
                m.put(burningKey(axis, count), ".cloneItem(Items.CHARCOAL).noOcclusion()));");
            }
        }
        return m;
    }

    private TfcShapes() {
    }

    /** Every spec, in table order. */
    static List<Spec> specs() {
        List<Spec> s = new ArrayList<>();
        String pile = B + "CharcoalPileBlock.java";
        for (int n = 1; n <= 8; n++) {
            s.add(Spec.of("tfc:charcoal_pile[layers=" + n + "]", "layer_pile", Kind.OBJECT, List.of(pile),
                "SHAPE_BY_LAYER[" + n + "]", "SHAPE_BY_LAYER[" + (n - 1) + "]",
                List.of("return SHAPE_BY_LAYER[state.getValue(LAYERS)];", "return SHAPE_BY_LAYER[state.getValue(LAYERS) - 1];"),
                List.of()));
        }
        String forge = D + "CharcoalForgeBlock.java";
        s.add(Spec.of("tfc:charcoal_forge", "charcoal_forge", Kind.OBJECT, List.of(forge), "CharcoalPileBlock.SHAPE_BY_LAYER[7]",
            "same", List.of("return CharcoalPileBlock.SHAPE_BY_LAYER[7];"), List.of("getCollisionShape")));
        String bloom = B + "BloomBlock.java";
        for (int n = 1; n <= 8; n++) {
            s.add(Spec.of("tfc:bloom[layers=" + n + "]", "layer_pile", Kind.OBJECT, List.of(bloom), "SHAPE_BY_LAYER[" + n + "]",
                "same", List.of("return SHAPE_BY_LAYER[state.getValue(LAYERS)];"), List.of("getCollisionShape")));
        }
        String ingots = D + "IngotPileBlock.java";
        for (int n = 0; n < 8; n++) {
            s.add(Spec.of("tfc:ingot_pile[count=" + (8 * n + 1) + "]", "layer_pile", Kind.OBJECT, List.of(ingots),
                "SHAPES[" + n + "]", "same", List.of("return SHAPES[(state.getValue(getCountProperty()) - 1) / 8];"),
                List.of("getCollisionShape")));
        }
        String firepit = D + "FirepitBlock.java";
        String bottom = D + "BottomSupportedDeviceBlock.java";
        s.add(Spec.of("tfc:firepit", "firepit", Kind.OBJECT, List.of(firepit, bottom), "BASE_SHAPE", "same",
            List.of("this(properties, BASE_SHAPE);"), List.of("getCollisionShape")));
        s.add(Spec.of("tfc:grill", "grill", Kind.OBJECT, List.of(D + "GrillBlock.java", firepit), "GRILL_SHAPE", "same",
            List.of("super(properties, GRILL_SHAPE);"), List.of("getCollisionShape")));
        s.add(Spec.of("tfc:pot", "pot", Kind.OBJECT, List.of(D + "PotBlock.java", firepit), "POT_SHAPE", "same",
            List.of("super(properties, POT_SHAPE);"), List.of("getCollisionShape")));
        for (String f : List.of("north", "east", "south", "west")) {
            s.add(new Spec("tfc:bellows[facing=" + f + "]", "bellows", Kind.OBJECT, List.of(D + "BellowsBlock.java", HELPERS),
                null, null, List.of("COMPLETE_SHAPES = Helpers.computeHorizontalShapes(d -> createShapeFor(d, 0.875f));"),
                List.of("getCollisionShape"), x -> bellows(x, f)));
        }
        s.add(Spec.of("tfc:farmland", "farmland", Kind.OBJECT, List.of(B + "soil/FarmlandBlock.java"), "SHAPE", "same",
            List.of("return SHAPE;"), List.of("getCollisionShape")));
        String spike = B + "rock/RockSpikeBlock.java";
        for (String part : List.of("base", "middle", "tip")) {
            s.add(Spec.of("tfc:rock/spike[part=" + part + "]", "spike", Kind.OBJECT, List.of(spike),
                part.toUpperCase(java.util.Locale.ROOT) + "_SHAPE", "same", List.of("case BASE -> BASE_SHAPE;"),
                List.of("getCollisionShape")));
        }
        String loose = B + "rock/LooseRockBlock.java";
        String rock = B + "rock/Rock.java";
        String looseNoCollision = "LOOSE((rock, self) -> new LooseRockBlock(properties(rock).strength(0.05f, 0.0f).noCollission()), false),";
        String[] counts = {"ONE", "TWO", "THREE"};
        for (int n = 1; n <= 3; n++) {
            s.add(Spec.of("tfc:rock/loose[count=" + n + "]", "loose_rock_" + n, Kind.GROUND, List.of(loose, rock), counts[n - 1],
                "Shapes.empty()", List.of("case " + n + " -> " + counts[n - 1] + ";", looseNoCollision), List.of()));
        }
        String cover = B + "GroundcoverBlock.java";
        String blocks = B + "TFCBlocks.java";
        s.add(Spec.of("tfc:ore/small", "small_ore", Kind.GROUND, List.of(cover, blocks), "SMALL", "Shapes.empty()",
            List.of("return new GroundcoverBlock(ExtendedProperties.of(properties), SMALL);", "GroundcoverBlock.looseOre(Properties.of()",
                ".sound(SoundType.NETHER_ORE).noCollission()"), List.of()));
        for (String shape : List.of("small", "medium", "flat", "pixel_high", "twig")) {
            s.add(Spec.of("tfc:groundcover[shape=" + shape + "]", "groundcover_" + shape, Kind.GROUND, List.of(cover),
                shape.toUpperCase(java.util.Locale.ROOT), "Shapes.empty()",
                List.of(".sound(SoundType.NETHER_WART).noCollission()", "return shape;"), List.of("getCollisionShape")));
        }
        String placed = D + "PlacedItemBlock.java";
        for (int mask = 0; mask < 16; mask++) {
            final int m = mask;
            s.add(new Spec("tfc:placed_item[" + placedKey(mask) + "]", "placed_item", Kind.OBJECT, List.of(placed), null, null,
                List.of("shape = Shapes.or(shape, SHAPES[i]);", "this(properties, Shapes.empty(), false);",
                    "protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context)\n    {\n        return Shapes.empty();"),
                List.of(), x -> placedItem(x, m)));
        }
        s.add(Spec.of("tfc:wood/leaves", "leaves", Kind.CUBE, List.of(B + "wood/TFCLeavesBlock.java"), "Shapes.block()",
            "Shapes.empty()", List.of("getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context)\n    {\n        return Shapes.empty();"),
            List.of("getShape(")));
        s.add(Spec.of("tfc:thatch", "thatch", Kind.CUBE, List.of(B + "ThatchBlock.java"), "Shapes.block()", "Shapes.empty()",
            List.of("getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context)\n    {\n        return Shapes.empty();"),
            List.of("getShape(")));
        // WattleBlock.getCollisionShape: unwoven wattle (a frame without the weave) has no collision; the outline stays the
        // full cube of ExtendedBlock (no getShape override in WattleBlock or StainedWattleBlock).
        s.add(Spec.of("tfc:wattle[woven=false]", "wattle", Kind.CUBE, List.of(B + "WattleBlock.java", B + "StainedWattleBlock.java"),
            "Shapes.block()", "Shapes.empty()",
            List.of("return state.getValue(WOVEN) ? super.getCollisionShape(state, level, pos, context) : Shapes.empty();"),
            List.of("getShape(")));
        s.add(Spec.of("tfc:rock/raw", "full_cube", Kind.CUBE, List.of(rock), "Shapes.block()", "same",
            List.of("RAW("), List.of()));
        // Containers.
        String sealable = D + "SealableDeviceBlock.java";
        String barrel = D + "BarrelBlock.java";
        for (String sealed : List.of("true", "false")) {
            s.add(Spec.of("tfc:wood/barrel[axis=y,sealed=" + sealed + "]", "barrel_upright", Kind.OBJECT, List.of(sealable),
                sealed.equals("true") ? "SHAPE" : "SHAPE_UNSEALED", "same",
                List.of("return state.getValue(SEALED) ? SHAPE : SHAPE_UNSEALED;"), List.of("getCollisionShape")));
        }
        for (String axis : List.of("x", "z")) {
            for (String rack : List.of("false", "true")) {
                String a = axis.toUpperCase(java.util.Locale.ROOT);
                s.add(Spec.of("tfc:wood/barrel[axis=" + axis + ",rack=" + rack + "]", "barrel_side", Kind.OBJECT, List.of(barrel),
                    "SHAPE_" + a + (rack.equals("true") ? "_RACK" : ""), "same",
                    List.of("case " + a + " -> rack ? SHAPE_" + a + "_RACK : SHAPE_" + a + ";"), List.of("getCollisionShape")));
            }
        }
        s.add(Spec.of("tfc:barrel_rack", "barrel_rack", Kind.OBJECT, List.of(D + "BarrelRackBlock.java"),
            "BarrelBlock.RACK_SHAPE", "same", List.of("return BarrelBlock.RACK_SHAPE;"), List.of("getCollisionShape")));
        String keg = D + "PowderkegBlock.java";
        for (String sealed : List.of("true", "false")) {
            s.add(Spec.of("tfc:powderkeg[sealed=" + sealed + "]", "powderkeg", Kind.OBJECT, List.of(keg),
                sealed.equals("true") ? "SHAPE" : "SHAPE_UNSEALED", "same",
                List.of("return state.getValue(SEALED) ? SHAPE : SHAPE_UNSEALED;"), List.of("getCollisionShape")));
        }
        String vessel = B + "LargeVesselBlock.java";
        for (String sealed : List.of("false", "true")) {
            s.add(Spec.of("tfc:ceramic/large_vessel[sealed=" + sealed + "]", "large_vessel", Kind.OBJECT, List.of(vessel),
                sealed.equals("true") ? "CLOSED_SHAPE" : "OPENED_SHAPE", "same",
                List.of("return state.getValue(SEALED) ? CLOSED_SHAPE : OPENED_SHAPE;"), List.of("getCollisionShape")));
        }
        s.add(Spec.of("tfc:nest_box", "nest_box", Kind.OBJECT, List.of(D + "NestBoxBlock.java"), "SHAPE", "same",
            List.of("super(properties, InventoryRemoveBehavior.DROP, SHAPE);"), List.of("getCollisionShape")));
        s.add(Spec.of("tfc:ceramic/bowl", "bowl", Kind.OBJECT, List.of(B + "BowlBlock.java"), "SHAPE", "same",
            List.of("super(properties, InventoryRemoveBehavior.DROP, SHAPE);"), List.of("getCollisionShape")));
        s.add(new Spec("tfc:crucible", "crucible", Kind.OBJECT, List.of(D + "CrucibleBlock.java"), null, null,
            List.of("VoxelShape shape = base;", "return SHAPES[(state.getValue(NORTH) ? 1 << Direction.NORTH.get2DDataValue() : 0) |"),
            List.of("getCollisionShape"), TfcShapes::crucible));
        String shelf = B + "ShelfBlock.java";
        for (String thick : List.of("false", "true")) {
            s.add(Spec.of("tfc:wood/shelf[thick=" + thick + "]", "shelf", Kind.OBJECT, List.of(shelf),
                thick.equals("true") ? "TOP_SHAPE_TALL" : "TOP_SHAPE", "TOP_SHAPE",
                List.of("super(properties, thick ? TOP_SHAPE_TALL : TOP_SHAPE, true);", "return TOP_SHAPE;"), List.of()));
        }
        String rack = B + "wood/ToolRackBlock.java";
        for (String f : List.of("north", "east", "south", "west")) {
            s.add(Spec.of("tfc:wood/tool_rack[facing=" + f + "]", "tool_rack", Kind.OBJECT, List.of(rack),
                "SHAPE_" + f.toUpperCase(java.util.Locale.ROOT), "same",
                List.of("case NORTH -> SHAPE_NORTH;", "case SOUTH -> SHAPE_SOUTH;", "case WEST -> SHAPE_WEST;", "default -> SHAPE_EAST;"),
                List.of("getCollisionShape")));
        }
        s.add(Spec.of("tfc:lobster_trap", "lobster_trap", Kind.OBJECT, List.of(B + "LobsterTrapBlock.java"), "Shapes.block()",
            "same", List.of("class LobsterTrapBlock extends DeviceBlock"), List.of("getShape(", "getCollisionShape")));
        s.add(Spec.of("tfc:drying_bricks[count=2]", "drying_bricks", Kind.OBJECT, List.of(D + "DryingBricksBlock.java"),
            "SHAPE_2", "same", List.of("case 2 -> SHAPE_2;"), List.of("getCollisionShape")));
        // PAR-PROC-04: the quern. Without a handstone the base (16x10x16) for both; with one, the selection is the base,
        // handstone and handle minus the input slot, the collision the base and handstone.
        String quern = D + "QuernBlock.java";
        List<String> quernNeedles = List.of("return state.getValue(HAS_HANDSTONE) ? FULL_SHAPE : BASE_SHAPE;",
            "return state.getValue(HAS_HANDSTONE) ? COLLISION_FULL_SHAPE : BASE_SHAPE;");
        s.add(Spec.of("tfc:quern[has_handstone=false]", "quern", Kind.OBJECT, List.of(quern), "BASE_SHAPE", "BASE_SHAPE",
            quernNeedles, List.of()));
        s.add(Spec.of("tfc:quern[has_handstone=true]", "quern", Kind.OBJECT, List.of(quern), "FULL_SHAPE",
            "COLLISION_FULL_SHAPE", quernNeedles, List.of()));
        // PAR-PROC-29: the scraping surface, a 1 px plate on the log; getShape only, so the collision is the same. WAXED
        // changes no shape, so one row serves both states.
        s.add(Spec.of("tfc:scraping", "scraping", Kind.OBJECT, List.of(D + "ScrapingBlock.java"), "SHAPE", "same",
            List.of("protected VoxelShape getShape(BlockState state, BlockGetter worldIn, BlockPos pos, CollisionContext context)\n    {\n        return SHAPE;"),
            List.of("getCollisionShape")));
        // PAR-PROC-34: the composter, a full cube minus the 14x14 well from max(2, stage * 2) up (TFCComposterBlock.SHAPES,
        // built in a Util.make loop, so ported); getShape only, so the collision is the same. TYPE changes no shape.
        String composter = D + "TFCComposterBlock.java";
        for (int stage = 0; stage <= COMPOSTER_MAX_STAGE; stage++) {
            final int st = stage;
            s.add(new Spec("tfc:composter[stage=" + stage + "]", "composter", Kind.OBJECT, List.of(composter), null, null,
                List.of(COMPOSTER_SHAPE, "return SHAPES[state.getValue(STAGE)];", "new VoxelShape[9]", "for (int i = 0; i < 9; ++i)"),
                List.of("getCollisionShape"), x -> composter(st)));
        }
        // PAR-PROC-37: the log pile, 16 logs in 4 rows of 4 per axis (LogPileBlock.SHAPES_BY_DIR_BY_COUNT, built in a
        // Util.make loop, so ported): the selection adds the 15.5 x 1 x 15.5 base below 4 logs, the collision has none.
        // The burning log pile has no getShape / getCollisionShape (the full cube) and is noOcclusion (SEE_THROUGH).
        String logPile = D + "LogPileBlock.java";
        String burning = D + "BurningLogPileBlock.java";
        for (String axis : LOG_PILE_AXES) {
            for (int count = 1; count <= LOG_PILE_MAX; count++) {
                final int c = count;
                s.add(new Spec(logPileKey(axis, count), "log_pile", Kind.OBJECT, List.of(logPile, HELPERS), null, null,
                    LOG_PILE_NEEDLES, List.of(), x -> logPile(axis, c)));
            }
        }
        for (String axis : LOG_PILE_AXES) {
            for (int count = 1; count <= LOG_PILE_MAX; count++) {
                s.add(Spec.of(burningKey(axis, count), "full_cube", Kind.OBJECT, List.of(burning), "Shapes.block()", "same",
                    List.of("class BurningLogPileBlock extends Block"), List.of("getShape(", "getCollisionShape")));
            }
        }
        // PAR-HEAT-21: the four torch blocks extend vanilla TorchBlock / WallTorchBlock with no shape override, and
        // TFCBlocks registers each noCollission(): the outline is vanilla's (ported, TORCH_SHAPE / WALL_TORCH_SHAPES), no
        // collision. The dead torches share the lit ones' outlines.
        for (String[] t : TORCH_CLASSES) {
            boolean wall = t[0].contains("wall");
            List<String> files = List.of(B + t[1] + ".java", blocks);
            List<String> needles = List.of("public class " + t[1] + " extends " + (wall ? "WallTorchBlock" : "TorchBlock"),
                t[0].toUpperCase(java.util.Locale.ROOT) + " = registerNoItem(\"" + t[0] + "\", () -> new " + t[1]
                    + "(ExtendedProperties.of().noCollission()");
            List<String> absent = List.of("getShape(", "getCollisionShape", "getVisualShape");
            if (!wall) {
                s.add(new Spec("tfc:" + t[0], "torch", Kind.OBJECT, files, null, null, needles, absent,
                    x -> new Shape[] {TORCH_SHAPE, Shape.EMPTY}));
                continue;
            }
            for (String f : WALL_TORCH_SHAPES.keySet()) {
                s.add(new Spec("tfc:" + t[0] + "[facing=" + f + "]", "wall_torch", Kind.OBJECT, files, null, null, needles,
                    absent, x -> new Shape[] {WALL_TORCH_SHAPES.get(f), Shape.EMPTY}));
            }
        }
        // PAR-HEAT-26: the candles extend vanilla CandleBlock with no shape override (the outline and collision are
        // vanilla's per count, ported: CANDLE_SHAPES); one row per state so the selector keeps lit and waterlogged (the
        // dyed candles are the same class and share these rows). The candle cake's SHAPE is its own (cake + candle, collision
        // the same); the jack-o'-lantern extends CarvedPumpkinBlock (a full cube) with no override.
        List<String> candleFiles = List.of(B + "TFCCandleBlock.java", blocks);
        List<String> candleNeedles = List.of("public class TFCCandleBlock extends CandleBlock",
            "CANDLE = register(\"candle\", () -> new TFCCandleBlock(ExtendedProperties.of(Blocks.CANDLE)");
        for (int n = 1; n <= 4; n++) {
            final Shape shape = CANDLE_SHAPES.get(n - 1);
            for (String lit : List.of("false", "true")) {
                for (String wet : List.of("false", "true")) {
                    s.add(new Spec(candleKey(n, Boolean.parseBoolean(lit), Boolean.parseBoolean(wet)), "candle", Kind.OBJECT,
                        candleFiles, null, null, candleNeedles, List.of("getShape(", "getCollisionShape"),
                        x -> new Shape[] {shape, shape}));
                }
            }
        }
        for (String lit : List.of("false", "true")) {
            s.add(Spec.of("tfc:candle_cake[lit=" + lit + "]", "candle_cake", Kind.OBJECT, List.of(B + "TFCCandleCakeBlock.java"),
                "SHAPE", "same", List.of("protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, "
                    + "CollisionContext context)\n    {\n        return SHAPE;"), List.of("getCollisionShape")));
        }
        for (String f : List.of("north", "east", "south", "west")) {
            s.add(Spec.of("tfc:jack_o_lantern[facing=" + f + "]", "jack_o_lantern", Kind.CUBE,
                List.of(D + "JackOLanternBlock.java"), "Shapes.block()", "same",
                List.of("public class JackOLanternBlock extends CarvedPumpkinBlock"), List.of("getShape(", "getCollisionShape")));
        }
        return s;
    }

    /** PAR-HEAT-26: the tfc-shapes row of a TFC candle state (every colour shares it). */
    public static String candleKey(int candles, boolean lit, boolean waterlogged) {
        return "tfc:candle[candles=" + candles + ",lit=" + lit + ",waterlogged=" + waterlogged + "]";
    }

    /**
     * Vanilla 1.21.1 {@code CandleBlock.ONE_AABB} .. {@code FOUR_AABB} ({@code Block.box(7, 0, 7, 9, 6, 9)},
     * {@code (5, 0, 6, 11, 6, 9)}, {@code (5, 0, 6, 10, 6, 11)}, {@code (5, 0, 5, 11, 6, 10)}), by candle count.
     */
    static final List<Shape> CANDLE_SHAPES = List.of(Shape.box(7, 0, 7, 9, 6, 9), Shape.box(5, 0, 6, 11, 6, 9),
        Shape.box(5, 0, 6, 10, 6, 11), Shape.box(5, 0, 5, 11, 6, 10));

    /** PAR-HEAT-21: TFC's torch blocks (registry path, class), in TFCBlocks order. */
    static final List<String[]> TORCH_CLASSES = List.of(new String[] {"torch", "TFCTorchBlock"},
        new String[] {"wall_torch", "TFCWallTorchBlock"}, new String[] {"dead_torch", "DeadTorchBlock"},
        new String[] {"dead_wall_torch", "DeadWallTorchBlock"});
    /** Vanilla 1.21.1 {@code BaseTorchBlock.AABB} ({@code Block.box(6, 0, 6, 10, 10, 10)}). */
    static final Shape TORCH_SHAPE = Shape.box(6, 0, 6, 10, 10, 10);
    /** Vanilla 1.21.1 {@code WallTorchBlock.AABBS}: the 5 x 10 x 5 stick against the wall behind {@code facing}. */
    static final Map<String, Shape> WALL_TORCH_SHAPES = wallTorchShapes();

    private static Map<String, Shape> wallTorchShapes() {
        Map<String, Shape> m = new LinkedHashMap<>();
        m.put("north", Shape.box(5.5, 3, 11, 10.5, 13, 16));
        m.put("east", Shape.box(0, 3, 5.5, 5, 13, 10.5));
        m.put("south", Shape.box(5.5, 3, 0, 10.5, 13, 5));
        m.put("west", Shape.box(11, 3, 5.5, 16, 13, 10.5));
        return java.util.Collections.unmodifiableMap(m);
    }

    static final List<String> LOG_PILE_NEEDLES = List.of(
        "box2ByCount[i] = new double[] {0, 4 * layer, 0, 16, 4 * layer + 4, 4 * row};",
        "box1ByCount[i] = new double[] {0, 0, 0, 16, 4 * (layer), 16};",
        "layer = i / 4;", "row = i % 4 + 1;",
        "Direction direction = Direction.SOUTH;", "direction = Direction.EAST;",
        "shapes[dir][count] = Shapes.or(box1, box2);",
        "final VoxelShape baseBox = Block.box(0.25, 0, 0.25, 15.75, 1, 15.75);",
        "final int dir = axis == Direction.Axis.X ? 0 : 1;",
        "if (based && count < 4)",
        "return getShapeByDirByCount(state.getValue(AXIS), state.getValue(COUNT), true);",
        "protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context)\n    {\n        return getShapeByDirByCount(state.getValue(AXIS), state.getValue(COUNT), false);",
        "case EAST -> Block.box(16 - z2, y1, x1, 16 - z1, y2, x2);",
        "case SOUTH -> Block.box(16 - x2, y1, 16 - z2, 16 - x1, y2, 16 - z1);");

    public static String logPileKey(String axis, int count) {
        return "tfc:log_pile[axis=" + axis + ",count=" + count + "]";
    }

    public static String burningKey(String axis, int count) {
        return "tfc:burning_log_pile[axis=" + axis + ",count=" + count + "]";
    }

    /**
     * {@code LogPileBlock.getShapeByDirByCount(axis, count, based)}: the full layers below ({@code box1}, 4 px each) or'd
     * with the top layer's rows ({@code box2}, 4 px deep each), turned SOUTH for axis x and EAST for axis z
     * ({@code Helpers.rotateShape}); {@code based} adds the base plate below 4 logs. Returns {selection, collision}.
     */
    static Shape[] logPile(String axis, int count) {
        int i = count - 1;
        int layer = i / 4;
        int row = i % 4 + 1;
        double[] b2 = {0, 4 * layer, 0, 16, 4 * layer + 4, 4 * row};
        double[] b1 = {0, 0, 0, 16, 4 * layer, 16};
        boolean east = axis.equals("z");
        Shape shape = rotated(east, b1).union(rotated(east, b2));
        Shape based = count < 4 ? Shape.box(0.25, 0, 0.25, 15.75, 1, 15.75).union(shape) : shape;
        return new Shape[] {based, shape};
    }

    /** {@code Helpers.rotateShape(SOUTH|EAST, box)}; a box without volume is empty ({@code Shapes.box}). */
    private static Shape rotated(boolean east, double[] b) {
        double[] r = east ? new double[] {16 - b[5], b[1], b[0], 16 - b[2], b[4], b[3]}
            : new double[] {16 - b[3], b[1], 16 - b[5], 16 - b[0], b[4], 16 - b[2]};
        if (r[3] - r[0] <= 0 || r[4] - r[1] <= 0 || r[5] - r[2] <= 0) {
            return Shape.EMPTY;
        }
        return Shape.box(r[0], r[1], r[2], r[3], r[4], r[5]);
    }

    /** TFC {@code TFCBlockStateProperties.STAGE_8}: the composter's stages 0..8. */
    public static final int COMPOSTER_MAX_STAGE = 8;
    static final String COMPOSTER_SHAPE = "shapes[i] = Shapes.join(Shapes.block(), Block.box(1.0D, Math.max(2, i * 2), 1.0D, "
        + "15.0D, 16.0D, 15.0D), BooleanOp.ONLY_FIRST);";

    /** {@code TFCComposterBlock.SHAPES[stage]}: the full block minus the well from {@code max(2, stage * 2)} to the top. */
    static Shape[] composter(int stage) {
        double floor = Math.max(2, stage * 2);
        Shape shape = floor >= 16 ? Shape.FULL : Shape.FULL.minus(Shape.box(1, floor, 1, 15, 16, 15));
        return new Shape[] {shape, shape};
    }

    static String placedKey(int mask) {
        List<String> p = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            p.add("item_" + i + "=" + ((mask & (1 << i)) != 0));
        }
        return String.join(",", p);
    }

    /** {@code BellowsBlock.createShapeFor(direction, 0.875f)}: front plate, the 12x12 middle and the end plate. */
    private static Shape[] bellows(TfcShapeExpressions x, String facing) {
        String src = x.source(D + "BellowsBlock.java");
        for (String needle : List.of("return Helpers.rotateShape(direction, 0, 0, 0, 16, 16, 2);",
                "return Helpers.rotateShape(direction, 2, 2, 2, 14, 14, extension * 16);",
                "return Helpers.rotateShape(direction, 0, 0, extension * 16, 16, 16, extension * 16 + 2);")) {
            require(src, needle, "BellowsBlock.java");
        }
        require(x.source(HELPERS), "case EAST -> Block.box(16 - z2, y1, x1, 16 - z1, y2, x2);", "Helpers.java");
        double ext = 0.875 * 16;
        int turns = List.of("north", "east", "south", "west").indexOf(facing);
        Shape north = Shape.box(0, 0, 0, 16, 16, 2).union(Shape.box(2, 2, 2, 14, 14, ext)).union(Shape.box(0, 0, ext, 16, 16, ext + 2));
        Shape shape = north.rotateY(turns); // Helpers.rotateShape: EAST/SOUTH/WEST are 1/2/3 clockwise quarter turns of NORTH
        return new Shape[] {shape, shape};
    }

    /** {@code PlacedItemBlock.makeShapes}: the empty shape or'd with SHAPES[i] for every occupied ITEM_i. No collision. */
    private static Shape[] placedItem(TfcShapeExpressions x, int mask) {
        String file = D + "PlacedItemBlock.java";
        Shape[] quadrants = new Shape[4];
        for (int i = 0; i < 4; i++) {
            quadrants[i] = x.shape(List.of(file), "SHAPES[" + i + "]");
        }
        Shape shape = Shape.EMPTY;
        for (int i = 0; i < 4; i++) {
            if ((mask & (1 << i)) != 0) {
                shape = shape.union(quadrants[i]);
            }
        }
        return new Shape[] {shape, Shape.EMPTY};
    }

    /** {@code CrucibleBlock.SHAPES[0]}: the base shell without channel nubs (no neighbour connected). */
    private static Shape[] crucible(TfcShapeExpressions x) {
        Shape base = x.shape(List.of(D + "CrucibleBlock.java"), "base");
        return new Shape[] {base, base};
    }

    private static void require(String src, String needle, String file) {
        if (!src.contains(needle)) {
            throw new DatagenException(file + " no longer contains \"" + needle + "\": re-check the hitbox shape table");
        }
    }

    /** Evaluates every spec against the checkout. */
    public static List<Row> read(TfcCheckout tfc) {
        TfcShapeExpressions x = new TfcShapeExpressions(tfc, CLASS_FILES);
        List<Row> rows = new ArrayList<>();
        for (Spec spec : specs()) {
            String all = String.join("\n", spec.files().stream().map(x::source).toList());
            for (String needle : spec.needles()) {
                require(all, needle, spec.files().toString());
            }
            for (String absent : spec.absent()) {
                if (x.source(spec.files().getFirst()).contains(absent)) {
                    throw new DatagenException(spec.files().getFirst() + " now contains \"" + absent + "\": re-check "
                        + spec.key() + " in the hitbox shape table");
                }
            }
            Shape sel;
            Shape col;
            String from;
            if (spec.ported() != null) {
                Shape[] both = spec.ported().apply(x);
                sel = both[0];
                col = both[1];
                from = fileName(spec.files().getFirst()) + " (ported)";
            } else {
                sel = x.shape(spec.files(), spec.sel());
                col = spec.col().equals("same") ? sel : x.shape(spec.files(), spec.col());
                from = fileName(spec.files().getFirst()) + " " + spec.sel() + " / " + spec.col();
            }
            boolean seeThrough = SEE_THROUGH.containsKey(spec.key());
            String proof = SEE_THROUGH.get(spec.key());
            if (proof != null) {
                require(x.source(B + "TFCBlocks.java"), proof, "TFCBlocks.java");
            }
            rows.add(new Row(spec.key(), spec.shapeClass(), spec.kind(), sel, col, seeThrough, from));
        }
        return rows;
    }

    private static String fileName(String path) {
        return path.substring(path.lastIndexOf('/') + 1);
    }

    /** The JSON of the table (the committed resource). */
    public static String json(List<Row> rows) {
        JsonObject root = new JsonObject();
        root.addProperty("generated", "tools/datagen hitbox.TfcShapes from TerraFirmaCraft @ "
            + TfcCheckout.PINNED_COMMIT.substring(0, 9) + "; do not edit, regenerate (TfcShapesTest prints how)");
        root.addProperty("units", "pixels; selection = getShape, collision = getCollisionShape (empty = noCollission)");
        JsonArray a = new JsonArray();
        for (Row r : rows) {
            JsonObject o = new JsonObject();
            o.addProperty("key", r.key());
            o.addProperty("class", r.shapeClass());
            o.addProperty("kind", r.kind().name().toLowerCase(java.util.Locale.ROOT));
            o.add("selection", r.selection().toJson());
            o.add("collision", r.collision().toJson());
            if (r.seeThrough()) {
                o.addProperty("see_through", true);
            }
            o.addProperty("from", r.from());
            a.add(o);
        }
        root.add("rows", a);
        return compact(Json.write(root));
    }

    /** Puts every box on one line, so the table stays readable. */
    static String compact(String pretty) {
        return pretty.replaceAll("\\[\\s+(-?[\\d.]+),\\s+(-?[\\d.]+),\\s+(-?[\\d.]+),\\s+(-?[\\d.]+),\\s+(-?[\\d.]+),\\s+(-?[\\d.]+)\\s+]",
            "[$1, $2, $3, $4, $5, $6]").replaceAll("\\[\\s+\\[", "[[").replaceAll("]\\s+]", "]]").replaceAll("],\\s+\\[", "], [");
    }

    /** The committed table, by key. */
    public static Map<String, Row> load() {
        try (InputStream in = TfcShapes.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new DatagenException("missing datagen resource hitbox/" + RESOURCE);
            }
            return parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static Map<String, Row> parse(String text) {
        Map<String, Row> rows = new LinkedHashMap<>();
        for (JsonElement e : Json.parse(text, RESOURCE).getAsJsonObject().getAsJsonArray("rows")) {
            JsonObject o = e.getAsJsonObject();
            Row r = new Row(o.get("key").getAsString(), o.get("class").getAsString(),
                Kind.valueOf(o.get("kind").getAsString().toUpperCase(java.util.Locale.ROOT)),
                Shape.fromJson(o.get("selection")), Shape.fromJson(o.get("collision")),
                o.has("see_through") && o.get("see_through").getAsBoolean(), o.get("from").getAsString());
            if (rows.put(r.key(), r) != null) {
                throw new DatagenException(RESOURCE + ": duplicate key " + r.key());
            }
        }
        return rows;
    }

    /** Regenerates the committed table: {@code TfcShapes <checkout> <output file>}. */
    public static void main(String[] args) throws IOException {
        TfcCheckout tfc = TfcCheckout.open(Path.of(args[0]), TfcCheckout.PINNED_COMMIT);
        Files.writeString(Path.of(args[1]), json(read(tfc)));
    }
}
