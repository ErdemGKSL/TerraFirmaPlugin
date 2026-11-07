/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.tabs;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.calendar.CalendarTicks;
import dev.tfp.core.calendar.Day;
import dev.tfp.core.calendar.Month;
import dev.tfp.core.climate.ClimateTemperatureStyle;
import dev.tfp.paper.i18n.Messages;
import dev.tfp.paper.tabs.TabLayout.Page;
import dev.tfp.paper.tabs.TabLayout.Tab;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import org.junit.jupiter.api.Test;

/** PAR-SURV-07: page text at TFC's positions, the overlay frames, the Koppen class and the title component. */
class TabPageTest {
    private static final Messages MESSAGES = Messages.fromJar();
    private static final TabPage.Lang EN = (k, a) -> MESSAGES.get("en_us", k, a);
    private static final TabPage.Lang TR = (k, a) -> MESSAGES.get("tr_tr", k, a);

    private static TabPage.Text at(TabPage page, int y, String text) {
        return page.lines().stream().filter(t -> t.y() == y && t.text().equals(text)).findFirst()
            .orElseThrow(() -> new AssertionError("no '" + text + "' at y " + y + " in " + page.lines()));
    }

    @Test
    void calendarDrawsClockDateBirthdayAndSeasonLikeCalendarScreen() {
        int dim = 8;
        long t = CalendarTicks.of(1004, Month.JULY, 7, 21, 5, dim);
        TabPage p = TabPage.calendar(new TabPage.CalendarInput(true, t, dim, true), EN);
        assertThat(p.label()).isEqualTo("Calendar");
        // drawLine(RIGHT, -1, 83, 22): 176 - 83 - 8 - width; "21" is 6 + 6 wide
        assertThat(at(p, 22, "21").x()).isEqualTo(85 - 12);
        assertThat(at(p, 22, ":").x()).isEqualTo((176 - 2) / 2);
        assertThat(at(p, 22, "05").x()).isEqualTo(91);
        String day = MESSAGES.get("en_us", "tabs.day." + Day.ofTotalDays(CalendarTicks.totalDays(t)).name().toLowerCase());
        String date = day + ", July 7, 1004";
        assertThat(at(p, 33, date).x()).isEqualTo((176 - TabFont.width(date)) / 2);
        at(p, 44, "Bioxx's Birthday!");
        at(p, 55, "Summer");
        assertThat(p.frames()).containsEntry(TabLayout.NIGHT_LAYER, 0)
            .containsEntry(TabLayout.tabLayer(Tab.CALENDAR), TabLayout.FRAME_ACTIVE)
            .containsEntry(TabLayout.tabLayer(Tab.NUTRITION), TabLayout.FRAME_INACTIVE);
        assertThat(p.plainLines()).containsExactly("21:05", date, "Bioxx's Birthday!", "Summer");
    }

    @Test
    void daysLeftLinesAndTheSouthernSeasonAndDaytime() {
        int dim = 8;
        TabPage mid = TabPage.calendar(new TabPage.CalendarInput(true, CalendarTicks.of(1001, Month.MARCH, 2, 12, 0, dim), dim, false), EN);
        at(mid, 44, "6 days left in March");
        at(mid, 55, "Early Autumn"); // the southern hemisphere shows September's season
        assertThat(mid.frames()).doesNotContainKey(TabLayout.NIGHT_LAYER);
        TabPage second = TabPage.calendar(new TabPage.CalendarInput(true, CalendarTicks.of(1001, Month.MARCH, 7, 4, 59, dim), dim, true), EN);
        at(second, 44, "1 day left in March");
        assertThat(second.frames()).containsKey(TabLayout.NIGHT_LAYER);
        TabPage last = TabPage.calendar(new TabPage.CalendarInput(true, CalendarTicks.of(1001, Month.MARCH, 8, 20, 0, dim), dim, true), EN);
        at(last, 44, "Last day of March!");
        TabPage unknown = TabPage.calendar(new TabPage.CalendarInput(false, 0, dim, true), EN);
        assertThat(unknown.lines()).extracting(TabPage.Text::text).containsExactly("-");
    }

