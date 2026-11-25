/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen.potgui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.tfp.core.gui.ChestGeometry;
import dev.tfp.core.gui.GuiImage;
import dev.tfp.core.gui.ProgressFrames;
import dev.tfp.datagen.DatagenException;
import dev.tfp.datagen.OutputTree;
import dev.tfp.datagen.TfcCheckout;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/** PAR-HEAT-25: the pot screen art on the real checkout. */
class PotScreenArtTest {
    private static final int PANEL = 0xFFC6C6C6;
    private static final int FRAME_DARK = 0xFF373737;
    private static OutputTree out;
    private static BufferedImage tfc;
    private static BufferedImage art;
    private static int images;

    @BeforeAll
    static void generate() {
        Path path = Path.of(System.getProperty("tfc.checkout", "/opt/tfc"));
        Assumptions.assumeTrue(Files.isDirectory(path.resolve(".git")), "no TFC checkout at " + path);
        TfcCheckout checkout;
        try {
            checkout = TfcCheckout.open(path, TfcCheckout.PINNED_COMMIT);
        } catch (RuntimeException e) {
            Assumptions.abort("TFC checkout is not at the pinned commit: " + e.getMessage());
            return;
        }
        assertThat(PotScreenArt.available(checkout)).isTrue();
        out = new OutputTree();
        images = PotScreenArt.run(checkout, out);
        tfc = PotScreenArt.read(checkout.read(PotScreenArt.TEXTURE), "fire_pit_cooking_pot.png");
        art = png(PotScreenArt.BACKGROUND);
    }

    private static BufferedImage png(GuiImage image) {
        return PotScreenArt.read(out.get(PotScreenArt.RP + image.packPath()), image.name() + ".png");
    }

    @Test
    @SuppressWarnings("unchecked")
    void writesTheFourImagesInTheGuiFontWithTheLayoutsIds() {
        assertThat(images).isEqualTo(4);
        Map<String, Object> root = new Yaml().load(new String(out.get(PotScreenArt.CONFIG), StandardCharsets.UTF_8));
        Map<String, Object> entries = (Map<String, Object>) root.get("images");
        assertThat(entries).containsOnlyKeys("tfp:gui/pot", "tfp:gui/pot_thermometer", "tfp:gui/pot_boiling", "tfp:gui/pot_flames");
        assertThat((Map<String, Object>) entries.get("tfp:gui/pot")).containsEntry("height", 186)
            .containsEntry("font", "tfp:gui").containsEntry("file", "tfp:font/gui/pot.png");
        assertThat((Map<String, Object>) entries.get("tfp:gui/pot_thermometer")).containsEntry("grid_size", "4,13");
        assertThat((Map<String, Object>) entries.get("tfp:gui/pot_boiling")).containsEntry("grid_size", "3,7")
            .containsEntry("height", 20);
        assertThat((Map<String, Object>) entries.get("tfp:gui/pot_flames")).containsEntry("grid_size", "1,2");
        assertThat(PotScreenArt.CONFIG).isEqualTo("craftengine/tfp/configuration/pot_gui.yml");
        assertThat(art.getWidth()).isEqualTo(ChestGeometry.WIDTH);
        assertThat(art.getHeight()).isEqualTo(186);
    }

    /** The numbers of {@code paper/pot/gui/PotScreenLayout} (DY 2, chest slots 27, 18, 9, 0, 3, 4, 12, 13, 14, the image rects). */
    @Test
    void layoutNumbersMatchTheScreenLayout() {
        assertThat(PotScreenArt.DY).isEqualTo(2);
        assertThat(PotScreenArt.DY_PLAYER).isEqualTo(-1);
        assertThat(PotScreenArt.CHEST_SLOTS).containsExactly(27, 18, 9, 0, 3, 4, 12, 13, 14);
        assertThat(PotScreenArt.THERMOMETER).isEqualTo(new GuiImage("tfp:gui/pot_thermometer", 30, 27, 15, 56, 4, 13));
        assertThat(PotScreenArt.BOILING).isEqualTo(new GuiImage("tfp:gui/pot_boiling", 131, 12, 24, 20, 3, 7));
        assertThat(PotScreenArt.FLAMES).isEqualTo(new GuiImage("tfp:gui/pot_flames", 121, 60, 43, 13, 1, 2));
        assertThat(PotScreenArt.BACKGROUND).isEqualTo(GuiImage.of("tfp:gui/pot", 0, 0, 176, 186));
    }

