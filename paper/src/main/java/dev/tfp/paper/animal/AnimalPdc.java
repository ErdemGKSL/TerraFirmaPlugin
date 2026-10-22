/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/entities/livestock/TFCAnimalProperties.java @ c3b4d9536 (the saved animal data)
 */
package dev.tfp.paper.animal;

import dev.tfp.core.animal.AnimalRecord;
import dev.tfp.core.livestock.AnimalState;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * The entity PDC codec of {@link AnimalRecord} (M5-ENT-01). The record lives on the carrier under {@value #KEY} as a
 * nested container, readable with {@code /data get entity}:
 * <pre>
 * tfp:animal = { tfp:v: int, tfp:species: string, tfp:male: bool, tfp:familiarity: float, tfp:familiarityTick: long,
 *   tfp:birthTick: long, tfp:oldTick: long, tfp:lastFedTick: long, tfp:lastMateTick: long, tfp:uses: int,
 *   tfp:fertilized: bool, tfp:pregnantDay: long, tfp:size: int, tfp:variant: string, tfp:baby: bool,
 *   tfp:growtick: long }
 * </pre>
 * Keys are {@link AnimalRecord#toMap()}'s, lower-cased (PDC keys are {@code [a-z0-9._-]}). Other PDC entries are kept.
 *
 * <p>Threads: the carrier's owning thread.
 *
 * <h2>Version history</h2>
 * <ol>
 *   <li>the fields above without {@code baby} and {@code growtick} (M5-ENT-01)</li>
 *   <li>{@code baby} and {@code growtick}: TFC {@code WildAnimal}'s baby flag and its lazy grow-up tick; version 1
 *       entries read as not a baby</li>
 * </ol>
 */
public final class AnimalPdc {
    public static final String KEY = "tfp:animal";
    private static final NamespacedKey ROOT = Objects.requireNonNull(NamespacedKey.fromString(KEY));
    /** Field → PDC type, from the value types {@link AnimalRecord#toMap()} writes. */
    private static final Map<String, PersistentDataType<?, ?>> SCHEMA = schema();

    private AnimalPdc() {
    }

    private static NamespacedKey key(String field) {
        return new NamespacedKey("tfp", field.toLowerCase(java.util.Locale.ROOT));
    }

    /** The record, or empty when there is none, it is malformed, or it is of a newer version. */
    public static Optional<AnimalRecord> read(PersistentDataContainer pdc) {
        PersistentDataContainer c = pdc.get(ROOT, PersistentDataType.TAG_CONTAINER);
        if (c == null) {
            return Optional.empty();
        }
        Map<String, Object> m = new HashMap<>();
        for (Map.Entry<String, PersistentDataType<?, ?>> e : SCHEMA.entrySet()) {
            Object value = c.get(key(e.getKey()), e.getValue());
            if (value != null) {
                m.put(e.getKey(), value);
            }
        }
        return AnimalRecord.fromMap(m);
    }

    /** Whether the container holds a {@value #KEY} entry of any version. */
    public static boolean has(PersistentDataContainer pdc) {
        return pdc.has(ROOT, PersistentDataType.TAG_CONTAINER);
    }

    /**
     * Writes {@code record} ({@code null} removes the entry). Returns false and changes nothing when the container holds
     * an entry of a newer version.
     */
    public static boolean write(PersistentDataContainer pdc, AnimalRecord record) {
        PersistentDataContainer existing = pdc.get(ROOT, PersistentDataType.TAG_CONTAINER);
        if (existing != null) {
            Integer v = existing.get(key(AnimalRecord.V), PersistentDataType.INTEGER);
            if (v != null && v > AnimalRecord.VERSION) {
                return false;
            }
        }
        if (record == null) {
            pdc.remove(ROOT);
            return true;
        }
        PersistentDataContainer c = pdc.getAdapterContext().newPersistentDataContainer();
        for (Map.Entry<String, Object> e : record.toMap().entrySet()) {
            set(c, key(e.getKey()), e.getValue());
        }
        pdc.set(ROOT, PersistentDataType.TAG_CONTAINER, c);
        return true;
    }

    private static Map<String, PersistentDataType<?, ?>> schema() {
        Map<String, PersistentDataType<?, ?>> out = new LinkedHashMap<>();
        AnimalRecord template = new AnimalRecord("tfc:x", AnimalState.spawned(true, 0, 0), 1, "");
        template.toMap().forEach((field, value) -> out.put(field, typeOf(value)));
        return Map.copyOf(out);
    }

    @SuppressWarnings("unchecked")
    private static <T> PersistentDataType<?, T> typeOf(T value) {
        Object type = switch (value) {
            case Integer i -> PersistentDataType.INTEGER;
            case Long l -> PersistentDataType.LONG;
            case Float f -> PersistentDataType.FLOAT;
            case Boolean b -> PersistentDataType.BOOLEAN;
            case String s -> PersistentDataType.STRING;
            default -> throw new IllegalArgumentException("unsupported value " + value.getClass());
        };
        return (PersistentDataType<?, T>) type;
    }

    private static <T> void set(PersistentDataContainer c, NamespacedKey key, T value) {
        c.set(key, typeOf(value), value);
    }
}
