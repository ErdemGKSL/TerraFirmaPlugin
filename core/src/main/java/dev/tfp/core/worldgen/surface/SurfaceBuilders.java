/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.worldgen.surface;

import static dev.tfp.core.worldgen.surface.SurfaceRules.*;

/**
 * The first set of configured surface builders: the unseeded ones of the normal, simple and shore builders, without the
 * frozen ocean, the lava flows and the ocean ridge. {@link SurfaceFamily} names them per biome for the terrain wiring that
 * predates {@link BiomeSurfaces}, which holds every TFC builder.
 */
public final class SurfaceBuilders {
    /** {@code NormalSurfaceBuilder.INSTANCE}: grass, dirt and gravel over the rock. */
    public static final NormalSurface NORMAL = NormalSurface.land(-1,
        TOP_GRASS_TO_GRAVEL, MID_DIRT_TO_GRAVEL, UNDER_GRAVEL);

    /** {@code NormalSurfaceBuilder.ROCKY}: the same states, which may skip the top layers on steep ground. */
    public static final NormalSurface ROCKY = NormalSurface.land(-3,
        TOP_GRASS_TO_GRAVEL, MID_DIRT_TO_GRAVEL, UNDER_GRAVEL);

    /** {@code SimpleSurfaceBuilder.VOLCANIC_SOIL} (rocky): the andisol grass and dirt, with local gravel. */
    public static final NormalSurface VOLCANIC_SOIL = new NormalSurface(-3,
        VOLCANIC_TOP_GRASS_TO_LOCAL_GRAVEL, VOLCANIC_MID_DIRT_TO_LOCAL_GRAVEL, VOLCANIC_MID_DIRT_TO_LOCAL_GRAVEL, GRAVEL, GRAVEL);

    /** {@code SimpleSurfaceBuilder.ROCKY_SHORE} (rocky): bare rock, with gravel under the water. */
    public static final NormalSurface ROCKY_SHORE = new NormalSurface(-3,
        RAW, RAW, RAW, GRAVEL, GRAVEL);

    /** {@code SimpleSurfaceBuilder.OCEAN_MUD}: the ocean floor, mud throughout. */
    public static final NormalSurface OCEAN_FLOOR = new NormalSurface(-1,
        OCEAN_MUD, OCEAN_MUD, OCEAN_MUD, OCEAN_MUD, OCEAN_MUD);

    /** {@code ShoreAndOceanSurfaceBuilder.OCEAN}: the ocean, with the beach where the floor is shallow. */
    public static final SurfaceBuilder OCEAN = new ShoreSurface(SHORE_SURFACE, SHORE_UNDERLAYER, 6,
        OCEAN_FLOOR, OCEAN_FLOOR);

    /** {@code ShoreAndOceanSurfaceBuilder.OCEAN_RIDGE}: the ridge's deep floor is the mud floor (see the ledger). */
    public static final SurfaceBuilder OCEAN_RIDGE = new ShoreSurface(SHORE_SURFACE, SHORE_UNDERLAYER, 6,
        ROCKY_SHORE, OCEAN_FLOOR);

    /** {@code ShoreAndOceanSurfaceBuilder.SANDY}: sand and sandstone on the beach, rocky land above. */
    public static final SurfaceBuilder SHORE_SANDY = new ShoreSurface(SHORE_SAND, SHORE_SANDSTONE, 6,
        ROCKY, OCEAN_FLOOR);

    /** {@code ShoreAndOceanSurfaceBuilder.SEA_CLIFFS}: the beach of the cliffs, rocky land above. */
    public static final SurfaceBuilder SEA_CLIFFS = new ShoreSurface(SHORE_SURFACE, SHORE_UNDERLAYER, 2,
        ROCKY, OCEAN_FLOOR);

    /** {@code ShoreAndOceanSurfaceBuilder.MOUNTAINS}: a gravel shore under mountains. */
    public static final SurfaceBuilder MOUNTAIN_SHORE = new ShoreSurface(GRAVEL, RAW, 2,
        ROCKY, OCEAN_FLOOR);

    /** {@code ShoreAndOceanSurfaceBuilder.VOLCANIC_MOUNTAINS}: a gravel shore under volcanic mountains. */
    public static final SurfaceBuilder VOLCANIC_MOUNTAIN_SHORE = new ShoreSurface(GRAVEL, RAW, 2,
        VOLCANIC_SOIL, OCEAN_FLOOR);

    /** {@code ShoreAndOceanSurfaceBuilder.ROCKY_SHORE}: bare rock on the coast. */
    public static final SurfaceBuilder ROCKY_COAST = new ShoreSurface(RAW, RAW, 6,
        ROCKY_SHORE, OCEAN_FLOOR);

    private SurfaceBuilders() {}
}
