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
import dev.tfp.core.calendar.Season;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The info plate of {@link BetterHudPack} holds the longest date line (PAR-HUD-13). */
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

    /** Every date line the HUD can show with a two-digit day and a four-digit year, with every season. */
    static List<String> dateLines() {
        int daysInMonth = 30;
        java.util.ArrayList<String> lines = new java.util.ArrayList<>();
        for (long year : List.of((long) CalendarTicks.FIRST_YEAR, 9999L)) {
            for (Month m : Month.values()) {
                for (int day = 1; day <= daysInMonth; day++) {
                    long ticks = CalendarTicks.of(year, m, day, 12, 0, daysInMonth);
                    String date = CalendarFormat.date(ticks, daysInMonth);
                    for (Season s : Season.values()) {
                        String season = s.name().charAt(0) + s.name().substring(1).toLowerCase(Locale.ROOT);
                        lines.add(date + "  (" + season + ")");
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
        assertThat(CalendarFormat.season(ticks, 30)).isEqualTo("Fall");
    }

    @Test
    void advancesMatchTheInGameMeasurement() {
        assertThat(width("December 6, 1000  (Winter)")).isEqualTo(134);
        assertThat(width("September 30, 1000  (Fall)")).isEqualTo(132);
    }

    @Test
    void plateIsAtLeastAsWideAsTheLongestDateLinePlusInsetShadowAndMargin() {
        int longest = dateLines().stream().mapToInt(BetterHudPackTest::width).max().orElseThrow();
        assertThat(longest).isEqualTo(width("September 30, 1000  (Summer)")).isEqualTo(150);

        Map<String, Object> layouts = new org.yaml.snakeyaml.Yaml().load(BetterHudPack.LAYOUTS);
        int textX = (Integer) at(layouts, "tfp_date", "texts", "1", "x");
        HudLayouts.OverlaySpec plate = HudLayouts.OVERLAYS.get("m2-cal-02-info-plate").get(0);
        int inset = textX - plate.x();
        BufferedImage img = HudArt.image(PLATE);

        assertThat(inset).isEqualTo(5);
        assertThat(img.getWidth()).isGreaterThanOrEqualTo(inset + longest + SHADOW + MARGIN);
        assertThat(img.getHeight()).isEqualTo(22);
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
