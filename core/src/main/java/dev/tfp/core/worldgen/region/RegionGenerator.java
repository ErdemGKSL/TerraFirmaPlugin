/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/world/region/RegionGenerator.java @ c3b4d9536
 *
 * Scope of this port: the continent, ocean, island, mountain, distance, climate and rock annotations. The biome choice
 * (ChooseBiomes), rivers and lakes (AddRiversAndLakes, RiverEdge, RegionPartition), karst surface rocks and chunk data
 * are not ported yet; see docs/porting/ledger.md (World generation, region).
 */
package dev.tfp.core.worldgen.region;

import dev.tfp.core.worldgen.FastConcurrentCache;
import dev.tfp.core.worldgen.Hash;
import dev.tfp.core.worldgen.Mth;
import dev.tfp.core.worldgen.Seed;
import dev.tfp.core.worldgen.biome.BiomeNoise;
import dev.tfp.core.worldgen.layer.Area;
import dev.tfp.core.worldgen.noise.Cellular2D;
import dev.tfp.core.worldgen.noise.Noise2D;
import dev.tfp.core.worldgen.noise.OpenSimplex2D;
import dev.tfp.core.worldgen.random.XoroshiroRandomSource;

/**
 * Builds the region map from a level seed: for each grid point, the cell (region) it belongs to and the annotations of
 * that region. Safe to call from many threads at once: the caches are concurrent, and a region is built on the calling
 * thread from immutable noise and its own seeded random, so two threads that build the same region get the same result.
 */
public final class RegionGenerator {
    private static double triangle(double frequency, double value) {
        return Math.abs(4f * frequency * value + 1f - 4f * Mth.floor(frequency * value + 0.75f)) - 1f;
    }

    private static Noise2D baseNoise(boolean axisIsX, float scale, float constant) {
        final float frequency = Units.GRID_WIDTH_IN_BLOCK / (2f * scale);
        return scale == 0
            ? (x, z) -> constant
            : axisIsX
                ? (x, z) -> triangle(frequency, x)
                : (x, z) -> triangle(frequency, z);
    }

    final Cellular2D cellNoise;
    final Noise2D continentNoise;
    final Noise2D temperatureNoise;
    final Noise2D oceanicInfluenceNoise;
    final Noise2D rainfallNoise;
    final Noise2D rainfallVarianceNoise;
    final RegionSettings settings;
    final Noise2D hotSpotAgeNoise;
    final Noise2D hotSpotIntensityNoise;
    final Cellular2D plateRegionNoise;

    /** The rock layer stack. Each thread builds its own {@link Area} from the factory, because {@link Area} is not synchronised. */
    final ThreadLocal<Area> rockArea;

    private final Seed seed;
    private final FastConcurrentCache<Region> cellCache;

    public RegionGenerator(RegionSettings settings, Seed seed) {
        this.settings = settings;
        this.seed = seed;

        this.cellNoise = BiomeNoise.continentCellNoise(1f, seed.seed());

        // Regions are large (about 12 km), so a small concurrent cache holds the recently used ones.
        this.cellCache = new FastConcurrentCache<>(256);

        final float min = settings.continentalness() * 10f - 2.5f; // range [0, 1], default 0.5 -> 2.5 continentalness
        this.continentNoise = cellNoise.then(c -> 1 - c.f1() / (0.37f + c.f2()))
            .lazyProduct(new OpenSimplex2D(seed.next())
                .spread(0.24f)
                .scaled(min, 8.7f)
                .octaves(4));

        this.temperatureNoise = baseNoise(false, settings.temperatureScale(), settings.temperatureConstant())
            .scaled(-20f, 30f)
            .add(new OpenSimplex2D(seed.next())
                .octaves(2)
                .spread(0.15f)
                .scaled(-3f, 3f));

        this.oceanicInfluenceNoise = new OpenSimplex2D(seed.next())
            .spread(0.02f);

        this.rainfallNoise = baseNoise(true, settings.rainfallScale(), settings.rainfallConstant())
            .scaled(0f, 500f)
            .add(new OpenSimplex2D(seed.next())
                .octaves(2)
                .spread(0.15f)
                .scaled(-80f, 40f)); // Bias slightly negative, as we bias near-ocean areas to be positive rainfall, so this encourages deserts inland

        this.rainfallVarianceNoise = new OpenSimplex2D(seed.next())
            .octaves(2)
            .spread(0.1f)
            .scaled(0f, 20f);

        this.hotSpotAgeNoise = BiomeNoise.hotSpotAge(seed.seed()).spread(128);
        this.hotSpotIntensityNoise = BiomeNoise.hotSpotIntensity(seed.seed()).spread(128);
        this.plateRegionNoise = BiomeNoise.plateRegions(seed.seed()).spread(128);

        // The biome stack (ChooseBiomes, not ported) is built here only to take its seeds in TFC's order, so the rock stack's seeds match.
        RegionLayers.createUniformLayer(seed, 2);
        this.rockArea = ThreadLocal.withInitial(RegionLayers.createUniformLayer(seed, 3));
    }

    public Seed seed() {
        return seed;
    }

    /** @return The continent cell noise at {@code scaleFactor}: the cells are the regions. */
    public Cellular2D continentCellNoise(float scaleFactor) {
        return BiomeNoise.continentCellNoise(scaleFactor, seed.seed());
    }

