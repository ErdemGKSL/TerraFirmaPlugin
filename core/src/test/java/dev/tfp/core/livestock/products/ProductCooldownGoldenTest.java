/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.livestock.products;

import dev.tfp.core.livestock.AnimalState;
import dev.tfp.core.livestock.LivestockSpecies;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Golden cooldown, regrowth and gating values, hand-traced from TFC at c3b4d9536.
 *
 * <p>The TFC lines each case follows are named in its Javadoc:
 * {@code ProducingAnimal.getProductsCooldown} = {@code max(0, produceTicks + producedTick - now)}, and
 * {@code ProducingMammal.getProductsCooldown} is the same line. {@code DairyAnimal.hasProduct} is
 * {@code isFemale && ADULT && getProductsCooldown() == 0}. {@code WoolyAnimal.hasProduct} is
 * {@code producedTick <= 0 || getProductsCooldown() <= 0}, and {@code WoolyAnimal.isReadyForAnimalProduct} adds ADULT.
 * {@code ProducingMammal.isReadyForAnimalProduct} is {@code familiarity > produceFamiliarity && hasProduct}, read as a
 * double comparison. {@code WoolyAnimal.getWoolItem} gives 2 wool when {@code familiarity > 0.99f}.
 */
class ProductCooldownGoldenTest {
    /** Adult for every species: {@code birth + adulthoodDays * 28800} is negative (the largest, bactrian camel, is 80 days). */
    private static final long ADULT_BIRTH = -10_000_000L;

    private static final ProductSpec COW = ProductSpec.defaults(LivestockSpecies.COW).orElseThrow();
    private static final ProductSpec SHEEP = ProductSpec.defaults(LivestockSpecies.SHEEP).orElseThrow();
    private static final ProductSpec CHICKEN = ProductSpec.defaults(LivestockSpecies.CHICKEN).orElseThrow();

    private static AnimalState animal(boolean male, float familiarity, long familiarityTick, long birthTick, long oldTick, int uses) {
        return new AnimalState(male, familiarity, familiarityTick, birthTick, oldTick,
            AnimalState.NEVER, AnimalState.NEVER, uses, false, AnimalState.NOT_PREGNANT);
    }

    /** A female adult, familiarity 0.5 (above the 0.3 decay limit, so nothing decays), anchored at tick 0. */
    private static AnimalState adultFemale() {
        return animal(false, 0.5f, 0L, ADULT_BIRTH, AnimalState.NO_OLD_TICK, 0);
    }

    /**
     * TFC {@code DairyAnimal.hasProduct} with {@code getProductsCooldown() == 0}. A fresh cow has {@code producedTick 0},
     * so her cooldown is {@code 28800 - now}, and she is milkable from tick 28800 on.
     * She is milked at 28800, the cooldown restarts there, and she is milkable again at 28800 + 28800 = 57600.
     */
    @Test
    void milkCooldownFollowsDairyAnimalHasProduct() {
        final AnimalState cow = adultFemale();
        final ProductState fresh = ProductState.fresh();

        assertThat(ProductRules.cooldownRemaining(COW, fresh, 0L)).isEqualTo(28_800L);
        assertThat(ProductRules.isReady(COW, LivestockSpecies.COW, cow, fresh, 28_799L)).isFalse();
        assertThat(ProductRules.milk(COW, LivestockSpecies.COW, cow, fresh, 28_799L)).isEmpty();

        final Optional<ProductRules.Harvest> first = ProductRules.milk(COW, LivestockSpecies.COW, cow, fresh, 28_800L);
        assertThat(first).isPresent();
        assertThat(first.get().product().producedTick()).isEqualTo(28_800L);
        // one use per milking, and the familiarity is not changed by milking
        assertThat(first.get().animal().uses()).isEqualTo(1);
        assertThat(first.get().animal().familiarity()).isEqualTo(0.5f);

        final ProductState after = first.get().product();
        assertThat(ProductRules.milk(COW, LivestockSpecies.COW, cow, after, 28_800L + 28_799L)).isEmpty();
        assertThat(ProductRules.cooldownRemaining(COW, after, 28_800L + 28_799L)).isEqualTo(1L);
        assertThat(ProductRules.milk(COW, LivestockSpecies.COW, cow, after, 57_600L)).isPresent();
    }

