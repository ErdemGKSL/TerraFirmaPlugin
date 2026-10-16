/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.install;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/** M1-INST-02: single-key edits change one line and keep every comment. */
class YamlKeyEditorTest {
    static final String DOC = """
            # header
            top: 1 # top comment
            a:
              # about b
              b:
                c: true    # keep me
                d: 'x # not a comment'
              e: "q"
            list-block:
            - one
            - "two"   # second
            list-nested:
              - x
              - y
            flow: [ "m", n ]
            empty: []
            """;

    @Test
    void readsScalarsAndLists() {
        YamlKeyEditor ed = YamlKeyEditor.of(DOC);
        assertThat(ed.scalar("top")).contains("1");
        assertThat(ed.scalar("a.b.c")).contains("true");
        assertThat(ed.scalar("a.b.d")).contains("x # not a comment");
        assertThat(ed.scalar("a.e")).contains("q");
        assertThat(ed.scalar("a.b")).isEmpty();
        assertThat(ed.scalar("missing.key")).isEmpty();
        assertThat(ed.list("list-block")).contains(List.of("one", "two"));
        assertThat(ed.list("list-nested")).contains(List.of("x", "y"));
        assertThat(ed.list("flow")).contains(List.of("m", "n"));
        assertThat(ed.list("empty")).contains(List.of());
    }

    @Test
    void setScalarChangesOnlyThatLineAndKeepsTheComment() {
        String out = YamlKeyEditor.of(DOC).setScalar("a.b.c", "false").text();
        assertThat(out).isEqualTo(DOC.replace("c: true    # keep me", "c: false    # keep me"));
        assertThat(parse(out)).extractingByKey("a").asString().contains("c=false");
    }

    @Test
    @SuppressWarnings("unchecked")
    void setScalarAddsMissingKeysInsideTheDeepestExistingSection() {
        String out = YamlKeyEditor.of(DOC).setScalar("a.b.z", "5").setScalar("a.new.deep", "on-purpose").text();
        Map<String, Object> tree = parse(out);
        @SuppressWarnings("unchecked")
        Map<String, Object> a = (Map<String, Object>) tree.get("a");
        assertThat(a).containsEntry("e", "q");
        assertThat((Map<Object, Object>) a.get("b")).containsEntry("z", 5).containsEntry("c", true);
        assertThat((Map<Object, Object>) a.get("new")).containsEntry("deep", "on-purpose");
        assertThat(out).contains("# keep me", "# about b", "# header");

        String top = YamlKeyEditor.of(DOC).setScalar("x.y", "1").text();
        assertThat(top).endsWith("empty: []\nx:\n  y: 1\n");
    }

    @Test
    void refusesToOverwriteASection() {
        assertThatThrownBy(() -> YamlKeyEditor.of(DOC).setScalar("a.b", "1")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> YamlKeyEditor.of(DOC).setScalar("top.x", "1")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void removesListItemsInEveryStyle() {
        YamlKeyEditor ed = YamlKeyEditor.of(DOC);
        assertThat(ed.removeListItem("list-block", "one")).isTrue();
        assertThat(ed.removeListItem("list-nested", "y")).isTrue();
        assertThat(ed.removeListItem("flow", "m")).isTrue();
        assertThat(ed.removeListItem("flow", "absent")).isFalse();
        String out = ed.text();
        assertThat(out).contains("- \"two\"   # second", "flow: [n]").doesNotContain("- one", "- y");
        Map<String, Object> tree = parse(out);
        assertThat(tree).containsEntry("list-block", List.of("two")).containsEntry("list-nested", List.of("x"))
                .containsEntry("flow", List.of("n"));
        // Removing the last item leaves an explicit empty list.
        String emptied = YamlKeyEditor.of(out).text();
        YamlKeyEditor e2 = YamlKeyEditor.of(emptied);
        e2.removeListItem("list-block", "two");
        assertThat(parse(e2.text())).containsEntry("list-block", List.of());
        assertThat(e2.text()).contains("list-block: []\nlist-nested:");
    }

    @Test
    void addsListItems() {
        YamlKeyEditor ed = YamlKeyEditor.of(DOC);
        assertThat(ed.addListItem("list-nested", "z", "z")).isTrue();
        assertThat(ed.addListItem("list-nested", "x", "x")).isFalse();
        assertThat(ed.addListItem("flow", "o", "o")).isTrue();
        assertThat(ed.addListItem("empty", "p", "p")).isTrue();
        Map<String, Object> tree = parse(ed.text());
        assertThat(tree).containsEntry("list-nested", List.of("x", "y", "z")).containsEntry("flow", List.of("m", "n", "o"))
                .containsEntry("empty", List.of("p"));
        assertThat(ed.text()).contains("  - y\n  - z\n");
    }

    @Test
    void keepsCrLfAndMissingTrailingNewline() {
        String crlf = "a: 1\r\nb: 2";
        assertThat(YamlKeyEditor.of(crlf).setScalar("b", "3").text()).isEqualTo("a: 1\r\nb: 3");
    }

    @Test
    void quotesOnlyWhenNeeded() {
        assertThat(YamlKeyEditor.quote("1.21.4")).isEqualTo("1.21.4");
        assertThat(YamlKeyEditor.quote("BetterModel/build.zip")).isEqualTo("BetterModel/build.zip");
        assertThat(YamlKeyEditor.quote("true")).isEqualTo("\"true\"");
        assertThat(YamlKeyEditor.quote("a b")).isEqualTo("\"a b\"");
        assertThat(YamlKeyEditor.literal("false")).isEqualTo("false");
        assertThat(YamlKeyEditor.literal("3000")).isEqualTo("3000");
        assertThat(YamlKeyEditor.literal("1.21.4")).isEqualTo("1.21.4");
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> parse(String text) {
        return (Map<String, Object>) new Yaml().load(text);
    }
}
