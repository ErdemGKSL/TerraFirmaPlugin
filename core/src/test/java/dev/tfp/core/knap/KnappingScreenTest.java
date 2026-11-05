/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.knap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.tfp.core.gui.GuiImage;
import dev.tfp.core.gui.TitleGlyphs;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** PAR-PROC-01: the knapping screen layout in a 6-row chest. */
class KnappingScreenTest {
    @Test
    void cellsAreChestSlotsInColumnsOneToFiveRowsZeroToFour() {
        Set<Integer> slots = new HashSet<>();
        for (int i = 0; i < 25; i++) {
            int slot = KnappingScreen.cellSlot(i);
            assertThat(slots.add(slot)).isTrue();
            assertThat(slot % 9).isBetween(1, 5);
            assertThat(slot / 9).isBetween(0, 4);
            assertThat(KnappingScreen.cellAt(slot)).isEqualTo(i);
        }
        assertThat(KnappingScreen.cellSlot(KnappingRules.buttonIndex(0, 0))).isEqualTo(1);
        assertThat(KnappingScreen.cellSlot(KnappingRules.buttonIndex(4, 4))).isEqualTo(41);
        assertThat(KnappingScreen.OUTPUT_SLOT).isEqualTo(26);
        assertThat(KnappingScreen.cellAt(KnappingScreen.OUTPUT_SLOT)).isEqualTo(-1);
        assertThat(KnappingScreen.cellAt(0)).isEqualTo(-1);
        assertThat(KnappingScreen.cellAt(45 + 1)).isEqualTo(-1);
    }

    @Test
    void theArtMovesWithTheOutputSlot() {
        // TFC's output at (128, 46); the chest's output slot at (152, 54)
        assertThat(KnappingScreen.ART_DX).isEqualTo(24);
        assertThat(KnappingScreen.ART_DY).isEqualTo(8);
        // TFC's inventory at 84 + 20; the 6-row chest's at 139
        assertThat(KnappingScreen.PLAYER_DY).isEqualTo(35);
    }

    @Test
    void cellImagesSitOnTheirSlotsAndShareOneImagePerRow() {
        GuiImage a = KnappingScreen.cellImage("rock/loose/granite", 0, 2);
        GuiImage b = KnappingScreen.cellImage("rock/loose/granite", 4, 2);
        assertThat(a.id()).isEqualTo(b.id()).isEqualTo("tfp:gui/knapping/rock/loose/granite_r2");
        assertThat(a.x()).isEqualTo(26);
        assertThat(b.x()).isEqualTo(26 + 4 * 18);
        assertThat(a.y()).isEqualTo(54);
        assertThat(a.ascent()).isEqualTo(b.ascent()).isEqualTo(13 - 54);
        assertThat(KnappingScreen.rowImages()).hasSize(KnappingScreen.TEXTURES.size() * 5);
    }

    @Test
    void theTextureIsTheItemPathOrTheFallback() {
        assertThat(KnappingScreen.texture("tfp:rock/loose/granite", "x")).isEqualTo("rock/loose/granite");
        assertThat(KnappingScreen.texture("tfp:rock/mossy_loose/chert", "x")).isEqualTo("rock/mossy_loose/chert");
        assertThat(KnappingScreen.texture("minecraft:clay_ball", "x")).isEqualTo("clay_ball");
        assertThat(KnappingScreen.texture("minecraft:leather", "x")).isEqualTo("leather");
        assertThat(KnappingScreen.texture("addon:pebble", "leather")).isEqualTo("leather");
        assertThatThrownBy(() -> KnappingScreen.layers("nope")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void framesShowOnCellsAndTheDisabledTextureOnlyWhenTheTypeAndTextureHaveOne() {
        KnappingPattern grid = new KnappingPattern();
        grid.click(KnappingRules.buttonIndex(2, 0));
        Map<String, Integer> clay = KnappingScreen.frames(grid, "clay_ball", true);
        assertThat(clay).hasSize(25).containsEntry("cell0", 0).containsEntry("cell2", 1);
        Map<String, Integer> rock = KnappingScreen.frames(grid, "rock/loose/granite", false);
        assertThat(rock).containsEntry("cell0", 0).containsEntry("cell2", -1);
        assertThat(KnappingScreen.frames(grid, "leather", false)).containsEntry("cell2", -1);
        // the composed title draws the background and the 24 on cells
        TitleGlyphs g = KnappingScreen.layers("rock/loose/granite").compose(rock);
        long glyphs = g.tokens().stream().filter(t -> t instanceof TitleGlyphs.Glyph).count();
        assertThat(glyphs).isEqualTo(25);
    }

    @Test
    void openingTypeAndResultFromTheFixture() {
        KnappingFixture.Data data = KnappingFixture.load();
        KnappingDefinitions defs = new KnappingDefinitions(data.types(), data.recipes());
        KnappingRecipe any = data.recipes().getFirst();
        assertThat(defs.resultTemplate(any).id()).isEqualTo(any.result().item());
        assertThat(defs.resultTemplate(any).count()).isEqualTo(any.result().count());
        Optional<KnappingRecipe> horn = data.recipes().stream().filter(r -> !r.result().components().isEmpty()).findFirst();
        horn.ifPresent(r -> assertThat(defs.resultTemplate(r).components()).isPresent());
    }

    @Test
    void stillValidFollowsTfc() {
        KnappingFixture.Data data = KnappingFixture.load();
        KnappingType rock = data.types().stream().filter(t -> !t.consumeAfterComplete()).findFirst().orElseThrow();
        KnappingType clay = data.types().stream().filter(KnappingType::consumeAfterComplete).findFirst().orElseThrow();
        KnappingSession r = new KnappingSession(rock, false);
        assertThat(r.stillValid(false)).isFalse();
        r.click(0);
        assertThat(r.modified()).isTrue();
        assertThat(r.stillValid(false)).isTrue();
        KnappingSession c = new KnappingSession(clay, false);
        c.click(0);
        assertThat(c.stillValid(false)).isFalse();
        assertThat(c.stillValid(true)).isTrue();
    }
}
