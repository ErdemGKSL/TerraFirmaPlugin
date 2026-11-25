/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.sapling;

import dev.tfp.core.worldgen.tree.TreeData;
import dev.tfp.core.worldgen.tree.TreeFeature;
import dev.tfp.core.worldgen.tree.Wood;
import dev.tfp.paper.data.RegistryService;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The tree feature a wood's sapling grows ({@code TreeGrower}'s {@code tfc:tree/<wood>}: the normal tree of the wood's forest
 * entry), read once from the datagen output in the plugin jar. Any thread.
 */
public final class SaplingFeatures {
    private final ClassLoader loader;
    private final Map<Wood, TreeFeature> cache = new ConcurrentHashMap<>();

    public SaplingFeatures(ClassLoader loader) {
        this.loader = loader;
    }

    /** The feature of a wood. */
    public TreeFeature of(Wood wood) {
        return cache.computeIfAbsent(wood, w -> TreeData.entry(this::read, "tfp:tree/" + w.serializedName() + "_entry").normalTree());
    }

    private Optional<String> read(String path) {
        try (InputStream in = loader.getResourceAsStream(RegistryService.JAR_DATA_ROOT + "/" + path)) {
            return in == null ? Optional.empty() : Optional.of(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + path + " from the plugin jar", e);
        }
    }
}
