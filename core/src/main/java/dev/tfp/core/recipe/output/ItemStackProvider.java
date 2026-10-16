/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/recipes/outputs/ItemStackProvider.java @ c3b4d9536
 */
package dev.tfp.core.recipe.output;

import com.google.gson.JsonElement;
import dev.tfp.core.json.Codec;
import dev.tfp.core.json.DecodeContext;
import dev.tfp.core.json.ObjectReader;
import dev.tfp.core.recipe.hooks.ModifierHooks;
import java.util.List;
import java.util.Optional;

/**
 * TFC {@code ItemStackProvider}: an optional base stack plus a list of {@link ItemStackModifier}s.
 * JSON is either a plain item stack ({@code {"id": ..., "count": ...}}) or
 * {@code {"stack": {...}?, "modifiers": [...]}}; the {@code modifiers} key selects the second form.
 */
public record ItemStackProvider(Optional<ItemStackTemplate> stack, List<ItemStackModifier> modifiers) {
    public ItemStackProvider {
        modifiers = List.copyOf(modifiers);
    }

    public static final Codec<ItemStackProvider> CODEC = new Codec<>() {
        @Override
        public ItemStackProvider decode(JsonElement json, DecodeContext ctx) {
            if (json.isJsonObject() && json.getAsJsonObject().has("modifiers")) {
                ObjectReader r = ObjectReader.of(json, ctx);
                ItemStackProvider p = new ItemStackProvider(
                        r.optional("stack", ItemStackTemplate.CODEC),
                        r.required("modifiers", ItemStackModifier.CODEC.listOf()));
                r.finish();
                return p;
            }
            return new ItemStackProvider(Optional.of(ItemStackTemplate.CODEC.decode(json, ctx)), List.of());
        }
    };

    /**
     * TFC {@code getStack(input, context)}: the base stack, its count multiplied by the input count
     * (when there is an input), then every modifier in order.
     */
    public <S> S getStack(S input, ItemStackModifier.Context context, ModifierHooks<S> hooks) {
        S output = stack.map(hooks::create).orElseGet(hooks::empty);
        if (!hooks.isEmpty(input) && !hooks.isEmpty(output)) {
            output = hooks.withCount(output, hooks.count(output) * hooks.count(input));
        }
        for (ItemStackModifier modifier : modifiers) {
            output = modifier.apply(output, input, context, hooks);
        }
        return output;
    }

    /** TFC {@code getSingleStack}: the input treated as a single item. */
    public <S> S getSingleStack(S input, ModifierHooks<S> hooks) {
        S single = hooks.isEmpty(input) ? input : hooks.withCount(input, 1);
        return getStack(single, ItemStackModifier.Context.DEFAULT, hooks);
    }

    public boolean dependsOnInput() {
        for (ItemStackModifier m : modifiers) {
            if (m.dependsOnInput()) {
                return true;
            }
        }
        return false;
    }
}
