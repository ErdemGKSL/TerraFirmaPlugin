/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.gui;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * PAR-INFRA-03 {@code fw-background-art}: every GUI image the plugin draws. The datagen writes one PNG and one
 * CraftEngine {@code images:} entry per element; screen items add theirs here.
 */
public final class GuiImages {
    /**
     * The TFC thermometer indicator as a 52-frame strip ({@link ProgressFrames#thermometer}), placed where TFC's firepit
     * draws it: x 30, travel from y 25 to 80 ({@code 76 - 51} to {@code 76 + 5}) in TFC's screen coordinates. A screen
     * that moves TFC's art by {@code dy} declares its own image with {@code y + dy} over the same strip.
     */
    public static final GuiImage THERMOMETER = new GuiImage("tfp:gui/thermometer", 30, 76 - ProgressFrames.THERMOMETER_TRAVEL,
        ProgressFrames.THERMOMETER_WIDTH, ProgressFrames.THERMOMETER_FRAME_HEIGHT, 4, 13);

    /** The empty item model button slots show (the art under the slot is the button). */
    public static final String BLANK_MODEL = "tfp:gui/blank";

    private GuiImages() {
    }

    /** All images, ids unique. */
    public static List<GuiImage> all() {
        List<GuiImage> out = new ArrayList<>(SampleScreen.LAYERS.images());
        out.add(THERMOMETER);
        Set<String> ids = new HashSet<>();
        for (GuiImage i : out) {
            if (!ids.add(i.id())) {
                throw new IllegalStateException("duplicate GUI image " + i.id());
            }
        }
        return List.copyOf(out);
    }
}
