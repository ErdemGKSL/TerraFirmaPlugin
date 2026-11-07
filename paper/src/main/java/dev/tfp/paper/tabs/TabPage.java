/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/client/screen/CalendarScreen.java @ c3b4d9536 (renderLabels: the clock, date,
 *       days-left or birthday line, season, night sprite),
 *       src/main/java/net/dries007/tfc/client/screen/NutritionScreen.java @ c3b4d9536 (renderBg / renderLabels),
 *       src/main/java/net/dries007/tfc/client/screen/ClimateScreen.java @ c3b4d9536 (renderLabels),
 *       src/main/java/net/dries007/tfc/client/screen/TFCContainerScreen.java @ c3b4d9536 (drawLine alignment),
 *       src/main/java/net/dries007/tfc/util/calendar/Day.java @ c3b4d9536 (BIRTHDAYS, getDayName)
 */
package dev.tfp.paper.tabs;

import dev.tfp.core.calendar.CalendarTicks;
import dev.tfp.core.calendar.Day;
import dev.tfp.core.calendar.Month;
import dev.tfp.core.climate.ClimateFormat;
import dev.tfp.core.climate.ClimateTemperatureStyle;
import dev.tfp.core.food.Nutrient;
import dev.tfp.core.gui.ChestGeometry;
import dev.tfp.paper.tabs.TabLayout.Page;
import dev.tfp.paper.tabs.TabLayout.Tab;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * PAR-SURV-07: what one page shows, as plain values (no Bukkit): the text pieces at TFC's positions and the overlay
 * frames. Built on a worker from a snapshot, drawn by {@link TabTitles}.
 *
 * @param page   the page
 * @param label  the title text (TFC's screen name, drawn at the vanilla title position)
 * @param lines  text pieces below the title
 * @param frames overlay frames ({@link TabLayout#layers})
 */
public record TabPage(Page page, String label, List<Text> lines, Map<String, Integer> frames) {
    /** TFC's default label colour. */
    public static final int DEFAULT_COLOR = 0x404040;
    /** ClimateScreen's rainfall colour. */
    public static final int RAIN_COLOR = 0x202080;

    public TabPage {
        lines = List.copyOf(lines);
        frames = Map.copyOf(frames);
    }

    /** One piece of text: its left x and top y in the window, sanitized text, colour. */
    public record Text(int x, int y, String text, int color) {
        public Text {
            text = TabFont.sanitize(text);
        }
    }

    /** Translations for the viewer ({@code Messages.get(player, key, args)}). */
    @FunctionalInterface
    public interface Lang {
        String get(String key, Object... args);
    }

    /** The calendar inputs: {@code known} false while the calendar has not been published. */
    public record CalendarInput(boolean known, long calendarTicks, int daysInMonth, boolean northernHemisphere) {
    }

    /** The climate at the player (TFC {@code ClimateRenderCache}); {@code known} false outside the overworld model. */
    public record ClimateInput(boolean known, float averageTemperature, float averageRainfall, float rainVariance,
            float temperature, float rainfall, boolean northernHemisphere, ClimateTemperatureStyle style) {
    }

    /** TFC's developer birthdays ({@code Day.BIRTHDAYS}), keyed by month name and day of month. */
    static final Map<String, String> BIRTHDAYS = Map.ofEntries(
        Map.entry("JULY7", "Bioxx"), Map.entry("JUNE18", "Kitty"), Map.entry("OCTOBER2", "Dunk"),
        Map.entry("MAY1", "Dries"), Map.entry("DECEMBER9", "Alcatraz"), Map.entry("FEBRUARY31", "Bunsan"),
        Map.entry("MARCH14", "Claycorp"), Map.entry("DECEMBER1", "LightningShock"), Map.entry("JANUARY20", "Therighthon"),
        Map.entry("FEBRUARY21", "CtrlAltDavid"), Map.entry("MARCH10", "Disastermoo"), Map.entry("OCTOBER30", "Dodo"),
        Map.entry("OCTOBER31", "alekiponi"), Map.entry("JULY14", "Daniel"), Map.entry("JANUARY6", "Char"));

    private static final int WIDTH = ChestGeometry.WIDTH;
    /** CalendarScreen: the clock line. */
    static final int CLOCK_Y = 22;

    /** {@code drawLine(LEFT, x)}: {@code x + 8}. */
    static Text left(String text, int x, int y, int color) {
        return new Text(x + 8, y, text, color);
    }

    /** {@code drawLine(CENTER)}: {@code (imageWidth - width) / 2}. */
    static Text center(String text, int y) {
        String t = TabFont.sanitize(text);
        return new Text((WIDTH - TabFont.width(t)) / 2, y, t, DEFAULT_COLOR);
    }

    /** {@code drawLine(RIGHT, x)}: {@code imageWidth - width - 8 - x}. */
    static Text right(String text, int x, int y) {
        String t = TabFont.sanitize(text);
        return new Text(WIDTH - TabFont.width(t) - 8 - x, y, t, DEFAULT_COLOR);
    }

    private static Map<String, Integer> tabFrames(Page page) {
        Map<String, Integer> f = new HashMap<>();
        for (Tab t : Tab.values()) {
            f.put(TabLayout.tabLayer(t), t == page.tab ? TabLayout.FRAME_ACTIVE : TabLayout.FRAME_INACTIVE);
        }
        return f;
    }

    /** The calendar page (CalendarScreen.renderLabels). */
    public static TabPage calendar(CalendarInput in, Lang lang) {
        Map<String, Integer> frames = tabFrames(Page.CALENDAR);
        List<Text> lines = new ArrayList<>();
        String label = lang.get("tabs.title.calendar");
        if (!in.known()) {
            lines.add(center(ClimateFormat.UNKNOWN, 33));
            return new TabPage(Page.CALENDAR, label, lines, frames);
        }
        long t = in.calendarTicks();
        int dim = in.daysInMonth();
        int hour = CalendarTicks.hourOfDay(t);
        int dayOfMonth = CalendarTicks.dayOfMonth(t, dim);
        Month month = CalendarTicks.month(t, dim);
        String monthName = lang.get("tooltip.month." + key(month));

        lines.add(right(String.format(Locale.ROOT, "%02d", hour), 83, 22));
        lines.add(center(":", 22));
        lines.add(left(String.format(Locale.ROOT, "%02d", CalendarTicks.minuteOfHour(t)), 83, 22, DEFAULT_COLOR));

        Day day = Day.ofTotalDays(CalendarTicks.totalDays(t));
        lines.add(center(lang.get("tabs.day." + key(day)) + ", " + monthName + " " + dayOfMonth + ", "
            + CalendarTicks.year(t, dim), 33));

        String birthday = BIRTHDAYS.get(month.name() + dayOfMonth);
        if (birthday == null) {
            int daysLeft = dim - dayOfMonth;
            String line = switch (daysLeft) {
                case 0 -> lang.get("tabs.calendar.last_day", monthName);
                case 1 -> lang.get("tabs.calendar.second_last_day", daysLeft, monthName);
                default -> lang.get("tabs.calendar.days_left", daysLeft, monthName);
            };
            lines.add(center(line, 44));
        } else {
            lines.add(center(lang.get("tabs.calendar.birthday", birthday), 44));
        }

        Month seasonal = in.northernHemisphere() ? month : month.opposite();
        lines.add(center(lang.get("tabs.season." + key(seasonal)), 55));
        if (hour >= 20 || hour < 5) {
            frames.put(TabLayout.NIGHT_LAYER, 0);
        }
        return new TabPage(Page.CALENDAR, label, lines, frames);
    }

    /**
     * The nutrition page: one bar per nutrient, {@code (int) (value * 93)} wide, and the nutrient names right-aligned
     * to x 63 ({@code drawLine(RIGHT, -1, 105, 17 + 11 * ordinal)}).
     *
     * @param nutrients the five values in {@link Nutrient} order, 0 to 1
     */
    public static TabPage nutrition(float[] nutrients, Lang lang) {
        Objects.requireNonNull(nutrients);
        if (nutrients.length != Nutrient.TOTAL) {
            throw new IllegalArgumentException("five nutrients, not " + nutrients.length);
        }
        Map<String, Integer> frames = tabFrames(Page.NUTRITION);
        List<Text> lines = new ArrayList<>();
        for (Nutrient n : Nutrient.VALUES) {
            int width = barWidth(nutrients[n.ordinal()]);
            if (width > 0) {
                frames.put(TabLayout.barLayer(n.ordinal()), width);
            }
            lines.add(right(lang.get("tooltip.nutrient." + n.serializedName()), 105, 17 + 11 * n.ordinal()));
        }
        return new TabPage(Page.NUTRITION, lang.get("tabs.title.nutrition"), lines, frames);
    }

    /** {@code (int) (value * 93)}, clamped to the bar. */
    static int barWidth(float value) {
        int w = (int) (value * TabLayout.NUTRITION_WIDTH);
        return Math.clamp(w, 0, TabLayout.NUTRITION_WIDTH);
    }

    /** The climate page (ClimateScreen.renderLabels). */
    public static TabPage climate(ClimateInput in, Lang lang) {
        Map<String, Integer> frames = tabFrames(Page.CLIMATE);
        List<Text> lines = new ArrayList<>();
        String label = lang.get("tabs.title.climate");
        if (!in.known()) {
            lines.add(center(ClimateFormat.UNKNOWN, 18));
            return new TabPage(Page.CLIMATE, label, lines, frames);
        }
        Koppen k = Koppen.classify(in.averageTemperature(), in.averageRainfall(), in.rainVariance(), in.northernHemisphere());
        lines.add(center(lang.get("tabs.koppen." + k.key()), 18));

        ClimateTemperatureStyle style = in.style();
        lines.add(left(lang.get("tabs.climate.temperature"), 0, 32, DEFAULT_COLOR));
        lines.add(left(lang.get("tabs.climate.average", style.format(in.averageTemperature())), 36, 32, DEFAULT_COLOR));
        lines.add(left(lang.get("tabs.climate.now", style.format(in.temperature())), 96, 32, DEFAULT_COLOR));

        lines.add(left(lang.get("tabs.climate.rainfall"), 0, 46, RAIN_COLOR));
        lines.add(left(lang.get("tabs.climate.rainfall_average", whole(in.averageRainfall())), 36, 46, RAIN_COLOR));
        lines.add(left(lang.get("tabs.climate.rainfall_now", whole(in.rainfall())), 96, 46, RAIN_COLOR));

        lines.add(left(lang.get("tabs.climate.peak"), 0, 57, RAIN_COLOR));
        String peak = whole(in.averageRainfall() * (1 + Math.abs(in.rainVariance())));
        lines.add(left(lang.get(in.rainVariance() > 0 ? "tabs.climate.peak_july" : "tabs.climate.peak_january", peak),
            36, 57, RAIN_COLOR));
        return new TabPage(Page.CLIMATE, label, lines, frames);
    }

    /** {@code String.format("%.0f", v)}. */
    static String whole(float v) {
        return String.format(Locale.ROOT, "%.0f", v);
    }

    private static String key(Enum<?> e) {
        return e.name().toLowerCase(Locale.ROOT);
    }

    /** The page's text as plain lines, top to bottom (pieces of one y joined by spaces): the tab button tooltip. */
    public List<String> plainLines() {
        Map<Integer, StringBuilder> rows = new java.util.TreeMap<>();
        lines.stream().sorted(java.util.Comparator.comparingInt(Text::y).thenComparingInt(Text::x)).forEach(t -> {
            StringBuilder sb = rows.computeIfAbsent(t.y(), y -> new StringBuilder());
            if (sb.length() > 0 && !(page == Page.CALENDAR && t.y() == CLOCK_Y)) {
                sb.append(' ');
            }
            sb.append(t.text());
        });
        List<String> out = new ArrayList<>();
        rows.values().forEach(sb -> out.add(sb.toString()));
        return out;
    }
}
