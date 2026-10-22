/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** The two layers of every bar overlay pixel-perfect ({@code plans/hud-layered-redesign.md} section 5). */
class HudArtTest {

    static List<HudArt.Bar> bars() {
        return HudLayouts.BAR_SPECS.values().stream().map(s -> HudArt.bar(s.art())).toList();
    }

    static List<String> fills(HudArt.Bar bar) {
        List<String> files = new ArrayList<>();
        files.add(bar.fillFile());
        bar.variants().forEach(v -> files.add(bar.variantFile(v)));
        return files;
    }

    @Test
    void everyLayerHasTheCanvasSize() {
        for (HudArt.Bar bar : bars()) {
            assertSize(bar, bar.bgFile());
            fills(bar).forEach(f -> assertSize(bar, f));
        }
    }

    @Test
    void fillPixelsLieOnlyInsideTheInteriorAndNeverOnTheFrame() {
        for (HudArt.Bar bar : bars()) {
            BufferedImage bg = HudArt.image(bar.bgFile());
            for (String file : fills(bar)) {
                BufferedImage fill = HudArt.image(file);
                for (int y = 0; y < bar.height(); y++) {
                    for (int x = 0; x < bar.width(); x++) {
                        boolean opaque = alpha(fill, x, y) != 0;
                        if (opaque) {
                            assertThat(bar.interior(x, y)).as(file + " pixel " + x + "," + y + " outside the interior").isTrue();
                        }
                        if (!bar.interior(x, y) && alpha(bg, x, y) != 0) {
                            assertThat(opaque).as(file + " covers frame pixel " + x + "," + y).isFalse();
                        }
                    }
                }
            }
        }
    }

    @Test
    void interiorIsFullyOpaqueInTheBackgroundAndInEveryFullFill() {
        for (HudArt.Bar bar : bars()) {
            BufferedImage bg = HudArt.image(bar.bgFile());
            for (int y = 0; y < bar.height(); y++) {
                for (int x = 0; x < bar.width(); x++) {
                    if (bar.interior(x, y)) {
                        assertThat(alpha(bg, x, y)).as(bar.bgFile() + " " + x + "," + y).isEqualTo(255);
                        for (String file : fills(bar)) {
                            assertThat(alpha(HudArt.image(file), x, y)).as(file + " gap at " + x + "," + y).isEqualTo(255);
                        }
                    }
                }
            }
        }
    }

    @Test
    void everyBarHasAFrameRingAndAnEmptyInteriorDarkerThanTheFill() {
        for (HudArt.Bar bar : bars()) {
            BufferedImage bg = HudArt.image(bar.bgFile());
            BufferedImage fill = HudArt.image(bar.fillFile());
            int ring = 0;
            long bgLight = 0;
            long fillLight = 0;
            for (int y = 0; y < bar.height(); y++) {
                for (int x = 0; x < bar.width(); x++) {
                    if (!bar.interior(x, y) && alpha(bg, x, y) != 0) {
                        ring++;
                    }
                    if (bar.interior(x, y)) {
                        bgLight += light(bg.getRGB(x, y));
                        fillLight += light(fill.getRGB(x, y));
                    }
                }
            }
            assertThat(ring).as(bar.name() + " frame").isPositive();
            assertThat(bgLight).as(bar.name() + " empty interior darker than the fill").isLessThan(fillLight);
        }
    }

    @Test
    void alphaIsBinaryInTheBars() {
        for (HudArt.Bar bar : bars()) {
            List<String> files = new ArrayList<>(fills(bar));
            files.add(bar.bgFile());
            for (String file : files) {
                BufferedImage img = HudArt.image(file);
                for (int y = 0; y < img.getHeight(); y++) {
                    for (int x = 0; x < img.getWidth(); x++) {
                        int a = alpha(img, x, y);
                        assertThat(a == 0 || a == 255).as(file + " " + x + "," + y).isTrue();
                    }
                }
            }
        }
    }

    /** BetterHud trims each layer separately; the left trims differ only by the interior offset, which it keeps (xOffset). */
    @Test
    void firstOpaqueRowsAreKnownAndTheBackgroundStartsAtTheTop() {
        for (HudArt.Bar bar : bars()) {
            assertThat(HudArt.firstOpaqueRow(bar.bgFile())).as(bar.name()).isLessThanOrEqualTo(1);
            for (String file : fills(bar)) {
                int top = bar.interior().stream().mapToInt(HudArt.Rect::y0).min().orElseThrow();
                assertThat(HudArt.firstOpaqueRow(file)).as(file).isEqualTo(top);
            }
        }
    }