    /**
     * @return A double representing whether a point is near a converging (negative) or diverging (positive) tectonic
     * boundary, and what magnitude.
     */
    public double getDivergence(Region.Point point) {
        final Cellular2D.Cell thisCell = cellNoise.cell(point.x, point.z);
        final double thisX = thisCell.x();
        final double thisY = thisCell.y();
        final double adjacentX = thisCell.nx();
        final double adjacentY = thisCell.ny();
        final Cellular2D.Cell adjacentCell = cellNoise.cell(adjacentX, adjacentY);

        final double thisVX = getVX(thisCell.noise());
        final double thisVY = getVY(thisCell.noise());
        final double adjacentVX = getVX(adjacentCell.noise());
        final double adjacentVY = getVY(adjacentCell.noise());

        final double convergeX = getDivergence(thisX, adjacentX, thisVX, adjacentVX);
        final double convergeY = getDivergence(thisY, adjacentY, thisVY, adjacentVY);

        return convergeX + convergeY;
    }

    /**
     * @return A double representing whether two points in a 1D space with different velocities are converging (negative)
     * or diverging (positive) and to what magnitude.
     */
    public double getDivergence(double x0, double x1, double v0, double v1) {
        final double dvx = v1 - v0;
        if (x0 < x1) {
            return dvx;
        } else {
            return -dvx;
        }
    }

    public double getVX(double noise) {
        return Hash.hashDouble(noise, 95274);
    }

    public double getVY(double noise) {
        return Hash.hashDouble(noise, 894824);
    }

    /**
     * @return A smoothly interpolated value in {@code [0, 1]} representing if we are within the finite continent region or
     * not. Higher values are within the finite continent region.
     */
    public float continentFactor(Region.Point point) {
        if (settings.finiteContinents()) {
            // Finite continent area is within one pole-pole area. Interpolate from 1 -> 0 to 1.2x scale for smooth borders
            final int scaleX = settings.rainfallScale();
            final int scaleZ = settings.temperatureScale();
            return Math.min(
                scaleX == 0 ? 1f : Mth.clampedMap(Math.abs(Units.gridToBlock(point.x)), scaleX, 1.2f * scaleX, 1, 0),
                scaleZ == 0 ? 1f : Mth.clampedMap(Math.abs(Units.gridToBlock(point.z) - 0.5f * scaleZ), scaleZ, 1.2f * scaleZ, 1, 0)
            );
        }
        return 1f;
    }

    /** @return The region point at the grid coordinates, creating its region if needed. */
    public Region.Point getOrCreateRegionPoint(int gridX, int gridZ) {
        return getOrCreateRegion(gridX, gridZ).atOrThrow(gridX, gridZ);
    }

    /** @return The region that owns the grid coordinates (the region of their cell). */
    public Region getOrCreateRegion(int gridX, int gridZ) {
        return getOrCreateRegion(sampleCell(gridX, gridZ));
    }

    private Region getOrCreateRegion(Cellular2D.Cell cell) {
        final int cellX = Float.floatToIntBits((float) cell.x());
        final int cellZ = Float.floatToIntBits((float) cell.y());

        Region entry = cellCache.getIfPresent(cellX, cellZ);
        if (entry == null) {
            entry = createRegion(cell);
            cellCache.set(cellX, cellZ, entry);
        }
        return entry;
    }

    private Region createRegion(Cellular2D.Cell regionCell) {
        return new Context(regionCell).runTasks().region;
    }

    /** @return The continent cell of the grid coordinates. Its {@code cx()} and {@code cy()} are the region's cell index. */
    public Cellular2D.Cell sampleCell(int gridX, int gridZ) {
        return cellNoise.cell(gridX, gridZ);
    }

    /** The annotation steps, in TFC's order. */
    public enum Task {
        INIT(Init.INSTANCE),
        ANNOTATE_DISTANCE_TO_CELL_EDGE(AnnotateDistanceToCellEdge.INSTANCE),
        ANNOTATE_BOUNDARY_TYPES(AnnotateBoundaryTypes.INSTANCE),
        ADD_CONTINENTS(AddContinentsAndSetOceanDepths.INSTANCE),
        FLOOD_FILL_SMALL_OCEANS(FloodFillSmallOceans.INSTANCE),
        ADD_HOTSPOTS(AddHotspots.INSTANCE),
        ANNOTATE_DISTANCE_TO_OCEAN(AnnotateDistanceToOcean.INSTANCE),
        ANNOTATE_DISTANCE_TO_DEEP_OCEAN(AnnotateDistanceToDeepOcean.INSTANCE),
        ANNOTATE_BASE_LAND_HEIGHT(AnnotateBaseLandHeight.INSTANCE),
        ANNOTATE_DISTANCE_TO_WEST_COAST(AnnotateDistanceToWestCoast.INSTANCE),
        ADD_MOUNTAINS(AddMountainsAndBarrierIslands.INSTANCE),
        ADD_ISLANDS(AddIslands.INSTANCE),
        ANNOTATE_CLIMATE(AnnotateClimate.INSTANCE),
        CHOOSE_ROCKS(ChooseRocks.INSTANCE),
        ;

        private static final Task[] VALUES = values();

        private final RegionTask task;

        Task(RegionTask task) {
            this.task = task;
        }
    }

    /** The state of one region being built: its cell, its random stream and the region itself. Used on one thread only. */
    public final class Context {
        public final Cellular2D.Cell regionCell;
        public final XoroshiroRandomSource random;
        public final Region region;

        Context(Cellular2D.Cell regionCell) {
            this.regionCell = regionCell;
            this.region = new Region(regionCell);

            final long regionSeed = seed.seed() ^ Float.floatToIntBits((float) regionCell.noise()) * 7189234123L;
            this.random = new XoroshiroRandomSource(regionSeed);
        }

        Context runTasks() {
            for (Task task : Task.VALUES) {
                task.task.apply(this);
            }
            return this;
        }

        public RegionGenerator generator() {
            return RegionGenerator.this;
        }
    }
}
