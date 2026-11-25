/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.glass;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.glass.GlassBlowpipes.Outcome;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** PAR-PROC-16: the blowpipe's operation per hand, the finish rule and the break roll, on TFC's real recipes. */
class GlassBlowpipesTest {
    private static final String SILICA = "tfc:silica_glass_batch";
    private static final List<GlassOperation> LAMP = List.of(GlassOperation.BLOW, GlassOperation.PINCH, GlassOperation.FLATTEN,
        GlassOperation.BLOW, GlassOperation.SAW);

    @Test
    void anEmptyHandBlowsAndLookingStraightDownStretches() {
        assertThat(GlassBlowpipes.operationOf("", 0.0)).hasValue(GlassOperation.BLOW);
        assertThat(GlassBlowpipes.operationOf(null, 1.0)).hasValue(GlassOperation.BLOW);
        assertThat(GlassBlowpipes.operationOf("", -0.94)).hasValue(GlassOperation.BLOW);
        assertThat(GlassBlowpipes.operationOf("", -0.95)).hasValue(GlassOperation.BLOW); // strictly below
        assertThat(GlassBlowpipes.operationOf("", -0.96)).hasValue(GlassOperation.STRETCH);
        assertThat(GlassBlowpipes.operationOf("", -1.0)).hasValue(GlassOperation.STRETCH);
    }

    @Test
    void theFourToolsGiveTheirOperationAndNothingElseDoes() {
        assertThat(GlassBlowpipes.operationOf("tfc:wool_cloth", 0)).hasValue(GlassOperation.ROLL);
        assertThat(GlassBlowpipes.operationOf("tfc:jacks", 0)).hasValue(GlassOperation.PINCH);
        assertThat(GlassBlowpipes.operationOf("tfc:paddle", 0)).hasValue(GlassOperation.FLATTEN);
        assertThat(GlassBlowpipes.operationOf("tfc:gem_saw", 0)).hasValue(GlassOperation.SAW);
        assertThat(GlassBlowpipes.operationOf("minecraft:stick", 0)).isEmpty();
        assertThat(GlassBlowpipes.operationOf("tfc:blowpipe_with_glass", 0)).isEmpty();
        assertThat(GlassBlowpipes.tools().values()).doesNotContain(GlassOperation.BLOW, GlassOperation.STRETCH);
    }

    @Test
    void transformGoesBothWaysAndLeavesOtherItemsAlone() {
        assertThat(GlassBlowpipes.transform(GlassBlowpipes.BLOWPIPE)).isEqualTo(GlassBlowpipes.BLOWPIPE_WITH_GLASS);
        assertThat(GlassBlowpipes.transform(GlassBlowpipes.BLOWPIPE_WITH_GLASS)).isEqualTo(GlassBlowpipes.BLOWPIPE);
        assertThat(GlassBlowpipes.transform(GlassBlowpipes.CERAMIC_BLOWPIPE)).isEqualTo(GlassBlowpipes.CERAMIC_BLOWPIPE_WITH_GLASS);
        assertThat(GlassBlowpipes.transform(GlassBlowpipes.CERAMIC_BLOWPIPE_WITH_GLASS)).isEqualTo(GlassBlowpipes.CERAMIC_BLOWPIPE);
        assertThat(GlassBlowpipes.transform("minecraft:stick")).isEqualTo("minecraft:stick");
        assertThat(GlassBlowpipes.isGlassBlowpipe(GlassBlowpipes.BLOWPIPE_WITH_GLASS)).isTrue();
        assertThat(GlassBlowpipes.isGlassBlowpipe(GlassBlowpipes.BLOWPIPE)).isFalse();
        assertThat(GlassBlowpipes.isEmptyBlowpipe(GlassBlowpipes.CERAMIC_BLOWPIPE)).isTrue();
    }

    @Test
    void onlyTheClayBlowpipeBreaks() {
        assertThat(GlassBlowpipes.breakChance(GlassBlowpipes.BLOWPIPE_WITH_GLASS)).isEqualTo(0.0);
        assertThat(GlassBlowpipes.breakChance(GlassBlowpipes.CERAMIC_BLOWPIPE_WITH_GLASS)).isEqualTo(0.1);
    }

