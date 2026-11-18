/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/TFCBlocks.java @ c3b4d9536 (litBlockEmission(15) of the firepit family;
 *       PAR-HEAT-26: the candle cakes' litBlockEmission(3), the jack-o'-lantern's alwaysLit(), DyeColor order of DYED_CANDLE),
 *       src/main/java/net/dries007/tfc/common/blocks/TFCCandleBlock.java @ c3b4d9536 (LIGHTING_SCALE)
 */
package dev.tfp.datagen.hitbox;

import dev.tfp.datagen.hitbox.HitboxAllocation.Demand;
import java.util.ArrayList;
import java.util.List;

/**
 * Every appearance datagen emits on an explicit vanilla carrier, in one list, so the budget pass sees them all. A
 * generator that emits a non-full-cube CE block adds its demands here and takes its carriers from
 * {@link HitboxAllocation#agreed} (or a per-family {@link HitboxAllocation.Result}); full cubes go through
 * VisualAllocator. {@code HardcodedCarrierTest} fails on a vanilla state literal anywhere else.
 */
public final class HitboxDemands {
    /** The cold forge model every heat level carries (ForgeDevices checks it against TFC's blockstate). */
    public static final String FORGE_MODEL = "tfp:block/charcoal_forge/heat_0";
    /** The forge's demand covers every heat level: one carrier, one model (the lit look is an overlay). */
    public static final String FORGE_APPEARANCE = "heat_*";
    public static final List<String> FACINGS = List.of("north", "east", "south", "west");
    /** TFC: {@code lightLevel(litBlockEmission(15))} for the firepit, grill and pot. */
    public static final int FIREPIT_LIT_LIGHT = 15;
    /** PAR-PROC-04: the quern's base model; both handstone states carry it (the handstone is a display entity). */
    public static final String QUERN_MODEL = "tfp:block/quern";
    /** PAR-PROC-04: the quern appearance of TFC {@code has_handstone=false} and {@code true}. */
    public static final String QUERN_BASE = "base";
    public static final String QUERN_HANDSTONE = "handstone";
    /** PAR-PROC-29: the scraping surface block and its one appearance (WAXED is the plugin's record state, no CE state). */
    public static final String SCRAPING_ID = "tfp:scraping";
    public static final String SCRAPING_APPEARANCE = "base";

    /** PAR-PROC-32: the upright barrel's two appearances (TFC {@code sealed=false|true}). */
    public static final String BARREL_UNSEALED = "unsealed";
    public static final String BARREL_SEALED = "sealed";
    /** PAR-PROC-32: TFC's woods ({@code Wood.java}); BarrelBlock checks the list against the checkout. */
    public static final List<String> BARREL_WOODS = List.of("acacia", "ash", "aspen", "birch", "blackwood", "chestnut",
        "douglas_fir", "hickory", "kapok", "mangrove", "maple", "oak", "palm", "pine", "rosewood", "sequoia", "spruce",
        "sycamore", "white_cedar", "willow");
    /**
     * PAR-PROC-38: the demand patterns of a barrel on its side (TFC {@code facing} a side): one per axis and rack, matching
     * the appearances {@code <axis>_<facing>_<sealed|unsealed>} and {@code <axis>_<facing>_<sealed|unsealed>_rack}.
     */
    public static final List<String> BARREL_SIDE_AXES = List.of("x", "z");
    /** PAR-PROC-38: the barrel rack block and its one appearance (TFC {@code BarrelRackBlock}). */
    public static final String BARREL_RACK_ID = "tfp:barrel_rack";
    public static final String BARREL_RACK_APPEARANCE = "base";
    public static final String BARREL_RACK_MODEL = "tfp:block/barrel_rack";
    /** PAR-PROC-38: served after every other demand (adding the rack and the side states moves no other carrier). */
    static final int BARREL_SIDE_WEIGHT = -2;

    /** PAR-PROC-34: the composter block, its stage-0 appearance and the compost types of TFC's {@code CompostType}. */
    public static final String COMPOSTER_ID = "tfp:composter";
    public static final String COMPOSTER_EMPTY = "empty";
    public static final List<String> COMPOSTER_TYPES = List.of("normal", "ready", "rotten");
    /** PAR-PROC-34: TFC's bin model ({@code tfc:block/composter/composter}), drawn on every stage's carrier. */
    public static final String COMPOSTER_MODEL = "tfp:block/composter/composter";

    /**
     * PAR-PROC-37: the log pile, the burning log pile and the charcoal pile (TFC registerNoItem blocks; the plugin's
     * CharcoalPitDevice writes their properties).
     */
    public static final String LOG_PILE_ID = "tfp:log_pile";
    public static final String BURNING_LOG_PILE_ID = "tfp:burning_log_pile";
    public static final String CHARCOAL_PILE_ID = "tfp:charcoal_pile";
    /** PAR-PROC-37: TFC's {@code CharcoalPileBlock.LAYERS} 1..8; the eighth layer is TFC's full cube. */
    public static final int CHARCOAL_PILE_MAX = 8;
    /** PAR-PROC-37: served after every other demand (adding these blocks moves no carrier of another block). */
    static final int CHARCOAL_WEIGHT = -1;
    /**
     * PAR-PROC-37: the first log pile count drawn from a CE pool (13..15 nearly full: the leaves pool, the selector's pick
     * once the farmland and campfire carriers are taken; 16 is TFC's full cube: the solid pool).
     */
    public static final int LOG_PILE_POOL_FROM = 13;
    public static final String CHARCOAL_POOL = "solid";
    public static final String BURNING_LOG_PILE_POOL = "leaves";
    /**
     * PAR-PROC-37: outlines whose best free carrier is a crafted block (the daylight detector, which TFC crafts) take the
     * next count's TFC row instead (the outline one log row or one layer larger; a ledger divergence).
     */
    public static final java.util.Map<String, String> BORROWED_ROWS = java.util.Map.of(
        TfcShapes.logPileKey("x", 5), TfcShapes.logPileKey("x", 6),
        TfcShapes.logPileKey("z", 5), TfcShapes.logPileKey("z", 6),
        "tfc:charcoal_pile[layers=3]", "tfc:charcoal_pile[layers=4]");

    /**
     * PAR-HEAT-21: TFC's torch blocks ({@code light/TorchBlocks}). The lit ones draw vanilla's torch on vanilla's torch
     * states (light 14, the same as TFC's: no other demand can take them); the dead ones TFC's dead torch models.
     */
    public static final String TORCH_ID = "tfp:torch";
    public static final String WALL_TORCH_ID = "tfp:wall_torch";
    public static final String DEAD_TORCH_ID = "tfp:dead_torch";
    public static final String DEAD_WALL_TORCH_ID = "tfp:dead_wall_torch";
    /** PAR-HEAT-21: the appearance of a standing torch; a wall torch's appearance is its facing. */
    public static final String TORCH_STANDING = "standing";
    /** PAR-HEAT-21: TFC {@code lightLevel(state -> 14)} on the lit torches. */
    public static final int TORCH_LIGHT = 14;
    /** PAR-HEAT-21: served after every other demand (adding the torches moves no carrier of another block). */
    static final int TORCH_WEIGHT = -3;

    /**
     * PAR-PROC-39: the powderkeg block and its three looks (TFC's blockstate: {@code powderkeg} unsealed, lit or not;
     * {@code powderkeg_sealed}; {@code powderkeg_lit} sealed and lit).
     */
    public static final String POWDERKEG_ID = "tfp:powderkeg";
    public static final String POWDERKEG_UNSEALED = "unsealed";
    public static final String POWDERKEG_SEALED = "sealed";
    public static final String POWDERKEG_LIT = "lit";
    /** PAR-PROC-39: the TFC outline row every powderkeg appearance takes (TFC {@code SHAPE}, the sealed keg). */
    public static final String POWDERKEG_ROW = "tfc:powderkeg[sealed=true]";
    /** PAR-PROC-39: served after every other demand (adding the keg moves no carrier of another block). */
    static final int POWDERKEG_WEIGHT = -4;

    private HitboxDemands() {
    }

    public static List<Demand> all() {
        List<Demand> d = new ArrayList<>();
        // PAR-METAL-07: every heat level on one carrier with the cold model; the lit levels are display overlays.
        d.add(new Demand("tfp:charcoal_forge", FORGE_APPEARANCE, "tfc:charcoal_forge", 0, FORGE_MODEL, true, 1));
        for (String f : FACINGS) {
            d.add(new Demand("tfp:bellows", f, "tfc:bellows[facing=" + f + "]", 0, HitboxAllocation.VANILLA_LOOK, true, 1));
        }
        // M3-HEAT-03 placeholder: the carrier's vanilla look until the pot's models exist (the grill's: PAR-HEAT-24, below).
        d.add(new Demand("tfp:pot", "lit", "tfc:pot", FIREPIT_LIT_LIGHT, HitboxAllocation.VANILLA_LOOK, true, 1));
        d.add(new Demand("tfp:pot", "unlit", "tfc:pot", 0, HitboxAllocation.VANILLA_LOOK, true, 1));
        // PAR-HEAT-24: the grill's old vanilla-look carrier stays reserved (no appearance uses it), so moving the grill to
        // its own carrier (below) moves no carrier of another block: freed, snow[layers=5] would go to the quern and shift
        // the snow-layer hide mappings. Released when PAR-INFRA-51 settles the snow-layer carriers.
        d.add(new Demand(GRILL_ID, GRILL_RESERVED, "tfc:grill", 0, HitboxAllocation.VANILLA_LOOK, true, 1));
        // PAR-PROC-04: the quern, TFC's two outlines (base only; base + handstone + handle), each with the base model.
        d.add(new Demand("tfp:quern", QUERN_BASE, "tfc:quern[has_handstone=false]", 0, QUERN_MODEL, true, 1));
        d.add(new Demand("tfp:quern", QUERN_HANDSTONE, "tfc:quern[has_handstone=true]", 0, QUERN_MODEL, true, 1));
        // PAR-PROC-29: the scraping surface; drawn empty, the look is the item display of PaperScrapingEffects.
        d.add(new Demand(SCRAPING_ID, SCRAPING_APPEARANCE, "tfc:scraping", 0, HitboxAllocation.TRANSPARENT, true, 1));
        // PAR-PROC-32: the upright barrel per wood, unsealed and sealed. The allocator serves at most 16 explicit model
        // carriers for TFC's 12x16x12 outline (the cactus ages; 40 looks are needed), so every barrel state is hitbox-only
        // and the per-wood look is an entity_renderer element (BarrelBlock). The side and rack states come with PAR-PROC-09.
        for (String w : BARREL_WOODS) {
            for (boolean sealed : new boolean[] {false, true}) {
                d.add(new Demand(barrelId(w), sealed ? BARREL_SEALED : BARREL_UNSEALED,
                    "tfc:wood/barrel[axis=y,sealed=" + sealed + "]", 0, HitboxAllocation.TRANSPARENT, true, 1));
            }
        }
        // PAR-PROC-38: the barrel on its side, per wood, axis and rack (TFC SHAPE_X / SHAPE_Z, with RACK_SHAPE when racked),
        // hitbox-only like the upright barrel (the look is TFC's side model as an item display, turned per facing), and the
        // barrel rack with TFC's model.
        for (String w : BARREL_WOODS) {
            for (String axis : BARREL_SIDE_AXES) {
                for (boolean rack : new boolean[] {false, true}) {
                    d.add(new Demand(barrelId(w), barrelSideDemand(axis, rack), barrelSideDemandKey(axis, rack), 0,
                        HitboxAllocation.TRANSPARENT, true, BARREL_SIDE_WEIGHT));
                }
            }
        }
        d.add(new Demand(BARREL_RACK_ID, BARREL_RACK_APPEARANCE, "tfc:barrel_rack", 0, BARREL_RACK_MODEL, true,
            BARREL_SIDE_WEIGHT));
        // PAR-PROC-34: the composter, one outline per stage (TFC's well from max(2, stage * 2)). Every stage carries TFC's
        // bin model (one look, so stages may share a carrier); the compost of each type is an entity_renderer overlay
        // (ComposterBlock), so the 25 looks of TFC's multipart take 8 carriers at most. Stage 8 is a full cube and is no
        // demand here: it is drawn from CE's solid pool (COMPOSTER_POOL_STAGE).
        // Weight 0: served after every earlier demand, so adding the composter moves no carrier of another block.
        for (int stage = 0; stage < COMPOSTER_POOL_STAGE; stage++) {
            d.add(new Demand(COMPOSTER_ID, composterDemand(stage), composterKey(stage), 0, COMPOSTER_MODEL, true, 0));
        }
        // PAR-PROC-37: the log pile, one outline and one look per axis and count up to LOG_PILE_POOL_FROM - 1 (TFC's
        // log_pile_<count> model, turned y 90 for axis x as blockstates/log_pile.json does), and the charcoal pile, layers
        // 1..7 with TFC's charcoal_height models. The fuller log piles, every burning log pile and the eight-layer charcoal
        // pile are drawn from CE's pools (POOLS, the selector's own pick for them), so they are no demands here.
        for (String axis : TfcShapes.LOG_PILE_AXES) {
            for (int count = 1; count < LOG_PILE_POOL_FROM; count++) {
                d.add(new Demand(LOG_PILE_ID, logPileAppearance(axis, count), logPileDemandKey(axis, count), 0,
                    logPileLook(axis, count, false), true, CHARCOAL_WEIGHT));
            }
        }
        for (int layers = 1; layers < CHARCOAL_PILE_MAX; layers++) {
            d.add(new Demand(CHARCOAL_PILE_ID, charcoalPileAppearance(layers), charcoalDemandKey(layers), 0,
                charcoalPileModel(layers), true, CHARCOAL_WEIGHT));
        }
        // PAR-HEAT-21: the torches. The lit ones keep vanilla's torch look (TFC's blockstates draw minecraft:block/torch and
        // wall_torch); the dead ones carry TFC's dead torch models (the wall model turned by TFC's blockstate y) or, with no
        // free carrier of light 0 in their outline, go hitbox-only with the model as an item display (TorchBlocks).
        d.add(new Demand(TORCH_ID, TORCH_STANDING, "tfc:torch", TORCH_LIGHT, HitboxAllocation.VANILLA_LOOK, true, TORCH_WEIGHT));
        d.add(new Demand(DEAD_TORCH_ID, TORCH_STANDING, "tfc:dead_torch", 0, deadTorchLook(null), true, TORCH_WEIGHT));
        for (String f : FACINGS) {
            d.add(new Demand(WALL_TORCH_ID, f, "tfc:wall_torch[facing=" + f + "]", TORCH_LIGHT, HitboxAllocation.VANILLA_LOOK,
                true, TORCH_WEIGHT));
            d.add(new Demand(DEAD_WALL_TORCH_ID, f, "tfc:dead_wall_torch[facing=" + f + "]", 0, deadTorchLook(f), true,
                TORCH_WEIGHT));
        }
        // PAR-PROC-39: the powderkeg, TFC's three models. A lit unsealed keg (lit by another explosion) draws TFC's
        // unsealed model, so it is the unsealed appearance; the lit look is sealed only. Every appearance takes TFC's
        // sealed outline (SHAPE): the hollow SHAPE_UNSEALED row's only carrier, a composter level, has a full 16x16 bottom
        // plate that culls the top face of the block below, so the floor showed through the keg's 1 px inset in game
        // (PAR-PROC-39 verify); on the sealed row the open keg draws its model on its own cactus age.
        d.add(new Demand(POWDERKEG_ID, POWDERKEG_UNSEALED, POWDERKEG_ROW, 0,
            powderkegModel(POWDERKEG_UNSEALED), true, POWDERKEG_WEIGHT));
        d.add(new Demand(POWDERKEG_ID, POWDERKEG_SEALED, POWDERKEG_ROW, 0,
            powderkegModel(POWDERKEG_SEALED), true, POWDERKEG_WEIGHT));
        d.add(new Demand(POWDERKEG_ID, POWDERKEG_LIT, POWDERKEG_ROW, 0,
            powderkegModel(POWDERKEG_LIT), true, POWDERKEG_WEIGHT));
        // PAR-HEAT-35 (owner decision 2026-11-18, was PAR-HEAT-23's four model carriers): every (lit, axis) appearance of
        // the firepit is hitbox-only on one shared carrier, drawn empty; TFC's lit or unlit model is an item display
        // turned per axis (Firepit), as the grill's.
        for (boolean lit : new boolean[] {true, false}) {
            for (String axis : FIREPIT_AXES) {
                d.add(new Demand(FIREPIT_ID, firepitAppearance(lit, axis), "tfc:firepit", lit ? FIREPIT_LIT_LIGHT : 0,
                    HitboxAllocation.TRANSPARENT, true, FIREPIT_WEIGHT));
            }
        }
        // PAR-HEAT-24: the grill, TFC's multipart (firepit_grill over firepit_lit_low or firepit_unlit) merged into one
        // model per lit value. No free state scores 0.5 or more for TFC's grill outline with a model of its own (snow
        // layers 5 has no free hide target), so every (lit, axis) appearance is hitbox-only on one carrier and the merged
        // model is an item display turned per axis (Grill).
        for (boolean lit : new boolean[] {true, false}) {
            for (String axis : FIREPIT_AXES) {
                d.add(new Demand(GRILL_ID, firepitAppearance(lit, axis), "tfc:grill", lit ? FIREPIT_LIT_LIGHT : 0,
                    HitboxAllocation.TRANSPARENT, true, GRILL_WEIGHT));
            }
        }
        // PAR-HEAT-26: the candles (per count, lit, waterlogged), the candle cakes (per lit) and the jack-o'-lantern (per
        // facing). TFC draws vanilla's own models for all of them, so each takes its vanilla twin's state of the same
        // properties with the vanilla look (twin): the client's flame and smoke particles and its extinguish prediction are
        // vanilla's candle code, which TFC's TFCCandleBlock / TFCCandleCakeBlock inherit.
        for (String id : candleIds(CANDLE_ID)) {
            for (int n = 1; n <= 4; n++) {
                for (boolean lit : new boolean[] {false, true}) {
                    for (boolean wet : new boolean[] {false, true}) {
                        d.add(new Demand(id, candleAppearance(n, lit, wet), TfcShapes.candleKey(n, lit, wet),
                            lit ? candleLight(n) : 0, HitboxAllocation.VANILLA_LOOK, true, CANDLE_WEIGHT));
                    }
                }
            }
        }
        for (String id : candleIds(CANDLE_CAKE_ID)) {
            for (boolean lit : new boolean[] {false, true}) {
                d.add(new Demand(id, "lit=" + lit, "tfc:candle_cake[lit=" + lit + "]", lit ? CANDLE_CAKE_LIGHT : 0,
                    HitboxAllocation.VANILLA_LOOK, true, CANDLE_WEIGHT));
            }
        }
        for (String f : FACINGS) {
            d.add(new Demand(JACK_O_LANTERN_ID, f, "tfc:jack_o_lantern[facing=" + f + "]", JACK_O_LANTERN_LIGHT,
                HitboxAllocation.VANILLA_LOOK, true, CANDLE_WEIGHT));
        }
        return d;
    }

    /** PAR-HEAT-26: TFC's candle, candle cake and jack-o'-lantern block ids (light/candle/CandleBlocks writes them). */
    public static final String CANDLE_ID = "tfp:candle";
    public static final String CANDLE_CAKE_ID = "tfp:candle_cake";
    public static final String JACK_O_LANTERN_ID = "tfp:jack_o_lantern";
    /** PAR-HEAT-26: vanilla {@code DyeColor} order (TFC {@code Helpers.mapOf(DyeColor.class, ...)}). */
    public static final List<String> CANDLE_COLORS = List.of("white", "orange", "magenta", "light_blue", "yellow", "lime", "pink",
        "gray", "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black");
    /** PAR-HEAT-26: TFC candle cake {@code litBlockEmission(3)}. */
    public static final int CANDLE_CAKE_LIGHT = 3;
    /** PAR-HEAT-26: TFC jack-o'-lantern {@code alwaysLit()}. */
    public static final int JACK_O_LANTERN_LIGHT = 15;
    /** PAR-HEAT-26: served after every other demand (only the twins' states serve these, so nothing else moves). */
    static final int CANDLE_WEIGHT = -6;

    /** PAR-HEAT-26: {@code base} and its dyed ids ({@code base/<colour>}), plain first. */
    public static List<String> candleIds(String base) {
        List<String> ids = new ArrayList<>();
        ids.add(base);
        CANDLE_COLORS.forEach(c -> ids.add(base + "/" + c));
        return List.copyOf(ids);
    }

    /** PAR-HEAT-26: TFC {@code TFCCandleBlock.LIGHTING_SCALE} while lit: {@code 3 * candles + 2}. */
    public static int candleLight(int candles) {
        return 3 * candles + 2;
    }

    /** PAR-HEAT-26: a candle appearance ({@code candles=2,lit=true,waterlogged=false}). */
    public static String candleAppearance(int candles, boolean lit, boolean waterlogged) {
        return "candles=" + candles + ",lit=" + lit + ",waterlogged=" + waterlogged;
    }

    /**
     * PAR-HEAT-26: the vanilla block whose own states and look serve {@code blockId} ({@code tfp:candle/red} to
     * {@code minecraft:red_candle}, {@code tfp:candle_cake} to {@code minecraft:candle_cake}), or null for every other
     * block ({@link HitboxCarrier.TfcState#twin}).
     */
    public static String twin(String blockId) {
        if (blockId.equals(JACK_O_LANTERN_ID)) {
            return "minecraft:jack_o_lantern";
        }
        for (String base : List.of(CANDLE_ID, CANDLE_CAKE_ID)) {
            String name = base.substring(base.indexOf(':') + 1);
            if (blockId.equals(base)) {
                return "minecraft:" + name;
            }
            if (blockId.startsWith(base + "/") && CANDLE_COLORS.contains(blockId.substring(base.length() + 1))) {
                return "minecraft:" + blockId.substring(base.length() + 1) + "_" + name;
            }
        }
        return null;
    }

    /** PAR-HEAT-23: the firepit block and its axes (TFC {@code HORIZONTAL_AXIS}). */
    public static final String FIREPIT_ID = "tfp:firepit";
    public static final List<String> FIREPIT_AXES = List.of("x", "z");
    /** PAR-HEAT-23: served after every other demand (adding the firepit models moves no carrier of another block). */
    static final int FIREPIT_WEIGHT = -5;

    /** PAR-HEAT-23: the firepit appearance of {@code lit} and {@code axis} ({@code lit_x}, {@code unlit_z}). */
    public static String firepitAppearance(boolean lit, String axis) {
        return (lit ? "lit" : "unlit") + "_" + axis;
    }

    /** PAR-HEAT-23: TFC's firepit model ({@code tfp:block/firepit_lit} / {@code _unlit}). */
    public static String firepitModel(boolean lit) {
        return lit ? "tfp:block/firepit_lit" : "tfp:block/firepit_unlit";
    }

    /** PAR-HEAT-23: TFC's blockstates/firepit.json turns the model y 90 for axis z, none for axis x. */
    public static int firepitRotation(String axis) {
        return switch (axis) {
            case "x" -> 0;
            case "z" -> 90;
            default -> throw new IllegalArgumentException("not a horizontal axis: " + axis);
        };
    }

    /** PAR-HEAT-24: the grill block. */
    public static final String GRILL_ID = "tfp:grill";
    /** PAR-HEAT-24: the demand that keeps the grill's old carrier {@code snow[layers=5]} reserved; no appearance. */
    public static final String GRILL_RESERVED = "reserved";
    /** PAR-HEAT-24: served after every other demand (adding the grill models moves no carrier of another block). */
    static final int GRILL_WEIGHT = -7;

    /** PAR-HEAT-24: the grill's merged model ({@code tfp:block/grill_lit} / {@code _unlit}). */
    public static String grillModel(boolean lit) {
        return lit ? "tfp:block/grill_lit" : "tfp:block/grill_unlit";
    }

    /** PAR-PROC-39: TFC's model of a powderkeg appearance ({@code tfp:block/powderkeg}, {@code _sealed}, {@code _lit}). */
    public static String powderkegModel(String appearance) {
        return switch (appearance) {
            case POWDERKEG_UNSEALED -> "tfp:block/powderkeg";
            case POWDERKEG_SEALED -> "tfp:block/powderkeg_sealed";
            case POWDERKEG_LIT -> "tfp:block/powderkeg_lit";
            default -> throw new IllegalArgumentException("not a powderkeg appearance: " + appearance);
        };
    }

    /**
     * PAR-HEAT-21: the look key of a dead torch: TFC's {@code dead_torch} model, or {@code dead_wall_torch} turned by
     * TFC's blockstate y of {@code facing} (east 0, south 90, west 180, north 270; two turns are two looks).
     */
    public static String deadTorchLook(String facing) {
        if (facing == null) {
            return "tfp:block/dead_torch";
        }
        int y = wallTorchY(facing);
        return y == 0 ? "tfp:block/dead_wall_torch" : "tfp:block/dead_wall_torch@y" + y;
    }

    /** PAR-HEAT-21: TFC's {@code dead_wall_torch} / {@code wall_torch} blockstate y of {@code facing}. */
    public static int wallTorchY(String facing) {
        return switch (facing) {
            case "east" -> 0;
            case "south" -> 90;
            case "west" -> 180;
            case "north" -> 270;
            default -> throw new IllegalArgumentException("not a horizontal facing: " + facing);
        };
    }

    /** PAR-PROC-34: the composter appearance of {@code type} at {@code stage} (TFC's model name; stage 0 is empty). */
    public static String composterAppearance(String type, int stage) {
        return stage == 0 ? COMPOSTER_EMPTY : type + "_" + stage;
    }

    /** PAR-PROC-34: the demand serving every appearance of {@code stage} ({@code empty}, or the pattern {@code *_N}). */
    public static String composterDemand(int stage) {
        return stage == 0 ? COMPOSTER_EMPTY : "*_" + stage;
    }

    /**
     * PAR-PROC-34: the composter stage whose outline is TFC's full cube ({@code SHAPES[8]}, the well has no height). Like
     * every full-cube block it is drawn from CraftEngine's {@code auto_state} pool {@link #COMPOSTER_POOL} (the selector
     * scores that pool 1.0 for the row {@code tfc:composter[stage=8]}; ComposterBlockTest checks it), each type with a
     * merged bin and compost model, so it is not a demand of the explicit-carrier budget.
     */
    public static final int COMPOSTER_POOL_STAGE = 8;
    public static final String COMPOSTER_POOL = "solid";

    /**
     * Full-cube appearances drawn from a CE pool by their generator instead of an explicit carrier: block id to the
     * appearance pattern and the pool. GeneratedCarrierTest accepts {@code auto_state} only for these.
     */
    public static final java.util.Map<String, java.util.Map.Entry<String, String>> FULL_CUBE_POOLS = java.util.Map.of(
        COMPOSTER_ID, java.util.Map.entry("*_" + COMPOSTER_POOL_STAGE, COMPOSTER_POOL));

    /**
     * PAR-PROC-37: the pool appearances of the charcoal pit blocks (block id to appearance pattern to pool), in the order
     * {@link #charcoalPitPool} tries them: every burning log pile (TFC's full cube, noOcclusion: the see-through pool), the
     * full log pile and the full charcoal pile (opaque full outlines), the nearly full log piles (the leaves pool, full
     * collision). GeneratedCarrierTest accepts {@code auto_state} for these too; CharcoalBlocksTest pins them.
     */
    public static final java.util.Map<String, java.util.Map<String, String>> POOLS = pools();

    private static java.util.Map<String, java.util.Map<String, String>> pools() {
        java.util.Map<String, String> log = new java.util.LinkedHashMap<>();
        log.put("*_" + TfcShapes.LOG_PILE_MAX, CHARCOAL_POOL);
        for (int count = LOG_PILE_POOL_FROM; count < TfcShapes.LOG_PILE_MAX; count++) {
            log.put("*_" + count, BURNING_LOG_PILE_POOL);
        }
        return java.util.Map.of(LOG_PILE_ID, java.util.Collections.unmodifiableMap(log),
            BURNING_LOG_PILE_ID, java.util.Map.of("*_*", BURNING_LOG_PILE_POOL),
            CHARCOAL_PILE_ID, java.util.Map.of(charcoalPileAppearance(CHARCOAL_PILE_MAX), CHARCOAL_POOL));
    }

    public static String composterKey(int stage) {
        return "tfc:composter[stage=" + stage + "]";
    }

    /** PAR-PROC-37: the log pile (and burning log pile) appearance of {@code axis} and {@code count} ({@code x_1}). */
    public static String logPileAppearance(String axis, int count) {
        return axis + "_" + count;
    }

    /**
     * PAR-PROC-37: TFC's log pile model of {@code count} ({@code tfp:block/log_pile/log_pile_<count>}, with
     * {@code _burning} for the burning pile).
     */
    public static String logPileModel(int count, boolean burning) {
        return "tfp:block/log_pile/log_pile_" + count + (burning ? "_burning" : "");
    }

    /** PAR-PROC-37: TFC's blockstates/log_pile.json turns the model y 90 for axis x, none for axis z. */
    public static int logPileRotation(String axis) {
        return axis.equals("x") ? 90 : 0;
    }

    /** PAR-PROC-37: the look key of a log pile state: the model and its turn (two turns are two looks). */
    public static String logPileLook(String axis, int count, boolean burning) {
        int y = logPileRotation(axis);
        return y == 0 ? logPileModel(count, burning) : logPileModel(count, burning) + "@y" + y;
    }

    /** PAR-PROC-37: the charcoal pile appearance of {@code layers} ({@code layers_1}). */
    public static String charcoalPileAppearance(int layers) {
        return "layers_" + layers;
    }

    /** PAR-PROC-37: TFC's charcoal pile model of {@code layers} (blockstates/charcoal_pile.json). */
    public static String charcoalPileModel(int layers) {
        return layers == CHARCOAL_PILE_MAX ? "tfp:block/charcoal_pile/charcoal_block"
            : "tfp:block/charcoal_pile/charcoal_height" + (2 * layers);
    }

    /** PAR-PROC-37: the tfc-shapes row the log pile demand of {@code axis} and {@code count} is served for. */
    public static String logPileDemandKey(String axis, int count) {
        String key = TfcShapes.logPileKey(axis, count);
        return BORROWED_ROWS.getOrDefault(key, key);
    }

    /** PAR-PROC-37: the tfc-shapes row the charcoal pile demand of {@code layers} is served for. */
    public static String charcoalDemandKey(int layers) {
        String key = charcoalPileKey(layers);
        return BORROWED_ROWS.getOrDefault(key, key);
    }

    /**
     * PAR-PROC-37: the CE pool an appearance of a charcoal pit block is drawn from, or null for an explicit carrier of
     * {@link #all()}.
     */
    public static String charcoalPitPool(String blockId, String appearance) {
        java.util.Map<String, String> m = POOLS.get(blockId);
        if (m == null) {
            return null;
        }
        for (java.util.Map.Entry<String, String> e : m.entrySet()) {
            if (matches(e.getKey(), appearance)) {
                return e.getValue();
            }
        }
        return null;
    }

    /** A {@code *} pattern ({@code heat_*}, {@code *_16}) or a plain name. */
    public static boolean matches(String pattern, String name) {
        if (!pattern.contains("*")) {
            return pattern.equals(name);
        }
        String[] parts = pattern.split("\\*", -1);
        return name.startsWith(parts[0]) && name.endsWith(parts[parts.length - 1])
            && name.length() >= parts[0].length() + parts[parts.length - 1].length();
    }

    public static String charcoalPileKey(int layers) {
        return "tfc:charcoal_pile[layers=" + layers + "]";
    }

    /**
     * PAR-PROC-38: the demand pattern of a barrel on its side along {@code axis}, racked or not. A demand holds one
     * {@code *} pattern; the unracked one, {@code x_*sealed}, takes both {@code x_<facing>_sealed} and
     * {@code x_<facing>_unsealed} and no {@code _rack} name (pinned in GeneratedCarrierTest).
     */
    public static String barrelSideDemand(String axis, boolean rack) {
        return axis + "_*" + (rack ? "_rack" : "sealed");
    }

    /**
     * PAR-PROC-38: the row the side demands are served for. TFC's own side rows ({@link #barrelSideKey}) rank
     * {@code farmland[moisture=6]} first among the free carriers; drawn empty, its full bottom face makes the client cull
     * the top face of the block below, so the gaps beside TFC's 12 wide side model and between the rack's legs showed
     * through the ground (in game, PAR-PROC-38). The side demands therefore borrow the sealed upright barrel's row, whose
     * transparent cactus carrier occludes nothing below (14 x 15 collision as farmland's 16 x 15; ledger divergence).
     */
    public static final String BARREL_SIDE_BORROWED_ROW = "tfc:wood/barrel[axis=y,sealed=true]";

    /**
     * PAR-PROC-38: the tfc-shapes row a side demand is served for ({@link #BARREL_SIDE_BORROWED_ROW}). The axis and rack
     * are kept on purpose: every side demand borrows the one row today, and a carrier with TFC's own side shape would
     * return {@link #barrelSideKey} here without touching the callers.
     */
    public static String barrelSideDemandKey(String axis, boolean rack) {
        return BARREL_SIDE_BORROWED_ROW;
    }

    /** PAR-PROC-38: the tfc-shapes row of a barrel on its side ({@code tfc:wood/barrel[axis=x,rack=false]}). */
    public static String barrelSideKey(String axis, boolean rack) {
        return "tfc:wood/barrel[axis=" + axis + ",rack=" + rack + "]";
    }

    /** PAR-PROC-32: the CE block id of the barrel of {@code wood}. */
    public static String barrelId(String wood) {
        return "tfp:wood/barrel/" + wood;
    }
}
