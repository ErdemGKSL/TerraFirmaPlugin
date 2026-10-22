/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HudLayoutsTest {
    private static final String PIN = TfcCheckout.PINNED_COMMIT;

    @TempDir
    Path tmp;

    /** The real catalogue counts section: 9 placeholders, 4 bars, 4 readouts, 2 popups, 1 indicator, 3 hidden-vanilla. */
    @Test
    void realCatalogueRowCountsMatchTheCountsSection() {
        HudLayouts.Catalogue cat = HudLayouts.parse(HudLayouts.catalogue());

        assertThat(cat.elements()).hasSize(23);
        Map<String, Integer> expected = Map.of(
            "placeholder", 9, "bar", 4, "readout", 4, "popup", 2, "indicator", 1, "hidden-vanilla", 3);
        assertThat(countsByKind(cat.elements())).isEqualTo(expected);
        assertThat(cat.total()).isEqualTo(expected);
    }

    @Test
    void realCatalogueEmitsOneLayoutPerSpecifiedElementAndLeavesOutTheUnspecifiedOnes() {
        HudLayouts.Summary s = HudLayouts.run(new OutputTree(), HudLayouts.catalogue());

        assertThat(s.emitted()).isEqualTo(Map.of(
            "placeholder", 9, "bar", 4, "readout", 0, "popup", 1, "indicator", 0, "hidden-vanilla", 3));
        assertThat(s.leftOut()).hasSize(4);
        assertThat(String.join("\n", s.leftOut())).contains(
            "m3-surv-02-nutrition-readout", "m6-mech-01-rotation-readout", "m4-pros-01-result-message",
            "m6-glass-01-step-indicator");
    }

    @Test
    void everyEmittedIdStartsWithTfpPrefix() {
        OutputTree out = new OutputTree();
        HudLayouts.run(out, HudLayouts.catalogue());

        for (String file : List.of(HudLayouts.IMAGES, HudLayouts.BAR_LAYOUTS, HudLayouts.READOUT_LAYOUTS,
            HudLayouts.POPUP_LAYOUTS, HudLayouts.POPUPS, HudLayouts.PLACEHOLDERS, HudLayouts.HIDDEN)) {
            if (!out.contains(file)) {
                continue;
            }
            Map<String, Object> root = yaml(out, file);
            for (String key : root.keySet()) {
                if (key.equals("placeholders") || key.equals("hidden-vanilla")) {
                    for (Object item : (List<?>) root.get(key)) {
                        Map<?, ?> m = (Map<?, ?>) item;
                        String id = m.containsKey("name") ? (String) m.get("name") : (String) m.get("id");
                        assertThat(id).as(file + " " + key).startsWith("tfp_");
                    }
                } else {
                    assertThat(key).as(file).startsWith("tfp_");
                }
            }
        }
    }

    @Test
    void layoutFilesHoldTheBarsReadoutsAndPopupsOnly() {
        OutputTree out = new OutputTree();
        HudLayouts.run(out, HudLayouts.catalogue());

        assertThat(yaml(out, HudLayouts.BAR_LAYOUTS).keySet()).containsExactlyInAnyOrder(
            "tfp_m3_surv_06_health_bar", "tfp_m3_surv_06_thirst_bar", "tfp_m3_surv_06_hunger_bar", "tfp_m3_surv_06_air_bar");
        assertThat(out.contains(HudLayouts.READOUT_LAYOUTS)).as("both readouts duplicate BetterHudPack").isFalse();
        assertThat(yaml(out, HudLayouts.POPUP_LAYOUTS).keySet()).containsExactly("tfp_m3_surv_06_held_temp_popup");
        assertThat(yaml(out, HudLayouts.POPUPS).keySet()).containsExactly("tfp_m3_surv_06_held_temp_popup");
    }

    @Test
    void healthBarReadsTheValueAndTheMaxAndShowsTheSlashText() {
        OutputTree out = new OutputTree();
        HudLayouts.run(out, HudLayouts.catalogue());

        Map<String, Object> images = yaml(out, HudLayouts.IMAGES);
        assertThat(images).containsKeys("tfp_m3_surv_06_health_bar_bg", "tfp_m3_surv_06_health_bar_fill");
        assertThat(text(images, "tfp_m3_surv_06_health_bar_fill")).contains("class=placeholder", "value=tfp_health", "max=tfp_max_health");

        Map<String, Object> bar = map(yaml(out, HudLayouts.BAR_LAYOUTS).get("tfp_m3_surv_06_health_bar"));
        Map<String, Object> text = map(map(bar.get("texts")).get("1"));
        assertThat(text.get("pattern")).isEqualTo("[tfp_health]//[tfp_max_health]");
        assertThat(text.get("name")).isEqualTo("tfp_font");
    }

    @Test
    void thirstAndHungerBarsUseTheirOwnListeners() {
        OutputTree out = new OutputTree();
        HudLayouts.run(out, HudLayouts.catalogue());

        Map<String, Object> images = yaml(out, HudLayouts.IMAGES);
        assertThat(text(images, "tfp_m3_surv_06_thirst_bar_fill")).contains("class=tfp_thirst");
        assertThat(text(images, "tfp_m3_surv_06_hunger_bar_fill")).contains("class=tfp_hunger");
        assertThat(map(yaml(out, HudLayouts.BAR_LAYOUTS).get("tfp_m3_surv_06_thirst_bar")).containsKey("texts")).isFalse();
    }

    /** The date readout that the catalogue row duplicates is the one BetterHudPack writes, with the catalogue tick. */
    @Test
    void dateReadoutIsTheOneBetterHudPackWritesWithTheCatalogueTick() {
        Map<String, Object> packed = new org.yaml.snakeyaml.Yaml().load(BetterHudPack.LAYOUTS);
        Map<String, Object> date = map(map(packed.get("tfp_date")).get("texts"));
        assertThat(map(date.get(1)))
            .containsEntry("pattern", "[tfp_date]  ([tfp_season])")
            .containsEntry("tick", 20);
    }

    @Test
    void packLayoutIdsAreTheLayoutsBetterHudPackWrites() {
        Map<String, Object> packed = new org.yaml.snakeyaml.Yaml().load(BetterHudPack.LAYOUTS);
        assertThat(packed.keySet()).isEqualTo(BetterHudPack.LAYOUT_IDS);
    }

    @Test
    void noEmittedLayoutIdIsWrittenByBetterHudPack() {
        OutputTree out = new OutputTree();
        HudLayouts.run(out, HudLayouts.catalogue());

        for (String file : List.of(HudLayouts.BAR_LAYOUTS, HudLayouts.POPUP_LAYOUTS, HudLayouts.POPUPS)) {
            assertThat(yaml(out, file).keySet()).as(file).doesNotContainAnyElementsOf(BetterHudPack.LAYOUT_IDS);
        }
        assertThat(out.contains(HudLayouts.READOUT_LAYOUTS)).isFalse();
    }

    @Test
    void popupShowsWhileTheHeldTemperatureIsAboveZero() {
        OutputTree out = new OutputTree();
        HudLayouts.run(out, HudLayouts.catalogue());

        Map<String, Object> popup = map(yaml(out, HudLayouts.POPUPS).get("tfp_m3_surv_06_held_temp_popup"));
        assertThat(popup).containsEntry("default", true);
        Map<String, Object> condition = map(map(popup.get("conditions")).get("1"));
        assertThat(condition).containsEntry("first", "tfp_held_temp").containsEntry("second", 0).containsEntry("operation", ">");
    }

    @Test
    void hiddenVanillaListCarriesTheSpriteFilesOfEachRow() {
        OutputTree out = new OutputTree();
        HudLayouts.run(out, HudLayouts.catalogue());

        List<?> hidden = (List<?>) yaml(out, HudLayouts.HIDDEN).get("hidden-vanilla");
        assertThat(hidden).hasSize(3);
        Map<String, Object> hearts = map(hidden.get(0));
        assertThat(hearts).containsEntry("files", 47)
            .containsEntry("sprites", List.of("gui/sprites/hud/heart/*.png"));
        assertThat(map(hidden.get(1))).containsEntry("files", 6);
        assertThat(map(hidden.get(2))).containsEntry("files", 3)
            .containsEntry("sprites", List.of("gui/sprites/hud/air.png", "gui/sprites/hud/air_bursting.png",
                "gui/sprites/hud/air_empty.png"));
    }

    @Test
    void placeholderListNamesTheSnapshotPlaceholdersOfTheCatalogue() {
        OutputTree out = new OutputTree();
        HudLayouts.run(out, HudLayouts.catalogue());

        List<?> placeholders = (List<?>) yaml(out, HudLayouts.PLACEHOLDERS).get("placeholders");
        Set<Object> names = new java.util.HashSet<>();
        placeholders.forEach(p -> names.add(map(p).get("name")));
        assertThat(names).containsExactlyInAnyOrder("tfp_health", "tfp_max_health", "tfp_thirst", "tfp_hunger",
            "tfp_held_temp", "tfp_date", "tfp_season", "tfp_ambient_temp", "tfp_air");
    }

    @Test
    void datagenRunWritesTheHudFilesIntoTheBetterHudPack() throws IOException {
        Path root = Fixture.create(tmp.resolve("tfc"), PIN);
        OutputTree out = Datagen.generate(root, PIN).tree();

        assertThat(out.contains(HudLayouts.BAR_LAYOUTS)).isTrue();
        assertThat(out.contains(HudLayouts.POPUPS)).isTrue();
        assertThat(out.contains(HudLayouts.PLACEHOLDERS)).isTrue();
        assertThat(out.contains(HudLayouts.HIDDEN)).isTrue();
    }

    /** Vanilla's XP bar starts 29 GUI px above the bottom, the hotbar 22 px; the XP number is centred, 9 px either side. */
    @Test
    void barsSitAboveTheXpRowAndOutsideTheXpNumber() {
        OutputTree out = new OutputTree();
        HudLayouts.run(out, HudLayouts.catalogue());

        Map<String, Object> layouts = yaml(out, HudLayouts.BAR_LAYOUTS);
        for (String bar : layouts.keySet()) {
            Map<String, Object> bg = map(map(map(layouts.get(bar)).get("images")).get("1"));
            String sprite = bar.substring("tfp_".length()) + "_bg.png";
            int height = HudBarArt.image(sprite).getHeight();
            int top = (Integer) bg.get("y");
            int x = (Integer) bg.get("x");
            assertThat(top + height).as(bar + " bottom").isLessThanOrEqualTo(-30);
            int left = x - HudBarArt.WIDTH / 2;
            int right = left + HudBarArt.WIDTH;
            assertThat(right <= -9 || left >= 9).as(bar + " clear of the XP number").isTrue();
            assertThat(left).as(bar).isGreaterThanOrEqualTo(-92);
            assertThat(right).as(bar).isLessThanOrEqualTo(92);
        }
    }

    @Test
    void rightColumnBarsHaveOneHeightAndOneStepLikeTfc() {
        OutputTree out = new OutputTree();
        HudLayouts.run(out, HudLayouts.catalogue());

        Map<String, Object> layouts = yaml(out, HudLayouts.BAR_LAYOUTS);
        int hunger = top(layouts, "tfp_m3_surv_06_hunger_bar");
        int thirst = top(layouts, "tfp_m3_surv_06_thirst_bar");
        int air = top(layouts, "tfp_m3_surv_06_air_bar");
        assertThat(List.of(hunger - thirst, thirst - air)).containsOnly(HudLayouts.ROW_STEP);
        for (String s : List.of("hunger", "thirst", "air")) {
            assertThat(HudBarArt.image("m3_surv_06_" + s + "_bar_fill.png").getHeight()).as(s).isEqualTo(HudBarArt.BAR_HEIGHT);
        }
        assertThat(HudBarArt.BAR_HEIGHT).isLessThan(HudLayouts.ROW_STEP);
        assertThat(top(layouts, "tfp_m3_surv_06_health_bar") + HudBarArt.HEALTH_HEIGHT).isEqualTo(hunger + HudBarArt.BAR_HEIGHT);
    }

    @Test
    void healthTextSitsInsideTheThickBarWithTfcShadow() {
        OutputTree out = new OutputTree();
        HudLayouts.run(out, HudLayouts.catalogue());

        Map<String, Object> bar = map(yaml(out, HudLayouts.BAR_LAYOUTS).get("tfp_m3_surv_06_health_bar"));
        Map<String, Object> text = map(map(bar.get("texts")).get("1"));
        int barTop = top(yaml(out, HudLayouts.BAR_LAYOUTS), "tfp_m3_surv_06_health_bar");
        int glyphs = 7; // the vanilla font at scale 0.5
        assertThat((Integer) text.get("y")).isEqualTo(barTop + 1);
        assertThat((Integer) text.get("y") + glyphs + 1).as("glyphs and shadow").isLessThanOrEqualTo(barTop + HudBarArt.HEALTH_HEIGHT);
        assertThat(text).containsEntry("outline", "FF680000").containsEntry("align", "center");
        assertThat(HudBarArt.HEALTH_HEIGHT).isEqualTo(9);
    }

    @Test
    void airBarIsHiddenAtFullBreathAndTheOtherBarsAlwaysShow() {
        OutputTree out = new OutputTree();
        HudLayouts.run(out, HudLayouts.catalogue());

        Map<String, Object> layouts = yaml(out, HudLayouts.BAR_LAYOUTS);
        Map<String, Object> air = map(map(map(layouts.get("tfp_m3_surv_06_air_bar")).get("conditions")).get("1"));
        assertThat(air).containsEntry("first", "tfp_air").containsEntry("second", 300).containsEntry("operation", "<");
        for (String bar : List.of("tfp_m3_surv_06_health_bar", "tfp_m3_surv_06_hunger_bar", "tfp_m3_surv_06_thirst_bar")) {
            assertThat(map(layouts.get(bar))).as(bar).doesNotContainKey("conditions");
        }
    }

    @Test
    void popupShowsTheHeldTemperatureInWholeDegreesCelsius() {
        OutputTree out = new OutputTree();
        HudLayouts.run(out, HudLayouts.catalogue());

        Map<String, Object> text = map(map(map(yaml(out, HudLayouts.POPUP_LAYOUTS).get("tfp_m3_surv_06_held_temp_popup"))
            .get("texts")).get("1"));
        assertThat(text).containsEntry("pattern", "[tfp_held_temp]\u00B0C").containsEntry("outline", true);
        assertThat(map(text.get("placeholder-string-format"))).containsEntry("number", "#");
    }

    @Test
    void barSpritesInThePackAreTheTfcSlices() {
        OutputTree out = new OutputTree();
        HudLayouts.run(out, HudLayouts.catalogue());

        for (String file : HudBarArt.SLICES.keySet()) {
            assertThat(out.get(BetterHudPack.DIR + "assets/tfp/hud/" + file)).as(file).isEqualTo(HudBarArt.png(file));
        }
    }

    private static int top(Map<String, Object> layouts, String bar) {
        return (Integer) map(map(map(layouts.get(bar)).get("images")).get("1")).get("y");
    }

    @Test
    void missingCatalogueIsRefused() {
        assertThatThrownBy(() -> HudLayouts.run(new OutputTree(), tmp.resolve("nope.md")))
            .isInstanceOf(DatagenException.class).hasMessageContaining("HUD catalogue not found");
    }

    @Test
    void catalogueWithoutCountsSectionIsRefused() throws IOException {
        Path file = write(replaceOnce(real(), "## 3. Counts", "## 3. Tally"));
        assertThatThrownBy(() -> HudLayouts.run(new OutputTree(), file))
            .isInstanceOf(DatagenException.class).hasMessageContaining("counts section");
    }

    @Test
    void rowWithTooFewCellsIsRefusedWithTheRow() throws IOException {
        Path file = write(replaceOnce(real(), "| `m3-surv-06-thirst-bar` | thirst bar | M3-SURV-06 | bar | ",
            "| `m3-surv-06-thirst-bar` | thirst bar | M3-SURV-06 | "));
        assertThatThrownBy(() -> HudLayouts.run(new OutputTree(), file))
            .isInstanceOf(DatagenException.class).hasMessageContaining("expected 8 cells")
            .hasMessageContaining("m3-surv-06-thirst-bar");
    }

    @Test
    void unknownKindIsRefused() throws IOException {
        Path file = write(replaceOnce(real(), "| bar | `tfp_thirst`", "| gauge | `tfp_thirst`"));
        assertThatThrownBy(() -> HudLayouts.run(new OutputTree(), file))
            .isInstanceOf(DatagenException.class).hasMessageContaining("unknown kind \"gauge\"");
    }

    @Test
    void countsThatDisagreeWithTheRowsAreRefused() throws IOException {
        Path file = write(replaceOnce(real(), "| bar | 4 | 0 |", "| bar | 5 | 0 |"));
        assertThatThrownBy(() -> HudLayouts.run(new OutputTree(), file))
            .isInstanceOf(DatagenException.class).hasMessageContaining("kind bar has 4 rows");
    }

    @Test
    void placeholderWithoutTfpPrefixIsRefused() throws IOException {
        Path file = write(replaceOnce(real(), "`tfp_health` (snapshot)", "`health` (snapshot)"));
        assertThatThrownBy(() -> HudLayouts.run(new OutputTree(), file))
            .isInstanceOf(DatagenException.class).hasMessageContaining("must start with tfp_");
    }

    @Test
    void readoutWithoutTickIsRefused() throws IOException {
        Path file = write(replaceOnce(real(), "`tfp_season`; text `tick: 20`", "`tfp_season`"));
        assertThatThrownBy(() -> HudLayouts.run(new OutputTree(), file))
            .isInstanceOf(DatagenException.class).hasMessageContaining("names its tick");
    }

    @Test
    void duplicateIdIsRefused() throws IOException {
        String text = real();
        String row = text.lines().filter(l -> l.startsWith("| `m3-surv-06-hunger-bar`")).findFirst().orElseThrow();
        Path file = write(text.replace(row, row + "\n" + row));
        assertThatThrownBy(() -> HudLayouts.run(new OutputTree(), file))
            .isInstanceOf(DatagenException.class).hasMessageContaining("listed twice");
    }

    private static String real() throws IOException {
        return Files.readString(HudLayouts.catalogue(), StandardCharsets.UTF_8);
    }

    private static String replaceOnce(String text, String from, String to) {
        if (text.indexOf(from) < 0 || text.indexOf(from) != text.lastIndexOf(from)) {
            throw new IllegalStateException("fixture text must occur exactly once: " + from);
        }
        return text.replace(from, to);
    }

    private Path write(String text) throws IOException {
        Path file = tmp.resolve("catalogue-" + System.nanoTime() + ".md");
        Files.writeString(file, text, StandardCharsets.UTF_8);
        return file;
    }

    private static Map<String, Object> yaml(OutputTree out, String file) {
        byte[] bytes = out.get(file);
        assertThat(bytes).as(file).isNotNull();
        Object root = new org.yaml.snakeyaml.Yaml().load(new String(bytes, StandardCharsets.UTF_8));
        return map(root);
    }

    private static String text(Map<String, Object> images, String name) {
        return map(images.get(name)).toString();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object o) {
        return (Map<String, Object>) o;
    }

    private static Map<String, Integer> countsByKind(List<HudLayouts.Element> elements) {
        Map<String, Integer> out = new java.util.LinkedHashMap<>();
        for (HudLayouts.Element e : elements) {
            out.merge(e.kind(), 1, Integer::sum);
        }
        return out;
    }
}
