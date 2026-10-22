/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen.gui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.tfp.core.gui.ChestGeometry;
import dev.tfp.core.gui.GuiImage;
import dev.tfp.core.gui.GuiImages;
import dev.tfp.core.gui.ProgressFrames;
import dev.tfp.core.gui.SampleScreen;
import dev.tfp.core.gui.SlotLayout;
import dev.tfp.datagen.DatagenException;
import dev.tfp.datagen.OutputTree;
import dev.tfp.datagen.TfcCheckout;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;

/** PAR-INFRA-03: the GUI images from the real pinned TFC checkout (skipped when it is absent). */
class GuiArtTest {
    private static TfcCheckout tfc;
    private static OutputTree out;

    @BeforeAll
    static void generate() {
        Path path = Path.of(System.getProperty("tfc.checkout", "/opt/tfc"));
        Assumptions.assumeTrue(Files.isDirectory(path.resolve(".git")), "no TFC checkout at " + path);
        try {
            tfc = TfcCheckout.open(path, TfcCheckout.PINNED_COMMIT);
        } catch (RuntimeException e) {
            Assumptions.abort("TFC checkout is not at the pinned commit: " + e.getMessage());
        }
        out = new OutputTree();
        assertThat(GuiArt.run(tfc, out)).isEqualTo(GuiImages.all().size());
    }

    private static BufferedImage png(String path) {
        byte[] b = out.get(path);
        assertThat(b).as(path).isNotNull();
        try {
            return ImageIO.read(new ByteArrayInputStream(b));
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void everyReferencedImageExistsWithTheExpectedSize() {
        LoaderOptions opts = new LoaderOptions();
        opts.setAllowDuplicateKeys(false);
        String yaml = GuiArt.yaml(out);
        assertThat(yaml).doesNotContain("\t");
        Map<String, Object> root = new Yaml(opts).load(yaml);
        Map<String, Map<String, Object>> images = (Map<String, Map<String, Object>>) root.get("images");
        assertThat(images.keySet()).containsExactlyElementsOf(GuiImages.all().stream().map(GuiImage::id).toList());
        for (GuiImage image : GuiImages.all()) {
            Map<String, Object> e = images.get(image.id());
            String file = (String) e.get("file");
            assertThat(file).startsWith("tfp:font/gui/").endsWith(".png");
            String packPath = GuiArt.RP + "textures/" + file.substring("tfp:".length());
            BufferedImage png = png(packPath);
            int rows = 1;
            int cols = 1;
            if (e.containsKey("grid_size")) {
                String[] g = ((String) e.get("grid_size")).split(",");
                rows = Integer.parseInt(g[0]);
                cols = Integer.parseInt(g[1]);
            }
            int height = (Integer) e.get("height");
            int ascent = (Integer) e.get("ascent");
            assertThat(png.getHeight()).as(image.id() + " cell height").isEqualTo(height * rows);
            assertThat(png.getWidth()).as(image.id()).isEqualTo(image.width() * cols);
            assertThat(ascent).as(image.id() + ": CE refuses ascent > height").isLessThanOrEqualTo(height);
            assertThat(height).isLessThanOrEqualTo(GuiImage.MAX_CELL);
            assertThat(13 - ascent).as(image.id() + " top").isEqualTo(image.y());
            assertThat(e.get("font")).isEqualTo("tfp:gui");
        }
        assertThat(new String(out.get(GuiArt.BLANK_ITEM), StandardCharsets.UTF_8)).contains("minecraft:empty");
    }

    /** The 18 x 18 frame around item position (x, y). */
    private static void assertSameFrame(BufferedImage src, int sx, int sy, BufferedImage dst, int dx, int dy, String what) {
        for (int j = 0; j < 18; j++) {
            for (int i = 0; i < 18; i++) {
                assertThat(dst.getRGB(dx - 1 + i, dy - 1 + j)).as(what + " pixel " + i + "," + j)
                    .isEqualTo(src.getRGB(sx - 1 + i, sy - 1 + j));
            }
        }
    }

    @Test
    void theBackgroundsSlotFramesSitUnderTheChestSlots() {
        BufferedImage src = GuiArt.read(tfc, GuiArt.LARGE_VESSEL_TEXTURE);
        BufferedImage bg = png(GuiArt.RP + SampleScreen.BACKGROUND.packPath());
        ChestGeometry chest = SampleScreen.CHEST;
        assertThat(bg.getWidth()).isEqualTo(176);
        assertThat(bg.getHeight()).isEqualTo(chest.height());
        for (int i = 0; i < SampleScreen.TFC_SLOTS.size(); i++) {
            SlotLayout.Point p = SampleScreen.TFC_SLOTS.get(i);
            int c = SampleScreen.SLOTS.chestSlot(i);
            assertSameFrame(src, p.x(), p.y(), bg, chest.slotX(c), chest.slotY(c), "container slot " + i);
        }
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 9; c++) {
                assertSameFrame(src, 8 + 18 * c, ChestRelayout.TFC_PLAYER_Y + 18 * r, bg, 8 + 18 * c, chest.playerRowY(r),
                    "player slot " + r + "," + c);
            }
        }
        for (int c = 0; c < 9; c++) {
            assertSameFrame(src, 8 + 18 * c, ChestRelayout.TFC_HOTBAR_Y, bg, 8 + 18 * c, chest.hotbarY(), "hotbar " + c);
        }
        for (int x = 0; x < 176; x++) {
            for (int b = 0; b < ChestRelayout.BORDER; b++) {
                assertThat(bg.getRGB(x, b)).as("top border").isEqualTo(src.getRGB(x, b));
                assertThat(bg.getRGB(x, chest.height() - 1 - b)).as("bottom border")
                    .isEqualTo(src.getRGB(x, SampleScreen.TFC_HEIGHT - 1 - b));
            }
        }
        for (int y = ChestRelayout.BORDER; y < chest.height() - ChestRelayout.BORDER; y++) {
            for (int x = 0; x < 176; x++) {
                assertThat(bg.getRGB(x, y) >>> 24).as("no hole at " + x + "," + y).isEqualTo(0xFF);
            }
        }
    }

