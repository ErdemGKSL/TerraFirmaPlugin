/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.item.feature;

import java.util.List;
import java.util.Objects;

/**
 * One tooltip line of a TFC item feature (PAR-INFRA-50): its parts, concatenated in order, and the style TFC puts on
 * the whole line ({@code withStyle(ChatFormatting...)}). Colours written into a lang string ({@code §6Fertilized}) are
 * part of the text, not of this record. Immutable.
 *
 * @param parts  the pieces, appended in order (TFC's {@code literal(..).append(..)} chains)
 * @param color  the line colour TFC sets, or {@link LineColor#NONE}
 * @param italic whether TFC sets {@code ChatFormatting.ITALIC}
 */
public record FeatureLine(List<LinePart> parts, LineColor color, boolean italic) {
    /** The {@code ChatFormatting} colours TFC's feature lines use. */
    public enum LineColor {
        NONE, GRAY, DARK_GREEN, AQUA
    }

    public FeatureLine {
        parts = List.copyOf(parts);
        Objects.requireNonNull(color, "color");
    }

    /** A line of {@code parts} without a style. */
    public static FeatureLine of(LinePart... parts) {
        return new FeatureLine(List.of(parts), LineColor.NONE, false);
    }

    /** This line in {@code color}. */
    public FeatureLine colored(LineColor color) {
        return new FeatureLine(parts, color, italic);
    }

    /** This line in italics. */
    public FeatureLine inItalics() {
        return new FeatureLine(parts, color, true);
    }
}
