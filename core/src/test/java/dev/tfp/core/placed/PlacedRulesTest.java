/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.placed;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.item.ContainerRules.PlacedSlot;
import dev.tfp.core.persist.DataMap;
import java.util.List;
import org.junit.jupiter.api.Test;

/** PAR-POT-01: TFC's slot selection, rotation, insert and take-back rules, quadrant support and the display poses. */
class PlacedRulesTest {
    private static final boolean[] NONE = {false, false, false, false};
    private static final boolean[] ALL = {true, true, true, true};

    @Test
    void slotFromTheHitPositionAsTfc() {
        // TFC getSlotSelected: (round(x) < x ? 1 : 0) + (round(z) < z ? 2 : 0); low halves are true
        assertThat(PlacedRules.slotSelected(10.75, 4.75)).as("+x +z quadrant").isZero();
        assertThat(PlacedRules.slotSelected(10.25, 4.75)).as("-x +z").isEqualTo(1);
        assertThat(PlacedRules.slotSelected(10.75, 4.25)).as("+x -z").isEqualTo(2);
        assertThat(PlacedRules.slotSelected(10.25, 4.25)).as("-x -z (the NW corner)").isEqualTo(3);
        // negative coordinates: block -2 spans [-2, -1); -1.7 is its low half, -1.3 its high half
        assertThat(PlacedRules.slotSelected(-1.7, -1.7)).isEqualTo(3);
        assertThat(PlacedRules.slotSelected(-1.3, -1.3)).isZero();
        // the exact middle and the block edge belong to the high side (Math.round rounds half up)
        assertThat(PlacedRules.slotSelected(10.5, 4.5)).isZero();
        assertThat(PlacedRules.slotSelected(-1.5, -1.5)).isZero();
        assertThat(PlacedRules.slotSelected(10.0, 4.0)).isZero();
    }

    @Test
    void rotationIsTheYawIn15DegreeSteps() {
        assertThat(PlacedRules.rotation(0f)).isZero();
        assertThat(PlacedRules.rotation(37f)).isEqualTo(30f);
        assertThat(PlacedRules.rotation(37.5f)).isEqualTo(45f);
        assertThat(PlacedRules.rotation(-7.4f)).isZero();
        assertThat(PlacedRules.rotation(-8f)).isEqualTo(-15f);
        assertThat(PlacedRules.rotation(172.5f)).isEqualTo(180f);
        assertThat(PlacedRules.rotation(-179f)).isEqualTo(-180f);
    }

    @Test
    void aSmallItemTakesAnEmptyQuadrant() {
        PlacedRules.Insert in = PlacedRules.insert(PlacedState.EMPTY, NONE, 3, PlacedSlot.GROUP, true, 98f).orElseThrow();
        assertThat(in.slot()).isEqualTo(3);
        assertThat(in.state().rotation(3)).isEqualTo(105f);
        assertThat(in.state().large()).isFalse();
        assertThat(PlacedRules.insert(in.state(), new boolean[] {false, false, false, true}, 3, PlacedSlot.GROUP, true, 0f))
            .as("occupied").isEmpty();
        assertThat(PlacedRules.insert(in.state(), new boolean[] {false, false, false, true}, 0, PlacedSlot.GROUP, false, 0f))
            .as("a small item needs no full support").isPresent();
    }

    @Test
    void aLargeItemNeedsAnEmptyFullySupportedBlock() {
        PlacedRules.Insert in = PlacedRules.insert(PlacedState.EMPTY, NONE, 2, PlacedSlot.SINGLE, true, -90f).orElseThrow();
        assertThat(in.slot()).isEqualTo(PlacedRules.SLOT_LARGE_ITEM);
        assertThat(in.state().large()).isTrue();
        assertThat(in.state().rotation(0)).isEqualTo(-90f);
        assertThat(PlacedRules.insert(PlacedState.EMPTY, NONE, 2, PlacedSlot.SINGLE, false, 0f)).as("partly supported")
            .isEmpty();
        assertThat(PlacedRules.insert(PlacedState.EMPTY, new boolean[] {false, true, false, false}, 2, PlacedSlot.SINGLE,
            true, 0f)).as("not empty").isEmpty();
        // a small item cannot join a large one
        assertThat(PlacedRules.insert(in.state(), new boolean[] {true, false, false, false}, 3, PlacedSlot.GROUP, true, 0f))
            .isEmpty();
    }

