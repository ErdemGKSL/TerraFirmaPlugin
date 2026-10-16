/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import javax.imageio.ImageIO;

/**
 * Composites an ore overlay over its host rock texture ("source over", straight alpha) into one opaque PNG.
 *
 * <p>Done in Java with {@code javax.imageio} rather than a Pillow script: the JDK is already required, so CI
 * needs no Python environment, and the encoder is fixed by the pinned JDK, which keeps the bytes
 * deterministic. The flattened texture lets a vanilla client draw the ore with one cube instead of TFC's
 * two-layer {@code tfc:block/ore} model (half the faces, no cutout layer).
 */
final class OreFlattener {
    private OreFlattener() {
    }

    static byte[] flatten(byte[] basePng, byte[] overlayPng, String what) {
        BufferedImage base = read(basePng, what + " (base)");
        BufferedImage overlay = read(overlayPng, what + " (overlay)");
        if (base.getWidth() != overlay.getWidth() || base.getHeight() != overlay.getHeight()) {
            throw new DatagenException(what + ": base " + base.getWidth() + "x" + base.getHeight()
                + " and overlay " + overlay.getWidth() + "x" + overlay.getHeight() + " differ in size");
        }
        int w = base.getWidth();
        int h = base.getHeight();
        boolean opaque = true;
        int[] px = new int[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int c = over(overlay.getRGB(x, y), base.getRGB(x, y));
                px[y * w + x] = c;
                opaque &= (c >>> 24) == 0xFF;
            }
        }
        BufferedImage out = new BufferedImage(w, h, opaque ? BufferedImage.TYPE_INT_RGB : BufferedImage.TYPE_INT_ARGB);
        out.setRGB(0, 0, w, h, px, 0, w);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try {
            if (!ImageIO.write(out, "png", bytes)) {
                throw new DatagenException("no PNG writer available");
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return bytes.toByteArray();
    }

    /** Porter-Duff source-over of two non-premultiplied ARGB pixels, rounded to 8 bits. */
    static int over(int src, int dst) {
        int sa = src >>> 24;
        int da = dst >>> 24;
        if (sa == 0xFF || da == 0) {
            return src;
        }
        if (sa == 0) {
            return dst;
        }
        double a = sa / 255.0;
        double b = da / 255.0 * (1 - a);
        double outA = a + b;
        int r = channel(src >> 16, dst >> 16, a, b, outA);
        int g = channel(src >> 8, dst >> 8, a, b, outA);
        int bl = channel(src, dst, a, b, outA);
        return ((int) Math.round(outA * 255) << 24) | (r << 16) | (g << 8) | bl;
    }

    private static int channel(int s, int d, double a, double b, double outA) {
        return (int) Math.round(((s & 0xFF) * a + (d & 0xFF) * b) / outA);
    }

    private static BufferedImage read(byte[] png, String what) {
        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(png));
            if (img == null) {
                throw new DatagenException(what + " is not a readable PNG");
            }
            return img;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
