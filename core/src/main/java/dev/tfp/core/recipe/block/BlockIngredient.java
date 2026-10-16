/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/recipes/ingredients/BlockIngredient.java @ c3b4d9536
 */
package dev.tfp.core.recipe.block;

import dev.tfp.core.json.Codec;
import dev.tfp.core.json.Codecs;
import dev.tfp.core.json.Ids;
import dev.tfp.core.tag.TagLookup;
import dev.tfp.core.tag.TagType;
import java.util.List;
import java.util.Optional;

/**
 * TFC {@code BlockIngredient}: either an array of block ids or a {@code "#tag"} string. Used by
 * collapse/landslide/chisel recipes and support/lamp-fuel definitions.
 *
 * @param blocks the listed blocks (empty when a tag is used)
 * @param tag    the block tag (without {@code #}), if this is the tag form
 */
public record BlockIngredient(List<String> blocks, Optional<String> tag) {
    public BlockIngredient {
        blocks = List.copyOf(blocks);
    }

    public boolean test(String blockId, TagLookup tags) {
        return tag.map(t -> tags.contains(TagType.BLOCK, t, blockId)).orElseGet(() -> blocks.contains(blockId));
    }

    private static final Codec<List<String>> LIST = Codecs.ref(TagType.BLOCK.directory()).listOf(1, Integer.MAX_VALUE)
            .validate(l -> l.stream().distinct().count() == l.size(), "duplicate block in block ingredient");

    public static final Codec<BlockIngredient> CODEC = (json, ctx) -> {
        if (json.isJsonArray()) {
            return new BlockIngredient(LIST.decode(json, ctx), Optional.empty());
        }
        String raw = Codecs.STRING.decode(json, ctx);
        if (!raw.startsWith("#")) {
            throw ctx.error("expected an array of blocks or a '#tag' string, got \"" + raw + "\"");
        }
        String tag;
        try {
            tag = Ids.parse(raw.substring(1));
        } catch (IllegalArgumentException e) {
            throw ctx.error(e.getMessage());
        }
        ctx.reference(TagType.BLOCK.directory(), tag, true);
        return new BlockIngredient(List.of(), Optional.of(tag));
    };
}
