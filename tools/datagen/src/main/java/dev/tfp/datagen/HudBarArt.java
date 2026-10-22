/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft)
 * src/main/java/net/dries007/tfc/client/IngameOverlays.java (the blit rectangles of renderHealthBar, renderFood and
 * renderThirst) @ commit c3b4d9536b82e5a9746dd793c586720a74108057.
 */
package dev.tfp.datagen;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.imageio.ImageIO;

/**
 * The HUD bar sprites, sliced from TFC's own overlay sheet ({@code textures/gui/icons/overlay.png}, copied as the
 * resource {@link #OVERLAY}) at the rectangles TFC's {@code IngameOverlays} blits, so the bars look like TFC's: one
 * 81 px wide family with a black outline, health 9 px tall (the "850/850" text fits inside it), the right-hand bars
 * 5 px tall.
 *
 * <p>Two bars are not in TFC's sheet and are derived by a deterministic recolour (no new art):
 * <ul>
 *   <li>hunger: TFC's food bar is green; ours is TFC's food bar with the hue turned to orange (keeps saturation,
 *       brightness and the black outline). Divergence: the owner's colour language (orange hunger), and a green bar
 *       right above the green vanilla XP bar read as one row in the in-game review.</li>
 *   <li>air: TFC keeps the vanilla bubbles; ours is TFC's thirst bar lightened towards white (light blue), so it reads
 *       as a different bar from thirst.</li>
 * </ul>
 */
final class HudBarArt {
    /** The copied TFC sheet (see {@code hud/SOURCES.txt}). */
    static final String OVERLAY = "/dev/tfp/datagen/hud/tfc-overlay.png";
    /** The sheet's path in the TFC checkout. */
    static final String TFC_OVERLAY = "src/main/resources/assets/tfc/textures/gui/icons/overlay.png";
    /** Every bar is 81 px wide (TFC). */
    static final int WIDTH = 81;
    /** TFC's health bar height; the health text (7 px glyphs) sits inside it. */
    static final int HEALTH_HEIGHT = 9;
    /** TFC's food and thirst bar height; also the air bar's. */
    static final int BAR_HEIGHT = 5;
    /** Hunger hue (orange), 0..1. */
    static final float HUNGER_HUE = 30f / 360f;
    /** How far the air bar's colours move towards white, 0..1. */
    static final float AIR_LIGHTEN = 0.45f;

    enum Tint { NONE, ORANGE, LIGHT }

    /** One sprite: the rectangle of the TFC sheet and how it is recoloured. */
    record Slice(String file, int u, int v, int w, int h, Tint tint) {
    }

    /** The sprites by file name, in the names {@link HudLayouts} points its images at. */
    static final Map<String, Slice> SLICES = slices();

    private HudBarArt() {
    }

    private static Map<String, Slice> slices() {
        Map<String, Slice> s = new LinkedHashMap<>();
        put(s, "m3_surv_06_health_bar_bg.png", 0, 0, HEALTH_HEIGHT, Tint.NONE);
        put(s, "m3_surv_06_health_bar_fill.png", 0, 9, HEALTH_HEIGHT, Tint.NONE);
        put(s, "m3_surv_06_hunger_bar_bg.png", 0, 19, BAR_HEIGHT, Tint.ORANGE);
        put(s, "m3_surv_06_hunger_bar_fill.png", 0, 24, BAR_HEIGHT, Tint.ORANGE);
        put(s, "m3_surv_06_thirst_bar_bg.png", 90, 19, BAR_HEIGHT, Tint.NONE);
        put(s, "m3_surv_06_thirst_bar_fill.png", 90, 24, BAR_HEIGHT, Tint.NONE);
        put(s, "m3_surv_06_air_bar_bg.png", 90, 19, BAR_HEIGHT, Tint.NONE);
        put(s, "m3_surv_06_air_bar_fill.png", 90, 24, BAR_HEIGHT, Tint.LIGHT);
        return Map.copyOf(s);
    }

    private static void put(Map<String, Slice> s, String file, int u, int v, int h, Tint tint) {
        s.put(file, new Slice(file, u, v, WIDTH, h, tint));
    }

    /** The PNG bytes of the sprite {@code file} (a key of {@link #SLICES}). Deterministic. */
    static byte[] png(String file) {
        Slice s = SLICES.get(file);
        if (s == null) {
            throw new DatagenException("no HUD bar slice for " + file);
        }
        return encode(image(sheet(), s));
    }

    /** The sprite as an image (for tests). */
    static BufferedImage image(String file) {
        return image(sheet(), SLICES.get(file));
    }

    static BufferedImage sheet() {
        try (InputStream in = HudBarArt.class.getResourceAsStream(OVERLAY)) {
            if (in == null) {
                throw new DatagenException("missing datagen resource " + OVERLAY);
            }
            BufferedImage img = ImageIO.read(in);
            if (img == null) {
                throw new DatagenException("not a PNG: " + OVERLAY);
            }
            return img;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static BufferedImage image(BufferedImage sheet, Slice s) {
        BufferedImage out = new BufferedImage(s.w(), s.h(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < s.h(); y++) {
            for (int x = 0; x < s.w(); x++) {
                out.setRGB(x, y, tint(sheet.getRGB(s.u() + x, s.v() + y), s.tint()));
            }
        }
        return out;
    }

    /** Recolours one ARGB pixel. Transparent pixels and the black outline stay as they are. */
    static int tint(int argb, Tint tint) {
        int a = argb >>> 24;
        if (a == 0) {
            return 0;
        }
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        return switch (tint) {
            case NONE -> argb;
            case ORANGE -> {
                float[] hsb = Color.RGBtoHSB(r, g, b, null);
                yield (a << 24) | (Color.HSBtoRGB(HUNGER_HUE, hsb[1], hsb[2]) & 0xFFFFFF);
            }
            case LIGHT -> {
                if (Math.max(r, Math.max(g, b)) < 16) {
                    yield argb; // the outline
                }
                yield (a << 24) | (lighten(r) << 16) | (lighten(g) << 8) | lighten(b);
            }
        };
    }

    private static int lighten(int c) {
        return Math.round(c + (255 - c) * AIR_LIGHTEN);
    }

    private static byte[] encode(BufferedImage img) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            if (!ImageIO.write(img, "png", out)) {
                throw new DatagenException("no PNG writer");
            }
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
