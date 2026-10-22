/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/client/screen/FirepitScreen.java @ c3b4d9536 (renderBg: the thermometer
 *       indicator at 76 - min(51, temp), 15 x 5, only when temp > 0),
 *       src/main/java/net/dries007/tfc/common/component/heat/Heat.java @ c3b4d9536 (scaleTemperatureForGui)
 */
package dev.tfp.core.gui;

import dev.tfp.core.heat.HeatLevel;

/**
 * PAR-INFRA-03 {@code fw-progress-bars}: the frame index a bar or marker overlay shows. A title glyph cannot be clipped
 * or moved at draw time, so every bar state is one pre-drawn frame of a grid image (datagen draws the frames from TFC's
 * sprite) and the server picks the frame.
 */
public final class ProgressFrames {
    /** TFC's thermometer travel: the indicator moves up to 51 pixels. */
    public static final int THERMOMETER_TRAVEL = 51;
    /** The thermometer indicator sprite, 15 x 5 ({@code FirepitScreen.renderBg}). */
    public static final int THERMOMETER_WIDTH = 15;
    public static final int THERMOMETER_HEIGHT = 5;
    /** Frames of a thermometer strip: 0 (no indicator, TFC draws nothing at 0) and 1 to 51. */
    public static final int THERMOMETER_FRAMES = THERMOMETER_TRAVEL + 1;
    /** Height of one thermometer frame: the travel plus the sprite. */
    public static final int THERMOMETER_FRAME_HEIGHT = THERMOMETER_TRAVEL + THERMOMETER_HEIGHT;

    private ProgressFrames() {
    }

    /**
     * The thermometer frame for {@code temperature} (degrees C): {@code Heat.scaleTemperatureForGui}, so frame {@code k}
     * draws the indicator {@code k} pixels above the bottom of its travel, and frame 0 draws nothing.
     */
    public static int thermometer(float temperature) {
        return Math.min(THERMOMETER_TRAVEL, HeatLevel.scaleForGui(temperature));
    }

    /** The y of the indicator's top inside a thermometer frame ({@code 51 - k}), for frames 1 to 51. */
    public static int thermometerOffset(int frame) {
        if (frame < 1 || frame > THERMOMETER_TRAVEL) {
            throw new IllegalArgumentException("thermometer frame " + frame + " draws no indicator");
        }
        return THERMOMETER_TRAVEL - frame;
    }

    /**
     * A filling bar of {@code frames} frames (frame 0 empty, the last full): {@code floor(value / max * (frames - 1))},
     * clamped. TFC's progress arrows and fluid levels scale the same way ({@code (int) (size * fraction)}).
     */
    public static int fill(double value, double max, int frames) {
        if (frames < 2) {
            throw new IllegalArgumentException("a bar needs at least 2 frames, not " + frames);
        }
        if (!(max > 0) || !(value > 0)) {
            return 0;
        }
        int f = (int) (value / max * (frames - 1));
        return Math.max(0, Math.min(frames - 1, f));
    }

    /** The pixels of a {@code size}-pixel bar shown at frame {@code frame} of {@code frames}. */
    public static int fillPixels(int frame, int frames, int size) {
        if (frame < 0 || frame >= frames) {
            throw new IndexOutOfBoundsException("frame " + frame + " of " + frames);
        }
        return frame * size / (frames - 1);
    }
}
