/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.recipe.output;

import com.google.gson.JsonObject;
import dev.tfp.core.json.Codec;
import dev.tfp.core.json.Codecs;
import dev.tfp.core.json.MapCodec;
import dev.tfp.core.tag.TagType;
import java.util.Optional;

/**
 * An item stack written in data ({@code ItemStack.CODEC}): {@code {"id": item, "count": 1..99,
 * "components": {...}}}. Components stay raw JSON; the Paper side turns them into item data.
 */
public record ItemStackTemplate(String id, int count, Optional<JsonObject> components) {
    public ItemStackTemplate {
        components = components.map(JsonObject::deepCopy);
    }

    public static final Codec<ItemStackTemplate> CODEC = ((MapCodec<ItemStackTemplate>) r -> new ItemStackTemplate(
            r.required("id", Codecs.ref(TagType.ITEM.directory())),
            r.optional("count", Codecs.intRange(1, 99), 1),
            r.optional("components", Codecs.JSON_OBJECT))).codec();
}
