/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.gui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** PAR-INFRA-03: chest geometry, glyph metrics, title building, layers and slot layouts. */
class GuiGeometryTest {
    @Test
    void chestScreensMatchTheVanillaLayout() {
        ChestGeometry six = new ChestGeometry(6);
        assertThat(six.height()).isEqualTo(222);
        assertThat(six.playerRowY(0)).isEqualTo(139);
        assertThat(six.hotbarY()).isEqualTo(197);
        assertThat(six.inventoryLabelY()).isEqualTo(128);
        ChestGeometry three = new ChestGeometry(3);
        assertThat(three.height()).isEqualTo(168);
        assertThat(three.playerRowY(0)).isEqualTo(85);
        assertThat(three.playerRowY(2)).isEqualTo(121);
        assertThat(three.hotbarY()).isEqualTo(143);
        assertThat(three.size()).isEqualTo(27);
        for (int i = 0; i < three.size(); i++) {
            assertThat(three.slotAt(three.slotX(i), three.slotY(i))).isEqualTo(i);
        }
        assertThat(three.slotX(0)).isEqualTo(8);
        assertThat(three.slotY(0)).isEqualTo(18);
        assertThat(three.slotX(26)).isEqualTo(8 + 18 * 8);
        assertThat(three.slotY(26)).isEqualTo(18 + 36);
        assertThat(three.slotAt(9, 18)).isEqualTo(-1);
        assertThat(three.slotAt(8, 72)).as("row 3 is the player inventory").isEqualTo(-1);
        assertThatThrownBy(() -> new ChestGeometry(7)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> three.slotX(27)).isInstanceOf(IndexOutOfBoundsException.class);
    }

    @Test
    void glyphMetricsMatchCraftEnginesMeasuredItemBrowser() {
        // CE's item_browser.png: ascent 18 and a -11 shift put its PNG origin at window (-3, -5)
        assertThat(Glyphs.top(18)).isEqualTo(-5);
        assertThat(ChestGeometry.TITLE_X - 11).isEqualTo(-3);
        assertThat(Glyphs.ascent(0)).isEqualTo(13);
        assertThat(Glyphs.advance(176)).isEqualTo(177);
        for (int y = -40; y < 300; y++) {
            assertThat(Glyphs.top(Glyphs.ascent(y))).isEqualTo(y);
        }
    }

    @Test
    void aFullSixRowBackgroundIsTheSkillsWorkedExample() {
        GuiImage bg = GuiImage.of("tfp:gui/anvil", 0, 0, 176, 222);
        assertThat(bg.ascent()).isEqualTo(13);
        TitleGlyphs t = TitleGlyphs.builder().draw(bg, 0).returnToLabel().build();
        assertThat(t.tags()).isEqualTo("<shift:-8><image:tfp:gui/anvil><shift:-169>");
        assertThat(t.pen()).isEqualTo(ChestGeometry.TITLE_X);
    }

    @Test
    void imagesThatCraftEngineWouldRefuseAreRejected() {
        assertThatThrownBy(() -> GuiImage.of("tfp:gui/x", 0, -10, 16, 16))
            .hasMessageContaining("ascent 23 > height 16");
        assertThatThrownBy(() -> GuiImage.of("tfp:gui/x", 0, 0, 16, 257)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> GuiImage.of("minecraft:x", 0, 0, 16, 16)).isInstanceOf(IllegalArgumentException.class);
        GuiImage grid = new GuiImage("tfp:gui/g", 0, 20, 10, 10, 2, 3);
        assertThat(grid.frames()).isEqualTo(6);
        assertThat(grid.frameRow(4)).isEqualTo(1);
        assertThat(grid.frameCol(4)).isEqualTo(1);
        assertThat(grid.pngWidth()).isEqualTo(30);
        assertThat(grid.pngHeight()).isEqualTo(20);
        assertThat(grid.file()).isEqualTo("tfp:font/gui/g.png");
        assertThatThrownBy(() -> grid.frameRow(6)).isInstanceOf(IndexOutOfBoundsException.class);
    }

