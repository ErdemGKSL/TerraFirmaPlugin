/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/world/region/RiverEdge.java @ c3b4d9536
 *
 * Divergence: TFC's widthSq(double, double) passes drain().y() as the segment's x end (a copy error). It is kept as
 * written, so the interpolated width matches TFC; see docs/porting/ledger.md.
 */
package dev.tfp.core.worldgen.region;

import dev.tfp.core.worldgen.Mth;
import dev.tfp.core.worldgen.river.MidpointFractal;
import dev.tfp.core.worldgen.river.River;
import dev.tfp.core.worldgen.river.RiverHelpers;
import dev.tfp.core.worldgen.random.XoroshiroRandomSource;

/** One river edge of a region, with its fractal shape, its width (in grid units) and its link to the edge it drains into. */
public final class RiverEdge {
    public static final int MIN_WIDTH = 8;
    public static final int MAX_WIDTH = 24;
    public static final int MIN_VALLEY_WIDTH = 20;

    private static final int MAX_AFFECTING_GRID_DISTANCE = 1 + Mth.ceil(1.5f * AddRiversAndLakes.RIVER_LENGTH);

    public int width;

    private final River.Vertex source, drain;
    private final MidpointFractal fractal;

    final int minPartX, minPartZ, maxPartX, maxPartZ;

    // River-wide drain/source properties
    private boolean sourceEdge; // `true` if this river has a source edge, `false` if it does not.
    private RiverEdge drainEdge; // The drain edge of this river, or null if it has none

    public RiverEdge(River.Edge edge, XoroshiroRandomSource random) {
        this.source = edge.source();
        this.drain = edge.drain();
        this.fractal = edge.fractal(random, 4);

        final int centerGridX = (int) Math.round(0.5f * (edge.source().x() + edge.drain().x()));
        final int centerGridZ = (int) Math.round(0.5f * (edge.source().y() + edge.drain().y()));

        this.minPartX = Units.gridToPart(centerGridX - MAX_AFFECTING_GRID_DISTANCE);
        this.minPartZ = Units.gridToPart(centerGridZ - MAX_AFFECTING_GRID_DISTANCE);
        this.maxPartX = Units.gridToPart(centerGridX + MAX_AFFECTING_GRID_DISTANCE);
        this.maxPartZ = Units.gridToPart(centerGridZ + MAX_AFFECTING_GRID_DISTANCE);

        this.sourceEdge = false;
        this.drainEdge = null;
    }

    public River.Vertex source() {
        return source;
    }

    /** @return {@code true} if this river has a source edge. */
    public boolean sourceEdge() {
        return sourceEdge;
    }

    public River.Vertex drain() {
        return drain;
    }

    /** @return The drain edge connected to this river, or null if one does not exist. */
    public RiverEdge drainEdge() {
        return drainEdge;
    }

    public MidpointFractal fractal() {
        return fractal;
    }

    public int widthSq() {
        return width * width;
    }

    /** @return the interpolated width, with a given reference grid position, in grid coordinates. */
    public double widthSq(double exactGridX, double exactGridZ) {
        // Use a simple lerp, source = 0, drain = 1
        // This is not exactly correct, since the interpolation will be based on
        final double lerpFac = RiverHelpers.projectAlongLine(
            source().x(), source().y(),
            drain().y(), drain().y(), // Kept from TFC (see the class comment)
            exactGridX, exactGridZ
        );

        final double realWidth = Mth.lerp(lerpFac, width, drainEdge == null ? width : drainEdge.width);

        return realWidth * realWidth;
    }

    /** Links this edge to the provided drain edge via {@code this --> edge}. */
    public void linkToDrain(RiverEdge edge) {
        this.drainEdge = edge;
        if (edge != null) {
            edge.sourceEdge = true;
        }
    }
}
