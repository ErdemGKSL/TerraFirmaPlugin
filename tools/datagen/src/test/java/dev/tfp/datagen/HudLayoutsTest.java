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

        assertThat(cat.elements()).hasSize(28);
        Map<String, Integer> expected = Map.of(
            "placeholder", 10, "bar", 6, "overlay", 1, "readout", 4, "popup", 2, "indicator", 1, "hidden-vanilla", 4);
        assertThat(countsByKind(cat.elements())).isEqualTo(expected);
        assertThat(cat.total()).isEqualTo(expected);
    }

    @Test
    void realCatalogueEmitsOneLayoutPerSpecifiedElementAndLeavesOutTheUnspecifiedOnes() {
        HudLayouts.Summary s = HudLayouts.run(new OutputTree(), HudLayouts.catalogue());

        assertThat(s.emitted()).isEqualTo(Map.of(
            "placeholder", 10, "bar", 6, "overlay", 1, "readout", 0, "popup", 1, "indicator", 0, "hidden-vanilla", 4));
        assertThat(s.leftOut()).hasSize(4);
        assertThat(String.join("\n", s.leftOut())).contains(
            "m3-surv-02-nutrition-readout", "m6-mech-01-rotation-readout", "m4-pros-01-result-message",
            "m6-glass-01-step-indicator");
    }

    @Test
    void everyEmittedIdStartsWithTfpPrefix() {
        OutputTree out = new OutputTree();
        HudLayouts.run(out, HudLayouts.catalogue());

        for (String file : List.of(HudLayouts.IMAGES, HudLayouts.BAR_LAYOUTS, HudLayouts.INFO_LAYOUTS, HudLayouts.READOUT_LAYOUTS,
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
            "tfp_m3_surv_06_health_bar", "tfp_m3_surv_06_thirst_bar", "tfp_m3_surv_06_hunger_bar", "tfp_m3_surv_06_air_bar",
            "tfp_m3_surv_06_armor_bar");
        assertThat(yaml(out, HudLayouts.INFO_LAYOUTS).keySet()).isEqualTo(BetterHudPack.INFO_LAYOUTS);
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
        assertThat(hidden).hasSize(4);
        Map<String, Object> hearts = map(hidden.get(0));
        assertThat(hearts).containsEntry("files", 47)
            .containsEntry("sprites", List.of("gui/sprites/hud/heart/*.png"));
        assertThat(map(hidden.get(1))).containsEntry("files", 6);
        assertThat(map(hidden.get(2))).containsEntry("files", 3)
            .containsEntry("sprites", List.of("gui/sprites/hud/air.png", "gui/sprites/hud/air_bursting.png",
                "gui/sprites/hud/air_empty.png"));
        assertThat(map(hidden.get(3))).containsEntry("id", "tfp_m3_surv_06_hide_armor").containsEntry("files", 3)
            .containsEntry("sprites", List.of("gui/sprites/hud/armor_empty.png", "gui/sprites/hud/armor_full.png",
                "gui/sprites/hud/armor_half.png"));
        for (String sprite : List.of("armor_empty.png", "armor_full.png", "armor_half.png")) {
            assertThat(out.contains("craftengine/tfp/resourcepack/assets/minecraft/textures/gui/sprites/hud/" + sprite))
                .as(sprite + " hidden in the CraftEngine pack").isTrue();
        }
    }

    @Test
    void placeholderListNamesTheSnapshotPlaceholdersOfTheCatalogue() {
        OutputTree out = new OutputTree();
        HudLayouts.run(out, HudLayouts.catalogue());

        List<?> placeholders = (List<?>) yaml(out, HudLayouts.PLACEHOLDERS).get("placeholders");
        Set<Object> names = new java.util.HashSet<>();
        placeholders.forEach(p -> names.add(map(p).get("name")));
        assertThat(names).containsExactlyInAnyOrder("tfp_health", "tfp_max_health", "tfp_thirst", "tfp_hunger",
            "tfp_held_temp", "tfp_date", "tfp_season", "tfp_ambient_temp", "tfp_air", "tfp_armor");
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

    /**
     * Vanilla: XP bar from H-29, hotbar from H-22 (centre-91..+90), XP number within 9 px of the centre. The vanilla
     * armour row (H-49..H-41, left) is hidden and replaced by the armour bar, so it is no longer a limit. Checked in GUI
     * pixels, which are the same at GUI scale 2 and 3 (BetterHud scales the whole layout).
     */
    @Test
    void barsStayClearOfTheXpRowHotbarAndXpNumber() {
        for (var e : HudLayouts.BAR_SPECS.entrySet()) {
            HudLayouts.BarSpec spec = e.getValue();
            if (!spec.hud().equals(HudLayouts.BARS)) {
                continue;
            }
            HudArt.Bar art = HudArt.bar(spec.art());
            int top = spec.y();
            int bottom = spec.y() + art.height(); // exclusive
            int left = spec.x();
            int right = spec.x() + art.width();
            assertThat(bottom).as(e.getKey() + " bottom").isLessThanOrEqualTo(-30);
            int screenLeft = left + HudLayouts.SCREEN_SHIFT_X;
            int screenRight = right + HudLayouts.SCREEN_SHIFT_X; // exclusive
            assertThat(screenLeft).as(e.getKey() + " within the hotbar width").isGreaterThanOrEqualTo(-91);
            assertThat(screenRight).as(e.getKey() + " within the hotbar width").isLessThanOrEqualTo(91);
            assertThat(screenRight <= -9 || screenLeft >= 9).as(e.getKey() + " clear of the XP number").isTrue();
            assertThat(top).as(e.getKey() + " top").isGreaterThanOrEqualTo(-60);
        }
    }

    /** No two bars of the bars HUD share a GUI pixel (the armour bar never touches health, air never thirst). */
    @Test
    void noTwoBarsOverlap() {
        List<Map.Entry<String, HudLayouts.BarSpec>> bars = HudLayouts.BAR_SPECS.entrySet().stream()
            .filter(e -> e.getValue().hud().equals(HudLayouts.BARS)).toList();
        for (var a : bars) {
            for (var b : bars) {
                if (a.getKey().compareTo(b.getKey()) >= 0) {
                    continue;
                }
                HudArt.Bar aa = HudArt.bar(a.getValue().art());
                HudArt.Bar bb = HudArt.bar(b.getValue().art());
                HudLayouts.BarSpec sa = a.getValue();
                HudLayouts.BarSpec sb = b.getValue();
                boolean apartX = sa.x() + aa.width() <= sb.x() || sb.x() + bb.width() <= sa.x();
                boolean apartY = sa.y() + aa.height() <= sb.y() || sb.y() + bb.height() <= sa.y();
                assertThat(apartX || apartY).as(a.getKey() + " overlaps " + b.getKey()).isTrue();
            }
        }
    }

    /**
     * Owner request 2026-10-22: the armour bar is thin like thirst and hunger (81x7, two layers), directly above health,
     * the same x, one empty row between (vanilla's armour icons touched the health frame); shown only while the player
     * has armour, with a listener on {@code tfp_armor}.
     */
    @Test
    void leftColumnIsArmourAboveHealthWithOneEmptyRow() {
        HudLayouts.BarSpec health = HudLayouts.BAR_SPECS.get("m3-surv-06-health-bar");
        HudLayouts.BarSpec armor = HudLayouts.BAR_SPECS.get("m3-surv-06-armor-bar");
        HudArt.Bar art = HudArt.bar(armor.art());
        assertThat(art.width()).isEqualTo(81);
        assertThat(art.height()).isEqualTo(7);
        assertThat(art.width()).isEqualTo(HudArt.bar(health.art()).width());
        assertThat(armor.x()).isEqualTo(health.x());
        assertThat(health.y() - (armor.y() + art.height())).as("empty rows between armour and health").isEqualTo(1);

        OutputTree out = new OutputTree();
        HudLayouts.run(out, HudLayouts.catalogue());
        Map<String, Object> layout = map(yaml(out, HudLayouts.BAR_LAYOUTS).get("tfp_m3_surv_06_armor_bar"));
        assertThat(map(map(layout.get("conditions")).get("1")))
            .containsEntry("first", "tfp_armor").containsEntry("second", 0).containsEntry("operation", ">");
        Map<String, Object> images = yaml(out, HudLayouts.IMAGES);
        assertThat(text(images, "tfp_m3_surv_06_armor_bar_fill")).contains("class=tfp_armor");
        assertThat(layout).doesNotContainKey("texts");
    }

    /**
     * PAR-HUD-05, armour worn. Vanilla (1.21.11 {@code Gui.renderPlayerHealth}) draws the armour row at H-49..H-41 on the
     * left whenever armour is above 0; with one heart row (max health 20, TFC's 50 is still 10 hearts of the vanilla
     * attribute) it sits right on top of the health bar (H-40..H-31): the rows touch. So with armour worn: the vanilla
     * armour sprites are transparent, the armour bar is the only thing drawn above health, it shares no row with the
     * health frame or its text, and the health text (glyphs and shadow) stays inside the health interior. The right
     * column with a living mount: vanilla draws the vehicle hearts from H-39 upwards in place of the food row (up to
     * three rows of ten, H-59 at most) and the horse jump bar on the XP row; the vehicle hearts are transparent, and no
     * bar reaches the XP row. The XP number (centred, H-35..H-28) is cleared for levels up to three digits (17 px).
     */
    @Test
    void armourWornNeitherTouchesHealthNorItsText() {
        HudLayouts.BarSpec health = HudLayouts.BAR_SPECS.get("m3-surv-06-health-bar");
        HudLayouts.BarSpec armor = HudLayouts.BAR_SPECS.get("m3-surv-06-armor-bar");
        HudArt.Bar healthArt = HudArt.bar(health.art());
        HudArt.Bar armorArt = HudArt.bar(armor.art());
        int vanillaArmorTop = -49;
        int vanillaArmorBottom = -41; // inclusive
        int healthTop = health.y();
        int healthBottom = health.y() + healthArt.height() - 1;
        assertThat(healthTop).as("health frame top row").isEqualTo(-40);
        assertThat(healthBottom).as("health frame bottom row").isEqualTo(-31);
        assertThat(vanillaArmorBottom + 1).as("vanilla's armour row touches the health frame, so it must be hidden")
            .isEqualTo(healthTop);

        OutputTree out = new OutputTree();
        HudLayouts.run(out, HudLayouts.catalogue());
        for (String sprite : List.of("armor_empty.png", "armor_full.png", "armor_half.png", "heart/vehicle_full.png",
            "heart/vehicle_half.png", "heart/vehicle_container.png")) {
            assertThat(out.contains("craftengine/tfp/resourcepack/assets/minecraft/textures/gui/sprites/hud/" + sprite))
                .as(sprite + " transparent").isTrue();
        }

        // The armour bar (shown only with armour) replaces the vanilla row inside its rows, one empty row above health.
        int armorTop = armor.y();
        int armorBottom = armor.y() + armorArt.height() - 1;
        assertThat(armorTop).isGreaterThanOrEqualTo(vanillaArmorTop);
        assertThat(armorBottom).isLessThan(vanillaArmorBottom);
        assertThat(healthTop - armorBottom).as("rows from the armour bar to the health frame").isEqualTo(2);

        Map<String, Object> bar = map(yaml(out, HudLayouts.BAR_LAYOUTS).get("tfp_m3_surv_06_health_bar"));
        Map<String, Object> text = map(map(bar.get("texts")).get("1"));
        int textTop = (Integer) text.get("y");
        int textBottom = textTop + 7; // 7 glyph rows at scale 0.5, then the shadow row
        assertThat(textTop).as("health text below the armour bar").isGreaterThan(armorBottom + 1);
        assertThat(textTop).isGreaterThanOrEqualTo(health.y() + healthArt.interior().get(0).y0());
        assertThat(textBottom).isLessThan(health.y() + healthArt.interior().get(0).y1());

        // Right column and the XP row: nothing at or below H-30, nothing within 9 px of the centre.
        for (String right : List.of("m3-surv-06-hunger-bar", "m3-surv-06-thirst-bar", "m3-surv-06-air-bar")) {
            HudLayouts.BarSpec r = HudLayouts.BAR_SPECS.get(right);
            assertThat(r.y() + HudArt.bar(r.art()).height()).as(right + " clear of the jump bar").isLessThanOrEqualTo(-30);
            assertThat(r.x() + HudLayouts.SCREEN_SHIFT_X).as(right + " clear of a 3-digit XP number").isGreaterThanOrEqualTo(9);
        }
        assertThat(armor.x() + HudLayouts.SCREEN_SHIFT_X + armorArt.width()).isLessThanOrEqualTo(-9);
    }

    /**
     * On screen (layout x + {@link HudLayouts#SCREEN_SHIFT_X}) the left column is vanilla's hearts (centre-91..-11) and
     * the right column vanilla's food row (centre+10..+90): mirror images about the hotbar's centre line at -0.5.
     */
    @Test
    void columnsMirrorEachOtherOnScreen() {
        for (String left : List.of("m3-surv-06-health-bar", "m3-surv-06-armor-bar")) {
            HudLayouts.BarSpec l = HudLayouts.BAR_SPECS.get(left);
            int w = HudArt.bar(l.art()).width();
            int first = l.x() + HudLayouts.SCREEN_SHIFT_X;
            assertThat(first).as(left).isEqualTo(-91);
            assertThat(first + w - 1).as(left).isEqualTo(-11);
            for (String right : List.of("m3-surv-06-hunger-bar", "m3-surv-06-thirst-bar", "m3-surv-06-air-bar")) {
                HudLayouts.BarSpec r = HudLayouts.BAR_SPECS.get(right);
                int rFirst = r.x() + HudLayouts.SCREEN_SHIFT_X;
                int rLast = rFirst + HudArt.bar(r.art()).width() - 1;
                assertThat(rFirst).as(right + " mirrors " + left).isEqualTo(-1 - (first + w - 1));
                assertThat(rLast).as(right + " mirrors " + left).isEqualTo(-1 - first);
            }
        }
    }

    /** Pixel-perfect layers: same x, and each layer's y compensates BetterHud's trim of transparent rows. */
    @Test
    void layersShareTheCanvasOriginInTheEmittedLayouts() {
        OutputTree out = new OutputTree();
        HudLayouts.run(out, HudLayouts.catalogue());
        Map<String, Object> layouts = new java.util.LinkedHashMap<>(yaml(out, HudLayouts.BAR_LAYOUTS));
        layouts.putAll(yaml(out, HudLayouts.INFO_LAYOUTS));
        Map<String, Object> images = yaml(out, HudLayouts.IMAGES);

        for (var e : HudLayouts.BAR_SPECS.entrySet()) {
            HudLayouts.BarSpec spec = e.getValue();
            String name = HudLayouts.name(e.getKey());
            List<Map<String, Object>> entries = new java.util.ArrayList<>();
            map(map(layouts.get(name)).get("images")).values().forEach(v -> entries.add(map(v)));
            assertThat(map(layouts.get(name))).containsEntry("offset", "left");
            HudArt.Bar art = HudArt.bar(spec.art());
            assertThat(entries).hasSize(2 + art.variants().size());
            for (Map<String, Object> img : entries) {
                String image = (String) img.get("name");
                String file = image.substring("tfp_".length()) + ".png";
                assertThat(img.get("x")).as(image).isEqualTo(spec.x());
                assertThat(img.get("y")).as(image).isEqualTo(spec.y() + HudArt.firstOpaqueRow(file));
                assertThat(img.get("layer")).as(image).isEqualTo(file.endsWith("_bg.png") ? HudLayouts.LAYER_BG : HudLayouts.LAYER_FILL);
                if (!file.endsWith("_bg.png")) {
                    Map<String, Object> def = map(images.get(image));
                    assertThat(def).containsEntry("type", "listener").containsEntry("split-type", "left");
                    int split = spec.split() > 0 ? spec.split() : art.interiorWidth();
                    assertThat(def).containsEntry("split", split);
                    assertThat(art.interiorWidth() % split).as(image + " frames end on whole pixels").isZero();
                }
            }
        }
    }

    @Test
    void healthTextSitsInsideTheInteriorWithTfcShadow() {
        OutputTree out = new OutputTree();
        HudLayouts.run(out, HudLayouts.catalogue());

        Map<String, Object> bar = map(yaml(out, HudLayouts.BAR_LAYOUTS).get("tfp_m3_surv_06_health_bar"));
        Map<String, Object> text = map(map(bar.get("texts")).get("1"));
        HudLayouts.BarSpec spec = HudLayouts.BAR_SPECS.get("m3-surv-06-health-bar");
        HudArt.Bar art = HudArt.bar(spec.art());
        int glyphs = 7; // the vanilla font at scale 0.5
        int interiorTop = spec.y() + art.interior().get(0).y0();
        int interiorBottom = spec.y() + art.interior().get(0).y1(); // exclusive
        assertThat((Integer) text.get("y")).isGreaterThanOrEqualTo(interiorTop);
        assertThat((Integer) text.get("y") + glyphs + 1).as("glyphs and shadow").isLessThanOrEqualTo(interiorBottom);
        assertThat(text).containsEntry("outline", "FF000000").containsEntry("align", "center")
            .containsEntry("x", spec.x() + art.width() / 2).containsEntry("layer", HudLayouts.LAYER_TEXT);
    }

    @Test
    void airBarSitsAboveThirstOnlyBelowFullAirWithARedFillWhenCritical() {
        OutputTree out = new OutputTree();
        HudLayouts.run(out, HudLayouts.catalogue());
        Map<String, Object> layouts = yaml(out, HudLayouts.BAR_LAYOUTS);

        Map<String, Object> air = map(layouts.get("tfp_m3_surv_06_air_bar"));
        assertThat(map(map(air.get("conditions")).get("1")))
            .containsEntry("first", "tfp_air").containsEntry("second", 300).containsEntry("operation", "<");
        Map<String, Object> images = map(air.get("images"));
        assertThat(map(images.get("2"))).containsEntry("name", "tfp_m3_surv_06_air_bar_fill");
        assertThat(map(map(map(images.get("2")).get("conditions")).get("1"))).containsEntry("operation", ">=").containsEntry("second", 60);
        assertThat(map(images.get("3"))).containsEntry("name", "tfp_m3_surv_06_air_bar_fill_critical");
        assertThat(map(map(map(images.get("3")).get("conditions")).get("1"))).containsEntry("operation", "<").containsEntry("second", 60);
        for (String bar : List.of("tfp_m3_surv_06_health_bar", "tfp_m3_surv_06_hunger_bar", "tfp_m3_surv_06_thirst_bar")) {
            assertThat(map(layouts.get(bar))).as(bar).doesNotContainKey("conditions");
        }
        // right column: same x and size, one empty row between bars, air on top
        HudLayouts.BarSpec hunger = HudLayouts.BAR_SPECS.get("m3-surv-06-hunger-bar");
        HudLayouts.BarSpec thirst = HudLayouts.BAR_SPECS.get("m3-surv-06-thirst-bar");
        HudLayouts.BarSpec airSpec = HudLayouts.BAR_SPECS.get("m3-surv-06-air-bar");
        int h = HudArt.bar(hunger.art()).height();
        for (HudLayouts.BarSpec b : List.of(thirst, airSpec)) {
            assertThat(b.x()).isEqualTo(hunger.x());
            assertThat(HudArt.bar(b.art()).width()).isEqualTo(HudArt.bar(hunger.art()).width());
            assertThat(HudArt.bar(b.art()).height()).isEqualTo(h);
        }
        assertThat(hunger.y() - thirst.y()).isEqualTo(h + 1);
        assertThat(thirst.y() - airSpec.y()).isEqualTo(h + 1);
    }

    /**
     * The red air threshold is a BetterHud condition on the {@code tfp_air} value (ticks of breath), not a pixel
     * quantity: there is one air layout, placed once in one HUD, with no per-GUI-scale copy, so GUI scale 2 and 3 switch
     * to the red fill at exactly the same air value. Pinned on the datagen output and, when a previous
     * {@code generateContent} left it (it is git-ignored), on the generated pack the test servers install.
     */
    @Test
    void airRedThresholdIsOneScaleIndependentConditionAt60() throws IOException {
        assertThat(HudLayouts.CRITICAL_AIR).isEqualTo(60);
        OutputTree out = new OutputTree();
        HudLayouts.run(out, HudLayouts.catalogue());
        Path generated = HudLayouts.catalogue().getParent().getParent().getParent()
            .resolve("content/generated/content/" + HudLayouts.BAR_LAYOUTS);
        List<Map<String, Object>> sources = new java.util.ArrayList<>(List.of(yaml(out, HudLayouts.BAR_LAYOUTS)));
        if (Files.isRegularFile(generated)) {
            sources.add(map(new org.yaml.snakeyaml.Yaml().load(Files.readString(generated))));
        }
        for (Map<String, Object> layouts : sources) {
            List<String> airLayouts = layouts.keySet().stream().filter(k -> k.contains("air")).toList();
            assertThat(airLayouts).as("one air layout, no per-scale copy").containsExactly("tfp_m3_surv_06_air_bar");
            Map<String, Object> images = map(map(layouts.get("tfp_m3_surv_06_air_bar")).get("images"));
            List<String> thresholds = new java.util.ArrayList<>();
            for (Object image : images.values()) {
                Object conds = map(image).get("conditions");
                if (conds != null) {
                    for (Object c : map(conds).values()) {
                        assertThat(map(c)).containsEntry("first", "tfp_air");
                        thresholds.add(map(c).get("operation") + " " + map(c).get("second"));
                    }
                }
            }
            assertThat(thresholds).containsExactlyInAnyOrder(">= 60", "< 60");
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
    void artInThePackIsTheResourceBytes() {
        OutputTree out = new OutputTree();
        HudLayouts.run(out, HudLayouts.catalogue());

        Map<String, Object> images = yaml(out, HudLayouts.IMAGES);
        for (Object def : images.values()) {
            String path = (String) map(def).get("file");
            String file = path.substring(path.lastIndexOf('/') + 1);
            byte[] expected = file.endsWith("_mirrored.png")
                ? HudArt.mirrored(file.replace("_mirrored.png", ".png")) : HudArt.bytes(file);
            assertThat(out.get(BetterHudPack.DIR + "assets/" + path)).as(path).isEqualTo(expected);
        }
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
        Path file = write(replaceOnce(real(), "| bar | 6 | 0 |", "| bar | 7 | 0 |"));
        assertThatThrownBy(() -> HudLayouts.run(new OutputTree(), file))
            .isInstanceOf(DatagenException.class).hasMessageContaining("kind bar has 6 rows");
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
