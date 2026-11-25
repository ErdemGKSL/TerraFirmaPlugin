/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.animal.sound;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** PAR-FAUNA-20: the shipped event list and the species to event mapping. */
class AnimalSoundsTest {
    private static Set<String> events() throws Exception {
        Path f = Path.of(System.getProperty("tfp.contentData"), "tfp", "data_maps", "animal_sounds.json");
        Set<String> out = new HashSet<>();
        for (JsonElement e : JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonArray()) {
            out.add(e.getAsString());
        }
        return out;
    }

    @Test
    void tfcEventsMapBySpeciesAndBearsShareOne() throws Exception {
        Set<String> events = events();
        assertThat(events).hasSizeGreaterThan(100).contains("entity.alpaca.ambient", "entity.bear.hurt");
        assertThat(AnimalSoundsModule.event(events, "tfc:alpaca", "ambient")).contains("entity.alpaca.ambient");
        assertThat(AnimalSoundsModule.event(events, "tfc:grizzly_bear", "hurt")).contains("entity.bear.hurt");
        assertThat(AnimalSoundsModule.event(events, "tfc:cow", "ambient")).isEmpty(); // TFC uses the vanilla cow voice
    }

    @Test
    void theGameBirdsAndPoultryPlayTheirTfcVoicesAndTheChickenItsCarriers() throws Exception {
        Set<String> events = events();
        for (String bird : new String[] {"duck", "quail", "pheasant", "grouse", "turkey", "peafowl"}) {
            for (String kind : new String[] {"ambient", "hurt", "death"}) {
                assertThat(AnimalSoundsModule.event(events, "tfc:" + bird, kind)).as(bird + " " + kind)
                    .contains("entity." + bird + "." + kind);
            }
        }
        assertThat(AnimalSoundsModule.event(events, "tfc:chicken", "hurt")).isEmpty(); // the vanilla chicken voice
    }
}
