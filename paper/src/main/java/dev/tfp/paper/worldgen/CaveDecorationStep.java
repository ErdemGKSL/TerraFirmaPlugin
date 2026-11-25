/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.worldgen;

import dev.tfp.core.worldgen.carver.Carve;
import dev.tfp.core.worldgen.cave.CaveCell;
import dev.tfp.core.worldgen.cave.CaveDecoration;
import dev.tfp.core.worldgen.cave.CaveLevel;
import dev.tfp.core.worldgen.cave.CaveVegetation;
import dev.tfp.core.worldgen.cave.UndergroundLakes;
import dev.tfp.core.worldgen.chunk.PlaceholderRocks;
import dev.tfp.core.worldgen.erosion.Erosion;
import dev.tfp.core.worldgen.groundcover.GroundView;
import dev.tfp.core.worldgen.tree.TreeGround;
import dev.tfp.core.worldgen.volcano.VolcanicBlock;
import dev.tfp.core.worldgen.volcano.VolcanicKind;
import dev.tfp.core.worldgen.volcano.VolcanoView;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * PAR-WORLD-64: the cave features of PAR-WORLD-20 and the underground flood-fill lake in generation, and the erosion pass.
 *
 * <p>Per NP-WORLD-01 a chunk decides the features of itself and its 8 neighbours (x outer, z inner), each origin chunk over a
 * fresh {@link CaveLevel} that reads the terrain as the noise stage and the carvers leave it ({@link Terrain}: carves, rock,
 * surface; the same answer for every chunk that asks) with that origin's own earlier blocks on top, in TFC's step order: the
 * underground lake ({@code lakes}), the cave decorations ({@code underground_decoration}), then the cave vegetation of the
 * biome ({@code large_features}). Only the blocks inside the decorated chunk are kept. Erosion runs on the chunk's own
 * columns over the filled blocks (TFC does the same). Pure: the caller writes the blocks on its worldgen worker.
 */
final class CaveDecorationStep {
    /** What the cave features read of the terrain, beyond the {@link VolcanoView}. */
    interface Terrain {
        /** The carvers' and the density caves' decision at a position at or below the terrain top. */
        Carve caveCarve(int x, int y, int z);

        /** The rock index of a raw rock position, or -1 where the column holds no rock there. */
        int rock(int x, int y, int z);

        /** The highest block the terrain density keeps in a column. */
        int terrainTop(int x, int z);

        /** Whether a carver's tunnel shape holds the position (TFC's carving mask). */
        boolean carved(int x, int y, int z);
    }

    /** The feature step a block belongs to, in TFC's step order. */
    enum Stage { LAKE, DECORATION, VEGETATION }

    /**
     * One block for the decorated chunk.
     *
     * @param before the terrain cell the decision saw there before any feature (the writer checks the real block still is
     *               one, else it keeps the real block)
     */
    record Write(int x, int y, int z, CaveCell cell, CaveCell before, Stage stage) {
    }

    private final UndergroundLakes lakes;
    private final CaveDecoration decoration;
    private final CaveVegetation vegetation;

    CaveDecorationStep(long seed) {
        this.lakes = new UndergroundLakes(seed);
        this.decoration = new CaveDecoration(seed);
        this.vegetation = new CaveVegetation(seed);
    }

    /**
     * The terrain cell at a position as the terrain holds it before the features: a cave's carve (cave air, water, lava),
     * water or air above the terrain top, raw rock where the column holds rock, else another block ({@link CaveCell#OTHER},
     * soils and sands; {@link CaveCell#STONE} for a stone the rock layers do not name).
     */
    static CaveCell terrainCell(VolcanoView view, Terrain terrain, int x, int y, int z, int maxY) {
        if (y <= view.minY() || y >= maxY) {
            return CaveCell.OTHER;
        }
        if (y <= terrain.terrainTop(x, z)) {
            switch (terrain.caveCarve(x, y, z)) {
                case AIR -> {
                    return CaveCell.CAVE_AIR;
                }
                case WATER -> {
                    return CaveCell.fluid(CaveCell.Fluid.WATER);
                }
                case LAVA -> {
                    return CaveCell.fluid(CaveCell.Fluid.LAVA);
                }
                case KEEP -> {
                }
            }
        }
        final TreeGround ground = view.ground(x, y, z);
        return switch (ground) {
            case AIR -> CaveCell.AIR;
            case WATER -> CaveCell.fluid(CaveCell.Fluid.WATER);
            case SALT_WATER -> CaveCell.fluid(CaveCell.Fluid.SALT_WATER);
            case STONE -> {
                final int rock = terrain.rock(x, y, z);
                yield rock >= 0 ? CaveCell.raw(rock) : CaveCell.STONE;
            }
            default -> CaveCell.OTHER;
        };
    }

