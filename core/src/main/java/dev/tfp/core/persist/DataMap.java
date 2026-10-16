/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.persist;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * The neutral, NBT-shaped tree our persistent codecs write to and read from. One {@link VersionedCodec}
 * produces a {@code DataMap}; storage adapters turn it into bytes ({@link DataMapBinary}: player files,
 * chunk PDC) or into CraftEngine NBT ({@code integration/craftengine}). Keeping codecs on this type means
 * the same codec and the same migrations work for every store.
 *
 * <p>Value types (exactly these): {@code int}, {@code long}, {@code float}, {@code double},
 * {@code boolean}, {@link String}, {@code byte[]}, nested {@code DataMap}, and {@code List<DataMap>}.
 * Typed getters fail with {@link PersistException} on a wrong type; {@code require*} also fails on a
 * missing key, the defaulting getters return the default for a missing key only.
 *
 * <p>Mutable and not thread-safe: build it, encode it, and drop it on one thread. {@code byte[]} values
 * are copied in and out.
 */
public final class DataMap {
    private final Map<String, Object> values = new LinkedHashMap<>();

    public DataMap() {
    }

    // ---- writes ----

    public DataMap putInt(String key, int value) {
        return put(key, value);
    }

    public DataMap putLong(String key, long value) {
        return put(key, value);
    }

    public DataMap putFloat(String key, float value) {
        return put(key, value);
    }

    public DataMap putDouble(String key, double value) {
        return put(key, value);
    }

    public DataMap putBoolean(String key, boolean value) {
        return put(key, value);
    }

    public DataMap putString(String key, String value) {
        return put(key, Objects.requireNonNull(value, key));
    }

    public DataMap putBytes(String key, byte[] value) {
        return put(key, Objects.requireNonNull(value, key).clone());
    }

    public DataMap putMap(String key, DataMap value) {
        return put(key, Objects.requireNonNull(value, key));
    }

    public DataMap putList(String key, List<DataMap> value) {
        for (DataMap m : Objects.requireNonNull(value, key)) {
            Objects.requireNonNull(m, key + " element");
        }
        return put(key, List.copyOf(value));
    }

    private DataMap put(String key, Object value) {
        Objects.requireNonNull(key, "key");
        if (key.isEmpty()) {
            throw new IllegalArgumentException("empty key");
        }
        values.put(key, value);
        return this;
    }

    /** Removes {@code key}; returns the removed raw value or null. */
    public Object remove(String key) {
        return values.remove(key);
    }

    /** Moves the value of {@code from} to {@code to} (no-op if absent). For migrations. */
    public DataMap rename(String from, String to) {
        if (values.containsKey(from)) {
            Object v = values.remove(from);
            put(to, v);
        }
        return this;
    }

    // ---- reads ----

    public boolean has(String key) {
        return values.containsKey(key);
    }

    public Set<String> keys() {
        return Collections.unmodifiableSet(values.keySet());
    }

    public int size() {
        return values.size();
    }

    public int getInt(String key, int def) {
        Object v = values.get(key);
        return v == null ? def : typed(key, v, Integer.class);
    }

    public int requireInt(String key) {
        return typed(key, required(key), Integer.class);
    }

    public long getLong(String key, long def) {
        Object v = values.get(key);
        return v == null ? def : typed(key, v, Long.class);
    }

    public long requireLong(String key) {
        return typed(key, required(key), Long.class);
    }

    public float getFloat(String key, float def) {
        Object v = values.get(key);
        return v == null ? def : typed(key, v, Float.class);
    }

    public float requireFloat(String key) {
        return typed(key, required(key), Float.class);
    }

    public double getDouble(String key, double def) {
        Object v = values.get(key);
        return v == null ? def : typed(key, v, Double.class);
    }

    public boolean getBoolean(String key, boolean def) {
        Object v = values.get(key);
        return v == null ? def : typed(key, v, Boolean.class);
    }

    public String getString(String key, String def) {
        Object v = values.get(key);
        return v == null ? def : typed(key, v, String.class);
    }

    public String requireString(String key) {
        return typed(key, required(key), String.class);
    }

