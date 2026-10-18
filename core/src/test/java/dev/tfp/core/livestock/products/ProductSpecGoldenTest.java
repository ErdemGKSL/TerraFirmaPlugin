/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.livestock.products;

import dev.tfp.core.livestock.LivestockSpecies;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static dev.tfp.core.livestock.LivestockSpecies.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The produce defaults, golden against {@code ServerConfig.java} at c3b4d9536. Each produce time is
 * {@code hours * ICalendar.PLAYER_TICKS_IN_DEFAULT_HOUR} (60 * 20 = 1200), and every producing species has the default
 * {@code MinProduceFamiliarity} 0.15. The kind follows the TFC base class: cow, goat and yak are {@code DairyAnimal};
 * alpaca, sheep and muskOx are {@code WoolyAnimal}; the bactrian camel shears like one; chicken, duck and quail are
 * {@code OviparousAnimal}.
 */
class ProductSpecGoldenTest {
    @Test
    void milkSpeciesUseTheirConfiguredCooldowns() {
        // ServerConfig: cowConfig 24h, goatConfig 72h, yakConfig 24h
        assertThat(ProductSpec.defaults(COW)).contains(new ProductSpec(ProductKind.MILK, 28_800, 0.15));
        assertThat(ProductSpec.defaults(GOAT)).contains(new ProductSpec(ProductKind.MILK, 86_400, 0.15));
        assertThat(ProductSpec.defaults(YAK)).contains(new ProductSpec(ProductKind.MILK, 28_800, 0.15));
    }

    @Test
    void woolSpeciesUseTheirConfiguredCooldowns() {
        // ServerConfig: alpacaConfig 120h, sheepConfig 96h, muskOxConfig 96h, bactrianCamelConfig 168h
        assertThat(ProductSpec.defaults(ALPACA)).contains(new ProductSpec(ProductKind.SHEAR, 144_000, 0.15));
        assertThat(ProductSpec.defaults(SHEEP)).contains(new ProductSpec(ProductKind.SHEAR, 115_200, 0.15));
        assertThat(ProductSpec.defaults(MUSK_OX)).contains(new ProductSpec(ProductKind.SHEAR, 115_200, 0.15));
        assertThat(ProductSpec.defaults(BACTRIAN_CAMEL)).contains(new ProductSpec(ProductKind.SHEAR, 201_600, 0.15));
    }

    @Test
    void eggSpeciesUseTheirConfiguredCooldowns() {
        // ServerConfig: chickenConfig 30h, duckConfig 32h, quailConfig 28h
        assertThat(ProductSpec.defaults(CHICKEN)).contains(new ProductSpec(ProductKind.EGG, 36_000, 0.15));
        assertThat(ProductSpec.defaults(DUCK)).contains(new ProductSpec(ProductKind.EGG, 38_400, 0.15));
        assertThat(ProductSpec.defaults(QUAIL)).contains(new ProductSpec(ProductKind.EGG, 33_600, 0.15));
    }

    @Test
    void exactlyTenSpeciesProduceAndTheRestDoNot() {
        final long producing = Arrays.stream(LivestockSpecies.values())
            .filter(s -> ProductSpec.defaults(s).isPresent())
            .count();
        assertThat(producing).isEqualTo(10);
        // mounts, pets, pig and cat have no product in TFC
        assertThat(ProductSpec.defaults(PIG)).isEmpty();
        assertThat(ProductSpec.defaults(DROMEDARY_CAMEL)).isEmpty();
        assertThat(ProductSpec.defaults(DOG)).isEmpty();
    }

    @Test
    void rejectsNegativeCooldown() {
        assertThatThrownBy(() -> new ProductSpec(ProductKind.MILK, -1, 0.15)).isInstanceOf(IllegalArgumentException.class);
    }
}