    /** Every PNG in the HUD resource folder (bars, variants, plate, transparent sprite). */
    static List<String> allHudPngs() throws IOException, URISyntaxException {
        Path dir = Path.of(HudArt.class.getResource(HudArt.DIR).toURI());
        try (Stream<Path> files = Files.list(dir)) {
            List<String> out = files.map(f -> f.getFileName().toString()).filter(n -> n.endsWith(".png")).sorted().toList();
            assertThat(out).isNotEmpty();
            return out;
        }
    }

    /**
     * Defringe (owner report 2026-10-22: pink/red fringe on the outlines): pixel art needs no semi-transparency, so every
     * pixel of every HUD PNG is fully opaque or fully transparent.
     */
    @Test
    void noHudPngHasASemiTransparentPixel() throws Exception {
        for (String file : allHudPngs()) {
            BufferedImage img = HudArt.image(file);
            for (int y = 0; y < img.getHeight(); y++) {
                for (int x = 0; x < img.getWidth(); x++) {
                    int a = alpha(img, x, y);
                    assertThat(a == 0 || a == 255).as(file + " semi-transparent at " + x + "," + y + " (alpha " + a + ")").isTrue();
                }
            }
        }
    }

    /**
     * The outer ring (opaque pixels next to a transparent pixel or the canvas edge) of every background and overlay is a
     * dark neutral colour: no reddish, pinkish or other tinted fringe. Fills are exempt (their edge is the fill colour
     * inside the frame).
     */
    @Test
    void outerRingOfEveryFrameIsDarkNeutral() throws Exception {
        for (String file : allHudPngs()) {
            if (file.contains("_fill")) {
                continue;
            }
            BufferedImage img = HudArt.image(file);
            for (int y = 0; y < img.getHeight(); y++) {
                for (int x = 0; x < img.getWidth(); x++) {
                    if (alpha(img, x, y) != 255 || !onOuterRing(img, x, y)) {
                        continue;
                    }
                    int argb = img.getRGB(x, y);
                    int r = (argb >> 16) & 0xFF;
                    int g = (argb >> 8) & 0xFF;
                    int b = argb & 0xFF;
                    int max = Math.max(r, Math.max(g, b));
                    int min = Math.min(r, Math.min(g, b));
                    assertThat(max <= 64 && max - min <= 12)
                        .as(file + " outer-ring pixel " + x + "," + y + " is (" + r + "," + g + "," + b + "), not dark neutral").isTrue();
                }
            }
        }
    }

    /** {@code "ring": "outline"} in every bar's layers.json: the whole frame (opaque, outside the interior) is pure black. */
    @Test
    void everyBarFrameIsPureBlack() {
        for (HudArt.Bar bar : bars()) {
            BufferedImage bg = HudArt.image(bar.bgFile());
            for (int y = 0; y < bar.height(); y++) {
                for (int x = 0; x < bar.width(); x++) {
                    if (!bar.interior(x, y) && alpha(bg, x, y) != 0) {
                        assertThat(bg.getRGB(x, y)).as(bar.bgFile() + " frame pixel " + x + "," + y).isEqualTo(0xFF000000);
                    }
                }
            }
        }
    }

    /** Each bar layer is quantized to a small palette ({@code "colors"} in layers.json), so stray hues cannot survive. */
    @Test
    void everyBarLayerHasASmallPalette() {
        for (HudArt.Bar bar : bars()) {
            List<String> files = new ArrayList<>(fills(bar));
            files.add(bar.bgFile());
            for (String file : files) {
                BufferedImage img = HudArt.image(file);
                Set<Integer> colours = new HashSet<>();
                for (int y = 0; y < img.getHeight(); y++) {
                    for (int x = 0; x < img.getWidth(); x++) {
                        if (alpha(img, x, y) != 0) {
                            colours.add(img.getRGB(x, y));
                        }
                    }
                }
                assertThat(colours.size()).as(file + " colours").isLessThanOrEqualTo(bar.name().contains("ambient") ? 33 : 7);
            }
        }
    }

