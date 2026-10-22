/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.animal;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Every TFC living entity type with its vanilla carrier (M5-ENT-01), read from {@code carriers.tsv} next to this
 * class. The table was exported from {@code TFCEntities.java} by a scratch script; the carrier picks follow the
 * {@code bettermodel} skill (closest hitbox and movement among mobs that do not attack players or change blocks), and
 * each row keeps the hitbox delta for review. Immutable, thread-safe.
 */
public final class CarrierTable {
    /** Hitbox deltas above this many blocks are flagged in the table. */
    public static final float DELTA_LIMIT = 0.25f;
    static final String RESOURCE = "carriers.tsv";
    private static final String NONE = "-";
    private static final CarrierTable DEFAULT = load();

    private final Map<String, Carrier> byId;

    private CarrierTable(Map<String, Carrier> byId) {
        this.byId = Collections.unmodifiableMap(byId);
    }

    /** The shipped table. */
    public static CarrierTable get() {
        return DEFAULT;
    }

    /** All rows in file order. */
    public List<Carrier> all() {
        return List.copyOf(byId.values());
    }

    /** The row of {@code id}; a bare name ({@code cow}) means {@code tfc:cow}. */
    public Optional<Carrier> find(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(byId.get(id.indexOf(':') < 0 ? "tfc:" + id : id));
    }

    static CarrierTable load() {
        try (InputStream in = CarrierTable.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException(RESOURCE + " is missing next to " + CarrierTable.class.getName());
            }
            return parse(new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)).lines().toList());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static CarrierTable parse(List<String> lines) {
        Map<String, Carrier> out = new LinkedHashMap<>();
        boolean header = false;
        for (String line : lines) {
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            if (!header) {
                header = true; // column names
                continue;
            }
            String[] c = line.split("\t", -1);
            if (c.length != 14) {
                throw new IllegalArgumentException(RESOURCE + ": expected 14 columns, got " + c.length + ": " + line);
            }
            Carrier row = new Carrier(c[0], Float.parseFloat(c[1]), Float.parseFloat(c[2]), c[3], Integer.parseInt(c[4]),
                    c[5], Float.parseFloat(c[6]), Float.parseFloat(c[7]), Boolean.parseBoolean(c[10]), opt(c[11]),
                    opt(c[12]), opt(c[13]));
            if (out.put(row.id(), row) != null) {
                throw new IllegalArgumentException(RESOURCE + ": duplicate id " + row.id());
            }
        }
        return new CarrierTable(out);
    }

    private static String opt(String s) {
        return s.isEmpty() || s.equals(NONE) ? null : s;
    }

    /** The ids, for command suggestions. */
    public List<String> ids() {
        return new ArrayList<>(byId.keySet());
    }
}
