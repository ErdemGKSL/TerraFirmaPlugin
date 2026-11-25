/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.chest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.tfp.core.chest.ChestRules.Chest;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** PAR-DECO-04: vanilla's chest pairing rules, which TFC's chests inherit unchanged. */
class ChestRulesTest {
    private static java.util.function.Function<ChestSide, Chest> around(Map<ChestSide, Chest> m) {
        return m::get;
    }

    @Test
    void quarterTurnsAreClockwiseFromAbove() {
        assertThat(ChestSide.NORTH.clockwise()).isEqualTo(ChestSide.EAST);
        assertThat(ChestSide.SOUTH.clockwise()).isEqualTo(ChestSide.WEST);
        assertThat(ChestSide.SOUTH.counterClockwise()).isEqualTo(ChestSide.EAST);
        assertThat(ChestSide.WEST.opposite()).isEqualTo(ChestSide.EAST);
        assertThat(ChestSide.NORTH.sameAxis(ChestSide.SOUTH)).isTrue();
        assertThat(ChestSide.NORTH.sameAxis(ChestSide.EAST)).isFalse();
    }

    @Test
    void leftChestsPartnerIsOnTheClockwiseSide() {
        assertThat(ChestRules.partnerSide(ChestSide.SOUTH, ChestType.LEFT)).isEqualTo(ChestSide.WEST);
        assertThat(ChestRules.partnerSide(ChestSide.SOUTH, ChestType.RIGHT)).isEqualTo(ChestSide.EAST);
        assertThatThrownBy(() -> ChestRules.partnerSide(ChestSide.SOUTH, ChestType.SINGLE)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aLoneChestFacesThePlayerAndIsSingle() {
        Chest c = ChestRules.placement(ChestSide.NORTH, null, false, around(Map.of()));
        assertThat(c).isEqualTo(new Chest(ChestSide.SOUTH, ChestType.SINGLE));
    }

    @Test
    void placingBesideASingleChestMakesADoubleChest() {
        // a chest faces south; the new one goes to its east side, the player looks north (the chest faces back: south)
        Chest partner = new Chest(ChestSide.SOUTH, ChestType.SINGLE);
        // the partner is on the new chest's west side = south.clockwise: the new chest is LEFT
        Chest left = ChestRules.placement(ChestSide.NORTH, null, false, around(Map.of(ChestSide.WEST, partner)));
        assertThat(left).isEqualTo(new Chest(ChestSide.SOUTH, ChestType.LEFT));
        Chest right = ChestRules.placement(ChestSide.NORTH, null, false, around(Map.of(ChestSide.EAST, partner)));
        assertThat(right).isEqualTo(new Chest(ChestSide.SOUTH, ChestType.RIGHT));
    }

    @Test
    void aChestWithAnotherFacingOrAnAlreadyDoubleChestIsNoPartner() {
        Chest other = new Chest(ChestSide.EAST, ChestType.SINGLE);
        assertThat(ChestRules.placement(ChestSide.NORTH, null, false, around(Map.of(ChestSide.WEST, other))).type())
            .isEqualTo(ChestType.SINGLE);
        Chest half = new Chest(ChestSide.SOUTH, ChestType.LEFT);
        assertThat(ChestRules.placement(ChestSide.NORTH, null, false, around(Map.of(ChestSide.WEST, half))).type())
            .isEqualTo(ChestType.SINGLE);
    }

    @Test
    void sneakingAgainstAChestsSideJoinsItWhateverWayThePlayerLooks() {
        // the partner lies to the west and faces north; the player sneaks on its east face (clicked face EAST), looking south
        Chest partner = new Chest(ChestSide.NORTH, ChestType.SINGLE);
        Chest c = ChestRules.placement(ChestSide.SOUTH, ChestSide.EAST, true, around(Map.of(ChestSide.WEST, partner)));
        assertThat(c.facing()).isEqualTo(ChestSide.NORTH);
        // facing north: ccw = west = clickedFace.opposite() -> right
        assertThat(c.type()).isEqualTo(ChestType.RIGHT);
        // the same neighbour without sneaking: facing south, no partner facing south: single
        assertThat(ChestRules.placement(ChestSide.NORTH, ChestSide.EAST, false, around(Map.of(ChestSide.WEST, partner))).type())
            .isEqualTo(ChestType.SINGLE);
        // sneaking onto the front or back (same axis as the partner's facing) does not join
        Chest front = ChestRules.placement(ChestSide.SOUTH, ChestSide.NORTH, true, around(Map.of(ChestSide.SOUTH, partner)));
        assertThat(front.type()).isEqualTo(ChestType.SINGLE);
        assertThat(front.facing()).isEqualTo(ChestSide.NORTH); // faces back at the player looking south
    }

    @Test
    void theOtherHalfFollowsWhenAChestJoinsAndLeavesWhenItGoes() {
        Chest self = new Chest(ChestSide.SOUTH, ChestType.SINGLE);
        // the new chest on its west side is a LEFT chest; its partner direction is clockwise = west... seen from self, the
        // new chest lies on the side self.partner: the neighbour's partnerSide is the opposite of the side it lies on
        Chest newLeft = new Chest(ChestSide.SOUTH, ChestType.RIGHT); // lies on self's east; its partner (ccw = east) ... 
        assertThat(ChestRules.partnerSide(newLeft.facing(), newLeft.type())).isEqualTo(ChestSide.EAST);
        // self is west of newLeft's partner side? self must lie on newLeft's EAST side: the update came from self's west side
        assertThat(ChestRules.afterNeighbourChange(self, ChestSide.WEST, newLeft)).isEqualTo(ChestType.LEFT);
        // a neighbour that is not paired with self changes nothing
        assertThat(ChestRules.afterNeighbourChange(self, ChestSide.NORTH, newLeft)).isEqualTo(ChestType.SINGLE);
        // the partner is gone: the half becomes single
        Chest half = new Chest(ChestSide.SOUTH, ChestType.LEFT);
        assertThat(ChestRules.afterNeighbourChange(half, ChestSide.WEST, null)).isEqualTo(ChestType.SINGLE);
        assertThat(ChestRules.afterNeighbourChange(half, ChestSide.EAST, null)).isEqualTo(ChestType.LEFT);
    }

    @Test
    void doubleChestHasTheRightHalfFirst() {
        assertThat(ChestRules.isFirst(ChestType.RIGHT)).isTrue();
        assertThat(ChestRules.isFirst(ChestType.LEFT)).isFalse();
        assertThat(ChestRules.DOUBLE_SLOTS).isEqualTo(36);
        assertThat(ChestType.of("left").opposite()).isEqualTo(ChestType.RIGHT);
        assertThat(ChestSide.of("west").id()).isEqualTo("west");
    }
}
