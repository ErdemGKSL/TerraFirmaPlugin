/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/** Metal sword and mace attack damage: modifier amount = TFC tier damage x factor - 1. */
class MetalWeaponsTest {
    @Test
    void amountIsTierDamageTimesFactorMinusOne() {
        assertThat(AssetPipeline.weaponDamageModifier(new BigDecimal("3.25"), AssetPipeline.METAL_WEAPON_KINDS.get("sword")))
            .isEqualByComparingTo("2.25");
        assertThat(AssetPipeline.weaponDamageModifier(new BigDecimal("5.75"), AssetPipeline.METAL_WEAPON_KINDS.get("mace")))
            .isEqualByComparingTo("6.475");
    }

    @Test
    void emitsTheWeaponTemplateFromTheRealCheckout() {
        Path path = Path.of(System.getProperty("tfc.checkout", "/opt/tfc"));
        Assumptions.assumeTrue(Files.isDirectory(path.resolve(".git")), "no TFC checkout at " + path);
        Assumptions.assumeTrue(TfcCheckout.PINNED_COMMIT.equals(TfcCheckout.resolveHead(path)), "not at the pinned commit");
        var files = Datagen.generate(path, TfcCheckout.PINNED_COMMIT).tree().files();
        String metal = files.entrySet().stream().filter(e -> e.getKey().endsWith("/metal.yml"))
            .map(e -> new String(e.getValue(), StandardCharsets.UTF_8)).findFirst().orElseThrow();
        assertThat(metal).contains("tfp:metal/sword/copper:", "tfp:metal/mace/steel:", "template: tfp:item/weapon");
        assertThat(metal).contains("damage: 2.25", "damage: 6.475");
    }
}
