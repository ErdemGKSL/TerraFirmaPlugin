/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * PAR-INFRA-54: {@link Report#countCeStates}, the line-based state counter the installer sizes CraftEngine's
 * {@code block.serverside-blocks} from. The real-tree check (the counter equals a YAML parse of every generated entry) is
 * {@code GeneratedCarrierTest.theSummaryCountsEveryCraftEngineState}; these cases pin the shapes one by one.
 */
class ReportTest {

    @Test
    void aBlockWithoutPropertiesIsOneState() {
        assertThat(Report.countCeStates("blocks:\n  tfp:a:\n    state:\n      template: t\n")).isEqualTo(1);
        assertThat(Report.countCeStates("blocks:\n  tfp:a:\n    template: t\n  tfp:b:\n    template: t\n")).isEqualTo(2);
    }

    @Test
    void suffixedBlockSectionsCountAndOtherSectionsDoNot() {
        String text = "blocks#rock:\n  tfp:a:\n    template: t\n"
            + "'blocks#wood':\n  tfp:b:\n    template: t\n"
            + "items:\n  tfp:i:\n    states:\n      properties:\n        c:\n          type: boolean\n"
            + "templates:\n  tfp:t:\n    states:\n      properties:\n        c:\n          type: boolean\n";
        assertThat(Report.countCeStates(text)).isEqualTo(2);
    }

    @Test
    void listedValuesCountTheirItems() {
        String text = "blocks:\n  tfp:a:\n    states:\n      properties:\n"
            + "        axis:\n          type: string\n          values:\n            - x\n            - z\n          default: x\n"
            + "        facing:\n          type: string\n          values:\n            - north\n            - east\n"
            + "            - south\n            - west\n";
        assertThat(Report.countCeStates(text)).isEqualTo(8);
    }

    @Test
    void axisIsThreeUnlessValuesAreListed() {
        assertThat(Report.countCeStates("blocks:\n  tfp:a:\n    states:\n      properties:\n        axis:\n"
            + "          type: axis\n")).isEqualTo(3);
        assertThat(Report.countCeStates("blocks:\n  tfp:a:\n    states:\n      properties:\n        axis:\n"
            + "          type: axis\n          values:\n            - x\n            - z\n")).isEqualTo(2);
    }

    @Test
    void quotedAndUnquotedRangesAndBooleans() {
        String text = "blocks:\n  tfp:a:\n    states:\n      properties:\n"
            + "        count:\n          type: int\n          range: '1~16'\n          default: 1\n"
            + "        layers:\n          type: int\n          range: 0~7\n"
            + "        lit:\n          type: boolean\n          default: false\n";
        assertThat(Report.countCeStates(text)).isEqualTo(16 * 8 * 2);
    }

    @Test
    void variantsAndAppearancesUnderStatesDoNotAddProperties() {
        // A variant or an appearance may hold keys of its own (settings, nested state lines); they are not properties.
        String text = "blocks:\n  tfp:a:\n    states:\n      properties:\n"
            + "        count:\n          type: int\n          range: '1~4'\n"
            + "      appearances:\n        one:\n          state: 'minecraft:stone'\n"
            + "          model:\n            path: tfp:block/a\n"
            + "      variants:\n        count=1:\n          appearance: one\n          settings:\n"
            + "            luminance: 3\n        count=2:\n          appearance: one\n"
            + "  tfp:b:\n    states:\n      properties:\n        lit:\n          type: boolean\n"
            + "      variants:\n        lit=true:\n          appearance: x\n";
        assertThat(Report.countCeStates(text)).isEqualTo(4 + 2);
    }

    @Test
    void theFileOverloadReadsOnlyConfigurationYaml() {
        byte[] one = "blocks:\n  tfp:a:\n    states:\n      properties:\n        lit:\n          type: boolean\n"
            .getBytes(StandardCharsets.UTF_8);
        Map<String, byte[]> files = Map.of(
            "craftengine/tfp/configuration/a.yml", one,
            "craftengine/tfp/configuration/sub/b.yml", one,
            "craftengine/tfp/resourcepack/c.yml", one,
            "betterhud/configuration/d.yml", one,
            "craftengine/tfp/configuration/e.json", one);
        assertThat(Report.countCeStates(files)).isEqualTo(4);
    }

    @Test
    void theSummaryCarriesCeStatesOnlyWhenSet() {
        Report r = new Report();
        assertThat(r.toJson().getAsJsonObject("counts").has("ce_states")).isFalse();
        r.ceStates(42);
        assertThat(r.toJson().getAsJsonObject("counts").get("ce_states").getAsInt()).isEqualTo(42);
    }
}