    @Test
    void theLampGlassSequenceFinishesOnTheLastStepAndOnlyThere() {
        GlassState state = GlassRules.withBatch(GlassState.EMPTY, SILICA);
        for (int i = 0; i < LAMP.size() - 1; i++) {
            Outcome o = GlassBlowpipes.finish(GlassRecipes.ALL, GlassBlowpipes.BLOWPIPE_WITH_GLASS, state, LAMP.get(i), () -> 0.5);
            assertThat(o).isInstanceOf(Outcome.Stepped.class);
            state = ((Outcome.Stepped) o).state();
            assertThat(state.steps()).containsExactlyElementsOf(LAMP.subList(0, i + 1));
        }
        Outcome last = GlassBlowpipes.finish(GlassRecipes.ALL, GlassBlowpipes.BLOWPIPE_WITH_GLASS, state, GlassOperation.SAW, () -> 0.5);
        assertThat(last).isInstanceOf(Outcome.Finished.class);
        Outcome.Finished f = (Outcome.Finished) last;
        assertThat(f.result().item()).isEqualTo("tfc:lamp_glass");
        assertThat(f.blowpipe()).isEqualTo(GlassBlowpipes.BLOWPIPE);
        assertThat(f.broke()).isFalse();
    }

    @Test
    void aWrongOrderGivesNoResult() {
        GlassState state = GlassRules.withBatch(GlassState.EMPTY, SILICA);
        for (GlassOperation op : List.of(GlassOperation.PINCH, GlassOperation.BLOW)) {
            state = ((Outcome.Stepped) GlassBlowpipes.finish(GlassRecipes.ALL, GlassBlowpipes.BLOWPIPE_WITH_GLASS, state, op, () -> 0.5)).state();
        }
        assertThat(GlassBlowpipes.finish(GlassRecipes.ALL, GlassBlowpipes.BLOWPIPE_WITH_GLASS, state, GlassOperation.SAW, () -> 0.5))
            .isInstanceOf(Outcome.Stepped.class);
    }

    @Test
    void aSteppedOperationDrawsNoRandomNumberAndAFinishDrawsOne() {
        AtomicInteger draws = new AtomicInteger();
        GlassState state = GlassRules.withBatch(GlassState.EMPTY, SILICA);
        GlassBlowpipes.finish(GlassRecipes.ALL, GlassBlowpipes.CERAMIC_BLOWPIPE_WITH_GLASS, state, GlassOperation.BLOW,
            () -> { draws.incrementAndGet(); return 0.0; });
        assertThat(draws).hasValue(0);
        state = new GlassState(new ArrayList<>(List.of(GlassOperation.BLOW, GlassOperation.PINCH)), SILICA);
        GlassBlowpipes.finish(GlassRecipes.ALL, GlassBlowpipes.CERAMIC_BLOWPIPE_WITH_GLASS, state, GlassOperation.SAW,
            () -> { draws.incrementAndGet(); return 0.5; });
        assertThat(draws).hasValue(1);
    }

    @Test
    void theClayBlowpipeBreaksBelowItsChanceAndTheMetalOneNever() {
        GlassState state = new GlassState(List.of(GlassOperation.BLOW, GlassOperation.PINCH), SILICA);
        Outcome.Finished broken = (Outcome.Finished) GlassBlowpipes.finish(GlassRecipes.ALL,
            GlassBlowpipes.CERAMIC_BLOWPIPE_WITH_GLASS, state, GlassOperation.SAW, () -> 0.0999);
        assertThat(broken.broke()).isTrue();
        assertThat(broken.blowpipe()).isEmpty();
        assertThat(broken.result().item()).isEqualTo("tfc:silica_glass_bottle");
        Outcome.Finished kept = (Outcome.Finished) GlassBlowpipes.finish(GlassRecipes.ALL,
            GlassBlowpipes.CERAMIC_BLOWPIPE_WITH_GLASS, state, GlassOperation.SAW, () -> 0.1);
        assertThat(kept.broke()).isFalse();
        assertThat(kept.blowpipe()).isEqualTo(GlassBlowpipes.CERAMIC_BLOWPIPE);
        Outcome.Finished metal = (Outcome.Finished) GlassBlowpipes.finish(GlassRecipes.ALL,
            GlassBlowpipes.BLOWPIPE_WITH_GLASS, state, GlassOperation.SAW, () -> 0.0);
        assertThat(metal.broke()).isFalse();
    }

    @Test
    void theLensNeedsAStretchBetweenBlowAndRoll() {
        GlassState state = GlassRules.withBatch(GlassState.EMPTY, SILICA);
        GlassOperation[] ops = {GlassOperation.BLOW, GlassOperation.STRETCH, GlassOperation.ROLL, GlassOperation.SAW};
        Outcome o = null;
        for (GlassOperation op : ops) {
            o = GlassBlowpipes.finish(GlassRecipes.ALL, GlassBlowpipes.BLOWPIPE_WITH_GLASS, state, op, () -> 0.5);
            if (o instanceof Outcome.Stepped s) {
                state = s.state();
            }
        }
        assertThat(((Outcome.Finished) o).result().item()).isEqualTo("tfc:lens");
    }

    @Test
    void theDurationsAreTfcs() {
        assertThat(GlassBlowpipes.OPERATION_USE_TICKS).isEqualTo(40);
        assertThat(GlassBlowpipes.COOLDOWN_TICKS).isEqualTo(20);
    }
}
