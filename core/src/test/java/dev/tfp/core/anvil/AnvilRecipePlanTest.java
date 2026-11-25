/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/recipes/AnvilRecipe.java @ c3b4d9536 (computeTarget),
 * src/main/java/net/dries007/tfc/client/screen/AnvilPlanScreen.java @ c3b4d9536 (paging)
 */
package dev.tfp.core.anvil;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.anvil.AnvilFixtures.AnvilRow;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** PAR-METAL-04: the seeded work target of a selected plan, and the plan screen's paging, over TFC's 281 anvil recipes. */
class AnvilRecipePlanTest {
    static final List<AnvilRow> ROWS = AnvilFixtures.anvilRows();
    static final AnvilRecipes RECIPES = new AnvilRecipes(ROWS.stream().map(AnvilRow::recipe).toList());
    static final dev.tfp.core.recipe.ingredient.IngredientContext<String> CTX = AnvilFixtures.context(AnvilFixtures.tagMembers());

    @Test
    void targetIsFortyPlusNextIntOfTheHashedResultInTfcsRange() {
        Set<Integer> seen = new HashSet<>();
        for (long seed : new long[] {0L, 1L, -1L, 123456789L, Long.MIN_VALUE, 0x5DEECE66DL}) {
            for (AnvilRecipe r : RECIPES.all()) {
                int target = r.computeTarget(seed);
                assertThat(target).as(r.id()).isBetween(40, 113);
                assertThat(target).isEqualTo(r.computeTarget(seed)); // pure
                assertThat(target).isEqualTo(AnvilRecipe.workTarget(seed, r.output().item()));
                seen.add(target);
            }
        }
        assertThat(seen.size()).as("the targets spread over the range").isGreaterThan(50);
    }

    @Test
    void sameResultItemSameTargetInAWorld() {
        // two recipes with the result tfc:metal/bars/copper (single and double sheet) have one target
        AnvilRecipe a = RECIPES.byId("tfc:anvil/metal/bars/copper").orElseThrow();
        AnvilRecipe b = RECIPES.byId("tfc:anvil/metal/bars/copper_double").orElseThrow();
        assertThat(a.output().item()).isEqualTo(b.output().item());
        assertThat(a.computeTarget(42L)).isEqualTo(b.computeTarget(42L));
    }

    @Test
    void seedAndItemChangeTheTarget() {
        Set<Integer> bySeed = new HashSet<>();
        for (long seed = 0; seed < 40; seed++) {
            bySeed.add(AnvilRecipe.workTarget(seed, "tfc:metal/pickaxe_head/copper"));
        }
        assertThat(bySeed.size()).isGreaterThan(15);
        Set<Integer> byItem = new HashSet<>();
        for (AnvilRow row : ROWS) {
            byItem.add(AnvilRecipe.workTarget(7L, row.output().item()));
        }
        assertThat(byItem.size()).isGreaterThan(40);
    }

    /**
     * Golden values. The formula is TFC's ({@code 40 + XoroshiroRandomSource(seed).forkPositional().fromHashOf(item).nextInt(74)});
     * the numbers were produced by this port's {@code XoroshiroRandomSource} and {@code PositionalRandom}, which
     * {@code PositionalRandomTest} and the worldgen goldens check against the Minecraft 1.21 server, so a change here means a change
     * of the random chain.
     */
    @Test
    void goldenTargets() {
        assertThat(AnvilRecipe.workTarget(0L, "tfc:metal/pickaxe_head/copper")).isEqualTo(GOLDEN_PICKAXE_0);
        assertThat(AnvilRecipe.workTarget(1234567890123L, "tfc:metal/pickaxe_head/copper")).isEqualTo(GOLDEN_PICKAXE_SEED);
        assertThat(AnvilRecipe.workTarget(1234567890123L, "tfc:metal/hammer_head/copper")).isEqualTo(GOLDEN_HAMMER_SEED);
    }

    static final int GOLDEN_PICKAXE_0 = 104;
    static final int GOLDEN_PICKAXE_SEED = 102;
    static final int GOLDEN_HAMMER_SEED = 47;

    @Test
    void copperIngotListsTfcsPlansAndPickaxeHeadIsOne() {
        List<AnvilRecipe> plans = RECIPES.candidates("tfc:metal/ingot/copper", 1, CTX);
        assertThat(plans).extracting(AnvilRecipe::id).contains("tfc:anvil/metal/pickaxe_head/copper");
        assertThat(plans).hasSize(14);
        // a tier 0 (rock) anvil offers none of them
        assertThat(RECIPES.candidates("tfc:metal/ingot/copper", 0, CTX)).isEmpty();
        AnvilRecipe pick = RECIPES.byId("tfc:anvil/metal/pickaxe_head/copper").orElseThrow();
        assertThat(pick.computeTarget(99L)).isBetween(40, 113);
    }

    @Test
    void pagingIsEighteenPerPageInTwoRowsOfNine() {
        assertThat(AnvilRecipePage.maxPage(0)).isZero();
        assertThat(AnvilRecipePage.maxPage(1)).isZero();
        assertThat(AnvilRecipePage.maxPage(18)).isZero();
        assertThat(AnvilRecipePage.maxPage(19)).isEqualTo(1);
        assertThat(AnvilRecipePage.maxPage(36)).isEqualTo(1);
        assertThat(AnvilRecipePage.maxPage(37)).isEqualTo(2);
        assertThat(AnvilRecipePage.pageOf(17)).isZero();
        assertThat(AnvilRecipePage.pageOf(18)).isEqualTo(1);
        assertThat(AnvilRecipePage.cellOf(20)).isEqualTo(2);
        assertThat(AnvilRecipePage.cellOf(9) / AnvilRecipePage.COLUMNS).isEqualTo(1);

        List<Integer> all = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            all.add(i);
        }
        assertThat(AnvilRecipePage.pageItems(all, 0)).hasSize(18).startsWith(0).endsWith(17);
        assertThat(AnvilRecipePage.pageItems(all, 1)).startsWith(18).endsWith(35);
        assertThat(AnvilRecipePage.pageItems(all, 2)).containsExactly(36, 37, 38, 39);
        assertThat(AnvilRecipePage.pageItems(all, 9)).containsExactly(36, 37, 38, 39); // clamped
        assertThat(AnvilRecipePage.pageItems(all, -3)).hasSize(18).startsWith(0);
        assertThat(AnvilRecipePage.pageItems(List.<Integer>of(), 0)).isEmpty();
        assertThat(AnvilRecipePage.hasPrevious(0)).isFalse();
        assertThat(AnvilRecipePage.hasPrevious(1)).isTrue();
        assertThat(AnvilRecipePage.hasNext(0, 18)).isFalse();
        assertThat(AnvilRecipePage.hasNext(0, 19)).isTrue();
        assertThat(AnvilRecipePage.hasNext(2, 40)).isFalse();
    }

    @Test
    void everyRealItemFitsOnItsPages() {
        // the most plans any item has stay within TFC's pages; each plan is on exactly one page
        int most = 0;
        for (AnvilRow row : ROWS) {
            for (String item : AnvilFixtures.members(row.input(), AnvilFixtures.tagMembers())) {
                List<AnvilRecipe> plans = RECIPES.candidates(item, 6, CTX);
                most = Math.max(most, plans.size());
                int count = 0;
                for (int p = 0; p <= AnvilRecipePage.maxPage(plans.size()); p++) {
                    count += AnvilRecipePage.pageItems(plans, p).size();
                }
                assertThat(count).as(item).isEqualTo(plans.size());
            }
        }
        assertThat(most).isGreaterThan(1);
    }
}
