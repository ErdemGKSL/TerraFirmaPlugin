/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.recipe.fluid;

import com.google.gson.JsonElement;
import dev.tfp.core.json.Codec;
import dev.tfp.core.json.Codecs;
import dev.tfp.core.json.DecodeContext;
import dev.tfp.core.json.MapCodec;
import dev.tfp.core.json.ObjectReader;
import dev.tfp.core.tag.TagLookup;
import dev.tfp.core.tag.TagType;
import java.util.List;

/**
 * NeoForge's {@code FluidIngredient} as TFC data uses it: {@code {"fluid": id}}, {@code {"tag": tag}},
 * or an array of those (any of). Other NeoForge fluid ingredient types are not used by TFC and
 * fail the load.
 */
public sealed interface FluidIngredient {
    boolean test(String fluidId, TagLookup tags);

    record Single(String fluid) implements FluidIngredient {
        @Override
        public boolean test(String fluidId, TagLookup tags) {
            return fluid.equals(fluidId);
        }
    }

    record Tag(String tag) implements FluidIngredient {
        @Override
        public boolean test(String fluidId, TagLookup tags) {
            return tags.contains(TagType.FLUID, tag, fluidId);
        }
    }

    record AnyOf(List<FluidIngredient> children) implements FluidIngredient {
        public AnyOf {
            children = List.copyOf(children);
        }

        @Override
        public boolean test(String fluidId, TagLookup tags) {
            for (FluidIngredient c : children) {
                if (c.test(fluidId, tags)) {
                    return true;
                }
            }
            return false;
        }
    }

    /** Object form, reading only its own keys (so {@link SizedFluidIngredient} can add {@code amount}). */
    MapCodec<FluidIngredient> MAP_CODEC = r -> {
        if (r.has("fluid")) {
            return new Single(r.required("fluid", Codecs.ref(TagType.FLUID.directory())));
        }
        if (r.has("tag")) {
            return new Tag(r.required("tag", Codecs.tagRef(TagType.FLUID.directory())));
        }
        if (r.has("type")) {
            throw r.context().field("type").error("unsupported fluid ingredient type " + r.take("type"));
        }
        throw r.context().error("expected a fluid ingredient with 'fluid' or 'tag'");
    };

    Codec<FluidIngredient> CODEC = new Codec<>() {
        private final Codec<List<FluidIngredient>> list = this.listOf(1, Integer.MAX_VALUE);

        @Override
        public FluidIngredient decode(JsonElement json, DecodeContext ctx) {
            if (json.isJsonArray()) {
                return new AnyOf(list.decode(json, ctx));
            }
            ObjectReader r = ObjectReader.of(json, ctx);
            FluidIngredient value = MAP_CODEC.decode(r);
            r.finish();
            return value;
        }
    };
}
