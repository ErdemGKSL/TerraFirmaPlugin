/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.recipe.fluid;

import com.google.gson.JsonObject;
import dev.tfp.core.json.Codec;
import dev.tfp.core.json.Codecs;
import dev.tfp.core.json.MapCodec;
import dev.tfp.core.tag.TagType;
import java.util.Optional;

/**
 * A fluid output ({@code FluidStack.CODEC}): {@code {"id": fluid, "amount": mB, "components": {...}}}.
 * Components are kept as raw JSON until something needs them.
 */
public record FluidStackTemplate(String id, int amount, Optional<JsonObject> components) {
    public FluidStackTemplate {
        components = components.map(JsonObject::deepCopy);
    }

    public static final Codec<FluidStackTemplate> CODEC = ((MapCodec<FluidStackTemplate>) r -> new FluidStackTemplate(
            r.required("id", Codecs.ref(TagType.FLUID.directory())),
            r.required("amount", Codecs.POSITIVE_INT),
            r.optional("components", Codecs.JSON_OBJECT))).codec();
}
