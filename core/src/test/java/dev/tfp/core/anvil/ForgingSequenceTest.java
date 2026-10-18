/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * files: src/main/java/net/dries007/tfc/common/component/forge/ForgeRule.java and ForgeSteps.java (matching a step history against the
 * rules) and src/main/java/net/dries007/tfc/common/blockentities/AnvilBlockEntity.java (work, the completion check) @ c3b4d9536
 */
package dev.tfp.core.anvil;

import static dev.tfp.core.forge.ForgeStep.BEND;
import static dev.tfp.core.forge.ForgeStep.DRAW;
import static dev.tfp.core.forge.ForgeStep.HIT_LIGHT;
import static dev.tfp.core.forge.ForgeStep.PUNCH;
import static dev.tfp.core.forge.ForgeStep.SHRINK;
import static dev.tfp.core.forge.ForgeStep.UPSET;
import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.craft.CraftOutput;
import dev.tfp.core.forge.ForgeStep;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * M4-ANV-02 golden forging sequences. Each plan is a step sequence that the TFC rules of a golden recipe accept: a prefix of
 * {@code SHRINK} steps brings the offset into range, then the last three steps are the ones the recipe's rules constrain. The sequences
 * were worked out by hand from the rules in tfc-anvil-recipes.tsv, and checked by the forge rules in dev.tfp.core.forge.
 */
class ForgingSequenceTest {

    /** A worked plan: the recipe file, the steps taken in order, the work target it completes at, and the expected result. */
    record Plan(String file, List<ForgeStep> steps, int target, CraftOutput output) {}

    static final List<Plan> PLANS = List.of(
        // blowpipe: draw_last, draw_second_last, hit_third_last
        new Plan("blowpipe", List.of(SHRINK, SHRINK, UPSET, HIT_LIGHT, DRAW, DRAW), 12, new CraftOutput("tfc:blowpipe", 1)),
        // iron_door: hit_last, punch_not_last, draw_not_last
        new Plan("iron_door", List.of(SHRINK, SHRINK, SHRINK, PUNCH, DRAW, HIT_LIGHT), 32, new CraftOutput("minecraft:iron_door", 1)),
        // jar_lid: hit_last, hit_second_last, punch_third_last
        new Plan("jar_lid", List.of(SHRINK, SHRINK, SHRINK, PUNCH, HIT_LIGHT, HIT_LIGHT), 44, new CraftOutput("tfc:jar_lid", 16)),
        // metal/axe_head/bronze: punch_last, hit_second_last, upset_third_last
        new Plan("metal/axe_head/bronze", List.of(SHRINK, SHRINK, UPSET, HIT_LIGHT, PUNCH), 44,
            new CraftOutput("tfc:metal/axe_head/bronze", 1))
    );

    @Test
    void tfcStepSequencesAreAcceptedAndGiveTheRecipeOutput() {
        for (Plan plan : PLANS) {
            final AnvilRecipe recipe = AnvilGoldenTest.byFile(plan.file()).orElseThrow();
            final ForgingProgress progress = play(plan.steps());

            assertThat(progress.isDestroyed()).as(plan.file()).isFalse();
            assertThat(progress.steps().matches(recipe.rules())).as(plan.file()).isTrue();
            assertThat(recipe.checkComplete(progress, plan.target(), 0)).as(plan.file()).isTrue();
            assertThat(recipe.output()).as(plan.file()).isEqualTo(plan.output());
            assertThat(progress.work()).as(plan.file()).isEqualTo(plan.target());
        }
    }

    @Test
    void reversingTheRuleStepsIsRejected() {
        for (Plan plan : PLANS) {
            final AnvilRecipe recipe = AnvilGoldenTest.byFile(plan.file()).orElseThrow();
            final List<ForgeStep> steps = new ArrayList<>(plan.steps());
            // The last three steps are the ones the rules constrain. Reversing them breaks the positional rules.
            final List<ForgeStep> tail = new ArrayList<>(steps.subList(steps.size() - 3, steps.size()));
            Collections.reverse(tail);
            steps.subList(steps.size() - 3, steps.size()).clear();
            steps.addAll(tail);

            final ForgingProgress progress = play(steps);
            assertThat(recipe.checkComplete(progress, plan.target(), 0)).as(plan.file() + " reversed").isFalse();
        }
    }

    @Test
    void workMustLieWithinTheLeeway() {
        for (Plan plan : PLANS) {
            final AnvilRecipe recipe = AnvilGoldenTest.byFile(plan.file()).orElseThrow();
            final ForgingProgress progress = play(plan.steps());

            assertThat(recipe.checkComplete(progress, plan.target() + 1, 0)).as(plan.file()).isFalse();
            assertThat(recipe.checkComplete(progress, plan.target() + 1, 1)).as(plan.file()).isTrue();
            assertThat(recipe.checkComplete(progress, plan.target() - 1, 1)).as(plan.file()).isTrue();
            assertThat(recipe.checkComplete(progress, plan.target() - 2, 1)).as(plan.file()).isFalse();
        }
    }

    /** Applies the steps in order, asserting each one is allowed and does not destroy the item on the way. */
    private static ForgingProgress play(List<ForgeStep> steps) {
        ForgingProgress progress = ForgingProgress.EMPTY;
        for (ForgeStep step : steps) {
            assertThat(progress.canApply(step)).isTrue();
            progress = progress.apply(step);
        }
        return progress;
    }
}
