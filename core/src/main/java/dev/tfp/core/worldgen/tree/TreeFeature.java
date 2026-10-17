/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/world/feature/tree/RandomTreeConfig.java @ c3b4d9536 (the records and their helpers;
 * codecs removed), with OverlayTreeConfig, StackedTreeConfig, KrummholzConfig, TrunkConfig, RootConfig and TreePlacementConfig
 * in the same package
 */
package dev.tfp.core.worldgen.tree;

import dev.tfp.core.worldgen.ore.Weighted;
import dev.tfp.core.worldgen.random.XoroshiroRandomSource;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** The configured tree features of TFC: what a forest entry places. Immutable. */
public sealed interface TreeFeature {
    /** The wood of the tree's blocks. */
    Wood wood();

    /** {@code tfc:random_tree}: one random template, on an optional trunk, with optional roots. */
    record RandomTree(Wood wood, List<TreeTemplate> structures, Optional<Trunk> trunk, Placement placement,
                      Optional<Roots> rootSystem) implements TreeFeature {
        public RandomTree {
            structures = List.copyOf(structures);
            if (structures.isEmpty()) {
                throw new IllegalArgumentException("a random tree needs a structure");
            }
        }
    }

    /** {@code tfc:overlay_tree}: a base template and an overlay template whose blocks are kept with {@code overlayIntegrity}. */
    record OverlayTree(Wood wood, TreeTemplate base, TreeTemplate overlay, Optional<Trunk> trunk, float overlayIntegrity,
                       Placement placement, Optional<Roots> rootSystem) implements TreeFeature {}

    /** {@code tfc:stacked_tree}: a trunk, then layers of templates stacked on it. */
    record StackedTree(Wood wood, List<Layer> layers, Trunk trunk, Placement placement, Optional<Roots> rootSystem)
        implements TreeFeature {
        public StackedTree {
            layers = List.copyOf(layers);
        }
    }

    /** {@code tfc:krummholz}: a column of krummholz blocks of a uniform height. */
    record Krummholz(Wood wood, int minHeight, int maxHeight, boolean spawnsOnStone, boolean spawnsOnGravel)
        implements TreeFeature {
        /** {@code UniformInt.sample}: {@code Mth.randomBetweenInclusive}. */
        int sampleHeight(XoroshiroRandomSource random) {
            return random.nextInt(maxHeight - minHeight + 1) + minHeight;
        }
    }

    /** {@code StackedTreeConfig.Layer}. */
    record Layer(List<TreeTemplate> templates, int minCount, int maxCount) {
        public Layer {
            templates = List.copyOf(templates);
            if (maxCount < minCount) {
                throw new IllegalArgumentException("max count " + maxCount + " < min count " + minCount);
            }
        }

        int count(XoroshiroRandomSource random) {
            return maxCount == minCount ? minCount : minCount + random.nextInt(1 + maxCount - minCount);
        }
    }

    /**
     * {@code TrunkConfig}: a column of logs ({@code wide}: a 2 x 2 column) whose height is drawn.
     *
     * @param axis the axis of the trunk state (TFC's trunks are {@code y})
     */
    record Trunk(Wood wood, TreeBlock.Axis axis, int minHeight, int maxHeight, boolean wide) {
        public Trunk {
            if (minHeight > maxHeight) {
                throw new IllegalArgumentException("min height " + minHeight + " > max height " + maxHeight);
            }
        }

        int height(XoroshiroRandomSource random) {
            return maxHeight == minHeight ? minHeight : minHeight + random.nextInt(1 + maxHeight - minHeight);
        }
    }

    /**
     * {@code RootConfig}: {@code tries} roots in a triangle spread of {@code width} around the trunk, up to {@code height} below
     * it; a root replaces the ground kinds of {@code blocks}. The special placer (mangrove only) is not ported.
     *
     * @param blocks the weighted root kinds for each replaced ground kind (a lookup that matches draws one double)
     */
    record Roots(Map<TreeGround, Weighted<TreeBlock.Kind>> blocks, int width, int height, int tries, boolean required) {
        public Roots {
            blocks = Map.copyOf(blocks);
        }
    }

    /** {@code TreePlacementConfig}: the ground and trunk space a tree needs. */
    record Placement(int width, int height, GroundType groundType) {
        boolean mayPlaceInWater() {
            return groundType == GroundType.SHALLOW_WATER || groundType == GroundType.SUBMERGED
                || groundType == GroundType.SUBMERGED_ALLOW_SALTWATER;
        }

        boolean mayPlaceUnderwater() {
            return groundType == GroundType.SUBMERGED || groundType == GroundType.SUBMERGED_ALLOW_SALTWATER;
        }

        boolean requiresFreshwater() {
            return groundType != GroundType.SUBMERGED_ALLOW_SALTWATER && groundType != GroundType.SHALLOW_ALLOW_SALTWATER;
        }
    }

    /** {@code TreePlacementConfig.GroundType}. */
    enum GroundType { NORMAL, SAND, SHALLOW_WATER, SUBMERGED, SHALLOW_ALLOW_SALTWATER, SUBMERGED_ALLOW_SALTWATER, FLOATING }
}
