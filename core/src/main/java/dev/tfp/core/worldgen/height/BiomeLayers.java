/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/world/layer/TFCLayers.java @ c3b4d9536 (createRegionBiomeLayer, isMountains, isLow,
 * isFlats, hasShore, shoreFor), with src/main/java/net/dries007/tfc/world/layer/RegionLayer.java, RegionBiomeLayer.java,
 * RegionEdgeBiomeLayer.java, ShoreAndRiverLayer.java, MoreShoresLayer.java, IceSheetEdgeLayer.java, and
 * src/main/java/net/dries007/tfc/world/biome/RegionBiomeSource.java (getBiomeExtensionNoRiver)
 */
package dev.tfp.core.worldgen.height;

import static dev.tfp.core.worldgen.biome.TfcBiomes.*;

import dev.tfp.core.worldgen.Seed;
import dev.tfp.core.worldgen.biome.TfcBiomes;
import dev.tfp.core.worldgen.layer.AdjacentTransformLayer;
import dev.tfp.core.worldgen.layer.Area;
import dev.tfp.core.worldgen.layer.AreaContext;
import dev.tfp.core.worldgen.layer.AreaFactory;
import dev.tfp.core.worldgen.layer.SmoothLayer;
import dev.tfp.core.worldgen.layer.TypedArea;
import dev.tfp.core.worldgen.layer.TypedAreaFactory;
import dev.tfp.core.worldgen.layer.TypedSourceLayer;
import dev.tfp.core.worldgen.layer.ZoomLayer;
import dev.tfp.core.worldgen.region.Region;
import dev.tfp.core.worldgen.region.RegionGenerator;
import java.util.function.IntPredicate;
import java.util.function.Predicate;

/**
 * TFC's biome layer: the region biomes at grid scale, their edges, zoomed to quart scale (4 blocks) with shores, ice sheet
 * edges and a final smoothing. This is the biome TFC's height filler weighs ({@code getBiomeExtensionNoRiver}): no river
 * biome. Thread-safe: each thread has its own layer caches.
 */
public final class BiomeLayers {
    private final ThreadLocal<Area> area;

    /**
     * Takes eleven seeds from {@code seed}, as TFC's {@code createRegionBiomeLayer} does right after the region generator and
     * its chunk data generator took theirs.
     */
    public BiomeLayers(RegionGenerator generator, Seed seed) {
        final AreaFactory factory = createRegionBiomeLayer(generator, seed);
        this.area = ThreadLocal.withInitial(factory);
    }

    /** @return the TFC biome id at a quart position, without rivers. */
    public int biomeNoRiverAtQuart(int quartX, int quartZ) {
        return area.get().get(quartX, quartZ);
    }

    /** @return the TFC biome id of the quart that holds a block column, without rivers ({@code QuartPos.fromBlock}). */
    public int biomeNoRiver(int blockX, int blockZ) {
        return biomeNoRiverAtQuart(blockX >> 2, blockZ >> 2);
    }

    static AreaFactory createRegionBiomeLayer(RegionGenerator generator, Seed seed) {
        final TypedAreaFactory<Region.Point> regionLayer = new RegionLayer(generator).apply(seed.next());

        AreaFactory mainLayer;

        mainLayer = regionBiomeLayer(regionLayer);

        // Grid scale

        mainLayer = RegionEdgeBiomeLayer.INSTANCE.apply(seed.next(), mainLayer);
        mainLayer = ZoomLayer.NORMAL.apply(seed.next(), mainLayer);

        // 4x4 Chunk Scale
        mainLayer = ShoreAndRiverLayer.INSTANCE.apply(seed.next(), mainLayer);
        mainLayer = MoreShoresLayer.INSTANCE.apply(seed.next(), mainLayer);
        mainLayer = IceSheetEdgeLayer.INSTANCE.apply(seed.next(), mainLayer);
        mainLayer = ZoomLayer.NORMAL.apply(seed.next(), mainLayer);
        mainLayer = ZoomLayer.NORMAL.apply(seed.next(), mainLayer);

        // Chunk scale

        mainLayer = ZoomLayer.NORMAL.apply(seed.next(), mainLayer);
        mainLayer = ZoomLayer.NORMAL.apply(seed.next(), mainLayer);

        // Quart scale

        mainLayer = SmoothLayer.INSTANCE.apply(seed.next(), mainLayer);

        return mainLayer;
    }