    @Test
    void nutritionBarsAreTheFloorOfValueTimes93AndNamesEndAtX63() {
        TabPage p = TabPage.nutrition(new float[] {0.5f, 1f, 0f, 0.999f, 1.2f}, EN);
        assertThat(p.frames()).containsEntry(TabLayout.barLayer(0), 46).containsEntry(TabLayout.barLayer(1), 93)
            .doesNotContainKey(TabLayout.barLayer(2)).containsEntry(TabLayout.barLayer(3), 92)
            .containsEntry(TabLayout.barLayer(4), 93);
        TabPage.Text grain = at(p, 17, "Grain");
        assertThat(grain.x() + TabFont.width("Grain")).isEqualTo(63);
        at(p, 28, "Fruit");
        at(p, 39, "Vegetables");
        at(p, 50, "Protein");
        at(p, 61, "Dairy");
        assertThat(p.label()).isEqualTo("Nutrition");
    }

    @Test
    void climateLinesAreAtTfcPositions() {
        TabPage p = TabPage.climate(new TabPage.ClimateInput(true, 10.04f, 250.4f, 0.25f, 12.36f, 300.6f, true,
            ClimateTemperatureStyle.CELSIUS), EN);
        at(p, 18, "Cold Oceanic"); // 8 < 10.04 <= 12, |rainVar| <= 0.5: CFC
        assertThat(at(p, 32, "Temp:").x()).isEqualTo(8);
        assertThat(at(p, 32, "Avg: 10.0°C").x()).isEqualTo(44);
        assertThat(at(p, 32, "Now: 12.4°C").x()).isEqualTo(104);
        TabPage.Text rain = at(p, 46, "Rain:");
        assertThat(rain.color()).isEqualTo(TabPage.RAIN_COLOR);
        assertThat(at(p, 46, "Avg: 250mm").x()).isEqualTo(44);
        assertThat(at(p, 46, "Now: 301mm").x()).isEqualTo(104);
        at(p, 57, "Peak:");
        assertThat(at(p, 57, "313mm in July").x()).isEqualTo(44); // 250.4 * 1.25, positive variance
        TabPage dry = TabPage.climate(new TabPage.ClimateInput(true, 10f, 250f, -0.6f, 10f, 100f, true,
            ClimateTemperatureStyle.FAHRENHEIT), EN);
        at(dry, 18, "Cold Coastal");
        at(dry, 57, "400mm in January");
        at(dry, 32, "Avg: 50.0°F");
        TabPage none = TabPage.climate(new TabPage.ClimateInput(false, Float.NaN, Float.NaN, 0, Float.NaN, Float.NaN, true,
            ClimateTemperatureStyle.CELSIUS), EN);
        assertThat(none.lines()).extracting(TabPage.Text::text).containsExactly("-");
    }

    @Test
    void koppenFollowsTfc() {
        assertThat(Koppen.classify(-20f, 100f, 0f, true)).isEqualTo(Koppen.EF);
        assertThat(Koppen.classify(-12f, 100f, 0f, true)).isEqualTo(Koppen.ET);
        assertThat(Koppen.classify(25f, 50f, 0f, true)).isEqualTo(Koppen.BWH);
        assertThat(Koppen.classify(10f, 100f, 0f, true)).isEqualTo(Koppen.BSK);
        assertThat(Koppen.classify(25f, 400f, 0.6f, true)).isEqualTo(Koppen.AM);
        assertThat(Koppen.classify(25f, 300f, 0.6f, true)).isEqualTo(Koppen.AW);
        assertThat(Koppen.classify(25f, 300f, 0.6f, false)).isEqualTo(Koppen.AS); // the south flips the variance
        assertThat(Koppen.classify(25f, 300f, 0f, true)).isEqualTo(Koppen.AF);
        assertThat(Koppen.classify(18f, 300f, 0f, true)).isEqualTo(Koppen.CFA);
        assertThat(Koppen.classify(13f, 300f, -0.6f, true)).isEqualTo(Koppen.CSB);
        assertThat(Koppen.classify(5f, 300f, 0.6f, true)).isEqualTo(Koppen.DWA);
        assertThat(Koppen.classify(0f, 300f, 0f, true)).isEqualTo(Koppen.DFB);
        assertThat(Koppen.classify(-5f, 300f, -0.6f, true)).isEqualTo(Koppen.DSC);
        assertThat(Koppen.classify(-10f, 300f, 0f, true)).isEqualTo(Koppen.DFD);
        for (Koppen k : Koppen.values()) {
            assertThat(MESSAGES.get("en_us", "tabs.koppen." + k.key())).doesNotStartWith("tabs.");
        }
    }

