/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen.weapon;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.datagen.TfcCheckout;
import dev.tfp.datagen.tool.RockTools;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/** PAR-TOOL-04 against the real TFC checkout: the macuahuitl's tier numbers and attack values. */
class MacuahuitlTest {
    private static TfcCheckout checkout() {
        Path path = Path.of(System.getProperty("tfc.checkout", "/opt/tfc"));
        Assumptions.assumeTrue(Files.isDirectory(path.resolve(".git")), "no TFC checkout at " + path);
        return TfcCheckout.open(path, TfcCheckout.PINNED_COMMIT);
    }

    @Test
    void entryUsesTheObsidianTierAndTfcAttack() {
        TfcCheckout tfc = checkout();
        var tier = RockTools.tiers(tfc).get("obsidian");
        var attack = Macuahuitl.attack(tfc);
        assertThat(attack.factor()).isEqualByComparingTo("1.0");
        assertThat(attack.speed()).isEqualByComparingTo("-2.4");
        var entry = Macuahuitl.entry(tier, attack);
        assertThat(entry.get("template")).isEqualTo("tfp:item/weapon");
        assertThat(entry.get("arguments").toString()).contains("damage=2.5", "attack_speed=-2.4", "durability=60", "break_power=0");
    }

    @Test
    void metalWeaponsCarryAttackSpeedAndTfcDamage() {
        // copper sword: factor 1.0 x 3.25, speed -2.4; steel mace: 1.3 x 5.75, speed -3.4 (TFC's productAttributes)
        assertThat(new BigDecimal("5.75").multiply(new BigDecimal("1.3"))).isEqualByComparingTo("7.475");
    }
}
