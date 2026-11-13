/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import javax.imageio.ImageIO;

/**
 * The HUD sprites ({@code plans/hud-layered-redesign.md}). Each bar is two pixel-aligned layers derived from one master
 * image by {@code tools/images/hud_layers.py}: {@code <name>_bg.png} (frame + empty interior) and {@code <name>_fill.png}
 * (interior only, transparent elsewhere), plus optional fill variants ({@code <name>_fill_low.png}). The spec the script
 * used ({@code <name>.layers.json}: canvas and interior mask) is read here too, so the datagen and its tests know the
 * mask. Single-layer images (the info plate) are plain PNGs.
 *
 * <p>Everything is a resource under {@link #DIR}; the bytes go into the pack unchanged. PAR-HUD-19: the state layers of
 * the health and thirst bars are derived here from those resources at datagen time, deterministically (no hand-drawn
 * pixels, as the mirrored plate): the frame ring recoloured ({@link #ring}: TFC's white highlight, TFC's red overheat
 * outline composited over our black frame at each stepped alpha) and the fill re-hued to TFC's surplus yellow
 * ({@link #rehued}).
 */
final class HudArt {
    static final String DIR = "/dev/tfp/datagen/hud/";

    /** One rectangle of the interior mask, {@code [x0, x1) x [y0, y1)} in canvas pixels. */
    record Rect(int x0, int y0, int x1, int y1) {
        boolean contains(int x, int y) {
            return x >= x0 && x < x1 && y >= y0 && y < y1;
        }
    }

    /** A two-layer bar: canvas size, interior mask and the names of its fill variants. */
    record Bar(String name, int width, int height, List<Rect> interior, List<String> variants) {
        boolean interior(int x, int y) {
            for (Rect r : interior) {
                if (r.contains(x, y)) {
                    return true;
                }
            }
            return false;
        }

        /** Leftmost and one past the rightmost interior column: the fill's clip range. */
        int interiorX0() {
            return interior.stream().mapToInt(Rect::x0).min().orElseThrow();
        }

        int interiorX1() {
            return interior.stream().mapToInt(Rect::x1).max().orElseThrow();
        }

        /** The width BetterHud splits the fill into (it trims the fill to the interior columns first). */
        int interiorWidth() {
            return interiorX1() - interiorX0();
        }

        String bgFile() {
            return name + "_bg.png";
        }

        String fillFile() {
            return name + "_fill.png";
        }

        String variantFile(String variant) {
            return name + "_fill_" + variant + ".png";
        }
    }

    private HudArt() {
    }

    /** The bar {@code name} from its {@code <name>.layers.json}. */
    static Bar bar(String name) {
        JsonObject o = JsonParser.parseString(new String(bytes(name + ".layers.json"), StandardCharsets.UTF_8))
            .getAsJsonObject();
        if (!name.equals(o.get("name").getAsString())) {
            throw new DatagenException(name + ".layers.json names " + o.get("name").getAsString());
        }
        JsonArray canvas = o.getAsJsonArray("canvas");
        List<Rect> interior = new ArrayList<>();
        for (JsonElement e : o.getAsJsonArray("interior")) {
            JsonArray r = e.getAsJsonArray();
            interior.add(new Rect(r.get(0).getAsInt(), r.get(1).getAsInt(), r.get(2).getAsInt(), r.get(3).getAsInt()));
        }
        List<String> variants = new ArrayList<>();
        if (o.has("variants")) {
            variants.addAll(o.getAsJsonObject("variants").keySet());
            variants.sort(null);
        }
        return new Bar(name, canvas.get(0).getAsInt(), canvas.get(1).getAsInt(), List.copyOf(interior), List.copyOf(variants));
    }

