/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.health;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

/**
 * Golden strings exported from TFC's own {@code HealthDisplayStyle} formatters ({@code String.format} of the float
 * values, a scratch program outside the repo at c3b4d9536).
 */
class HealthDisplayStyleTest {
    @ParameterizedTest
    @CsvSource(delimiter = ';', value = {
            "15;20;750/1000;15.0/20.0;750;15.0",
            "17;17;850/850;17.0/17.0;850;17.0",
            "0.5;20;25/1000;0.5/20.0;25;0.5",
            "20;20;1000/1000;20.0/20.0;1000;20.0",
            "3.33;17.2;167/860;3.3/17.2;167;3.3",
            "0.01;20;1/1000;0.0/20.0;1;0.0",
            "19.99;20;1000/1000;20.0/20.0;1000;20.0",
    })
    void everyStyleMatchesTfc(float current, float max, String tfc, String vanilla, String tfcCurrent, String vanillaCurrent) {
        assertThat(HealthDisplayStyle.TFC.format(current, max)).isEqualTo(tfc);
        assertThat(HealthDisplayStyle.VANILLA.format(current, max)).isEqualTo(vanilla);
        assertThat(HealthDisplayStyle.TFC_CURRENT.format(current, max)).isEqualTo(tfcCurrent);
        assertThat(HealthDisplayStyle.VANILLA_CURRENT.format(current, max)).isEqualTo(vanillaCurrent);
    }

    @Test
    void defaultIsTfcsDefault() {
        assertThat(HealthDisplayStyle.DEFAULT).isEqualTo(HealthDisplayStyle.TFC);
        assertThat(HealthDisplayStyle.values()).extracting(Enum::name)
                .containsExactly("TFC", "VANILLA", "TFC_CURRENT", "VANILLA_CURRENT");
    }

    @Test
    void formatsAShownHealthDisplayInAttributeUnits() {
        // 15/20 vanilla HP at modifier 0.85: TFC's max health attribute is 17, the health is 12.75 of 17.
        HealthDisplay d = HealthDisplay.of(15f, 20f, 0.85f, HealthConfig.DEFAULT);
        assertThat(HealthDisplayStyle.TFC.format(d, HealthConfig.DEFAULT)).isEqualTo("638/850");
        assertThat(HealthDisplayStyle.VANILLA.format(d, HealthConfig.DEFAULT)).isEqualTo("12.8/17.0");
        assertThat(HealthDisplayStyle.TFC_CURRENT.format(d, HealthConfig.DEFAULT)).isEqualTo("638");
        assertThat(HealthDisplayStyle.VANILLA_CURRENT.format(d, HealthConfig.DEFAULT)).isEqualTo("12.8");
    }

    @Test
    void ignoresTheDefaultLocale() {
        Locale before = Locale.getDefault();
        try {
            Locale.setDefault(Locale.GERMANY);
            assertThat(HealthDisplayStyle.VANILLA.format(15f, 20f)).isEqualTo("15.0/20.0");
        } finally {
            Locale.setDefault(before);
        }
    }

    @Test
    void byNameAcceptsTfcNamesLeniently() {
        assertThat(HealthDisplayStyle.byName("VANILLA_CURRENT")).hasValue(HealthDisplayStyle.VANILLA_CURRENT);
        assertThat(HealthDisplayStyle.byName(" tfc-current ")).hasValue(HealthDisplayStyle.TFC_CURRENT);
        assertThat(HealthDisplayStyle.byName("hearts")).isEmpty();
        assertThat(HealthDisplayStyle.byName(null)).isEmpty();
    }
}
