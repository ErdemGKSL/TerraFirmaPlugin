/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/util/data/Fertilizer.java @ c3b4d9536 (the record and its lookup by stack)
 */
package dev.tfp.paper.farm.fertilizer;

import dev.tfp.core.json.SchemaRecord;
import dev.tfp.core.recipe.ingredient.Ingredient;
import dev.tfp.core.recipe.ingredient.IngredientContext;
import dev.tfp.core.registry.DefinitionKinds;
import dev.tfp.core.registry.Registries;
import dev.tfp.core.registry.Registry;
import dev.tfp.core.registry.RegistrySnapshot;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Supplier;
import org.bukkit.inventory.ItemStack;

/** The {@code fertilizer} data definitions (TFC {@code Fertilizer}), cached per registry version. */
public final class Fertilizers {
    /** One fertilizer: what accepts it and the nutrients one item gives. */
    public record Fertilizer(String id, Ingredient ingredient, float nitrogen, float phosphorus, float potassium) {
    }

    private record Entry(long version, List<Fertilizer> list) {
    }

    private final Supplier<Registries> registries;
    private volatile Entry entry = new Entry(0, List.of());

    /** @param registries the plugin's registries, or a supplier that returns null before they exist */
    public Fertilizers(Supplier<Registries> registries) {
        this.registries = registries;
    }

    /** All definitions, sorted by id. */
    public List<Fertilizer> all() {
        Registries r = registries.get();
        if (r == null || r.version() == 0) {
            return List.of();
        }
        Entry e = entry;
        if (e.version() == r.version()) {
            return e.list();
        }
        List<Fertilizer> list = fromRegistry(r.get());
        entry = new Entry(r.version(), list);
        return list;
    }

    static List<Fertilizer> fromRegistry(RegistrySnapshot snapshot) {
        Registry<SchemaRecord> source = snapshot.get(DefinitionKinds.key("fertilizer"));
        Map<String, Fertilizer> sorted = new TreeMap<>();
        source.asMap().forEach((id, r) -> r.get("ingredient", Ingredient.class).ifPresent(ingredient ->
            sorted.put(id, new Fertilizer(id, ingredient, r.get("nitrogen", Float.class).orElse(0f),
                r.get("phosphorus", Float.class).orElse(0f), r.get("potassium", Float.class).orElse(0f)))));
        return new ArrayList<>(sorted.values());
    }

    /** TFC {@code Fertilizer.get}: the first definition whose ingredient accepts the stack. */
    public Optional<Fertilizer> find(ItemStack stack, IngredientContext<ItemStack> context) {
        if (stack == null || stack.isEmpty()) {
            return Optional.empty();
        }
        for (Fertilizer f : all()) {
            if (f.ingredient().test(stack, context)) {
                return Optional.of(f);
            }
        }
        return Optional.empty();
    }
}
