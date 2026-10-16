/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/** Runs datagen on the real pinned TFC checkout when it is available (-Ptfc.checkout, $TFC_CHECKOUT, /opt/tfc). */
class PinnedCheckoutTest {
    @Test
    void fullCheckoutGeneratesValidRockSoilOreContent() {
        Path checkout = Path.of(System.getProperty("tfc.checkout", "/opt/tfc"));
        Assumptions.assumeTrue(Files.isDirectory(checkout.resolve(".git")), "no TFC checkout at " + checkout);
        Assumptions.assumeTrue(TfcCheckout.PINNED_COMMIT.equals(TfcCheckout.resolveHead(checkout)),
            "TFC checkout is not at the pinned commit");

        Datagen.Result r = Datagen.generate(checkout, TfcCheckout.PINNED_COMMIT);
        // 21 rocks x 10 cube types + 7 magma, 53 ore kinds x 21 rocks, 8 dirt, 8 clay, 7 sand
        assertThat(r.report().emittedCount()).isEqualTo(21 * 10 + 7 + 53 * 21 + 8 + 8 + 7);
        assertThat(r.report().count("ore_textures_flattened", 0)).isEqualTo(53 * 21);
        assertThat(r.report().missing()).isEmpty();
        CeYamlCheck.assertValid(r.tree().files());
    }
}
