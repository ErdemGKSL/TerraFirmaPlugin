/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.gui;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.gui.ChestGeometry;
import dev.tfp.core.gui.GuiImage;
import dev.tfp.core.gui.Glyphs;
import dev.tfp.core.gui.SampleScreen;
import dev.tfp.core.gui.SlotLayout;
import dev.tfp.core.gui.TitleGlyphs;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * PAR-INFRA-03: the sample title, replayed the way the client lays out a title, puts every glyph where the art expects
 * it; the slots sit at TFC's x and at TFC's y plus the layout offset; and all of it holds at GUI scales 1 to 4.
 */
class GuiAlignmentTest {
    /** A glyph as the client draws it: left and top in window GUI pixels. */
    record Drawn(String id, int left, int top) {
    }

    /** Replays the tokens: the pen starts at titleLabelX, a shift moves it, a glyph is drawn at the pen. */
    static List<Drawn> layout(TitleGlyphs glyphs, List<GuiImage> images) {
        List<Drawn> out = new ArrayList<>();
        int pen = ChestGeometry.TITLE_X;
        for (TitleGlyphs.Token t : glyphs.tokens()) {
            switch (t) {
                case TitleGlyphs.Shift s -> pen += s.px();
                case TitleGlyphs.Glyph g -> {
                    GuiImage img = images.stream().filter(i -> i.id().equals(g.id())).findFirst().orElseThrow();
                    out.add(new Drawn(g.id(), pen, Glyphs.top(img.ascent())));
                    pen += img.advance();
                }
            }
        }
        assertThat(pen).as("the label starts where the vanilla title does").isEqualTo(ChestGeometry.TITLE_X);
        return out;
    }

    @Test
    void theSampleGlyphsLandOnTheArtsPlaces() {
        List<GuiImage> images = SampleScreen.LAYERS.images();
        for (boolean sealed : new boolean[] {false, true}) {
            List<Drawn> drawn = layout(SampleScreen.glyphs(sealed), images);
            assertThat(drawn).containsExactly(
                new Drawn("tfp:gui/large_vessel", 0, 0),
                new Drawn("tfp:gui/large_vessel_seal", SampleScreen.TFC_BUTTON_X, SampleScreen.TFC_BUTTON_Y - 1));
        }
    }

    @Test
    void slotXOffsetsAreTfcsAndYOffsetsMoveByTheLayoutOffset() {
        ChestGeometry chest = SampleScreen.CHEST;
        SlotLayout layout = SampleScreen.SLOTS;
        for (int i = 0; i < SampleScreen.TFC_SLOTS.size(); i++) {
            SlotLayout.Point tfc = SampleScreen.TFC_SLOTS.get(i);
            int slot = layout.chestSlot(i);
            assertThat(chest.slotX(slot)).as("x of TFC slot " + i).isEqualTo(tfc.x());
            assertThat(chest.slotY(slot) - tfc.y()).as("y offset of TFC slot " + i).isEqualTo(SampleScreen.DY);
        }
        // TFC's player inventory (84, hotbar 142) moves down by one: the 3-row chest's are at 85 and 143
        assertThat(chest.playerRowY(0) - 84).isEqualTo(1);
        assertThat(chest.hotbarY() - 142).isEqualTo(1);
        // the button's art covers its slot's whole 16 x 16 interior
        int b = SampleScreen.BUTTON_SLOT;
        assertThat(chest.slotX(b)).isBetween(SampleScreen.TFC_BUTTON_X, SampleScreen.TFC_BUTTON_X + 20 - 16);
        assertThat(chest.slotY(b)).isBetween(SampleScreen.SEAL.y(), SampleScreen.SEAL.y() + 20 - 16);
    }

    @Test
    void artAndSlotsKeepTheirOffsetAtEveryGuiScale() {
        // the client centres the window in GUI pixels and multiplies by the scale; integer GUI offsets stay exact
        int[][] windows = {{854, 480}, {1280, 720}, {1920, 1080}, {2560, 1440}, {1366, 768}};
        for (int scale = 1; scale <= 4; scale++) {
            for (int[] w : windows) {
                int guiW = (int) Math.ceil(w[0] / (double) scale);
                int guiH = (int) Math.ceil(w[1] / (double) scale);
                int left = (guiW - ChestGeometry.WIDTH) / 2;
                int top = (guiH - SampleScreen.CHEST.height()) / 2;
                for (int i = 0; i < SampleScreen.TFC_SLOTS.size(); i++) {
                    int slot = SampleScreen.SLOTS.chestSlot(i);
                    // item pixel on screen vs the art's slot interior pixel on screen (art origin at window 0, 0)
                    int itemX = (left + SampleScreen.CHEST.slotX(slot)) * scale;
                    int itemY = (top + SampleScreen.CHEST.slotY(slot)) * scale;
                    int artX = (left + SampleScreen.BACKGROUND.x() + SampleScreen.TFC_SLOTS.get(i).x()) * scale;
                    int artY = (top + SampleScreen.BACKGROUND.y() + SampleScreen.TFC_SLOTS.get(i).y() + SampleScreen.DY) * scale;
                    assertThat(itemX).isEqualTo(artX);
                    assertThat(itemY).isEqualTo(artY);
                    assertThat(itemX % scale).as("whole screen pixels").isZero();
                }
            }
        }
    }
}
