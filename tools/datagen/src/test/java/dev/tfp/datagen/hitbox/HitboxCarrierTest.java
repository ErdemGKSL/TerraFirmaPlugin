/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen.hitbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.tfp.datagen.hitbox.HitboxCarrier.Choice;
import dev.tfp.datagen.hitbox.HitboxCarrier.Rejected;
import dev.tfp.datagen.hitbox.HitboxCarrier.TfcState;
import dev.tfp.datagen.visuals.Profile;
import dev.tfp.datagen.visuals.VersionFamily;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * PAR-INFRA-13: the carrier, both IoUs and the mode-relevant score for one representative state of every pinned shape
 * class on every version family, the ground-piece and container rules, the owner overrides and the shape-quality filter.
 */
class HitboxCarrierTest {
    private static final double EPS = 0.001;
    private static final String TRAPDOOR_BOTTOM = "minecraft:iron_trapdoor[facing=east,half=bottom,open=false,powered=false,waterlogged=false]";
    private static final String TRAPDOOR_TOP = "minecraft:iron_trapdoor[facing=east,half=top,open=false,powered=false,waterlogged=false]";
    private static final String PLATE = "minecraft:heavy_weighted_pressure_plate[power=0]";
    private static final String BUTTON = "minecraft:oak_button[face=floor,facing=east,powered=false]";
    private static final String HEAVY_CORE = "minecraft:heavy_core[waterlogged=false]";
    private static final String WALL_POST = "minecraft:cobblestone_wall[east=none,north=none,south=none,up=true,waterlogged=false,west=none]";

    private static Choice select(String key, VersionFamily f) {
        return HitboxCarrier.select(TfcState.of(key), Profile.defaultFor(f), f);
    }

    /** The carrier and IoUs of {@code key} on both families. */
    private static void pin(String key, String carrier, double sel, double col) {
        for (VersionFamily f : VersionFamily.values()) {
            pin(key, f, carrier, sel, col);
        }
    }

    private static void pin(String key, VersionFamily f, String carrier, double sel, double col) {
        Choice c = select(key, f);
        assertThat(c.chosen()).as(key + " on " + f.id()).isNotNull();
        assertThat(c.chosen().carrier()).as(key + " on " + f.id()).isEqualTo(carrier);
        assertThat(c.chosen().selIoU()).as(key + " selection IoU on " + f.id()).isCloseTo(sel, within(EPS));
        assertThat(c.chosen().colIoU()).as(key + " collision IoU on " + f.id()).isCloseTo(col, within(EPS));
    }

    @Test
    void representativeStateOfEveryShapeClass() {
        pin("tfc:rock/raw", "auto_state:solid", 1, 1);
        // PAR-METAL-07's carrier, re-derived: the outline is exact, vanilla snow collides one layer (2 px) lower.
        pin("tfc:charcoal_forge", "minecraft:snow[layers=7]", 1, 12.0 / 14);
        assertThat(select("tfc:charcoal_forge", VersionFamily.V1_21).chosen().hideTarget()).isEqualTo("minecraft:snow[layers=8]");
        for (int n = 2; n <= 7; n++) {
            pin("tfc:charcoal_pile[layers=" + n + "]", "minecraft:snow[layers=" + n + "]", 1, 1);
        }
        // snow[layers=1] is replaceable (any held block predicts placing into it): out of the candidate set.
        pin("tfc:charcoal_pile[layers=1]", PLATE, 196.0 / 512, 1);
        // 8 layers: mud is exact too (16 outline, 14 collision) and needs no hide mapping.
        pin("tfc:charcoal_pile[layers=8]", "minecraft:mud", 1, 1);
        pin("tfc:firepit", "minecraft:daylight_detector[inverted=false,power=0]", 1088.0 / 1536, 1088.0 / 1536);
        pin("tfc:grill", "minecraft:snow[layers=5]", 1664.0 / 2704, 1376.0 / 2480);
        pin("tfc:pot", "minecraft:cactus[age=0]", 2120.0 / 3256, 2120.0 / 3060);
        pin("tfc:farmland", "minecraft:farmland[moisture=0]", 1, 1);
        pin("tfc:rock/spike[part=base]", "minecraft:cactus[age=0]", 2304.0 / 3136, 2160.0 / 3084);
        pin("tfc:rock/spike[part=middle]", WALL_POST, 1, 1024.0 / 1536);
        pin("tfc:rock/spike[part=tip]", "minecraft:lightning_rod[facing=down,powered=false,waterlogged=false]", 1, 1);
        pin("tfc:rock/loose[count=1]", BUTTON, 48.0 / 72, 1);
        pin("tfc:rock/loose[count=2]", PLATE, 144.0 / 340, 1);
        pin("tfc:rock/loose[count=3]", "minecraft:red_mushroom", 144.0 / 216, 1);
        pin("tfc:ore/small", BUTTON, 48.0 / 72, 1);
        pin("tfc:groundcover[shape=small]", BUTTON, 48.0 / 72, 1);
        pin("tfc:groundcover[shape=medium]", "minecraft:red_mushroom", 144.0 / 216, 1);
        pin("tfc:groundcover[shape=flat]", PLATE, 144.0 / 340, 1);
        pin("tfc:groundcover[shape=pixel_high]", VersionFamily.V1_21, PLATE, 196.0 / 256, 1);
        pin("tfc:groundcover[shape=pixel_high]", VersionFamily.V26, "minecraft:leaf_litter[facing=east,segment_amount=3]", 1, 1);
        pin("tfc:placed_item[item_0=true,item_1=false,item_2=false,item_3=false]", PLATE, 49.0 / 211, 1);
        // Residual (ledger): TFC leaves have no collision, no full-outline vanilla state without collision is allowed.
        pin("tfc:wood/leaves", "auto_state:leaves", 1, 0);
        pin("tfc:thatch", "auto_state:leaves", 1, 0);
    }

