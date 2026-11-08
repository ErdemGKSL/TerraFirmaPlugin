/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/util/data/Drinkable.java @ c3b4d9536 (get(Fluid), onDrink),
 * src/main/java/net/dries007/tfc/common/component/food/FoodData.java @ c3b4d9536 (intoxication, mul)
 */
package dev.tfp.core.intox;

import dev.tfp.core.json.SchemaRecord;
import dev.tfp.core.recipe.fluid.FluidIngredient;
import dev.tfp.core.registry.DefinitionKinds;
import dev.tfp.core.registry.Registry;
import dev.tfp.core.registry.RegistrySnapshot;
import dev.tfp.core.tag.TagLookup;
import dev.tfp.core.tag.TagRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The intoxication each drinkable fluid gives (PAR-SURV-08): the {@code intoxication} field of TFC's {@code drinkable}
 * data ({@code alcohol.json}: 4000 ticks for every fluid of {@code #tfp:alcohols}). The lookup is TFC's
 * {@code Drinkable.get}: the first definition, in registry order, whose ingredient covers the fluid. Immutable; any
 * thread.
 *
 * <p>TFC's {@code onDrink(player, mB)} eats {@code food.mul(mB / 25)}, and {@code FoodData.mul} multiplies water,
 * hunger, saturation and nutrients but keeps {@code intoxication}: one drink adds the full value whatever the amount
 * (a 100 mB jug of beer and a 25 mB sip both add 4000 ticks).
 */
public final class Intoxicants {
    /** One drinkable's ingredient and intoxication ticks. */
    public record Entry(FluidIngredient ingredient, int intoxication) {
        public Entry {
            Objects.requireNonNull(ingredient, "ingredient");
        }
    }

    public static final Intoxicants EMPTY = new Intoxicants(List.of(), TagRegistry.EMPTY);

    private final List<Entry> entries;
    private final TagLookup tags;

    public Intoxicants(List<Entry> entries, TagLookup tags) {
        this.entries = List.copyOf(entries);
        this.tags = tags;
    }

    /** Every {@code drinkable} definition of a snapshot, in registry order (the intoxication defaults to 0). */
    public static Intoxicants fromRegistry(RegistrySnapshot snapshot) {
        Registry<SchemaRecord> defs = snapshot.get(DefinitionKinds.key("drinkable"));
        List<Entry> out = new ArrayList<>();
        for (SchemaRecord r : defs.values()) {
            r.get("ingredient", FluidIngredient.class).ifPresent(ingredient -> out.add(new Entry(ingredient,
                    r.get("intoxication", Number.class).orElse(0).intValue())));
        }
        return new Intoxicants(out, snapshot.tags());
    }

    /**
     * The intoxication ticks one drink of {@code fluidId} adds: the first covering definition's value, 0 when no
     * definition covers it. The amount drunk does not matter (see the class comment).
     */
    public int ticksPerDrink(String fluidId) {
        for (Entry e : entries) {
            if (e.ingredient().test(fluidId, tags)) {
                return e.intoxication();
            }
        }
        return 0;
    }

    public int size() {
        return entries.size();
    }
}