    /** {@code RegionLayer}: the region point at each grid coordinate. */
    private record RegionLayer(RegionGenerator generator) implements TypedSourceLayer<Region.Point> {
        @Override
        public Region.Point apply(AreaContext context, int x, int z) {
            return generator.getOrCreateRegionPoint(x, z);
        }
    }

    /** {@code RegionBiomeLayer}: the biome of each region point. */
    private static AreaFactory regionBiomeLayer(TypedAreaFactory<Region.Point> prev) {
        return () -> {
            final TypedArea<Region.Point> prevArea = prev.get();
            return new Area((x, z) -> prevArea.get(x, z).biome, 1024);
        };
    }

    enum RegionEdgeBiomeLayer implements AdjacentTransformLayer {
        INSTANCE;

        @Override
        public int apply(AreaContext context, int north, int east, int south, int west, int center) {
            final Predicate<IntPredicate> matcher = p -> p.test(north) || p.test(east) || p.test(south) || p.test(west);

            // >= 2 Adjacent border conditions
            if (isLow(center)) {
                if (matcher.test(TfcBiomes::isOcean) && matcher.test(BiomeLayers::isMountains)) {
                    return OCEANIC_MOUNTAINS;
                } else if (matcher.test(TfcBiomes::isOcean) && matcher.test(i -> i == LOWLANDS)) {
                    return SALT_MARSH;
                }
            }

            // No mud/salt flats near oceans
            if (isFlats(center)) {
                if (matcher.test(TfcBiomes::isOcean) && matcher.test(BiomeLayers::isFlats)) {
                    return CANYONS;
                }
            }

            if (center == PLATEAU || center == BADLANDS) {
                if (matcher.test(i -> i == LOW_CANYONS || i == LOWLANDS)) {
                    return HILLS;
                } else if (matcher.test(i -> i == PLAINS || i == HILLS)) {
                    return ROLLING_HILLS;
                }
            } else if (isMountains(center)) {
                if (matcher.test(BiomeLayers::isLow)) {
                    return ROLLING_HILLS;
                }
            }
            // Inverses of above conditions
            else if (center == LOWLANDS || center == LOW_CANYONS) {
                if (matcher.test(i -> i == PLATEAU || i == BADLANDS)) {
                    return HILLS;
                } else if (matcher.test(BiomeLayers::isMountains)) {
                    return ROLLING_HILLS;
                }
            } else if (center == PLAINS || center == HILLS) {
                if (matcher.test(i -> i == PLATEAU || i == BADLANDS)) {
                    return HILLS;
                } else if (matcher.test(BiomeLayers::isMountains)) {
                    return ROLLING_HILLS;
                }
            } else if (center == DEEP_OCEAN_TRENCH) {
                if (matcher.test(i -> !TfcBiomes.isOcean(i))) {
                    return OCEAN;
                }
            }
            return center;
        }
    }

    enum ShoreAndRiverLayer implements AdjacentTransformLayer {
        INSTANCE;

        @Override
        public int apply(AreaContext context, int north, int east, int south, int west, int center) {
            Predicate<IntPredicate> matcher = p -> p.test(north) || p.test(east) || p.test(south) || p.test(west);
            if (hasShore(center)) {
                if (matcher.test(TfcBiomes::isOcean)) {
                    return shoreFor(center);
                }
            } else if (center == RIVER_VALLEY) {
                // Make river valleys narrower when they cut mountains
                if (matcher.test(BiomeLayers::isMountains)) {
                    return PLATEAU_WIDE;
                } else if (matcher.test(TfcBiomes::isOcean)) {
                    return SALT_MARSH;
                }
            }
            return center;
        }
    }

