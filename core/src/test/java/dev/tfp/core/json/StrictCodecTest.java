/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.json;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.gson.JsonElement;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class StrictCodecTest {
    record Point(int x, float y) {
    }

    static final Codec<Point> POINT = ((MapCodec<Point>) r -> new Point(r.required("x", Codecs.INT), r.optional("y", Codecs.FLOAT, 0f))).codec();

    static JsonElement json(String text) {
        return StrictJson.parse(text, "test.json");
    }

    @Test
    void parsesStrictJsonOnly() {
        assertThat(json("{\"a\": [1, 2.5, true, null, \"s\"]}").getAsJsonObject().get("a").getAsJsonArray()).hasSize(5);
        assertThatThrownBy(() -> json("{a: 1}")).isInstanceOf(JsonDataException.class).hasMessageContaining("malformed");
        assertThatThrownBy(() -> json("{\"a\": 1,}")).isInstanceOf(JsonDataException.class);
        assertThatThrownBy(() -> json("// comment\n{}")).isInstanceOf(JsonDataException.class);
        assertThatThrownBy(() -> json("{} {}")).isInstanceOf(JsonDataException.class);
    }

    @Test
    void duplicateKeysFail() {
        assertThatThrownBy(() -> json("{\"a\": 1, \"a\": 2}"))
                .isInstanceOf(JsonDataException.class).hasMessageContaining("duplicate key 'a'");
    }

    @Test
    void decodesAndAppliesDefaults() {
        assertThat(POINT.decode(json("{\"x\": 3}"), "p")).isEqualTo(new Point(3, 0f));
        assertThat(POINT.decode(json("{\"x\": 3, \"y\": 1.5}"), "p")).isEqualTo(new Point(3, 1.5f));
    }

    @Test
    void unknownKeysFailWithPath() {
        Codec<List<Point>> list = POINT.listOf();
        assertThatThrownBy(() -> list.decode(json("[{\"x\": 1}, {\"x\": 2, \"z\": 0}]"), "f.json"))
                .isInstanceOf(JsonDataException.class)
                .hasMessage("f.json $[1]: unknown key(s) [z]");
    }

    @Test
    void typesAreStrict() {
        assertThatThrownBy(() -> POINT.decode(json("{\"x\": \"3\"}"), "p")).hasMessageContaining("$.x: expected a number");
        assertThatThrownBy(() -> POINT.decode(json("{\"x\": 3.5}"), "p")).hasMessageContaining("expected an integer");
        assertThatThrownBy(() -> POINT.decode(json("{\"x\": null}"), "p")).hasMessageContaining("null is not allowed");
        assertThatThrownBy(() -> POINT.decode(json("{}"), "p")).hasMessageContaining("missing required key 'x'");
        assertThatThrownBy(() -> Codecs.BOOL.decode(json("1"), "p")).hasMessageContaining("expected a boolean");
        assertThatThrownBy(() -> Codecs.FLOAT.decode(json("1e400"), "p")).hasMessageContaining("float range");
    }

    @Test
    void idsAreValidatedAndNormalised() {
        assertThat(Codecs.ID.decode(json("\"stone\""), "p")).isEqualTo("minecraft:stone");
        assertThat(Codecs.ID.decode(json("\"tfc:rock/raw/granite\""), "p")).isEqualTo("tfc:rock/raw/granite");
        assertThatThrownBy(() -> Codecs.ID.decode(json("\"tfc:Rock\""), "p")).hasMessageContaining("invalid character 'R'");
        assertThatThrownBy(() -> Codecs.ID.decode(json("\"tfc:\""), "p")).hasMessageContaining("empty");
    }

    @Test
    void referencesAreRecordedWithLocation() {
        List<Reference> refs = new ArrayList<>();
        Codec<List<String>> items = Codecs.ref("item").listOf();
        items.decode(json("[\"tfc:a\", \"tfc:b\"]"), DecodeContext.root("r.json", refs::add));
        assertThat(refs).containsExactly(
                new Reference("item", "tfc:a", false, "r.json $[0]"),
                new Reference("item", "tfc:b", false, "r.json $[1]"));
        assertThatThrownBy(() -> Codecs.tagRef("item").decode(json("\"#c:x\""), "p")).hasMessageContaining("must not start with '#'");
    }

    @Test
    void typeDispatchRejectsUnknownTypes() {
        TypeDispatch<String> d = TypeDispatch.<String>builder("thing").unit("tfc:a", "A").build();
        Codec<String> codec = d.codec();
        assertThat(codec.decode(json("{\"type\": \"tfc:a\"}"), "p")).isEqualTo("A");
        assertThatThrownBy(() -> codec.decode(json("{\"type\": \"tfc:b\"}"), "p"))
                .hasMessage("p $.type: unknown thing type 'tfc:b' (known: [tfc:a])");
        assertThatThrownBy(() -> codec.decode(json("{\"type\": \"tfc:a\", \"x\": 1}"), "p")).hasMessageContaining("unknown key(s) [x]");
    }

    @Test
    void schemaRecordsKeepFieldsAndRunChecks() {
        ObjectSchema schema = ObjectSchema.of("s", ObjectSchema.required("a", Codecs.INT), ObjectSchema.optional("b", Codecs.STRING))
                .checked((rec, ctx) -> {
                    if (rec.require("a", Integer.class) < 0) {
                        throw ctx.error("a must be >= 0");
                    }
                });
        SchemaRecord rec = schema.codec().decode(json("{\"a\": 2}"), "p");
        assertThat(rec.schema()).isEqualTo("s");
        assertThat(rec.get("a", Integer.class)).contains(2);
        assertThat(rec.has("b")).isFalse();
        assertThatThrownBy(() -> schema.codec().decode(json("{\"a\": -1}"), "p")).hasMessageContaining("a must be >= 0");
    }
}
