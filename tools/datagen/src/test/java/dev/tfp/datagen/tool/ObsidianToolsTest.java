/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen.tool;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.datagen.TfcCheckout;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/** PAR-TOOL-11 against the real TFC checkout: 12 items, TFCTiers.OBSIDIAN numbers, TFC's attack values. */
class ObsidianToolsTest {
    private static TfcCheckout checkout() {
        Path path = Path.of(System.getProperty("tfc.checkout", "/opt/tfc"));
        Assumptions.assumeTrue(Files.isDirectory(path.resolve(".git")), "no TFC checkout at " + path);
        return TfcCheckout.open(path, TfcCheckout.PINNED_COMMIT);
    }

    @Test
    void twelveItems() {
        assertThat(ObsidianTools.items()).hasSize(12);
        assertThat(ObsidianTools.items().stream().filter(RockTools.Item::tool)).hasSize(6);
    }

    @Test
    void tierIsTfcObsidian() {
        var t = RockTools.tiers(checkout()).get("obsidian");
        assertThat(t).isEqualTo(new RockTools.Tier(0, 60, new BigDecimal("5.0"), new BigDecimal("2.5")));
    }

    @Test
    void attacksMatchTfcItemRegistrations() {
        var a = ObsidianTools.attacks(checkout());
        assertThat(a.get("axe").factor()).isEqualByComparingTo("1.5");
        assertThat(a.get("axe").speed()).isEqualByComparingTo("-3.2");
        assertThat(a.get("javelin").factor()).isEqualByComparingTo("0.7");
        assertThat(a.get("javelin").speed()).isEqualByComparingTo("-2.2");
        assertThat(a.get("hoe").speed()).isEqualByComparingTo("-3.0");
    }
}