    /** Every slot of the screen shows a frame where its chest slot is, and TFC's frames are copied pixel for pixel. */
    @Test
    void framesSitUnderTheirChestSlots() {
        ChestGeometry chest = PotScreenArt.CHEST;
        for (int slot : PotScreenArt.CHEST_SLOTS) {
            int x = chest.slotX(slot);
            int y = chest.slotY(slot);
            assertThat(art.getRGB(x - 1, y - 1)).as("frame corner of chest slot " + slot).isEqualTo(FRAME_DARK);
            assertThat(art.getRGB(x + 8, y + 8)).as("inside chest slot " + slot).isNotEqualTo(PANEL);
        }
        for (int i = PotScreenArt.FIRST_INPUT; i < PotScreenArt.TFC_SLOTS.size(); i++) {
            int[] from = PotScreenArt.TFC_SLOTS.get(i);
            int[] to = PotScreenArt.chestPoint(PotScreenArt.CHEST_SLOTS.get(i));
            for (int dy = 0; dy < ChestGeometry.SLOT; dy++) {
                for (int dx = 0; dx < ChestGeometry.SLOT; dx++) {
                    assertThat(art.getRGB(to[0] - 1 + dx, to[1] - 1 + dy)).as("input %d at %d,%d", i, dx, dy)
                        .isEqualTo(tfc.getRGB(from[0] - 1 + dx, from[1] - 1 + dy));
                }
            }
        }
        // the chest slots left of and under the inputs that TFC does not have show plain panel
        for (int slot : List.of(2, 5, 11, 15, 8, 17, 26, 35)) {
            if (slot == 17 || slot == 8 || slot == 26) {
                continue; // right of the pot drawing's edge: not asserted
            }
            assertThat(art.getRGB(chest.slotX(slot) + 8, chest.slotY(slot) + 8)).as("chest slot " + slot).isEqualTo(PANEL);
        }
    }

    /** TFC's old frame places hold plain panel again, except where a moved frame now sits. */
    @Test
    void theOldFramePlacesAreClearedAndNothingElseOfTheBandChanged() {
        ChestGeometry chest = PotScreenArt.CHEST;
        for (int y = PotScreenArt.CLEAR_Y0 + PotScreenArt.DY; y < PotScreenArt.CLEAR_Y1 + PotScreenArt.DY; y++) {
            for (int x = PotScreenArt.CLEAR_X0; x < PotScreenArt.CLEAR_X1; x++) {
                boolean inNew = false;
                for (int i = PotScreenArt.FIRST_INPUT; i < PotScreenArt.TFC_SLOTS.size(); i++) {
                    int slot = PotScreenArt.CHEST_SLOTS.get(i);
                    int fx = chest.slotX(slot) - 1;
                    int fy = chest.slotY(slot) - 1;
                    inNew |= x >= fx && x < fx + 18 && y >= fy && y < fy + 18;
                }
                if (!inNew) {
                    assertThat(art.getRGB(x, y)).as("(%d, %d) is plain panel", x, y).isEqualTo(PANEL);
                }
            }
        }
    }

    /** The fuel column, thermometer socket and the pot drawing move by DY; the player inventory by DY_PLAYER. */
    @Test
    void contentAndInventoryMoveAsTheFirepitArt() {
        for (int y = 12; y < 90; y++) {
            for (int x = 4; x < 52; x++) {
                assertThat(art.getRGB(x, y + PotScreenArt.DY)).isEqualTo(tfc.getRGB(x, y));
            }
            for (int x = 118; x < 172; x++) {
                assertThat(art.getRGB(x, y + PotScreenArt.DY)).as("pot drawing at %d,%d", x, y).isEqualTo(tfc.getRGB(x, y));
            }
        }
        for (int col = 0; col < 9; col++) {
            int x = PotScreenArt.CHEST.slotX(0) + 18 * col;
            assertThat(art.getRGB(x - 1, PotScreenArt.CHEST.playerRowY(0) - 1)).isEqualTo(FRAME_DARK);
            assertThat(art.getRGB(x - 1, PotScreenArt.CHEST.hotbarY() - 1)).isEqualTo(FRAME_DARK);
        }
        for (int y = 0; y < PotScreenArt.BORDER; y++) {
            for (int x = 0; x < ChestGeometry.WIDTH; x++) {
                assertThat(art.getRGB(x, y)).isEqualTo(tfc.getRGB(x, y));
                assertThat(art.getRGB(x, 185 - y)).isEqualTo(tfc.getRGB(x, 185 - y));
            }
        }
    }

