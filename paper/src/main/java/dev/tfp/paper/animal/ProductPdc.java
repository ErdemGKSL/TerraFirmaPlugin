/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.animal;

import dev.tfp.core.animal.AnimalRecord;
import dev.tfp.core.livestock.LivestockSpecies;
import dev.tfp.core.livestock.products.ProductKind;
import dev.tfp.core.livestock.products.ProductRules;
import dev.tfp.core.livestock.products.ProductSpec;
import dev.tfp.core.livestock.products.ProductState;
import java.util.Locale;
import java.util.Optional;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.persistence.PersistentDataType;

/**
 * The product state of an animal (TFC's {@code DATA_PRODUCED}: the player tick of its last milking, shearing or egg) in its
 * PDC, {@code tfp:animal_produced}, next to the animal record (PAR-FAUNA-06). Owning thread.
 */
public final class ProductPdc {
    private static final NamespacedKey KEY = new NamespacedKey("tfp", "animal_produced");

    private ProductPdc() {
    }

    public static ProductState read(Entity e) {
        Long v = e.getPersistentDataContainer().get(KEY, PersistentDataType.LONG);
        return v == null ? ProductState.fresh() : new ProductState(v);
    }

    public static void write(Entity e, ProductState state) {
        e.getPersistentDataContainer().set(KEY, PersistentDataType.LONG, state.producedTick());
    }

    /** The product rules of {@code species} ({@code tfc:cow}), empty if it has none. */
    public static Optional<ProductSpec> specOf(String species) {
        String name = species.substring(species.indexOf(':') + 1).toUpperCase(Locale.ROOT);
        try {
            return ProductSpec.defaults(LivestockSpecies.valueOf(name));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /** Whether a wool animal has been shorn and its wool has not grown back (the model shows no wool). */
    public static boolean isSheared(Entity e, AnimalRecord record, long playerTicks) {
        Optional<ProductSpec> spec = specOf(record.species());
        if (spec.isEmpty() || spec.get().kind() != ProductKind.SHEAR) {
            return false;
        }
        ProductState state = read(e);
        if (state.producedTick() <= 0L) {
            return false;
        }
        LivestockSpecies species = LivestockSpecies.valueOf(
                record.species().substring(record.species().indexOf(':') + 1).toUpperCase(Locale.ROOT));
        return !ProductRules.hasProduct(spec.get(), species, record.state(), state, playerTicks);
    }
}
