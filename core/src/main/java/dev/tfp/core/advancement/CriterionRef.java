/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/resources/data/tfc/advancement/ @ c3b4d9536 (advancement ids and criterion names)
 */
package dev.tfp.core.advancement;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * PAR-DATA-02: one criterion of one advancement, e.g. {@code tfp:story/find_rock#rock_knapping}. The advancement id is
 * ours ({@code tfc:} renamed to {@code tfp:}); the criterion name is TFC's, unchanged. Every TFC criterion except the tab
 * roots' {@code minecraft:tick} is {@code minecraft:impossible} in our datapack, so the plugin awards it by this name.
 *
 * <p>Immutable; any thread.
 */
public record CriterionRef(String advancement, String criterion) {
    private static final Pattern ID = Pattern.compile("[a-z0-9_.\\-]+:[a-z0-9_.\\-/]+");
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_.\\-/]+");
    /** Separates the advancement id from the criterion name in {@link #parse} and {@link #toString}. */
    public static final char SEPARATOR = '#';

    public CriterionRef {
        Objects.requireNonNull(advancement, "advancement");
        Objects.requireNonNull(criterion, "criterion");
        if (!ID.matcher(advancement).matches()) {
            throw new IllegalArgumentException("not an advancement id: " + advancement);
        }
        if (!NAME.matcher(criterion).matches()) {
            throw new IllegalArgumentException("not a criterion name: " + criterion);
        }
    }

    /** {@code <advancement id>#<criterion>}, e.g. {@code tfp:story/find_rock#rock_knapping}. */
    public static CriterionRef parse(String text) {
        Objects.requireNonNull(text, "text");
        int i = text.indexOf(SEPARATOR);
        if (i <= 0 || i == text.length() - 1 || text.indexOf(SEPARATOR, i + 1) >= 0) {
            throw new IllegalArgumentException("not <advancement>#<criterion>: " + text);
        }
        return new CriterionRef(text.substring(0, i), text.substring(i + 1));
    }

    @Override
    public String toString() {
        return advancement + SEPARATOR + criterion;
    }
}
