/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** M1-BOOT-03: the text merge keeps the owner's file and comments and appends only missing top-level keys. */
class YamlDefaultsMergeTest {
    static final String DEFAULTS = """
            # Header comment, separated by a blank line: not attached to any key.

            # Visual allocation.
            # Second comment line.
            visuals:
              profile:
                "1.21": level-3a   # inline comment
                "26": level-2

              solid-reserve-warning: 500

            # Installer switches.
            auto-install:
              craftengine: true
            "quoted-key": 1
            """;

    @Test
    void addsMissingTopLevelKeysWithTheirCommentBlock() {
        String owner = """
                # my own notes
                visuals:
                  solid-reserve-warning: 10 # tuned
                """;
        YamlDefaultsMerge.Result r = YamlDefaultsMerge.merge(owner, DEFAULTS);
        assertThat(r.added()).containsExactly("auto-install", "quoted-key");
        assertThat(r.text()).startsWith(owner)
                .contains("# Installer switches.\nauto-install:\n  craftengine: true\n")
                .contains("\"quoted-key\": 1")
                .doesNotContain("Header comment")
                .doesNotContain("level-3a");
        assertThat(YamlDefaultsMerge.topLevelKeys(r.text())).containsExactly("visuals", "auto-install", "quoted-key");
    }

    @Test
    void completeFileIsReturnedUnchanged() {
        String owner = "visuals: {}\nauto-install:\n  craftengine: false\n'quoted-key': 2\n";
        YamlDefaultsMerge.Result r = YamlDefaultsMerge.merge(owner, DEFAULTS);
        assertThat(r.changed()).isFalse();
        assertThat(r.text()).isSameAs(owner);
    }

    @Test
    void mergeIsIdempotent() {
        YamlDefaultsMerge.Result once = YamlDefaultsMerge.merge("visuals:\n  profile: {}", DEFAULTS);
        YamlDefaultsMerge.Result twice = YamlDefaultsMerge.merge(once.text(), DEFAULTS);
        assertThat(twice.changed()).isFalse();
        assertThat(twice.text()).isEqualTo(once.text());
    }

    @Test
    void emptyFileGetsEveryBlockWithBodiesAndInnerBlankLines() {
        YamlDefaultsMerge.Result r = YamlDefaultsMerge.merge("", DEFAULTS);
        assertThat(r.added()).containsExactly("visuals", "auto-install", "quoted-key");
        assertThat(r.text()).contains("# Visual allocation.\n# Second comment line.\nvisuals:\n  profile:\n"
                + "    \"1.21\": level-3a   # inline comment\n    \"26\": level-2\n\n  solid-reserve-warning: 500\n");
    }

    @Test
    void keepsWindowsLineEndings() {
        YamlDefaultsMerge.Result r = YamlDefaultsMerge.merge("visuals: {}\r\n", DEFAULTS);
        assertThat(r.text()).contains("auto-install:\r\n  craftengine: true\r\n");
    }

    @Test
    void recognisesOnlyTopLevelKeys() {
        assertThat(YamlDefaultsMerge.topLevelKey("  nested: 1")).isNull();
        assertThat(YamlDefaultsMerge.topLevelKey("# visuals: 1")).isNull();
        assertThat(YamlDefaultsMerge.topLevelKey("- item")).isNull();
        assertThat(YamlDefaultsMerge.topLevelKey("---")).isNull();
        assertThat(YamlDefaultsMerge.topLevelKey("key:value")).isNull();
        assertThat(YamlDefaultsMerge.topLevelKey("auto-install:")).isEqualTo("auto-install");
        assertThat(YamlDefaultsMerge.topLevelKey("a b : 1 # c")).isEqualTo("a b");
        assertThat(YamlDefaultsMerge.topLevelKey("'x': 1")).isEqualTo("x");
    }
}
