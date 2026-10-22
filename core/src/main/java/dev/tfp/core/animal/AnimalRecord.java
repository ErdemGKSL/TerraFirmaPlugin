/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/entities/livestock/TFCAnimalProperties.java @ c3b4d9536
 * (saveCommonAnimalData, readCommonAnimalData)
 */
package dev.tfp.core.animal;

import dev.tfp.core.livestock.AnimalState;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * What a TFC animal stores on its carrier (M5-ENT-01): the species, TFC's common animal data ({@link AnimalState}),
 * the genetic size and a texture variant. Base values only; the age and scale are derived from them and the calendar
 * when read. TFC's {@code lastAge} is not stored: the glue remembers the stage it last applied.
 *
 * <p>{@link #toMap()} and {@link #fromMap} are the codec, over primitive values only ({@code Integer}, {@code Long},
 * {@code Float}, {@code Boolean}, {@code String}), so the entity PDC codec is a thin key-by-key copy.
 *
 * @param species     TFC entity id ({@code tfc:cow})
 * @param state       TFC's common animal data
 * @param geneticSize 1..32, or {@link AnimalScale#NO_SIZE} for an animal without one
 * @param variant     texture variant id, empty for the default
 */
public record AnimalRecord(String species, AnimalState state, int geneticSize, String variant) {
    /** Codec version, stored as {@code v}. Newer entries read as empty. */
    public static final int VERSION = 1;

    public static final String V = "v";
    public static final String SPECIES = "species";
    public static final String MALE = "male";
    public static final String FAMILIARITY = "familiarity";
    public static final String FAMILIARITY_TICK = "familiarityTick";
    public static final String BIRTH_TICK = "birthTick";
    public static final String OLD_TICK = "oldTick";
    public static final String LAST_FED_TICK = "lastFedTick";
    public static final String LAST_MATE_TICK = "lastMateTick";
    public static final String USES = "uses";
    public static final String FERTILIZED = "fertilized";
    public static final String PREGNANT_DAY = "pregnantDay";
    public static final String SIZE = "size";
    public static final String VARIANT = "variant";

    public AnimalRecord {
        Objects.requireNonNull(species, "species");
        Objects.requireNonNull(state, "state");
        variant = variant == null ? "" : variant;
        if (geneticSize != AnimalScale.NO_SIZE) {
            geneticSize = AnimalScale.clampSize(geneticSize);
        }
    }

    public AnimalRecord withState(AnimalState next) {
        return new AnimalRecord(species, next, geneticSize, variant);
    }

    /** The record as ordered key → primitive value pairs, including {@link #V}. */
    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put(V, VERSION);
        m.put(SPECIES, species);
        m.put(MALE, state.male());
        m.put(FAMILIARITY, state.familiarity());
        m.put(FAMILIARITY_TICK, state.familiarityTick());
        m.put(BIRTH_TICK, state.birthTick());
        m.put(OLD_TICK, state.oldTick());
        m.put(LAST_FED_TICK, state.lastFedTick());
        m.put(LAST_MATE_TICK, state.lastMateTick());
        m.put(USES, state.uses());
        m.put(FERTILIZED, state.fertilized());
        m.put(PREGNANT_DAY, state.pregnantDay());
        m.put(SIZE, geneticSize);
        m.put(VARIANT, variant);
        return m;
    }

    /** Decodes {@link #toMap()} output. Absent, malformed or newer-version entries read as empty. */
    public static Optional<AnimalRecord> fromMap(Map<String, ?> m) {
        if (m == null || !(m.get(V) instanceof Integer v) || v > VERSION || v < 1) {
            return Optional.empty();
        }
        try {
            AnimalState state = new AnimalState(
                    (Boolean) req(m, MALE),
                    (Float) req(m, FAMILIARITY),
                    (Long) req(m, FAMILIARITY_TICK),
                    (Long) req(m, BIRTH_TICK),
                    (Long) req(m, OLD_TICK),
                    (Long) req(m, LAST_FED_TICK),
                    (Long) req(m, LAST_MATE_TICK),
                    (Integer) req(m, USES),
                    (Boolean) req(m, FERTILIZED),
                    (Long) req(m, PREGNANT_DAY));
            return Optional.of(new AnimalRecord((String) req(m, SPECIES), state, (Integer) req(m, SIZE),
                    (String) m.get(VARIANT)));
        } catch (ClassCastException | IllegalArgumentException | NullPointerException e) {
            return Optional.empty();
        }
    }

    private static Object req(Map<String, ?> m, String key) {
        Object o = m.get(key);
        if (o == null) {
            throw new NullPointerException(key);
        }
        return o;
    }
}
