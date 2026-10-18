/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.knap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** The knapping answers do not depend on the order recipes or types were loaded in, nor on repeated calls. */
class KnappingDeterminismTest {

    private static final KnappingFixture.Data DATA = KnappingFixture.load();

    @Test
    void answersDoNotDependOnLoadOrder() {
        List<KnappingRecipe> sorted = KnappingRules.sortedById(DATA.recipes());
        for (long seed = 1; seed <= 20; seed++) {
            List<KnappingRecipe> shuffled = new ArrayList<>(DATA.recipes());
            Collections.shuffle(shuffled, new Random(seed));
            for (KnappingFixture.PickRow row : DATA.picks()) {
                KnappingPattern grid = KnappingPattern.grid(row.grid());
                Optional<KnappingRecipe> expected = KnappingRules.find(sorted, row.type(), grid, row.item(), DATA.tags());
                assertThat(KnappingRules.find(shuffled, row.type(), grid, row.item(), DATA.tags())).as("seed %d %s", seed, row.grid()).isEqualTo(expected);
            }
            for (KnappingFixture.MatchRow row : DATA.matches()) {
                KnappingPattern grid = KnappingPattern.grid(row.grid());
                assertThat(KnappingRules.matching(shuffled, row.type(), grid)).as("seed %d %s", seed, row.grid())
                    .isEqualTo(KnappingRules.matching(sorted, row.type(), grid));
            }
        }
    }

    @Test
    void repeatedCallsAgreeAndDoNotChangeTheGrid() {
        KnappingFixture.PickRow row = DATA.picks().get(0);
        KnappingPattern grid = KnappingPattern.grid(row.grid());
        String before = KnappingFixture.cells(grid);
        Optional<KnappingRecipe> first = KnappingRules.find(DATA.recipes(), row.type(), grid, row.item(), DATA.tags());
        for (int i = 0; i < 5; i++) {
            assertThat(KnappingRules.find(DATA.recipes(), row.type(), grid, row.item(), DATA.tags())).isEqualTo(first);
        }
        assertThat(KnappingFixture.cells(grid)).isEqualTo(before);
    }

    @Test
    void typeLookupDoesNotDependOnOrder() {
        for (KnappingFixture.ItemRow row : DATA.items()) {
            List<KnappingType> reversed = new ArrayList<>(DATA.types());
            Collections.reverse(reversed);
            assertThat(KnappingRules.typeOf(reversed, row.item(), DATA.tags())).isEqualTo(KnappingRules.typeOf(DATA.types(), row.item(), DATA.tags()));
        }
    }

    @Test
    void loadingTwiceGivesEqualData() {
        KnappingFixture.Data again = KnappingFixture.load();
        assertThat(again.recipes()).isEqualTo(DATA.recipes());
        assertThat(again.types()).isEqualTo(DATA.types());
        assertThat(again.matches()).isEqualTo(DATA.matches());
    }

    @Test
    void duplicateRecipeIdsAreRejected() {
        KnappingRecipe r = DATA.recipes().get(0);
        assertThatThrownBy(() -> KnappingRules.sortedById(List.of(r, r))).isInstanceOf(IllegalArgumentException.class);
    }
}
