/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.device.automation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.tfp.core.device.ItemBlob;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Golden side tables, transcribed by hand from the TFC block entity constructors at c3b4d9536 (one row per face:
 * insert slots / extract slots, "-" for a face with no handler).
 */
class TfcSideTablesTest {
    private static String row(SideTable t, Face f) {
        return t.get(f).map(p -> p.insert() + "/" + p.extract()).orElse("-");
    }

    private static Map<Face, String> rows(SideTable t) {
        Map<Face, String> m = new EnumMap<>(Face.class);
        for (Face f : Face.values()) {
            m.put(f, row(t, f));
        }
        return m;
    }

    private static Map<Face, String> expect(String down, String up, String north, String south, String west,
            String east) {
        Map<Face, String> m = new EnumMap<>(Face.class);
        m.put(Face.DOWN, down);
        m.put(Face.UP, up);
        m.put(Face.NORTH, north);
        m.put(Face.SOUTH, south);
        m.put(Face.WEST, west);
        m.put(Face.EAST, east);
        return m;
    }

    private static final String CONTAINERS = "[0]/[1]";
    private static final String ITEM_IN = "[2]/[]";
    private static final String ITEM_OUT = "[]/[2]";

    @Test
    void barrelAllSixFacings() {
        // upright: containers on all four sides, item in from the top, out from the bottom
        assertThat(rows(TfcSideTables.barrel(Face.UP)))
            .isEqualTo(expect(ITEM_OUT, ITEM_IN, CONTAINERS, CONTAINERS, CONTAINERS, CONTAINERS));
        // on its side along z: containers on west/east; facing in, opposite out
        assertThat(rows(TfcSideTables.barrel(Face.NORTH)))
            .isEqualTo(expect("-", "-", ITEM_IN, ITEM_OUT, CONTAINERS, CONTAINERS));
        assertThat(rows(TfcSideTables.barrel(Face.SOUTH)))
            .isEqualTo(expect("-", "-", ITEM_OUT, ITEM_IN, CONTAINERS, CONTAINERS));
        assertThat(rows(TfcSideTables.barrel(Face.WEST)))
            .isEqualTo(expect("-", "-", CONTAINERS, CONTAINERS, ITEM_IN, ITEM_OUT));
        assertThat(rows(TfcSideTables.barrel(Face.EAST)))
            .isEqualTo(expect("-", "-", CONTAINERS, CONTAINERS, ITEM_OUT, ITEM_IN));
        // DOWN never occurs in TFC (FACING is up or horizontal); the constructor's arithmetic still applies
        assertThat(rows(TfcSideTables.barrel(Face.DOWN)))
            .isEqualTo(expect(ITEM_IN, ITEM_OUT, CONTAINERS, CONTAINERS, CONTAINERS, CONTAINERS));
    }

    @Test
    void firepitGrillPot() {
        String side = "[3]/[5, 6]";
        assertThat(rows(TfcSideTables.firepit())).isEqualTo(expect("-", "[4]/[]", side, side, side, side));
        String extra = "[3]/[4, 5, 6, 7, 8]";
        String top = "[4, 5, 6, 7, 8]/[]";
        assertThat(rows(TfcSideTables.grill())).isEqualTo(expect("-", top, extra, extra, extra, extra));
        assertThat(rows(TfcSideTables.pot())).isEqualTo(expect("-", top, extra, extra, extra, extra));
    }

    @Test
    void quernComposterPowderkeg() {
        // TFC lists insert(SLOT_INPUT, SLOT_HANDSTONE); a hopper tries them in slot order
        String side = "[0, 1]/[]";
        assertThat(rows(TfcSideTables.quern())).isEqualTo(expect("[]/[2]", "-", side, side, side, side));
        assertThat(rows(TfcSideTables.composter())).isEqualTo(expect("[]/[0]", "-", "-", "-", "-", "-"));
        assertThat(rows(TfcSideTables.powderkeg()))
            .isEqualTo(expect("-", "[0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11]/[]", "-", "-", "-", "-"));
    }

    @Test
    void laterOnReplacesEarlierAsSidedHandler() {
        SideTable t = SideTable.NONE.on(PartialSlots.of(2).insert(0), Face::horizontal)
            .on(PartialSlots.of(2).extract(1), Face.NORTH);
        assertThat(row(t, Face.NORTH)).isEqualTo("[]/[1]");
        assertThat(row(t, Face.SOUTH)).isEqualTo("[0]/[]");
        assertThat(t.insertSlots(Face.UP)).isEmpty();
    }

    @Test
    void partialSlotsRejectsSlotsOutsideTheInventory() {
        assertThatThrownBy(() -> PartialSlots.of(3).insert(3)).isInstanceOf(IllegalArgumentException.class);
        assertThat(PartialSlots.of(3).insert(2, 0, 2).insert()).containsExactly(0, 2);
    }

    @Test
    void faceGeometry() {
        assertThat(Face.of(0, -1, 0)).contains(Face.DOWN);
        assertThat(Face.of(1, 1, 0)).isEmpty();
        assertThat(Face.parse("north")).contains(Face.NORTH);
        assertThat(Face.parse("sideways")).isEmpty();
        for (Face f : Face.values()) {
            assertThat(f.opposite().opposite()).isEqualTo(f);
            assertThat(f.opposite().axis()).isEqualTo(f.axis());
        }
    }

    @Test
    void fuelCascadeMatchesTfc() {
        ItemBlob a = ItemBlob.of(new byte[] {1});
        ItemBlob b = ItemBlob.of(new byte[] {2});
        ItemBlob e = ItemBlob.EMPTY;
        ItemBlob out = ItemBlob.of(new byte[] {9});
        List<ItemBlob> before = List.of(e, a, e, b, out);
        assertThat(AutomationRules.cascadeFuel(before, 0, 3)).containsExactly(a, b, e, e, out);
        assertThat(AutomationRules.cascadeFuel(List.of(a, e, e, e), 0, 3)).containsExactly(a, e, e, e);
        assertThat(AutomationRules.cascadeFuel(List.of(e, e, e, a), 0, 3)).containsExactly(a, e, e, e);
    }

    @Test
    void switchesDefaultToTfc() {
        assertThat(AutomationRules.Switches.TFC_DEFAULTS)
            .isEqualTo(new AutomationRules.Switches(true, true, true, true, true));
        assertThat(AutomationRules.KEYS).hasSize(5).allMatch(k -> k.startsWith("automation."));
    }
}
