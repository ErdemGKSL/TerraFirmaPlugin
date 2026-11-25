/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.forge;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.gui.ChestGeometry;
import dev.tfp.core.gui.GuiImage;
import dev.tfp.core.gui.TitleGlyphs;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** PAR-METAL-05: the forging screen's slots, images and overlay frames. */
class AnvilScreenLayoutTest {
    private static final ChestGeometry CHEST = AnvilScreenLayout.CHEST;

    @Test
    void everySlotAndButtonIsOnItsOwnChestSlotAtTfcsPositionMoved() {
        Set<Integer> used = new HashSet<>(List.of(AnvilScreenLayout.SECOND, AnvilScreenLayout.CATALYST, AnvilScreenLayout.WELD,
            AnvilScreenLayout.MAIN, AnvilScreenLayout.PLAN, AnvilScreenLayout.HAMMER));
        assertThat(used).hasSize(6);
        for (ForgeStep step : ForgeStep.VALUES) {
            int slot = AnvilScreenLayout.stepSlot(step);
            assertThat(used.add(slot)).as(step.name()).isTrue();
            assertThat(AnvilScreenLayout.stepAt(slot)).isEqualTo(step);
            assertThat(CHEST.slotX(slot)).isEqualTo(step.buttonX() + AnvilScreenLayout.CENTRE_DX);
            assertThat(CHEST.slotY(slot)).isEqualTo(step.buttonY() + AnvilScreenLayout.CENTRE_DY);
        }
        // TFC's container slots: main (22, 76), second (22, 20), hammer (138, 76), catalyst (22, 38)
        int[][] want = {{4, -4}, {4, -2}, {14, -4}, {4, -2}};
        for (int i = 0; i < 4; i++) {
            int slot = AnvilScreenLayout.CHEST_SLOTS[i];
            assertThat(CHEST.slotX(slot) - AnvilScreenLayout.TFC_SLOTS[i][0]).isEqualTo(want[i][0]);
            assertThat(CHEST.slotY(slot) - AnvilScreenLayout.TFC_SLOTS[i][1]).isEqualTo(want[i][1]);
        }
        // TFC's plan button (137, 56) and weld button (21, 56) frames sit on the chest slots they replace
        assertThat(CHEST.slotX(AnvilScreenLayout.PLAN) - 1).isEqualTo(AnvilScreenLayout.PLAN_BUTTON.x());
        assertThat(CHEST.slotY(AnvilScreenLayout.WELD) - 1).isEqualTo(AnvilScreenLayout.WELD_BUTTON.y());
        assertThat(AnvilScreenLayout.stepAt(AnvilScreenLayout.MAIN)).isNull();
    }

    @Test
    void theInventoryMovesUpByFourOntoTheFiveRowChest() {
        assertThat(CHEST.height()).isEqualTo(204);
        assertThat(AnvilScreenLayout.PLAYER_DY).isEqualTo(-4);
        assertThat(CHEST.hotbarY() - (142 + 41)).isEqualTo(-4);
    }

    @Test
    void imagesStayInsideTheirCellsAndTheBarKeepsTfcsPositions() {
        for (GuiImage image : AnvilScreenLayout.LAYERS.images()) {
            assertThat(image.width()).isLessThanOrEqualTo(GuiImage.MAX_CELL);
            assertThat(image.x() + image.width()).isLessThanOrEqualTo(ChestGeometry.WIDTH);
            assertThat(image.y() + image.height()).isLessThanOrEqualTo(CHEST.height());
        }
        assertThat(AnvilScreenLayout.PROGRESS.y()).isEqualTo(104);
        assertThat(AnvilScreenLayout.POINTER.y()).isEqualTo(98);
        assertThat(AnvilScreenLayout.PROGRESS.frames()).isGreaterThanOrEqualTo(151);
        assertThat(AnvilScreenLayout.ruleImage(0).frames()).isGreaterThanOrEqualTo(61);
        assertThat(AnvilScreenLayout.ruleImage(2).x()).isEqualTo(59 + 2 * 19 + 9);
        assertThat(AnvilScreenLayout.stepImage(0).x()).isEqualTo(99 + 9);
        assertThat(AnvilScreenLayout.stepImage(2).x()).isEqualTo(99 - 2 * 19 + 9);
    }

    @Test
    void framesWithoutAPlanShowOnlyTheStepsAndTheButtons() {
        Map<String, Integer> f = AnvilScreenLayout.frames(false, 0, 0, 0, List.of(), ForgeSteps.EMPTY, false, true);
        assertThat(f).containsOnlyKeys(AnvilScreenLayout.KEY_WELD, AnvilScreenLayout.KEY_PLAN);
        assertThat(f.get(AnvilScreenLayout.KEY_PLAN)).isEqualTo(AnvilScreenLayout.PLAN_WORKABLE);
        assertThat(AnvilScreenLayout.frames(false, 0, 0, 0, List.of(), ForgeSteps.EMPTY, false, false).get(AnvilScreenLayout.KEY_PLAN))
            .isEqualTo(AnvilScreenLayout.PLAN_UNWORKABLE);
    }

