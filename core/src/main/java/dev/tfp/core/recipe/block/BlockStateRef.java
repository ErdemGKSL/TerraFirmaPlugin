/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.recipe.block;

import dev.tfp.core.json.Codec;
import dev.tfp.core.json.Codecs;
import dev.tfp.core.json.Ids;
import dev.tfp.core.tag.TagType;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A block state written as a string, {@code "ns:block"} or {@code "ns:block[prop=value,...]"}
 * (TFC {@code Codecs.BLOCK_STATE}). Property names/values are checked against the block later,
 * when CraftEngine block definitions are known.
 */
public record BlockStateRef(String block, Map<String, String> properties) {
    public BlockStateRef {
        properties = Collections.unmodifiableMap(new LinkedHashMap<>(properties));
    }

    public static final Codec<BlockStateRef> CODEC = (json, ctx) -> {
        String raw = Codecs.STRING.decode(json, ctx);
        int open = raw.indexOf('[');
        String id = open < 0 ? raw : raw.substring(0, open);
        Map<String, String> props = new LinkedHashMap<>();
        if (open >= 0) {
            if (!raw.endsWith("]")) {
                throw ctx.error("unterminated block state properties: " + raw);
            }
            String body = raw.substring(open + 1, raw.length() - 1);
            if (!body.isEmpty()) {
                for (String pair : body.split(",", -1)) {
                    int eq = pair.indexOf('=');
                    if (eq <= 0 || eq == pair.length() - 1 || props.put(pair.substring(0, eq), pair.substring(eq + 1)) != null) {
                        throw ctx.error("malformed block state property '" + pair + "' in " + raw);
                    }
                }
            }
        }
        String block;
        try {
            block = Ids.parse(id);
        } catch (IllegalArgumentException e) {
            throw ctx.error(e.getMessage());
        }
        ctx.reference(TagType.BLOCK.directory(), block, false);
        return new BlockStateRef(block, props);
    };
}
