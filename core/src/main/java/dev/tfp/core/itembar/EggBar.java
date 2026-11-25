/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/client/BarSystem.java @ c3b4d9536 (EGG)
 */
package dev.tfp.core.itembar;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.LongSupplier;

/**
 * TFC's egg bar ({@code BarSystem.EGG}): a fertilized egg shows a gold bar that grows over its {@value #MAX_DAYS} incubation
 * days. Visible only for a fertilized egg; width {@code round(13 * incubationDays / 8)} with
 * {@code incubationDays = 8 - clamp(hatchDay - today, 0, 8)}. TFC reads {@code Calendars.CLIENT.getTotalCalendarDays()}; here
 * {@code today} is the current calendar day, read on every call (the egg is lazy: it stores its hatch day and nothing ticks).
 *
 * @param <S> the stack view the bar reads
 */
public final class EggBar<S> implements ItemBar<S> {
    public static final String ID = "egg";
    /** TFC {@code getBarWidth}'s {@code maxDays}: the days a fertilized egg incubates. */
    public static final int MAX_DAYS = 8;
    /** TFC {@code ChatFormatting.GOLD}. */
    public static final int GOLD = 0xFFAA00;

    /**
     * The egg component's state the bar reads (TFC {@code EggComponent}).
     *
     * @param fertilized whether the egg is fertilized
     * @param hatchDay   the calendar day it hatches
     */
    public record Incubation(boolean fertilized, long hatchDay) {
    }

    private final Function<S, Optional<Incubation>> egg;
    private final LongSupplier today;

    /**
     * @param egg   the stack's egg component, empty for a stack that has none
     * @param today the current total calendar day
     */
    public EggBar(Function<S, Optional<Incubation>> egg, LongSupplier today) {
        this.egg = Objects.requireNonNull(egg, "egg");
        this.today = Objects.requireNonNull(today, "today");
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public boolean isBarVisible(S stack) {
        return egg.apply(stack).filter(Incubation::fertilized).isPresent();
    }

    @Override
    public int barWidth(S stack) {
        Optional<Incubation> e = egg.apply(stack);
        return e.isEmpty() ? 0 : width(e.get().hatchDay(), today.getAsLong());
    }

    @Override
    public int barColor(S stack) {
        return GOLD;
    }

    /** TFC's width of an egg that hatches on {@code hatchDay}, on calendar day {@code today}. */
    public static int width(long hatchDay, long today) {
        long left = hatchDay - today;
        int daysLeft = (int) left; // TFC casts the long difference to int before the clamp
        int incubationDays = MAX_DAYS - Math.max(0, Math.min(MAX_DAYS, daysLeft));
        return Math.round(WIDTH * (float) incubationDays / MAX_DAYS);
    }
}