    /**
     * {@code DairyAnimal.hasProduct} requires {@code isFemale()}, and {@code getAgeType() == ADULT}. An old animal
     * has {@code getAgeType() == OLD}, so it gives no milk even with a zero cooldown. An old-tick of 1000 is in the past
     * at 28800.
     */
    @Test
    void maleAndOldCowsGiveNoMilk() {
        final ProductState fresh = ProductState.fresh();
        final AnimalState male = animal(true, 0.5f, 0L, ADULT_BIRTH, AnimalState.NO_OLD_TICK, 0);
        assertThat(ProductRules.milk(COW, LivestockSpecies.COW, male, fresh, 28_800L)).isEmpty();

        final AnimalState old = animal(false, 0.5f, 0L, ADULT_BIRTH, 1_000L, 0);
        assertThat(ProductRules.hasProduct(COW, LivestockSpecies.COW, old, fresh, 28_800L)).isFalse();
        assertThat(ProductRules.milk(COW, LivestockSpecies.COW, old, fresh, 28_800L)).isEmpty();
    }

    /**
     * {@code ProducingMammal.isReadyForAnimalProduct}: {@code getFamiliarity() > produceFamiliarity.get()}. The float
     * familiarity 0.15f is widened to double 0.15000000596..., which is above the double 0.15, so it is ready. A familiarity
     * of 0.14f is not. The read is at the anchor tick, so no decay applies.
     */
    @Test
    void familiarityThresholdComparesFloatAsDouble() {
        final ProductState fresh = ProductState.fresh();
        final AnimalState exact = animal(false, 0.15f, 28_800L, ADULT_BIRTH, AnimalState.NO_OLD_TICK, 0);
        assertThat(ProductRules.isReady(COW, LivestockSpecies.COW, exact, fresh, 28_800L)).isTrue();

        final AnimalState below = animal(false, 0.14f, 28_800L, ADULT_BIRTH, AnimalState.NO_OLD_TICK, 0);
        assertThat(ProductRules.isReady(COW, LivestockSpecies.COW, below, fresh, 28_800L)).isFalse();
    }

    /**
     * {@code WoolyAnimal.hasProduct} is {@code producedTick <= 0 || cooldown <= 0}. A never-sheared sheep has
     * {@code producedTick == 0}, so she has wool at tick 0 even though her cooldown is 115200. A cow with the same
     * tick has none, because {@code DairyAnimal} has no such shortcut.
     */
    @Test
    void neverShornSheepHasWoolWhileNeverMilkedCowHasNoMilk() {
        final AnimalState ewe = adultFemale();
        final ProductState fresh = ProductState.fresh();
        assertThat(ProductRules.cooldownRemaining(SHEEP, fresh, 0L)).isEqualTo(115_200L);
        assertThat(ProductRules.hasProduct(SHEEP, LivestockSpecies.SHEEP, ewe, fresh, 0L)).isTrue();
        assertThat(ProductRules.shear(SHEEP, LivestockSpecies.SHEEP, ewe, fresh, 0L)).isPresent();

        assertThat(ProductRules.hasProduct(COW, LivestockSpecies.COW, ewe, fresh, 0L)).isFalse();
    }

