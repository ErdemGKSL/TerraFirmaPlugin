/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/util/data/Drinkable.java @ c3b4d9536 (CODEC defaults, get(Fluid))
 */
package dev.tfp.core.thirst;

import dev.tfp.core.json.SchemaRecord;
import dev.tfp.core.recipe.fluid.FluidIngredient;
import dev.tfp.core.registry.DefinitionKinds;
import dev.tfp.core.registry.Registry;
import dev.tfp.core.registry.RegistrySnapshot;
import dev.tfp.core.tag.TagLookup;
import dev.tfp.core.tag.TagRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The loaded {@code drinkable} definitions and TFC's lookup by fluid ({@code Drinkable.get}: the first definition whose
 * ingredient accepts the fluid). Immutable; any thread.
 */
public final class Drinkables {
    /** Plain water ({@code minecraft:water}, TFC {@code water.json}). */
    public static final String WATER = "minecraft:water";
    /** Salt water ({@code tfc:salt_water}, here {@code tfp:salt_water}). */
    public static final String SALT_WATER = "tfp:salt_water";

    public static final Drinkables EMPTY = new Drinkables(List.of(), TagRegistry.EMPTY);

    private final List<Drinkable> all;
    private final TagLookup tags;

    public Drinkables(List<Drinkable> all, TagLookup tags) {
        this.all = List.copyOf(all);
        this.tags = tags;
    }

    /** The definitions of a registry snapshot ({@code drinkable} kind), in registry order; TFC's codec defaults. */
    public static Drinkables fromRegistry(RegistrySnapshot snapshot) {
        Registry<SchemaRecord> defs = snapshot.get(DefinitionKinds.key("drinkable"));
        List<Drinkable> out = new ArrayList<>();
        for (SchemaRecord r : defs.values()) {
            Optional<FluidIngredient> ingredient = r.get("ingredient", FluidIngredient.class);
            if (ingredient.isEmpty()) {
                continue;
            }
            out.add(new Drinkable(ingredient.get(), num(r, "consume_chance").floatValue(),
                    r.get("may_drink_when_full", Boolean.class).orElse(false), num(r, "water").floatValue(),
                    effects(r)));
        }
        return new Drinkables(out, snapshot.tags());
    }

    private static List<Drinkable.Effect> effects(SchemaRecord r) {
        List<Drinkable.Effect> out = new ArrayList<>();
        if (!(r.get("effects", Object.class).orElse(null) instanceof List<?> list)) {
            return out;
        }
        for (Object o : list) {
            if (o instanceof SchemaRecord e) {
                out.add(new Drinkable.Effect(e.require("effect", String.class), num(e, "duration").intValue(),
                        num(e, "amplifier").intValue(), num(e, "chance").floatValue()));
            }
        }
        return out;
    }

    private static Number num(SchemaRecord r, String field) {
        return r.get(field, Number.class).orElse(0);
    }

    /** TFC {@code Drinkable.get(fluid)}: the first definition covering {@code fluidId}, if any. */
    public Optional<Drinkable> get(String fluidId) {
        for (Drinkable d : all) {
            if (d.ingredient().test(fluidId, tags)) {
                return Optional.of(d);
            }
        }
        return Optional.empty();
    }

    public int size() {
        return all.size();
    }
}
