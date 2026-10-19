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
    private static TfcCheckout checkout() {
        Path path = Path.of(System.getProperty("tfc.checkout", "/opt/tfc"));
        Assumptions.assumeTrue(Files.isDirectory(path.resolve(".git")), "no TFC checkout at " + path);
        Assumptions.assumeTrue(TfcCheckout.PINNED_COMMIT.equals(TfcCheckout.resolveHead(path)),
            "TFC checkout is not at the pinned commit");
        return TfcCheckout.open(path, TfcCheckout.PINNED_COMMIT);
    }

    @Test
    void theForgeHasEightHeatLevelsAndTheBellowsFourFacings() {
        OutputTree out = new OutputTree();
        int states = ForgeDevices.run(checkout(), out);

        assertThat(states).isEqualTo(8 + 4);
        String forge = new String(out.files().get(ForgeDevices.FORGE_OUTPUT), StandardCharsets.UTF_8);
        assertThat(forge).contains("tfp:charcoal_forge:", "heat_level:", "range: '0~7'", "default: 0");
        assertThat(forge).contains("heat_level=0:", "heat_level=7:", "appearance: cold", "appearance: lit");
        String bellows = new String(out.files().get(ForgeDevices.BELLOWS_OUTPUT), StandardCharsets.UTF_8);
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
