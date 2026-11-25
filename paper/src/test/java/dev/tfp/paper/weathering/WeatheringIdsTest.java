/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.weathering;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.weathering.WeatheringRules.Age;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** PAR-METAL-17: which CraftEngine ids weather, and what each becomes. */
class WeatheringIdsTest {
    @Test
    void theThreeShapesOfTheSevenMetalsWeather() {
        for (String metal : new String[] {"copper", "bronze", "brass", "silver", "sterling_silver", "steel", "wrought_iron"}) {
            for (String shape : new String[] {"", "_slab", "_stairs"}) {
                var kind = WeatheringIds.parse("tfp:metal/block/" + metal + shape);
                assertThat(kind).as(metal + shape).isPresent();
                assertThat(kind.get().age()).isEqualTo(Age.NONE);
                assertThat(kind.get().next()).contains("tfp:metal/exposed_block/" + metal + shape);
            }
        }
    }

    @Test
    void theChainOfStagesEndsAtOxidized() {
        var kind = WeatheringIds.parse("tfp:metal/weathered_block/sterling_silver_stairs").orElseThrow();
        assertThat(kind.age()).isEqualTo(Age.WEATHERED);
        assertThat(kind.resistance()).isEqualTo(0.95f);
        assertThat(kind.next()).contains("tfp:metal/oxidized_block/sterling_silver_stairs");
        assertThat(WeatheringIds.parse("tfp:metal/oxidized_block/sterling_silver_stairs").orElseThrow().next()).isEmpty();
    }

    @Test
    void metalsThatDoNotWeatherAndOtherBlocksAreIgnored() {
        for (String id : new String[] {"tfp:metal/block/bismuth_bronze", "tfp:metal/block/black_steel_slab",
            "tfp:metal/grate/copper", "tfp:metal/trapdoor/copper", "tfp:metal/block/gold", "tfp:metal/block/nothing",
            "tfp:wood/planks/oak", "tfp:metal/block", "minecraft:stone"}) {
            assertThat(WeatheringIds.parse(id)).as(id).isEqualTo(Optional.empty());
        }
    }
}
