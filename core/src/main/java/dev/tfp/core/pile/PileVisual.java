/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/client/model/IngotPileBlockModel.java @ c3b4d9536 (one trapezoid per ingot, textured
 *       by the metal of that ingot)
 */
package dev.tfp.core.pile;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What to draw for a pile: the display parts for the metals of its ingots, bottom to top. TFC draws one textured cuboid per
 * ingot; we draw one item display per ingot, except that a full layer of one metal is one display with the whole layer in its
 * model (a full pile of one metal is at most 8 displays, not 64). Pure and thread-safe.
 */
public final class PileVisual {
    private PileVisual() {}

    /** The model of a part: one ingot, or a full layer of {@link PileKind#perLayer()} ingots of one metal. */
    public enum Variant {
        SINGLE, LAYER
    }

    /**
     * One display.
     *
     * @param variant the model variant
     * @param metal   the metal of the part ({@link #UNKNOWN} for a stack that is no known metal)
     * @param layer   the 1-based layer
     * @param index   the 0-based index of its (first) ingot
     * @param at      where it lies
     */
    public record Part(Variant variant, String metal, int layer, int index, PileLayout.Placement at) {
        /** A stable key of the part, to match displays that are already in the world. */
        public String signature() {
            return variant.name().charAt(0) + ":" + index + ":" + metal;
        }
    }

    /** TFC {@code MetalItem.unknown()}: the metal of anything that melts into no known metal. */
    public static final String UNKNOWN = "unknown";

    private static final Pattern INGOT_ID = Pattern.compile("tfp:metal/(?:double_)?ingot/([a-z0-9_]+)");

    /** The metal of a custom item id ({@code tfp:metal/ingot/copper}, {@code tfp:metal/double_ingot/copper}), else {@link #UNKNOWN}. */
    public static String metalOf(String customItemId) {
        if (customItemId == null) {
            return UNKNOWN;
        }
        Matcher m = INGOT_ID.matcher(customItemId);
        return m.matches() ? m.group(1) : UNKNOWN;
    }

    /**
     * The parts of a pile.
     *
     * @param metals the metal of each ingot, bottom to top; {@code 1..capacity} entries
     */
    public static List<Part> plan(PileKind kind, List<String> metals) {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(metals, "metals");
        if (metals.isEmpty() || metals.size() > kind.capacity()) {
            throw new IllegalArgumentException("ingot count out of range for " + kind + ": " + metals.size());
        }
        int per = kind.perLayer();
        List<Part> parts = new ArrayList<>();
        for (int first = 0; first < metals.size(); first += per) {
            int end = Math.min(first + per, metals.size());
            int layer = first / per + 1;
            String metal = metals.get(first);
            boolean uniform = end - first == per;
            for (int i = first + 1; uniform && i < end; i++) {
                uniform = metal.equals(metals.get(i));
            }
            if (uniform) {
                parts.add(new Part(Variant.LAYER, metal, layer, first, PileLayout.ofLayer(kind, layer)));
            } else {
                for (int i = first; i < end; i++) {
                    parts.add(new Part(Variant.SINGLE, metals.get(i), layer, i, PileLayout.of(kind, i)));
                }
            }
        }
        return List.copyOf(parts);
    }
}
