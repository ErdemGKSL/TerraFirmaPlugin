/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.farm.fertilizer;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.farm.Nutrients;
import dev.tfp.core.farm.SoilTier;
import dev.tfp.paper.farm.fertilizer.Fertilizers.Fertilizer;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** PAR-FARM-02: TFC's {@code useFertilizer} arithmetic: one item, sneaking fills, full soil refuses, soil tiers scale. */
class FertilizerUseTest {
    private static final Fertilizer PURE_NITROGEN = new Fertilizer("tfp:pure_nitrogen", null, 0.1f, 0f, 0f);
    private static final Fertilizer COMPOST = new Fertilizer("tfp:compost", null, 0.2f, 0f, 0.2f);
    private static final Fertilizer GUANO = new Fertilizer("tfp:groundcover/guano", null, 0.4f, 0.5f, 0.1f);

    @Test
    void oneItemRaisesNitrogenByItsAmountTimesTheBonus() {
        FertilizerUse.Plan plan = FertilizerUse.plan(Nutrients.EMPTY, PURE_NITROGEN, SoilTier.NORMAL.bonus(), false, 16, false).orElseThrow();
        assertThat(plan.repeat()).isEqualTo(1);
        assertThat(plan.after().nitrogen()).isEqualTo(0.1f);
        assertThat(plan.after().phosphorus()).isZero();
        FertilizerUse.Plan rich = FertilizerUse.plan(Nutrients.EMPTY, PURE_NITROGEN, SoilTier.VERY_RICH.bonus(), false, 16, false).orElseThrow();
        assertThat(rich.after().nitrogen()).isEqualTo(0.1f * 1.2f);
        FertilizerUse.Plan poor = FertilizerUse.plan(Nutrients.EMPTY, PURE_NITROGEN, SoilTier.VERY_POOR.bonus(), false, 16, false).orElseThrow();
        assertThat(poor.after().nitrogen()).isEqualTo(0.1f * 0.8f);
    }

    @Test
    void sneakingUsesEnoughItemsForTheNextBarOfTheNutrientNeedingTheFewest() {
        // compost 0.2 N / 0.2 K; N at 0.5 needs 3 (ceil(2.5)), K at 0.9 needs 1 (ceil(0.5)): the minimum is 1
        Nutrients soil = new Nutrients(0.5f, 0f, 0.9f);
        assertThat(FertilizerUse.plan(soil, COMPOST, 1f, true, 64, false).orElseThrow().repeat()).isEqualTo(1);
        // only nitrogen can rise: ceil((1 - 0.5) / 0.1) = 5, capped by the stack
        Nutrients half = new Nutrients(0.5f, 0f, 0f);
        assertThat(FertilizerUse.plan(half, PURE_NITROGEN, 1f, true, 64, false).orElseThrow().repeat()).isEqualTo(5);
        assertThat(FertilizerUse.plan(half, PURE_NITROGEN, 1f, true, 3, false).orElseThrow().repeat()).isEqualTo(3);
        assertThat(FertilizerUse.plan(half, PURE_NITROGEN, 1f, true, 3, true).orElseThrow().repeat()).isEqualTo(99);
        assertThat(FertilizerUse.plan(half, PURE_NITROGEN, 1f, true, 64, false).orElseThrow().after().nitrogen()).isEqualTo(1f);
    }

    @Test
    void aFullNutrientTheFertilizerGivesRefusesAndAnOtherOneStillWorks() {
        assertThat(FertilizerUse.plan(new Nutrients(1f, 0f, 0f), PURE_NITROGEN, 1f, false, 4, false)).isEmpty();
        Optional<FertilizerUse.Plan> guano = FertilizerUse.plan(new Nutrients(1f, 0f, 1f), GUANO, 1f, false, 4, false);
        assertThat(guano).isPresent();
        assertThat(guano.get().after().phosphorus()).isEqualTo(0.5f);
        assertThat(guano.get().after().nitrogen()).isEqualTo(1f);
    }

    @Test
    void sneakingWithNothingToFillStillUsesOne() {
        FertilizerUse.Plan plan = FertilizerUse.plan(new Nutrients(1f, 0f, 1f), COMPOST, 1f, true, 9, false).orElse(null);
        assertThat(plan).isNull(); // compost gives only N and K: both full, nothing happens
        FertilizerUse.Plan other = FertilizerUse.plan(new Nutrients(1f, 0.2f, 1f), GUANO, 1f, true, 9, false).orElseThrow();
        assertThat(other.repeat()).isEqualTo(2); // ceil(0.8 / 0.5) = 2
    }
}
