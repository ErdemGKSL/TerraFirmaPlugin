/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/util/collections/IndirectHashCollection.java @ c3b4d9536 (key → recipes,
 * reloaded with the data) and src/main/java/net/dries007/tfc/common/recipes/RecipeHelpers.java @ c3b4d9536
 * (getRecipe: the first candidate that matches)
 */
package dev.tfp.paper.recipe;

import dev.tfp.core.json.SchemaRecord;
import dev.tfp.core.recipe.block.BlockIngredient;
import dev.tfp.core.recipe.ingredient.Ingredient;
import dev.tfp.core.recipe.ingredient.IngredientContext;
import dev.tfp.core.recipe.ingredient.SizedIngredient;
import dev.tfp.core.registry.DefinitionKinds;
import dev.tfp.core.registry.RegistrySnapshot;
import dev.tfp.core.tag.TagLookup;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * An immutable index of every loaded recipe by type and input key (TFC's {@code IndirectHashCollection} caches, for
 * every type). Built off-thread from one {@link RegistrySnapshot} and one tag snapshot; any thread may read it.
 *
 * <p>Candidate lists are in recipe-id order, and {@link #first} returns the first candidate that matches, so overlapping
 * recipes resolve to the lowest id. TFC returns the first in its recipe manager's iteration order, which is the hash
 * order of the ids (a recorded divergence: ours is stable across reloads and servers).
 */
public final class RecipeIndex {
    /** An index of nothing (before the first data load). */
    public static final RecipeIndex EMPTY = new RecipeIndex(RegistrySnapshot.EMPTY, Map.of());

    /** One key space of one type: recipe ordinals by key, plus the ordinals that match any key. */
    private record Space(Map<String, int[]> byKey, int[] any) {
        int[] get(String key) {
            int[] keyed = byKey.getOrDefault(key, EMPTY_INTS);
            return any.length == 0 ? keyed : keyed.length == 0 ? any : merge(keyed, any);
        }
    }

    private record TypeIndex(List<RecipeEntry> recipes, Space items, Space blocks, Space fluids, Space keys) {
    }

    private static final int[] EMPTY_INTS = new int[0];

    private final RegistrySnapshot registry;
    private final Map<String, TypeIndex> types;

    private RecipeIndex(RegistrySnapshot registry, Map<String, TypeIndex> types) {
        this.registry = registry;
        this.types = Map.copyOf(types);
    }

    /** Builds the index of every recipe in {@code registry}; tag ingredients are expanded with {@code tags}. */
    public static RecipeIndex build(RegistrySnapshot registry, TagMembers tags) {
        Map<String, List<RecipeEntry>> byType = new TreeMap<>();
        // Registry ids sorted: TreeMap keeps the per-type lists in id order.
        Map<String, SchemaRecord> sorted = new TreeMap<>(registry.get(DefinitionKinds.RECIPE).asMap());
        for (Map.Entry<String, SchemaRecord> e : sorted.entrySet()) {
            String type = e.getValue().schema();
            byType.computeIfAbsent(type, k -> new ArrayList<>()).add(new RecipeEntry(e.getKey(), type, e.getValue()));
        }
        Map<String, TypeIndex> types = new HashMap<>();
        for (Map.Entry<String, List<RecipeEntry>> e : byType.entrySet()) {
            RecipeTypes.KeySpec spec = RecipeTypes.spec(e.getKey());
            List<RecipeEntry> recipes = List.copyOf(e.getValue());
            types.put(e.getKey(), new TypeIndex(recipes,
                    space(recipes, spec.item(), v -> IngredientKeys.itemValue(v, tags)),
                    space(recipes, spec.block(), v -> IngredientKeys.blockValue(v, tags)),
                    space(recipes, spec.fluid(), v -> IngredientKeys.fluidValue(v, tags)),
                    space(recipes, spec.key().map(List::of).orElse(List.of()),
                            v -> v instanceof String s ? IngredientKeys.Keys.of(Set.of(s)) : IngredientKeys.Keys.ANY)));
        }
        return new RecipeIndex(registry, types);
    }

    private static Space space(List<RecipeEntry> recipes, List<String> fields,
            Function<Object, IngredientKeys.Keys> keysOf) {
        Map<String, List<Integer>> byKey = new HashMap<>();
        List<Integer> any = new ArrayList<>();
        for (int i = 0; i < recipes.size(); i++) {
            IngredientKeys.Keys keys = IngredientKeys.Keys.NONE;
            if (fields.isEmpty()) {
                keys = IngredientKeys.Keys.ANY;
            }
            Map<String, Object> values = recipes.get(i).recipe().values();
            for (String f : fields) {
                keys = keys.union(keysOf.apply(values.get(f)));
            }
            if (keys.any()) {
                any.add(i);
            } else {
                for (String k : keys.ids()) {
                    byKey.computeIfAbsent(k, x -> new ArrayList<>()).add(i);
                }
            }
        }
        Map<String, int[]> out = new HashMap<>(byKey.size() * 2);
        byKey.forEach((k, v) -> out.put(k, ints(v)));
        return new Space(out, ints(any));
    }

    private static int[] ints(List<Integer> list) {
        int[] a = new int[list.size()];
        for (int i = 0; i < a.length; i++) {
            a[i] = list.get(i);
        }
        Arrays.sort(a);
        return a;
    }

    private static int[] merge(int[] a, int[] b) {
        int[] out = new int[a.length + b.length];
        int i = 0;
        int j = 0;
        int n = 0;
        while (i < a.length || j < b.length) {
            if (j >= b.length || (i < a.length && a[i] < b[j])) {
                out[n++] = a[i++];
            } else {
                out[n++] = b[j++];
            }
        }
        return out;
    }

    /** The registry snapshot the index was built from (its recipes are the ones returned). */
    public RegistrySnapshot registry() {
        return registry;
    }

    /** The recipe types present. */
    public Set<String> types() {
        return Set.copyOf(types.keySet());
    }

    /** Every recipe of {@code type} in id order. */
    public List<RecipeEntry> all(String type) {
        TypeIndex t = types.get(type);
        return t == null ? List.of() : t.recipes();
    }

    public int size(String type) {
        return all(type).size();
    }

    public Optional<RecipeEntry> byId(String id) {
        return registry.get(DefinitionKinds.RECIPE).get(id).map(r -> new RecipeEntry(id, r.schema(), r));
    }

    /** Candidates of {@code type} whose item ingredients may match item {@code itemId}, in id order. */
    public List<RecipeEntry> byItem(String type, String itemId) {
        return view(type, TypeIndex::items, itemId);
    }

    /** Candidates whose block ingredient may match {@code blockId}, in id order. */
    public List<RecipeEntry> byBlock(String type, String blockId) {
        return view(type, TypeIndex::blocks, blockId);
    }

    /** Candidates whose fluid ingredients may match {@code fluidId}, in id order. */
    public List<RecipeEntry> byFluid(String type, String fluidId) {
        return view(type, TypeIndex::fluids, fluidId);
    }

    /** Candidates by the type's string key ({@code knapping_type} for knapping), in id order. */
    public List<RecipeEntry> byKey(String type, String key) {
        return view(type, TypeIndex::keys, key);
    }

    private List<RecipeEntry> view(String type, Function<TypeIndex, Space> space, String key) {
        TypeIndex t = types.get(type);
        if (t == null || key == null) {
            return List.of();
        }
        int[] ordinals = space.apply(t).get(key);
        List<RecipeEntry> recipes = t.recipes();
        return new AbstractList<>() {
            @Override
            public RecipeEntry get(int index) {
                return recipes.get(ordinals[index]);
            }

            @Override
            public int size() {
                return ordinals.length;
            }
        };
    }

    /** TFC {@code RecipeHelpers.getRecipe}: the first candidate accepted by {@code test}. */
    public static Optional<RecipeEntry> first(Collection<RecipeEntry> candidates, Predicate<RecipeEntry> test) {
        for (RecipeEntry r : candidates) {
            if (test.test(r)) {
                return Optional.of(r);
            }
        }
        return Optional.empty();
    }

    /**
     * The first recipe of {@code type} whose item ingredient accepts {@code stack} (TFC {@code HeatingRecipe.getRecipe},
     * {@code QuernRecipe.getRecipe}, {@code LoomRecipe.getRecipe}, ...). For types with several item fields, any
     * field that accepts the stack counts; a sized ingredient's count is not checked (devices check amounts).
     */
    public <S> Optional<RecipeEntry> first(String type, S stack, IngredientContext<S> ctx) {
        return first(type, stack, ctx, r -> true);
    }

    /** {@link #first(String, Object, IngredientContext)} with an extra condition (tier, fluid, mode...). */
    public <S> Optional<RecipeEntry> first(String type, S stack, IngredientContext<S> ctx, Predicate<RecipeEntry> extra) {
        String id = ctx.itemId(stack);
        List<String> fields = RecipeTypes.spec(type).item();
        return first(byItem(type, id), r -> acceptsItem(r, fields, stack, ctx) && extra.test(r));
    }

    /** The first recipe of {@code type} whose block ingredient accepts {@code blockId} (TFC {@code CollapseRecipe.getRecipe}). */
    public Optional<RecipeEntry> firstForBlock(String type, String blockId, TagLookup tags, Predicate<RecipeEntry> extra) {
        List<String> fields = RecipeTypes.spec(type).block();
        return first(byBlock(type, blockId), r -> {
            for (String f : fields) {
                if (r.recipe().values().get(f) instanceof BlockIngredient b && b.test(blockId, tags)) {
                    return extra.test(r);
                }
            }
            return fields.isEmpty() && extra.test(r);
        });
    }

    private static <S> boolean acceptsItem(RecipeEntry r, List<String> fields, S stack, IngredientContext<S> ctx) {
        if (fields.isEmpty()) {
            return true;
        }
        for (String f : fields) {
            if (accepts(r.recipe().values().get(f), stack, ctx)) {
                return true;
            }
        }
        return false;
    }

    /** Whether a field value (ingredient, sized ingredient, list, shaped key) has an ingredient accepting {@code stack}. */
    public static <S> boolean accepts(Object value, S stack, IngredientContext<S> ctx) {
        return switch (value) {
            case null -> false;
            case Ingredient i -> i.test(stack, ctx);
            case SizedIngredient s -> s.ingredient().test(stack, ctx);
            case Optional<?> o -> o.isPresent() && accepts(o.get(), stack, ctx);
            case Map<?, ?> m -> anyAccepts(m.values(), stack, ctx);
            case Collection<?> c -> anyAccepts(c, stack, ctx);
            default -> false;
        };
    }

    private static <S> boolean anyAccepts(Collection<?> values, S stack, IngredientContext<S> ctx) {
        for (Object v : values) {
            if (accepts(v, stack, ctx)) {
                return true;
            }
        }
        return false;
    }
}
