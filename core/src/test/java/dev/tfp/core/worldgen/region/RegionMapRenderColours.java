/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/test/java/net/dries007/tfc/test/drawing/RegionGeneratorTests.java @ c3b4d9536 (the map colours),
 * src/test/java/net/dries007/tfc/test/drawing/Artist.java @ c3b4d9536 (the gradients)
 */
package dev.tfp.core.worldgen.region;

import static dev.tfp.core.worldgen.biome.TfcBiomes.*;

import dev.tfp.core.worldgen.Mth;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.DoubleFunction;
import java.util.stream.IntStream;

/**
 * The colour schemes of TerraFirmaCraft's region drawing test ({@code RegionGeneratorTests.taskColor} and the
 * {@code Artist.Colors} gradients), kept as written so a map rendered from our port can be compared pixel by pixel with
 * the same map rendered from TFC's own classes. Each map is drawn from the finished region (after every task), one
 * pixel per grid point (128 blocks), or {@link #RIVER_SCALE} pixels per grid point for the river map.
 */
public final class RegionMapRenderColours {
    private RegionMapRenderColours() {}

    /** The maps drawn per seed. The names are TFC's {@code DrawnTask} names where one matches. */
    enum MapKind {
        ADD_ISLANDS,
        ANNOTATE_DISTANCE_TO_CELL_EDGE,
        ANNOTATE_BOUNDARY_TYPES,
        ANNOTATE_HOT_SPOT_AGE,
        ANNOTATE_DISTANCE_TO_OCEAN_AND_DEEP_OCEAN,
        ANNOTATE_BASE_LAND_HEIGHT,
        ANNOTATE_DISTANCE_TO_WEST_COAST,
        ADD_MOUNTAINS,
        ANNOTATE_BIOME_ALTITUDE,
        TEMPERATURE,
        RAINFALL_AFTER_RIVERS,
        RAINFALL_VARIANCE,
        CHOOSE_ROCKS,
        ADD_RIVERS_AND_LAKES,
        CHOOSE_BIOMES,
    }

    /** Pixels per grid point of the river map. */
    public static final int RIVER_SCALE = 4;

    /**
     * Every map, one pixel per grid point, a square of {@code 2 * radius} points around the centre, row by row; indexed
     * by {@link MapKind#ordinal()}. Each grid point belongs to exactly one region, so the regions already met are tried
     * first (the generator's small region cache would otherwise rebuild regions many times over a 400 x 400 window).
     */
    static BufferedImage[] renderAll(RegionGenerator generator, int centerX, int centerZ, int radius) {
        final int size = radius * 2;
        final MapKind[] kinds = MapKind.values();
        final BufferedImage[] images = new BufferedImage[kinds.length];
        for (int i = 0; i < kinds.length; i++) {
            images[i] = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        }
        final List<Region> seen = new ArrayList<>();
        for (int dz = 0; dz < size; dz++) {
            for (int dx = 0; dx < size; dx++) {
                final int x = centerX - radius + dx, z = centerZ - radius + dz;
                Region region = null;
                for (Region r : seen) {
                    if (x >= r.minX() && x <= r.maxX() && z >= r.minZ() && z <= r.maxZ() && r.at(x, z) != null) {
                        region = r;
                        break;
                    }
                }
                if (region == null) {
                    region = generator.getOrCreateRegion(x, z);
                    seen.add(region);
                }
                for (int i = 0; i < kinds.length; i++) {
                    images[i].setRGB(dx, dz, taskColor(kinds[i], region, x, z).getRGB());
                }
            }
        }
        return images;
    }

