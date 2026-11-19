/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/** PAR-WORLD-10 against the real TFC checkout: 20 woods x 6 block kinds, TFC strengths, one file per wood. */
class WoodBlocksTest {
    @Test
    void twentyWoodsTimesSixKinds() {
        Path path = Path.of(System.getProperty("tfc.checkout", "/opt/tfc"));
        Assumptions.assumeTrue(Files.isDirectory(path.resolve(".git")), "no TFC checkout at " + path);
        TfcCheckout tfc = TfcCheckout.open(path, TfcCheckout.PINNED_COMMIT);
        OutputTree out = new OutputTree();
        Report report = new Report();
        AssetPipeline.run(tfc, out, report);
        long wood = report.emittedIds().stream().filter(id -> id.startsWith("wood/")).count();
        assertThat(wood).isEqualTo(20 * 6);
        String oak = new String(out.get(AssetPipeline.CONFIG + "wood/oak.yml"), StandardCharsets.UTF_8);
        assertThat(oak).contains("tfp:wood/log/oak:", "tfp:wood/stripped_wood/oak:", "tfp:wood/planks/oak:",
            "tfp:wood/leaves/oak:", "wood_hardness: 8.0", "wood_hardness: 7.5", "wood_hardness: 1.5", "wood_resistance: 3.0");
        String chestnut = new String(out.get(AssetPipeline.CONFIG + "wood/chestnut.yml"), StandardCharsets.UTF_8);
        assertThat(chestnut).contains("tfp:wood/leaves/chestnut:");
        assertThat(report.emittedIds()).contains("wood/leaves/chestnut"); // the multipart burr state is replaced by the dense leaves
    }
}
