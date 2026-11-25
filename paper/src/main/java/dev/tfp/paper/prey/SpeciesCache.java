/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.prey;

import dev.tfp.core.animal.AnimalRecord;
import dev.tfp.paper.animal.AnimalPdc;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.persistence.PersistentDataContainer;

/**
 * The species of each of our animals, read from its record once and kept until the entity is removed (a species never
 * changes). The senses ask it for every entity in a reading, so a dense herd costs one record read per animal instead of
 * one per animal per observer per period (PAR-FAUNA-26). Thread-safe; the modules call {@link #forget} on
 * {@code EntityRemoveEvent}.
 */
public final class SpeciesCache {
    private static final Map<UUID, String> OURS = new ConcurrentHashMap<>();
    private static final Map<EntityType, String> VANILLA = new ConcurrentHashMap<>();

    private SpeciesCache() {
    }

    /** Our species id ({@code tfc:cow}) when the entity carries an animal record, else null. */
    public static String ours(Entity e) {
        String s = OURS.get(e.getUniqueId());
        if (s != null) {
            return s;
        }
        PersistentDataContainer pdc = e.getPersistentDataContainer();
        if (!AnimalPdc.has(pdc)) {
            return null;
        }
        Optional<AnimalRecord> r = AnimalPdc.read(pdc);
        if (r.isEmpty()) {
            return null;
        }
        OURS.put(e.getUniqueId(), r.get().species());
        return r.get().species();
    }

    /** Our species id for one of our animals, else the vanilla type key. */
    public static String of(Entity e) {
        String s = ours(e);
        return s != null ? s : VANILLA.computeIfAbsent(e.getType(), t -> t.getKey().toString());
    }

    public static void forget(UUID id) {
        OURS.remove(id);
    }
}
