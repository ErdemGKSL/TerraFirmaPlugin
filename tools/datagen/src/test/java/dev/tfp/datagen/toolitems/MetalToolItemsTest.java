/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen.toolitems;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.datagen.TfcCheckout;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/** PAR-TOOL-06 against the real TFC checkout: the chisel, propick, tuyere and shears of every metal. */
class MetalToolItemsTest {
    private static TfcCheckout checkout() {
        Path path = Path.of(System.getProperty("tfc.checkout", "/opt/tfc"));
        Assumptions.assumeTrue(Files.isDirectory(path.resolve(".git")), "no TFC checkout at " + path);
        return TfcCheckout.open(path, TfcCheckout.PINNED_COMMIT);
    }

    @Test
    void oneItemPerMetalAndKind() {
        var items = MetalToolItems.items(checkout());
        assertThat(items).hasSize(36); // 9 metals x chisel, propick, shears, tuyere
        assertThat(items).extracting(i -> i.id()).contains("metal/chisel/copper", "metal/shears/steel", "metal/tuyere/bronze",
            "metal/propick/black_steel");
    }

    @Test
    void attacksAreTfcsChiselAndPropick() {
        var a = MetalToolItems.attacks(checkout());
        assertThat(a.get("chisel").factor()).isEqualByComparingTo("0.27");
        assertThat(a.get("chisel").speed()).isEqualByComparingTo("1.5");
        assertThat(a.get("propick").factor()).isEqualByComparingTo("0.5");
        assertThat(a.get("propick").speed()).isEqualByComparingTo("-2.8");
    }
}