    enum MoreShoresLayer implements AdjacentTransformLayer {
        INSTANCE;

        @Override
        public int apply(AreaContext context, int north, int east, int south, int west, int center) {
            Predicate<IntPredicate> matcher = p -> p.test(north) || p.test(east) || p.test(south) || p.test(west);
            if (matcher.test(TfcBiomes::isOcean)) {
                if (matcher.test(layer -> layer == TERRACE_LOWER)) {
                    return TERRACE_UPPER;
                }
                if (matcher.test(layer -> layer == SEA_STACKS)) {
                    return SEA_STACKS;
                }
                if (matcher.test(layer -> layer == TIDAL_FLATS)) {
                    return SHORE;
                }
                if (matcher.test(layer -> layer == COASTAL_DUNES)) {
                    return COASTAL_DUNES;
                }
                if (matcher.test(layer -> layer == SETBACK_CLIFFS)) {
                    return SETBACK_CLIFFS;
                }
                if (matcher.test(layer -> layer == ROCKY_SHORES)) {
                    return ROCKY_SHORES;
                }
                if (matcher.test(layer -> layer == EMBAYMENTS)) {
                    return EMBAYMENTS;
                }
            }
            return center;
        }
    }

    enum IceSheetEdgeLayer implements AdjacentTransformLayer {
        INSTANCE;

        @Override
        public int apply(AreaContext context, int north, int east, int south, int west, int center) {
            final Predicate<IntPredicate> matcher = p -> p.test(north) || p.test(east) || p.test(south) || p.test(west);
            if (center == KNOB_AND_KETTLE || center == PATTERNED_GROUND || center == INVERTED_PATTERNED_GROUND || center == STONE_CIRCLES) {
                if ((matcher.test(i -> i == ICE_SHEET_TUYAS))) {
                    return ICE_SHEET_TUYAS_EDGE;
                } else if ((matcher.test(TfcBiomes::isFlatIceSheet))) {
                    return ICE_SHEET_EDGE;
                }
            }

            // Ice sheet mountain edges
            if (center == ICE_SHEET_OCEANIC_MOUNTAINS || center == ICE_SHEET_VOLCANIC_OCEANIC_MOUNTAINS) {
                if (matcher.test(IceSheetEdgeLayer::isNotIceSheet)) {
                    return ICE_SHEET_OCEANIC_MOUNTAINS_EDGE;
                }
            }
            if (center == ICE_SHEET_MOUNTAINS || center == ICE_SHEET_VOLCANIC_MOUNTAINS) {
                if (matcher.test(IceSheetEdgeLayer::isNotIceSheet)) {
                    return ICE_SHEET_MOUNTAINS_EDGE;
                }
            }

            if (center == ICE_SHEET_EDGE && matcher.test(i -> i == ICE_SHEET_MOUNTAINS || i == ICE_SHEET_MOUNTAINS_EDGE || i == ICE_SHEET_OCEANIC_MOUNTAINS || i == ICE_SHEET_OCEANIC)) {
                return KNOB_AND_KETTLE;
            }

            // Lakes near edges of ice sheets
            if (center == LAKE && matcher.test(TfcBiomes::isFlatIceSheet) && !matcher.test(i -> i == ICE_SHEET_MOUNTAINS || i == ICE_SHEET_MOUNTAINS_EDGE || i == ICE_SHEET_OCEANIC_MOUNTAINS || i == ICE_SHEET_OCEANIC)) {
                return SUBGLACIAL_LAKE;
            }
            if (isFlatIceSheet(center) && matcher.test(i -> i == MELTWATER_LAKE)) {
                if (matcher.test(IceSheetEdgeLayer::isNotIceSheet)) {
                    return SUBGLACIAL_LAKE;
                }
            }

            if (isFlatIceSheet(center) && matcher.test(i -> i == OCEAN || i == OCEAN_REEF || i == DEEP_OCEAN || i == DEEP_OCEAN_TRENCH || i == ICE_SHEET_SHORE)) {
                return ICE_SHEET_OCEANIC;
            }

            // Glaciated mountains should have glacially carved edges to avoid cirque glaciers turning to stone near borders with lower biomes
            if (isNotIceSheetOrGlaciated(center)) {
                if (matcher.test(i -> i == GLACIATED_MOUNTAINS)) {
                    return GLACIALLY_CARVED_MOUNTAINS;
                } else if (matcher.test(i -> i == GLACIATED_OCEANIC_MOUNTAINS)) {
                    return GLACIALLY_CARVED_OCEANIC_MOUNTAINS;
                }
            }

            // Prevent borders between ice sheet oceanic mountain edges that could cause icy-cliffs
            if (center == PLATEAU || center == BADLANDS || center == BURREN_BADLANDS || center == BURREN_BADLANDS_TALL || center == GLACIATED_SHIELD_VOLCANO) {
                if (matcher.test(i -> i == ICE_SHEET_OCEANIC_MOUNTAINS_EDGE)) {
                    return GLACIATED_OCEANIC_MOUNTAINS;
                }
            }

            // Similar to above, tall ice sheets can create icy cliffs at edges of moraines
            if (center == ICE_SHEET || center == ICE_SHEET_TUYAS || center == ICE_SHEET_SHIELD_VOLCANO) {
                if (matcher.test(i -> i == ICE_SHEET_OCEANIC_MOUNTAINS_EDGE)) {
                    return ICE_SHEET_OCEANIC;
                }
            }

            // See above
            if (center == ICE_SHEET_MOUNTAINS) {
                if (matcher.test(i -> i == ICE_SHEET_OCEANIC_MOUNTAINS_EDGE)) {
                    return ICE_SHEET_OCEANIC_MOUNTAINS;
                }
            }

            return center;
        }

