/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.gui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * PAR-INFRA-03 {@code fw-overlay-layers}: a screen's background and the overlays drawn over it (bars, markers, button
 * states), in a fixed order. Later glyphs are drawn over earlier ones, so the order is: the background, then the layers
 * by ascending {@link Layer#z}, ties in declaration order. TFC draws the same pieces with {@code blit} in
 * {@code renderBg} and the widgets after it.
 *
 * @param background the full-window art, frame 0 (null for a screen with only overlays)
 * @param layers     the overlays
 */
public record GuiLayers(GuiImage background, List<Layer> layers) {
    public GuiLayers {
        List<Layer> sorted = new ArrayList<>(layers);
        Set<String> keys = new HashSet<>();
        for (Layer l : sorted) {
            if (!keys.add(l.key())) {
                throw new IllegalArgumentException("duplicate overlay key " + l.key());
            }
        }
        sorted.sort(Comparator.comparingInt(Layer::z)); // stable: ties keep declaration order
        layers = List.copyOf(sorted);
    }

    /**
     * One overlay.
     *
     * @param key   the name a session sets the frame with
     * @param z     draw order (higher is drawn later, on top)
     * @param image the image whose frames this overlay shows
     */
    public record Layer(String key, int z, GuiImage image) {
    }

    /**
     * The title glyphs with {@code frames} (overlay key to frame; a missing key or a negative frame hides the overlay),
     * ending back at the label position.
     */
    public TitleGlyphs compose(Map<String, Integer> frames) {
        for (String k : frames.keySet()) {
            if (layers.stream().noneMatch(l -> l.key().equals(k))) {
                throw new IllegalArgumentException("unknown overlay " + k);
            }
        }
        TitleGlyphs.Builder b = TitleGlyphs.builder();
        if (background != null) {
            b.draw(background, 0);
        }
        for (Layer l : layers) {
            Integer f = frames.get(l.key());
            if (f != null && f >= 0) {
                b.draw(l.image(), f);
            }
        }
        return b.returnToLabel().build();
    }

    /** Every image of this screen (background first), for datagen checks. */
    public List<GuiImage> images() {
        List<GuiImage> out = new ArrayList<>();
        if (background != null) {
            out.add(background);
        }
        layers.forEach(l -> out.add(l.image()));
        return List.copyOf(out);
    }
}
