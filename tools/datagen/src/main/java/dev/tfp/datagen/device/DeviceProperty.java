/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen.device;

import dev.tfp.datagen.DatagenException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * One block state property of a device block (PAR-INFRA-08), as TFC declares it in {@code TFCBlockStateProperties} or
 * vanilla's {@code BlockStateProperties}: a boolean, an integer range or a set of names (directions, axes, enums).
 *
 * <p>Names are always written as CraftEngine {@code string} properties, never CraftEngine's {@code axis}/{@code direction}
 * types: those carry values TFC's property does not have (the vertical axis, up and down) and would multiply the server
 * states with states TFC never has.
 *
 * @param name   the property name, e.g. {@code lit}
 * @param kind   the CraftEngine property type
 * @param values every value in order, as it appears in variant keys
 * @param dflt   the default value, one of {@code values}
 */
public record DeviceProperty(String name, Kind kind, List<String> values, String dflt) {
    /** The CraftEngine property type. */
    public enum Kind {
        BOOLEAN, INT, STRING
    }

    public DeviceProperty {
        if (name == null || !name.matches("[a-z][a-z0-9_]*")) {
            throw new DatagenException("device property name " + name + " is not a lower-case identifier");
        }
        values = List.copyOf(values);
        if (values.isEmpty() || new LinkedHashSet<>(values).size() != values.size()) {
            throw new DatagenException("device property " + name + " has no values or repeats one: " + values);
        }
        if (!values.contains(dflt)) {
            throw new DatagenException("device property " + name + ": default " + dflt + " is not one of " + values);
        }
        switch (kind) {
            case BOOLEAN -> {
                if (!values.equals(List.of("false", "true"))) {
                    throw new DatagenException("boolean device property " + name + " must have the values [false, true]");
                }
            }
            case INT -> {
                int lo = Integer.parseInt(values.getFirst());
                for (int i = 0; i < values.size(); i++) {
                    if (!values.get(i).equals(Integer.toString(lo + i))) {
                        throw new DatagenException("int device property " + name + " is not a contiguous range: " + values);
                    }
                }
            }
            case STRING -> {
                for (String v : values) {
                    if (!v.matches("[a-z0-9_]+")) {
                        throw new DatagenException("string device property " + name + " has the value " + v
                            + ", which a CraftEngine variant key cannot hold");
                    }
                }
            }
            default -> throw new IllegalStateException();
        }
    }

    /** {@code BlockStateProperties.LIT} and the like: false, true. */
    public static DeviceProperty bool(String name, boolean dflt) {
        return new DeviceProperty(name, Kind.BOOLEAN, List.of("false", "true"), Boolean.toString(dflt));
    }

    /** {@code IntegerProperty.create(name, lo, hi)}. */
    public static DeviceProperty range(String name, int lo, int hi, int dflt) {
        if (hi < lo) {
            throw new DatagenException("device property " + name + ": range " + lo + " to " + hi + " is empty");
        }
        List<String> values = new ArrayList<>();
        for (int i = lo; i <= hi; i++) {
            values.add(Integer.toString(i));
        }
        return new DeviceProperty(name, Kind.INT, values, Integer.toString(dflt));
    }

    /** An enum, direction or axis property, restricted to the values TFC's property has. */
    public static DeviceProperty names(String name, List<String> values, String dflt) {
        return new DeviceProperty(name, Kind.STRING, values, dflt);
    }

    /** The CraftEngine {@code properties.<name>} map. */
    Map<String, Object> yaml() {
        Map<String, Object> m = new LinkedHashMap<>();
        switch (kind) {
            case BOOLEAN -> {
                m.put("type", "boolean");
                m.put("default", Boolean.parseBoolean(dflt));
            }
            case INT -> {
                m.put("type", "int");
                m.put("range", values.getFirst() + "~" + values.getLast());
                m.put("default", Integer.parseInt(dflt));
            }
            case STRING -> {
                m.put("type", "string");
                m.put("values", values);
                m.put("default", dflt);
            }
            default -> throw new IllegalStateException();
        }
        return m;
    }
}
