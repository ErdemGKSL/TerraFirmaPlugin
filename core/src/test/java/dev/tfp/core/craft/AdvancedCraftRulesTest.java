/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.craft;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class AdvancedCraftRulesTest {
    private static final List<String> NONE = Arrays.asList(null, null, null);

    @Test
    void primaryIsTheFirstMatchInSlotOrder() {
        assertThat(AdvancedCraftRules.primaryIndex(List.of(false, true, true))).isEqualTo(1);
        assertThat(AdvancedCraftRules.primaryIndex(List.of(false, false))).isEqualTo(-1);
    }

    @Test
    void noPrimaryGivesNoOutput() {
        assertThat(AdvancedCraftRules.output(null, item -> item + "_out")).isNull();
        assertThat(AdvancedCraftRules.output("flint", item -> item + "_out")).isEqualTo("flint_out");
    }

    @Test
    void everySlotWithThePrimaryItemGetsTheProviderOthersKeepVanilla() {
        List<String> items = Arrays.asList("flint", "stick", "flint");
        List<String> vanilla = Arrays.asList(null, "bucket", null);
        List<String> out = AdvancedCraftRules.remainders(items, vanilla, "flint", item -> item + "_chipped");
        assertThat(out).containsExactly("flint_chipped", "bucket", "flint_chipped");
    }

    @Test
    void emptySlotsAndNoRemainderStayEmpty() {
        List<String> out = AdvancedCraftRules.remainders(Arrays.asList(null, "stick", null), NONE, "flint", item -> item + "_x");
        assertThat(out).containsExactly(null, null, null);
    }

    @Test
    void noPrimaryUsesOnlyVanillaRemainders() {
        List<String> items = Arrays.asList("stick", "bucket", null);
        List<String> vanilla = Arrays.asList(null, "bucket", null);
        List<String> out = AdvancedCraftRules.remainders(items, vanilla, null, item -> "never");
        assertThat(out).containsExactly(null, "bucket", null);
    }
}
