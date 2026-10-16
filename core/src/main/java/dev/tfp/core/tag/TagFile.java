/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.tag;

import com.google.gson.JsonElement;
import dev.tfp.core.json.Codec;
import dev.tfp.core.json.Codecs;
import dev.tfp.core.json.DecodeContext;
import dev.tfp.core.json.Ids;
import dev.tfp.core.json.MapCodec;
import dev.tfp.core.json.ObjectReader;
import java.util.List;

/**
 * One tag JSON file: {@code {"replace": false, "values": [...], "remove": [...]}}. Entries are
 * {@code "ns:id"}, {@code "#ns:tag"} or {@code {"id": ..., "required": false}}. {@code remove} is
 * NeoForge's extension (TFC uses it), applied after all values are resolved.
 */
public record TagFile(boolean replace, List<Entry> values, List<Entry> remove) {
    public TagFile {
        values = List.copyOf(values);
        remove = List.copyOf(remove);
    }

    /** A tag entry. {@code required = false} entries are skipped when they don't resolve. */
    public record Entry(String id, boolean tag, boolean required) {
        @Override
        public String toString() {
            return (tag ? "#" : "") + id + (required ? "" : " (optional)");
        }
    }

    public static final Codec<Entry> ENTRY = new Codec<>() {
        @Override
        public Entry decode(JsonElement json, DecodeContext ctx) {
            if (json.isJsonObject()) {
                ObjectReader r = ObjectReader.of(json, ctx);
                Entry e = parse(r.required("id", Codecs.STRING), r.optional("required", Codecs.BOOL, true), r.context().field("id"));
                r.finish();
                return e;
            }
            return parse(Codecs.STRING.decode(json, ctx), true, ctx);
        }

        private Entry parse(String raw, boolean required, DecodeContext ctx) {
            boolean tag = raw.startsWith("#");
            try {
                return new Entry(Ids.parse(tag ? raw.substring(1) : raw), tag, required);
            } catch (IllegalArgumentException e) {
                throw ctx.error(e.getMessage());
            }
        }
    };

    public static final Codec<TagFile> CODEC = ((MapCodec<TagFile>) r -> new TagFile(
            r.optional("replace", Codecs.BOOL, false),
            r.required("values", ENTRY.listOf()),
            r.optional("remove", ENTRY.listOf(), List.of()))).codec();
}