    /**
     * {@code WoolyAnimal.onSheared} sets the cooldown to now, then adds one use. Regrowth is
     * {@code producedTick + 115200 <= now} for a sheep. Shearing at 1000 makes her ready at 116200, not at 116199.
     * {@code isShearable} is {@code isReadyForAnimalProduct}, so a second shear at 2000 is refused.
     */
    @Test
    void shornWoolRegrowsAfterTheSheepCooldown() {
        final AnimalState sheep = adultFemale();
        final ProductState fresh = ProductState.fresh();

        final ProductRules.Shearing first = ProductRules.shear(SHEEP, LivestockSpecies.SHEEP, sheep, fresh, 1_000L).orElseThrow();
        assertThat(first.product().producedTick()).isEqualTo(1_000L);
        assertThat(first.animal().uses()).isEqualTo(1);
        assertThat(first.wool()).isEqualTo(1);

        assertThat(ProductRules.shear(SHEEP, LivestockSpecies.SHEEP, sheep, first.product(), 2_000L)).isEmpty();
        assertThat(ProductRules.cooldownRemaining(SHEEP, first.product(), 116_199L)).isEqualTo(1L);
        assertThat(ProductRules.shear(SHEEP, LivestockSpecies.SHEEP, sheep, first.product(), 116_199L)).isEmpty();
        assertThat(ProductRules.shear(SHEEP, LivestockSpecies.SHEEP, sheep, first.product(), 116_200L)).isPresent();
    }

    /**
     * {@code WoolyAnimal.getWoolItem}: {@code familiarity > 0.99f ? 2 : 1}. Strict comparison, so exactly 0.99f gives one.
     */
    @Test
    void woolAmountIsTwoOnlyAboveNinetyNinePercentFamiliarity() {
        assertThat(ProductRules.woolAmount(0.995f)).isEqualTo(2);
        assertThat(ProductRules.woolAmount(1.0f)).isEqualTo(2);
        assertThat(ProductRules.woolAmount(0.99f)).isEqualTo(1);
        assertThat(ProductRules.woolAmount(0.5f)).isEqualTo(1);
    }

    /**
     * The wool count is read at the shearing tick. A tame sheep at 0.995 familiarity gives two.
     */
    @Test
    void tameShearingGivesTwoWool() {
        final AnimalState tame = animal(true, 0.995f, 0L, ADULT_BIRTH, AnimalState.NO_OLD_TICK, 0);
        final ProductRules.Shearing shearing = ProductRules.shear(SHEEP, LivestockSpecies.SHEEP, tame,
            ProductState.fresh(), 0L).orElseThrow();
        assertThat(shearing.wool()).isEqualTo(2);
    }

    /**
     * {@code WoolyAnimal.isReadyForAnimalProduct} adds {@code getAgeType() == ADULT}. A lamb born at 0 is a child until
     * its adult tick {@code 0 + 56 * 28800 = 1612800}, and is an adult from 1612801. Shearing is refused before that.
     */
    @Test
    void lambCannotBeShornUntilAdult() {
        final AnimalState lamb = animal(false, 0.5f, 0L, 0L, AnimalState.NO_OLD_TICK, 0);
        assertThat(ProductRules.shear(SHEEP, LivestockSpecies.SHEEP, lamb, ProductState.fresh(), 100L)).isEmpty();
        assertThat(ProductRules.shear(SHEEP, LivestockSpecies.SHEEP, lamb, ProductState.fresh(), 1_612_800L)).isEmpty();
        assertThat(ProductRules.shear(SHEEP, LivestockSpecies.SHEEP, lamb, ProductState.fresh(), 1_612_801L)).isPresent();
    }

    /**
     * {@code OviparousAnimal.hasProduct}: female, adult, and {@code producedTick <= 0 || cooldown <= 0}. A chicken that
     * laid at 150 has cooldown {@code 150 + 36000 - now} (her {@code produceTicks} is 30h = 36000), so she is ready again
     * at 36150.
     */
    @Test
    void chickenCooldownUsesOviparousHasProduct() {
        final AnimalState hen = adultFemale();
        final ProductState laid = new ProductState(150L);
        assertThat(ProductRules.cooldownRemaining(CHICKEN, laid, 36_149L)).isEqualTo(1L);
        assertThat(ProductRules.hasProduct(CHICKEN, LivestockSpecies.CHICKEN, hen, laid, 36_149L)).isFalse();
        assertThat(ProductRules.hasProduct(CHICKEN, LivestockSpecies.CHICKEN, hen, laid, 36_150L)).isTrue();
    }
}
