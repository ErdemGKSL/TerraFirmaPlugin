/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.worldgen.surface;

import static dev.tfp.core.worldgen.surface.SurfaceKind.*;

/**
 * The configured surface builders of TFC's {@code SurfaceBuilderFactory} entries that are ported, in kind space. The
 * shore builders wrap a land builder: {@code ShoreAndOceanSurfaceBuilder} with its land and its deep floor.
 */
public final class SurfaceBuilders {
    /** {@code NormalSurfaceBuilder.INSTANCE}: grass, dirt and gravel over the rock. */
    public static final NormalSurface NORMAL = NormalSurface.land(-1,
        SurfaceRules.grassTop(SurfaceRules.of(GRAVEL)), SurfaceRules.midDirt(SurfaceRules.of(GRAVEL)), SurfaceRules.underGravel());

    /** {@code NormalSurfaceBuilder.ROCKY}: the same states, which may skip the top layers on steep ground. */
    public static final NormalSurface ROCKY = NormalSurface.land(-3,
        SurfaceRules.grassTop(SurfaceRules.of(GRAVEL)), SurfaceRules.midDirt(SurfaceRules.of(GRAVEL)), SurfaceRules.underGravel());

    /** {@code SimpleSurfaceBuilder.VOLCANIC_SOIL} (rocky): the andisol grass and dirt, with local gravel. */
    public static final NormalSurface VOLCANIC_SOIL = new NormalSurface(-3,
        SurfaceRules.volcanicGrassTop(SurfaceRules.of(GRAVEL)), SurfaceRules.volcanicMidDirt(SurfaceRules.of(GRAVEL)),
        SurfaceRules.volcanicMidDirt(SurfaceRules.of(GRAVEL)), SurfaceRules.of(GRAVEL), SurfaceRules.of(GRAVEL));

    /** {@code SimpleSurfaceBuilder.ROCKY_SHORE} (rocky): bare rock, with gravel under the water. */
    public static final NormalSurface ROCKY_SHORE = new NormalSurface(-3,
        SurfaceRules.RAW, SurfaceRules.RAW, SurfaceRules.RAW, SurfaceRules.of(GRAVEL), SurfaceRules.of(GRAVEL));

    /** {@code SimpleSurfaceBuilder.OCEAN_MUD}: the ocean floor, mud throughout. */
    public static final NormalSurface OCEAN_MUD = new NormalSurface(-1,
        SurfaceRules.of(MUD), SurfaceRules.of(MUD), SurfaceRules.of(MUD), SurfaceRules.of(MUD), SurfaceRules.of(MUD));

    /** {@code ShoreAndOceanSurfaceBuilder.OCEAN}: the ocean, with the beach where the floor is shallow. */
    public static final SurfaceBuilder OCEAN = new ShoreSurface(SurfaceRules.shoreSurface(), SurfaceRules.shoreUnderlayer(), 6,
        OCEAN_MUD, OCEAN_MUD);

    /** {@code ShoreAndOceanSurfaceBuilder.OCEAN_RIDGE}: the ridge's deep floor is the mud floor (see the ledger). */
    public static final SurfaceBuilder OCEAN_RIDGE = new ShoreSurface(SurfaceRules.shoreSurface(), SurfaceRules.shoreUnderlayer(), 6,
        ROCKY_SHORE, OCEAN_MUD);

    /** {@code ShoreAndOceanSurfaceBuilder.SANDY}: sand and sandstone on the beach, rocky land above. */
    public static final SurfaceBuilder SHORE_SANDY = new ShoreSurface(SurfaceRules.of(SAND), SurfaceRules.of(SANDSTONE), 6,
        ROCKY, OCEAN_MUD);

    /** {@code ShoreAndOceanSurfaceBuilder.SEA_CLIFFS}: the beach of the cliffs, rocky land above. */
    public static final SurfaceBuilder SEA_CLIFFS = new ShoreSurface(SurfaceRules.shoreSurface(), SurfaceRules.shoreUnderlayer(), 2,
        ROCKY, OCEAN_MUD);

    /** {@code ShoreAndOceanSurfaceBuilder.MOUNTAINS}: a gravel shore under mountains. */
    public static final SurfaceBuilder MOUNTAIN_SHORE = new ShoreSurface(SurfaceRules.of(GRAVEL), SurfaceRules.RAW, 2,
        ROCKY, OCEAN_MUD);

    /** {@code ShoreAndOceanSurfaceBuilder.VOLCANIC_MOUNTAINS}: a gravel shore under volcanic mountains. */
    public static final SurfaceBuilder VOLCANIC_MOUNTAIN_SHORE = new ShoreSurface(SurfaceRules.of(GRAVEL), SurfaceRules.RAW, 2,
        VOLCANIC_SOIL, OCEAN_MUD);

    /** {@code ShoreAndOceanSurfaceBuilder.ROCKY_SHORE}: bare rock on the coast. */
    public static final SurfaceBuilder ROCKY_COAST = new ShoreSurface(SurfaceRules.RAW, SurfaceRules.RAW, 6,
        ROCKY_SHORE, OCEAN_MUD);

    private SurfaceBuilders() {}
}
