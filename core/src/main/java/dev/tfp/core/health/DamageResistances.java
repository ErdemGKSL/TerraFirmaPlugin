/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/util/data/EntityDamageResistance.java @ c3b4d9536 (get),
 * src/main/java/net/dries007/tfc/util/data/ItemDamageResistance.java @ c3b4d9536 (get)
 */
package dev.tfp.core.health;

import dev.tfp.core.json.SchemaRecord;
import dev.tfp.core.recipe.fluid.FluidAmount;
import dev.tfp.core.recipe.hooks.FluidHooks;
import dev.tfp.core.recipe.hooks.FoodHooks;
import dev.tfp.core.recipe.hooks.HeatHooks;
import dev.tfp.core.recipe.ingredient.Ingredient;
import dev.tfp.core.recipe.ingredient.IngredientContext;
import dev.tfp.core.registry.DefinitionKinds;
import dev.tfp.core.registry.RegistrySnapshot;
import dev.tfp.core.tag.TagLookup;
import dev.tfp.core.tag.TagType;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/**
 * TFC's {@code entity_damage_resistance} and {@code item_damage_resistance} definitions, loaded from the exported data
 * (datagen copies TFC's generated JSON). {@link #entity} is {@code EntityDamageResistance.get}: the first definition
 * whose entity type tag contains the target; {@link #item} is {@code ItemDamageResistance.get}: the first definition
 * whose ingredient accepts the item. TFC's definitions do not overlap, so the order (sorted by id here) does not change
 * any result. Immutable and thread-safe.
 */
public final class DamageResistances {
    /** One {@code entity_damage_resistance}: an entity type tag and its resistance. */
    public record EntityEntry(String id, String entityTag, PhysicalResistance resistance) {
        public EntityEntry {
            Objects.requireNonNull(entityTag, "entityTag");
            Objects.requireNonNull(resistance, "resistance");
        }
    }

    /** One {@code item_damage_resistance}: an item ingredient and its resistance. */
    public record ItemEntry(String id, Ingredient ingredient, PhysicalResistance resistance) {
        public ItemEntry {
            Objects.requireNonNull(ingredient, "ingredient");
            Objects.requireNonNull(resistance, "resistance");
        }
    }

    public static final DamageResistances EMPTY = new DamageResistances(List.of(), List.of());

    private final List<EntityEntry> entities;
    private final List<ItemEntry> items;

    public DamageResistances(List<EntityEntry> entities, List<ItemEntry> items) {
        this.entities = List.copyOf(entities);
        this.items = List.copyOf(items);
    }

    /** Both tables from {@code snapshot}, sorted by id. */
    public static DamageResistances fromRegistry(RegistrySnapshot snapshot) {
        TreeMap<String, EntityEntry> entities = new TreeMap<>();
        snapshot.get(DefinitionKinds.key("entity_damage_resistance")).asMap().forEach((id, r) ->
                r.get("entity", String.class).ifPresent(tag -> entities.put(id, new EntityEntry(id, tag, damages(r)))));
        TreeMap<String, ItemEntry> items = new TreeMap<>();
        snapshot.get(DefinitionKinds.key("item_damage_resistance")).asMap().forEach((id, r) ->
                r.get("ingredient", Ingredient.class).ifPresent(i -> items.put(id, new ItemEntry(id, i, damages(r)))));
        return new DamageResistances(new ArrayList<>(entities.values()), new ArrayList<>(items.values()));
    }

    /** TFC {@code PhysicalDamage.CODEC}: each field optional, default 0. */
    private static PhysicalResistance damages(SchemaRecord r) {
        return new PhysicalResistance(r.get("crushing", Float.class).orElse(0f),
                r.get("piercing", Float.class).orElse(0f), r.get("slashing", Float.class).orElse(0f));
    }

    public List<EntityEntry> entities() {
        return entities;
    }

    public List<ItemEntry> items() {
        return items;
    }

    /** TFC {@code EntityDamageResistance.get}: the natural resistance of an entity type, empty when none applies. */
    public Optional<PhysicalResistance> entity(String entityTypeId, TagLookup tags) {
        if (entityTypeId == null) {
            return Optional.empty();
        }
        for (EntityEntry e : entities) {
            if (tags.contains(TagType.ENTITY_TYPE, e.entityTag(), entityTypeId)) {
                return Optional.of(e.resistance());
            }
        }
        return Optional.empty();
    }

    /** TFC {@code ItemDamageResistance.get}: the resistance of a worn item by its id, empty when none applies. */
    public Optional<PhysicalResistance> item(String itemId, TagLookup tags) {
        if (itemId == null) {
            return Optional.empty();
        }
        IdContext ctx = new IdContext(tags);
        for (ItemEntry e : items) {
            if (e.ingredient().test(itemId, ctx)) {
                return Optional.of(e.resistance());
            }
        }
        return Optional.empty();
    }

    /** An ingredient context over a bare item id: TFC's resistance ingredients read only the item and its tags. */
    private record IdContext(TagLookup tags) implements IngredientContext<String> {
        private static final FoodHooks<String> NO_FOOD = new FoodHooks<>() {
            @Override
            public boolean isRotten(String stack) {
                return false;
            }

            @Override
            public boolean hasTrait(String stack, String trait) {
                return false;
            }
        };
        private static final HeatHooks<String> NO_HEAT = stack -> 0f;
        private static final FluidHooks<String> NO_FLUID = stack -> Optional.<FluidAmount>empty();

        @Override
        public String itemId(String stack) {
            return stack;
        }

        @Override
        public FoodHooks<String> food() {
            return NO_FOOD;
        }

        @Override
        public HeatHooks<String> heat() {
            return NO_HEAT;
        }

        @Override
        public FluidHooks<String> fluids() {
            return NO_FLUID;
        }
    }
}