    /**
     * The river map: TFC's {@code drawWithRivers} over the rivers and lakes colours, sampled {@link #RIVER_SCALE} times
     * per grid point. A sample on land or shore inside a river edge's fractal (distance 0.1 grid) is drawn river blue.
     */
    public static BufferedImage renderRivers(RegionGenerator generator, int centerX, int centerZ, int radius) {
        final int size = radius * 2 * RIVER_SCALE;
        final BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        for (int pz = 0; pz < size; pz++) {
            for (int px = 0; px < size; px++) {
                final double xi = centerX - radius + px / (double) RIVER_SCALE;
                final double zi = centerZ - radius + pz / (double) RIVER_SCALE;
                image.setRGB(px, pz, riverPixel(generator, xi, zi).getRGB());
            }
        }
        return image;
    }

    /** TFC's {@code RegionGeneratorTests.drawWithRivers} pixel, with {@code ADD_RIVERS_AND_LAKES} as the base. */
    static Color riverPixel(RegionGenerator generator, double xi, double zi) {
        final int x = (int) xi, z = (int) zi;
        final float xf = (float) xi, zf = (float) zi;
        final Region region = generator.getOrCreateRegion(x, z);
        final Region.Point point = generator.getOrCreateRegionPoint(x, z);

        // Early exit - we don't draw rivers over oceans
        if (point.land() || point.shore()) {
            for (RiverEdge edge : generator.getOrCreatePartitionPoint(x, z).rivers()) {
                if (edge.fractal().intersect(xf, zf, 0.1f)) {
                    return new Color(100, 210, 250);
                }
            }
        }
        return taskColor(MapKind.ADD_RIVERS_AND_LAKES, region, x, z);
    }

    static final DoubleFunction<Color> blue = linearGradient(new Color(50, 50, 150), new Color(100, 140, 255));
    static final DoubleFunction<Color> green = linearGradient(new Color(0, 100, 0), new Color(80, 200, 80));
    static final DoubleFunction<Color> teal = linearGradient(new Color(0, 150, 150), new Color(40, 250, 250));
    static final DoubleFunction<Color> temperature = multiLinearGradient(
        new Color(180, 20, 240),
        new Color(0, 180, 240),
        new Color(180, 180, 220),
        new Color(210, 210, 0),
        new Color(200, 120, 60),
        new Color(200, 40, 40));

    static Color taskColor(MapKind task, Region region, int x, int y) {
        final Region.Point point = region.at(x, y);
        if (point == null) {
            throw new IllegalStateException("no point at " + x + ", " + y);
        }
        return switch (task) {
            case ADD_ISLANDS -> point.land()
                ? new Color(0, 130, 0)
                : oceanDepthColor(point);
            case ANNOTATE_DISTANCE_TO_CELL_EDGE -> blue.apply(point.distanceToEdge / 24f);
            case ANNOTATE_DISTANCE_TO_OCEAN_AND_DEEP_OCEAN -> point.land()
                ? green.apply(point.distanceToOcean / 20f)
                : point.oceanDepth == 2
                ? teal.apply(1 - point.distanceToDeepOcean / 20f)
                : cellColor(region);
            case ANNOTATE_BASE_LAND_HEIGHT -> continentColor(point);
            case ADD_MOUNTAINS -> mountainColor(point);
            case ANNOTATE_DISTANCE_TO_WEST_COAST -> point.land()
                ? green.apply(point.distanceToWestCoast / 100f)
                : cellColor(region);
            case ANNOTATE_BIOME_ALTITUDE -> point.land()
                ? green.apply(Mth.clampedMap(point.discreteBiomeAltitude(), 0, 3, 0, 1))
                : continentColor(point);
            case ANNOTATE_HOT_SPOT_AGE -> point.hotSpotAge > 0 ? hotspot(point.hotSpotAge) : point.land()
                ? green.apply(Mth.clampedMap(point.discreteBiomeAltitude(), 0, 3, 0, 1))
                : continentColor(point);
            case ANNOTATE_BOUNDARY_TYPES -> point.distanceToEdge < 2 ?
                point.divergence > 0 ? Color.MAGENTA : Color.RED : point.land() ? green.apply(0.5 * point.divergence + 0.5) : point.divergence > 0 ? Color.BLUE : Color.ORANGE;
            case TEMPERATURE -> temperatureGradient(point, point.temperature, -25f, 35f);
            case RAINFALL_AFTER_RIVERS -> temperatureGradient(point, point.rainfall, 0, 500);
            case RAINFALL_VARIANCE -> oceanOutlineTemperatureGradient(point, point.rainfallVariance, -1, 1);
            case CHOOSE_ROCKS -> {
                final double value = new Random(point.rock >> 2).nextDouble();
                yield switch (point.rock & 0b11) {
                    case ChooseRocks.OCEAN -> blue.apply(value);
                    case ChooseRocks.LAND -> green.apply(value);
                    case ChooseRocks.VOLCANIC -> new Color(200, (int) (100 * value), 100);
                    case ChooseRocks.UPLIFT -> new Color(180, (int) (180 * value), 200);
                    default -> throw new RuntimeException("value: " + point.rock);
                };
            }
            case ADD_RIVERS_AND_LAKES -> {
                if (point.river()) yield new Color(100, 210, 250);
                if (point.shore()) yield new Color(240, 224, 120);
                if (point.lake()) yield new Color(150, 160, 255);
                yield continentColor(point);
            }
            case CHOOSE_BIOMES -> biomeColor(point.biome);
        };
    }

