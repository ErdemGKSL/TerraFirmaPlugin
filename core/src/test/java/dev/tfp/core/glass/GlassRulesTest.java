/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.glass;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.tfp.core.craft.CraftOutput;
import dev.tfp.core.heat.HeatLevel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class GlassRulesTest {

    static final String SILICA = "tfc:silica_glass_batch";
    static final String VOLCANIC = "tfc:volcanic_glass_batch";

    @Test
    void applyAppendsStepsAndKeepsTheBatch() {
        GlassState s = GlassRules.withBatch(GlassState.EMPTY, SILICA);
        s = GlassRules.apply(s, GlassOperation.COPPER);
        s = GlassRules.apply(s, GlassOperation.TABLE_POUR);
        assertThat(s.steps()).containsExactly(GlassOperation.COPPER, GlassOperation.TABLE_POUR);
        assertThat(s.batch()).isEqualTo(SILICA);
    }

    @Test
    void applyWithoutBatchIsANoOp() {
        assertThat(GlassRules.apply(GlassState.EMPTY, GlassOperation.BLOW)).isEqualTo(GlassState.EMPTY);
    }

    @Test
    void stepsStopAtTheLimit() {
        GlassState s = GlassRules.withBatch(GlassState.EMPTY, SILICA);
        for (int i = 0; i < GlassState.LIMIT; i++) {
            s = GlassRules.apply(s, GlassOperation.ROLL);
        }
        assertThat(s.steps()).hasSize(GlassState.LIMIT);
        assertThat(GlassRules.apply(s, GlassOperation.PINCH)).isEqualTo(s);
    }

    @Test
    void newBatchKeepsTheSteps() {
        GlassState s = GlassRules.apply(GlassRules.withBatch(GlassState.EMPTY, SILICA), GlassOperation.BLOW);
        GlassState replaced = GlassRules.withBatch(s, VOLCANIC);
        assertThat(replaced.steps()).containsExactly(GlassOperation.BLOW);
        assertThat(replaced.batch()).isEqualTo(VOLCANIC);
        assertThatThrownBy(() -> GlassRules.withBatch(s, "")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void stateRejectsStepsWithoutABatch() {
        assertThatThrownBy(() -> new GlassState(List.of(GlassOperation.BLOW), "")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GlassState(Collections.nCopies(GlassState.LIMIT + 1, GlassOperation.ROLL), SILICA))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void powdersAddTheirOperationAndOtherItemsDoNot() {
        GlassState s = GlassRules.withBatch(GlassState.EMPTY, SILICA);
        assertThat(GlassRules.applyPowder(s, "tfc:powder/native_copper").steps()).containsExactly(GlassOperation.COPPER);
        assertThat(GlassRules.applyPowder(s, "tfc:powder/hematite").steps()).containsExactly(GlassOperation.IRON);
        assertThat(GlassRules.applyPowder(s, "tfc:powder/saltpeter")).isEqualTo(s);
        assertThat(GlassRules.applyPowder(s, SILICA)).isEqualTo(s);
    }

    @Test
    void everyPowderItemMapsToOneOperation() {
        assertThat(GlassOperation.byPowder("tfc:powder/malachite")).contains(GlassOperation.COPPER);
        assertThat(GlassOperation.byPowder("tfc:powder/magnetite")).contains(GlassOperation.IRON);
        assertThat(GlassOperation.byPowder("tfc:powder/soda_ash")).contains(GlassOperation.SODA_ASH);
        assertThat(GlassOperation.byPowder("tfc:wool_cloth")).isEmpty();
        for (GlassOperation op : GlassOperation.values()) {
            assertThat(op.isPowder()).isEqualTo(op.id().matches("tfc:(soda_ash|amethyst|sulfur|iron|ruby|lapis_lazuli|pyrite|sapphire|gold|graphite|copper|nickel|tin|silver)"));
        }
    }

    @Test
    void operationsNeedAStrictlyHotterBlowpipe() {
        final float min = HeatLevel.FAINT_RED.min();
        assertThat(GlassRules.isHotEnough(GlassOperation.BLOW, min)).isFalse();
        assertThat(GlassRules.isHotEnough(GlassOperation.BLOW, min + 0.5f)).isTrue();
        assertThat(GlassRules.isHotEnough(GlassOperation.SAW, 0.1f)).isTrue();
        assertThat(GlassRules.isHotEnough(GlassOperation.SAW, 0f)).isFalse();
    }

    @Test
    void matchNeedsTheStepsInOrderAndAnAcceptedBatch() {
        final GlassState pour = new GlassState(List.of(GlassOperation.TABLE_POUR), SILICA);
        assertThat(GlassRules.output(GlassRecipes.ALL, pour)).contains(new CraftOutput("tfc:poured_glass", 1));

        // Order matters: sapphire before copper is the _2 recipe, and the first order does not match it.
        final GlassState reversed = new GlassState(List.of(GlassOperation.SAPPHIRE, GlassOperation.COPPER, GlassOperation.TABLE_POUR), "tfc:olivine_glass_batch");
        assertThat(GlassRules.match(GlassRecipes.ALL, reversed).map(GlassRecipe::id)).contains("glassworking/cyan_poured_glass_2");
        final GlassState unknownOrder = new GlassState(List.of(GlassOperation.TABLE_POUR, GlassOperation.COPPER), "tfc:olivine_glass_batch");
        assertThat(GlassRules.match(GlassRecipes.ALL, unknownOrder)).isEmpty();
    }

    @Test
    void tierTagsDecideWhichBatchesMakeTheColour() {
        // Cyan needs tier 3 (silica, hematitic, olivine); volcanic is not in that tier.
        final List<GlassOperation> cyan = List.of(GlassOperation.COPPER, GlassOperation.SAPPHIRE, GlassOperation.TABLE_POUR);
        assertThat(GlassRules.match(GlassRecipes.ALL, new GlassState(cyan, "tfc:olivine_glass_batch")).map(GlassRecipe::id))
            .contains("glassworking/cyan_poured_glass");
        assertThat(GlassRules.match(GlassRecipes.ALL, new GlassState(cyan, VOLCANIC))).isEmpty();
        // Tinted glass is every batch except silica.
        final GlassState tinted = new GlassState(List.of(GlassOperation.AMETHYST, GlassOperation.BASIN_POUR), VOLCANIC);
        assertThat(GlassRules.output(GlassRecipes.ALL, tinted)).contains(new CraftOutput("minecraft:tinted_glass", 1));
        assertThat(GlassRules.match(GlassRecipes.ALL, new GlassState(tinted.steps(), SILICA))).isEmpty();
    }

    @Test
    void lookupDoesNotDependOnRecipeOrder() {
        final GlassState s = new GlassState(List.of(GlassOperation.TABLE_POUR), SILICA);
        final List<GlassRecipe> shuffled = new ArrayList<>(GlassRecipes.ALL);
        Collections.shuffle(shuffled, new Random(42));
        assertThat(GlassRules.match(shuffled, s)).isEqualTo(GlassRules.match(GlassRecipes.ALL, s));
    }

    @Test
    void batchTagsResolveRecursively() {
        assertThat(GlassBatches.members("#tfc:glass_batches_tier_2")).containsExactly("tfc:hematitic_glass_batch", "tfc:silica_glass_batch");
        assertThat(GlassBatches.members("#tfc:glass_batches")).hasSize(4);
        assertThat(GlassBatches.members("#tfc:glass_batches_not_tier_1")).doesNotContain(SILICA);
        assertThat(GlassBatches.members("tfc:olivine_glass_batch")).containsExactly("tfc:olivine_glass_batch");
        assertThat(GlassBatches.members("#tfc:no_such_tag")).isEmpty();
    }
}
