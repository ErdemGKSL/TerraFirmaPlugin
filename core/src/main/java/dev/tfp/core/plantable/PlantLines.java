/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/items/PlantableInfo.java @ c3b4d9536 (addTooltipInfo, findStartingMonth, formatNutrientAmount),
 *       src/main/java/net/dries007/tfc/client/ClientForgeEventHandler.java @ c3b4d9536 (onItemTooltip: fertilizer)
 */
package dev.tfp.core.plantable;

import dev.tfp.core.berry.Lifecycle;
import dev.tfp.core.calendar.Month;
import dev.tfp.core.climate.ClimateRange;
import dev.tfp.core.climate.ClimateTemperatureStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The tooltip lines of plantable items and fertilizers as a pure model: which line, in which order, with its formatted numbers.
 * The Paper side maps each {@link Kind} to a lang key and TFC's colours. A vanilla client cannot report Shift, so the expanded
 * block is always produced and the "hold shift" hint never (ledger).
 */
public final class PlantLines {
    private PlantLines() {}

    /** The kind of a line. */
    public enum Kind {
        CLIMATE_HEADER, TEMPERATURE_RANGE, HYDRATION_RANGE, BLANK,
        NUTRIENTS_HEADER, NITROGEN, PHOSPHORUS, POTASSIUM,
        LIFECYCLE_HEADER, GROWTH_SPEED, CAN_PLANT, STARTS_FRUITING
    }

    /**
     * One line. {@code a} and {@code b} are the formatted arguments (the temperatures, the hydrations, the nutrient percent),
     * {@code ticks} the growth time of {@link Kind#GROWTH_SPEED}, {@code month} the month of the lifecycle lines.
     */
    public record Line(Kind kind, String a, String b, long ticks, Month month) {
        static Line of(Kind kind) {
            return new Line(kind, "", "", 0L, null);
        }

        static Line of(Kind kind, String a, String b) {
            return new Line(kind, a, b, 0L, null);
        }
    }

    /** {@code PlantableInfo.addTooltipInfo} without the shift test. */
    public static List<Line> plantable(PlantInfo info, ClimateTemperatureStyle style) {
        List<Line> out = new ArrayList<>();
        boolean addedText = false;
        if (info.climate().isPresent()) {
            ClimateRange climate = info.climate().get();
            out.add(Line.of(Kind.CLIMATE_HEADER));
            out.add(Line.of(Kind.TEMPERATURE_RANGE, style.format(climate.getMinTemperature(false)), style.format(climate.getMaxTemperature(false))));
            out.add(Line.of(Kind.HYDRATION_RANGE, Integer.toString(climate.getMinHydration(false)), Integer.toString(climate.getMaxHydration(false))));
            addedText = true;
        }
        if (info.nutrients().isPresent()) {
            if (addedText) {
                out.add(Line.of(Kind.BLANK));
            }
            PlantInfo.Nutrients n = info.nutrients().get();
            boolean consumes = n.nitrogen() > 0 || n.phosphorus() > 0 || n.potassium() > 0;
            out.add(Line.of(Kind.NUTRIENTS_HEADER));
            out.add(Line.of(Kind.NITROGEN, nutrientAmount(n.nitrogen(), consumes), ""));
            out.add(Line.of(Kind.PHOSPHORUS, nutrientAmount(n.phosphorus(), consumes), ""));
            out.add(Line.of(Kind.POTASSIUM, nutrientAmount(n.potassium(), consumes), ""));
            addedText = true;
        }
        boolean growInfo = info.growthTicks() > 0;
        boolean lifecycleInfo = info.lifecycle().isPresent();
        if (growInfo || lifecycleInfo) {
            if (addedText) {
                out.add(Line.of(Kind.BLANK));
            }
            out.add(Line.of(Kind.LIFECYCLE_HEADER));
        }
        if (growInfo) {
            out.add(new Line(Kind.GROWTH_SPEED, "", "", info.growthTicks(), null));
        }
        if (lifecycleInfo) {
            Month grow = null;
            Month fruit = null;
            // TFC starts at December and stops before January (index > JANUARY), to find the rising edge of each stage
            for (int index = Month.DECEMBER.ordinal(); index > Month.JANUARY.ordinal(); index--) {
                Month month = Month.valueOf(index);
                Lifecycle stage = info.lifecycle().get().get(index);
                grow = startingMonth(Lifecycle.HEALTHY, stage, grow, month);
                fruit = startingMonth(Lifecycle.FRUITING, stage, fruit, month);
            }
            if (grow != null) {
                out.add(new Line(Kind.CAN_PLANT, "", "", 0L, grow));
            }
            if (fruit != null) {
                out.add(new Line(Kind.STARTS_FRUITING, "", "", 0L, fruit));
            }
        }
        return out;
    }

    /** {@code findStartingMonth}. */
    static Month startingMonth(Lifecycle target, Lifecycle current, Month found, Month currentMonth) {
        if (current == target) {
            if (found == null) {
                return currentMonth;
            }
            if (currentMonth.next() == found) {
                return currentMonth;
            }
        }
        return found;
    }

    /** {@code formatNutrientAmount}: a plant that gives nutrients back shows {@code +}, 30 percent of it when it takes none. */
    static String nutrientAmount(float value, boolean consumes) {
        if (value < 0) {
            if (consumes) {
                return String.format(Locale.ROOT, "+%.0f", Math.abs(value) * 100);
            }
            return String.format(Locale.ROOT, "+%.0f", Math.abs(value) * 0.3 * 100);
        }
        return String.format(Locale.ROOT, "%.0f", value * 100);
    }

    /** The fertilizer lines of the client tooltip: each non-zero nutrient with one decimal. */
    public static List<Line> fertilizer(float nitrogen, float phosphorus, float potassium) {
        List<Line> out = new ArrayList<>(3);
        if (nitrogen != 0) {
            out.add(Line.of(Kind.NITROGEN, String.format(Locale.ROOT, "%.1f", nitrogen * 100), ""));
        }
        if (phosphorus != 0) {
            out.add(Line.of(Kind.PHOSPHORUS, String.format(Locale.ROOT, "%.1f", phosphorus * 100), ""));
        }
        if (potassium != 0) {
            out.add(Line.of(Kind.POTASSIUM, String.format(Locale.ROOT, "%.1f", potassium * 100), ""));
        }
        return out;
    }
}
