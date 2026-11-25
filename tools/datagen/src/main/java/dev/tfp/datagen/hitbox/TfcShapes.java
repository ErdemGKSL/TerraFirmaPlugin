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
 *       src/main/java/net/dries007/tfc/common/blocks/devices/PitKilnBlock.java @ c3b4d9536 (SHAPE_BY_LAYER, built in a loop),
 *       src/main/java/net/dries007/tfc/common/blocks/devices/LogPileBlock.java @ c3b4d9536 (SHAPES_BY_DIR_BY_COUNT,
 *       BASED_SHAPES_BY_DIR_BY_COUNT, getShapeByDirByCount),
 *       src/main/java/net/dries007/tfc/common/blocks/{TFCTorchBlock,TFCWallTorchBlock,DeadTorchBlock,DeadWallTorchBlock}.java
 *       @ c3b4d9536 (vanilla TorchBlock / WallTorchBlock outlines, noCollission in TFCBlocks),
 *       src/main/java/net/dries007/tfc/common/blocks/{TFCCandleBlock,TFCCandleCakeBlock}.java and
 *       devices/JackOLanternBlock.java @ c3b4d9536 (vanilla CandleBlock outlines; the candle cake SHAPE; a full cube),
 *       src/main/java/net/dries007/tfc/common/blocks/{SnowPileBlock,IcePileBlock,SeaIceBlock,IcicleBlock,ThinSpikeBlock}.java
 *       @ c3b4d9536 (vanilla SnowLayerBlock and IceBlock outlines; TIP_SHAPE and PILLAR_SHAPE),
 *       src/main/java/net/dries007/tfc/common/blocks/crop/{CropBlock,Crop}.java @ c3b4d9536 (QUARTER/HALF/FULL_SHAPE, noCollission),
 *       src/main/java/net/dries007/tfc/common/blocks/rotation/AbstractShaftAxleBlock.java @ c3b4d9536 (SHAPE_X/Y/Z by AXIS),
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
        // PAR-CLIM-04: TFC's ice blocks copy vanilla ICE (noOcclusion, translucent); the icicle is noOcclusion itself.
        m.put("tfc:ice_pile", "new IcePileBlock(ExtendedProperties.of(Blocks.ICE).randomTicks()");
        m.put("tfc:sea_ice", "new SeaIceBlock(Properties.ofFullCopy(Blocks.ICE)");
        for (String tip : List.of("true", "false")) {
            m.put("tfc:icicle[tip=" + tip + "]", "sound(SoundType.GLASS).noOcclusion()");
        }
        // PAR-PROC-37: the burning log pile (every axis and count) draws TFC's partial log models.
        for (String axis : LOG_PILE_AXES) {
            for (int count = 1; count <= LOG_PILE_MAX; count++) {
                m.put(burningKey(axis, count), ".cloneItem(Items.CHARCOAL).noOcclusion()));");
            }
        }
        // PAR-POT-14: the pit kiln is noOcclusion (TFCBlocks), every stage.
        for (int stage = 0; stage <= PIT_KILN_LIT; stage++) {
            m.put(pitKilnKey(stage), "ExtendedProperties.of(MapColor.WOOD).sound(SoundType.WOOD).strength(0.6f).noOcclusion()");
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
        // PAR-METAL-14: the bloomery, TFC's getShape by facing and open (no collision override: the outline is both).
        String bloomery = D + "BloomeryBlock.java";
        for (String f : List.of("north", "east", "south", "west")) {
            for (boolean open : new boolean[] {false, true}) {
                String field = (open ? "OPEN_" : "CLOSED_") + f.toUpperCase(java.util.Locale.ROOT) + "_SHAPE";
                s.add(Spec.of(bloomeryKey(f, open), "bloomery", Kind.OBJECT, List.of(bloomery), field, "same",
                    List.of("case " + f.toUpperCase(java.util.Locale.ROOT) + " -> " + field + ";"), List.of("getCollisionShape")));
            }
        }
        String ingots = D + "IngotPileBlock.java";
        for (int n = 0; n < 8; n++) {
            s.add(Spec.of("tfc:ingot_pile[count=" + (8 * n + 1) + "]", "layer_pile", Kind.OBJECT, List.of(ingots),
                "SHAPES[" + n + "]", "same", List.of("return SHAPES[(state.getValue(getCountProperty()) - 1) / 8];"),
                List.of("getCollisionShape")));
        }
        // PAR-METAL-16: the double ingot pile, six layers (TFC DoubleIngotPileBlock.SHAPES, getShape for the collision too).
        String doubleIngots = D + "DoubleIngotPileBlock.java";
        for (int n = 0; n < DOUBLE_INGOT_PILE_LAYERS; n++) {
            final int layer = n;
            s.add(new Spec(doubleIngotPileKey(n), "layer_pile", Kind.OBJECT, List.of(doubleIngots), null, null,
                DOUBLE_INGOT_PILE_NEEDLES, List.of("getCollisionShape"), x -> doubleIngotPile(layer)));
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
        // PAR-PROC-13: the sluice, every wood alike (TFC's one SluiceBlock); one row per facing and half.
        for (String f : List.of("north", "east", "south", "west")) {
            for (boolean upper : new boolean[] {true, false}) {
                s.add(new Spec("tfc:wood/sluice[facing=" + f + ",upper=" + upper + "]", "sluice", Kind.OBJECT,
                    List.of(D + "SluiceBlock.java", HELPERS), null, null,
                    List.of("return state.getValue(UPPER) ? TOP_SHAPES[idx] : BOTTOM_SHAPES[idx];"),
                    List.of("getCollisionShape"), x -> sluice(x, f, upper)));
            }
        }
        // PAR-PROC-06: the loom, every wood alike (TFC's one TFCLoomBlock); one row per facing.
        for (String f : List.of("north", "east", "south", "west")) {
            s.add(new Spec("tfc:wood/loom[facing=" + f + "]", "loom", Kind.OBJECT, List.of(B + "wood/TFCLoomBlock.java"), null,
                null, List.of("private static final VoxelShape SHAPE_EAST = box(2, 0, 1, 8, 16, 15);",
                    "private static final VoxelShape SHAPE_WEST = box(8, 0, 1, 14, 16, 15);",
                    "private static final VoxelShape SHAPE_SOUTH = box(1, 0, 2, 15, 16, 8);",
                    "private static final VoxelShape SHAPE_NORTH = box(1, 0, 8, 15, 16, 14);"),
                List.of("getCollisionShape"), x -> {
                    x.source(B + "wood/TFCLoomBlock.java");
                    Shape shape = switch (f) {
                        case "east" -> Shape.box(2, 0, 1, 8, 16, 15);
                        case "west" -> Shape.box(8, 0, 1, 14, 16, 15);
                        case "south" -> Shape.box(1, 0, 2, 15, 16, 8);
                        default -> Shape.box(1, 0, 8, 15, 16, 14);
                    };
                    return new Shape[] {shape, shape};
                }));
        }
        // PAR-PROC-49: the scribing table (side boards turn with the facing) and the sewing table, every wood alike.
        String scribing = B + "wood/ScribingTableBlock.java";
        for (String f : List.of("north", "east", "south", "west")) {
            boolean ns = f.equals("north") || f.equals("south");
            s.add(new Spec("tfc:wood/scribing_table[facing=" + f + "]", "scribing_table", Kind.OBJECT, List.of(scribing), null,
                null, List.of("box(0, 12, 0, 16, 16, 16),", "box(1, 0, 1, 4, 12, 4),", "box(12, 0, 1, 15, 12, 4),",
                    "box(1, 0, 12, 4, 12, 15),", "box(12, 0, 12, 15, 12, 15)",
                    "box(12, 5, 4, 14, 12, 12),", "box(2, 5, 4, 4, 12, 12)",
                    "box(4, 5, 12, 12, 12, 14),", "box(4, 5, 2, 12, 12, 4)",
                    "case NORTH, SOUTH -> SHAPE_NS;", "case EAST, WEST -> SHAPE_EW;"),
                List.of("getCollisionShape"), x -> {
                    x.source(scribing);
                    Shape base = Shape.box(0, 12, 0, 16, 16, 16).union(Shape.box(1, 0, 1, 4, 12, 4))
                        .union(Shape.box(12, 0, 1, 15, 12, 4)).union(Shape.box(1, 0, 12, 4, 12, 15))
                        .union(Shape.box(12, 0, 12, 15, 12, 15));
                    Shape shape = ns ? base.union(Shape.box(12, 5, 4, 14, 12, 12)).union(Shape.box(2, 5, 4, 4, 12, 12))
                        : base.union(Shape.box(4, 5, 12, 12, 12, 14)).union(Shape.box(4, 5, 2, 12, 12, 4));
                    return new Shape[] {shape, shape};
                }));
        }
        String sewing = B + "wood/SewingTableBlock.java";
        s.add(new Spec("tfc:wood/sewing_table", "sewing_table", Kind.OBJECT, List.of(sewing), null, null,
            List.of("box(2, 0, 2, 14, 14, 14),", "box(0, 14, 0, 16, 16, 16)", "return SHAPE;"), List.of("getCollisionShape"),
            x -> {
                x.source(sewing);
                Shape shape = Shape.box(2, 0, 2, 14, 14, 14).union(Shape.box(0, 14, 0, 16, 16, 16));
                return new Shape[] {shape, shape};
            }));
        s.add(Spec.of("tfc:farmland", "farmland", Kind.OBJECT, List.of(B + "soil/FarmlandBlock.java"), "SHAPE", "same",
            List.of("return SHAPE;"), List.of("getCollisionShape")));
        // PAR-FARM-25: the growing plant shapes (columns, kelp, kelp trees, vines, creepers, epiphytes, branching cacti) are
        // served on the column body's outline, no collision: TFC's nonSolidTallPlant blocks have none, and one full-height
        // row stands in for the per-shape ones (ledger divergence).
        s.add(Spec.of(GROWING_PLANT_KEY, "growing_plant", Kind.OBJECT, List.of(B + "plant/BodyPlantBlock.java"), "BODY_SHAPE",
            "Shapes.empty()", List.of("public static final VoxelShape BODY_SHAPE = box(1.0D, 0.0D, 1.0D, 15.0D, 16.0D, 15.0D);"),
            List.of()));
        addGrowingPlantShapes(s);
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
        String cover = B + "GroundcoverBlock.java";
        // PAR-WORLD-58: GroundcoverBlock.FLUID (empty, water, salt water; LooseRockBlock extends GroundcoverBlock): the same
        // outline in water, a row of its own so the selector serves it with a waterlogged carrier
        String fluidNeedle = "public static final FluidProperty FLUID = TFCBlockStateProperties.WATER;";
        for (int n = 1; n <= 3; n++) {
            s.add(Spec.of("tfc:rock/loose[count=" + n + "]", "loose_rock_" + n, Kind.GROUND, List.of(loose, rock), counts[n - 1],
                "Shapes.empty()", List.of("case " + n + " -> " + counts[n - 1] + ";", looseNoCollision), List.of()));
            s.add(Spec.of(wetKey("tfc:rock/loose[count=" + n + "]"), "loose_rock_" + n, Kind.GROUND, List.of(loose, rock, cover),
                counts[n - 1], "Shapes.empty()", List.of("case " + n + " -> " + counts[n - 1] + ";", looseNoCollision,
                    "public class LooseRockBlock extends GroundcoverBlock", fluidNeedle), List.of()));
        }
        String blocks = B + "TFCBlocks.java";
        List<String> oreNeedles = List.of("return new GroundcoverBlock(ExtendedProperties.of(properties), SMALL);",
            "GroundcoverBlock.looseOre(Properties.of()", ".sound(SoundType.NETHER_ORE).noCollission()");
        s.add(Spec.of("tfc:ore/small", "small_ore", Kind.GROUND, List.of(cover, blocks), "SMALL", "Shapes.empty()", oreNeedles,
            List.of()));
        List<String> wetOreNeedles = new ArrayList<>(oreNeedles);
        wetOreNeedles.add(fluidNeedle);
        s.add(Spec.of(wetKey("tfc:ore/small"), "small_ore", Kind.GROUND, List.of(cover, blocks), "SMALL", "Shapes.empty()",
            wetOreNeedles, List.of()));
        for (String shape : List.of("small", "medium", "flat", "pixel_high", "twig")) {
            for (boolean wet : List.of(false, true)) {
                String key = "tfc:groundcover[shape=" + shape + "]";
                s.add(Spec.of(wet ? wetKey(key) : key, "groundcover_" + shape, Kind.GROUND, List.of(cover),
                    shape.toUpperCase(java.util.Locale.ROOT), "Shapes.empty()",
                    wet ? List.of(".sound(SoundType.NETHER_WART).noCollission()", "return shape;", fluidNeedle)
                        : List.of(".sound(SoundType.NETHER_WART).noCollission()", "return shape;"), List.of("getCollisionShape")));
            }
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
        // PAR-METAL-13: the channel (SHAPES by the four horizontal connections) and the mold table (one slab).
        for (int mask = 0; mask < 16; mask++) {
            final int m = mask;
            s.add(new Spec(channelKey(mask), "channel", Kind.OBJECT, List.of(D + "ChannelBlock.java"), null, null,
                List.of("final VoxelShape[] directions = new VoxelShape[] {south, west, north, east};",
                    "if (((i >> direction.get2DDataValue()) & 1) == 1)"),
                List.of("getCollisionShape"), x -> channel(x, m)));
        }
        s.add(Spec.of(MOLD_TABLE_KEY, "mold_table", Kind.OBJECT, List.of(D + "MoldTableBlock.java"), "SHAPE", "same",
            List.of("static final VoxelShape SHAPE = box(0, 0, 0, 16, 5, 16);", "return SHAPE;"), List.of("getCollisionShape")));
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
        // PAR-POT-08: TFC registers the drying bricks noCollission (SoilBlockType.DRYING_BRICKS), so the collision is empty.
        for (int n = 1; n <= 4; n++) {
            s.add(Spec.of("tfc:drying_bricks[count=" + n + "]", "drying_bricks", Kind.OBJECT,
                List.of(D + "DryingBricksBlock.java", "src/main/java/net/dries007/tfc/common/blocks/soil/SoilBlockType.java"),
                "SHAPE_" + n, "Shapes.empty()", List.of(n == 1 ? "default -> SHAPE_1;" : "case " + n + " -> SHAPE_" + n + ";",
                    "DRYING_BRICKS((self, variant) -> new DryingBricksBlock(ExtendedProperties.of(MapColor.DIRT).noCollission()"),
                List.of("getCollisionShape")));
        }
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
        // PAR-METAL-18: the bronze and brass bells extend vanilla BellBlock with no shape override: vanilla's getVoxelShape
        // (the same outline for getShape and getCollisionShape), ported. One row per distinct outline (9 of the 16 states).
        String bell = B + "TFCBellBlock.java";
        for (String metal : BELL_METALS) {
            for (String cls : BELL_CLASSES) {
                s.add(new Spec(bellKey(metal, cls), "bell", Kind.OBJECT, List.of(bell), null, null,
                    List.of("public class TFCBellBlock extends BellBlock"), List.of("getShape(", "getCollisionShape"),
                    x -> bellShape(cls)));
            }
        }
        // PAR-METAL-19: the metal lamps (LampBlock.SHAPE and HANGING_SHAPE, shared by the nine lamps), ported.
        String lamp = D + "LampBlock.java";
        for (boolean hanging : new boolean[] {false, true}) {
            s.add(new Spec(lampKey(hanging), "lamp", Kind.OBJECT, List.of(lamp), null, null,
                List.of("public class LampBlock extends ExtendedBlock",
                    "Shapes.or(Block.box(5.0D, 0.0D, 5.0D, 11.0D, 7.0D, 11.0D), Block.box(6.0D, 7.0D, 6.0D, 10.0D, 9.0D, 10.0D))",
                    "Shapes.or(Block.box(5.0D, 1.0D, 5.0D, 11.0D, 8.0D, 11.0D), Block.box(6.0D, 8.0D, 6.0D, 10.0D, 10.0D, 10.0D))",
                    "return state.getValue(HANGING) ? HANGING_SHAPE : SHAPE;"),
                List.of("getCollisionShape"), x -> lampShape(hanging)));
        }
        // PAR-POT-14: the pit kiln, SHAPE_BY_LAYER[stage] (built in a Util.make loop, so ported) for the selection and the
        // collision (getShape, getCollisionShape and the support shape all return it).
        String kiln = D + "PitKilnBlock.java";
        for (int stage = 0; stage <= PIT_KILN_LIT; stage++) {
            final int st = stage;
            s.add(new Spec(pitKilnKey(stage), "pit_kiln", Kind.OBJECT, List.of(kiln), null, null, PIT_KILN_NEEDLES, List.of(),
                x -> pitKiln(st)));
        }
        // PAR-CLIM-04: the weather blocks. The snow pile extends vanilla SnowLayerBlock with no shape override: vanilla's
        // SHAPE_BY_LAYER (the selection 2 px per layer, the collision one layer lower), ported. The ice pile and sea ice
        // extend vanilla IceBlock (a full cube; ice is noOcclusion, see SEE_THROUGH). The icicle is ThinSpikeBlock:
        // TIP_SHAPE for the tip, PILLAR_SHAPE for the pieces above; getShape only, so the collision is the same.
        String snowPile = B + "SnowPileBlock.java";
        for (int layers = 1; layers <= SNOW_PILE_MAX; layers++) {
            final int n = layers;
            s.add(new Spec(snowPileKey(layers), "snow_layer", Kind.OBJECT, List.of(snowPile), null, null,
                List.of("public class SnowPileBlock extends SnowLayerBlock"), List.of("getShape(", "getCollisionShape"),
                x -> snowLayer(n)));
        }
        s.add(Spec.of("tfc:ice_pile", "full_cube", Kind.OBJECT, List.of(B + "IcePileBlock.java"), "Shapes.block()", "same",
            List.of("public class IcePileBlock extends IceBlock"), List.of("getShape(", "getCollisionShape")));
        s.add(Spec.of("tfc:sea_ice", "full_cube", Kind.OBJECT, List.of(B + "SeaIceBlock.java"), "Shapes.block()", "same",
            List.of("public class SeaIceBlock extends IceBlock"), List.of("getShape(", "getCollisionShape")));
        String spikeFile = B + "ThinSpikeBlock.java";
        for (String tip : List.of("true", "false")) {
            s.add(Spec.of("tfc:icicle[tip=" + tip + "]", "thin_spike", Kind.OBJECT, List.of(spikeFile, B + "IcicleBlock.java"),
                tip.equals("true") ? "TIP_SHAPE" : "PILLAR_SHAPE", "same",
                List.of("return state.getValue(TIP) ? TIP_SHAPE : PILLAR_SHAPE;", "public class IcicleBlock extends ThinSpikeBlock"),
                List.of("getCollisionShape")));
        }
        // PAR-FARM-03: the crop blocks. CropBlock.getShape is QUARTER_SHAPE, HALF_SHAPE or FULL_SHAPE by growth (the double
        // crops' top half is HALF_SHAPE, the dead crops' mature one FULL_SHAPE; no class below CropBlock overrides it);
        // Crop.dead() registers every crop, dead and wild block noCollission(). One row per outline.
        String cropFile = B + "crop/CropBlock.java";
        String cropEnum = B + "crop/Crop.java";
        for (String shape : CROP_ROWS) {
            s.add(Spec.of(cropKey(shape), "crop_" + shape, Kind.OBJECT, List.of(cropFile, cropEnum),
                shape.toUpperCase(java.util.Locale.ROOT) + "_SHAPE", "Shapes.empty()",
                List.of("public static final VoxelShape QUARTER_SHAPE = box(2, 0, 2, 14, 4, 14);",
                    "public static final VoxelShape HALF_SHAPE = box(2, 0, 2, 14, 8, 14);",
                    "public static final VoxelShape FULL_SHAPE = box(2, 0, 2, 14, 16, 14);",
                    "return ExtendedProperties.of(MapColor.PLANT).noCollission().randomTicks().strength(0.4F)"
                        + ".sound(SoundType.CROP).flammable(60, 30).pushReaction(PushReaction.DESTROY);"),
                List.of("getCollisionShape")));
        }
        // PAR-FARM-14: the berry bushes. StationaryBerryBushBlock.getShape is HALF_PLANT for every state; the dead bush
        // (SeasonalPlantBlock.getShape) is the full cube at stage 2, else PLANT_SHAPE, both without collision; the selector knows
        // no carrier that tall without collision, so the dead bush has no row and is served on the bush's (BERRY_BUSH_KEY).
        String fruit = B + "plant/fruit/";
        String seasonal = fruit + "SeasonalPlantBlock.java";
        List<String> collisionNeedle = List.of("protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, "
            + "CollisionContext context)\n    {\n        return Shapes.empty();");
        s.add(Spec.of(BERRY_BUSH_KEY, "berry_bush", Kind.OBJECT, List.of(fruit + "StationaryBerryBushBlock.java", seasonal),
            "HALF_PLANT", "Shapes.empty()", List.of("private static final VoxelShape HALF_PLANT = box(2, 0, 2, 14, 8, 14);",
                "protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context)\n"
                    + "    {\n        return HALF_PLANT;", collisionNeedle.getFirst()), List.of()));
        // PAR-FARM-16: the fruit tree branches (and growing branches) are vanilla PipeBlocks of radius 0.25 (FruitTreeBranchBlock
        // super(0.25F, ...), no shape override; collision is the outline): the core 4..12 plus an arm per set connection flag.
        String branchFile = fruit + "FruitTreeBranchBlock.java";
        for (int bits = 0; bits < 64; bits++) {
            final Shape pipe = pipe(bits);
            s.add(new Spec(fruitBranchKey(bits), "fruit_branch", Kind.OBJECT, List.of(branchFile, B + "plant/PipePlantBlock.java"),
                null, null, List.of("super(0.25F, properties);"), List.of("getShape(", "getCollisionShape"), x -> new Shape[] {pipe, pipe}));
        }
        // PAR-FARM-17: the banana: TRUNK_0 and TRUNK_1 (stages 0, 1) collide, PLANT (stage 2) does not.
        String banana = fruit + "BananaPlantBlock.java";
        for (int stage = 0; stage < 3; stage++) {
            s.add(Spec.of(bananaKey(stage), "banana_" + stage, Kind.OBJECT, List.of(banana),
                stage == 0 ? "TRUNK_0" : stage == 1 ? "TRUNK_1" : "PLANT", stage == 2 ? "Shapes.empty()" : "same",
                List.of("private static final VoxelShape TRUNK_0 = box(4.0, 0.0, 4.0, 12.0, 16.0, 12.0);",
                    "private static final VoxelShape TRUNK_1 = box(5.0, 0.0, 5.0, 11.0, 16.0, 11.0);",
                    "public static final VoxelShape PLANT = box(2.0, 0.0, 2.0, 14.0, 6.0, 14.0);",
                    "return state.getValue(STAGE) == 2 ? Shapes.empty() : getShape(state, level, pos, context);"), List.of()));
        }
        // PAR-CLIM-27: the climate instruments (DeviceBlock subclasses; getShape only, so the collision is the same).
        for (String[] r : INSTRUMENT_SHAPES) {
            double[] b = java.util.Arrays.stream(r[4].split(",")).mapToDouble(Double::parseDouble).toArray();
            final Shape shape = Shape.box(b[0], b[1], b[2], b[3], b[4], b[5]);
            s.add(new Spec(instrumentKey(r[0], r[1]), "instrument_" + r[0], Kind.OBJECT, List.of(D + r[2], B + "devices/DeviceBlock.java"),
                null, null, List.of(r[3]), List.of("getCollisionShape"), x -> new Shape[] {shape, shape}));
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
        // PAR-WORLD-50: the corals (TFCCoralPlantBlock: BIG_SHAPE for coral, SMALL_SHAPE for the fans, noCollission) and
        // the wall fans (CoralWallFanBlock.SHAPES per facing), dead and living alike; one row per fluid state so the selector
        // keeps the twin's waterlogged state. The sea pickle's AABB per count, collision the same (no override).
        String coralDir = B + "plant/coral/";
        List<String> coralFiles = List.of(coralDir + "TFCCoralPlantBlock.java", coralDir + "Coral.java");
        for (String wet : List.of("false", "true")) {
            boolean w = Boolean.parseBoolean(wet);
            for (String type : List.of("coral", "dead_coral")) {
                s.add(Spec.of(coralKey(type, null, w), "coral", Kind.OBJECT, coralFiles, "BIG_SHAPE", "Shapes.empty()",
                    List.of("return shape;", "TFCCoralPlantBlock.BIG_SHAPE, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY)"
                        + ".requiresCorrectToolForDrops().noCollission()"), List.of("getCollisionShape")));
            }
            for (String type : List.of("coral_fan", "dead_coral_fan")) {
                s.add(Spec.of(coralKey(type, null, w), "coral_fan", Kind.OBJECT, coralFiles, "SMALL_SHAPE", "Shapes.empty()",
                    List.of("return shape;", "TFCCoralPlantBlock.SMALL_SHAPE, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY)"
                        + ".requiresCorrectToolForDrops().noCollission()"), List.of("getCollisionShape")));
            }
            for (String type : List.of("coral_wall_fan", "dead_coral_wall_fan")) {
                for (String f : CORAL_WALL_FAN_SHAPES.keySet()) {
                    final Shape shape = CORAL_WALL_FAN_SHAPES.get(f);
                    s.add(new Spec(coralKey(type, f, w), "coral_wall_fan", Kind.OBJECT,
                        List.of(coralDir + "CoralWallFanBlock.java", coralDir + "Coral.java"), null, null,
                        List.of("return SHAPES.get(state.getValue(FACING));", CORAL_WALL_FAN_SOURCE.get(f), "DEAD_CORAL_WALL_FAN((color, type) -> "
                            + "new CoralWallFanBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).requiresCorrectToolForDrops().noCollission()"),
                        List.of("getCollisionShape"), x -> new Shape[] {shape, Shape.EMPTY}));
                }
            }
            for (int n = 1; n <= 4; n++) {
                s.add(Spec.of(seaPickleKey(n, w), "sea_pickle", Kind.OBJECT, List.of(coralDir + "TFCSeaPickleBlock.java"),
                    SEA_PICKLE_AABBS.get(n - 1), "same", List.of((n == 1 ? "default" : "case " + n) + " -> " + SEA_PICKLE_AABBS.get(n - 1) + ";"),
                    List.of("VoxelShape getCollisionShape")));
            }
        }
        // PAR-METAL-03: the anvils. AnvilBlock.getShape picks SHAPE_X for facing east and west, SHAPE_Z for north and south;
        // RockAnvilBlock is one box. Neither overrides getCollisionShape.
        String anvil = D + "AnvilBlock.java";
        for (String axis : List.of("x", "z")) {
            s.add(Spec.of("tfc:metal/anvil[axis=" + axis + "]", "metal_anvil", Kind.OBJECT, List.of(anvil),
                "SHAPE_" + axis.toUpperCase(java.util.Locale.ROOT), "same",
                List.of("return state.getValue(FACING).getAxis() == Direction.Axis.X ? SHAPE_X : SHAPE_Z;"),
                List.of("getCollisionShape")));
        }
        s.add(Spec.of("tfc:rock/anvil", "rock_anvil", Kind.OBJECT, List.of(B + "rock/RockAnvilBlock.java"), "SHAPE", "same",
            List.of("return SHAPE;"), List.of("getCollisionShape")));
        // PAR-DECO-01: the wood stairs, slabs, fences and log fences extend vanilla StairBlock / SlabBlock / FenceBlock with no
        // shape override (Wood.BlockType STAIRS, SLAB, FENCE and LOG_FENCE), so their outlines are vanilla's (ported:
        // stairsShape, SLAB_SHAPES, fenceShape). One row per state, shared by every wood (as the candle colours share theirs).
        String wood = B + "wood/Wood.java";
        List<String> stairNeedles = List.of("public class TFCStairBlock extends StairBlock",
            "STAIRS(wood -> new TFCStairBlock(");
        List<String> shapeAbsent = List.of("getShape(", "getCollisionShape", "getVisualShape");
        for (String facing : STAIRS_FACINGS) {
            for (String half : STAIRS_HALVES) {
                for (String shape : STAIRS_SHAPES) {
                    for (boolean wet : new boolean[] {false, true}) {
                        final Shape outline = stairsShape(facing, half, shape);
                        s.add(new Spec(stairsKey(facing, half, shape, wet), "stairs", Kind.OBJECT,
                            List.of(B + "wood/TFCStairBlock.java", wood), null, null, stairNeedles, shapeAbsent,
                            x -> new Shape[] {outline, outline}));
                    }
                }
            }
        }
        for (String type : SLAB_TYPES) {
            for (boolean wet : new boolean[] {false, true}) {
                final Shape outline = SLAB_SHAPES.get(type);
                s.add(new Spec(slabKey(type, wet), "slab", type.equals("double") ? Kind.CUBE : Kind.OBJECT,
                    List.of(B + "wood/TFCSlabBlock.java", wood), null, null,
                    List.of("public class TFCSlabBlock extends SlabBlock", "SLAB(wood -> new TFCSlabBlock("), shapeAbsent,
                    x -> new Shape[] {outline, outline}));
            }
        }
        for (int mask = 0; mask < 16; mask++) {
            for (boolean wet : new boolean[] {false, true}) {
                final int m = mask;
                s.add(new Spec(fenceKey(mask, wet), "fence", Kind.OBJECT, List.of(B + "wood/TFCFenceBlock.java", wood), null,
                    null, List.of("public class TFCFenceBlock extends FenceBlock", "FENCE(wood -> new TFCFenceBlock(",
                        "LOG_FENCE(wood -> new TFCFenceBlock("), shapeAbsent,
                    x -> new Shape[] {fenceShape(m, FENCE_HEIGHT), fenceShape(m, FENCE_COLLISION_HEIGHT)}));
            }
        }
        // PAR-DECO-02: the wood doors, trapdoors, fence gates, buttons and pressure plates extend vanilla DoorBlock,
        // TrapDoorBlock, FenceGateBlock, ButtonBlock and PressurePlateBlock with no shape override, so their outlines are
        // vanilla's (ported below). One row per state, shared by every wood.
        for (String facing : DOOR_FACINGS) {
            for (String half : DOOR_HALVES) {
                for (String hinge : DOOR_HINGES) {
                    for (boolean open : BOOLS) {
                        for (boolean powered : BOOLS) {
                            final Shape outline = doorShape(facing, hinge, open);
                            s.add(new Spec(doorKey(facing, half, hinge, open, powered), "door", Kind.OBJECT,
                                List.of(B + "wood/TFCDoorBlock.java", wood), null, null,
                                List.of("public class TFCDoorBlock extends DoorBlock", "DOOR(wood -> new TFCDoorBlock("),
                                shapeAbsent, x -> new Shape[] {outline, outline}));
                        }
                    }
                }
            }
        }
        for (String facing : DOOR_FACINGS) {
            for (String half : STAIRS_HALVES) {
                for (boolean open : BOOLS) {
                    for (boolean powered : BOOLS) {
                        for (boolean wet : BOOLS) {
                            final Shape outline = trapdoorShape(facing, half, open);
                            s.add(new Spec(trapdoorKey(facing, half, open, powered, wet), "trapdoor", Kind.OBJECT,
                                List.of(B + "wood/TFCTrapDoorBlock.java", wood), null, null,
                                List.of("public class TFCTrapDoorBlock extends TrapDoorBlock",
                                    "TRAPDOOR(wood -> new TFCTrapDoorBlock("),
                                shapeAbsent, x -> new Shape[] {outline, outline}));
                        }
                    }
                }
            }
        }
        for (String facing : DOOR_FACINGS) {
            for (boolean inWall : BOOLS) {
                for (boolean open : BOOLS) {
                    for (boolean powered : BOOLS) {
                        final Shape[] outline = gateShapes(facing, inWall, open);
                        s.add(new Spec(gateKey(facing, inWall, open, powered), "fence_gate", Kind.OBJECT,
                            List.of(B + "wood/TFCFenceGateBlock.java", wood), null, null,
                            List.of("public class TFCFenceGateBlock extends FenceGateBlock",
                                "FENCE_GATE(wood -> new TFCFenceGateBlock("),
                            shapeAbsent, x -> outline));
                    }
                }
            }
        }
        for (String face : BUTTON_FACES) {
            for (String facing : DOOR_FACINGS) {
                for (boolean powered : BOOLS) {
                    final Shape outline = buttonShape(face, facing, powered);
                    s.add(new Spec(buttonKey(face, facing, powered), "button", Kind.OBJECT,
                        List.of(B + "wood/TFCWoodButtonBlock.java", wood), null, null,
                        List.of("public class TFCWoodButtonBlock extends ButtonBlock", "BUTTON(wood -> new TFCWoodButtonBlock("),
                        shapeAbsent, x -> new Shape[] {outline, Shape.EMPTY}));
                }
            }
        }
        for (boolean powered : BOOLS) {
            final Shape outline = Shape.box(1, 0, 1, 15, powered ? 0.5 : 1, 15);
            s.add(new Spec(plateKey(powered), "pressure_plate", Kind.OBJECT,
                List.of(B + "wood/TFCPressurePlateBlock.java", wood), null, null,
                List.of("public class TFCPressurePlateBlock extends PressurePlateBlock",
                    "PRESSURE_PLATE(wood -> new TFCPressurePlateBlock("),
                shapeAbsent, x -> new Shape[] {outline, Shape.EMPTY}));
        }
        // PAR-MECH-15: the axle (AbstractShaftAxleBlock.getShape picks SHAPE_X/Y/Z by AXIS; no getCollisionShape, and
        // ExtendedRotatedPillarBlock overrides neither, so the collision is the outline). One row per axis, shared by every wood.
        String shaft = B + "rotation/AbstractShaftAxleBlock.java";
        for (String axis : AXLE_AXES) {
            String upper = axis.toUpperCase(java.util.Locale.ROOT);
            s.add(Spec.of(axleKey(axis), "axle", Kind.OBJECT, List.of(shaft, B + "wood/ExtendedRotatedPillarBlock.java"),
                "SHAPE_" + upper, "same", List.of("case " + upper + " -> SHAPE_" + upper + ";",
                    "public abstract class AbstractShaftAxleBlock extends ExtendedRotatedPillarBlock"),
                List.of("getCollisionShape")));
        }
        return s;
    }

    static final List<Boolean> BOOLS = List.of(false, true);
    /** PAR-DECO-02: the horizontal facings in alphabetical order (the order of the vanilla twin's state keys). */
    public static final List<String> DOOR_FACINGS = List.of("north", "south", "west", "east");
    public static final List<String> DOOR_HALVES = List.of("upper", "lower");
    public static final List<String> DOOR_HINGES = List.of("left", "right");
    public static final List<String> BUTTON_FACES = List.of("floor", "wall", "ceiling");

    /** PAR-DECO-02: the shared tfc-shapes row of a wood door state (properties in vanilla's alphabetical order). */
    public static String doorKey(String facing, String half, String hinge, boolean open, boolean powered) {
        return "tfc:wood/door[facing=" + facing + ",half=" + half + ",hinge=" + hinge + ",open=" + open + ",powered="
            + powered + "]";
    }

    public static String trapdoorKey(String facing, String half, boolean open, boolean powered, boolean waterlogged) {
        return "tfc:wood/trapdoor[facing=" + facing + ",half=" + half + ",open=" + open + ",powered=" + powered
            + ",waterlogged=" + waterlogged + "]";
    }

    public static String gateKey(String facing, boolean inWall, boolean open, boolean powered) {
        return "tfc:wood/fence_gate[facing=" + facing + ",in_wall=" + inWall + ",open=" + open + ",powered=" + powered + "]";
    }

    public static String buttonKey(String face, String facing, boolean powered) {
        return "tfc:wood/button[face=" + face + ",facing=" + facing + ",powered=" + powered + "]";
    }

    public static String plateKey(boolean powered) {
        return "tfc:wood/pressure_plate[powered=" + powered + "]";
    }

    /** Vanilla {@code DoorBlock}: a 3 px leaf on the facing's side, turned to the hinge side when open. */
    static Shape doorShape(String facing, String hinge, boolean open) {
        boolean right = hinge.equals("right");
        String side = open ? switch (facing) {
            case "east" -> right ? "north" : "south";
            case "south" -> right ? "east" : "west";
            case "west" -> right ? "south" : "north";
            default -> right ? "west" : "east"; // north
        } : facing;
        return leaf(side);
    }

    private static Shape leaf(String side) {
        return switch (side) {
            case "south" -> Shape.box(0, 0, 0, 16, 16, 3);
            case "north" -> Shape.box(0, 0, 13, 16, 16, 16);
            case "east" -> Shape.box(0, 0, 0, 3, 16, 16);
            case "west" -> Shape.box(13, 0, 0, 16, 16, 16);
            default -> throw new IllegalArgumentException(side);
        };
    }

    /** Vanilla {@code TrapDoorBlock}: a 3 px panel on the bottom, the top, or (open) the facing's side. */
    static Shape trapdoorShape(String facing, String half, boolean open) {
        if (!open) {
            return half.equals("top") ? Shape.box(0, 13, 0, 16, 16, 16) : Shape.box(0, 0, 0, 16, 3, 16);
        }
        return leaf(facing.equals("north") ? "north" : facing.equals("south") ? "south" : facing.equals("east") ? "east"
            : "west");
    }

    /** Vanilla {@code FenceGateBlock}: selection 16 px (13 in a wall), collision 24 px and none when open. */
    static Shape[] gateShapes(String facing, boolean inWall, boolean open) {
        boolean z = facing.equals("north") || facing.equals("south");
        double h = inWall ? 13 : 16;
        Shape sel = z ? Shape.box(0, 0, 6, 16, h, 10) : Shape.box(6, 0, 0, 10, h, 16);
        Shape col = open ? Shape.EMPTY : z ? Shape.box(0, 0, 6, 16, 24, 10) : Shape.box(6, 0, 0, 10, 24, 16);
        return new Shape[] {sel, col};
    }

    /** Vanilla {@code ButtonBlock}: 2 px deep (1 px pressed) on the floor, ceiling or the facing's wall. */
    static Shape buttonShape(String face, String facing, boolean pressed) {
        double d = pressed ? 1 : 2;
        boolean x = facing.equals("east") || facing.equals("west");
        return switch (face) {
            case "floor" -> x ? Shape.box(6, 0, 5, 10, d, 11) : Shape.box(5, 0, 6, 11, d, 10);
            case "ceiling" -> x ? Shape.box(6, 16 - d, 5, 10, 16, 11) : Shape.box(5, 16 - d, 6, 11, 16, 10);
            default -> switch (facing) {
                case "north" -> Shape.box(5, 6, 16 - d, 11, 10, 16);
                case "south" -> Shape.box(5, 6, 0, 11, 10, d);
                case "west" -> Shape.box(16 - d, 6, 5, 16, 10, 11);
                default -> Shape.box(0, 6, 5, d, 10, 11); // east
            };
        };
    }

    /** PAR-MECH-15: TFC {@code RotatedPillarBlock.AXIS}, the axle's axes. */
    public static final List<String> AXLE_AXES = List.of("x", "y", "z");

    /** PAR-MECH-15: the shared tfc-shapes row of the axle along {@code axis} (every wood). */
    public static String axleKey(String axis) {
        return "tfc:wood/axle[axis=" + axis + "]";
    }

    /** PAR-DECO-01: blockstate-file order of the facings, halves and shapes. */
    public static final List<String> STAIRS_FACINGS = List.of("east", "north", "south", "west");
    public static final List<String> STAIRS_HALVES = List.of("bottom", "top");
    /** Vanilla {@code StairsShape} in ordinal order. */
    public static final List<String> STAIRS_SHAPES = List.of("straight", "inner_left", "inner_right", "outer_left", "outer_right");
    public static final List<String> SLAB_TYPES = List.of("bottom", "top", "double");
    /** PAR-DECO-01: the fence sides in the bit order of {@link #fenceKey} (north 1, east 2, south 4, west 8). */
    public static final List<String> FENCE_SIDES = List.of("north", "east", "south", "west");

    /** PAR-DECO-01: the shared tfc-shapes row of a wood stairs state. */
    public static String stairsKey(String facing, String half, String shape, boolean waterlogged) {
        return "tfc:wood/stairs[facing=" + facing + ",half=" + half + ",shape=" + shape + ",waterlogged=" + waterlogged + "]";
    }

    /** PAR-DECO-01: the shared tfc-shapes row of a wood slab state. */
    public static String slabKey(String type, boolean waterlogged) {
        return "tfc:wood/slab[type=" + type + ",waterlogged=" + waterlogged + "]";
    }

    /** PAR-DECO-01: the shared tfc-shapes row of a fence state ({@code mask}: bit i set = side {@link #FENCE_SIDES}[i]). */
    public static String fenceKey(int mask, boolean waterlogged) {
        return "tfc:wood/fence[east=" + bit(mask, 1) + ",north=" + bit(mask, 0) + ",south=" + bit(mask, 2) + ",waterlogged="
            + waterlogged + ",west=" + bit(mask, 3) + "]";
    }

    private static boolean bit(int mask, int i) {
        return (mask & (1 << i)) != 0;
    }

    /** Vanilla 1.21.1 {@code SlabBlock.BOTTOM_AABB}, {@code TOP_AABB} and a full block for {@code double}. */
    static final Map<String, Shape> SLAB_SHAPES = Map.of("bottom", Shape.box(0, 0, 0, 16, 8, 16),
        "top", Shape.box(0, 8, 0, 16, 16, 16), "double", Shape.FULL);

    /** Vanilla 1.21.1 {@code StairBlock.SHAPE_BY_STATE}, indexed by {@code shape.ordinal() * 4 + facing.get2DDataValue()}. */
    private static final int[] STAIRS_SHAPE_BY_STATE = {12, 5, 3, 10, 14, 13, 7, 11, 13, 7, 11, 14, 8, 4, 1, 2, 4, 1, 2, 8};
    /** Vanilla {@code Direction.get2DDataValue}: south 0, west 1, north 2, east 3. */
    private static final List<String> DATA_2D = List.of("south", "west", "north", "east");

    /**
     * Vanilla 1.21.1 {@code StairBlock.getShape}: the half slab plus the octets of {@code SHAPE_BY_STATE} (bits 1, 2, 4, 8 =
     * octets NN, PN, NP, PP of the other half; checked against the 1.21.11 server's own shapes). Selection = collision.
     */
    static Shape stairsShape(String facing, String half, String shape) {
        boolean top = half.equals("top");
        int bits = STAIRS_SHAPE_BY_STATE[STAIRS_SHAPES.indexOf(shape) * 4 + DATA_2D.indexOf(facing)];
        double y0 = top ? 0 : 8; // the octets sit in the other half
        Shape out = top ? Shape.box(0, 8, 0, 16, 16, 16) : Shape.box(0, 0, 0, 16, 8, 16);
        double[][] octets = {{0, 0}, {8, 0}, {0, 8}, {8, 8}}; // NN, PN, NP, PP as (x, z)
        for (int i = 0; i < 4; i++) {
            if ((bits & (1 << i)) != 0) {
                out = out.union(Shape.box(octets[i][0], y0, octets[i][1], octets[i][0] + 8, y0 + 8, octets[i][1] + 8));
            }
        }
        return out;
    }

    /** Vanilla 1.21.1 {@code FenceBlock}: outline 16 px, collision 24 px. */
    static final double FENCE_HEIGHT = 16;
    static final double FENCE_COLLISION_HEIGHT = 24;

    /** The 4 px post plus a 4 px wide arm to each connected side, {@code h} high. */
    static Shape fenceShape(int mask, double h) {
        Shape out = Shape.box(6, 0, 6, 10, h, 10);
        double[][] arms = {{6, 0, 10, 8}, {8, 6, 16, 10}, {6, 8, 10, 16}, {0, 6, 8, 10}}; // north, east, south, west (x1 z1 x2 z2)
        for (int i = 0; i < 4; i++) {
            if (bit(mask, i)) {
                out = out.union(Shape.box(arms[i][0], 0, arms[i][1], arms[i][2], h, arms[i][3]));
            }
        }
        return out;
    }

    /** PAR-WORLD-50: vanilla's {@code SeaPickleBlock} AABB constant names per count, as TFCSeaPickleBlock.getShape names them. */
    static final List<String> SEA_PICKLE_AABBS = List.of("ONE_AABB", "TWO_AABB", "THREE_AABB", "FOUR_AABB");
    /** PAR-WORLD-50: TFC {@code CoralWallFanBlock.SHAPES} (checked against its source by the spec needles). */
    static final Map<String, Shape> CORAL_WALL_FAN_SHAPES = coralWallFanShapes();

    /** PAR-WORLD-50: the source text of each {@code CoralWallFanBlock.SHAPES} entry. */
    static final Map<String, String> CORAL_WALL_FAN_SOURCE = Map.of(
        "north", "Direction.NORTH, Block.box(0.0D, 4.0D, 5.0D, 16.0D, 12.0D, 16.0D)",
        "east", "Direction.EAST, Block.box(0.0D, 4.0D, 0.0D, 11.0D, 12.0D, 16.0D)",
        "south", "Direction.SOUTH, Block.box(0.0D, 4.0D, 0.0D, 16.0D, 12.0D, 11.0D)",
        "west", "Direction.WEST, Block.box(5.0D, 4.0D, 0.0D, 16.0D, 12.0D, 16.0D)");

    private static Map<String, Shape> coralWallFanShapes() {
        Map<String, Shape> m = new LinkedHashMap<>();
        m.put("north", Shape.box(0, 4, 5, 16, 12, 16));
        m.put("east", Shape.box(0, 4, 0, 11, 12, 16));
        m.put("south", Shape.box(0, 4, 0, 16, 12, 11));
        m.put("west", Shape.box(5, 4, 0, 16, 12, 16));
        return java.util.Collections.unmodifiableMap(m);
    }

    /**
     * PAR-WORLD-50: the tfc-shapes row of a coral type ({@code coral}, {@code dead_coral_fan}, ...; every colour shares it),
     * with the facing of a wall fan ({@code null} otherwise).
     */
    public static String coralKey(String type, String facing, boolean waterlogged) {
        return "tfc:coral/" + type + "[" + (facing == null ? "" : "facing=" + facing + ",") + "waterlogged=" + waterlogged + "]";
    }

    /** PAR-WORLD-50: the tfc-shapes row of a sea pickle state. */
    public static String seaPickleKey(int pickles, boolean waterlogged) {
        return "tfc:sea_pickle[pickles=" + pickles + ",waterlogged=" + waterlogged + "]";
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

    /** TFC {@code PitKilnBlock.LIT}: the stages are 0..16 (8 straw, 8 logs, lit). */
    public static final int PIT_KILN_LIT = 16;
    static final List<String> PIT_KILN_NEEDLES = List.of(
        "shapes[i] = Block.box(0.0D, 0.0D, 0.0D, 16.0D, i + 1, 16.0D);",
        "shapes[8 + i] = Shapes.or(shapes[7 + i], Block.box(4 * i, 8.0D, 0.0D, 4 * (i + 1), 12.0D, 16.0D));",
        "shapes[12 + i] = Shapes.or(shapes[11 + i], Block.box(4 * i, 12.0D, 0.0D, 4 * (i + 1), 16.0D, 16.0D));",
        "shapes[16] = Shapes.block();",
        "new VoxelShape[17]",
        "protected VoxelShape getShape(BlockState state, BlockGetter levle, BlockPos pos, CollisionContext context)\n    {\n        return SHAPE_BY_LAYER[state.getValue(STAGE)];",
        "protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context)\n    {\n        return SHAPE_BY_LAYER[state.getValue(STAGE)];");

    public static String pitKilnKey(int stage) {
        return "tfc:pit_kiln[stage=" + stage + "]";
    }

    /** {@code PitKilnBlock.SHAPE_BY_LAYER[stage]}: the straw slab 1..8 px, then the log rows (4 px wide) at 8..12 and 12..16. */
    static Shape[] pitKiln(int stage) {
        Shape[] shapes = new Shape[PIT_KILN_LIT + 1];
        for (int i = 0; i < 8; i++) {
            shapes[i] = Shape.box(0, 0, 0, 16, i + 1, 16);
        }
        for (int i = 0; i < 4; i++) {
            shapes[8 + i] = shapes[7 + i].union(Shape.box(4 * i, 8, 0, 4 * (i + 1), 12, 16));
        }
        for (int i = 0; i < 4; i++) {
            shapes[12 + i] = shapes[11 + i].union(Shape.box(4 * i, 12, 0, 4 * (i + 1), 16, 16));
        }
        shapes[PIT_KILN_LIT] = Shape.FULL;
        return new Shape[] {shapes[stage], shapes[stage]};
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

    /**
     * PAR-CLIM-27: the instruments' rows: block, state, source file, the constant line the sources must contain, the box
     * (pixels). The thermometer's shape switch maps {@code DOWN} and {@code UP} to the north box.
     */
    static final List<String[]> INSTRUMENT_SHAPES = List.of(
        new String[] {"thermometer", "facing=north", "ThermometerBlock.java",
            "private static final VoxelShape SHAPE_NORTH = box(4D, 1D, 14D, 12D, 15D, 16D);", "4,1,14,12,15,16"},
        new String[] {"thermometer", "facing=south", "ThermometerBlock.java",
            "private static final VoxelShape SHAPE_SOUTH = box(4D, 1D, 0D, 12D, 15D, 2D);", "4,1,0,12,15,2"},
        new String[] {"thermometer", "facing=east", "ThermometerBlock.java",
            "private static final VoxelShape SHAPE_EAST = box(0D, 1D, 4D, 2D, 15D, 12D);", "0,1,4,2,15,12"},
        new String[] {"thermometer", "facing=west", "ThermometerBlock.java",
            "private static final VoxelShape SHAPE_WEST = box(14D, 1D, 4D, 16D, 15D, 12D);", "14,1,4,16,15,12"},
        new String[] {"anemometer", "attached_wind_devices=false", "AnemometerBlock.java",
            "private static final VoxelShape SHAPE = box(6D, 0.0D, 6D, 10D, 8.0D, 10D);", "6,0,6,10,8,10"},
        new String[] {"anemometer", "attached_wind_devices=true", "AnemometerBlock.java",
            "private static final VoxelShape SHAPE_ATTACHED = box(6D, 0.0D, 6D, 10D, 16.0D, 10D);", "6,0,6,10,16,10"},
        new String[] {"vane", "attached_wind_devices=false", "VaneBlock.java",
            "private static final VoxelShape SHAPE = box(6D, 0.0D, 6D, 10D, 12.0D, 10D);", "6,0,6,10,12,10"},
        new String[] {"vane", "attached_wind_devices=true", "VaneBlock.java",
            "private static final VoxelShape SHAPE_ATTACHED = box(6D, 0.0D, 6D, 10D, 6.0D, 10D);", "6,0,6,10,6,10"},
        new String[] {"calendar_clock", "facing=up", "CalendarClockBlock.java",
            "private static final VoxelShape SHAPE_UP = box(1D, 0D, 1D, 15D, 2.0D, 15D);", "1,0,1,15,2,15"},
        new String[] {"calendar_clock", "facing=down", "CalendarClockBlock.java",
            "private static final VoxelShape SHAPE_DOWN = box(1D, 14D, 1D, 15D, 16.0D, 15D);", "1,14,1,15,16,15"},
        new String[] {"calendar_clock", "facing=north", "CalendarClockBlock.java",
            "private static final VoxelShape SHAPE_NORTH = box(1D, 1D, 14D, 15D, 15D, 16D);", "1,1,14,15,15,16"},
        new String[] {"calendar_clock", "facing=south", "CalendarClockBlock.java",
            "private static final VoxelShape SHAPE_SOUTH = box(1D, 1D, 0D, 15D, 15D, 2D);", "1,1,0,15,15,2"},
        new String[] {"calendar_clock", "facing=east", "CalendarClockBlock.java",
            "private static final VoxelShape SHAPE_EAST = box(0D, 1D, 1D, 2D, 15D, 15D);", "0,1,1,2,15,15"},
        new String[] {"calendar_clock", "facing=west", "CalendarClockBlock.java",
            "private static final VoxelShape SHAPE_WEST = box(14D, 1D, 1D, 16D, 15D, 15D);", "14,1,1,16,15,15"});

    public static String instrumentKey(String block, String state) {
        return "tfc:" + block + "[" + state + "]";
    }

    /**
     * PAR-FARM-14: the stationary bushes' outline row; the dead bush (12 x 16 x 12 or a full cube, no collision), the spreading
     * bushes and the canes (PAR-FARM-15: 8 x 9 x 16 boxes, no collision) borrow it: no tall or wide no-collision carrier exists.
     */
    public static final String BERRY_BUSH_KEY = "tfc:plant/berry_bush";

    /** PAR-FARM-25: the row every growing plant block (columns, kelp, vines, creepers, epiphytes) is served on. */
    public static final String GROWING_PLANT_KEY = "tfc:plant/body_plant";

    /** PAR-FARM-16: the connection flags of a fruit tree branch, bit i of a row is {@code FRUIT_FLAGS[i]} (TFC's PipeBlock order). */
    public static final List<String> FRUIT_FLAGS = List.of("north", "east", "south", "west", "up", "down");

    public static String fruitBranchKey(int bits) {
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            parts.add(FRUIT_FLAGS.get(i) + "=" + (((bits >> i) & 1) == 1));
        }
        return "tfc:fruit_branch[" + String.join(",", parts) + "]";
    }

    /** The appearance name of a connection pattern, {@code c010011}: one digit per flag in {@link #FRUIT_FLAGS} order. */
    public static String fruitBranchAppearance(int bits) {
        StringBuilder b = new StringBuilder("c");
        for (int i = 0; i < 6; i++) {
            b.append((bits >> i) & 1);
        }
        return b.toString();
    }

    /** Vanilla PipeBlock.makeShapes(0.25): the 8 x 8 x 8 core and, per flag, the arm from the core to that face. */
    static Shape pipe(int bits) {
        return pipe(bits, 4);
    }

    /**
     * Vanilla PipeBlock.makeShapes for a core of radius {@code r} pixels around the centre (0.25 is 4, 0.3125 is 5): the
     * 2r cube and, per flag, the arm from the core to that face.
     */
    static Shape pipe(int bits, double r) {
        double a = 8 - r;
        double b = 8 + r;
        Shape shape = Shape.box(a, a, a, b, b, b);
        double[][] arms = {{a, a, 0, b, b, b}, {a, a, a, 16, b, b}, {a, a, a, b, b, 16}, {0, a, a, b, b, b},
            {a, a, a, b, 16, b}, {a, 0, a, b, b, b}};
        for (int i = 0; i < 6; i++) {
            if (((bits >> i) & 1) == 1) {
                double[] arm = arms[i];
                shape = shape.union(Shape.box(arm[0], arm[1], arm[2], arm[3], arm[4], arm[5]));
            }
        }
        return shape;
    }

    // ---- PAR-FARM-30: the growing plants' per-shape outlines ------------------------------------------------------------

    /** The solid column body and head (TFC {@code solidTallPlant}: with collision) share these rows with the plain ones' boxes. */
    public static final String GROWING_BODY_SOLID_KEY = "tfc:plant/body_plant_solid";
    public static final String GROWING_WEEPING_KEY = "tfc:plant/weeping_shape";
    public static final String GROWING_TWISTING_KEY = "tfc:plant/twisting_shape";
    public static final String GROWING_TWISTING_SOLID_KEY = "tfc:plant/twisting_shape_solid";
    public static final String GROWING_THIN_KEY = "tfc:plant/thin_body_shape";
    public static final String GROWING_THIN_TOP_KEY = "tfc:plant/twisting_thin_shape";
    public static final String GROWING_FLOWER_KEY = "tfc:plant/kelp_tree_flower";
    /** {@code PlantBlock.PLANT_SHAPE}: the perched epiphytes and the saguaro fruit. */
    public static final String GROWING_PLANT_SHAPE_KEY = "tfc:plant/plant_shape";
    /** The face flags of the vines (vanilla: no down), the creeping plants and the pipes, bit i of a tag. */
    static final List<String> VINE_FLAGS = List.of("north", "east", "south", "west", "up");

    /**
     * The shape tags a growing plant block type needs (one outline row each, an appearance name prefix {@code <tag>_}). The
     * carrier budget of the selector holds only a handful of free outline-shaped states, so the face-pattern blocks are served
     * per face, not per combination: vines ({@code v<face>}) and creepers ({@code c<face>}) on the slab of the first set face
     * (north, east, south, west, up, down), the kelp tree and the branching cacti on their trunk (the 10 x 16 x 10 column,
     * the cactus colliding); epiphytes per facing ({@code e<facing>}); {@code none} is a pattern without any face (TFC's block
     * is removed then; it keeps the body row). Ledger divergence.
     */
    public static List<String> growingTags(String type) {
        switch (type) {
            case "WEEPING", "TWISTING" -> {
                return List.of("body");
            }
            case "TWISTING_SOLID" -> {
                return List.of("solidbody");
            }
            case "WEEPING_TOP" -> {
                return List.of("weep");
            }
            case "TWISTING_TOP", "TWISTING_TOP_SHORT" -> {
                return List.of("twist");
            }
            case "TWISTING_SOLID_TOP" -> {
                return List.of("solidtwist");
            }
            case "KELP" -> {
                return List.of("thin");
            }
            case "KELP_TOP" -> {
                return List.of("thintop");
            }
            case "KELP_TREE_FLOWER" -> {
                return List.of("flower");
            }
            case "PERCHED_EPIPHYTE", "CACTUS_FLOWER" -> {
                return List.of("plant");
            }
            case "EPIPHYTE" -> {
                return List.of("enorth", "eeast", "esouth", "ewest");
            }
            case "VINE" -> {
                return faceTags("v", VINE_FLAGS);
            }
            case "CREEPING", "CREEPING_STONE" -> {
                return faceTags("c", FRUIT_FLAGS);
            }
            case "KELP_TREE" -> {
                return List.of("ktrunk");
            }
            case "BRANCHING_CACTUS", "BRANCHING_CACTUS_TOP" -> {
                return List.of("xtrunk");
            }
            default -> throw new IllegalArgumentException("no growing plant shape for " + type);
        }
    }

    private static List<String> faceTags(String prefix, List<String> flags) {
        List<String> tags = new ArrayList<>();
        tags.add("none");
        flags.forEach(f -> tags.add(prefix + f));
        return List.copyOf(tags);
    }

    /** The tag of a state of a growing plant block type ({@code state}: the face flags and {@code facing} as strings). */
    public static String growingTag(String type, Map<String, String> state) {
        List<String> fixed = growingTags(type);
        if (fixed.size() == 1) {
            return fixed.getFirst();
        }
        if (type.equals("EPIPHYTE")) {
            return "e" + state.get("facing");
        }
        String prefix = type.equals("VINE") ? "v" : "c";
        for (String f : type.equals("VINE") ? VINE_FLAGS : FRUIT_FLAGS) {
            if (state.get(f).equals("true")) {
                return prefix + f;
            }
        }
        return "none";
    }

    /** The outline row of a tag. */
    public static String growingKey(String tag) {
        switch (tag) {
            case "body", "none" -> {
                return GROWING_PLANT_KEY;
            }
            case "solidbody" -> {
                return GROWING_BODY_SOLID_KEY;
            }
            case "weep" -> {
                return GROWING_WEEPING_KEY;
            }
            case "twist" -> {
                return GROWING_TWISTING_KEY;
            }
            case "solidtwist" -> {
                return GROWING_TWISTING_SOLID_KEY;
            }
            case "thin" -> {
                return GROWING_THIN_KEY;
            }
            case "thintop" -> {
                return GROWING_THIN_TOP_KEY;
            }
            case "flower" -> {
                return GROWING_FLOWER_KEY;
            }
            case "plant" -> {
                return GROWING_PLANT_SHAPE_KEY;
            }
            case "ktrunk" -> {
                return "tfc:plant/kelp_tree[up=true,down=true]";
            }
            case "xtrunk" -> {
                return "tfc:plant/branching_cactus[up=true,down=true]";
            }
            default -> { }
        }
        if (tag.startsWith("e")) {
            return "tfc:plant/epiphyte[facing=" + tag.substring(1) + "]";
        }
        return (tag.startsWith("v") ? "tfc:plant/vine[" : "tfc:plant/creeping[") + tag.substring(1) + "=true]";
    }

    /** The tag of every plant's states as appearance prefix: the demand pattern of a tag. */
    public static String growingPattern(String tag) {
        return tag + "_*";
    }

    private static void addGrowingPlantShapes(List<Spec> s) {
        String body = B + "plant/BodyPlantBlock.java";
        String plantJava = B + "plant/Plant.java";
        List<String> bodyFiles = List.of(body, plantJava);
        // the solid bodies (tree fern, cycad) and heads collide (TFC solidTallPlant has no noCollission)
        s.add(Spec.of(GROWING_BODY_SOLID_KEY, "growing_plant", Kind.OBJECT, bodyFiles, "BODY_SHAPE", "same",
            List.of("TWISTING_SOLID((plant, type) -> new BodyPlantBlock(fire(solidTallPlant()), plant.transform(), BodyPlantBlock.BODY_SHAPE"),
            List.of()));
        s.add(Spec.of(GROWING_WEEPING_KEY, "growing_plant", Kind.OBJECT, bodyFiles, "WEEPING_SHAPE", "Shapes.empty()",
            List.of("public static final VoxelShape WEEPING_SHAPE = box(4.0D, 9.0D, 4.0D, 12.0D, 16.0D, 12.0D);",
                "Direction.DOWN, BodyPlantBlock.WEEPING_SHAPE, 20, 26, plant"), List.of()));
        s.add(Spec.of(GROWING_TWISTING_KEY, "growing_plant", Kind.OBJECT, bodyFiles, "TWISTING_SHAPE", "Shapes.empty()",
            List.of("public static final VoxelShape TWISTING_SHAPE = box(4.0D, 0.0D, 4.0D, 12.0D, 15.0D, 12.0D);",
                "Direction.UP, BodyPlantBlock.TWISTING_SHAPE, 4, 8, plant"), List.of()));
        s.add(Spec.of(GROWING_TWISTING_SOLID_KEY, "growing_plant", Kind.OBJECT, bodyFiles, "TWISTING_SHAPE", "same",
            List.of("Direction.UP, BodyPlantBlock.TWISTING_SHAPE, 2, 5, plant"), List.of()));
        s.add(Spec.of(GROWING_THIN_KEY, "growing_plant", Kind.OBJECT, bodyFiles, "THIN_BODY_SHAPE", "Shapes.empty()",
            List.of("public static final VoxelShape THIN_BODY_SHAPE = box(5.0D, 0.0D, 5.0D, 11.0D, 16.0D, 11.0D);",
                "Direction.UP, BodyPlantBlock.THIN_BODY_SHAPE, TFCBlockStateProperties.SALT_WATER"), List.of()));
        s.add(Spec.of(GROWING_THIN_TOP_KEY, "growing_plant", Kind.OBJECT, bodyFiles, "TWISTING_THIN_SHAPE", "Shapes.empty()",
            List.of("public static final VoxelShape TWISTING_THIN_SHAPE = box(5.0D, 0.0D, 5.0D, 11.0D, 12.0D, 11.0D);",
                "Direction.UP, BodyPlantBlock.TWISTING_THIN_SHAPE, TFCBlockStateProperties.SALT_WATER"), List.of()));
        s.add(Spec.of(GROWING_FLOWER_KEY, "growing_plant", Kind.OBJECT, List.of(B + "plant/KelpTreeFlowerBlock.java", plantJava), "SHAPE",
            "Shapes.empty()", List.of("private static final VoxelShape SHAPE = Block.box(1.0D, 1.0D, 1.0D, 15.0D, 15.0D, 15.0D);",
                "KELP_TREE_FLOWER((plant, type) -> KelpTreeFlowerBlock.create(kelp(plant)", "noCollission().randomTicks().strength(1.0f)"),
            List.of()));
        s.add(Spec.of(GROWING_PLANT_SHAPE_KEY, "growing_plant", Kind.OBJECT, List.of(B + "plant/PlantBlock.java", plantJava), "PLANT_SHAPE",
            "Shapes.empty()", List.of("protected static final VoxelShape PLANT_SHAPE = box(2.0, 0.0, 2.0, 14.0, 16.0, 14.0);",
                "CACTUS_FLOWER((plant, type) -> PlantBlock.createCactusFlower(plant, fire(nonSolid(plant))",
                "PERCHED_EPIPHYTE((plant, type) -> PlantBlock.createPerchedEpiphyte(plant, fire(nonSolid(plant))"), List.of()));
        // the vines are vanilla VineBlocks (no shape override in TFCVineBlock), noCollission
        String vine = B + "plant/TFCVineBlock.java";
        double[][] vineBoxes = {{0, 0, 0, 16, 16, 1}, {15, 0, 0, 16, 16, 16}, {0, 0, 15, 16, 16, 16}, {0, 0, 0, 1, 16, 16},
            {0, 15, 0, 16, 16, 16}};
        for (int i = 0; i < 5; i++) {
            final Shape shape = union(vineBoxes, 1 << i);
            s.add(new Spec("tfc:plant/vine[" + VINE_FLAGS.get(i) + "=true]", "growing_vine", Kind.OBJECT, List.of(vine, plantJava),
                null, null, List.of("public class TFCVineBlock extends VineBlock implements IForgeBlockExtension",
                    "VINE((plant, type) -> new TFCVineBlock(fire(nonSolid(plant))))"), List.of("getShape(", "getCollisionShape"),
                x -> new Shape[] {shape, Shape.EMPTY}));
        }
        // the creeping plants: one 2 px slab per face, combined (CreepingPlantBlock.SHAPES), noCollission
        String creeping = B + "plant/CreepingPlantBlock.java";
        double[][] slabs = {{0, 0, 0, 16, 16, 2}, {14, 0, 0, 16, 16, 16}, {0, 0, 14, 16, 16, 16}, {0, 0, 0, 2, 16, 16},
            {0, 14, 0, 16, 16, 16}, {0, 0, 0, 16, 2, 16}};
        for (int i = 0; i < 6; i++) {
            final Shape shape = union(slabs, 1 << i);
            s.add(new Spec("tfc:plant/creeping[" + FRUIT_FLAGS.get(i) + "=true]", "growing_creeping", Kind.OBJECT, List.of(creeping, plantJava),
                null, null, List.of("protected static final VoxelShape UP_SHAPE = box(0.0, 14.0, 0.0, 16.0, 16.0, 16.0);",
                    "protected static final VoxelShape DOWN_SHAPE = box(0.0, 0.0, 0.0, 16.0, 2.0, 16.0);",
                    "protected static final VoxelShape NORTH_SHAPE = box(0.0, 0.0, 0.0, 16.0, 16.0, 2.0);",
                    "protected static final VoxelShape EAST_SHAPE = box(14.0, 0.0, 0.0, 16.0, 16.0, 16.0);",
                    "protected static final VoxelShape SOUTH_SHAPE = box(0.0, 0.0, 14.0, 16.0, 16.0, 16.0);",
                    "protected static final VoxelShape WEST_SHAPE = box(0.0, 0.0, 0.0, 2.0, 16.0, 16.0);",
                    "CREEPING((plant, type) -> CreepingPlantBlock.create(plant, fire(nonSolid(plant)"), List.of(),
                x -> new Shape[] {shape, Shape.EMPTY}));
        }
        // the epiphytes: the 12 px deep half block against the wall they cling to, noCollission
        String epiphyte = B + "plant/EpiphytePlantBlock.java";
        double[][] walls = {{0, 0, 4, 16, 16, 16}, {0, 0, 0, 12, 16, 16}, {0, 0, 0, 16, 16, 12}, {4, 0, 0, 16, 16, 16}};
        for (int i = 0; i < 4; i++) {
            final String f = HORIZONTAL_FACINGS.get(i);
            final double[] w = walls[i];
            s.add(new Spec("tfc:plant/epiphyte[facing=" + f + "]", "growing_epiphyte", Kind.OBJECT, List.of(epiphyte, plantJava),
                null, null, List.of("protected static final VoxelShape NORTH_SHAPE = box(0.0, 0.0, 4.0, 16.0, 16.0, 16.0);",
                    "protected static final VoxelShape EAST_SHAPE = box(0.0, 0.0, 0.0, 12.0, 16.0, 16.0);",
                    "protected static final VoxelShape SOUTH_SHAPE = box(0.0, 0.0, 0.0, 16.0, 16.0, 12.0);",
                    "protected static final VoxelShape WEST_SHAPE = box(4.0, 0.0, 0.0, 16.0, 16.0, 16.0);",
                    "EPIPHYTE((plant, type) -> EpiphytePlantBlock.create(plant, fire(nonSolid(plant)"), List.of(),
                x -> new Shape[] {Shape.box(w[0], w[1], w[2], w[3], w[4], w[5]), Shape.EMPTY}));
        }
        // the kelp tree (PipeBlock 0.3125, noCollission) and the branching cacti (the same pipe, solid: collision = outline)
        String kelpTree = B + "plant/KelpTreeBlock.java";
        String cactus = B + "plant/BranchingCactusBlock.java";
        {
            final Shape pipe = pipe(16 | 32, 5);
            s.add(new Spec("tfc:plant/kelp_tree[up=true,down=true]", "growing_kelp_tree", Kind.OBJECT,
                List.of(kelpTree, B + "plant/PipePlantBlock.java", plantJava), null, null,
                List.of("super(0.3125F, properties);", "KELP_TREE((plant, type) -> KelpTreeBlock.create(plant, ExtendedProperties.of(kelp(plant))",
                    "noCollission().randomTicks().strength(1.0f)"), List.of("getShape(", "getCollisionShape"),
                x -> new Shape[] {pipe, Shape.EMPTY}));
            s.add(new Spec("tfc:plant/branching_cactus[up=true,down=true]", "growing_cactus", Kind.OBJECT,
                List.of(cactus, B + "plant/PipePlantBlock.java", B + "plant/GrowingBranchingCactusBlock.java", plantJava), null, null,
                List.of("return new BranchingCactusBlock(0.3125f, properties);",
                    "return new GrowingBranchingCactusBlock(0.3125f, properties, body, flower);",
                    "BRANCHING_CACTUS((plant, type) -> BranchingCactusBlock.createBody(fire(solid())",
                    "BRANCHING_CACTUS_TOP((plant, type) -> GrowingBranchingCactusBlock.createGrowing(fire(solid())"),
                List.of("getShape(", "getCollisionShape"), x -> new Shape[] {pipe, pipe}));
        }
    }

    private static final List<String> HORIZONTAL_FACINGS = List.of("north", "east", "south", "west");

    /** The union of the boxes whose bit is set. */
    private static Shape union(double[][] boxes, int bits) {
        Shape shape = Shape.EMPTY;
        for (int i = 0; i < boxes.length; i++) {
            if (((bits >> i) & 1) == 1) {
                double[] b = boxes[i];
                shape = shape.union(Shape.box(b[0], b[1], b[2], b[3], b[4], b[5]));
            }
        }
        return shape;
    }

    /** PAR-FARM-17: the banana's outline rows by {@code stage}. */
    public static String bananaKey(int stage) {
        return "tfc:banana[stage=" + stage + "]";
    }

    /** PAR-FARM-03: TFC's three crop outlines ({@code CropBlock.QUARTER_SHAPE}, {@code HALF_SHAPE}, {@code FULL_SHAPE}). */
    public static final List<String> CROP_SHAPES = List.of("quarter", "half", "full");

    /**
     * The outlines with a row. The full outline (12 x 16 x 12, no collision) has none: the selector knows no carrier without
     * collision that tall (the best, a cactus, would block players walking through a field), so the full demands are served
     * on the half row ({@link HitboxDemands#cropDemandKey}, ledger divergence).
     */
    public static final List<String> CROP_ROWS = List.of("quarter", "half");

    /**
     * PAR-WORLD-58: the row of a groundcover, loose rock or small ore state in water: TFC's {@code fluid} set to
     * {@code water} ({@code tfc:ore/small[fluid=water]}). Salt water has the same outline and shares the row.
     */
    public static String wetKey(String key) {
        return key.endsWith("]") ? key.substring(0, key.length() - 1) + ",fluid=water]" : key + "[fluid=water]";
    }

    public static String cropKey(String shape) {
        return "tfc:crop[shape=" + shape + "]";
    }

    /** PAR-CLIM-04: vanilla {@code BlockStateProperties.LAYERS} 1..8, the snow pile's layers. */
    public static final int SNOW_PILE_MAX = 8;

    public static String snowPileKey(int layers) {
        return "tfc:snow_pile[layers=" + layers + "]";
    }

    /**
     * Vanilla {@code SnowLayerBlock.SHAPE_BY_LAYER}: index {@code layers} is {@code 2 * layers} px high (empty at 0), the
     * selection is {@code SHAPE_BY_LAYER[layers]}, the collision {@code SHAPE_BY_LAYER[layers - 1]}.
     */
    static Shape[] snowLayer(int layers) {
        return new Shape[] {snowHeight(layers), snowHeight(layers - 1)};
    }

    private static Shape snowHeight(int layers) {
        return layers <= 0 ? Shape.EMPTY : layers >= 8 ? Shape.FULL : Shape.box(0, 0, 0, 16, 2 * layers, 16);
    }

    /** PAR-METAL-18: the bell blocks (TFCBlocks BRONZE_BELL, BRASS_BELL) and the distinct outlines of vanilla's BellBlock. */
    public static final List<String> BELL_METALS = List.of("bronze", "brass");
    public static final List<String> BELL_CLASSES = List.of("ceiling", "floor_ns", "floor_ew", "double_ns", "double_ew",
        "single_north", "single_east", "single_south", "single_west");

    /** The representative TFC state of an outline class: {@code floor_ns} is facing north, {@code floor_ew} east. */
    public static String bellKey(String metal, String cls) {
        String state = switch (cls) {
            case "ceiling" -> "attachment=ceiling,facing=north";
            case "floor_ns" -> "attachment=floor,facing=north";
            case "floor_ew" -> "attachment=floor,facing=east";
            case "double_ns" -> "attachment=double_wall,facing=north";
            case "double_ew" -> "attachment=double_wall,facing=east";
            default -> "attachment=single_wall,facing=" + cls.substring("single_".length());
        };
        return "tfc:" + metal + "_bell[" + state + "]";
    }

    /** Vanilla {@code BellBlock.getVoxelShape}: the bell body (two boxes) plus the floor slab, the hanging bar or the ceiling stub. */
    static Shape[] bellShape(String cls) {
        Shape body = Shape.box(4, 4, 4, 12, 6, 12).union(Shape.box(5, 6, 5, 11, 13, 11));
        Shape shape = switch (cls) {
            case "floor_ns" -> Shape.box(0, 0, 4, 16, 16, 12);
            case "floor_ew" -> Shape.box(4, 0, 0, 12, 16, 16);
            case "ceiling" -> body.union(Shape.box(7, 13, 7, 9, 16, 9));
            case "double_ns" -> body.union(Shape.box(7, 13, 0, 9, 15, 16));
            case "double_ew" -> body.union(Shape.box(0, 13, 7, 16, 15, 9));
            case "single_north" -> body.union(Shape.box(7, 13, 0, 9, 15, 13));
            case "single_south" -> body.union(Shape.box(7, 13, 3, 9, 15, 16));
            case "single_east" -> body.union(Shape.box(3, 13, 7, 16, 15, 9));
            case "single_west" -> body.union(Shape.box(0, 13, 7, 13, 15, 9));
            default -> throw new IllegalArgumentException("unknown bell outline " + cls);
        };
        return new Shape[] {shape, shape};
    }

    /** PAR-METAL-19: the row of a lamp outline (the same for every metal). */
    public static String lampKey(boolean hanging) {
        return "tfc:metal/lamp[hanging=" + hanging + "]";
    }

    /** PAR-METAL-19: {@code LampBlock.SHAPE} (standing) and {@code HANGING_SHAPE}; getShape only, so the collision is the same. */
    static Shape[] lampShape(boolean hanging) {
        Shape shape = hanging ? Shape.box(5, 1, 5, 11, 8, 11).union(Shape.box(6, 8, 6, 10, 10, 10))
            : Shape.box(5, 0, 5, 11, 7, 11).union(Shape.box(6, 7, 6, 10, 9, 10));
        return new Shape[] {shape, shape};
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

    /** TFC's {@code SluiceBlock.createTopShape} and {@code createBottomShape}: eight stacked steps, north as the base. */
    private static final double[][] SLUICE_TOP = {{0, 0, 0, 16, 10, 1}, {0, 0, 1, 16, 9, 4}, {0, 0, 4, 16, 12, 5},
        {0, 0, 5, 16, 11, 8}, {0, 0, 8, 16, 14, 9}, {0, 0, 9, 16, 13, 12}, {0, 0, 12, 16, 16, 13}, {0, 0, 13, 16, 15, 16}};
    private static final double[][] SLUICE_BOTTOM = {{0, 0, 0, 16, 2, 1}, {0, 0, 1, 16, 1, 4}, {0, 0, 4, 16, 4, 5},
        {0, 0, 5, 16, 3, 8}, {0, 0, 8, 16, 6, 9}, {0, 0, 9, 16, 5, 12}, {0, 0, 12, 16, 8, 13}, {0, 0, 13, 16, 7, 16}};

    /** {@code SluiceBlock.getShape}: the steps of the half, turned like {@code Helpers.rotateShape} turns north. */
    private static Shape[] sluice(TfcShapeExpressions x, String facing, boolean upper) {
        String src = x.source(D + "SluiceBlock.java");
        for (double[][] boxes : new double[][][] {SLUICE_TOP, SLUICE_BOTTOM}) {
            for (double[] b : boxes) {
                require(src, "Helpers.rotateShape(direction, " + n(b[0]) + ", " + n(b[1]) + ", " + n(b[2]) + ", " + n(b[3])
                    + ", " + n(b[4]) + ", " + n(b[5]) + ")", "SluiceBlock.java");
            }
        }
        require(src, "private static VoxelShape createTopShape(Direction direction)", "SluiceBlock.java");
        require(src, "private static VoxelShape createBottomShape(Direction direction)", "SluiceBlock.java");
        require(x.source(HELPERS), "case EAST -> Block.box(16 - z2, y1, x1, 16 - z1, y2, x2);", "Helpers.java");
        int turns = List.of("north", "east", "south", "west").indexOf(facing);
        Shape north = Shape.EMPTY;
        for (double[] b : upper ? SLUICE_TOP : SLUICE_BOTTOM) {
            north = north.union(Shape.box(b[0], b[1], b[2], b[3], b[4], b[5]));
        }
        Shape shape = north.rotateY(turns);
        return new Shape[] {shape, shape};
    }

    private static String n(double v) {
        return Long.toString((long) v);
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

    /** PAR-METAL-14: the row of the bloomery with {@code facing}, open or closed. */
    public static String bloomeryKey(String facing, boolean open) {
        return "tfc:bloomery[facing=" + facing + ",open=" + open + "]";
    }

    /** PAR-METAL-14: the row of the bloom with {@code layers} (1 to 8). */
    public static String bloomKey(int layers) {
        return "tfc:bloom[layers=" + layers + "]";
    }

    /** PAR-METAL-13: the mold table's row. */
    public static final String MOLD_TABLE_KEY = "tfc:mold_table";

    /**
     * PAR-METAL-13: the row of the channel whose connections are the bits of {@code mask}, TFC's index of
     * {@code ChannelBlock.SHAPES}: bit {@code get2DDataValue} (south 0, west 1, north 2, east 3).
     */
    public static String channelKey(int mask) {
        return "tfc:channel[east=" + ((mask & 8) != 0) + ",south=" + ((mask & 1) != 0) + ",west=" + ((mask & 2) != 0)
            + ",north=" + ((mask & 4) != 0) + "]";
    }

    /** PAR-METAL-16: {@code DoubleIngotPileBlock.SHAPES.length}; six ingots a layer ({@code COUNT_1_36}). */
    public static final int DOUBLE_INGOT_PILE_LAYERS = 6;

    /** PAR-METAL-16: the row of the double ingot pile whose collision is {@code SHAPES[layer]} (counts 6 * layer + 1). */
    public static String doubleIngotPileKey(int layer) {
        return "tfc:double_ingot_pile[count=" + (6 * layer + 1) + "]";
    }

    static final List<String> DOUBLE_INGOT_PILE_NEEDLES = List.of(
        "box(0.25, 0, 0.25, 15.75, 1f / 6 * 16, 15.75),", "box(0.25, 0, 0.25, 15.75, 2f / 6 * 16, 15.75),",
        "box(0.25, 0, 0.25, 15.75, 3f / 6 * 16, 15.75),", "box(0.25, 0, 0.25, 15.75, 4f / 6 * 16, 15.75),",
        "box(0.25, 0, 0.25, 15.75, 5f / 6 * 16, 15.75),", "box(0.25, 0, 0.25, 15.75, 16, 15.75),",
        "return SHAPES[(state.getValue(getCountProperty()) - 1) / 6];");

    /** {@code DoubleIngotPileBlock.SHAPES[layer]}: the float heights {@code k / 6 * 16}, the sixth is the full 16. */
    static Shape[] doubleIngotPile(int layer) {
        float top = layer == DOUBLE_INGOT_PILE_LAYERS - 1 ? 16f : (layer + 1) / 6f * 16;
        Shape shape = Shape.box(0.25, 0, 0.25, 15.75, top, 15.75);
        return new Shape[] {shape, shape};
    }

    /** PAR-METAL-16: the row of the ingot pile whose collision is {@code SHAPES[layer]} (counts 8 * layer + 1). */
    public static String ingotPileKey(int layer) {
        return "tfc:ingot_pile[count=" + (8 * layer + 1) + "]";
    }

    /** {@code ChannelBlock.SHAPES[mask]}: the base post or'd with the arm of each set connection. */
    private static Shape[] channel(TfcShapeExpressions x, int mask) {
        List<String> file = List.of(D + "ChannelBlock.java");
        Shape shape = x.shape(file, "base");
        String[] arms = {"south", "west", "north", "east"};
        for (int bit = 0; bit < 4; bit++) {
            if ((mask & (1 << bit)) != 0) {
                shape = shape.union(x.shape(file, arms[bit]));
            }
        }
        return new Shape[] {shape, shape};
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

    private static volatile Map<String, Row> loaded;

    /** The committed table, by key (parsed once: the rows are immutable and every demand looks one up). */
    public static Map<String, Row> load() {
        Map<String, Row> rows = loaded;
        if (rows == null) {
            rows = java.util.Collections.unmodifiableMap(read());
            loaded = rows;
        }
        return rows;
    }

    private static Map<String, Row> read() {
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
