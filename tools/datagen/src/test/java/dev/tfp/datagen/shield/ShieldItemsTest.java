/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen.shield;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.datagen.TfcCheckout;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class ShieldItemsTest {
    private static TfcCheckout checkout() {
        Path path = Path.of(System.getProperty("tfc.checkout", "/opt/tfc"));
        Assumptions.assumeTrue(Files.isDirectory(path.resolve(".git")), "no TFC checkout at " + path);
        return TfcCheckout.open(path, TfcCheckout.PINNED_COMMIT);
    }

    @Test
    void nineMetalsWithTierUses() {
        var tfc = checkout();
        var metals = ShieldItems.metals(tfc);
        assertThat(metals).hasSize(9).contains("copper", "steel", "red_steel");
        assertThat(ShieldItems.entry("copper", 600).toString()).contains("material=shield", "max_damage=600",
            "property=minecraft:using_item", "tfp:item/metal/shield/copper_blocking");
    }
}
