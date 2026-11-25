/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen.blastfurnace;

import static dev.tfp.core.blastfurnace.BlastFurnaceScreenLayout.*;
import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.gui.ChestGeometry;
import dev.tfp.core.gui.GuiImage;
import dev.tfp.datagen.OutputTree;
import dev.tfp.datagen.TfcCheckout;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/** PAR-METAL-27: the blast furnace screen art on the real checkout, pixel for pixel against TFC's texture. */
class BlastFurnaceScreenArtTest {
    private static OutputTree out;
    private static BufferedImage tfc;

    @BeforeAll
    static void generate() throws Exception {
        Path path = Path.of(System.getProperty("tfc.checkout", "/opt/tfc"));
        Assumptions.assumeTrue(Files.isDirectory(path.resolve(".git")), "no TFC checkout at " + path);
        TfcCheckout checkout;
        try {
            checkout = TfcCheckout.open(path, TfcCheckout.PINNED_COMMIT);
        } catch (RuntimeException e) {
            Assumptions.abort("TFC checkout is not at the pinned commit: " + e.getMessage());
            return;
        }
        assertThat(BlastFurnaceScreenArt.available(checkout)).isTrue();
        out = new OutputTree();
        assertThat(BlastFurnaceScreenArt.run(checkout, out)).isEqualTo(7);
        tfc = ImageIO.read(new ByteArrayInputStream(checkout.read(BlastFurnaceScreenArt.TEXTURE)));
    }

    private static BufferedImage png(String name) throws Exception {
        return ImageIO.read(new ByteArrayInputStream(out.get(BlastFurnaceScreenArt.RP + "textures/font/gui/" + name + ".png")));
    }

    private static void same(BufferedImage a, int ax, int ay, int sx, int sy, int w, int h, String what) {
        for (int j = 0; j < h; j++) {
            for (int i = 0; i < w; i++) {
                assertThat(a.getRGB(ax + i, ay + j)).as("%s pixel %d,%d", what, i, j).isEqualTo(tfc.getRGB(sx + i, sy + j));
            }
        }
    }

    @Test
    void backgroundIsTheFourRowChestWithTfcsArtOnItsGrid() throws Exception {
        BufferedImage bg = png("blast_furnace");
        assertThat(bg.getWidth()).isEqualTo(176);
        assertThat(bg.getHeight()).isEqualTo(new ChestGeometry(4).height()).isEqualTo(186);
        // the tuyere slot frame (TFC 151,16, 18 x 18), moved down DY
        same(bg, 151, 16 + DY, 151, 16, 18, 18, "tuyere frame");
        // the thermometer's housing and the tank basin, moved down DY
        same(bg, 4, 12 + DY, 4, 12, 30, 80, "thermometer housing");
        same(bg, 55, 50 + DY, 55, 50, 66, 40, "tank basin");
        // the meter outlines
        same(bg, ORE_X - 1, METER_Y - 1 + DY, ORE_X - 1, METER_Y - 1, 12, 68, "ore meter");
        // the player inventory (TFC y 104, the chest's 103)
        ChestGeometry chest = new ChestGeometry(4);
        for (int row = 0; row < 3; row++) {
            same(bg, 7, chest.playerRowY(row) - 1, 7, 103 + 18 * row, 162, 18, "player row " + row);
        }
        same(bg, 7, chest.playerRowY(2) + 18 + 4 - 1, 7, 103 + 54 + 4, 162, 18, "hotbar");
        // the frame: the first and last rows
        same(bg, 0, 0, 0, 0, 176, 1, "top border");
        same(bg, 0, 185, 0, 185, 176, 1, "bottom border");
    }