    /**
     * The last pipeline step ({@code tools/images/hud_palette.py}): every HUD PNG is an indexed-colour image whose index 0
     * is the only transparent entry and whose other entries are fully opaque, with a small palette (bars 16, so they are
     * 4-bit; the thermometer's colour ramp 40, 8-bit).
     */
    @Test
    void everyHudPngIsASmallPaletteImage() throws Exception {
        for (String file : allHudPngs()) {
            BufferedImage img = HudArt.image(file);
            assertThat(img.getColorModel()).as(file + " colour model").isInstanceOf(IndexColorModel.class);
            IndexColorModel cm = (IndexColorModel) img.getColorModel();
            int max = file.startsWith("m2_clim_03_ambient_temp_bar") ? 40 : 16;
            int entries = plteEntries(HudArt.bytes(file)); // ImageIO pads an 8-bit palette to 256 entries
            assertThat(entries).as(file + " palette size").isBetween(1, max);
            assertThat(cm.getAlpha(0)).as(file + " index 0 transparent").isZero();
            for (int i = 1; i < entries; i++) {
                assertThat(cm.getAlpha(i)).as(file + " palette entry " + i + " opaque").isEqualTo(255);
            }
        }
    }

    /**
     * One palette per bar, same index order in every layer, and the layers are lossless: the fill
     * over the background reproduces the cleaned master {@code <name>_master.png} exactly.
     */
    @Test
    void layersShareThePaletteAndFillOverBackgroundIsTheMaster() {
        for (HudArt.Bar bar : bars()) {
            String masterFile = bar.name() + "_master.png";
            BufferedImage master = HudArt.image(masterFile);
            BufferedImage bg = HudArt.image(bar.bgFile());
            BufferedImage fill = HudArt.image(bar.fillFile());
            List<String> layers = new ArrayList<>(fills(bar));
            layers.add(bar.bgFile());
            IndexColorModel ref = (IndexColorModel) master.getColorModel();
            for (String file : layers) {
                IndexColorModel cm = (IndexColorModel) HudArt.image(file).getColorModel();
                for (int i = 0; i < Math.min(cm.getMapSize(), ref.getMapSize()); i++) {
                    assertThat(cm.getRGB(i)).as(file + " palette index " + i + " as in " + masterFile).isEqualTo(ref.getRGB(i));
                }
            }
            for (int y = 0; y < bar.height(); y++) {
                for (int x = 0; x < bar.width(); x++) {
                    int top = fill.getRGB(x, y);
                    int composite = (top >>> 24) != 0 ? top : bg.getRGB(x, y);
                    int expected = master.getRGB(x, y);
                    if ((expected >>> 24) == 0) {
                        assertThat(composite >>> 24).as(bar.name() + " transparent at " + x + "," + y).isZero();
                    } else {
                        assertThat(composite).as(bar.name() + " fill over bg at " + x + "," + y).isEqualTo(expected);
                    }
                }
            }
        }
    }

    /** The number of entries of the PNG's PLTE chunk. */
    static int plteEntries(byte[] png) {
        java.nio.ByteBuffer b = java.nio.ByteBuffer.wrap(png);
        int i = 8;
        while (i + 8 <= png.length) {
            int len = b.getInt(i);
            String type = new String(png, i + 4, 4, java.nio.charset.StandardCharsets.US_ASCII);
            if (type.equals("PLTE")) {
                return len / 3;
            }
            i += 12 + len;
        }
        throw new AssertionError("no PLTE chunk");
    }

    private static boolean onOuterRing(BufferedImage img, int x, int y) {
        int[][] n = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] d : n) {
            int a = x + d[0];
            int b = y + d[1];
            if (a < 0 || b < 0 || a >= img.getWidth() || b >= img.getHeight() || alpha(img, a, b) == 0) {
                return true;
            }
        }
        return false;
    }

    private static void assertSize(HudArt.Bar bar, String file) {
        BufferedImage img = HudArt.image(file);
        assertThat(img.getWidth()).as(file).isEqualTo(bar.width());
        assertThat(img.getHeight()).as(file).isEqualTo(bar.height());
    }

    private static int alpha(BufferedImage img, int x, int y) {
        return img.getRGB(x, y) >>> 24;
    }

    private static int light(int argb) {
        return ((argb >> 16) & 0xFF) + ((argb >> 8) & 0xFF) + (argb & 0xFF);
    }
}