    @Test
    void framesOfAPlanPutTheMarkersAtTfcsOffsets() {
        // pickaxe-like rules: HIT_LAST, DRAW_NOT_LAST, PUNCH_ANY; history PUNCH, DRAW, HIT_HARD (latest last)
        ForgeSteps steps = ForgeSteps.EMPTY.withStep(ForgeStep.PUNCH).withStep(ForgeStep.DRAW).withStep(ForgeStep.HIT_HARD);
        List<ForgeRule> rules = List.of(ForgeRule.HIT_LAST, ForgeRule.DRAW_NOT_LAST, ForgeRule.PUNCH_ANY);
        Map<String, Integer> f = AnvilScreenLayout.frames(true, 61, 74, 0, rules, steps, true, true);
        assertThat(f.get(AnvilScreenLayout.KEY_PROGRESS)).isEqualTo(61);
        assertThat(f.get(AnvilScreenLayout.KEY_TARGET)).isEqualTo(74);
        assertThat(f).doesNotContainKeys(AnvilScreenLayout.KEY_LEFT, AnvilScreenLayout.KEY_RIGHT, AnvilScreenLayout.KEY_CENTRE);
        // all three rules match the history, so each is the matched (odd) frame of its rule
        for (int i = 0; i < 3; i++) {
            assertThat(f.get(AnvilScreenLayout.ruleKey(i))).isEqualTo(AnvilScreenLayout.ruleFrame(rules.get(i), true));
        }
        // the latest step is on image 0
        assertThat(f.get(AnvilScreenLayout.stepKey(0))).isEqualTo(AnvilScreenLayout.stepFrame(ForgeStep.HIT_HARD));
        assertThat(f.get(AnvilScreenLayout.stepKey(2))).isEqualTo(AnvilScreenLayout.stepFrame(ForgeStep.PUNCH));
        assertThat(f.get(AnvilScreenLayout.KEY_WELD)).isEqualTo(1);
        assertThat(f.get(AnvilScreenLayout.KEY_PLAN)).isEqualTo(AnvilScreenLayout.PLAN_RECIPE);
    }

    @Test
    void anUnmatchedRuleShowsItsRedFrame() {
        ForgeSteps steps = ForgeSteps.EMPTY.withStep(ForgeStep.BEND);
        Map<String, Integer> f = AnvilScreenLayout.frames(true, 7, 20, 0, List.of(ForgeRule.HIT_LAST), steps, false, false);
        assertThat(f.get(AnvilScreenLayout.ruleKey(0))).isEqualTo(AnvilScreenLayout.ruleFrame(ForgeRule.HIT_LAST, false));
        assertThat(AnvilScreenLayout.ruleFrame(ForgeRule.HIT_LAST, true)).isEqualTo(AnvilScreenLayout.ruleFrame(ForgeRule.HIT_LAST, false) + 1);
    }

    @Test
    void aRangeOfTwoOrMoreShowsTfcsBracket() {
        // target 74, range 3: left 71, right 77, centre 74 (range > 2)
        Map<String, Integer> f = AnvilScreenLayout.frames(true, 0, 74, 3, List.of(), ForgeSteps.EMPTY, false, false);
        assertThat(f).doesNotContainKey(AnvilScreenLayout.KEY_TARGET);
        assertThat(f.get(AnvilScreenLayout.KEY_LEFT)).isEqualTo(71);
        assertThat(f.get(AnvilScreenLayout.KEY_RIGHT)).isEqualTo(77);
        assertThat(f.get(AnvilScreenLayout.KEY_CENTRE)).isEqualTo(74);
        // range 2: no centre; the limits clamp to [0, 145]
        Map<String, Integer> g = AnvilScreenLayout.frames(true, 0, 1, 2, List.of(), ForgeSteps.EMPTY, false, false);
        assertThat(g.get(AnvilScreenLayout.KEY_LEFT)).isZero();
        assertThat(g).doesNotContainKey(AnvilScreenLayout.KEY_CENTRE);
        assertThat(AnvilScreenLayout.frames(true, 0, 140, 9, List.of(), ForgeSteps.EMPTY, false, false).get(AnvilScreenLayout.KEY_RIGHT))
            .isEqualTo(145);
    }

    @Test
    void theTitleOfAFullStateIsOneGlyphPerLayerAndEndsAtTheLabel() {
        ForgeSteps steps = ForgeSteps.EMPTY.withStep(ForgeStep.UPSET);
        Map<String, Integer> f = AnvilScreenLayout.frames(true, 13, 74, 0, List.of(ForgeRule.UPSET_LAST), steps, false, false);
        TitleGlyphs g = AnvilScreenLayout.LAYERS.compose(f);
        assertThat(g.pen()).isEqualTo(ChestGeometry.TITLE_X);
        long glyphs = g.tokens().stream().filter(t -> t instanceof TitleGlyphs.Glyph).count();
        assertThat(glyphs).isEqualTo(1 /* background */ + f.size());
    }
}