    @Test
    void thermometerFramesCarryTfcsIndicator() throws Exception {
        BufferedImage t = png("blast_furnace_thermometer");
        for (int f : new int[] {1, 25, 51}) {
            int fx = THERMOMETER.frameCol(f) * 15;
            int fy = THERMOMETER.frameRow(f) * THERMOMETER.height() + 51 - f;
            same(t, fx, fy, THERMOMETER_U, THERMOMETER_V, 15, 5, "indicator of frame " + f);
        }
        assertThat(THERMOMETER.x()).isEqualTo(8);
        assertThat(THERMOMETER.y()).isEqualTo(76 - 51 + DY);
    }

    @Test
    void barFramesAreTfcsEmptyBarOfEveryHeight() throws Exception {
        BufferedImage b = png("blast_furnace_ore_bar");
        // frame 0: the dotted region
        same(b, 0, 0, DOTTED_U, 0, 10, 66, "dotted");
        for (int e : new int[] {0, 12, 40, 64}) {
            int f = 1 + e;
            int fx = ORE_BAR.frameCol(f) * 10;
            int fy = ORE_BAR.frameRow(f) * 66;
            same(b, fx, fy + 64 - e, EMPTY_U, 0, 10, 1 + e, "empty bar e=" + e);
            for (int j = 0; j < 64 - e; j++) { // nothing above it
                for (int i = 0; i < 9; i++) {
                    assertThat(b.getRGB(fx + i, fy + j) >>> 24).isZero();
                }
            }
        }
        assertThat(ORE_BAR.x()).isEqualTo(42);
        assertThat(FUEL_BAR.x()).isEqualTo(124);
        assertThat(ORE_BAR.y()).isEqualTo(22 + DY);
    }

    @Test
    void fillFramesAreTfcsFullBarsFromTheirColumns() throws Exception {
        BufferedImage ore = png("blast_furnace_ore_fill");
        BufferedImage fuel = png("blast_furnace_fuel_fill");
        for (int h : new int[] {1, 20, 64}) {
            int f = h - 1;
            same(ore, ORE_FILL.frameCol(f) * 10, ORE_FILL.frameRow(f) * 66 + 65 - h, ORE_FILL_UNLIT_U, 1, 10, h, "unlit " + h);
            int fl = 64 + h - 1;
            same(ore, ORE_FILL.frameCol(fl) * 10, ORE_FILL.frameRow(fl) * 66 + 65 - h, ORE_FILL_LIT_U, 1, 10, h, "lit " + h);
            same(fuel, FUEL_FILL.frameCol(f) * 10, FUEL_FILL.frameRow(f) * 66 + 65 - h, FUEL_FILL_U, 1, 10, h, "fuel " + h);
        }
    }

    @Test
    void fluidFramesFillFromTheBottom() throws Exception {
        BufferedImage f = png("blast_furnace_fluid");
        assertThat(f.getWidth()).isEqualTo(FLUID.pngWidth());
        int k = 10;
        int fx = FLUID.frameCol(k) * 36;
        int fy = FLUID.frameRow(k) * 31;
        assertThat(f.getRGB(fx + 3, fy + 30) >>> 24).isEqualTo(255);
        assertThat(f.getRGB(fx + 3, fy + 31 - k) >>> 24).isEqualTo(255);
        assertThat(f.getRGB(fx + 3, fy + 31 - k - 1) >>> 24).isZero();
        assertThat(FLUID.x()).isEqualTo(70);
        assertThat(FLUID.y()).isEqualTo(84 - 31 + DY);
    }

    @Test
    @SuppressWarnings("unchecked")
    void yamlDeclaresEveryImageOnTheGuiFont() {
        String yaml = new String(out.get(BlastFurnaceScreenArt.CONFIG), StandardCharsets.UTF_8);
        Map<String, Object> images = (Map<String, Object>) ((Map<String, Object>) new Yaml().load(yaml)).get("images");
        for (GuiImage g : LAYERS.images()) {
            assertThat(images).containsKey(g.id());
            Map<String, Object> e = (Map<String, Object>) images.get(g.id());
            assertThat(e.get("font")).isEqualTo("tfp:gui");
            assertThat(e.get("height")).isEqualTo(g.height());
        }
        assertThat(images).hasSize(7);
    }
}
