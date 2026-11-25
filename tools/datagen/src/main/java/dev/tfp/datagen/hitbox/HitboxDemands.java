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
    /** PAR-METAL-12: the crucible's one appearance (all 16 channel-connection states) and its model (TFC {@code crucible}). */
    public static final String CRUCIBLE_ID = "tfp:crucible";
    public static final String CRUCIBLE_APPEARANCE = "crucible";
    public static final String CRUCIBLE_MODEL = "tfp:block/crucible";
    /** PAR-METAL-12: served after every other demand, so adding the crucible moves no carrier of another block. */
    static final int CRUCIBLE_WEIGHT = -11;
    /** PAR-METAL-13: the channel (one appearance pattern per horizontal-connection mask) and the mold table. */
    public static final String CHANNEL_ID = "tfp:channel";
    public static final String MOLD_TABLE_ID = "tfp:mold_table";
    public static final String MOLD_TABLE_APPEARANCE = "table";
    /** PAR-METAL-13: served after every other demand, so adding them moves no carrier of another block. */
    static final int CHANNEL_WEIGHT = -13;

    /** PAR-METAL-13: the demand of the channels with the connections of {@code mask} (see {@link TfcShapes#channelKey}). */
    public static String channelPattern(int mask) {
        return "m" + mask;
    }

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

    /**
     * PAR-FARM-25: TFC's growing plant shapes (Plant.java: vines, weeping and twisting columns, kelp, kelp trees, branching
     * cacti, creeping plants, epiphytes), one block {@code tfp:plant/<name>} each, hitbox-only on one outline row
     * ({@link TfcShapes#GROWING_PLANT_KEY}); the look is item displays (GrowingPlantBlocks checks the list against the checkout).
     */
    public static final List<String> GROWING_PLANTS = List.of(
        "bird_nest_fern", "cobblestone_lichen", "elegant_sunburst_lichen", "guzmania", "licorice_fern",
        "artists_conk", "morning_glory", "moss", "philodendron", "ramirezella", "reindeer_lichen",
        "silver_bromeliad", "tank_bromeliad", "vriesea", "hanging_vines_plant", "hanging_vines",
        "spanish_moss_plant", "spanish_moss", "liana_plant", "liana", "flame_vine_plant", "flame_vine",
        "tree_fern_plant", "tree_fern", "cycad_plant", "cycad", "arundo_plant", "arundo",
        "dry_phragmite_plant", "dry_phragmite", "winged_kelp_plant", "winged_kelp", "leafy_kelp_plant",
        "leafy_kelp", "giant_kelp_plant", "giant_kelp_flower", "ivy", "jungle_vines", "saguaro_plant",
        "saguaro");
    public static final String GROWING_PLANT_PATTERN = "plant_*";
    /** PAR-FARM-25: served after every other demand (adding the growing plants moves no carrier of another block). */
    static final int GROWING_PLANT_WEIGHT = -16;

    public static String growingPlantId(String name) {
        return "tfp:plant/" + name;
    }

    /** PAR-FARM-26: TFC's soils (SoilBlockType.Variant; FarmlandBlocks checks the list) and the farmland of each. */
    public static final List<String> FARMLAND_SOILS = List.of("entisol", "aridisol", "oxisol", "fluvisol", "andisol",
        "podzol", "alfisol", "mollisol");
    public static final String FARMLAND_PREFIX = "tfp:farmland/";
    public static final String FARMLAND_APPEARANCE = "base";
    /** PAR-FARM-26: served after every other demand (adding the farmland moves no carrier of another block). */
    static final int FARMLAND_WEIGHT = -15;

    /** PAR-FARM-26: the CE block id of the farmland of {@code soil}. */
    public static String farmlandId(String soil) {
        return FARMLAND_PREFIX + soil;
    }

    /** PAR-FARM-26: our model of the farmland of {@code soil} (TFC's {@code tfc:block/farmland/<soil>}, renamed). */
    public static String farmlandModel(String soil) {
        return "tfp:block/farmland/" + soil;
    }

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
        "tfc:charcoal_pile[layers=3]", "tfc:charcoal_pile[layers=4]",
        // PAR-CLIM-04: the six-pixel snow pile's best carrier is the daylight detector, which TFC crafts.
        TfcShapes.snowPileKey(3), TfcShapes.snowPileKey(4));

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

    /**
     * PAR-CLIM-04: TFC's weather blocks. The snow pile ({@code tfc:snow_pile[layers=1..8]}) draws vanilla's snow models;
     * vanilla snow's own states are mostly taken (other blocks' carriers and their hide targets), so every pile appearance
     * is hitbox-only (a snow pile only exists on vegetation and loose rocks, so the item displays are few). The ice pile
     * and sea ice are TFC's translucent full cubes (the leaves pool, {@link #POOLS}); the icicle is {@code tip=true|false}
     * with TFC's models.
     */
    public static final String SNOW_PILE_ID = "tfp:snow_pile";
    public static final String ICE_PILE_ID = "tfp:ice_pile";
    public static final String SEA_ICE_ID = "tfp:sea_ice";
    public static final String ICICLE_ID = "tfp:icicle";
    public static final String ICE_POOL = "leaves";
    /** PAR-CLIM-04: the one appearance of the ice pile and sea ice. */
    public static final String ICE_APPEARANCE = "base";
    /** PAR-CLIM-04: served after every other demand (adding the weather blocks moves no carrier of another block). */
    static final int WEATHER_WEIGHT = -8;

    /** PAR-CLIM-04: the snow pile appearance of {@code layers} ({@code layers_3}); the model is vanilla's snow height. */
    public static String snowPileAppearance(int layers) {
        return "layers_" + layers;
    }

    /** PAR-CLIM-04: the tfc-shapes row the snow pile demand of {@code layers} is served for (see {@link #BORROWED_ROWS}). */
    public static String snowPileDemandKey(int layers) {
        String key = TfcShapes.snowPileKey(layers);
        return BORROWED_ROWS.getOrDefault(key, key);
    }

    /** PAR-CLIM-04: the icicle appearance of {@code tip} ({@code tip} or {@code pillar}). */
    public static String icicleAppearance(boolean tip) {
        return tip ? "tip" : "pillar";
    }

    /** PAR-CLIM-04: TFC's icicle model ({@code tfp:block/icicle_tip} or {@code tfp:block/icicle}). */
    public static String icicleModel(boolean tip) {
        return tip ? "tfp:block/icicle_tip" : "tfp:block/icicle";
    }

    /** PAR-CLIM-04: TFC's sea ice model; the ice pile draws vanilla's {@code minecraft:block/ice} through our own model. */
    public static String iceModel(String blockId) {
        return blockId.equals(SEA_ICE_ID) ? "tfp:block/sea_ice" : "tfp:block/ice_pile";
    }

    /**
     * PAR-FARM-03: TFC's crops ({@code Crop.java}; CropBlocks checks the list against the checkout) and the three block
     * families made of them: {@code tfp:crop/<name>}, {@code tfp:dead_crop/<name>}, {@code tfp:wild_crop/<name>}.
     */
    public static final List<String> CROPS = List.of("cassava", "green_bean", "lentil", "peanut", "soybean", "barley", "oat",
        "rye", "maize", "wheat", "rice", "beet", "cabbage", "carrot", "garlic", "onion", "potato", "squash", "tomato",
        "red_bell_pepper", "yellow_bell_pepper", "pumpkin", "melon", "canola", "radish", "alfalfa", "jute", "papyrus",
        "sugarcane");
    public static final List<String> CROP_KINDS = List.of("crop", "dead_crop", "wild_crop");
    /** PAR-CLIM-27: the climate instruments (TFC devices) and the clock's six facings (BlockStateProperties.FACING). */
    public static final String THERMOMETER_ID = "tfp:thermometer";
    public static final String ANEMOMETER_ID = "tfp:anemometer";
    public static final String VANE_ID = "tfp:vane";
    public static final String CLOCK_ID = "tfp:calendar_clock";
    public static final List<String> CLOCK_FACINGS = List.of("north", "east", "south", "west", "up", "down");
    static final int INSTRUMENT_WEIGHT = -11;
    /**
     * PAR-CLIM-27: crafted blocks that keep their vanilla look (CarrierPolicyRecipeTest.ALLOWED): the hitbox-only fallback never
     * takes them (a thin post such as the anemometer ranks the lightning rod first).
     */
    static final java.util.Set<String> KEEPS_VANILLA_LOOK = java.util.Set.of("minecraft:lightning_rod");

    /** PAR-FARM-15: TFC's SpreadingBush constants (SpreadingBushBlocks checks them) and the block families made of them. */
    public static final String SPREADING_PREFIX = "tfp:spreading_bush/";
    public static final List<String> SPREADING_SPECIES = List.of("blackberry", "raspberry", "blueberry", "elderberry");
    /** The blocks on the bush outline: the four bushes, the cranberry and the two dead blocks. */
    static final List<String> SPREADING_BUSHES = List.of("blackberry", "raspberry", "blueberry", "elderberry", "cranberry", "dead",
        "dead_cane");
    public static final String SPREADING_BUSH_PATTERN = "bush_*";
    static final int SPREADING_WEIGHT = -12;

    public static String canePattern(String facing) {
        return "cane_" + facing + "_*";
    }

    /** PAR-FARM-16: TFC's FruitBlocks.Tree constants (FruitTreeBlocks checks them) and the four block families made of them. */
    public static final List<String> FRUIT_TREES = List.of("cherry", "green_apple", "lemon", "olive", "orange", "peach", "plum",
        "red_apple");
    public static final String FRUIT_PREFIX = "tfp:plant/";
    /** The appearance patterns of a sapling (one outline for every saplings count). */
    public static final String FRUIT_SAPLING_PATTERN = "s_*";
    /** The leaves' one pattern: every lifecycle takes a state of the leaves pool. */
    public static final String FRUIT_LEAVES_PATTERN = "*";
    static final int FRUIT_WEIGHT = -20;
    public static final String FRUIT_LEAVES_POOL = "leaves";
    /** PAR-FARM-23: the pumpkin and melon blocks of the spreading crops and their rotten forms: full cubes on the solid pool. */
    public static final String FRUIT_BLOCK_PREFIX = "tfp:fruit/";
    public static final List<String> FRUIT_BLOCKS = List.of("pumpkin", "melon", "rotten_pumpkin", "rotten_melon");

    /** The block whose demands serve every tree's {@code part} ({@code sapling}, or {@code branch} for branches and growing branches). */
    public static String fruitDemandOwner(String part) {
        return fruitId(FRUIT_TREES.getFirst(), part.equals("growing_branch") ? "branch" : part);
    }

    public static String fruitId(String tree, String part) {
        return FRUIT_PREFIX + tree + "_" + part;
    }

    /** PAR-FARM-17: TFC's banana blocks, their appearance patterns by stage and demand weight. */
    public static final String BANANA_SAPLING_ID = "tfp:plant/banana_sapling";
    public static final String BANANA_PLANT_ID = "tfp:plant/banana_plant";
    public static final String BANANA_DEAD_ID = "tfp:plant/dead_banana_plant";
    public static final String BANANA_SAPLING_APPEARANCE = "base";

    public static String bananaPattern(int stage) {
        return "stage_" + stage + "*";
    }

    static final int BANANA_WEIGHT = -21;

    /** PAR-FARM-14: TFC's StationaryBush constants (BerryBlocks checks them), the block ids and the demand pattern. */
    public static final List<String> BERRY_SPECIES = List.of("snowberry", "bunchberry", "gooseberry", "cloudberry",
        "strawberry", "wintergreen_berry");
    public static final String BERRY_DEAD_ID = "tfp:berry_bush/dead";
    public static final String BERRY_PATTERN = "*_*";
    static final int BERRY_WEIGHT = -10;

    public static String berryId(String species) {
        return "tfp:berry_bush/" + species;
    }

    /** PAR-FARM-03: served after every other demand (adding the crops moves no carrier of another block). */
    static final int CROP_WEIGHT = -9;

    public static String cropId(String kind, String crop) {
        return "tfp:" + kind + "/" + crop;
    }

    /** PAR-FARM-03: the tfc-shapes row a crop outline is served for (the full one borrows the half row, see TfcShapes.CROP_ROWS). */
    public static String cropDemandKey(String shape) {
        return TfcShapes.cropKey(shape.equals("full") ? "half" : shape);
    }

    /** PAR-FARM-03: the demand pattern of the appearances on one outline ({@code quarter_*}). */
    public static String cropAppearancePattern(String shape) {
        return shape + "_*";
    }

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
        // PAR-PROC-13: the sluice per wood, facing and half, hitbox-only like the barrel (the look of each wood is TFC's
        // sluice model as an item display, SluiceBlock); every wood shares the carriers of one outline.
        for (String w : BARREL_WOODS) {
            for (String f : FACINGS) {
                for (boolean upper : new boolean[] {true, false}) {
                    d.add(new Demand(sluiceId(w), sluiceAppearance(f, upper), "tfc:wood/sluice[facing=" + f + ",upper=" + upper + "]",
                        0, HitboxAllocation.TRANSPARENT, true, SLUICE_WEIGHT));
                }
            }
        }
        // PAR-PROC-06: the loom per wood and facing, hitbox-only like the sluice (the look of each wood is TFC's loom model as
        // an item display, LoomBlock); every wood shares the carriers of one outline.
        for (String w : BARREL_WOODS) {
            for (String f : FACINGS) {
                d.add(new Demand(loomId(w), f, "tfc:wood/loom[facing=" + f + "]", 0, HitboxAllocation.TRANSPARENT, true,
                    LOOM_WEIGHT));
            }
        }
        // PAR-PROC-49: the scribing table (one outline per facing: the side boards turn) and the sewing table (one outline),
        // per wood, hitbox-only like the loom; every wood shares the carriers of one outline.
        for (String w : BARREL_WOODS) {
            for (String f : FACINGS) {
                d.add(new Demand("tfp:wood/scribing_table/" + w, f, "tfc:wood/scribing_table[facing=" + f + "]", 0,
                    HitboxAllocation.TRANSPARENT, true, TABLE_WEIGHT));
            }
            d.add(new Demand("tfp:wood/sewing_table/" + w, SEWING_APPEARANCE, "tfc:wood/sewing_table", 0,
                HitboxAllocation.TRANSPARENT, true, TABLE_WEIGHT));
        }
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
        // PAR-CLIM-04: the snow pile (hitbox-only, the look is a display) and the icicle with TFC's models; the ice pile
        // and sea ice are pool appearances (POOLS) and no demands.
        for (int layers = 1; layers <= TfcShapes.SNOW_PILE_MAX; layers++) {
            d.add(new Demand(SNOW_PILE_ID, snowPileAppearance(layers), snowPileDemandKey(layers), 0,
                HitboxAllocation.TRANSPARENT, true, WEATHER_WEIGHT));
        }
        for (boolean tip : new boolean[] {true, false}) {
            d.add(new Demand(ICICLE_ID, icicleAppearance(tip), "tfc:icicle[tip=" + tip + "]", 0, icicleModel(tip), true,
                WEATHER_WEIGHT));
        }
        // PAR-METAL-03: the metal anvils (per metal and axis) and the rock anvils (per igneous rock), hitbox-only on shared
        // carriers; the look is TFC's block model as an item display, turned per facing (AnvilBlocks).
        for (String metal : ANVIL_METALS) {
            for (String facing : ANVIL_FACINGS) {
                d.add(new Demand(anvilId(metal), facing, "tfc:metal/anvil[axis=" + anvilAxis(facing) + "]", 0,
                    HitboxAllocation.TRANSPARENT, true, ANVIL_WEIGHT));
            }
        }
        for (String rock : ANVIL_ROCKS) {
            d.add(new Demand(rockAnvilId(rock), ANVIL_ROCK_APPEARANCE, "tfc:rock/anvil", 0, HitboxAllocation.TRANSPARENT, true,
                ANVIL_WEIGHT));
        }
        // PAR-FARM-03: the crops, dead crops and wild crops of every Crop: one demand per block and TFC outline (quarter, half
        // or full, no collision), hitbox-only on one shared carrier per outline; the appearances of a block name the outline
        // they sit on ({@code quarter_age_0}) and the look is an item display (CropBlocks).
        for (String kind : CROP_KINDS) {
            for (String crop : CROPS) {
                for (String shape : TfcShapes.CROP_SHAPES) {
                    d.add(new Demand(cropId(kind, crop), cropAppearancePattern(shape), cropDemandKey(shape), 0,
                        HitboxAllocation.TRANSPARENT, true, CROP_WEIGHT));
                }
            }
        }
        // PAR-FARM-14: the six stationary berry bushes (every lifecycle and stage on the HALF_PLANT outline) and the dead
        // bush (served on the bush's row, see TfcShapes), hitbox-only; the look is TFC's model as an item display (BerryBlocks).
        for (String species : BERRY_SPECIES) {
            d.add(new Demand(berryId(species), BERRY_PATTERN, TfcShapes.BERRY_BUSH_KEY, 0, HitboxAllocation.TRANSPARENT, true,
                BERRY_WEIGHT));
        }
        for (int stage = 0; stage < 3; stage++) {
            d.add(new Demand(BERRY_DEAD_ID, "stage_" + stage, TfcShapes.BERRY_BUSH_KEY, 0, HitboxAllocation.TRANSPARENT,
                true, BERRY_WEIGHT));
        }
        // PAR-METAL-12: the crucible, TFC's shell outline (no channel nubs) with TFC's crucible model.
        d.add(new Demand(CRUCIBLE_ID, CRUCIBLE_APPEARANCE, "tfc:crucible", 0, CRUCIBLE_MODEL, true, CRUCIBLE_WEIGHT));
        // PAR-METAL-13: the channels (16 horizontal-connection outlines) and the mold table, hitbox-only; the look is item displays.
        for (int mask = 0; mask < 16; mask++) {
            d.add(new Demand(CHANNEL_ID, channelPattern(mask), TfcShapes.channelKey(mask), 0, HitboxAllocation.TRANSPARENT, true,
                CHANNEL_WEIGHT));
        }
        d.add(new Demand(MOLD_TABLE_ID, MOLD_TABLE_APPEARANCE, TfcShapes.MOLD_TABLE_KEY, 0, HitboxAllocation.TRANSPARENT, true,
            CHANNEL_WEIGHT));
        // PAR-CLIM-27: the four climate instruments, hitbox-only per TFC outline; the look is an item display (InstrumentBlocks).
        for (String f : FACINGS) {
            d.add(new Demand(THERMOMETER_ID, f + "_*", TfcShapes.instrumentKey("thermometer", "facing=" + f), 0,
                HitboxAllocation.TRANSPARENT, true, INSTRUMENT_WEIGHT));
        }
        for (String id : List.of(ANEMOMETER_ID, VANE_ID)) {
            String n = id.substring(id.indexOf(':') + 1);
            d.add(new Demand(id, "base", TfcShapes.instrumentKey(n, "attached_wind_devices=false"), 0,
                HitboxAllocation.TRANSPARENT, true, INSTRUMENT_WEIGHT));
            d.add(new Demand(id, "attached", TfcShapes.instrumentKey(n, "attached_wind_devices=true"), 0,
                HitboxAllocation.TRANSPARENT, true, INSTRUMENT_WEIGHT));
        }
        for (String f : CLOCK_FACINGS) {
            d.add(new Demand(CLOCK_ID, f + "_*", TfcShapes.instrumentKey("calendar_clock", "facing=" + f), 0,
                HitboxAllocation.TRANSPARENT, true, INSTRUMENT_WEIGHT));
        }
        // PAR-FARM-15: the spreading bushes (every bush, the cranberry and both dead blocks on the bush outline) and the
        // canes (a row per facing); hitbox-only, the look is an item display (SpreadingBushBlocks).
        for (String name : SPREADING_BUSHES) {
            d.add(new Demand(SPREADING_PREFIX + name, SPREADING_BUSH_PATTERN, TfcShapes.BERRY_BUSH_KEY, 0,
                HitboxAllocation.TRANSPARENT, true, SPREADING_WEIGHT));
        }
        for (String sp : SPREADING_SPECIES) {
            for (String f : FACINGS) {
                d.add(new Demand(SPREADING_PREFIX + sp + "_cane", canePattern(f), TfcShapes.BERRY_BUSH_KEY, 0,
                    HitboxAllocation.TRANSPARENT, true, SPREADING_WEIGHT));
            }
        }
        // PAR-DECO-01: the wood stairs, slabs, fences and log fences. TFC's blocks extend vanilla StairBlock, SlabBlock and
        // FenceBlock, so each takes the states of one vanilla twin of the same properties (woodTwin): the stairs and slabs
        // of each wood their own vanilla block with our models (TFC's model per state); the double slab is no demand (it
        // draws the planks' full-cube visual); every fence and log fence shares oak_fence drawn empty, the look is item
        // displays of TFC's post and side models (WoodFamilies).
        for (String w : BARREL_WOODS) {
            for (String facing : TfcShapes.STAIRS_FACINGS) {
                for (String half : TfcShapes.STAIRS_HALVES) {
                    for (String shape : TfcShapes.STAIRS_SHAPES) {
                        for (boolean wet : new boolean[] {false, true}) {
                            d.add(new Demand(woodStairsId(w), woodStairsAppearance(facing, half, shape, wet),
                                TfcShapes.stairsKey(facing, half, shape, wet), 0, woodStairsLook(w, facing, half, shape), false,
                                WOOD_FAMILY_WEIGHT));
                        }
                    }
                }
            }
            for (String type : WOOD_SLAB_CARRIED_TYPES) {
                for (boolean wet : new boolean[] {false, true}) {
                    d.add(new Demand(woodSlabId(w), woodSlabAppearance(type, wet), TfcShapes.slabKey(type, wet), 0,
                        woodSlabLook(w, type), false, WOOD_FAMILY_WEIGHT));
                }
            }
            for (String fence : List.of(woodFenceId(w), woodLogFenceId(w))) {
                for (int mask = 0; mask < 16; mask++) {
                    for (boolean wet : new boolean[] {false, true}) {
                        d.add(new Demand(fence, woodFenceAppearance(mask, wet), TfcShapes.fenceKey(mask, wet), 0,
                            HitboxAllocation.TRANSPARENT, false, WOOD_FAMILY_WEIGHT));
                    }
                }
            }
        }
        // PAR-DECO-02: the wood doors, trapdoors, fence gates, buttons and pressure plates, one demand per state, each
        // hitbox-only on the shared vanilla twin of the kind (every wood shares it, the look is item displays; WoodLogicBlocks).
        for (String w : BARREL_WOODS) {
            for (String kind : WOOD_LOGIC_KINDS) {
                for (String[] state : woodLogicStates(kind)) {
                    d.add(new Demand(woodLogicId(kind, w), state[0], state[1], 0, HitboxAllocation.TRANSPARENT, false,
                        WOOD_FAMILY_WEIGHT));
                }
            }
        }
        // PAR-WORLD-44 (PAR-WORLD-53 option a): the sapling, the potted sapling and the fallen leaves of every wood,
        // hitbox-only on the no-collision plant outlines (the look is TFC's model as an item display, WoodPlantBlocks);
        // the twigs share one tripwire pool state (POOLS), no demand.
        for (String w : BARREL_WOODS) {
            d.add(new Demand(woodPlantId(SAPLING, w), WOOD_PLANT_APPEARANCE, SAPLING_ROW, 0, HitboxAllocation.TRANSPARENT, true,
                WOOD_PLANT_WEIGHT));
            d.add(new Demand(woodPlantId(POTTED_SAPLING, w), WOOD_PLANT_APPEARANCE, POTTED_SAPLING_ROW, 0,
                HitboxAllocation.TRANSPARENT, true, WOOD_PLANT_WEIGHT));
            for (String row : FALLEN_LEAVES_ROWS) {
                d.add(new Demand(woodPlantId(FALLEN_LEAVES, w), row + "_*", TfcShapes.cropKey(row), 0, HitboxAllocation.TRANSPARENT,
                    true, WOOD_PLANT_WEIGHT));
            }
        }
        // PAR-WORLD-31: the loose rocks (per rock, plain and mossy, one outline per count) hitbox-only on one shared carrier
        // per count, the look an item display (TFC's model); the small ores and the groundcover types with TFC's model on
        // a carrier of their outline where one is free, else hitbox-only with the model as an item display (GroundcoverCeBlocks).
        for (String rock : LOOSE_ROCKS) {
            for (String kind : LOOSE_KINDS) {
                for (int count = 1; count <= LOOSE_MAX; count++) {
                    d.add(new Demand(looseRockId(kind, rock), looseAppearance(count), looseKey(count), 0,
                        HitboxAllocation.TRANSPARENT, true, GROUNDCOVER_WEIGHT));
                }
            }
        }
        for (String ore : SMALL_ORES) {
            d.add(new Demand(smallOreId(ore), GROUNDCOVER_APPEARANCE, SMALL_ORE_KEY, 0, groundcoverModel(ore), true,
                GROUNDCOVER_WEIGHT));
        }
        GROUNDCOVER_TYPES.forEach((type, shape) -> d.add(new Demand(groundcoverId(type), GROUNDCOVER_APPEARANCE,
            groundcoverKey(shape), 0, groundcoverModel(GROUNDCOVER_LOOKS.getOrDefault(type, type)), true, GROUNDCOVER_WEIGHT)));
        // PAR-FARM-16: the fruit trees. Every tree's saplings, branches and growing branches have the same outlines and the same
        // empty look, so one set of demands (owned by the first tree's blocks, see fruitDemandOwner) serves all of them: the
        // allocation does not grow by 1000 demands. Saplings: the bush outline (no tall no-collision carrier exists); branches:
        // one demand per connection pattern, TFC's PipeBlock outline. The leaves are full cubes on the leaves pool (POOLS).
        d.add(new Demand(fruitDemandOwner("sapling"), FRUIT_SAPLING_PATTERN, TfcShapes.BERRY_BUSH_KEY, 0,
            HitboxAllocation.TRANSPARENT, true, FRUIT_WEIGHT));
        for (int bits = 0; bits < 64; bits++) {
            d.add(new Demand(fruitDemandOwner("branch"), TfcShapes.fruitBranchAppearance(bits), TfcShapes.fruitBranchKey(bits), 0,
                HitboxAllocation.TRANSPARENT, true, FRUIT_WEIGHT));
        }
        // PAR-FARM-17: the banana: the sapling on the bush outline, the plant and the dead plant per stage (TFC TRUNK_0 and
        // TRUNK_1 collide, PLANT does not), hitbox-only with the look as an item display.
        d.add(new Demand(BANANA_SAPLING_ID, BANANA_SAPLING_APPEARANCE, TfcShapes.BERRY_BUSH_KEY, 0, HitboxAllocation.TRANSPARENT,
            true, BANANA_WEIGHT));
        for (String id : List.of(BANANA_PLANT_ID, BANANA_DEAD_ID)) {
            for (int stage = 0; stage < 3; stage++) {
                d.add(new Demand(id, bananaPattern(stage), TfcShapes.bananaKey(stage), 0, HitboxAllocation.TRANSPARENT, true,
                    BANANA_WEIGHT));
            }
        }
        // PAR-FARM-26: the farmland of each soil on TFC's farmland outline (SHAPE 16 x 15 x 16), TFC's model per soil.
        for (String soil : FARMLAND_SOILS) {
            d.add(new Demand(farmlandId(soil), FARMLAND_APPEARANCE, "tfc:farmland", 0, farmlandModel(soil), true,
                FARMLAND_WEIGHT));
        }
        // PAR-FARM-25: the growing plants, hitbox-only on the body plant outline, one demand per block (any appearance).
        for (String name : GROWING_PLANTS) {
            d.add(new Demand(growingPlantId(name), GROWING_PLANT_PATTERN, TfcShapes.GROWING_PLANT_KEY, 0,
                HitboxAllocation.TRANSPARENT, true, GROWING_PLANT_WEIGHT));
        }
        // PAR-MECH-15: the axle shaft per axis (every wood shares one carrier per axis), hitbox-only; the look is the spinning
        // shaft display (RotationBlocks).
        for (String axis : TfcShapes.AXLE_AXES) {
            d.add(new Demand(AXLE_ID, axis, TfcShapes.axleKey(axis), 0, HitboxAllocation.TRANSPARENT, true, AXLE_WEIGHT));
        }
        return d;
    }

    /** PAR-MECH-15: the demand block of the axles ({@code tfp:wood/axle/<wood>} share it); appearances are the axes. */
    public static final String AXLE_ID = "tfp:wood/axle";
    /** PAR-MECH-15: served after every other demand (adding the axles moves no carrier of another block). */
    static final int AXLE_WEIGHT = -22;

    /** PAR-WORLD-44: TFC's {@code Wood.BlockType} wood plant kinds, as block id folders ({@code tfp:wood/<kind>/<wood>}). */
    public static final String SAPLING = "sapling";
    public static final String POTTED_SAPLING = "potted_sapling";
    public static final String FALLEN_LEAVES = "fallen_leaves";
    public static final String TWIG = "twig";
    /** PAR-WORLD-44: the one appearance of a sapling, potted sapling and twig. */
    public static final String WOOD_PLANT_APPEARANCE = "base";
    /** PAR-WORLD-44: served after every other demand (adding the wood plants moves no carrier of another block). */
    static final int WOOD_PLANT_WEIGHT = -14;
    /**
     * PAR-WORLD-44: the rows the wood plants borrow (no collision, as TFC). TFC's sapling outline is 12 x 12 x 12; the
     * nearest row with no collision is the half crop (12 x 8 x 12). The potted sapling is TFC's flower pot (6 x 6 x 6, with
     * collision); it borrows the medium groundcover row (6 x 4 x 6, none). Ledger divergences.
     */
    public static final String SAPLING_ROW = TfcShapes.cropKey("half");
    public static final String POTTED_SAPLING_ROW = "tfc:groundcover[shape=medium]";
    /**
     * PAR-WORLD-44: the fallen leaves (TFC: the charcoal pile outlines, 2 to 16 px, no collision) borrow the quarter crop row
     * for layers 1..4 and the half crop row for 5..8; the appearance of {@code layers} is {@code <row>_<layers>}.
     */
    public static final List<String> FALLEN_LEAVES_ROWS = List.of("quarter", "half");
    /** PAR-WORLD-44: the CE pool the twigs share one state of (PAR-WORLD-53: 1 of the 3 tripwire states PAR-WORLD-12 left). */
    public static final String TWIG_POOL = "tripwire";
    /** PAR-WORLD-44: the share key of the twigs' one pool state. */
    public static final String TWIG_SHARE_KEY = "tfp:wood/twig";

    /** PAR-WORLD-44: the block id of a wood plant kind ({@link #SAPLING} and the others) of {@code wood}. */
    public static String woodPlantId(String kind, String wood) {
        return "tfp:wood/" + kind + "/" + wood;
    }

    /** PAR-WORLD-44: the fallen leaves appearance of {@code layers} ({@code quarter_3}, {@code half_5}). */
    public static String fallenLeavesAppearance(int layers) {
        return FALLEN_LEAVES_ROWS.get(layers <= 4 ? 0 : 1) + "_" + layers;
    }

    /** PAR-DECO-01: served after every other demand (the twins are reserved by only_class, so nothing else moves). */
    static final int WOOD_FAMILY_WEIGHT = -13;
    /** PAR-DECO-01: the slab types drawn on a twin state; {@code double} draws the planks' full-cube visual instead. */
    public static final List<String> WOOD_SLAB_CARRIED_TYPES = List.of("bottom", "top");
    /**
     * PAR-DECO-01: the vanilla stairs twin of each wood of {@link #BARREL_WOODS} (same index): vanilla blocks whose planks or
     * stone never exist in a TFC world (carrier-policy.json, only_class stairs). Not the oak and petrified oak slabs
     * (CraftEngine's internal mappings use them), no cut copper, no Nether wood.
     */
    public static final List<String> WOOD_STAIRS_TWINS = List.of("oak", "spruce", "birch", "jungle", "acacia", "dark_oak",
        "mangrove", "cherry", "pale_oak", "bamboo", "bamboo_mosaic", "polished_granite", "polished_diorite",
        "polished_andesite", "smooth_sandstone", "smooth_red_sandstone", "prismarine", "prismarine_brick", "dark_prismarine",
        "polished_deepslate");
    /** PAR-DECO-01: the vanilla slab twin of each wood (same index); their double states stay VisualAllocator's. */
    public static final List<String> WOOD_SLAB_TWINS = List.of("spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove",
        "cherry", "pale_oak", "bamboo", "bamboo_mosaic", "polished_granite", "polished_diorite", "polished_andesite",
        "smooth_sandstone", "smooth_red_sandstone", "prismarine", "prismarine_brick", "dark_prismarine", "polished_deepslate",
        "deepslate_tile");
    /** PAR-DECO-01: the one vanilla fence every TFC fence and log fence is carried by (hitbox-only). */
    public static final String WOOD_FENCE_TWIN = "minecraft:oak_fence";

    /** PAR-DECO-01: TFC's block ids (Wood.BlockType STAIRS, SLAB, FENCE, LOG_FENCE paths). */
    public static String woodStairsId(String wood) {
        return "tfp:wood/planks/" + wood + "_stairs";
    }

    public static String woodSlabId(String wood) {
        return "tfp:wood/planks/" + wood + "_slab";
    }

    public static String woodFenceId(String wood) {
        return "tfp:wood/fence/" + wood;
    }

    public static String woodLogFenceId(String wood) {
        return "tfp:wood/log_fence/" + wood;
    }

    /** PAR-DECO-01: the appearance of a stairs state (its full CE variant key). */
    public static String woodStairsAppearance(String facing, String half, String shape, boolean waterlogged) {
        return "facing=" + facing + ",half=" + half + ",shape=" + shape + ",waterlogged=" + waterlogged;
    }

    public static String woodSlabAppearance(String type, boolean waterlogged) {
        return "type=" + type + ",waterlogged=" + waterlogged;
    }

    /** PAR-DECO-01: the appearance of a fence state ({@code mask} as {@link TfcShapes#fenceKey}). */
    public static String woodFenceAppearance(int mask, boolean waterlogged) {
        String key = TfcShapes.fenceKey(mask, waterlogged);
        return key.substring(key.indexOf('[') + 1, key.length() - 1);
    }

    /** PAR-DECO-01: the look key of a stairs state: TFC draws one model and turn per (facing, half, shape). */
    public static String woodStairsLook(String wood, String facing, String half, String shape) {
        return "tfp:block/wood/planks/" + wood + "_stairs#facing=" + facing + ",half=" + half + ",shape=" + shape;
    }

    /** PAR-DECO-01: TFC's slab model of {@code type} ({@code <wood>_slab}, {@code <wood>_slab_top}). */
    public static String woodSlabLook(String wood, String type) {
        return switch (type) {
            case "bottom" -> "tfp:block/wood/planks/" + wood + "_slab";
            case "top" -> "tfp:block/wood/planks/" + wood + "_slab_top";
            default -> throw new IllegalArgumentException("not a carried slab type: " + type);
        };
    }

    /** PAR-DECO-02: the wood kinds drawn by item displays on one shared vanilla twin (Wood.BlockType DOOR ... PRESSURE_PLATE). */
    public static final List<String> WOOD_LOGIC_KINDS = List.of("fence_gate", "door", "trapdoor", "button", "pressure_plate");
    /** PAR-DECO-02: the vanilla block that carries each of {@link #WOOD_LOGIC_KINDS} (same order), hitbox-only. */
    public static final List<String> WOOD_LOGIC_TWINS = List.of("minecraft:oak_fence_gate", "minecraft:oak_door",
        "minecraft:oak_trapdoor", "minecraft:jungle_button", "minecraft:oak_pressure_plate");

    /** PAR-DECO-02: TFC's block id of a wood logic kind ({@code tfp:wood/door/oak}). */
    public static String woodLogicId(String kind, String wood) {
        return "tfp:wood/" + kind + "/" + wood;
    }

    /** PAR-DECO-02: every state of a wood logic kind as {appearance (the state's properties), tfc-shapes key}. */
    public static List<String[]> woodLogicStates(String kind) {
        List<String> keys = new ArrayList<>();
        switch (kind) {
            case "door" -> {
                for (String f : TfcShapes.DOOR_FACINGS) {
                    for (String h : TfcShapes.DOOR_HALVES) {
                        for (String hinge : TfcShapes.DOOR_HINGES) {
                            for (boolean o : TfcShapes.BOOLS) {
                                for (boolean p : TfcShapes.BOOLS) {
                                    keys.add(TfcShapes.doorKey(f, h, hinge, o, p));
                                }
                            }
                        }
                    }
                }
            }
            case "trapdoor" -> {
                for (String f : TfcShapes.DOOR_FACINGS) {
                    for (String h : TfcShapes.STAIRS_HALVES) {
                        for (boolean o : TfcShapes.BOOLS) {
                            for (boolean p : TfcShapes.BOOLS) {
                                for (boolean w : TfcShapes.BOOLS) {
                                    keys.add(TfcShapes.trapdoorKey(f, h, o, p, w));
                                }
                            }
                        }
                    }
                }
            }
            case "fence_gate" -> {
                for (String f : TfcShapes.DOOR_FACINGS) {
                    for (boolean wall : TfcShapes.BOOLS) {
                        for (boolean o : TfcShapes.BOOLS) {
                            for (boolean p : TfcShapes.BOOLS) {
                                keys.add(TfcShapes.gateKey(f, wall, o, p));
                            }
                        }
                    }
                }
            }
            case "button" -> {
                for (String face : TfcShapes.BUTTON_FACES) {
                    for (String f : TfcShapes.DOOR_FACINGS) {
                        for (boolean p : TfcShapes.BOOLS) {
                            keys.add(TfcShapes.buttonKey(face, f, p));
                        }
                    }
                }
            }
            case "pressure_plate" -> {
                for (boolean p : TfcShapes.BOOLS) {
                    keys.add(TfcShapes.plateKey(p));
                }
            }
            default -> throw new IllegalArgumentException("not a wood logic kind: " + kind);
        }
        List<String[]> out = new ArrayList<>();
        for (String key : keys) {
            out.add(new String[] {key.substring(key.indexOf('[') + 1, key.length() - 1), key});
        }
        return out;
    }

    /** PAR-DECO-01: the vanilla twin of a wood stairs, slab, fence or log fence block id, or null for every other id. */
    static String woodTwin(String blockId) {
        for (int i = 0; i < BARREL_WOODS.size(); i++) {
            String w = BARREL_WOODS.get(i);
            if (blockId.equals(woodStairsId(w))) {
                return "minecraft:" + WOOD_STAIRS_TWINS.get(i) + "_stairs";
            }
            if (blockId.equals(woodSlabId(w))) {
                return "minecraft:" + WOOD_SLAB_TWINS.get(i) + "_slab";
            }
            if (blockId.equals(woodFenceId(w)) || blockId.equals(woodLogFenceId(w))) {
                return WOOD_FENCE_TWIN;
            }
            for (int k = 0; k < WOOD_LOGIC_KINDS.size(); k++) {
                if (blockId.equals(woodLogicId(WOOD_LOGIC_KINDS.get(k), w))) {
                    return WOOD_LOGIC_TWINS.get(k); // PAR-DECO-02
                }
            }
        }
        return null;
    }

    /** PAR-WORLD-31: TFC {@code Rock} values in enum order (each has a loose and a mossy loose rock). */
    public static final List<String> LOOSE_ROCKS = List.of("granite", "diorite", "gabbro", "shale", "claystone", "limestone",
        "conglomerate", "dolomite", "chert", "chalk", "tuff", "rhyolite", "basalt", "andesite", "dacite", "quartzite", "slate",
        "phyllite", "schist", "gneiss", "marble");
    /** PAR-WORLD-31: TFC {@code Rock.BlockType.LOOSE} and {@code MOSSY_LOOSE}. */
    public static final List<String> LOOSE_KINDS = List.of("loose", "mossy_loose");
    /** PAR-WORLD-31: TFC {@code TFCBlockStateProperties.COUNT_1_3}. */
    public static final int LOOSE_MAX = 3;
    /** PAR-WORLD-31: TFC's graded {@code Ore} values in enum order (each has a small ore). */
    public static final List<String> SMALL_ORES = List.of("native_copper", "native_gold", "hematite", "native_silver",
        "cassiterite", "bismuthinite", "garnierite", "malachite", "magnetite", "limonite", "sphalerite", "tetrahedrite");
    /** PAR-WORLD-31: TFC {@code GroundcoverBlockType} in enum order, with its outline (a {@code TfcShapes} groundcover shape). */
    public static final java.util.Map<String, String> GROUNDCOVER_TYPES = groundcoverTypes();
    /** PAR-WORLD-31: the model of a groundcover type whose blockstate draws several (the first of TFC's list). */
    public static final java.util.Map<String, String> GROUNDCOVER_LOOKS = java.util.Map.of("driftwood", "driftwood_0", "stick",
        "stick1");
    public static final String GROUNDCOVER_APPEARANCE = "base";
    public static final String SMALL_ORE_KEY = "tfc:ore/small";
    /** PAR-WORLD-31: served after every other demand (adding the groundcover moves no carrier of another block). */
    static final int GROUNDCOVER_WEIGHT = -15;

    private static java.util.Map<String, String> groundcoverTypes() {
        java.util.Map<String, String> m = new java.util.LinkedHashMap<>();
        String[] rows = {"bone:medium", "clam:small", "driftwood:flat", "feather:flat", "flint:small", "guano:pixel_high",
            "humus:pixel_high", "mollusk:small", "mussel:small", "obsidian_shard:small", "pinecone:small", "pumice:small",
            "rotten_flesh:flat", "salt_lick:pixel_high", "seaweed:flat", "sea_urchin:medium", "stick:flat"};
        for (String r : rows) {
            m.put(r.substring(0, r.indexOf(':')), r.substring(r.indexOf(':') + 1));
        }
        return java.util.Collections.unmodifiableMap(m);
    }

    /** {@code tfp:rock/loose/<rock>} or {@code tfp:rock/mossy_loose/<rock>}. */
    public static String looseRockId(String kind, String rock) {
        return "tfp:rock/" + kind + "/" + rock;
    }

    public static String looseAppearance(int count) {
        return "count_" + count;
    }

    public static String looseKey(int count) {
        return "tfc:rock/loose[count=" + count + "]";
    }

    public static String smallOreId(String ore) {
        return "tfp:ore/small_" + ore;
    }

    public static String groundcoverId(String type) {
        return "tfp:groundcover/" + type;
    }

    public static String groundcoverKey(String shape) {
        return "tfc:groundcover[shape=" + shape + "]";
    }

    /** TFC's groundcover model ({@code tfp:block/groundcover/<name>}); the small ores draw {@code groundcover/<ore>}. */
    public static String groundcoverModel(String name) {
        return "tfp:block/groundcover/" + name;
    }

    /** PAR-METAL-03: the metals that have an anvil (TFC {@code Metal.BlockType.ANVIL}, tool-tier metals), in tier order. */
    public static final List<String> ANVIL_METALS = List.of("copper", "bronze", "bismuth_bronze", "black_bronze", "wrought_iron",
        "steel", "black_steel", "blue_steel", "red_steel");
    /** PAR-METAL-03: the igneous rocks (TFC {@code RockCategory.IGNEOUS_*}) that have a rock anvil. */
    public static final List<String> ANVIL_ROCKS = List.of("granite", "diorite", "gabbro", "rhyolite", "basalt", "andesite",
        "dacite");
    /** PAR-METAL-03: TFC {@code AnvilBlock.FACING}, the appearance of a metal anvil (the outline follows its axis). */
    public static final List<String> ANVIL_FACINGS = List.of("north", "east", "south", "west");
    public static final String ANVIL_ROCK_APPEARANCE = "base";
    /** PAR-METAL-03: served after every other demand (adding the anvils moves no carrier of another block). */
    static final int ANVIL_WEIGHT = -10;

    /** PAR-METAL-03: {@code AnvilBlock.getShape}: SHAPE_X for east and west, SHAPE_Z for north and south. */
    public static String anvilAxis(String facing) {
        return facing.equals("east") || facing.equals("west") ? "x" : "z";
    }

    public static String anvilId(String metal) {
        return "tfp:metal/anvil/" + metal;
    }

    public static String rockAnvilId(String rock) {
        return "tfp:rock/anvil/" + rock;
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
        String wood = woodTwin(blockId);
        if (wood != null) {
            return wood; // PAR-DECO-01: same outline class, our models (or drawn empty) on the twin's states
        }
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

    private static java.util.Map<String, java.util.Map<String, String>> basePools() {
        java.util.Map<String, String> log = new java.util.LinkedHashMap<>();
        log.put("*_" + TfcShapes.LOG_PILE_MAX, CHARCOAL_POOL);
        for (int count = LOG_PILE_POOL_FROM; count < TfcShapes.LOG_PILE_MAX; count++) {
            log.put("*_" + count, BURNING_LOG_PILE_POOL);
        }
        java.util.Map<String, java.util.Map<String, String>> all = new java.util.LinkedHashMap<>();
        all.put(LOG_PILE_ID, java.util.Collections.unmodifiableMap(log));
        all.put(BURNING_LOG_PILE_ID, java.util.Map.of("*_*", BURNING_LOG_PILE_POOL));
        all.put(CHARCOAL_PILE_ID, java.util.Map.of(charcoalPileAppearance(CHARCOAL_PILE_MAX), CHARCOAL_POOL));
        all.put(ICE_PILE_ID, java.util.Map.of(ICE_APPEARANCE, ICE_POOL));
        all.put(SEA_ICE_ID, java.util.Map.of(ICE_APPEARANCE, ICE_POOL));
        // PAR-FARM-16: the fruit tree leaves (TFC's full cube, noOcclusion, tinted like leaves) on the leaves pool.
        for (String tree : FRUIT_TREES) {
            all.put(fruitId(tree, "leaves"), java.util.Map.of(FRUIT_LEAVES_PATTERN, FRUIT_LEAVES_POOL));
        }
        // PAR-FARM-23: the pumpkin, melon and their rotten forms (opaque full cubes) on the solid pool.
        for (String f : FRUIT_BLOCKS) {
            all.put(FRUIT_BLOCK_PREFIX + f, java.util.Map.of("base", CHARCOAL_POOL));
        }
        return java.util.Collections.unmodifiableMap(all);
    }

    /** {@link #POOLS} plus PAR-WORLD-44's twigs (one tripwire pool state shared by every wood). */
    private static java.util.Map<String, java.util.Map<String, String>> pools() {
        java.util.Map<String, java.util.Map<String, String>> m = new java.util.LinkedHashMap<>(basePools());
        for (String w : BARREL_WOODS) {
            m.put(woodPlantId(TWIG, w), java.util.Map.of(WOOD_PLANT_APPEARANCE, TWIG_POOL));
        }
        return java.util.Collections.unmodifiableMap(m);
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

    /** PAR-PROC-49: the sewing table's one appearance and the weight of the tables' demands (served after the loom's). */
    public static final String SEWING_APPEARANCE = "base";
    static final int TABLE_WEIGHT = -23;

    /** PAR-PROC-06: the loom block of {@code wood} and the weight of its demands (served after the sluice's). */
    public static String loomId(String wood) {
        return "tfp:wood/loom/" + wood;
    }

    static final int LOOM_WEIGHT = -18;

    /** PAR-PROC-13: the sluice block of {@code wood}, its appearance names and the weight (served last of all). */
    public static String sluiceId(String wood) {
        return "tfp:wood/sluice/" + wood;
    }

    public static String sluiceAppearance(String facing, boolean upper) {
        return facing + "_" + (upper ? "upper" : "lower");
    }

    static final int SLUICE_WEIGHT = -17;

    /** PAR-PROC-32: the CE block id of the barrel of {@code wood}. */
    public static String barrelId(String wood) {
        return "tfp:wood/barrel/" + wood;
    }
}