    @Test
    void tooLargeItemsAreRefused() {
        assertThat(PlacedRules.insert(PlacedState.EMPTY, NONE, 0, PlacedSlot.NONE, true, 0f)).isEmpty();
    }

    @Test
    void takeBackReturnsTheTargetedSlotOrTheLargeItem() {
        boolean[] occ = {false, true, false, false};
        assertThat(PlacedRules.take(PlacedState.EMPTY, occ, 1).orElseThrow().slot()).isEqualTo(1);
        assertThat(PlacedRules.take(PlacedState.EMPTY, occ, 2)).isEmpty();
        PlacedState large = PlacedState.EMPTY.withLarge(true);
        PlacedRules.Take t = PlacedRules.take(large, new boolean[] {true, false, false, false}, 3).orElseThrow();
        assertThat(t.slot()).isZero();
        assertThat(t.state().large()).isFalse();
        assertThat(PlacedRules.takes(true, false)).isTrue();
        assertThat(PlacedRules.takes(false, true)).isTrue();
        assertThat(PlacedRules.takes(false, false)).isFalse();
    }

    @Test
    void emptinessAsTfc() {
        assertThat(PlacedRules.isEmpty(PlacedState.EMPTY, NONE)).isTrue();
        assertThat(PlacedRules.isEmpty(PlacedState.EMPTY, new boolean[] {false, false, true, false})).isFalse();
        assertThat(PlacedRules.isEmpty(PlacedState.EMPTY.withLarge(true), new boolean[] {false, false, true, false}))
            .as("TFC: a large block with slot 0 empty is empty").isTrue();
        assertThat(PlacedRules.isFullContents(ALL)).isTrue();
        assertThat(PlacedRules.isFullContents(new boolean[] {true, true, false, true})).isFalse();
        assertThat(PlacedRules.drawn(PlacedState.EMPTY.withLarge(true), ALL)).containsExactly(true, false, false, false);
        assertThat(PlacedRules.drawn(PlacedState.EMPTY, new boolean[] {false, true, true, false}))
            .containsExactly(false, true, true, false);
    }

    @Test
    void stateRoundTrips() {
        PlacedState s = new PlacedState(true, 15f, -30f, 45f, 180f);
        assertThat(PlacedState.CODEC.decode(PlacedState.CODEC.encode(s))).isEqualTo(s);
        DataMap minimal = PlacedState.CODEC.encode(PlacedState.EMPTY);
        assertThat(PlacedState.CODEC.decode(minimal)).isEqualTo(PlacedState.EMPTY);
    }

    @Test
    void quadrantSupportFromTheUpFaceBelow() {
        QuadrantSupport.Box cube = new QuadrantSupport.Box(0, 0, 0, 1, 1, 1);
        assertThat(QuadrantSupport.supported(List.of(cube))).containsExactly(true, true, true, true);
        assertThat(QuadrantSupport.supported(List.of())).containsExactly(false, false, false, false);
        QuadrantSupport.Box bottomSlab = new QuadrantSupport.Box(0, 0, 0, 1, 0.5, 1);
        assertThat(QuadrantSupport.supported(List.of(bottomSlab))).as("the face is below the top")
            .containsExactly(false, false, false, false);
        // bottom stairs facing east: the full lower half and the upper half on +x
        QuadrantSupport.Box upperEast = new QuadrantSupport.Box(0.5, 0.5, 0, 1, 1, 1);
        assertThat(QuadrantSupport.supported(List.of(bottomSlab, upperEast))).containsExactly(true, false, true, false);
        // a 14 x 14 centre (a heavy pressure plate is lower, a chest is 14 px): no quadrant is covered
        QuadrantSupport.Box inset = new QuadrantSupport.Box(1 / 16.0, 0, 1 / 16.0, 15 / 16.0, 1, 15 / 16.0);
        assertThat(QuadrantSupport.supported(List.of(inset))).containsExactly(false, false, false, false);
        // two halves together cover everything
        assertThat(QuadrantSupport.supported(List.of(new QuadrantSupport.Box(0, 0, 0, 0.5, 1, 1),
            new QuadrantSupport.Box(0.5, 0, 0, 1, 1, 1)))).containsExactly(true, true, true, true);
    }

