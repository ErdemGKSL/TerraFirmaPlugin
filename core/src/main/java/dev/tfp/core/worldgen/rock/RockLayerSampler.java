/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/world/settings/RockLayerSettings.java @ c3b4d9536 (decode, processTopLevel,
 * sampleAtLayer, sampler)
 */
package dev.tfp.core.worldgen.rock;

import dev.tfp.core.worldgen.random.XoroshiroRandomSource;
import dev.tfp.core.worldgen.settings.RockLayerConfig;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * TFC's rock layer walk (PAR-WORLD-23): the layer graph of a {@link RockLayerConfig}, built and validated as TFC's
 * {@code RockLayerSettings.decode}, and {@code sampleAtLayer}. The rocks are the config's rock ids ({@code tfc:granite});
 * {@link RockSettings} maps them to blocks. Immutable and thread-safe.
 */
public final class RockLayerSampler {
    /** The supertype bits of a region rock id ({@code ChooseRocks.TYPE_BITS}). */
    private static final int TYPE_BITS = 2;
    private static final int TYPE_MASK = (1 << TYPE_BITS) - 1;

    /** TFC's overworld preset. */
    public static final RockLayerSampler PRESET = decode(RockLayerConfig.PRESET);

    /** A layer entry: its rock id and the entries of the layer below it. */
    private record Layer(String rock, List<Layer> next) {}

    private final List<Layer> oceanFloor;
    private final List<Layer> land;
    private final List<Layer> volcanic;
    private final List<Layer> uplift;
    private final List<String> rocks;

    private RockLayerSampler(List<Layer> oceanFloor, List<Layer> land, List<Layer> volcanic, List<Layer> uplift, List<String> rocks) {
        this.oceanFloor = oceanFloor;
        this.land = land;
        this.volcanic = volcanic;
        this.uplift = uplift;
        this.rocks = rocks;
    }

    /**
     * TFC's {@code decode}: the bottom layer (each rock's next layer is the bottom layer itself), then the named layers in
     * file order (a layer may only name layers before it, so the graph has no cycles), then the four top-level lists.
     *
     * @throws IllegalArgumentException with TFC's message: {@code No rock with id: X}, {@code More than one layer named X},
     *                                  {@code No layer with id: X}; or {@code Empty layer: X} for a layer with no rocks,
     *                                  which TFC accepts and then fails on at the first sample
     */
    public static RockLayerSampler decode(RockLayerConfig data) {
        final List<Layer> bottom = new ArrayList<>();
        for (String id : data.bottom()) {
            final String rock = data.rocks().get(id);
            if (rock == null) {
                throw new IllegalArgumentException("No rock with id: " + id);
            }
            bottom.add(new Layer(rock, bottom));
        }
        final Map<String, List<Layer>> layers = new HashMap<>();
        layers.put("bottom", bottom);
        for (RockLayerConfig.Layer layer : data.layers()) {
            final List<Layer> baked = new ArrayList<>();
            if (layers.containsKey(layer.id())) {
                throw new IllegalArgumentException("More than one layer named " + layer.id());
            }
            for (Map.Entry<String, String> entry : layer.layers().entrySet()) {
                final List<Layer> next = layers.get(entry.getValue());
                if (next == null) {
                    throw new IllegalArgumentException("No layer with id: " + entry.getValue());
                }
                final String rock = data.rocks().get(entry.getKey());
                if (rock == null) {
                    throw new IllegalArgumentException("No rock with id: " + entry.getKey());
                }
                baked.add(new Layer(rock, next));
            }
            layers.put(layer.id(), baked);
        }
        for (Map.Entry<String, List<Layer>> e : layers.entrySet()) {
            if (e.getValue().isEmpty()) {
                throw new IllegalArgumentException("Empty layer: " + e.getKey());
            }
        }
        return new RockLayerSampler(top(layers, data.oceanFloor()), top(layers, data.land()), top(layers, data.volcanic()),
            top(layers, data.uplift()), List.copyOf(data.rocks().values()));
    }

    private static List<Layer> top(Map<String, List<Layer>> layers, List<String> entries) {
        final List<Layer> baked = new ArrayList<>();
        for (String id : entries) {
            final List<Layer> layer = layers.get(id);
            if (layer == null) {
                throw new IllegalArgumentException("No layer with id: " + id);
            }
            baked.addAll(layer);
        }
        if (baked.isEmpty()) {
            throw new IllegalArgumentException("Empty layer: " + entries);
        }
        return List.copyOf(baked);
    }

    /** The rock ids of the config, in its order. */
    public List<String> rocks() {
        return rocks;
    }

    /**
     * TFC's {@code sampleAtLayer}: the rock id {@code layerN} layers below the surface at a region rock id. The draws are
     * TFC's: {@code new XoroshiroRandomSource(pointRock >> 2)}, one {@code nextInt} for the top layer, one per layer down.
     */
    public String sampleAtLayer(int pointRock, int layerN) {
        final XoroshiroRandomSource source = new XoroshiroRandomSource(pointRock >> TYPE_BITS);
        final List<Layer> rootLayers = switch (pointRock & TYPE_MASK) {
            case 0 -> oceanFloor;
            case 1 -> volcanic;
            case 2 -> land;
            default -> uplift;
        };
        Layer layer = rootLayers.get(source.nextInt(rootLayers.size()));
        for (int i = 0; i < layerN; i++) {
            layer = layer.next().get(source.nextInt(layer.next().size()));
        }
        return layer.rock();
    }
}