    @Test
    void shiftsMergeAndSplitIntoPreRegisteredTags() {
        TitleGlyphs t = TitleGlyphs.builder().shift(200).shift(200).shift(-50).build();
        assertThat(t.tokens()).containsExactly(new TitleGlyphs.Shift(350));
        assertThat(t.tags()).isEqualTo("<shift:256><shift:94>");
        assertThat(TitleGlyphs.builder().shift(5).shift(-5).build().tokens()).isEmpty();
        assertThat(TitleGlyphs.builder().shift(-600).build().tags()).isEqualTo("<shift:-256><shift:-256><shift:-88>");
    }

    @Test
    void theSampleTitleDrawsTheBackgroundThenTheSealFrame() {
        assertThat(SampleScreen.glyphs(false).tags()).isEqualTo(
            "<shift:-8><image:tfp:gui/large_vessel><shift:-45><image:tfp:gui/large_vessel_seal:1:0><shift:-145>");
        assertThat(SampleScreen.glyphs(true).tags()).contains("<image:tfp:gui/large_vessel_seal:0:0>");
        assertThat(SampleScreen.glyphs(true).pen()).isEqualTo(ChestGeometry.TITLE_X);
    }

    @Test
    void layersDrawInZOrderThenDeclarationOrderAndHideMissingFrames() {
        GuiImage a = GuiImage.of("tfp:gui/a", 10, 20, 4, 4);
        GuiImage b = GuiImage.of("tfp:gui/b", 20, 20, 4, 4);
        GuiImage c = GuiImage.of("tfp:gui/c", 30, 20, 4, 4);
        GuiLayers layers = new GuiLayers(null, List.of(
            new GuiLayers.Layer("top", 5, a), new GuiLayers.Layer("low1", 1, b), new GuiLayers.Layer("low2", 1, c)));
        assertThat(layers.layers()).extracting(GuiLayers.Layer::key).containsExactly("low1", "low2", "top");
        String tags = layers.compose(Map.of("top", 0, "low1", 0, "low2", -1)).tags();
        assertThat(tags).isEqualTo("<shift:12><image:tfp:gui/b><shift:-15><image:tfp:gui/a><shift:-7>");
        assertThatThrownBy(() -> layers.compose(Map.of("nope", 0))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GuiLayers(null, List.of(new GuiLayers.Layer("x", 0, a), new GuiLayers.Layer("x", 1, b))))
            .hasMessageContaining("duplicate");
    }

    @Test
    void theLargeVesselSlotsLandOnTheMiddleColumnsOfAThreeRowChest() {
        // TFC: (62|80|98, 19|37|55); chest columns 3..5 are at x 62, 80, 98 and rows at y 18, 36, 54
        assertThat(SampleScreen.DY).isEqualTo(-1);
        assertThat(SampleScreen.SLOTS.chestSlots()).containsExactly(3, 4, 5, 12, 13, 14, 21, 22, 23);
        for (int i = 0; i < SampleScreen.TFC_SLOTS.size(); i++) {
            int chest = SampleScreen.SLOTS.chestSlot(i);
            assertThat(SampleScreen.CHEST.slotX(chest)).isEqualTo(SampleScreen.TFC_SLOTS.get(i).x());
            assertThat(SampleScreen.CHEST.slotY(chest)).isEqualTo(SampleScreen.TFC_SLOTS.get(i).y() + SampleScreen.DY);
            assertThat(SampleScreen.SLOTS.tfcSlot(chest)).isEqualTo(i);
        }
        // the 20 x 20 button at (132, 34) after the move covers the interior of column 7, row 1 completely
        assertThat(SampleScreen.BUTTON_SLOT).isEqualTo(16);
        assertThat(SampleScreen.SLOTS.tfcSlot(16)).isEqualTo(-1);
        assertThatThrownBy(() -> SlotLayout.of(SampleScreen.CHEST, 0, SampleScreen.TFC_SLOTS))
            .hasMessageContaining("off the 3-row chest grid");
    }

    @Test
    void everyImageIsUniqueAndPlaceable() {
        assertThat(GuiImages.all()).extracting(GuiImage::id)
            .containsExactly("tfp:gui/large_vessel", "tfp:gui/large_vessel_seal", "tfp:gui/thermometer");
        assertThat(GuiImages.THERMOMETER.frames()).isGreaterThanOrEqualTo(ProgressFrames.THERMOMETER_FRAMES);
    }
}
