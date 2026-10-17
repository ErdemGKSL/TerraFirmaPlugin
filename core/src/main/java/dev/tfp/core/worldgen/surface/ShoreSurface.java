/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.worldgen.surface;

/**
 * TFC's {@code ShoreAndOceanSurfaceBuilder}, without the frozen ocean extension and the lava flows: the floor below the tide
 * level takes the deep floor, the floor at or below the sand height takes the shore states, and the rest takes the land
 * builder. TFC compares the surface of a column that is under water (not its floor) with the sand height, as it does here.
 *
 * @param surface     the top state of the shore
 * @param subsurface  the state under the shore's top layers
 * @param sandHeight  how far above the tide level the shore's sand reaches
 * @param land        the builder above the shore
 * @param deepFloor   the builder of the floor below the tide level
 */
public record ShoreSurface(
    SurfaceRule surface,
    SurfaceRule subsurface,
    int sandHeight,
    SurfaceBuilder land,
    SurfaceBuilder deepFloor
) implements SurfaceBuilder {
    @Override
    public void build(SurfacePass pass, int startY, int endY) {
        final SurfaceColumn column = pass.column();
        final int tide = column.tideLevel();
        final int sandHeightAbsolute = tide + sandHeight;
        final int seaLevel = column.seaLevel();

        // Track ocean surface when relevant: above sea level the surface is the floor
        final int oceanFloorY = startY > seaLevel ? startY : pass.oceanFloorY();

        if (oceanFloorY < tide - 5) {
            // Below the tide level: the deep floor
            deepFloor.build(pass, startY, endY);
        } else if (oceanFloorY <= sandHeightAbsolute) {
            // At the shore: NormalSurfaceBuilder.INSTANCE with the shore states
            new NormalSurface(-1, surface, surface, subsurface, surface, surface).build(pass, startY, endY);
        } else {
            land.build(pass, startY, endY);
        }
    }
}
