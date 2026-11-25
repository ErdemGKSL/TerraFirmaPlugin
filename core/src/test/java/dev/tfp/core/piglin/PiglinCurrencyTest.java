/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.piglin;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/** PAR-DATA-22: TFC's {@code Metal.isPiglinCurrency} is GOLD only. */
class PiglinCurrencyTest {
    @Test
    void onlyTheGoldIngotIsCurrency() {
        assertThat(PiglinCurrency.isCurrency("tfp:metal/ingot/gold")).isTrue();
        for (String metal : List.of("rose_gold", "copper", "bronze", "bismuth", "wrought_iron", "steel", "silver", "unknown")) {
            assertThat(PiglinCurrency.isCurrency("tfp:metal/ingot/" + metal)).as(metal).isFalse();
        }
        assertThat(PiglinCurrency.isCurrency("tfp:metal/double_ingot/gold")).isFalse();
        assertThat(PiglinCurrency.isCurrency("minecraft:gold_ingot")).isFalse();
        assertThat(PiglinCurrency.isCurrency(null)).isFalse();
    }

    @Test
    void anAdultLivingPiglinWithAFreeOffHandAdmires() {
        assertThat(PiglinCurrency.canAdmire(true, true, false)).isTrue();
        assertThat(PiglinCurrency.canAdmire(false, true, false)).isFalse();
        assertThat(PiglinCurrency.canAdmire(true, false, false)).isFalse();
        assertThat(PiglinCurrency.canAdmire(true, true, true)).isFalse();
    }
}