    /** The cave blocks of chunk {@code (chunkX, chunkZ)} by stage, each list in decision order (a later write wins). */
    Map<Stage, List<Write>> decide(int chunkX, int chunkZ, VolcanoView view, Terrain terrain, int maxY) {
        final Map<Stage, List<Write>> out = new EnumMap<>(Stage.class);
        for (Stage s : Stage.values()) {
            out.put(s, new ArrayList<>());
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                final int ox = chunkX + dx;
                final int oz = chunkZ + dz;
                final Level level = new Level(view, terrain, maxY);
                level.stage = Stage.LAKE;
                for (VolcanicBlock b : lakes.place(ox, oz, view)) {
                    // the lake's water (the dirt under a grass floor never occurs below the surface it starts under)
                    if (b.kind() == VolcanicKind.WATER) {
                        level.set(b.x(), b.y(), b.z(), CaveCell.fluid(CaveCell.Fluid.WATER));
                    }
                }
                level.stage = Stage.DECORATION;
                decoration.place(ox, oz, level);
                level.stage = Stage.VEGETATION;
                if (CaveVegetation.placesIn(view.biome((ox << 4) + 8, (oz << 4) + 8))) {
                    vegetation.place(ox, oz, level);
                }
                for (Write w : level.writes.values()) {
                    if (w.x() >> 4 == chunkX && w.z() >> 4 == chunkZ) {
                        out.get(w.stage()).add(w);
                    }
                }
            }
        }
        return out;
    }

    /** One origin chunk's level: the terrain with that origin's blocks on top. Confined to one decision. */
    private static final class Level implements CaveLevel {
        private final VolcanoView view;
        private final Terrain terrain;
        private final int maxY;
        private final Map<Long, Write> writes = new LinkedHashMap<>();
        private Stage stage = Stage.LAKE;

        Level(VolcanoView view, Terrain terrain, int maxY) {
            this.view = view;
            this.terrain = terrain;
            this.maxY = maxY;
        }

        private static long key(int x, int y, int z) {
            return ((long) x & 0x3FFFFFFL) << 38 | ((long) z & 0x3FFFFFFL) << 12 | (long) y & 0xFFFL;
        }

        @Override
        public CaveCell get(int x, int y, int z) {
            final Write w = writes.get(key(x, y, z));
            return w != null ? w.cell() : terrainCell(view, terrain, x, y, z, maxY);
        }

        @Override
        public void set(int x, int y, int z, CaveCell cell) {
            if (y <= view.minY() || y >= maxY) {
                return;
            }
            final long k = key(x, y, z);
            final Write old = writes.get(k);
            final CaveCell before = old != null ? old.before() : terrainCell(view, terrain, x, y, z, maxY);
            // a later block moves the write to the end, so the writer applies the blocks in the decision's order
            writes.remove(k);
            writes.put(k, new Write(x, y, z, cell, before, stage));
        }

        @Override
        public int rockAt(int x, int y, int z) {
            return view.rockAt(x, y, z);
        }

        @Override
        public boolean carved(int x, int y, int z) {
            return y > view.minY() && y < maxY && terrain.carved(x, y, z);
        }

        @Override
        public GroundView ground() {
            return view;
        }

        @Override
        public int maxY() {
            return maxY;
        }
    }

    /**
     * The CraftEngine block of a cave cell, or null for the cells written as vanilla blocks or the palette's rock (air,
     * fluids, raw rock, cobweb, hanging roots) and the thin spikes, which never generate (PAR-WORLD-20).
     */
    static String ceRef(CaveCell cell) {
        final String rock = cell.rock() >= 0 && cell.rock() < PlaceholderRocks.count() ? PlaceholderRocks.names().get(cell.rock()) : null;
        return switch (cell.kind()) {
            case SPIKE -> rock == null ? null : "tfp:rock/spike/" + rock + "[part=" + cell.part().name().toLowerCase(Locale.ROOT)
                // PAR-DECO-06: the spike holds water only; salt water is written as water, lava as a dry spike (ledger)
                + ",waterlogged=" + (cell.fluid() == CaveCell.Fluid.WATER || cell.fluid() == CaveCell.Fluid.RIVER_WATER
                    || cell.fluid() == CaveCell.Fluid.SALT_WATER) + "]";
            case HARDENED -> rock == null ? null : "tfp:rock/hardened/" + rock;
            case MOSSY_COBBLE -> rock == null ? null : "tfp:rock/mossy_cobble/" + rock;
            case COBBLE -> rock == null ? null : "tfp:rock/cobble/" + rock;
            case GUANO -> "tfp:groundcover/guano";
            default -> null;
        };
    }

    /** Whether the real block at a write's position still is the terrain cell the decision saw. */
    static boolean fits(CaveCell before, boolean realAirOrFluid, boolean realIsRawRock) {
        if (before.isAir() || before.kind() == CaveCell.Kind.FLUID) {
            return realAirOrFluid;
        }
        if (before.kind() == CaveCell.Kind.RAW) {
            return realIsRawRock;
        }
        return false;
    }

    /** What the erosion pass reads and writes of the decorated chunk's own blocks. */
    interface ErosionTarget {
        boolean air(int x, int y, int z);

        /** A {@code LandslideRecipe} block: the soils, sand and gravel the terrain writes. */
        boolean landslides(int x, int y, int z);

        /** Air or a fluid: what a falling block falls through. */
        boolean fragile(int x, int y, int z);

        /** The rock index of a raw rock block, else -1. */
        int rawRock(int x, int y, int z);

        int rockAt(int x, int y, int z);

        void setHardened(int x, int y, int z, int rock);

        /** The aquifer's substance at a position (air, water, lava), or {@link Carve#KEEP} for a barrier. */
        Carve substance(int x, int y, int z);

        void setSubstance(int x, int y, int z, Carve substance);
    }

    /** {@code tfc:erosion} over the 16 x 16 columns of chunk {@code (chunkX, chunkZ)}, from the world surface down. */
    static void erode(int chunkX, int chunkZ, GroundView view, ErosionTarget target) {
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                final int x = (chunkX << 4) + lx;
                final int z = (chunkZ << 4) + lz;
                Erosion.column(new Erosion.Column() {
                    @Override
                    public boolean air(int y) {
                        return target.air(x, y, z);
                    }

                    @Override
                    public boolean landslides(int y) {
                        return target.landslides(x, y, z);
                    }

                    @Override
                    public boolean fragile(int y) {
                        return target.fragile(x, y, z);
                    }

                    @Override
                    public int hardenable(int y) {
                        return target.rawRock(x, y, z);
                    }

                    @Override
                    public int rockAt(int y) {
                        return target.rockAt(x, y, z);
                    }

                    @Override
                    public void setHardened(int y, int rock) {
                        target.setHardened(x, y, z, rock);
                    }

                    @Override
                    public boolean setSubstance(int y) {
                        final Carve s = target.substance(x, y, z);
                        if (s == Carve.KEEP) {
                            return false;
                        }
                        target.setSubstance(x, y, z, s);
                        return true;
                    }

                    @Override
                    public boolean hasSubstance(int y) {
                        return target.substance(x, y, z) != Carve.KEEP;
                    }
                }, view.worldSurface(x, z), view.minY());
            }
        }
    }
}
