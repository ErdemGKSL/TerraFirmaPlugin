/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/client/BarSystem.java @ c3b4d9536 (Bar)
 */
package dev.tfp.core.itembar;

/**
 * One durability-style bar drawn on items from their state (TFC {@code BarSystem.Bar}): the heat bar, the egg bar. If a
 * bar belongs to one item type only it lives with that item, not here.
 *
 * <p>{@code S} is whatever view of a stack the caller has (the packet copy on the server). Implementations are pure and
 * thread-safe: they read the stack's lazy state at the time of the call.
 *
 * @param <S> the stack view
 */
public interface ItemBar<S> {
    /** TFC {@code Bar.WIDTH}: the full bar in pixels. */
    int WIDTH = 13;

    /** A stable id ({@code heat}, {@code egg}), as TFC's registry name. */
    String id();

    /** TFC {@code isBarVisible}: whether the bar should be drawn. */
    boolean isBarVisible(S stack);

    /** TFC {@code getBarWidth}: the width in {@code [0, }{@link #WIDTH}{@code ]}. */
    int barWidth(S stack);

    /** TFC {@code getBarColor}: an RGB colour ({@code 0xRRGGBB}). */
    int barColor(S stack);

    /**
     * TFC {@code overridesOtherBars}: true if this bar shows even when the stack has a bar of its own (durability) or
     * another of these bars is visible.
     */
    default boolean overridesOtherBars() {
        return false;
    }
}
