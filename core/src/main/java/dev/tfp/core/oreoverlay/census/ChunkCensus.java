/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.oreoverlay.census;

/**
 * The exposure count of one chunk (immutable). Totals cover the <em>counted</em> targets only (the overlay ores, or every
 * target on a vanilla proxy world); the per-target arrays cover every target.
 *
 * <p>Depth band of a face: <em>surface</em> when its open neighbour cell is at or above the column's
 * {@link ChunkView#surfaceY} - {@value ExposureCensus#SURFACE_DEPTH} (outcrops, cliffs, river and lake beds), else
 * <em>cave</em>. An exposed block counts as surface when at least one of its exposed faces is surface.
 */
public final class ChunkCensus {
    /** Face directions in this order: down, up, north (-z), south (+z), west (-x), east (+x). */
    public static final String[] DIRECTIONS = {"down", "up", "north", "south", "west", "east"};

    private final int blocks;
    private final int exposed;
    private final int exposedSurface;
    private final int faces;
    private final int facesSurface;
    private final int[] facesByDirection;
    private final int[] targetBlocks;
    private final int[] targetExposed;
    private final int[] targetFaces;
    private final int[] targetFacesSurface;
    private final int example;

    ChunkCensus(int blocks, int exposed, int exposedSurface, int faces, int facesSurface, int[] facesByDirection,
            int[] targetBlocks, int[] targetExposed, int[] targetFaces, int[] targetFacesSurface, int example) {
        this.blocks = blocks;
        this.exposed = exposed;
        this.exposedSurface = exposedSurface;
        this.faces = faces;
        this.facesSurface = facesSurface;
        this.facesByDirection = facesByDirection.clone();
        this.targetBlocks = targetBlocks.clone();
        this.targetExposed = targetExposed.clone();
        this.targetFaces = targetFaces.clone();
        this.targetFacesSurface = targetFacesSurface.clone();
        this.example = example;
    }

    /** Counted target blocks in the chunk. */
    public int blocks() {
        return blocks;
    }

    /** Counted target blocks with at least one exposed face. */
    public int exposed() {
        return exposed;
    }

    /** Exposed counted blocks with at least one surface-band face. */
    public int exposedSurface() {
        return exposedSurface;
    }

    /** Exposed counted blocks whose faces are all in the cave band. */
    public int exposedCave() {
        return exposed - exposedSurface;
    }

    /** Exposed faces of counted blocks: the overlay entity count of this chunk. */
    public int faces() {
        return faces;
    }

    public int facesSurface() {
        return facesSurface;
    }

    public int facesCave() {
        return faces - facesSurface;
    }

    /** Exposed faces of counted blocks towards {@link #DIRECTIONS}{@code [direction]}. */
    public int facesByDirection(int direction) {
        return facesByDirection[direction];
    }

    /** Number of targets the per-target counts cover. */
    public int targetCount() {
        return targetBlocks.length;
    }

    public int targetBlocks(int target) {
        return targetBlocks[target];
    }

    public int targetExposed(int target) {
        return targetExposed[target];
    }

    public int targetFaces(int target) {
        return targetFaces[target];
    }

    public int targetFacesSurface(int target) {
        return targetFacesSurface[target];
    }

    /**
     * One exposed counted block with a surface-band face, packed by {@link #pack} in local coordinates (x, z in [0, 15]), or
     * -1 if there is none: a place to look at an exposed vein.
     */
    public int surfaceExample() {
        return example;
    }

    /** Packs local x, z in [0, 15] and a y in [-2048, 2047] into one int. */
    public static int pack(int x, int y, int z) {
        return (x & 15) << 16 | (z & 15) << 12 | (y & 0xFFF);
    }

    public static int unpackX(int packed) {
        return packed >> 16 & 15;
    }

    public static int unpackZ(int packed) {
        return packed >> 12 & 15;
    }

    public static int unpackY(int packed) {
        return packed << 20 >> 20;
    }
}