    static Color cellColor(Region region) {
        return blue.apply(0.5 + 0.5 * region.noise());
    }

    static Color continentColor(Region.Point point) {
        if (point.land()) return green.apply(point.baseLandHeight / 24f);
        return oceanDepthColor(point);
    }

    static Color oceanDepthColor(Region.Point point) {
        return switch (point.oceanDepth) {
            case 1 -> new Color(150, 160, 255);
            case 2 -> new Color(120, 120, 240);
            case 3 -> new Color(105, 105, 210);
            case 4 -> new Color(90, 90, 180);
            case 5 -> new Color(60, 60, 120);
            default -> new Color(255, 100, 100); // Error detection
        };
    }

    static Color mountainColor(Region.Point point) {
        if (point.mountain()) {
            return point.volcanic()
                ? point.coastalMountain() ? new Color(200, 90, 40) : new Color(240, 110, 50)
                : point.coastalMountain() ? new Color(140, 140, 140) : new Color(180, 180, 180);
        } else if (point.barrierIsland()) {
            return point.volcanic()
                ? new Color(200, 40, 40)
                : new Color(220, 140, 140);
        } else {
            return continentColor(point);
        }
    }

    static Color temperatureGradient(Region.Point point, float value, float min, float max) {
        return (point.land() ? temperature : blue).apply(Mth.clampedMap(value, min, max, 0f, 0.999f));
    }

    static Color oceanOutlineTemperatureGradient(Region.Point point, float value, float min, float max) {
        return (point.shore() ? green : temperature).apply(Mth.clampedMap(value, min, max, 0f, 0.999f));
    }

    static Color hotspot(int age) {
        if (age == 4) return new Color(190, 180, 0);
        if (age == 3) return new Color(220, 110, 0);
        if (age == 2) return new Color(240, 20, 0);
        if (age == 1) return new Color(240, 0, 180);
        return new Color(150, 240, 150);
    }