    public byte[] getBytes(String key, byte[] def) {
        Object v = values.get(key);
        return v == null ? def : typed(key, v, byte[].class).clone();
    }

    public DataMap getMap(String key) {
        Object v = values.get(key);
        return v == null ? null : typed(key, v, DataMap.class);
    }

    public DataMap requireMap(String key) {
        return typed(key, required(key), DataMap.class);
    }

    @SuppressWarnings("unchecked")
    public List<DataMap> getList(String key) {
        Object v = values.get(key);
        return v == null ? List.of() : (List<DataMap>) typed(key, v, List.class);
    }

    /** The raw value (one of the supported types) or null. */
    public Object raw(String key) {
        Object v = values.get(key);
        return v instanceof byte[] b ? b.clone() : v;
    }

    /** Puts a raw value of one of the supported types (used by storage adapters). */
    public DataMap putRaw(String key, Object value) {
        return switch (value) {
            case Integer i -> putInt(key, i);
            case Long l -> putLong(key, l);
            case Float f -> putFloat(key, f);
            case Double d -> putDouble(key, d);
            case Boolean b -> putBoolean(key, b);
            case String s -> putString(key, s);
            case byte[] b -> putBytes(key, b);
            case DataMap m -> putMap(key, m);
            case List<?> l -> {
                List<DataMap> maps = new ArrayList<>(l.size());
                for (Object o : l) {
                    if (!(o instanceof DataMap m)) {
                        throw new PersistException(key + ": list element of type "
                                + (o == null ? "null" : o.getClass().getSimpleName()) + ", expected a map");
                    }
                    maps.add(m);
                }
                yield putList(key, maps);
            }
            case null -> throw new PersistException(key + ": null value");
            default -> throw new PersistException(key + ": unsupported value type " + value.getClass().getName());
        };
    }

    /** Deep copy (migrations work on a copy, never on the caller's map). */
    public DataMap copy() {
        DataMap c = new DataMap();
        for (Map.Entry<String, Object> e : values.entrySet()) {
            Object v = e.getValue();
            if (v instanceof DataMap m) {
                v = m.copy();
            } else if (v instanceof List<?> l) {
                List<DataMap> copied = new ArrayList<>(l.size());
                for (Object o : l) {
                    copied.add(((DataMap) o).copy());
                }
                v = List.copyOf(copied);
            } else if (v instanceof byte[] b) {
                v = b.clone();
            }
            c.values.put(e.getKey(), v);
        }
        return c;
    }

    private Object required(String key) {
        Object v = values.get(key);
        if (v == null) {
            throw new PersistException("missing key '" + key + "'");
        }
        return v;
    }

    private static <V> V typed(String key, Object v, Class<V> type) {
        if (!type.isInstance(v)) {
            throw new PersistException("key '" + key + "': expected " + type.getSimpleName() + ", found "
                    + v.getClass().getSimpleName());
        }
        return type.cast(v);
    }

    /** Package-private view for the binary codec. */
    Map<String, Object> entries() {
        return Collections.unmodifiableMap(values);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof DataMap other) || other.values.size() != values.size()) {
            return false;
        }
        for (Map.Entry<String, Object> e : values.entrySet()) {
            Object a = e.getValue();
            Object b = other.values.get(e.getKey());
            if (a instanceof byte[] x && b instanceof byte[] y ? !Arrays.equals(x, y) : !Objects.equals(a, b)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public int hashCode() {
        int h = 0;
        for (Map.Entry<String, Object> e : values.entrySet()) {
            Object v = e.getValue();
            h += e.getKey().hashCode() ^ (v instanceof byte[] b ? Arrays.hashCode(b) : Objects.hashCode(v));
        }
        return h;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> e : values.entrySet()) {
            if (!first) {
                sb.append(", ");
            }
            first = false;
            Object v = e.getValue();
            sb.append(e.getKey()).append('=').append(v instanceof byte[] b ? "bytes[" + b.length + "]"
                    : v instanceof String s ? '"' + s + '"' : v instanceof Float f ? f + "f"
                    : v instanceof Long l ? l + "L" : String.valueOf(v));
        }
        return sb.append('}').toString();
    }
}
