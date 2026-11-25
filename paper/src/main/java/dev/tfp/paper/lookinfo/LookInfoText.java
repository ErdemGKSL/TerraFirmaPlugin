/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/util/tooltip/BlockEntityTooltips.java @ c3b4d9536 (CROP; the Jade readout, NP-UI-01 substitute)
 */
package dev.tfp.paper.lookinfo;

import dev.tfp.core.crop.CropPlant;
import java.util.List;

/**
 * The text lines of the look-at block readout (PAR-DATA-12), pure. One message key and its integer arguments per line;
 * {@link LookInfoOverlay} formats them with {@code Messages}. TFC's Jade crop line is {@code tfc.jade.yield} (yield * 100,
 * {@code %.0f}); the growth percent is added because a vanilla client has no age bar to read it from.
 */
public final class LookInfoText {
    private LookInfoText() {
    }

    /** One line: a message key and its arguments. */
    public record Line(String key, List<Object> args) {
        public Line {
            args = List.copyOf(args);
        }
    }

    /** A crop: growth percent (clamped to 0..100, floored) and the yield multiplier percent (TFC {@code %.0f}, rounded). */
    public static List<Line> crop(CropPlant plant) {
        float growth = Math.max(0f, Math.min(1f, plant.state().growth()));
        return List.of(new Line("lookinfo.crop.growth", List.of((int) (growth * 100f))),
            new Line("lookinfo.crop.yield", List.of(Math.round(plant.state().yield() * 100f))));
    }
}
