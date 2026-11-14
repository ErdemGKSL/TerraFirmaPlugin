/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/TFCBlocks.java @ c3b4d9536 (litBlockEmission(15) of the firepit family)
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

    /** PAR-PROC-34: the composter block, its stage-0 appearance and the compost types of TFC's {@code CompostType}. */
    public static final String COMPOSTER_ID = "tfp:composter";
    public static final String COMPOSTER_EMPTY = "empty";
    public static final List<String> COMPOSTER_TYPES = List.of("normal", "ready", "rotten");
    /** PAR-PROC-34: TFC's bin model ({@code tfc:block/composter/composter}), drawn on every stage's carrier. */
    public static final String COMPOSTER_MODEL = "tfp:block/composter/composter";

    private HitboxDemands() {
    }

    public static List<Demand> all() {
        List<Demand> d = new ArrayList<>();
        // PAR-METAL-07: every heat level on one carrier with the cold model; the lit levels are display overlays.
        d.add(new Demand("tfp:charcoal_forge", FORGE_APPEARANCE, "tfc:charcoal_forge", 0, FORGE_MODEL, true, 1));
        for (String f : FACINGS) {
            d.add(new Demand("tfp:bellows", f, "tfc:bellows[facing=" + f + "]", 0, HitboxAllocation.VANILLA_LOOK, true, 1));
        }
        // M3-HEAT-03 placeholders: the carrier's vanilla look until the firepit family's models exist.
        for (String device : List.of("firepit", "grill", "pot")) {
            d.add(new Demand("tfp:" + device, "lit", "tfc:" + device, FIREPIT_LIT_LIGHT, HitboxAllocation.VANILLA_LOOK, true, 1));
            d.add(new Demand("tfp:" + device, "unlit", "tfc:" + device, 0, HitboxAllocation.VANILLA_LOOK, true, 1));
        }
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
        // PAR-PROC-34: the composter, one outline per stage (TFC's well from max(2, stage * 2)). Every stage carries TFC's
        // bin model (one look, so stages may share a carrier); the compost of each type is an entity_renderer overlay
        // (ComposterBlock), so the 25 looks of TFC's multipart take 8 carriers at most. Stage 8 is a full cube and is no
        // demand here: it is drawn from CE's solid pool (COMPOSTER_POOL_STAGE).
        // Weight 0: served after every earlier demand, so adding the composter moves no carrier of another block.
        for (int stage = 0; stage < COMPOSTER_POOL_STAGE; stage++) {
            d.add(new Demand(COMPOSTER_ID, composterDemand(stage), composterKey(stage), 0, COMPOSTER_MODEL, true, 0));
        }
        return d;
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

    public static String composterKey(int stage) {
        return "tfc:composter[stage=" + stage + "]";
    }

    /** PAR-PROC-32: the CE block id of the barrel of {@code wood}. */
    public static String barrelId(String wood) {
        return "tfp:wood/barrel/" + wood;
    }
}
