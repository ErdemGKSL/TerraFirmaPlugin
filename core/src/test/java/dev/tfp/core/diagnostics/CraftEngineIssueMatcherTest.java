/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.diagnostics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.tfp.core.diagnostics.CraftEngineIssueMatcher.Issue;
import dev.tfp.core.diagnostics.CraftEngineIssueMatcher.Kind;
import org.junit.jupiter.api.Test;

class CraftEngineIssueMatcherTest {
    private final CraftEngineIssueMatcher m = new CraftEngineIssueMatcher("tfp");

    @Test
    void foundIssuesInOurPackFiles() {
        assertThat(m.match("WARN", "Found 3 issue(s) in file 'resources/tfp/configuration/blocks.yml':"))
                .contains(new Issue(Kind.FILE_ISSUES, 3, "resources/tfp/configuration/blocks.yml",
                        "Found 3 issue(s) in file 'resources/tfp/configuration/blocks.yml':"));
        assertThat(m.match("INFO", "Found 1 issue(s) in file '/srv/mc/plugins/CraftEngine/resources/tfp/configuration/items.yml':"))
                .hasValueSatisfying(i -> assertThat(i.count()).isEqualTo(1));
        assertThat(m.match("WARN", "Found 12 issue(s) in file 'C:\\srv\\plugins\\CraftEngine\\resources\\tfp\\a.yml':"))
                .hasValueSatisfying(i -> assertThat(i.count()).isEqualTo(12));
        assertThat(m.match("WARN", "Found 2 issue(s) in file 'tfp/configuration/blocks.yml':")).isPresent();
    }

    @Test
    void foundIssuesInOtherPacksAreIgnored() {
        assertThat(m.match("WARN", "Found 3 issue(s) in file 'resources/default/configuration/blocks.yml':")).isEmpty();
        assertThat(m.match("WARN", "Found 3 issue(s) in file 'resources/tfpextra/configuration/blocks.yml':")).isEmpty();
        assertThat(m.match("WARN", "Found 3 issue(s) in file 'resources/other/tfp.yml':")).isEmpty();
    }

    @Test
    void warningsNamingOurIds() {
        assertThat(m.match("WARN", "Unknown block tfp:rock/raw/granite in loot table"))
                .hasValueSatisfying(i -> {
                    assertThat(i.kind()).isEqualTo(Kind.NAMESPACED_WARNING);
                    assertThat(i.count()).isEqualTo(1);
                });
        assertThat(m.match("ERROR", "[1] items.tfp:ingot/bronze - missing model")).isPresent();
        assertThat(m.match("severe", "template 'tfp:block_state/slab' not found")).isPresent();
    }

    @Test
    void ignoresInfoOtherNamespacesAndLookalikes() {
        assertThat(m.match("INFO", "Loaded block tfp:rock/raw/granite")).isEmpty();
        assertThat(m.match("WARN", "Unknown block xtfp:thing")).isEmpty();
        assertThat(m.match("WARN", "Unknown block other:tfp")).isEmpty();
        assertThat(m.match("WARN", "tfp: plain text with a colon and space")).isEmpty();
        assertThat(m.match("WARN", "")).isEmpty();
        assertThat(m.match(null, "Unknown block tfp:x")).isEmpty();
        assertThat(m.match("WARN", null)).isEmpty();
    }

    @Test
    void rejectsBadNamespace() {
        assertThatThrownBy(() -> new CraftEngineIssueMatcher("Bad NS")).isInstanceOf(IllegalArgumentException.class);
    }
}
