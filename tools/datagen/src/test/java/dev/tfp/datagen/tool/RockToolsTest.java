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

/** PAR-TOOL-01 against the real TFC checkout: counts, tiers and attack values. */
class RockToolsTest {
    private static TfcCheckout checkout() {
        Path path = Path.of(System.getProperty("tfc.checkout", "/opt/tfc"));
        Assumptions.assumeTrue(Files.isDirectory(path.resolve(".git")), "no TFC checkout at " + path);
        return TfcCheckout.open(path, TfcCheckout.PINNED_COMMIT);
    }

    @Test
    void fortyEightItems() {
        assertThat(RockTools.items()).hasSize(48);
        assertThat(RockTools.items().stream().filter(RockTools.Item::tool).count()).isEqualTo(24);
    }

    @Test
    void tiersMatchTfcTiers() {
        var tiers = RockTools.tiers(checkout());
        assertThat(tiers.get("igneous_intrusive")).isEqualTo(new RockTools.Tier(0, 60, new BigDecimal("4.7"), new BigDecimal("2.0")));
        assertThat(tiers.get("igneous_extrusive").uses()).isEqualTo(70);
        assertThat(tiers.get("sedimentary").speed()).isEqualByComparingTo("4.0");
        assertThat(tiers.get("metamorphic").uses()).isEqualTo(55);
        assertThat(tiers.get("metamorphic").speed()).isEqualByComparingTo("4.35");
    }

    @Test
    void attacksMatchRockCategoryItemTypes() {
        var a = RockTools.attacks(checkout());
        assertThat(a.get("axe").factor()).isEqualByComparingTo("1.5");
        assertThat(a.get("axe").speed()).isEqualByComparingTo("-3.2");
        assertThat(a.get("javelin").factor()).isEqualByComparingTo("0.7");
        assertThat(a.get("shovel").factor()).isEqualByComparingTo("0.875");
        assertThat(a.get("knife").speed()).isEqualByComparingTo("-2.0");
    }

    @Test
    void entriesCarryTierValues() {
        var tier = new RockTools.Tier(0, 60, new BigDecimal("4.7"), new BigDecimal("2.0"));
        var attack = new RockTools.Attack(new BigDecimal("1.5"), new BigDecimal("-3.2"));
        var item = new RockTools.Item("stone/axe/igneous_intrusive", "axe", "igneous_intrusive", true);
        var e = RockTools.entry(item, tier, attack);
        assertThat(e.get("template")).isEqualTo("tfp:item/tool");
        assertThat(e.toString()).contains("durability=60", "break_power=0", "mineable/axe", "amount=3");
        var javelin = RockTools.entry(new RockTools.Item("stone/javelin/igneous_intrusive", "javelin", "igneous_intrusive", true),
            tier, new RockTools.Attack(new BigDecimal("0.7"), new BigDecimal("-2.2")));
        assertThat(javelin.get("template")).isEqualTo("tfp:item/javelin");
        assertThat(javelin.toString()).contains("durability=60", "damage=1.4", "attack_speed=-2.2");
    }
}
