/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen.grillgui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.tfp.core.gui.ChestGeometry;
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

/** PAR-HEAT-24: the grill screen art on the real checkout. */
class GrillScreenArtTest {
    private static final int PANEL = 0xFFC6C6C6;
    private static final int FRAME_DARK = 0xFF373737;
    private static final int SLOT_FILL = 0xFF8B8B8B;
    private static final int INK = 0xFF000000;
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
        assertThat(GrillScreenArt.available(checkout)).isTrue();
        out = new OutputTree();
        images = GrillScreenArt.run(checkout, out);
        tfc = GrillScreenArt.read(checkout.read(GrillScreenArt.TEXTURE), "fire_pit_grill.png");
        art = GrillScreenArt.read(out.get(GrillScreenArt.RP + GrillScreenArt.BACKGROUND.packPath()), "grill.png");
    }

    @Test
    @SuppressWarnings("unchecked")
    void writesTheBackgroundInTheGuiFont() {
        assertThat(images).isEqualTo(1);
        Map<String, Object> root = new Yaml().load(new String(out.get(GrillScreenArt.CONFIG), StandardCharsets.UTF_8));
        Map<String, Object> entries = (Map<String, Object>) root.get("images");
        assertThat(entries).containsOnlyKeys("tfp:gui/grill");
        assertThat((Map<String, Object>) entries.get("tfp:gui/grill")).containsEntry("height", 186)
            .containsEntry("font", "tfp:gui").containsEntry("file", "tfp:font/gui/grill.png");
        assertThat(GrillScreenArt.CONFIG).isEqualTo("craftengine/tfp/configuration/grill_gui.yml");
        assertThat(art.getWidth()).isEqualTo(ChestGeometry.WIDTH);
        assertThat(art.getHeight()).isEqualTo(186);
    }

    /** The numbers of {@code paper/grill/gui/GrillScreenLayout} (DY 2, chest slots 27, 18, 9, 0, 3..7). */
    @Test
    void layoutNumbersMatchTheScreenLayout() {
        assertThat(GrillScreenArt.DY).isEqualTo(2);
        assertThat(GrillScreenArt.DY_PLAYER).isEqualTo(-1);
        assertThat(GrillScreenArt.GRID_LIFT).isEqualTo(4);
        assertThat(GrillScreenArt.CHEST_SLOTS).containsExactly(27, 18, 9, 0, 3, 4, 5, 6, 7);
    }

    /** Every slot of the screen shows a frame where its chest slot is: the fuel column and the five grill slots. */
    @Test
    void framesSitUnderTheirChestSlots() {
        ChestGeometry chest = GrillScreenArt.CHEST;
        for (int slot : GrillScreenArt.CHEST_SLOTS) {
            int x = chest.slotX(slot);
            int y = chest.slotY(slot);
            assertThat(art.getRGB(x - 1, y - 1)).as("frame corner of chest slot " + slot).isEqualTo(FRAME_DARK);
            assertThat(art.getRGB(x + 8, y + 8)).as("inside chest slot " + slot).isNotEqualTo(PANEL);
        }
        for (int slot = 3; slot <= 7; slot++) {
            assertThat(art.getRGB(chest.slotX(slot) + 8, chest.slotY(slot) + 8)).as("grill slot " + slot).isEqualTo(SLOT_FILL);
        }
        // the grill row is TFC's frame band, pixel for pixel, lifted 2 px (TFC's y 19 onto chest row 0's frame top 17)
        for (int y = 0; y < ChestGeometry.SLOT; y++) {
            for (int x = GrillScreenArt.GRID_X0; x < GrillScreenArt.GRID_X1; x++) {
                assertThat(art.getRGB(x, 17 + y)).isEqualTo(tfc.getRGB(x, GrillScreenArt.GRID_Y0 + y));
            }
        }
        // the chest slots TFC does not have show plain panel
        for (int slot : List.of(2, 8, 17, 24, 26, 35)) {
            assertThat(art.getRGB(chest.slotX(slot) + 8, chest.slotY(slot) + 8)).as("chest slot " + slot).isEqualTo(PANEL);
        }
    }

    /** TFC's connector stems reach up to one plain row under the lifted frames; the rest of the connector moved by DY. */
    @Test
    void connectorStemsReachTheLiftedFrames() {
        List<Integer> stems = GrillScreenArt.stems(tfc);
        assertThat(stems).hasSize(5);
        for (int x : stems) {
            assertThat(art.getRGB(x, 35)).as("plain row under the frames at x " + x).isEqualTo(PANEL);
            for (int y = 36; y <= 41; y++) {
                assertThat(art.getRGB(x, y)).as("stem at " + x + "," + y).isEqualTo(INK);
            }
        }
        // the bar and the arrow below are TFC's, moved by DY
        for (int y = GrillScreenArt.STEM_Y; y < GrillScreenArt.STEM_Y + 10; y++) {
            for (int x = GrillScreenArt.GRID_X0; x < GrillScreenArt.GRID_X1; x++) {
                assertThat(art.getRGB(x, y + GrillScreenArt.DY)).isEqualTo(tfc.getRGB(x, y));
            }
        }
    }

    /** The fuel column, thermometer and icon move by DY; the player inventory by DY_PLAYER, as in the firepit art. */
    @Test
    void contentAndInventoryMoveAsTheFirepitArt() {
        for (int y = 12; y < 90; y++) {
            for (int x = 4; x < 56; x++) {
                assertThat(art.getRGB(x, y + GrillScreenArt.DY)).isEqualTo(tfc.getRGB(x, y));
            }
        }
        ChestGeometry chest = GrillScreenArt.CHEST;
        for (int col = 0; col < 9; col++) {
            int x = chest.slotX(0) + 18 * col;
            assertThat(art.getRGB(x - 1, chest.playerRowY(0) - 1)).isEqualTo(FRAME_DARK);
            assertThat(art.getRGB(x - 1, chest.hotbarY() - 1)).isEqualTo(FRAME_DARK);
        }
        for (int y = 0; y < GrillScreenArt.BORDER; y++) {
            for (int x = 0; x < ChestGeometry.WIDTH; x++) {
                assertThat(art.getRGB(x, y)).isEqualTo(tfc.getRGB(x, y));
                assertThat(art.getRGB(x, 185 - y)).isEqualTo(tfc.getRGB(x, 185 - y));
            }
        }
    }

    @Test
    void aDifferentThermometerSpriteIsRefused() {
        BufferedImage other = new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB);
        assertThatThrownBy(() -> GrillScreenArt.checkThermometer(tfc, other)).isInstanceOf(DatagenException.class)
            .hasMessageContaining("thermometer indicator differs");
    }

    @Test
    void artWithoutFiveStemsIsRefused() {
        BufferedImage copy = new BufferedImage(tfc.getWidth(), tfc.getHeight(), BufferedImage.TYPE_INT_ARGB);
        copy.getGraphics().drawImage(tfc, 0, 0, null);
        int plain = copy.getRGB(10, 10);
        for (int x = GrillScreenArt.GRID_X0; x < GrillScreenArt.GRID_X1; x++) {
            copy.setRGB(x, GrillScreenArt.STEM_Y, plain);
        }
        assertThatThrownBy(() -> GrillScreenArt.background(copy)).isInstanceOf(DatagenException.class)
            .hasMessageContaining("0 connector stems");
    }
}
