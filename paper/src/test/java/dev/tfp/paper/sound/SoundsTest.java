/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.sound;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** PAR-DATA-08: every key of the {@link Sounds} facade is an event the generated pack ships. */
class SoundsTest {
    private static Set<String> events() throws Exception {
        Path f = Path.of(System.getProperty("tfp.contentData"), "tfp", "data_maps", "sounds.json");
        Set<String> out = new HashSet<>();
        for (JsonElement e : JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonArray()) {
            out.add("tfp:" + e.getAsString());
        }
        return out;
    }

    @Test
    void everyFacadeKeyIsAShippedEvent() throws Exception {
        Set<String> events = events();
        int keys = 0;
        for (Field f : Sounds.class.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers()) && f.getType() == String.class && !f.getName().equals("NAMESPACE")) {
                keys++;
                assertThat(events).as(f.getName()).contains((String) f.get(null));
            }
        }
        assertThat(keys).isGreaterThan(30);
    }

    @Test
    void theKnappingEventsMatchTheKnappingMenu() throws Exception {
        assertThat(events()).contains("tfp:item.knapping.stone", "tfp:item.knapping.clay", "tfp:item.knapping.leather");
    }
}
