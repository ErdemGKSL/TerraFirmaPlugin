/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.oreoverlay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.tfp.core.oreoverlay.census.ExposureRule;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class OreOverlayDataTest {
    /** The datagen output next to {@code content/data} (skipped while datagen has not run). */
    private static Path visuals() {
        String data = System.getProperty("tfp.contentData");
        return data == null ? null : Path.of(data).resolveSibling("visuals").resolve("ore_overlay");
    }

    @Test
    void readsTheGeneratedOreAndOcclusionLists() throws Exception {
        Path dir = visuals();
        assumeTrue(dir != null && Files.isRegularFile(dir.resolve("ores.json")), "datagen output missing");
        OreOverlayData d;
        try (Reader o = Files.newBufferedReader(dir.resolve("ores.json"), StandardCharsets.UTF_8);
                Reader c = Files.newBufferedReader(dir.resolve("occluding.json"), StandardCharsets.UTF_8)) {
            d = OreOverlayData.read(o, c);
        }
        // TFC: 12 graded metals x 3 grades + 17 minerals = 53 overlays; (36 + 17) x 21 rocks = 1113 ore blocks
        assertThat(d.overlays()).hasSize(53);
        assertThat(d.ores()).isEqualTo(1113);
        int gold = d.kind("tfp:ore/normal_native_gold/granite");
        assertThat(gold).isGreaterThanOrEqualTo(0);
        assertThat(d.overlays().get(gold)).isEqualTo("tfp:ore_overlay/normal_native_gold");
        assertThat(d.kind("tfp:ore/normal_native_gold/shale")).as("columnar host, same overlay").isEqualTo(gold);
        assertThat(d.code("tfp:rock/raw/granite")).isEqualTo(ExposureRule.SOLID);
        assertThat(d.code("tfp:ore/diamond/gabbro")).isGreaterThanOrEqualTo(0);
        assertThat(d.code("someone_else:glass")).as("unknown CE blocks expose").isEqualTo(ExposureRule.OPEN);
        assertThat(d.itemModel(gold, -1)).isEqualTo("tfp:ore_overlay/normal_native_gold");
        assertThat(d.itemModel(gold, 4)).isEqualTo("tfp:ore_overlay/normal_native_gold/crack_4");
        assertThat(d.itemModel(gold, 12)).isEqualTo("tfp:ore_overlay/normal_native_gold/crack_9");
    }

    @Test
    void ownEntityIdsStayInTheHighRange() {
        // 26.x has no UnsafeValues#nextEntityId (found in game on Paper 26.3): our own range is used there.
        java.util.function.IntSupplier ids = OreOverlayModule.ownIds();
        int a = ids.getAsInt();
        int b = ids.getAsInt();
        assertThat(a).isGreaterThan(OreOverlayModule.OWN_ID_BASE);
        assertThat(b).isEqualTo(a + 1);
    }

    @Test
    void crackStagesFollowTheServerProgress() {
        assertThat(OreOverlayListener.stage(0f)).isEqualTo(-1);
        assertThat(OreOverlayListener.stage(0.05f)).isZero();
        assertThat(OreOverlayListener.stage(0.55f)).isEqualTo(5);
        assertThat(OreOverlayListener.stage(0.99f)).isEqualTo(9);
        assertThat(OreOverlayListener.stage(1f)).isEqualTo(-1);
        assertThat(OreOverlayListener.stage(-0.1f)).isEqualTo(-1);
    }
}
