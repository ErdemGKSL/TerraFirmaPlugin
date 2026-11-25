/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/resources/assets/tfc/textures/item/wood/sign_head.png @ c3b4d9536 (and the other sign_head, hanging_sign_head and overlay pngs,
 *       textures/item/wood/sign/*.png, textures/item/metal/hanging_sign/*.png, textures/color_palettes/wood/{planks/palette,plank_items/*}.png,
 *       assets/minecraft/atlases/blocks.json: the paletted_permutations of the sign heads)
 */
package dev.tfp.datagen.sign;

import dev.tfp.datagen.DatagenException;
import dev.tfp.datagen.TfcCheckout;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import javax.imageio.ImageIO;

/**
 * PAR-DECO-03: the inventory icons of the signs. TFC tints the grey sign heads per wood at atlas stitching (a
 * {@code paletted_permutations} source: each colour of {@code planks/palette} becomes the same index of
 * {@code plank_items/<wood>}) and stacks the layers in the item model; a vanilla client has no such atlas source, so the layers
 * are baked into one 16 x 16 PNG per item here.
 */
final class SignIcons {
    private static final String TEX = TfcCheckout.ASSETS + "textures/";

    private SignIcons() {
    }

    /** The standing sign item: the post (layer 0), the tinted head (layer 1) and the writing (layer 2). */
    static byte[] standing(TfcCheckout tfc, String wood) {
        return stack(load(tfc, "item/wood/sign/" + wood), tint(tfc, "item/wood/sign_head", wood),
            load(tfc, "item/wood/sign_head_overlay"));
    }

    /** The hanging sign item: the tinted head (layer 0), its overlay (layer 1) and the metal hanger (layer 2). */
    static byte[] hanging(TfcCheckout tfc, String wood, String metal) {
        return stack(tint(tfc, "item/wood/hanging_sign_head", wood), load(tfc, "item/wood/hanging_sign_head_overlay"),
            load(tfc, "item/metal/hanging_sign/" + metal));
    }

    static BufferedImage load(TfcCheckout tfc, String rel) {
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

    /** TFC's {@code paletted_permutations}: every pixel that is a colour of the key palette takes the permutation's colour at the same index. */
    static BufferedImage tint(TfcCheckout tfc, String rel, String wood) {
        BufferedImage key = load(tfc, "color_palettes/wood/planks/palette");
        BufferedImage perm = load(tfc, "color_palettes/wood/plank_items/" + wood);
        if (key.getWidth() != perm.getWidth()) {
            throw new DatagenException("the plank palette and plank_items/" + wood + " differ in length");
        }
        Map<Integer, Integer> map = new HashMap<>();
        for (int i = 0; i < key.getWidth(); i++) {
            map.put(key.getRGB(i, 0), perm.getRGB(i, 0));
        }
        BufferedImage img = load(tfc, rel);
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                int argb = img.getRGB(x, y);
                if ((argb >>> 24) != 0 && map.containsKey(argb)) {
                    img.setRGB(x, y, map.get(argb));
                }
            }
        }
        return img;
    }

    private static byte[] stack(BufferedImage... layers) {
        BufferedImage out = new BufferedImage(layers[0].getWidth(), layers[0].getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (BufferedImage l : layers) {
            for (int y = 0; y < out.getHeight(); y++) {
                for (int x = 0; x < out.getWidth(); x++) {
                    int a = l.getRGB(x, y);
                    if ((a >>> 24) != 0) {
                        out.setRGB(x, y, a); // the item layers are opaque or fully transparent
                    }
                }
            }
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try {
            ImageIO.write(out, "png", bytes);
        } catch (IOException e) {
            throw new DatagenException("cannot encode a sign icon: " + e.getMessage());
        }
        return bytes.toByteArray();
    }
}