    @Test
    void groundPiecesFollowTheCountAndNeverCollide() {
        for (VersionFamily f : VersionFamily.values()) {
            double[] h = new double[4];
            for (int n = 1; n <= 3; n++) {
                Choice c = select("tfc:rock/loose[count=" + n + "]", f);
                VanillaCandidates.VState s = VanillaCandidates.load().get(c.chosen().worstVersion(), c.chosen().carrier());
                assertThat(s.collision().isEmpty()).as("count " + n + " has no collision on " + f.id()).isTrue();
                h[n] = s.selection().height();
            }
            // TFC: 2, 2, 4 px. Where TFC grows (count 3), the carrier is at least as tall as for fewer rocks.
            assertThat(h[3]).isGreaterThanOrEqualTo(Math.max(h[1], h[2]));
        }
        for (TfcShapes.Row row : TfcShapes.load().values()) {
            Choice c = HitboxCarrier.select(new TfcState(row, 0, false), Profile.LEVEL_0, VersionFamily.V1_21);
            if (c.chosen() != null) {
                assertThat(c.chosen().block()).as(row.key() + " on 1.21: absent on 1.21.4")
                    .isNotIn("minecraft:wildflowers", "minecraft:leaf_litter");
            }
        }
        // 26.x: a segmentable block is used where it fits best (a 16x1 humus or guano layer on four litter segments).
        Choice humus = select("tfc:groundcover[shape=pixel_high]", VersionFamily.V26);
        assertThat(humus.chosen().selIoU()).isGreaterThan(select("tfc:groundcover[shape=pixel_high]", VersionFamily.V1_21).chosen().selIoU());
    }

    @Test
    void containers() {
        pin("tfc:wood/barrel[axis=y,sealed=true]", "minecraft:cactus[age=0]", 2304.0 / 3136, 2160.0 / 3084);
        // Residual: no vanilla shell fits the unsealed well; below 0.5, so a barrel takes hitbox-only mode.
        Choice open = select("tfc:wood/barrel[axis=y,sealed=false]", VersionFamily.V1_21);
        assertThat(open.chosen().carrier()).isEqualTo("minecraft:cactus[age=0]");
        assertThat(open.chosen().score()).isLessThan(HitboxCarrier.MIN_SCORE);
        pin("tfc:wood/barrel[axis=x,rack=false]", "minecraft:snow[layers=6]", 0.75, 2304.0 / 3536);
        pin("tfc:wood/barrel[axis=z,rack=false]", "minecraft:snow[layers=6]", 0.75, 2304.0 / 3536);
        pin("tfc:wood/barrel[axis=x,rack=true]", "auto_state:leaves", 3040.0 / 4096, 3040.0 / 4096);
        pin("tfc:barrel_rack", TRAPDOOR_TOP, 0.541, 0.541);
        pin("tfc:powderkeg[sealed=true]", "minecraft:cactus[age=0]", 1, 2940.0 / 3136);
        // The open keg's well: a composter is the one vanilla hollow box (its well is walkable like TFC's).
        pin("tfc:powderkeg[sealed=false]", "minecraft:composter[level=0]", 0.469, 0.469);
        pin("tfc:nest_box", "minecraft:daylight_detector[inverted=false,power=0]", 720.0 / 1536, 720.0 / 1536);
        pin("tfc:ceramic/bowl", TRAPDOOR_BOTTOM, 432.0 / 768, 432.0 / 768);
        pin("tfc:crucible", "minecraft:cactus[age=0]", 0.523, 0.509);
        pin("tfc:wood/shelf[thick=false]", TRAPDOOR_TOP, 1.0 / 3, 1.0 / 3);
        pin("tfc:wood/shelf[thick=true]", TRAPDOOR_TOP, 2.0 / 3, 1.0 / 3);
        for (String f : List.of("north", "east", "south", "west")) {
            pin("tfc:wood/tool_rack[facing=" + f + "]",
                "minecraft:iron_trapdoor[facing=" + f + ",half=bottom,open=true,powered=false,waterlogged=false]", 2.0 / 3, 2.0 / 3);
        }
        pin("tfc:lobster_trap", "auto_state:leaves", 1, 1);
        Set<String> clientRenderers = Set.of("minecraft:chest", "minecraft:barrel", "minecraft:decorated_pot", "minecraft:ender_chest");
        for (TfcShapes.Row row : TfcShapes.load().values()) {
            for (VersionFamily f : VersionFamily.values()) {
                Choice c = HitboxCarrier.select(new TfcState(row, 0, false), Profile.LEVEL_0, f);
                if (c.chosen() == null) {
                    continue;
                }
                assertThat(c.chosen().block()).as(row.key()).isNotIn(clientRenderers);
                VanillaCandidates.VState s = VanillaCandidates.load().get(c.chosen().worstVersion(), c.chosen().carrier());
                if (row.collision().isEmpty() && row.kind() != TfcShapes.Kind.CUBE) {
                    assertThat(s.collision().isEmpty()).as(row.key() + " has no collision in TFC").isTrue();
                }
            }
        }
    }

