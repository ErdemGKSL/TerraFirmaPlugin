/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.guide;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/** PAR-DATA-21: a translated guide takes the structure of the English one and falls back to it per entry. */
class FieldGuideFallbackTest {
    private static FieldGuide.Entry entry(String id, String title, String text) {
        return new FieldGuide.Entry("tfp:a/" + id, "tfp:a", title, List.of(new FieldGuide.Page(null, text)));
    }

    private static FieldGuide guide(String name, String category, FieldGuide.Entry... entries) {
        return new FieldGuide(name, "landing " + name, List.of(
            new FieldGuide.Category("tfp:a", category, "about " + category, List.of(entries))));
    }

    @Test
    void aMissingEntryComesFromTheBaseInTheBasesOrder() {
        FieldGuide en = guide("Field Guide", "Start", entry("one", "One", "1"), entry("two", "Two", "2"),
            entry("three", "Three", "3"));
        FieldGuide zh = guide("野外指南", "开始", entry("three", "三", "三文"),
            entry("one", "一", "一文"));
        FieldGuide merged = zh.withFallback(en);
        assertThat(merged.name()).isEqualTo("野外指南");
        assertThat(merged.landingText()).isEqualTo("landing 野外指南");
        assertThat(merged.categories().getFirst().name()).isEqualTo("开始");
        assertThat(merged.entries()).extracting(FieldGuide.Entry::title).containsExactly("一", "Two", "三");
    }

    @Test
    void aMissingCategoryIsTheBasesAndExtraTranslatedEntriesAreDropped() {
        FieldGuide en = guide("Field Guide", "Start", entry("one", "One", "1"));
        FieldGuide other = new FieldGuide("Guide", "x", List.of(new FieldGuide.Category("tfp:b", "B", "b",
            List.of(entry("extra", "Extra", "e")))));
        FieldGuide merged = other.withFallback(en);
        assertThat(merged.categories()).hasSize(1);
        assertThat(merged.categories().getFirst().name()).isEqualTo("Start");
        assertThat(merged.entries()).extracting(FieldGuide.Entry::id).containsExactly("tfp:a/one");
        assertThat(new GuideBooks(merged, "tfp").book("entry:tfp:a/one")).isNotNull();
    }
}