    @Test
    void theSealAndThermometerFramesAreTfcsSprites() {
        BufferedImage vessel = GuiArt.read(tfc, GuiArt.LARGE_VESSEL_TEXTURE);
        BufferedImage seal = png(GuiArt.RP + SampleScreen.SEAL.packPath());
        for (int j = 0; j < 20; j++) {
            for (int i = 0; i < 20; i++) {
                assertThat(seal.getRGB(i, j)).isEqualTo(vessel.getRGB(236 + i, j));
                assertThat(seal.getRGB(i, 20 + j)).isEqualTo(vessel.getRGB(236 + i, 20 + j));
            }
        }
        BufferedImage firePit = GuiArt.read(tfc, GuiArt.FIRE_PIT_TEXTURE);
        GuiImage t = GuiImages.THERMOMETER;
        BufferedImage strip = png(GuiArt.RP + t.packPath());
        for (int k = 0; k < t.frames(); k++) {
            int ox = t.frameCol(k) * t.width();
            int oy = t.frameRow(k) * t.height();
            for (int y = 0; y < t.height(); y++) {
                for (int x = 0; x < t.width(); x++) {
                    int got = strip.getRGB(ox + x, oy + y);
                    boolean inIndicator = k >= 1 && k < ProgressFrames.THERMOMETER_FRAMES
                        && y >= ProgressFrames.thermometerOffset(k) && y < ProgressFrames.thermometerOffset(k) + 5;
                    if (inIndicator) {
                        assertThat(got).isEqualTo(firePit.getRGB(176 + x, y - ProgressFrames.thermometerOffset(k)));
                    } else {
                        assertThat(got >>> 24).as("frame " + k + " transparent at " + x + "," + y).isZero();
                    }
                }
            }
        }
    }

    @Test
    void artWithoutAPlainGapRefusesToRelayout() {
        BufferedImage busy = new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 256; y++) {
            for (int x = 0; x < 256; x++) {
                busy.setRGB(x, y, 0xFF000000 | (x * 7 + y * 13));
            }
        }
        assertThatThrownBy(() -> ChestRelayout.compose(busy, 166, new ChestGeometry(3), -1, "busy"))
            .isInstanceOf(DatagenException.class).hasMessageContaining("no plain panel row");
        assertThatThrownBy(() -> ChestRelayout.compose(new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB), 166,
            new ChestGeometry(3), -1, "small")).isInstanceOf(DatagenException.class);
    }
}