    @Test
    void thermometerFramesDrawTheIndicatorKPixelsAboveTheBottom() {
        BufferedImage strip = png(PotScreenArt.THERMOMETER);
        GuiImage t = PotScreenArt.THERMOMETER;
        assertThat(strip.getWidth()).isEqualTo(t.pngWidth());
        for (int k : List.of(1, 17, 51)) {
            int fx = t.frameCol(k) * t.width();
            int fy = t.frameRow(k) * t.height() + ProgressFrames.thermometerOffset(k);
            for (int dy = 0; dy < ProgressFrames.THERMOMETER_HEIGHT; dy++) {
                for (int dx = 0; dx < ProgressFrames.THERMOMETER_WIDTH; dx++) {
                    assertThat(strip.getRGB(fx + dx, fy + dy)).isEqualTo(tfc.getRGB(PotScreenArt.THERMOMETER_U + dx,
                        PotScreenArt.THERMOMETER_V + dy));
                }
            }
        }
        assertThat(strip.getRGB(t.width() - 1, t.height() - 1)).as("frame 0 keeps its advance").isEqualTo(PotScreenArt.MARKER);
    }

    /** Frame h draws the bottom h rows of TFC's bubble sprite, twice. */
    @Test
    void bubbleFramesGrowFromTheBottomRow() {
        BufferedImage strip = png(PotScreenArt.BOILING);
        GuiImage b = PotScreenArt.BOILING;
        assertThat(b.frames()).isEqualTo(PotScreenArt.BUBBLE_HEIGHT + 1);
        for (int rows : List.of(1, 7, 20)) {
            int fx = b.frameCol(rows) * b.width();
            int fy = b.frameRow(rows) * b.height();
            for (int y = 0; y < b.height(); y++) {
                for (int x = 0; x < b.width(); x++) {
                    int column = x < PotScreenArt.BUBBLE_WIDTH ? x : x >= 13 && x < 13 + PotScreenArt.BUBBLE_WIDTH ? x - 13 : -1;
                    int got = strip.getRGB(fx + x, fy + y);
                    if (column < 0 || y < PotScreenArt.BUBBLE_HEIGHT - rows) {
                        assertThat(got >>> 24).as("frame %d at %d,%d is clear", rows, x, y).isLessThanOrEqualTo(1);
                    } else if (x == b.width() - 1 && y == b.height() - 1 && got == PotScreenArt.MARKER) {
                        continue; // the advance marker, where the sprite's corner is clear
                    } else {
                        int v = PotScreenArt.BUBBLE_V + PotScreenArt.BUBBLE_SPRITE_ROWS - rows + (y - (PotScreenArt.BUBBLE_HEIGHT - rows));
                        assertThat(got).as("frame %d at %d,%d", rows, x, y).isEqualTo(tfc.getRGB(PotScreenArt.BUBBLE_U + column, v));
                    }
                }
            }
        }
    }

    @Test
    void flameFrameDrawsTheThreeFlames() {
        BufferedImage strip = png(PotScreenArt.FLAMES);
        GuiImage f = PotScreenArt.FLAMES;
        int fx = f.frameCol(1) * f.width();
        for (int k = 0; k < PotScreenArt.FLAME_COUNT; k++) {
            for (int dy = 0; dy < PotScreenArt.FLAME_SIZE; dy++) {
                for (int dx = 0; dx < PotScreenArt.FLAME_SIZE; dx++) {
                    assertThat(strip.getRGB(fx + k * PotScreenArt.FLAME_STEP + dx, dy)).isEqualTo(tfc.getRGB(PotScreenArt.FLAME_U + dx,
                        PotScreenArt.FLAME_V + dy));
                }
            }
        }
        assertThat(strip.getRGB(f.width() - 1, f.height() - 1)).as("frame 0 keeps its advance").isEqualTo(PotScreenArt.MARKER);
    }

    @Test
    void artWithSomethingBesideTheFramesIsRefused() {
        BufferedImage copy = new BufferedImage(tfc.getWidth(), tfc.getHeight(), BufferedImage.TYPE_INT_ARGB);
        copy.getGraphics().drawImage(tfc, 0, 0, null);
        copy.setRGB(112, 30, 0xFF123456); // beside the old frames, inside the cleared band
        assertThatThrownBy(() -> PotScreenArt.background(copy)).isInstanceOf(DatagenException.class)
            .hasMessageContaining("beside the input frames");
    }
}
