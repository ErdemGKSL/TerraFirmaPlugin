/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/recipes/outputs/DyeLeatherModifier.java @ c3b4d9536 (which calls
 * vanilla DyedItemColor.applyDyes; the colour mix below is that method's arithmetic)
 */
package dev.tfp.paper.recipe;

import java.util.List;
import java.util.OptionalInt;

/** Vanilla's dye mix for dyeable items ({@code DyedItemColor.applyDyes}), as pure integer arithmetic. */
public final class DyeMixing {
    private DyeMixing() {
    }

    /**
     * @param existing the item's current dye colour (RGB), if dyed
     * @param dyes     the dye colours (each {@code DyeColor.getTextureDiffuseColor}, RGB)
     * @return the new RGB colour
     */
    public static int mix(OptionalInt existing, List<Integer> dyes) {
        int r = 0;
        int g = 0;
        int b = 0;
        int intensity = 0;
        int count = 0;
        if (existing.isPresent()) {
            int c = existing.getAsInt();
            int cr = (c >> 16) & 0xFF;
            int cg = (c >> 8) & 0xFF;
            int cb = c & 0xFF;
            intensity += Math.max(cr, Math.max(cg, cb));
            r += cr;
            g += cg;
            b += cb;
            count++;
        }
        for (int c : dyes) {
            int cr = (c >> 16) & 0xFF;
            int cg = (c >> 8) & 0xFF;
            int cb = c & 0xFF;
            intensity += Math.max(cr, Math.max(cg, cb));
            r += cr;
            g += cg;
            b += cb;
            count++;
        }
        if (count == 0) {
            throw new IllegalArgumentException("no colour to mix");
        }
        int ar = r / count;
        int ag = g / count;
        int ab = b / count;
        float avgIntensity = (float) intensity / (float) count;
        float max = (float) Math.max(ar, Math.max(ag, ab));
        ar = (int) ((float) ar * avgIntensity / max);
        ag = (int) ((float) ag * avgIntensity / max);
        ab = (int) ((float) ab * avgIntensity / max);
        return (ar << 16) | (ag << 8) | ab;
    }
}
