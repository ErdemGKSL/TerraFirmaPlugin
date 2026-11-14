/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/PowderkegBlockEntity.java @ c3b4d9536 (getStrength)
 *
 * The rows of tfc-powderkeg-strength.tsv were produced by a scratch mirror of TFC's getStrength at c3b4d9536 (with
 * Minecraft's Mth.floor); they are data here, not computed by this code.
 */
package dev.tfp.core.powderkeg;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class PowderkegStrengthGoldenTest {

    @Test
    void strengthMatchesTfcForEveryGoldenRow() throws IOException {
        String text;
        try (InputStream in = getClass().getResourceAsStream("tfc-powderkeg-strength.tsv")) {
            assertThat(in).isNotNull();
            text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        int rows = 0;
        for (String line : text.split("\n")) {
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            String[] f = line.split("\t");
            double modifier = Double.parseDouble(f[0]);
            int count = Integer.parseInt(f[1]);
            int want = Integer.parseInt(f[2].trim());
            assertThat(PowderkegRules.strength(count, modifier)).as("modifier %s count %s", modifier, count).isEqualTo(want);
            rows++;
        }
        assertThat(rows).isEqualTo(153);
    }

    @Test
    void aFullKegOfGunpowderIsTheMaximumAtTheDefaultModifier() {
        // 12 slots of 64 gunpowder: floor(1.0 * 768 / 12) = 64, TFC's MAX_STRENGTH (the full_powderkeg advancement)
        assertThat(PowderkegConfig.DEFAULTS.strength(12 * 64)).isEqualTo(PowderkegRules.MAX_STRENGTH);
        assertThat(PowderkegRules.strength(List.of(64, 64, 0, 10), 1d)).isEqualTo(11);
        assertThat(PowderkegRules.strength(11, 1d)).isZero();
    }

    @Test
    void configKeepsTfcDefaultsAndRanges() {
        PowderkegConfig d = PowderkegConfig.DEFAULTS;
        assertThat(d.enabled()).isTrue();
        assertThat(d.onlyBreaksNaturalBlocks()).isFalse();
        assertThat(d.automation()).isTrue();
        assertThat(d.strengthModifier()).isEqualTo(1d);
        assertThat(d.fuseTicks()).isEqualTo(80);
        assertThatThrownBy(() -> new PowderkegConfig(true, false, true, 64.5, 80)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PowderkegConfig(true, false, true, 1, 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void explosionFilterFollowsTheConfig() {
        // default: everything but #tfc:explosion_proof
        assertThat(PowderkegRules.breaks(false, false, false)).isTrue();
        assertThat(PowderkegRules.breaks(false, true, true)).isFalse();
        // onlyBreaksNaturalBlocks: only #tfc:powderkeg_breaking_blocks
        assertThat(PowderkegRules.breaks(true, false, false)).isFalse();
        assertThat(PowderkegRules.breaks(true, false, true)).isTrue();
        assertThat(PowderkegRules.breaks(true, true, true)).isTrue();
    }
}