    /** The resource bytes of {@code file} (a file name under {@link #DIR}). */
    static byte[] bytes(String file) {
        try (InputStream in = HudArt.class.getResourceAsStream(DIR + file)) {
            if (in == null) {
                throw new DatagenException("missing HUD art resource " + DIR + file);
            }
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static BufferedImage image(String file) {
        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(bytes(file)));
            if (img == null) {
                throw new DatagenException("not a PNG: " + DIR + file);
            }
            return img;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** The name of the left-right mirrored copy of {@code file}. */
    static String mirroredFile(String file) {
        return file.substring(0, file.length() - ".png".length()) + "_mirrored.png";
    }

    /** {@code file} mirrored left-right, as PNG bytes (deterministic). */
    static byte[] mirrored(String file) {
        BufferedImage src = image(file);
        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < src.getHeight(); y++) {
            for (int x = 0; x < src.getWidth(); x++) {
                out.setRGB(src.getWidth() - 1 - x, y, src.getRGB(x, y));
            }
        }
        try {
            java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
            ImageIO.write(out, "png", bytes);
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** PAR-HUD-19: TFC {@code overlay.png} (0, 29): the health highlight outline, white. */
    static final int GLOW_RGB = 0xFFFFFF;
    /** PAR-HUD-19: TFC {@code overlay.png} (90, 29): the thirst overheat outline, {@code #820000}. */
    static final int OVERHEAT_RGB = 0x820000;
    /** PAR-HUD-19: TFC {@code overlay.png} (90, 9): the main colour of the yellow surplus bar, {@code #ffba28}. */
    static final int SURPLUS_RGB = 0xFFBA28;

    /** The health glow layer of {@code bar}: {@code <name>_glow.png}. */
    static String glowFile(String bar) {
        return bar + "_glow.png";
    }

    /** The yellow surplus fill of {@code bar}: {@code <name>_fill_surplus.png}. */
    static String surplusFile(String bar) {
        return bar + "_fill_surplus.png";
    }

    /** The overheat frame {@code level} (1..levels) of {@code bar}: {@code <name>_overheat_<level>.png}. */
    static String overheatFile(String bar, int level) {
        return bar + "_overheat_" + level + ".png";
    }

    /**
     * The frame ring of {@code bar} (every opaque pixel of its background outside the interior) in {@code rgb}, the rest
     * transparent, same canvas.
     */
    static BufferedImage ring(Bar bar, int rgb) {
        BufferedImage bg = image(bar.bgFile());
        BufferedImage out = new BufferedImage(bar.width(), bar.height(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < bar.height(); y++) {
            for (int x = 0; x < bar.width(); x++) {
                if (!bar.interior(x, y) && (bg.getRGB(x, y) >>> 24) != 0) {
                    out.setRGB(x, y, 0xFF000000 | rgb);
                }
            }
        }
        return out;
    }

    /**
     * TFC draws the overheat outline {@link #OVERHEAT_RGB} at alpha {@code level / levels} over a black frame; over our
     * pure black frame that composite is the outline colour scaled by the alpha, so each pre-tinted frame is opaque.
     */
    static int overheatRgb(int level, int levels) {
        double a = (double) level / levels;
        int r = (int) Math.round(((OVERHEAT_RGB >> 16) & 0xFF) * a);
        int g = (int) Math.round(((OVERHEAT_RGB >> 8) & 0xFF) * a);
        int b = (int) Math.round((OVERHEAT_RGB & 0xFF) * a);
        return (r << 16) | (g << 8) | b;
    }

    /**
     * {@code bar}'s fill with every pixel's hue set to the hue of {@code rgb} (saturation and brightness kept, so the
     * light, main and dark rows stay a ramp): the surplus fill in TFC's yellow.
     */
    static BufferedImage rehued(Bar bar, int rgb) {
        float hue = Color.RGBtoHSB((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, null)[0];
        BufferedImage fill = image(bar.fillFile());
        BufferedImage out = new BufferedImage(bar.width(), bar.height(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < bar.height(); y++) {
            for (int x = 0; x < bar.width(); x++) {
                int p = fill.getRGB(x, y);
                if ((p >>> 24) == 0) {
                    continue;
                }
                float[] hsb = Color.RGBtoHSB((p >> 16) & 0xFF, (p >> 8) & 0xFF, p & 0xFF, null);
                out.setRGB(x, y, 0xFF000000 | (Color.HSBtoRGB(hue, hsb[1], hsb[2]) & 0xFFFFFF));
            }
        }
        return out;
    }

    /**
     * {@code img} as an indexed-colour PNG (the art pipeline's format, {@code tools/images/hud_palette.py}): index 0 the
     * only transparent entry, then the opaque colours in ascending RGB order. Deterministic.
     */
    static byte[] indexedPng(BufferedImage img) {
        TreeSet<Integer> colours = new TreeSet<>();
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                int p = img.getRGB(x, y);
                if ((p >>> 24) != 0) {
                    if ((p >>> 24) != 0xFF) {
                        throw new DatagenException("semi-transparent pixel at " + x + "," + y);
                    }
                    colours.add(p & 0xFFFFFF);
                }
            }
        }
        List<Integer> palette = new ArrayList<>(colours);
        int size = palette.size() + 1;
        if (size > 256) {
            throw new DatagenException("more than 255 colours");
        }
        byte[] r = new byte[size];
        byte[] g = new byte[size];
        byte[] b = new byte[size];
        byte[] a = new byte[size];
        for (int i = 1; i < size; i++) {
            int c = palette.get(i - 1);
            r[i] = (byte) (c >> 16);
            g[i] = (byte) (c >> 8);
            b[i] = (byte) c;
            a[i] = (byte) 0xFF;
        }
        IndexColorModel cm = new IndexColorModel(8, size, r, g, b, a);
        BufferedImage out = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_BYTE_INDEXED, cm);
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                int p = img.getRGB(x, y);
                int index = (p >>> 24) == 0 ? 0 : palette.indexOf(p & 0xFFFFFF) + 1;
                out.getRaster().setSample(x, y, 0, index);
            }
        }
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            ImageIO.write(out, "png", bytes);
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** The first row with an opaque pixel of {@code img}. */
    static int firstOpaqueRow(BufferedImage img, String what) {
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                if ((img.getRGB(x, y) >>> 24) != 0) {
                    return y;
                }
            }
        }
        throw new DatagenException(what + " is fully transparent");
    }

    /**
     * The first row with an opaque pixel. BetterHud trims transparent rows before drawing and does not move the image
     * down by the trim, so a layer is placed at {@code bar y + firstOpaqueRow} to stay where the canvas puts it.
     */
    static int firstOpaqueRow(String file) {
        return firstOpaqueRow(image(file), file);
    }
}
