/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen.rendered;

import dev.tfp.datagen.DatagenException;
import dev.tfp.datagen.TfcCheckout;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The cracked variants of the ore face overlays (PAR-INFRA-17, consumed by PAR-INFRA-15/16). Vanilla draws the mining
 * cracks on the host rock face, under the 0.01 overlay quad, so the ore specks would hide them. While a block is mined the
 * overlay runtime swaps each face overlay of that block to the variant of the current stage: a CraftEngine item whose
 * model is a {@code minecraft:composite} of the overlay quad and the crack quad {@code tfp:item/render/overlay_crack_<n>}
 * (the vanilla texture {@code minecraft:block/destroy_stage_<n>} on the same plane, a hair in front, so nothing is copied).
 *
 * <p>The crack quad has the geometry of the overlay quad of spike S1 (PAR-WORLD-17): one 16 x 16 face in the
 * {@code z = 8} plane facing {@code -z} (the {@code item_display} renderer turns item models 180 degrees about y), shade
 * and ambient occlusion off. It sits 0.02 model pixels in front of the overlay so the two never z-fight.
 */
public final class CrackedOverlays {
    /** TFC's overlay textures: one per ore and grade, {@code tfc:block/ore/<name>} (53 at the pinned commit). */
    public static final String TFC_OVERLAYS = "src/main/resources/assets/tfc/textures/block/ore";
    static final String CRACK_Z = "7.98";

    private CrackedOverlays() {
    }

    /** {@code tfp:item/render/overlay_crack_<n>}. */
    public static String crackQuad(int n) {
        RenderedTemplates.crackTexture(n);
        return "tfp:item/render/overlay_crack_" + n;
    }

    /** The model JSON of the crack quad of stage {@code n}. */
    public static String crackQuadModel(int n) {
        String tex = RenderedTemplates.crackTexture(n);
        return "{\n"
            + "  \"ambientocclusion\": false,\n"
            + "  \"textures\": {\"crack\": \"" + tex + "\", \"particle\": \"" + tex + "\"},\n"
            + "  \"elements\": [\n"
            + "    {\"from\": [0, 0, " + CRACK_Z + "], \"to\": [16, 16, " + CRACK_Z + "], \"shade\": false,\n"
            + "     \"faces\": {\"north\": {\"uv\": [0, 0, 16, 16], \"texture\": \"#crack\"}}}\n"
            + "  ]\n"
            + "}\n";
    }

    /** The CraftEngine item id of the cracked variant of {@code overlay} (a {@code tfp:} overlay item) at stage {@code n}. */
    public static String variantItem(String overlay, int n) {
        RenderedTemplates.crackTexture(n);
        if (!overlay.matches("tfp:[a-z0-9_/]+")) {
            throw new DatagenException("overlay item " + overlay + " is not tfp:<path>");
        }
        return overlay + "/crack_" + n;
    }

    /** The CraftEngine item {@code model:} of the cracked variant: the overlay model plus the crack quad. */
    public static Map<String, Object> variantModel(String overlayModel, int n) {
        Map<String, Object> overlay = new LinkedHashMap<>();
        overlay.put("type", "minecraft:model");
        overlay.put("path", overlayModel);
        Map<String, Object> crack = new LinkedHashMap<>();
        crack.put("type", "minecraft:model");
        crack.put("path", crackQuad(n));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", "minecraft:composite");
        m.put("models", List.of(overlay, crack));
        return m;
    }

    /** The ten cracked variants of one overlay, in stage order: item id to its CraftEngine item entry. */
    public static Map<String, Object> variants(String overlayItem, String overlayModel) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (int n = 0; n < RenderedTemplates.STAGES; n++) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("material", "paper");
            item.put("model", variantModel(overlayModel, n));
            out.put(variantItem(overlayItem, n), item);
        }
        return out;
    }

    /** TFC's overlay names (texture file names without {@code .png}), sorted. */
    public static List<String> tfcOverlays(TfcCheckout tfc) {
        List<String> names = new ArrayList<>();
        for (String f : tfc.list(TFC_OVERLAYS)) {
            String name = f.substring(f.lastIndexOf('/') + 1);
            if (name.endsWith(".png") && f.equals(TFC_OVERLAYS + "/" + name)) {
                names.add(name.substring(0, name.length() - 4));
            }
        }
        names.sort(null);
        return List.copyOf(names);
    }
}
