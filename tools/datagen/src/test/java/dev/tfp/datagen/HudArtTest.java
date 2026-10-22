/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
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
