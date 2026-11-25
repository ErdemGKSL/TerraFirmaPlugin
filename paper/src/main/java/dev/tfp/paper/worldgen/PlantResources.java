/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.worldgen;

import dev.tfp.core.worldgen.plant.PlantData;
import dev.tfp.core.worldgen.plant.PlantType;
import dev.tfp.paper.data.RegistryService;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

/**
 * The plant, wild crop and coral features the generator places (M2-GEN-13), read from the datagen output shipped in the jar
 * ({@code content/data/tfp/worldgen/{placed,configured}_feature/...} and the placed feature and block tags).
 */
final class PlantResources {
    /**
     * The plant block types {@link PlantBlocks} has blocks for. PAR-FARM-29 adds the columns, kelp, giant kelp, saguaro and
     * perched epiphytes (vanilla placeholders until the terrain places CraftEngine blocks, PAR-WORLD-46); the features of the
     * creeping plants, epiphytes and vines stay out (their faces need block data the placeholder cannot carry).
     */
    static final Set<PlantType> WRITTEN_TYPES = EnumSet.of(PlantType.STANDARD, PlantType.SHORT_SHRUB, PlantType.SHRUB,
        PlantType.FLOWERBED, PlantType.CACTUSBED, PlantType.PASSABLE_CACTUS, PlantType.DRY, PlantType.SHORT_GRASS,
        PlantType.BEACH_GRASS, PlantType.TALL_GRASS, PlantType.TALL_SHRUB, PlantType.FLOATING, PlantType.FLOATING_FRESH,
        PlantType.WATER, PlantType.WATER_FRESH, PlantType.GRASS_WATER, PlantType.GRASS_WATER_FRESH, PlantType.TALL_WATER,
        PlantType.TALL_WATER_FRESH, PlantType.WEEPING, PlantType.WEEPING_TOP, PlantType.TWISTING, PlantType.TWISTING_TOP,
        PlantType.TWISTING_TOP_SHORT, PlantType.TWISTING_SOLID, PlantType.TWISTING_SOLID_TOP, PlantType.KELP,
        PlantType.KELP_TOP, PlantType.KELP_TREE, PlantType.KELP_TREE_FLOWER, PlantType.BRANCHING_CACTUS,
        PlantType.BRANCHING_CACTUS_TOP, PlantType.PERCHED_EPIPHYTE);

    private PlantResources() {}

    /** The plant data of the plugin jar. */
    static PlantData fromJar(ClassLoader loader) {
        return PlantData.of(path -> {
            try (InputStream in = loader.getResourceAsStream(RegistryService.JAR_DATA_ROOT + "/" + path)) {
                return in == null ? Optional.empty() : Optional.of(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new UncheckedIOException("cannot read " + path + " from the plugin jar", e);
            }
        }, WRITTEN_TYPES);
    }
}
