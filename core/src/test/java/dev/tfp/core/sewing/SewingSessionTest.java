/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.sewing;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.craft.CraftRecipes;
import dev.tfp.core.craft.SewingRecipe;
import dev.tfp.core.craft.SewingRules;
import dev.tfp.core.sewing.SewingSession.ItemKind;
import dev.tfp.core.sewing.SewingSession.Mode;
import dev.tfp.core.sewing.SewingSession.Outcome;
import dev.tfp.core.sewing.SewingSession.View;
import org.junit.jupiter.api.Test;

/** PAR-PROC-07: the sewing session on TFC's real recipes: sewing a recipe gives the result and consumes by TFC's rule. */
class SewingSessionTest {
    private static final SewingRules.Supply PLENTY = new SewingRules.Supply(4, 4, 4, true);
    private static final SewingRecipe GLOBE = CraftRecipes.SEWING.stream()
        .filter(r -> r.id().equals("sewing/globe_banner_pattern")).findFirst().orElseThrow();

    private static SewingSession fresh() {
        return new SewingSession(CraftRecipes.SEWING);
    }

    /** Sews {@code recipe} with the real clicks: squares in the squares view, stitches in the stitches view. */
    private static void sew(SewingSession s, SewingRecipe recipe, SewingRules.Supply supply) {
        for (char material : new char[] {'B', 'W'}) {
            s.select(material == 'B' ? Mode.BURLAP : Mode.WOOL);
            for (int i = 0; i < SewingRecipe.SQUARES; i++) {
                if (recipe.squares().charAt(i) == material) {
                    assertThat(s.press(SewingScreen.squareSlot(i), supply)).isNotEqualTo(Outcome.NONE);
                }
            }
        }
        s.select(Mode.NEEDLE);
        for (int i = 0; i < SewingRecipe.STITCHES; i++) {
            if (recipe.stitches().charAt(i) == '#') {
                assertThat(s.press(SewingScreen.stitchSlot(i), supply)).isEqualTo(Outcome.STITCHED);
            }
        }
    }

    @Test
    void sewingEveryRecipeGivesItsResult() {
        for (SewingRecipe recipe : CraftRecipes.SEWING) {
            SewingSession s = fresh();
            sew(s, recipe, PLENTY);
            assertThat(s.update()).as(recipe.id()).hasValue(recipe);
        }
    }

    @Test
    void anIncompleteBoardMatchesNothing() {
        SewingSession s = fresh();
        s.select(Mode.BURLAP);
        s.press(0, PLENTY);
        assertThat(s.update()).isEmpty();
    }

    @Test
    void theMaterialButtonsPickTheView() {
        SewingSession s = fresh();
        assertThat(s.view()).isEqualTo(View.SQUARES);
        s.select(Mode.NEEDLE);
        assertThat(s.view()).isEqualTo(View.STITCHES);
        s.select(Mode.WOOL);
        assertThat(s.view()).isEqualTo(View.SQUARES);
        s.select(Mode.REMOVE);
        assertThat(s.view()).isEqualTo(View.SQUARES);
        s.select(Mode.REMOVE);
        assertThat(s.view()).isEqualTo(View.STITCHES);
        s.select(Mode.REMOVE);
        assertThat(s.view()).isEqualTo(View.SQUARES);
    }

    @Test
    void nothingIsPlacedWithoutAMaterial() {
        SewingSession s = fresh();
        assertThat(s.press(0, PLENTY)).isEqualTo(Outcome.NONE);
        s.select(Mode.NEEDLE);
        s.select(Mode.NONE);
        assertThat(s.press(0, PLENTY)).isEqualTo(Outcome.NONE);
        assertThat(s.board()).isEqualTo(SewingRules.Board.EMPTY);
    }

    @Test
    void clothIsLimitedByTheItemsInTheInputSlots() {
        SewingSession s = fresh();
        SewingRules.Supply one = new SewingRules.Supply(1, 0, 0, true); // one wool item covers 8 squares
        s.select(Mode.WOOL);
        for (int i = 0; i < 8; i++) {
            assertThat(s.press(SewingScreen.squareSlot(i), one)).isEqualTo(Outcome.PLACED_WOOL);
        }
        assertThat(s.press(SewingScreen.squareSlot(8), one)).isEqualTo(Outcome.NONE);
        assertThat(s.board().usedWool()).isEqualTo(8);
        s.select(Mode.BURLAP);
        assertThat(s.press(SewingScreen.squareSlot(9), one)).isEqualTo(Outcome.NONE);
    }

