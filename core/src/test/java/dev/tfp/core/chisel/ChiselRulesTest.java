/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.chisel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class ChiselRulesTest {
    private static final ChiselRecipe SMOOTH_ROCK = new ChiselRecipe("rock/smooth/x", List.of("tfc:rock/raw/x", "tfc:rock/hardened/x"),
        "tfc:rock/smooth/x", ChiselMode.SMOOTH, null);
    private static final ChiselRecipe SLAB_ROCK = new ChiselRecipe("rock/slab/x", List.of("tfc:rock/smooth/x"),
        "tfc:rock/smooth/x_slab", ChiselMode.SLAB, new ChiselRecipe.ItemOutput("tfc:rock/smooth/x_slab", 1));
    private static final List<ChiselRecipe> RECIPES = List.of(SMOOTH_ROCK, SLAB_ROCK);

    @Test
    void cycleFollowsTfcPriorityAndWraps() {
        assertThat(ChiselMode.SMOOTH.next()).isEqualTo(ChiselMode.SLAB);
        assertThat(ChiselMode.SLAB.next()).isEqualTo(ChiselMode.STAIR);
        assertThat(ChiselMode.STAIR.next()).isEqualTo(ChiselMode.SMOOTH);
        assertThat(ChiselMode.values()).extracting(ChiselMode::priority).containsExactly(0, 100, 200);
    }

    @Test
    void fromIdRejectsUnknownMode() {
        assertThat(ChiselMode.fromId("tfc:slab")).isEqualTo(ChiselMode.SLAB);
        assertThatThrownBy(() -> ChiselMode.fromId("tfc:cube")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void recipeMatchesAnyIngredientInItsOwnModeOnly() {
        assertThat(SMOOTH_ROCK.matches("tfc:rock/raw/x", ChiselMode.SMOOTH)).isTrue();
        assertThat(SMOOTH_ROCK.matches("tfc:rock/hardened/x", ChiselMode.SMOOTH)).isTrue();
        assertThat(SMOOTH_ROCK.matches("tfc:rock/raw/x", ChiselMode.SLAB)).isFalse();
        assertThat(SMOOTH_ROCK.matches("tfc:rock/smooth/x", ChiselMode.SMOOTH)).isFalse();
    }

    @Test
    void findReturnsFirstMatchOrEmpty() {
        assertThat(ChiselRules.find(RECIPES, "tfc:rock/smooth/x", ChiselMode.SLAB)).contains(SLAB_ROCK);
        assertThat(ChiselRules.find(RECIPES, "tfc:rock/smooth/x", ChiselMode.STAIR)).isEmpty();
    }

    @Test
    void useNeedsChiselInMainHandAndHammerInOffhand() {
        assertThat(ChiselRules.use(RECIPES, "tfc:rock/raw/x", ChiselMode.SMOOTH, false, true, true).status())
            .isEqualTo(ChiselRules.Status.NOT_CHISELING);
        assertThat(ChiselRules.use(RECIPES, "tfc:rock/raw/x", ChiselMode.SMOOTH, true, false, true).status())
            .isEqualTo(ChiselRules.Status.NOT_CHISELING);
    }

    @Test
    void useWithoutRecipePassesAndFailsOnlyWhenPlacementIsRefused() {
        assertThat(ChiselRules.use(RECIPES, "tfc:stone", ChiselMode.SMOOTH, true, true, true).status())
            .isEqualTo(ChiselRules.Status.NO_RECIPE);
        assertThat(ChiselRules.use(RECIPES, "tfc:rock/smooth/x", ChiselMode.SLAB, true, true, false).status())
            .isEqualTo(ChiselRules.Status.CANNOT_PLACE);
    }

    @Test
    void chiseledUseCarriesItsRecipe() {
        ChiselRules.Use use = ChiselRules.use(RECIPES, "tfc:rock/smooth/x", ChiselMode.SLAB, true, true, true);
        assertThat(use.status()).isEqualTo(ChiselRules.Status.CHISELED);
        assertThat(use.recipe()).isEqualTo(SLAB_ROCK);
        assertThat(ChiselRules.DAMAGE_PER_USE).isEqualTo(1);
        assertThat(ChiselRules.COOLDOWN_TICKS).isEqualTo(10);
    }

    @Test
    void slabHalfFollowsTfcFaceRules() {
        assertThat(ChiselRules.slabHalf(ChiselRules.HitFace.DOWN, 0.9)).isEqualTo(ChiselRules.SlabHalf.TOP);
        assertThat(ChiselRules.slabHalf(ChiselRules.HitFace.UP, 0.1)).isEqualTo(ChiselRules.SlabHalf.BOTTOM);
        assertThat(ChiselRules.slabHalf(ChiselRules.HitFace.NORTH, 0.5)).isEqualTo(ChiselRules.SlabHalf.BOTTOM);
        assertThat(ChiselRules.slabHalf(ChiselRules.HitFace.NORTH, 0.51)).isEqualTo(ChiselRules.SlabHalf.TOP);
        assertThat(ChiselRules.slabHalf(ChiselRules.HitFace.EAST, 0.2)).isEqualTo(ChiselRules.SlabHalf.BOTTOM);
    }

    @Test
    void recipeRejectsBadInput() {
        assertThatThrownBy(() -> new ChiselRecipe("bad", List.of(), "tfc:x", ChiselMode.SMOOTH, null))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ChiselRecipe.ItemOutput("tfc:x", 0)).isInstanceOf(IllegalArgumentException.class);
    }
}
