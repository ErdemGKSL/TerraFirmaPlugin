/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.tag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.tfp.core.json.StrictJson;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TagResolverTest {
    /** Everything in {@code tfc:}/{@code minecraft:} exists; tags in {@code minecraft:} are external. */
    static final TagResolver.IdTable IDS = new TagResolver.IdTable() {
        @Override
        public boolean hasId(TagType type, String id) {
            return !id.startsWith("missing:");
        }

        @Override
        public boolean hasExternalTag(TagType type, String tag) {
            return tag.startsWith("minecraft:");
        }
    };

    static TagFile file(String json) {
        return TagFile.CODEC.decode(StrictJson.parse(json, "t"), "t.json");
    }

    @Test
    void parsesAllEntryForms() {
        TagFile f = file("{\"replace\": true, \"values\": [\"tfc:a\", \"#tfc:b\", {\"id\": \"#tfc:c\", \"required\": false}], \"remove\": [\"tfc:d\"]}");
        assertThat(f.replace()).isTrue();
        assertThat(f.values()).containsExactly(
                new TagFile.Entry("tfc:a", false, true), new TagFile.Entry("tfc:b", true, true), new TagFile.Entry("tfc:c", true, false));
        assertThat(f.remove()).containsExactly(new TagFile.Entry("tfc:d", false, true));
        assertThatThrownBy(() -> file("{\"values\": [], \"extra\": 1}")).hasMessageContaining("unknown key(s) [extra]");
        assertThatThrownBy(() -> file("{\"replace\": false}")).hasMessageContaining("missing required key 'values'");
        assertThatThrownBy(() -> file("{\"values\": [\"#Bad\"]}")).hasMessageContaining("invalid character");
    }

    @Test
    void resolvesNestedTagsAndRemove() {
        TagResolver r = new TagResolver();
        r.add(TagType.ITEM, "tfc:fruit", file("{\"values\": [\"tfc:banana\", \"tfc:cherry\"]}"), "fruit.json");
        r.add(TagType.ITEM, "tfc:foods", file("{\"values\": [\"#tfc:fruit\", \"tfc:bread\", \"#minecraft:fish\"], \"remove\": [\"tfc:cherry\"]}"), "foods.json");
        r.add(TagType.ITEM, "tfc:all", file("{\"values\": [\"#tfc:foods\", \"tfc:stick\"], \"remove\": [\"#tfc:fruit\"]}"), "all.json");
        TagResolver.Result result = r.resolve(IDS);
        assertThat(result.errors()).isEmpty();
        TagRegistry tags = result.registry();
        assertThat(tags.members(TagType.ITEM, "tfc:foods")).containsExactly("tfc:banana", "tfc:bread");
        assertThat(tags.members(TagType.ITEM, "tfc:all")).containsExactly("tfc:bread", "tfc:stick");
        assertThat(tags.contains(TagType.ITEM, "tfc:fruit", "tfc:cherry")).isTrue();
        assertThat(tags.contains(TagType.BLOCK, "tfc:fruit", "tfc:cherry")).as("types are separate").isFalse();
        assertThat(tags.exists(TagType.ITEM, "minecraft:fish")).as("external tag used in our data").isTrue();
        assertThat(tags.exists(TagType.ITEM, "tfc:nope")).isFalse();
    }

    @Test
    void laterFilesMergeAndReplaceDropsEarlier() {
        TagResolver r = new TagResolver();
        r.add(TagType.BLOCK, "tfc:t", file("{\"values\": [\"tfc:a\"]}"), "pack1");
        r.add(TagType.BLOCK, "tfc:t", file("{\"values\": [\"tfc:b\"]}"), "pack2");
        assertThat(r.resolve(IDS).registry().members(TagType.BLOCK, "tfc:t")).containsExactly("tfc:a", "tfc:b");
        r.add(TagType.BLOCK, "tfc:t", file("{\"replace\": true, \"values\": [\"tfc:c\"]}"), "pack3");
        assertThat(r.resolve(IDS).registry().members(TagType.BLOCK, "tfc:t")).containsExactly("tfc:c");
    }

    @Test
    void cyclesAreReportedWithTheirPath() {
        TagResolver r = new TagResolver();
        r.add(TagType.FLUID, "tfc:a", file("{\"values\": [\"#tfc:b\"]}"), "a");
        r.add(TagType.FLUID, "tfc:b", file("{\"values\": [\"#tfc:c\", \"tfc:water\"]}"), "b");
        r.add(TagType.FLUID, "tfc:c", file("{\"values\": [\"#tfc:a\"]}"), "c");
        assertThat(r.resolve(IDS).errors()).containsExactly("fluid tag cycle: #tfc:a -> #tfc:b -> #tfc:c -> #tfc:a");
        TagResolver self = new TagResolver();
        self.add(TagType.ITEM, "tfc:s", file("{\"values\": [\"#tfc:s\"]}"), "s");
        assertThat(self.resolve(IDS).errors()).containsExactly("item tag cycle: #tfc:s -> #tfc:s");
    }

    @Test
    void unresolvedRequiredEntriesFailOptionalOnesAreSkipped() {
        TagResolver r = new TagResolver();
        r.add(TagType.ENTITY_TYPE, "tfc:t", file("""
                {"values": ["missing:x", "#tfc:undefined", {"id": "missing:y", "required": false},
                            {"id": "#tfc:also_undefined", "required": false}, "tfc:ok"]}"""), "t.json");
        TagResolver.Result result = r.resolve(IDS);
        assertThat(result.errors()).containsExactly(
                "unknown entity_type id 'missing:x' in tag #tfc:t [t.json]",
                "unknown entity_type tag '#tfc:undefined' in tag #tfc:t [t.json]");
        assertThat(result.registry().members(TagType.ENTITY_TYPE, "tfc:t")).containsExactly("tfc:ok");
    }

    @Test
    void tfcSampleTagsResolve() throws IOException {
        // TFC files from core/src/test/resources/tfc-sample (see SOURCES.txt there).
        TagResolver r = new TagResolver();
        r.add(TagType.ITEM, "tfc:foods/preserves", sample("tfc/tags/item/foods/preserves.json"), "preserves.json");
        r.add(TagType.BLOCK, "tfc:lamps", sample("tfc/tags/block/lamps.json"), "lamps.json");
        TagRegistry tags = r.resolve(IDS).registry();
        Set<String> preserves = tags.members(TagType.ITEM, "tfc:foods/preserves");
        assertThat(preserves).contains("tfc:jar/blackberry_unsealed", "tfc:jar/melon_slice_unsealed")
                .as("TFC removes peanut via NeoForge 'remove'").doesNotContain("tfc:jar/peanut_unsealed");
        assertThat(tags.contains(TagType.BLOCK, "tfc:lamps", "tfc:metal/lamp/bronze")).isTrue();
    }

    static TagFile sample(String path) throws IOException {
        try (Reader in = new InputStreamReader(Objects.requireNonNull(
                TagResolverTest.class.getResourceAsStream("/tfc-sample/data/" + path), path), StandardCharsets.UTF_8)) {
            return TagFile.CODEC.decode(StrictJson.parse(in, path), path);
        }
    }
}
