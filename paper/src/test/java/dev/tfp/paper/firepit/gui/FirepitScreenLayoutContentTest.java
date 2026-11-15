/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.firepit.gui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.tfp.core.firepit.FirepitSim;
import dev.tfp.core.gui.ChestGeometry;
import dev.tfp.core.gui.GuiImage;
import dev.tfp.core.gui.ProgressFrames;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/**
 * PAR-HEAT-20: the plugin's firepit screen layout ({@link FirepitScreenLayout}) must match the art datagen wrote
 * ({@code FirepitScreenArt}): the images' ids, heights, ascents and grids, the PNG sizes, a slot frame under each of
 * TFC's seven chest slots, no frame under the 25 chest slots TFC does not have, and a thermometer indicator frame per
 * {@link ProgressFrames#thermometer} value.
 */
class FirepitScreenLayoutContentTest {
    /** The dark top-left edge of TFC's slot frames. */
    private static final int FRAME_EDGE = 0xFF373737;

    private static Path pack() {
        String configured = System.getProperty("tfp.contentData");
        assumeTrue(configured != null, "no generated content");
        Path root = Path.of(configured).getParent().resolve("craftengine/tfp");
        assumeTrue(Files.isRegularFile(root.resolve("configuration/firepit_gui.yml")), "no firepit screen art generated");
        return root;
    }

    private static BufferedImage png(Path pack, GuiImage image) throws IOException {
        return ImageIO.read(pack.resolve("resourcepack/assets/tfp/" + image.packPath()).toFile());
    }

    @Test
    @SuppressWarnings("unchecked")
    void theImagesMatchTheLayout() throws IOException {
        Path pack = pack();
        Map<String, Object> root = new Yaml().load(Files.readString(pack.resolve("configuration/firepit_gui.yml")));
        Map<String, Map<String, Object>> images = (Map<String, Map<String, Object>>) root.get("images");
        List<GuiImage> layout = List.of(FirepitScreenLayout.BACKGROUND, FirepitScreenLayout.THERMOMETER);
        assertThat(FirepitScreenLayout.LAYERS.background()).isEqualTo(FirepitScreenLayout.BACKGROUND);
        for (GuiImage image : layout) {
            Map<String, Object> e = images.get(image.id());
            assertThat(e).as(image.id()).isNotNull();
            assertThat(e.get("height")).as(image.id() + " height").isEqualTo(image.height());
            assertThat(e.get("ascent")).as(image.id() + " ascent").isEqualTo(image.ascent());
            assertThat(e.get("file")).isEqualTo(image.file());
            assertThat(e.get("font")).isEqualTo(GuiImage.FONT);
            assertThat(e.get("grid_size")).isEqualTo(image.frames() > 1 ? image.rows() + "," + image.cols() : null);
            BufferedImage png = png(pack, image);
            assertThat(png.getWidth()).as(image.id() + " width").isEqualTo(image.pngWidth());
            assertThat(png.getHeight()).as(image.id() + " height").isEqualTo(image.pngHeight());
        }
        assertThat(images.keySet()).hasSize(layout.size());
    }

    @Test
    void aFrameUnderEveryTfcSlotAndNoneUnderTheOthers() throws IOException {
        BufferedImage bg = png(pack(), FirepitScreenLayout.BACKGROUND);
        ChestGeometry chest = FirepitScreenLayout.CHEST;
        for (int slot = 0; slot < chest.size(); slot++) {
            int edge = bg.getRGB(chest.slotX(slot) - 1, chest.slotY(slot) + 8);
            int tfc = FirepitScreenLayout.tfcSlot(slot);
            if (tfc >= 0) {
                assertThat(edge).as("TFC slot %d at chest slot %d", tfc, slot).isEqualTo(FRAME_EDGE);
            } else if (!FirepitScreenLayout.THERMOMETER_SLOTS.contains(slot)) {
                assertThat(edge).as("chest slot %d TFC does not have", slot).isNotEqualTo(FRAME_EDGE);
            }
            assertThat(bg.getRGB(chest.slotX(slot) + 8, chest.slotY(slot) + 8) >>> 24).as("slot %d covered", slot)
                .isEqualTo(0xFF);
        }
        assertThat(FirepitScreenLayout.CHEST_SLOTS).hasSize(FirepitSim.SLOTS);
    }

    @Test
    void theThermometerStripHasAnIndicatorFrameForEveryTemperature() throws IOException {
        GuiImage t = FirepitScreenLayout.THERMOMETER;
        BufferedImage strip = png(pack(), t);
        for (int temperature : new int[] {0, 1, 400, 1200, 1600}) {
            int frame = FirepitScreenLayout.frames(temperature).get(FirepitScreenLayout.THERMOMETER_LAYER);
            int x0 = t.frameCol(frame) * t.width();
            int y0 = t.frameRow(frame) * t.height();
            int opaqueTop = -1;
            for (int y = 0; y < t.height() && opaqueTop < 0; y++) {
                for (int x = 0; x < t.width(); x++) {
                    if ((strip.getRGB(x0 + x, y0 + y) >>> 24) > 1) { // alpha 1: the width marker
                        opaqueTop = y;
                        break;
                    }
                }
            }
            if (frame == 0) {
                assertThat(opaqueTop).as("cold: TFC draws nothing").isEqualTo(-1);
            } else {
                assertThat(opaqueTop).as("%d C: the indicator %d pixels up", temperature, frame)
                    .isEqualTo(ProgressFrames.thermometerOffset(frame));
            }
        }
    }
}
