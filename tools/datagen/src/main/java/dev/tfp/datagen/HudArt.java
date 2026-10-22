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
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;

/**
 * The HUD sprites ({@code plans/hud-layered-redesign.md}). Each bar is two pixel-aligned layers derived from one master
 * image by {@code tools/images/hud_layers.py}: {@code <name>_bg.png} (frame + empty interior) and {@code <name>_fill.png}
 * (interior only, transparent elsewhere), plus optional fill variants ({@code <name>_fill_low.png}). The spec the script
 * used ({@code <name>.layers.json}: canvas and interior mask) is read here too, so the datagen and its tests know the
 * mask. Single-layer images (the info plate) are plain PNGs.
 *
 * <p>Everything is a resource under {@link #DIR}; the bytes go into the pack unchanged.
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

    /**
     * The first row with an opaque pixel. BetterHud trims transparent rows before drawing and does not move the image
     * down by the trim, so a layer is placed at {@code bar y + firstOpaqueRow} to stay where the canvas puts it.
     */
    static int firstOpaqueRow(String file) {
        BufferedImage img = image(file);
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                if ((img.getRGB(x, y) >>> 24) != 0) {
                    return y;
                }
            }
        }
        throw new DatagenException(file + " is fully transparent");
    }
}