        static boolean isNotIceSheet(int value) {
            return value != ICE_SHEET && value != ICE_SHEET_TUYAS && value != SUBGLACIAL_LAKE && value != ICE_SHEET_MOUNTAINS && value != ICE_SHEET_OCEANIC_MOUNTAINS
                && value != ICE_SHEET_SHIELD_VOLCANO && value != ICE_SHEET_VOLCANIC_MOUNTAINS && value != ICE_SHEET_VOLCANIC_OCEANIC_MOUNTAINS;
        }

        static boolean isNotIceSheetOrGlaciated(int value) {
            return isNotIceSheet(value) && value != GLACIATED_MOUNTAINS && value != GLACIATED_OCEANIC_MOUNTAINS && value != GLACIATED_SHIELD_VOLCANO && value != GLACIATED_VOLCANIC_MOUNTAINS && value != GLACIATED_VOLCANIC_OCEANIC_MOUNTAINS;
        }
    }

    static boolean hasShore(int value) {
        return !TfcBiomes.isOcean(value) && value != LOW_CANYONS && value != CANYONS && value != OCEANIC_MOUNTAINS && value != VOLCANIC_OCEANIC_MOUNTAINS
            && value != TOWER_KARST_BAY && value != SUNKEN_SHIELD_VOLCANO && value != GLACIALLY_CARVED_OCEANIC_MOUNTAINS && value != ICE_SHEET_OCEANIC_MOUNTAINS_EDGE
            && value != ICE_SHEET_SHIELD_VOLCANO && value != GLACIATED_SHIELD_VOLCANO && value != GUANO_ISLAND && value != GLACIALLY_CARVED_VOLCANIC_OCEANIC_MOUNTAINS
            && value != VOLCANIC_MOUNTAIN_ISLANDS;
    }

