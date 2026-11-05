/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen.oreoverlay;

import dev.tfp.datagen.DatagenException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Locale;
import java.util.Properties;

/**
 * How ore blocks look in the shipped allocations (PAR-INFRA-15, design {@code docs/research/ore-entity-renderer-design.md}).
 *
 * <ul>
 *   <li>{@link #HOST_ROCK}: the target. An ore appearance is its host rock's raw unit (0 visual states); the ore texture
 *       is drawn only on exposed faces by the packet-only overlay runtime (PAR-INFRA-16). The flattened ore PNGs and
 *       the per-ore block models are not written.</li>
 *   <li>{@link #FULL_CUBE}: the transition look. Each ore keeps its own flattened full cube ({@code OreFlattener}),
 *       so no build ever shows ores as plain rock while the overlay runtime does not exist yet.</li>
 * </ul>
 * The overlay item models, the composite block-item models and the runtime data are emitted in both modes; the budget
 * tests check the {@code HOST_ROCK} allocations.
 */
public enum OreLook {
    FULL_CUBE,
    HOST_ROCK;

    /**
     * The switch file, owned by PAR-INFRA-16 (which flips it to {@code HOST_ROCK} as its last step). Key {@code mode};
     * an absent file means {@link #FULL_CUBE}.
     */
    public static final String MODE_RESOURCE = "/dev/tfp/datagen/oreoverlay/mode.properties";

    /** The shipped look: {@link #MODE_RESOURCE}, else {@link #FULL_CUBE}. */
    public static OreLook shipped() {
        try (InputStream in = OreLook.class.getResourceAsStream(MODE_RESOURCE)) {
            if (in == null) {
                return FULL_CUBE;
            }
            Properties p = new Properties();
            p.load(in);
            return parse(p.getProperty("mode", "full_cube"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** {@code full_cube} / {@code host_rock} (case and {@code -}/{@code _} ignored). */
    public static OreLook parse(String text) {
        String s = text.strip().toUpperCase(Locale.ROOT).replace('-', '_');
        for (OreLook l : values()) {
            if (l.name().equals(s)) {
                return l;
            }
        }
        throw new DatagenException(MODE_RESOURCE + ": unknown ore look '" + text + "' (expected full_cube or host_rock)");
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }
}