    @Test
    void everyPlacedItemStateTakesThePressurePlate() {
        int states = 0;
        for (TfcShapes.Row row : TfcShapes.load().values()) {
            if (!row.shapeClass().equals("placed_item")) {
                continue;
            }
            states++;
            for (VersionFamily f : VersionFamily.values()) {
                Choice c = HitboxCarrier.select(new TfcState(row, 0, false), Profile.defaultFor(f), f);
                assertThat(c.chosen().carrier()).as(row.key() + " on " + f.id()).isEqualTo(PLATE);
                assertThat(c.override()).isNotNull();
                assertThat(c.overrideIndex()).isZero();
                assertThat(c.chosen().colIoU()).isEqualTo(1);
            }
        }
        assertThat(states).as("ITEM_0..3: 16 states, the full one also holds a large item").isEqualTo(16);
        pin("tfc:placed_item[item_0=true,item_1=true,item_2=false,item_3=false]", PLATE, 98.0 / 226, 1);
        pin("tfc:placed_item[item_0=true,item_1=true,item_2=true,item_3=false]", PLATE, 147.0 / 241, 1);
        pin("tfc:placed_item[item_0=true,item_1=true,item_2=true,item_3=true]", PLATE, 196.0 / 256, 1);
    }

    @Test
    void largeVesselTakesTheOwnerOverrideOverAHigherIouBannedCandidate() {
        for (String key : List.of("tfc:ceramic/large_vessel[sealed=false]", "tfc:ceramic/large_vessel[sealed=true]")) {
            for (VersionFamily f : VersionFamily.values()) {
                Choice c = select(key, f);
                assertThat(c.chosen().carrier()).as(key + " on " + f.id()).isEqualTo(HEAVY_CORE);
                assertThat(c.overrideIndex()).isZero();
                assertThat(VanillaCandidates.load().inFamily(HEAVY_CORE, f)).isTrue();
                Rejected pickle = rejected(c, "minecraft:sea_pickle[pickles=4,waterlogged=false]");
                assertThat(pickle.reason()).startsWith("object ban");
                Shape tfc = TfcShapes.load().get(key).selection();
                double pickleIou = Shape.iou(tfc, VanillaCandidates.load().get("1.21.11", pickle.carrier()).selection());
                assertThat(pickleIou).as("sea_pickle[4] ranks higher by IoU").isGreaterThan(c.chosen().selIoU());
            }
        }
        assertThat(select("tfc:ceramic/large_vessel[sealed=false]", VersionFamily.V1_21).chosen().selIoU()).isCloseTo(0.512, within(EPS));
        assertThat(select("tfc:ceramic/large_vessel[sealed=true]", VersionFamily.V1_21).chosen().selIoU()).isCloseTo(0.451, within(EPS));
    }

    @Test
    void anUnusableOverrideFallsBackToTheNextEntryWhichNeedsALedgerRow() throws Exception {
        HitboxCarrier.Context ctx = HitboxCarrier.standardContext().withUnusable(List.of(new HitboxCarrier.Unusable(
            "large_vessel", HEAVY_CORE, "1.21", "heavy_core texture shows", "2026-10-22", "docs/verification/PAR-INFRA-13/")));
        Choice c = HitboxCarrier.select(TfcState.of("tfc:ceramic/large_vessel[sealed=false]"), Profile.LEVEL_0,
            VersionFamily.V1_21, ctx, s -> true);
        assertThat(c.chosen().carrier()).isEqualTo(WALL_POST);
        assertThat(c.overrideIndex()).isEqualTo(1);
        Choice other = HitboxCarrier.select(TfcState.of("tfc:ceramic/large_vessel[sealed=false]"), Profile.LEVEL_0,
            VersionFamily.V26, ctx, s -> true);
        assertThat(other.chosen().carrier()).as("the row names one family").isEqualTo(HEAVY_CORE);
        assertThat(ledger()).as("every override fallback has a ledger Divergences row").contains("cobblestone_wall")
            .contains("large vessel");
    }