    // Default biome color scheme, Karst Biomes invisible (TFC's biomeColor, verbatim)
    static Color biomeColor(int biome) {
        if (biome == OCEAN_REEF) return new Color(150, 160, 255);
        if (biome == OCEAN || biome == OCEAN_ATOLLS) return new Color(120, 120, 240);
        if (biome == OCEAN_RIDGE) return new Color(105, 105, 210);
        if (biome == DEEP_OCEAN || biome == DEEP_OCEAN_ATOLLS) return new Color(90, 90, 180);
        if (biome == DEEP_OCEAN_TRENCH) return new Color(60, 60, 120);
        if (biome == LAKE) return new Color(30, 30, 255);
        if (biome == MOUNTAIN_LAKE || biome == OCEANIC_MOUNTAIN_LAKE || biome == OLD_MOUNTAIN_LAKE || biome == VOLCANIC_MOUNTAIN_LAKE || biome == PLATEAU_LAKE) return new Color(20, 180, 255);
        if (biome == RIVER || biome == RIVER_VALLEY) return new Color(0, 200, 255);
        if (biome == GUANO_ISLAND) return new Color(170, 170, 170);
        if (biome == VOLCANIC_ISLAND) return new Color(120, 50, 70);
        if (biome == VOLCANIC_MOUNTAIN_ISLANDS) return new Color(150, 60, 90);

        if (biome == RIFT_VALLEY) return new Color(80, 0, 80);
        if (biome == RIFT_LAKE) return new Color(80, 0, 160);

        if (biome == OCEANIC_MOUNTAINS || biome == VOLCANIC_OCEANIC_MOUNTAINS) return new Color(255, 0, 255);
        if (biome == CANYONS || biome == TOWER_KARST_CANYONS || biome == SHILIN_CANYONS || biome == DOLINE_CANYONS || biome == CENOTE_CANYONS) return new Color(180, 60, 255);
        if (biome == LOW_CANYONS) return new Color(200, 110, 255);
        if (biome == LOWLANDS || biome == TOWER_KARST_BAY || biome == SALT_MARSH || biome == TOWER_KARST_LAKE) return new Color(220, 150, 230);

        if (biome == OCEANIC_VOLCANIC_ARC) return new Color(100, 10, 10);
        if (biome == COLLISIONAL_MOUNTAINS) return new Color(215, 20, 20);
        if (biome == MOUNTAINS || biome == VOLCANIC_MOUNTAINS) return new Color(255, 50, 50);
        if (biome == OLD_MOUNTAINS || biome == EXTREME_DOLINE_MOUNTAINS) return new Color(240, 100, 100);
        if (biome == PLATEAU || biome == PLATEAU_WIDE || biome == EXTREME_DOLINE_PLATEAU || biome == CENOTE_PLATEAU || biome == DOLINE_PLATEAU || biome == SHILIN_PLATEAU || biome == BURREN_PLATEAU) return new Color(190, 120, 120);

        if (biome == BADLANDS || biome == BURREN_BADLANDS || biome == BURREN_BADLANDS_TALL) return new Color(205, 160, 50);
        if (biome == STAIR_STEP_CANYONS) return new Color(250, 190, 0);
        if (biome == HOODOOS) return new Color(230, 180, 0);
        if (biome == MESAS) return new Color(210, 170, 0);
        if (biome == BUTTES) return new Color(190, 160, 0);
        if (biome == WHORLED_CANYONS) return new Color(250, 160, 0);

        if (biome == ROCKY_PLATEAU) return new Color(180, 160, 110);
        if (biome == DUNE_SEA || biome == GRASSY_DUNES) return new Color(250, 210, 140);
        if (biome == SALT_FLATS) return new Color(190, 190, 190);
        if (biome == MUD_FLATS) return new Color(190, 120, 100);

        if (biome == SHORE || biome == TIDAL_FLATS) return new Color(230, 210, 130);

        if (biome == HIGHLANDS || biome == SHILIN_HIGHLANDS || biome == TOWER_KARST_HIGHLANDS || biome == DOLINE_HIGHLANDS || biome == CENOTE_HIGHLANDS) return new Color(20, 80, 30);
        if (biome == ROLLING_HILLS || biome == DOLINE_ROLLING_HILLS || biome == CENOTE_ROLLING_HILLS) return new Color(50, 100, 50);
        if (biome == HILLS || biome == SHILIN_HILLS || biome == TOWER_KARST_HILLS || biome == DOLINE_HILLS || biome == CENOTE_HILLS) return new Color(80, 130, 80);
        if (biome == PLAINS || biome == BURREN_PLAINS || biome == TOWER_KARST_PLAINS || biome == DOLINE_PLAINS || biome == CENOTE_PLAINS || biome == SHILIN_PLAINS) return new Color(100, 200, 100);

        if (biome == ACTIVE_SHIELD_VOLCANO) return new Color(255, 85, 0);
        if (biome == DORMANT_SHIELD_VOLCANO) return new Color(255, 105, 0);
        if (biome == EXTINCT_SHIELD_VOLCANO) return new Color(255, 135, 0);
        if (biome == ANCIENT_SHIELD_VOLCANO || biome == SUNKEN_SHIELD_VOLCANO) return new Color(255, 155, 0);

        if (biome == ICE_SHEET || biome == SUBGLACIAL_LAKE) return new Color(255, 255, 255);
        if (biome == ICE_SHEET_OCEANIC) return new Color(215, 215, 215);
        if (biome == ICE_SHEET_TUYAS) return new Color(235, 235, 235);
        if (biome == ICE_SHEET_MOUNTAINS || biome == ICE_SHEET_MOUNTAINS_EDGE || biome == ICE_SHEET_VOLCANIC_MOUNTAINS) return new Color(255, 195, 195);
        if (biome == ICE_SHEET_OCEANIC_MOUNTAINS || biome == ICE_SHEET_OCEANIC_MOUNTAINS_EDGE || biome == ICE_SHEET_VOLCANIC_OCEANIC_MOUNTAINS) return new Color(255, 195, 255);
        if (biome == ICE_SHEET_SHIELD_VOLCANO) return new Color(255, 215, 185);

        if (biome == ICE_SHEET_EDGE || biome == ICE_SHEET_SHORE) return new Color(165, 165, 165);

        if (biome == PATTERNED_GROUND || biome == INVERTED_PATTERNED_GROUND || biome == STONE_CIRCLES) return new Color(135, 135, 135);
        if (biome == KNOB_AND_KETTLE) return new Color(115, 115, 115);
        if (biome == DRUMLINS || biome == BURREN_ROCHE_MOUTONEE) return new Color(135, 165, 135);
        if (biome == TUYAS) return new Color(115, 145, 115);
        if (biome == GLACIATED_MOUNTAINS || biome == GLACIATED_VOLCANIC_MOUNTAINS) return new Color(255, 165, 165);
        if (biome == GLACIATED_OCEANIC_MOUNTAINS || biome == GLACIATED_VOLCANIC_OCEANIC_MOUNTAINS) return new Color(255, 165, 255);
        if (biome == GLACIATED_SHIELD_VOLCANO) return new Color(255, 185, 125);
        if (biome == GLACIALLY_CARVED_MOUNTAINS || biome == GLACIALLY_CARVED_VOLCANIC_MOUNTAINS) return new Color(255, 135, 135);
        if (biome == GLACIALLY_CARVED_OCEANIC_MOUNTAINS || biome == GLACIALLY_CARVED_VOLCANIC_OCEANIC_MOUNTAINS) return new Color(255, 135, 255);

        return Color.BLACK;
    }

    // Artist.Colors and Artist's integer helpers

    static DoubleFunction<Color> linearGradient(Color from, Color to) {
        return value -> new Color(
            clampedLerp(value, from.getRed(), to.getRed()),
            clampedLerp(value, from.getGreen(), to.getGreen()),
            clampedLerp(value, from.getBlue(), to.getBlue())
        );
    }

    @SuppressWarnings("unchecked")
    static DoubleFunction<Color> multiLinearGradient(Color... colors) {
        final DoubleFunction<Color>[] parts = IntStream.range(0, colors.length - 1)
            .mapToObj(i -> linearGradient(colors[i], colors[i + 1]))
            .toArray(DoubleFunction[]::new);
        return value -> parts[(int) Math.floor(value * parts.length)].apply((value * parts.length) % 1);
    }

    static int clamp(int value, int min, int max) {
        return value < min ? min : Math.min(value, max);
    }

    static int clampedLerp(double value, int min, int max) {
        return clamp((int) (min + value * (max - min)), Math.min(min, max), Math.max(min, max));
    }
}
