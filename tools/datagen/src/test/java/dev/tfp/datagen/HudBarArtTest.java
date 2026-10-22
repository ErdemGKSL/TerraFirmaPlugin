/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class HudBarArtTest {

    @Test
    void everySpriteIsTfcWidthAndHealthIsTheThickOne() {
        for (var e : HudBarArt.SLICES.entrySet()) {
            BufferedImage img = HudBarArt.image(e.getKey());
            assertThat(img.getWidth()).as(e.getKey()).isEqualTo(81);
            int expected = e.getKey().contains("health") ? 9 : 5;
            assertThat(img.getHeight()).as(e.getKey()).isEqualTo(expected);
            assertThat(opaqueRows(img)).as(e.getKey() + " has no empty row for BetterHud to trim").isEqualTo(expected);
        }
    }

    @Test
    void alphaIsOnlyFullOrNone() {
        for (String file : HudBarArt.SLICES.keySet()) {
            BufferedImage img = HudBarArt.image(file);
            for (int y = 0; y < img.getHeight(); y++) {
                for (int x = 0; x < img.getWidth(); x++) {
                    int a = img.getRGB(x, y) >>> 24;
                    assertThat(a == 0 || a == 255).as(file + " " + x + "," + y).isTrue();
                }
            }
        }
    }

    /** Colour language: red health, orange hunger, blue thirst, light-blue air. Sampled in the middle of the fill. */
    @Test
    void fillsFollowTheColourLanguage() {
        assertThat(hue("m3_surv_06_health_bar_fill.png")).isBetween(-15f, 15f);
        assertThat(hue("m3_surv_06_hunger_bar_fill.png")).isBetween(20f, 45f);
        assertThat(hue("m3_surv_06_thirst_bar_fill.png")).isBetween(180f, 225f);
        assertThat(hue("m3_surv_06_air_bar_fill.png")).isBetween(180f, 225f);
        assertThat(brightness("m3_surv_06_air_bar_fill.png")).isGreaterThan(brightness("m3_surv_06_thirst_bar_fill.png"));
        assertThat(brightness("m3_surv_06_health_bar_bg.png")).isLessThan(brightness("m3_surv_06_health_bar_fill.png"));
    }

    @Test
    void outlineStaysBlack() {
        BufferedImage hunger = HudBarArt.image("m3_surv_06_hunger_bar_fill.png");
        BufferedImage air = HudBarArt.image("m3_surv_06_air_bar_fill.png");
        assertThat(hunger.getRGB(40, 0) & 0xFFFFFF).isZero();
        assertThat(air.getRGB(40, 4) & 0xFFFFFF).isZero();
    }

    @Test
    void pngIsDeterministic() {
        for (String file : HudBarArt.SLICES.keySet()) {
            assertThat(HudBarArt.png(file)).as(file).isEqualTo(HudBarArt.png(file));
        }
    }

    @Test
    void copiedSheetEqualsThePinnedCheckout() throws IOException {
        Path checkout = Path.of(System.getProperty("tfc.checkout", "/opt/tfc"));
        Path sheet = checkout.resolve(HudBarArt.TFC_OVERLAY);
        Assumptions.assumeTrue(Files.isRegularFile(sheet), "no TFC checkout at " + checkout);
        Assumptions.assumeTrue(TfcCheckout.PINNED_COMMIT.equals(TfcCheckout.resolveHead(checkout)), "TFC checkout not at the pin");
        try (InputStream in = HudBarArt.class.getResourceAsStream(HudBarArt.OVERLAY)) {
            assertThat(in).isNotNull();
            assertThat(in.readAllBytes()).isEqualTo(Files.readAllBytes(sheet));
        }
    }

    private static int opaqueRows(BufferedImage img) {
        int rows = 0;
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                if ((img.getRGB(x, y) >>> 24) != 0) {
                    rows++;
                    break;
                }
            }
        }
        return rows;
    }

    private static float[] hsb(String file) {
        BufferedImage img = HudBarArt.image(file);
        int rgb = img.getRGB(40, img.getHeight() / 2);
        return Color.RGBtoHSB((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, null);
    }

    private static float hue(String file) {
        float h = hsb(file)[0] * 360f;
        return h > 180f && file.contains("health") ? h - 360f : h;
    }

    private static float brightness(String file) {
        return hsb(file)[2];
    }
}
