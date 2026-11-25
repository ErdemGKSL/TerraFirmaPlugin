/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.weathering;

import dev.tfp.core.metal.Metal;
import dev.tfp.core.weathering.WeatheringRules.Age;
import java.util.Locale;
import java.util.Optional;

/**
 * The CraftEngine ids of the weathering metal blocks (PAR-DECO-09): {@code tfp:metal/<folder>/<metal>} and its
 * {@code _slab} and {@code _stairs}, with the folder {@code block}, {@code exposed_block}, {@code weathered_block} or
 * {@code oxidized_block} for the four ages. TFC's grates and trapdoors do not weather (no entry in its oxidizables map).
 */
public final class WeatheringIds {
    private static final String PREFIX = "tfp:metal/";
    private static final String[] FOLDERS = {"block", "exposed_block", "weathered_block", "oxidized_block"};
    private static final String[] SHAPES = {"", "_slab", "_stairs"};

    private WeatheringIds() {
    }

    /**
     * One weathering block id, taken apart.
     *
     * @param metal      the metal's lower-case name
     * @param shape      {@code ""}, {@code "_slab"} or {@code "_stairs"}
     * @param age        the stage
     * @param resistance the metal's weathering resistance, in {@code [0, 1]}
     */
    public record Kind(String metal, String shape, Age age, float resistance) {
        /** The id of this block at another stage. */
        public String at(Age other) {
            return PREFIX + FOLDERS[other.ordinal()] + "/" + metal + shape;
        }

        /** The id of the next stage, or empty at {@link Age#OXIDIZED}. */
        public Optional<String> next() {
            Age n = age.next();
            return n == null ? Optional.empty() : Optional.of(at(n));
        }
    }

    /** The parts of {@code id}, or empty when it is not a block of a weathering metal. */
    public static Optional<Kind> parse(String id) {
        if (!id.startsWith(PREFIX)) {
            return Optional.empty();
        }
        String rest = id.substring(PREFIX.length());
        int slash = rest.indexOf('/');
        if (slash <= 0) {
            return Optional.empty();
        }
        String folder = rest.substring(0, slash);
        String name = rest.substring(slash + 1);
        Age age = null;
        for (int i = 0; i < FOLDERS.length; i++) {
            if (FOLDERS[i].equals(folder)) {
                age = Age.values()[i];
            }
        }
        if (age == null) {
            return Optional.empty();
        }
        String shape = "";
        for (String s : SHAPES) {
            if (!s.isEmpty() && name.endsWith(s)) {
                shape = s;
                name = name.substring(0, name.length() - s.length());
            }
        }
        Metal metal;
        try {
            metal = Metal.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
        if (!metal.weathers()) {
            return Optional.empty();
        }
        return Optional.of(new Kind(name, shape, age, metal.weatheringResistance()));
    }
}
