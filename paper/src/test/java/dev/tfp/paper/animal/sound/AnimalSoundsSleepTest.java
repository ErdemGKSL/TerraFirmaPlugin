/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.animal.sound;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import dev.tfp.paper.testing.MockServerTest;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import org.bukkit.Location;
import org.bukkit.entity.Cow;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;

/**
 * PAR-FAUNA-29: a sleeping predator or pet plays TFC's {@code entity.<sound>.sleep} instead of the plain ambient sound
 * ({@code Predator.getAmbientSound}, {@code TamableMammal.getAmbientSound}).
 */
class AnimalSoundsSleepTest extends MockServerTest {
    private static Set<String> events() throws Exception {
        Path f = Path.of(System.getProperty("tfp.contentData"), "tfp", "data_maps", "animal_sounds.json");
        Set<String> out = new HashSet<>();
        for (JsonElement e : JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonArray()) {
            out.add(e.getAsString());
        }
        return out;
    }

    @Test
    void theSleepEventOfEachPredatorAndPetIsFoundByItsTfcSoundId() throws Exception {
        Set<String> events = events();
        assertThat(AnimalSoundsModule.event(events, "tfc:grizzly_bear", "sleep")).contains("entity.bear.sleep");
        assertThat(AnimalSoundsModule.event(events, "tfc:polar_bear", "sleep")).contains("entity.bear.sleep");
        assertThat(AnimalSoundsModule.event(events, "tfc:wolf", "sleep")).contains("entity.tfc_wolf.sleep");
        assertThat(AnimalSoundsModule.event(events, "tfc:direwolf", "sleep")).contains("entity.dog.sleep");
        assertThat(AnimalSoundsModule.event(events, "tfc:hyena", "sleep")).contains("entity.hyena.sleep");
        assertThat(AnimalSoundsModule.event(events, "tfc:lion", "sleep")).contains("entity.lion.sleep");
        assertThat(AnimalSoundsModule.event(events, "tfc:dog", "sleep")).contains("entity.dog.sleep");
        // a species without a sleep sound has none (the plain ambient is used)
        assertThat(AnimalSoundsModule.event(events, "tfc:deer", "sleep")).isEmpty();
    }

    @Test
    void theKindIsSleepWhileThePredatorOrPetFlagSaysAsleep() {
        var world = server.addSimpleWorld("w");
        Cow cow = world.spawn(new Location(world, 0, 64, 0), Cow.class);
        var pdc = cow.getPersistentDataContainer();
        assertThat(AnimalSoundsModule.ambientKind(cow)).isEqualTo("ambient");

        pdc.set(AnimalSoundsModule.PREDATOR_SLEEPING, PersistentDataType.BYTE, (byte) 1);
        assertThat(AnimalSoundsModule.ambientKind(cow)).isEqualTo("sleep");
        pdc.remove(AnimalSoundsModule.PREDATOR_SLEEPING);

        // petFlags: sleeping is bit 1, sitting bit 4 (a sitting pet still makes its ambient sound)
        pdc.set(AnimalSoundsModule.PET_FLAGS, PersistentDataType.BYTE, (byte) 4);
        assertThat(AnimalSoundsModule.ambientKind(cow)).isEqualTo("ambient");
        pdc.set(AnimalSoundsModule.PET_FLAGS, PersistentDataType.BYTE, (byte) 5);
        assertThat(AnimalSoundsModule.ambientKind(cow)).isEqualTo("sleep");
        pdc.set(AnimalSoundsModule.PET_FLAGS, PersistentDataType.BYTE, (byte) 1);
        assertThat(AnimalSoundsModule.ambientKind(cow)).isEqualTo("sleep");
    }
}
