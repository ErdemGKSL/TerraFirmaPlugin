/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.worldgen;

import dev.tfp.core.worldgen.plant.PlantData;
import dev.tfp.paper.data.RegistryService;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * The plant, wild crop and coral features the generator places (M2-GEN-13), read from the datagen output shipped in the jar
 * ({@code content/data/tfp/worldgen/{placed,configured}_feature/...} and the placed feature and block tags).
 */
final class PlantResources {
    private PlantResources() {}

    /** The plant data of the plugin jar. */
    static PlantData fromJar(ClassLoader loader) {
        return PlantData.of(path -> {
            try (InputStream in = loader.getResourceAsStream(RegistryService.JAR_DATA_ROOT + "/" + path)) {
                return in == null ? Optional.empty() : Optional.of(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new UncheckedIOException("cannot read " + path + " from the plugin jar", e);
            }
        });
    }
}
