/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.calendar.CalendarFormat;
import dev.tfp.core.calendar.CalendarTicks;
import dev.tfp.core.calendar.Month;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The info plate of {@link BetterHudPack} holds the longest date line (PAR-HUD-13, TFC's season names PAR-HUD-15). */
class BetterHudPackTest {
    /**
     * Advance widths in GUI pixels of the vanilla bitmap font ({@code assets/minecraft/textures/font/ascii.png} of the
     * 1.21.11 client: rightmost opaque column + 2, space 4), which BetterHud merges into {@code tfp_font}. At the text
     * scale 0.5 of our layouts one advance is one GUI pixel (measured in PAR-HUD-06: "December 6, 1000  (Winter)" is
     * 134 GUI px in game, the sum below).
     */
    static final Map<Character, Integer> ADVANCE = advances();

    private static Map<Character, Integer> advances() {
        java.util.HashMap<Character, Integer> a = new java.util.HashMap<>();
        for (char c = 'A'; c <= 'Z'; c++) {
            a.put(c, 6);
        }
        for (char c = 'a'; c <= 'z'; c++) {
            a.put(c, 6);
        }
        for (char c = '0'; c <= '9'; c++) {
            a.put(c, 6);
        }
        a.put('I', 4);
        a.put('f', 5);
        a.put('i', 2);
        a.put('k', 5);
        a.put('l', 3);
        a.put('t', 4);
        a.put(' ', 4);
        a.put(',', 2);
        a.put('(', 4);
        a.put(')', 4);
        return Map.copyOf(a);
    }

    /** The plate's right margin past the last glyph's shadow: its 2 px frame plus 2 px of plain inside. */
    static final int MARGIN = 4;
    /** The text shadow ({@code outline: true}) is one pixel right of the glyph. */
    static final int SHADOW = 1;
    static final String PLATE = "m2_cal_02_info_plate.png";

    static int width(String s) {
        int w = 0;
        for (char c : s.toCharArray()) {
            Integer a = ADVANCE.get(c);
            assertThat(a).as("advance of '" + c + "' in \"" + s + "\"").isNotNull();
            w += a;
        }
        return w;
    }

    /**
     * Every date line the HUD can show with a two-digit day and a four-digit year, with TFC's 12 season names
     * ({@link CalendarFormat#seasonName}) in both hemispheres (PAR-HUD-15: the south shows the opposite month's name).
     * The day is capped at 30, TFC's default month length; TFC allows any month length of 1 or more, so a configured
     * month of 100 days or more would give a three-digit day that this check does not cover.
     */
    static List<String> dateLines() {
        int daysInMonth = 30;
        java.util.ArrayList<String> lines = new java.util.ArrayList<>();
        for (long year : List.of((long) CalendarTicks.FIRST_YEAR, 9999L)) {
            for (Month m : Month.values()) {
                for (int day = 1; day <= daysInMonth; day++) {
                    long ticks = CalendarTicks.of(year, m, day, 12, 0, daysInMonth);
                    String date = CalendarFormat.date(ticks, daysInMonth);
                    for (boolean north : new boolean[] {true, false}) {
                        lines.add(date + "  (" + CalendarFormat.seasonName(ticks, daysInMonth, north) + ")");
                    }
                }
            }
        }
        return lines;
    }

    @Test
    void theDatePatternIsTheOneMeasuredHere() {
        assertThat(BetterHudPack.LAYOUTS).contains("pattern: \"[tfp_date]  ([tfp_season])\"");
        long ticks = CalendarTicks.of(1000, Month.SEPTEMBER, 30, 12, 0, 30);
        assertThat(CalendarFormat.date(ticks, 30)).isEqualTo("September 30, 1000");
        assertThat(CalendarFormat.seasonName(ticks, 30, true)).isEqualTo("Early Autumn");
        assertThat(CalendarFormat.seasonName(ticks, 30, false)).isEqualTo("Early Spring");
    }

    @Test
    void advancesMatchTheInGameMeasurement() {
        assertThat(width("December 6, 1000  (Winter)")).isEqualTo(134);
        assertThat(width("September 30, 1000  (Fall)")).isEqualTo(132);
    }

    @Test
    void plateIsAtLeastAsWideAsTheLongestDateLinePlusInsetShadowAndMargin() {
        int longest = dateLines().stream().mapToInt(BetterHudPackTest::width).max().orElseThrow();
        assertThat(longest).isEqualTo(width("September 30, 1000  (Early Autumn)")).isEqualTo(179);

        Map<String, Object> layouts = new org.yaml.snakeyaml.Yaml().load(BetterHudPack.LAYOUTS);
        int textX = (Integer) at(layouts, "tfp_date", "texts", "1", "x");
        HudLayouts.OverlaySpec plate = HudLayouts.OVERLAYS.get("m2-cal-02-info-plate").get(0);
        int inset = textX - plate.x();
        BufferedImage img = HudArt.image(PLATE);

        assertThat(inset).isEqualTo(5);
        assertThat(img.getWidth()).isGreaterThanOrEqualTo(inset + longest + SHADOW + MARGIN).isGreaterThanOrEqualTo(189);
        assertThat(img.getHeight()).isEqualTo(22);
    }

    /**
     * TFC's season names ({@code tfc.enum.season.<month>} in TFC's en_us.json at c3b4d9536), indexed by the
     * hemisphere-adjusted month: CalendarScreen shows {@code getHemispheralCalendarMonthOfYear(north)} in
     * {@code Month.Style.SEASON}, and the southern hemisphere uses the opposite month.
     */
    static final List<String> TFC_SEASON = List.of("Winter", "Late Winter", "Early Spring", "Spring", "Late Spring",
        "Early Summer", "Summer", "Late Summer", "Early Autumn", "Autumn", "Late Autumn", "Early Winter");

    /** PAR-HUD-15: the measured lines use TFC's names, and the 189 px plate covers the longest of them in either hemisphere. */
    @Test
    void dateLinesUseTfcSeasonNamesInBothHemispheres() {
        assertThat(CalendarFormat.SEASON_NAMES).isEqualTo(TFC_SEASON);
        List<String> lines = dateLines();
        assertThat(lines).contains("September 30, 1000  (Early Autumn)", "September 30, 1000  (Early Spring)",
            "March 30, 1000  (Early Autumn)");
        assertThat(lines).noneMatch(l -> l.endsWith("(Fall)"));
        // Any name with any date ("September 30, 1000  (Early Summer)", a pair that never occurs) would be 181 px.
        assertThat(width("September 30, 1000  (Early Summer)")).isEqualTo(181);
        assertThat(5 + 179 + SHADOW + MARGIN).isEqualTo(189);
    }

    /** BetterHud trims fully transparent columns; the plate keeps its width only while both edge columns are opaque. */
    @Test
    void plateEdgeColumnsAreOpaqueSoBetterHudKeepsItsWidth() {
        BufferedImage img = HudArt.image(PLATE);
        boolean left = false;
        boolean right = false;
        for (int y = 0; y < img.getHeight(); y++) {
            left |= (img.getRGB(0, y) >>> 24) != 0;
            right |= (img.getRGB(img.getWidth() - 1, y) >>> 24) != 0;
        }
        assertThat(left).isTrue();
        assertThat(right).isTrue();
    }

    /** The whole info HUD sits one GUI pixel right, so its left frame column stays on screen at GUI scale 2 and 3. */
    @Test
    void everyInfoLayoutCarriesTheSameScreenEdgeShift() {
        Map<String, Object> huds = new org.yaml.snakeyaml.Yaml().load(BetterHudPack.HUDS);
        Map<?, ?> entries = (Map<?, ?>) at(huds, "tfp_date_hud", "layouts");
        assertThat(entries).hasSize(4);
        for (Object e : entries.values()) {
            Map<?, ?> entry = (Map<?, ?>) e;
            assertThat(entry.get("gui")).isEqualTo(Map.of("x", 0, "y", 0));
            assertThat(entry.get("pixel")).as(String.valueOf(entry.get("name")))
                .isEqualTo(Map.of("x", BetterHudPack.INFO_SHIFT_X, "y", 0));
        }
        assertThat(BetterHudPack.INFO_SHIFT_X).isEqualTo(1);
    }

    private static Object at(Map<?, ?> root, String... path) {
        Object o = root;
        for (String p : path) {
            Map<?, ?> m = (Map<?, ?>) o;
            o = m.containsKey(p) ? m.get(p) : m.get(Integer.valueOf(p));
        }
        return o;
    }
}
