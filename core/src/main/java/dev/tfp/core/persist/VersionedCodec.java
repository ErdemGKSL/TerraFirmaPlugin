/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.persist;

import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * A versioned codec for one kind of persistent state (ARCHITECTURE.md §7: "All codecs carry a version
 * number and have migration tests"). It writes the version under {@link #VERSION_KEY} next to the
 * fields; reading upgrades old data step by step ({@code v → v+1}) through registered
 * {@link Migration}s on a copy, then decodes the current layout.
 *
 * <p>Rules:
 * <ul>
 *   <li>Every version below the current one needs exactly one migration ({@link Builder#build} checks).</li>
 *   <li>Data from a newer version fails with {@link PersistException.NewerVersion}. Stores must keep such
 *       data untouched instead of overwriting it with defaults.</li>
 *   <li>Migrations and the writer/reader are pure and must not keep the map.</li>
 * </ul>
 * Immutable and thread-safe once built.
 *
 * @param <T> the decoded value, immutable
 */
public final class VersionedCodec<T> {
    /** Key of the version number in every encoded map. */
    public static final String VERSION_KEY = "v";

    /** Writes the fields of the current layout (the version key is added by the codec). */
    @FunctionalInterface
    public interface Writer<T> {
        void write(T value, DataMap out);
    }

    /** Reads the current layout. */
    @FunctionalInterface
    public interface Reader<T> {
        T read(DataMap in);
    }

    /** Upgrades a map in place from version {@code from} to {@code from + 1}. */
    @FunctionalInterface
    public interface Migration {
        void apply(DataMap data);
    }

    private final String name;
    private final int version;
    private final Map<Integer, Migration> migrations;
    private final Writer<T> writer;
    private final Reader<T> reader;

    private VersionedCodec(String name, int version, Map<Integer, Migration> migrations, Writer<T> writer,
            Reader<T> reader) {
        this.name = name;
        this.version = version;
        this.migrations = Map.copyOf(migrations);
        this.writer = writer;
        this.reader = reader;
    }

    /** @param currentVersion the version this code writes, starting at 1 */
    public static <T> Builder<T> builder(String name, int currentVersion) {
        return new Builder<>(name, currentVersion);
    }

    public String name() {
        return name;
    }

    public int version() {
        return version;
    }

    public DataMap encode(T value) {
        Objects.requireNonNull(value, "value");
        DataMap out = new DataMap();
        out.putInt(VERSION_KEY, version);
        writer.write(value, out);
        if (out.getInt(VERSION_KEY, -1) != version) {
            throw new IllegalStateException(name + ": the writer overwrote '" + VERSION_KEY + "'");
        }
        return out;
    }

    /** Decodes {@code in}, migrating older versions first. {@code in} is not modified. */
    public T decode(DataMap in) {
        int found;
        try {
            found = in.requireInt(VERSION_KEY);
        } catch (PersistException e) {
            throw new PersistException(name + ": " + e.getMessage(), e);
        }
        if (found > version) {
            throw new PersistException.NewerVersion(name, found, version);
        }
        if (found < 1) {
            throw new PersistException(name + ": bad data version " + found);
        }
        DataMap data = found == version ? in : in.copy();
        for (int v = found; v < version; v++) {
            try {
                migrations.get(v).apply(data);
            } catch (RuntimeException e) {
                throw new PersistException(name + ": migration " + v + " -> " + (v + 1) + " failed: " + e.getMessage(), e);
            }
            data.putInt(VERSION_KEY, v + 1);
        }
        try {
            return reader.read(data);
        } catch (PersistException e) {
            throw new PersistException(name + " v" + version + ": " + e.getMessage(), e);
        }
    }

    /** {@link #encode} + {@link DataMapBinary#encode}. */
    public byte[] toBytes(T value) {
        return DataMapBinary.encode(encode(value));
    }

    /** {@link DataMapBinary#decode} + {@link #decode}. */
    public T fromBytes(byte[] bytes) {
        return decode(DataMapBinary.decode(bytes));
    }

    /** Builder; register one migration per older version, then {@link #build}. */
    public static final class Builder<T> {
        private final String name;
        private final int version;
        private final Map<Integer, Migration> migrations = new TreeMap<>();

        private Builder(String name, int version) {
            this.name = Objects.requireNonNull(name, "name");
            if (version < 1) {
                throw new IllegalArgumentException(name + ": version must be >= 1");
            }
            this.version = version;
        }

        /** Registers the upgrade from {@code fromVersion} to {@code fromVersion + 1}. */
        public Builder<T> migrate(int fromVersion, Migration migration) {
            if (fromVersion < 1 || fromVersion >= version) {
                throw new IllegalArgumentException(name + ": migration from " + fromVersion + " outside 1.." + (version - 1));
            }
            if (migrations.putIfAbsent(fromVersion, Objects.requireNonNull(migration)) != null) {
                throw new IllegalArgumentException(name + ": duplicate migration from " + fromVersion);
            }
            return this;
        }

        public VersionedCodec<T> build(Writer<T> writer, Reader<T> reader) {
            for (int v = 1; v < version; v++) {
                if (!migrations.containsKey(v)) {
                    throw new IllegalStateException(name + ": no migration from version " + v + " to " + (v + 1));
                }
            }
            return new VersionedCodec<>(name, version, migrations, Objects.requireNonNull(writer),
                    Objects.requireNonNull(reader));
        }
    }
}
