/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.livestock;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.animal.Carrier;
import dev.tfp.core.animal.CarrierTable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/** PAR-FAUNA-05: every livestock species has a food tag in the shipped data, and the behaviour serves all of them. */
class LivestockModuleTest {
    @Test
    void everyLivestockSpeciesHasAFoodTagFile() {
        Path tags = Path.of(System.getProperty("tfp.contentData"), "tfp", "tags", "item");
        List<Carrier> livestock = CarrierTable.get().all().stream().filter(c -> c.livestock().isPresent()).toList();
        assertThat(livestock).hasSize(18);
        for (Carrier c : livestock) {
            String tag = LivestockModule.foodTag(c);
            assertThat(tag).startsWith("tfp:").endsWith("_food");
            assertThat(Files.exists(tags.resolve(tag.substring(4) + ".json"))).as(tag).isTrue();
        }
    }

    @Test
    void bothCamelsShareOneFoodTag() {
        CarrierTable t = CarrierTable.get();
        assertThat(LivestockModule.foodTag(t.find("tfc:bactrian_camel").orElseThrow())).isEqualTo("tfp:camel_food");
        assertThat(LivestockModule.foodTag(t.find("tfc:dromedary_camel").orElseThrow())).isEqualTo("tfp:camel_food");
        assertThat(LivestockModule.foodTag(t.find("tfc:cow").orElseThrow())).isEqualTo("tfp:cow_food");
    }
}