    @Test
    void displayPosesFollowTfcsRenderer() {
        PlacedVisuals.Pose p = PlacedVisuals.pose(3, false, 0f, false, 0, 0);
        assertThat(p.tx()).isEqualTo(0.25);
        assertThat(p.tz()).isEqualTo(0.25);
        assertThat(p.ty()).isCloseTo(0.03125 - 0.00015, org.assertj.core.data.Offset.offset(1e-7));
        assertThat(p.scale()).isEqualTo(0.5f);
        PlacedVisuals.Pose q = PlacedVisuals.pose(0, false, 0f, true, 0, 0);
        assertThat(q.tx()).isEqualTo(0.75);
        assertThat(q.tz()).isEqualTo(0.75);
        assertThat(q.ty()).isEqualTo(0.125);
        PlacedVisuals.Pose large = PlacedVisuals.pose(0, true, 0f, true, 2, 3);
        assertThat(large.tx()).isEqualTo(0.5);
        assertThat(large.ty()).isCloseTo(0.249, org.assertj.core.data.Offset.offset(1e-6));
        assertThat(large.scale()).isEqualTo(1f);
        // a flat item at rotation 0: the pose followed by the renderer's own half turn is TFC's X 90 lay-down
        double[] seen = PlacedVisuals.mul(p.rotation(), PlacedVisuals.axis(0, 1, 0, 180));
        double[] tfc = PlacedVisuals.axis(1, 0, 0, 90);
        assertThat(Math.abs(dot(seen, tfc))).isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-9));
        // a block item at 45 degrees: Y 45 after the half turn
        double[] seenBlock = PlacedVisuals.mul(PlacedVisuals.pose(1, false, 45f, true, 0, 0).rotation(),
            PlacedVisuals.axis(0, 1, 0, 180));
        assertThat(Math.abs(dot(seenBlock, PlacedVisuals.axis(0, 1, 0, 45)))).isCloseTo(1.0,
            org.assertj.core.data.Offset.offset(1e-9));
    }

    private static double dot(double[] a, double[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2] + a[3] * b[3];
    }

    @Test
    void policyIsActionFirst() {
        assertThat(PlacePolicy.decide(new PlacePolicy.Item(true, false, false, false, false))).isEqualTo(PlacePolicy.Use.ACTION);
        assertThat(PlacePolicy.decide(new PlacePolicy.Item(false, true, false, false, false))).isEqualTo(PlacePolicy.Use.ACTION);
        assertThat(PlacePolicy.decide(new PlacePolicy.Item(false, false, true, false, false))).isEqualTo(PlacePolicy.Use.ACTION);
        assertThat(PlacePolicy.decide(new PlacePolicy.Item(false, false, false, false, true))).isEqualTo(PlacePolicy.Use.ACTION);
        assertThat(PlacePolicy.decide(new PlacePolicy.Item(false, false, false, true, false))).isEqualTo(PlacePolicy.Use.EXEMPT);
        assertThat(PlacePolicy.decide(new PlacePolicy.Item(false, false, false, false, false))).isEqualTo(PlacePolicy.Use.HOLD);
    }

    @Test
    void gesturesAndTheDoubleTap() {
        assertThat(Gesture.byId("sneak-double-tap")).contains(Gesture.SNEAK_DOUBLE_TAP);
        assertThat(Gesture.byId(" HOLD ")).contains(Gesture.HOLD);
        assertThat(Gesture.byId("both")).contains(Gesture.BOTH);
        assertThat(Gesture.byId("v-key")).isEmpty();
        assertThat(Gesture.HOLD.doubleTap()).isFalse();
        assertThat(Gesture.SNEAK_DOUBLE_TAP.hold()).isFalse();
        assertThat(Gesture.BOTH.hold() && Gesture.BOTH.doubleTap()).isTrue();

        java.util.concurrent.atomic.AtomicLong clock = new java.util.concurrent.atomic.AtomicLong(0);
        SneakDoubleTap tap = new SneakDoubleTap(clock::get);
        assertThat(tap.sneakStarted()).isFalse();
        clock.set(500);
        assertThat(tap.sneakStarted()).as("500 ms apart").isFalse();
        clock.set(800);
        assertThat(tap.sneakStarted()).as("300 ms apart").isTrue();
        assertThat(tap.armed()).isTrue();
        assertThat(tap.consume()).isTrue();
        assertThat(tap.armed()).isFalse();
        clock.set(5000);
        tap.sneakStarted();
        clock.set(5100);
        tap.sneakStarted();
        clock.set(8200);
        assertThat(tap.armed()).as("armed state expired").isFalse();
    }
}
