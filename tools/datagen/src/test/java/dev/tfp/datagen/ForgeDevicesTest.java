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

/** M4-FORGE-01: the charcoal forge and bellows entries, read from the real pinned TFC checkout when it is available. */
class ForgeDevicesTest {
    private static Path path() {
        Path path = Path.of(System.getProperty("tfc.checkout", "/opt/tfc"));
        Assumptions.assumeTrue(Files.isDirectory(path.resolve(".git")), "no TFC checkout at " + path);
        Assumptions.assumeTrue(TfcCheckout.PINNED_COMMIT.equals(TfcCheckout.resolveHead(path)),
            "TFC checkout is not at the pinned commit");
        return path;
    }

    private static TfcCheckout checkout() {
        return TfcCheckout.open(path(), TfcCheckout.PINNED_COMMIT);
    }

    @Test
    void theForgeHasEightHeatLevelsAndTheBellowsFourFacings() {
        // The whole pipeline: the bellows item model is copied by AssetPipeline before ForgeDevices checks it.
        var files = Datagen.generate(path(), TfcCheckout.PINNED_COMMIT).tree().files();

        String forge = new String(files.get(ForgeDevices.FORGE_OUTPUT), StandardCharsets.UTF_8);
        assertThat(forge).contains("tfp:charcoal_forge:", "heat_level:", "range: '0~7'", "default: 0");
        assertThat(forge).contains("heat_level=0:", "heat_level=7:", "appearance: cold", "appearance: lit");
        assertThat(forge).contains("template: tfp:settings/device", "template: tfp:loot_table/self");
        assertThat(forge).as("the forge has no item: TFC makes it by lighting a charcoal pile").doesNotContain("items:");
        String bellows = new String(files.get(ForgeDevices.BELLOWS_OUTPUT), StandardCharsets.UTF_8);
        assertThat(bellows).contains("items:", "template: tfp:item/block_item", "block.tfp.bellows");
        assertThat(files).containsKey(AssetPipeline.RP + "models/item/bellows.json");
        assertThat(bellows).contains("tfp:bellows:", "facing:", "default: north");
        for (String f : ForgeDevices.FACINGS) {
            assertThat(bellows).contains("facing=" + f + ":");
        }
    }

    @Test
    void theForgeLevelsAreReadFromTfcAndNotHardCoded() {
        ForgeDevices.HeatRange range = ForgeDevices.heatRange(checkout());
        assertThat(range.name()).isEqualTo("heat_level");
        assertThat(range.min()).isZero();
        assertThat(range.max()).isEqualTo(7);
    }
}