    @Test
    void textOutsideTheFontsBecomesAQuestionMarkAndTurkishFits() {
        assertThat(TabFont.sanitize("a中b")).isEqualTo("a?b");
        TabPage tr = TabPage.nutrition(new float[] {0, 0, 0, 0, 0}, TR);
        assertThat(tr.label()).isEqualTo(MESSAGES.get("tr_tr", "tabs.title.nutrition"));
        for (String key : List.of("tabs.title.climate", "tabs.season.february", "tabs.day.tuesday", "tooltip.month.august")) {
            String s = MESSAGES.get("tr_tr", key);
            assertThat(TabFont.sanitize(s)).as(key).isEqualTo(s);
        }
        assertThat(TabFont.width("Grain")).isEqualTo(6 + 6 + 6 + 2 + 6);
        assertThat(TabFont.width("i l")).isEqualTo(2 + 4 + 3);
    }

    @Test
    void everyLineIsAtAFontLine() {
        TabPage cal = TabPage.calendar(new TabPage.CalendarInput(true, 123_456_789L, 8, true), EN);
        TabPage nut = TabPage.nutrition(new float[] {0.1f, 0.2f, 0.3f, 0.4f, 0.5f}, EN);
        TabPage cli = TabPage.climate(new TabPage.ClimateInput(true, 1, 2, 0, 3, 4, true, ClimateTemperatureStyle.KELVIN), EN);
        for (TabPage p : List.of(cal, nut, cli)) {
            for (TabPage.Text t : p.lines()) {
                assertThat(TabFont.LINES).as(p.page() + " " + t).contains(t.y());
            }
        }
    }

    @Test
    void theTitleIsGlyphsThenFontedTextThenTheLabel() {
        TabPage p = TabPage.nutrition(new float[] {0.5f, 0, 0, 0, 0}, EN);
        Component title = TabTitles.title(true, p);
        List<Component> parts = title.children();
        String tags = ((TextComponent) parts.getFirst()).content();
        assertThat(tags).startsWith("<shift:-8><image:tfp:gui/player_nutrition>")
            .contains("<image:tfp:gui/player_tab_nutrition:1:0>")
            .contains("<image:tfp:gui/player_tab_calendar:0:0>")
            .contains("<image:tfp:gui/player_nutrition_bar_0:46:0>")
            .doesNotContain("player_nutrition_bar_1");
        assertThat(parts).anySatisfy(c -> {
            assertThat(((TextComponent) c).content()).isEqualTo("Grain");
            assertThat(c.font()).hasToString("tfp:tabs/line_17");
        });
        assertThat(((TextComponent) parts.getLast()).content()).isEqualTo("Nutrition");
        // the pen walk: shift to x, text, ..., shift back to 8 before the label
        int pen = 8;
        for (Component c : parts.subList(1, parts.size() - 1)) {
            String s = ((TextComponent) c).content();
            if (s.startsWith("<shift:")) {
                for (String n : s.replace("<shift:", "").split(">")) {
                    pen += Integer.parseInt(n);
                }
            } else {
                pen += TabFont.width(s);
            }
        }
        assertThat(pen).isEqualTo(8);
        assertThat(TabTitles.title(false, p)).isEqualTo(Component.text("Nutrition"));
    }

    @Test
    void layoutPutsTheButtonsOnTheTabRowAndTheBarsWhereTfcDrawsThem() {
        assertThat(Tab.CALENDAR.slot()).isEqualTo(28);
        assertThat(Tab.CALENDAR.image().x()).isEqualTo(25);
        assertThat(Tab.CALENDAR.image().y()).isEqualTo(71);
        assertThat(TabLayout.nutritionBar(4).y()).isEqualTo(18 + 44);
        assertThat(TabLayout.CHEST.height()).isEqualTo(186);
        assertThat(TabLayout.CHEST.playerRowY(0) - 84).isEqualTo(19);
        assertThat(Page.of(Tab.INVENTORY)).isNull();
        assertThat(Map.of(Tab.atSlot(30), 1)).containsKey(Tab.CLIMATE);
    }
}