    static int shoreFor(int value) {
        if (value == LOWLANDS || value == SALT_MARSH) {
            return SALT_MARSH;
        }
        if (value == MOUNTAINS || value == COLLISIONAL_MOUNTAINS) {
            return OCEANIC_MOUNTAINS;
        }
        if (value == VOLCANIC_MOUNTAINS) {
            return VOLCANIC_OCEANIC_MOUNTAINS;
        }
        if (value == TOWER_KARST_LAKE) {
            return TOWER_KARST_BAY;
        }
        if (value == ACTIVE_SHIELD_VOLCANO) {
            return SHIELD_VOLCANO_SHORE;
        }
        if (value == DORMANT_SHIELD_VOLCANO || value == EXTINCT_SHIELD_VOLCANO || value == ANCIENT_SHIELD_VOLCANO) {
            return OLD_SHIELD_VOLCANO_SHORE;
        }
        if (isFlatIceSheet(value) || value == ICE_SHEET_EDGE || value == ICE_SHEET_OCEANIC) {
            return ICE_SHEET_SHORE;
        }
        if (value == ICE_SHEET_OCEANIC_MOUNTAINS || value == ICE_SHEET_VOLCANIC_OCEANIC_MOUNTAINS) {
            return ICE_SHEET_OCEANIC_MOUNTAINS_EDGE;
        }
        if (value == GLACIATED_OCEANIC_MOUNTAINS || value == GLACIATED_MOUNTAINS || value == GLACIALLY_CARVED_MOUNTAINS) {
            return GLACIALLY_CARVED_OCEANIC_MOUNTAINS;
        }
        if (value == GLACIATED_VOLCANIC_OCEANIC_MOUNTAINS || value == GLACIATED_VOLCANIC_MOUNTAINS || value == GLACIALLY_CARVED_VOLCANIC_MOUNTAINS) {
            return GLACIALLY_CARVED_VOLCANIC_OCEANIC_MOUNTAINS;
        }
        if (value == OLD_MOUNTAINS || value == EXTREME_DOLINE_MOUNTAINS) {
            return TERRACE_LOWER;
        }
        if (value == PLATEAU || value == EXTREME_DOLINE_PLATEAU || value == BURREN_PLATEAU || value == SHILIN_PLATEAU) {
            return SEA_STACKS;
        }
        if (value == PLATEAU_WIDE || value == ROCKY_PLATEAU || value == DOLINE_PLATEAU) {
            return SETBACK_CLIFFS;
        }
        if (value == HIGHLANDS || value == CENOTE_HIGHLANDS || value == DOLINE_HIGHLANDS || value == SHILIN_HIGHLANDS || value == TOWER_KARST_HIGHLANDS) {
            return ROCKY_SHORES;
        }
        if (value == ROLLING_HILLS || value == DOLINE_ROLLING_HILLS || value == CENOTE_ROLLING_HILLS) {
            return EMBAYMENTS;
        }
        if (value == HILLS || value == CENOTE_HILLS || value == DOLINE_HILLS || value == SHILIN_HILLS || value == TOWER_KARST_HILLS || value == GRASSY_DUNES || value == DUNE_SEA) {
            return COASTAL_DUNES;
        }
        if (value == VOLCANIC_ISLAND) {
            return SHORE;
        }
        return TIDAL_FLATS;
    }

    static boolean isMountains(int value) {
        return value == MOUNTAINS || value == OCEANIC_MOUNTAINS || value == OLD_MOUNTAINS || value == VOLCANIC_MOUNTAINS || value == VOLCANIC_OCEANIC_MOUNTAINS || value == COLLISIONAL_MOUNTAINS;
    }

    static boolean isLow(int value) {
        return value == PLAINS || value == HILLS || value == LOW_CANYONS || value == LOWLANDS || value == SALT_MARSH || value == MUD_FLATS || value == SALT_FLATS || value == DUNE_SEA || value == RIFT_VALLEY;
    }

    static boolean isFlats(int value) {
        return value == MUD_FLATS || value == SALT_FLATS;
    }
}