    @Test
    void stitchesNeedANeedleAndYarn() {
        SewingSession s = fresh();
        s.select(Mode.NEEDLE);
        assertThat(s.press(0, new SewingRules.Supply(0, 0, 1, false))).isEqualTo(Outcome.NONE);
        assertThat(s.press(0, new SewingRules.Supply(0, 0, 0, true))).isEqualTo(Outcome.NONE);
        SewingRules.Supply yarn = new SewingRules.Supply(0, 0, 1, true); // 16 placements
        for (int i = 0; i < 16; i++) {
            assertThat(s.press(i, yarn)).isEqualTo(Outcome.STITCHED);
        }
        assertThat(s.press(16, yarn)).isEqualTo(Outcome.NONE);
    }

    @Test
    void removingAStitchSpendsAPlacementButOnlyWhenOneIsThere() {
        SewingSession s = fresh();
        s.select(Mode.NEEDLE);
        s.press(3, PLENTY);
        s.select(Mode.REMOVE);
        assertThat(s.view()).isEqualTo(View.STITCHES);
        assertThat(s.press(4, PLENTY)).isEqualTo(Outcome.NONE);
        assertThat(s.board().usedString()).isEqualTo(1);
        assertThat(s.press(3, PLENTY)).isEqualTo(Outcome.UNSTITCHED);
        assertThat(s.board().stitch(3)).isFalse();
        assertThat(s.board().usedString()).isEqualTo(2);
    }

    @Test
    void removingASquareRefundsNothing() {
        SewingSession s = fresh();
        s.select(Mode.BURLAP);
        s.press(0, PLENTY);
        s.select(Mode.REMOVE);
        assertThat(s.press(0, PLENTY)).isEqualTo(Outcome.REMOVED_SQUARE);
        assertThat(s.press(0, PLENTY)).isEqualTo(Outcome.NONE);
        assertThat(s.board().squares().charAt(0)).isEqualTo(' ');
        assertThat(s.board().usedBurlap()).isEqualTo(1);
    }

    @Test
    void takingTheResultConsumesByTfcsRoundingAndResetsTheBoard() {
        SewingSession s = fresh();
        sew(s, GLOBE, PLENTY);
        s.update();
        long wool = GLOBE.squares().chars().filter(c -> c == 'W').count();
        long burlap = GLOBE.squares().chars().filter(c -> c == 'B').count();
        long stitches = GLOBE.stitches().chars().filter(c -> c == '#').count();
        SewingRules.Consumption used = s.take();
        assertThat(used.wool()).isEqualTo((int) Math.ceil(wool / 8.0));
        assertThat(used.burlap()).isEqualTo((int) Math.ceil(burlap / 8.0));
        assertThat(used.yarn()).isEqualTo((int) Math.ceil(stitches / 16.0));
        assertThat(s.board()).isEqualTo(SewingRules.Board.EMPTY);
        assertThat(s.mode()).isEqualTo(Mode.NONE);
        assertThat(s.update()).isEmpty();
    }

    @Test
    void aNewResultClearsTheSelectedMaterial() {
        SewingSession s = fresh();
        sew(s, GLOBE, PLENTY);
        assertThat(s.mode()).isEqualTo(Mode.NEEDLE);
        s.update();
        assertThat(s.mode()).isEqualTo(Mode.NONE);
    }

    @Test
    void usedClothYarnAndNeedleStayInTheirSlots() {
        SewingSession s = fresh();
        for (ItemKind kind : ItemKind.values()) {
            assertThat(s.mayTake(kind)).isTrue();
        }
        s.select(Mode.WOOL);
        s.press(0, PLENTY);
        assertThat(s.mayTake(ItemKind.WOOL)).isFalse();
        assertThat(s.mayTake(ItemKind.BURLAP)).isTrue();
        assertThat(s.mayTake(ItemKind.STRING)).isTrue();
        s.select(Mode.NEEDLE);
        s.press(0, PLENTY);
        assertThat(s.mayTake(ItemKind.STRING)).isFalse();
        assertThat(s.mayTake(ItemKind.NEEDLE)).isFalse();
        assertThat(s.mayTake(ItemKind.OTHER)).isTrue();
    }
}
