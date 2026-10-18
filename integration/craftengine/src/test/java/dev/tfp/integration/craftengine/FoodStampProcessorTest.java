/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.integration.craftengine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.tfp.core.clock.GlobalClockHolder;
import dev.tfp.core.food.FoodTrait;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** M3-SURV-01: the {@code tfp:food_stamp} processor's config parsing and registration (no server). */
class FoodStampProcessorTest {
    @Test
    void parsesEmptyAndTraits() {
        assertThat(FoodStampProcessor.parseTraits(null)).isEmpty();
        assertThat(FoodStampProcessor.parseTraits(Map.of())).isEmpty();
        assertThat(FoodStampProcessor.parseTraits(Map.of("traits", List.of("tfp:wild", "tfp:salted"))))
                .containsExactly(FoodTrait.WILD, FoodTrait.SALTED);
        // unknown ids survive as neutral traits (FoodPdc round trip)
        assertThat(FoodStampProcessor.parseTraits(Map.of("traits", List.of("other:smoky"))))
                .containsExactly(new FoodTrait("other:smoky", 1f));
    }

    @Test
    void rejectsMalformedConfig() {
        assertThatThrownBy(() -> FoodStampProcessor.parseTraits(true)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> FoodStampProcessor.parseTraits(Map.of("traits", "tfp:wild")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void isNonConstantAndRegisteredUnderItsPath() {
        assertThat(new FoodStampProcessor(new GlobalClockHolder(), List.of()).isConstant()).isFalse();
        CraftEngineBehaviours b = new CraftEngineBehaviours()
                .itemProcessor(FoodStampProcessor.PATH, FoodStampProcessor.factory(new GlobalClockHolder()));
        assertThat(b.itemProcessorPaths()).containsExactly("food_stamp");
        assertThatThrownBy(() -> b.itemProcessor(FoodStampProcessor.PATH, FoodStampProcessor.factory(new GlobalClockHolder())))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