    @Test
    void bannedOrScatteredCandidatesAreRejected() {
        // The nest box: sea_pickle[3] fits better (0.83) but reads as three pickles (owner ban).
        Choice nest = select("tfc:nest_box", VersionFamily.V1_21);
        Rejected pickle = rejected(nest, "minecraft:sea_pickle[pickles=3,waterlogged=false]");
        assertThat(pickle.reason()).startsWith("object ban");
        assertThat(Shape.iou(TfcShapes.load().get("tfc:nest_box").selection(),
            VanillaCandidates.load().get("1.21.11", pickle.carrier()).selection())).isCloseTo(0.833, within(EPS));
        assertThat(nest.chosen().carrier()).isEqualTo("minecraft:daylight_detector[inverted=false,power=0]");
        // A lit campfire and the cake are never carriers.
        assertThat(rejected(select("tfc:firepit", VersionFamily.V1_21),
            "minecraft:campfire[facing=north,lit=true,signal_fire=false,waterlogged=false]").reason()).contains("particles");
        assertThat(rejected(select("tfc:nest_box", VersionFamily.V1_21), "minecraft:cake[bites=0]").reason()).contains("eating");

        Shape oneBox = Shape.box(2, 0, 2, 14, 12, 14);
        Shape twoPosts = Shape.box(2, 0, 2, 6, 8, 6).union(Shape.box(10, 0, 2, 14, 8, 6));
        assertThat(HitboxCarrier.geometricRejection(oneBox, twoPosts)).startsWith("components");
        // Against TFC's own two-part shape (DryingBricksBlock SHAPE_2) aligned posts are accepted.
        Shape bricks = TfcShapes.load().get("tfc:drying_bricks[count=2]").selection();
        assertThat(bricks.components()).hasSize(2);
        Shape aligned = Shape.box(2, 0, 1, 6, 4, 7).union(Shape.box(10, 0, 1, 14, 4, 7));
        assertThat(HitboxCarrier.geometricRejection(bricks, aligned)).isNull();
        Shape threePosts = twoPosts.union(Shape.box(6, 0, 10, 10, 8, 14));
        assertThat(HitboxCarrier.geometricRejection(bricks, threePosts)).as("more parts than TFC's, not on TFC's parts")
            .startsWith("components");
        // Off-centre: an open trapdoor on one side against a centred box.
        Shape trapdoor = VanillaCandidates.load().get("1.21.11",
            "minecraft:iron_trapdoor[facing=north,half=bottom,open=true,powered=false,waterlogged=false]").selection();
        assertThat(HitboxCarrier.geometricRejection(oneBox, trapdoor)).startsWith("centroid");
        // A random-offset state never carries a hitbox (the outline would move per position).
        assertThat(rejected(select("tfc:rock/spike[part=base]", VersionFamily.V1_21),
            "minecraft:pointed_dripstone[thickness=base,vertical_direction=up,waterlogged=false]").reason()).startsWith("random offset");
    }

    @Test
    void scoreFormula() {
        Choice forge = select("tfc:charcoal_forge", VersionFamily.V1_21);
        // (0.6 x 1 + 0.4 x 12/14) x quality 1 x hide 0.97
        assertThat(forge.chosen().score()).isCloseTo((0.6 + 0.4 * 12 / 14.0) * HitboxCarrier.HIDE_PENALTY, within(1e-9));
        assertThat(Shape.iou(Shape.EMPTY, Shape.EMPTY)).isEqualTo(1);
        assertThat(Shape.iou(Shape.EMPTY, Shape.FULL)).isZero();
        assertThat(HitboxCarrier.quality(Shape.FULL, Shape.box(0, 0, 0, 16, 8, 16))).isCloseTo(1 - 4 / 16.0, within(1e-9));
    }

    private static Rejected rejected(Choice c, String state) {
        return c.rejected().stream().filter(r -> r.carrier().equals(state)).findFirst()
            .orElseThrow(() -> new AssertionError(state + " was not rejected"));
    }

    static String ledger() throws Exception {
        Path root = Path.of(System.getProperty("tfp.repoRoot", "../.."));
        return Files.readString(root.resolve("docs/porting/ledger.md"));
    }
}
