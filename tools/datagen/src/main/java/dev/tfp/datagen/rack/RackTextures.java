/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/resources/assets/minecraft/atlases/blocks.json @ c3b4d9536 (paletted_permutations of the bookshelf top and side)
 */
package dev.tfp.datagen.rack;

import dev.tfp.datagen.DatagenException;
import dev.tfp.datagen.TfcCheckout;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;

/**
 * PAR-DECO-05: TFC tints the grey bookshelf {@code top.png} and {@code side.png} per wood at atlas stitching
 * ({@code paletted_permutations} with the key {@code color_palettes/wood/planks/palette} and the permutation
 * {@code color_palettes/wood/planks/<wood>}); a vanilla client has no such atlas source, so the tinted PNGs are baked here.
 */
final class RackTextures {
    private static final String TEX = TfcCheckout.ASSETS + "textures/";
    private static final Pattern TINTED = Pattern.compile("block/wood/bookshelf/(top|side)_([a-z_]+)");

    private RackTextures() {
    }

    /** The tinted PNG of a texture the atlas builds ({@code block/wood/bookshelf/top_oak}), or null for any other path. */
    static byte[] tinted(TfcCheckout tfc, String rel) {
        Matcher m = TINTED.matcher(rel);
        if (!m.matches()) {
            return null;
        }
        BufferedImage key = load(tfc, "color_palettes/wood/planks/palette");
        BufferedImage perm = load(tfc, "color_palettes/wood/planks/" + m.group(2));
        if (key.getWidth() != perm.getWidth()) {
            throw new DatagenException("the plank palette and planks/" + m.group(2) + " differ in length");
        }
        Map<Integer, Integer> map = new HashMap<>();
        for (int i = 0; i < key.getWidth(); i++) {
            map.put(key.getRGB(i, 0), perm.getRGB(i, 0));
        }
        BufferedImage img = load(tfc, "block/wood/bookshelf/" + m.group(1));
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                int argb = img.getRGB(x, y);
                if ((argb >>> 24) != 0 && map.containsKey(argb)) {
                    img.setRGB(x, y, map.get(argb));
                }
            }
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try {
            ImageIO.write(img, "png", bytes);
        } catch (IOException e) {
            throw new DatagenException("cannot encode " + rel + ": " + e.getMessage());
        }
        return bytes.toByteArray();
    }

    private static BufferedImage load(TfcCheckout tfc, String rel) {
        String path = TEX + rel + ".png";
        if (!tfc.exists(path)) {
            throw new DatagenException("TFC has no texture " + rel);
        }
        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(tfc.read(path)));
            BufferedImage argb = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_ARGB);
            argb.getGraphics().drawImage(img, 0, 0, null);
            return argb;
        } catch (IOException e) {
            throw new DatagenException("cannot read " + path + ": " + e.getMessage());
        }
    }
}
