/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.worldgen;

import dev.tfp.core.worldgen.chunk.PlaceholderRocks;
import dev.tfp.core.worldgen.decoration.Bamboo;
import dev.tfp.core.worldgen.decoration.DecorationBiomes;
import dev.tfp.core.worldgen.decoration.DecorationLevel;
import dev.tfp.core.worldgen.decoration.ForestFloor;
import dev.tfp.core.worldgen.decoration.SeaStacks;
import dev.tfp.core.worldgen.decoration.TidePools;
import dev.tfp.core.worldgen.tree.TreeGround;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.IntPredicate;

/**
 * PAR-WORLD-66: the surface decorations of PAR-WORLD-21 and PAR-WORLD-65 in generation: the bamboo (large features, after
 * the forest), the island and atoll guano (large features, after the corals), the sea stack patch (first in the tidal flats'
 * surface decoration), the forest floor (in the land plants) and the tide pools (after the shore groundcover).
 *
 * <p>Per NP-WORLD-01 a chunk decides the features of itself and its 8 neighbours (x outer, z inner), each origin chunk over a
 * fresh {@link DecorationLevel} on the terrain (with the trees decided for the chunk), in TFC's order for the biome at the
 * origin chunk's centre, and keeps only the blocks inside the decorated chunk. Pure: the caller writes them.
 */
final class SurfaceDecorationStep {
    /** Where in the decoration the blocks are written. */
    enum Slot {
        /** After the forest, before the cave vegetation: the four bamboo features. */
        BAMBOO,
        /** Before the surface decoration's plants: island and atoll guano, the sea stacks, the forest floor. */
        BEFORE_PLANTS,
        /** After the shore groundcover: the tide pools. */
        AFTER_SHORE
    }

    /**
     * One block for the decorated chunk.
     *
     * @param before the terrain block the decision saw there (the writer keeps the real block when it changed since)
     */
    record Write(int x, int y, int z, DecorationLevel.Block block, TreeGround before, Slot slot) {
    }

    private final ForestFloor floor;
    private final TidePools pools;
    private final SeaStacks stacks;
    private final Bamboo bamboo;

    SurfaceDecorationStep(long seed) {
        this.floor = new ForestFloor(seed);
        this.pools = new TidePools(seed);
        final IntPredicate seaStackRock = rock -> rock >= 0 && rock < PlaceholderRocks.count()
            && SeaStacks.SEA_STACK_ROCKS.contains(PlaceholderRocks.names().get(rock));
        this.stacks = new SeaStacks(seed, seaStackRock);
        this.bamboo = new Bamboo(seed);
    }

    /** The blocks of chunk {@code (chunkX, chunkZ)} by slot, each list in decision order (a later write wins). */
    Map<Slot, List<Write>> decide(int chunkX, int chunkZ, DecorationLevel.Terrain terrain) {
        final Map<Slot, List<Write>> out = new EnumMap<>(Slot.class);
        for (Slot s : Slot.values()) {
            out.put(s, new ArrayList<>());
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                final int ox = chunkX + dx;
                final int oz = chunkZ + dz;
                final int biome = terrain.biome((ox << 4) + 8, (oz << 4) + 8);
                final DecorationLevel level = new DecorationLevel(terrain);
                if (DecorationBiomes.land(biome)) {
                    for (int index : DecorationBiomes.BAMBOO_ORDER) {
                        bamboo.place(index, ox, oz, level);
                    }
                }
                int kept = keep(level, 0, chunkX, chunkZ, Slot.BAMBOO, out);
                final List<ForestFloor.Cover> covers = new ArrayList<>();
                if (DecorationBiomes.guanoIsland(biome)) {
                    floor.guanoIslands(ox, oz, terrain, covers::add);
                }
                if (DecorationBiomes.atoll(biome)) {
                    ForestFloor.atollGuano(ox, oz, terrain, covers::add);
                }
                if (DecorationBiomes.tidalFlats(biome)) {
                    stacks.place(ox, oz, level);
                }
                if (DecorationBiomes.land(biome)) {
                    floor.forestPlants(ox, oz, terrain, covers::add);
                }
                for (ForestFloor.Cover c : covers) {
                    if (c.inChunk(chunkX, chunkZ)) {
                        out.get(Slot.BEFORE_PLANTS).add(new Write(c.x(), c.y(), c.z(),
                            new DecorationLevel.Block(DecorationLevel.Kind.COVER, -1, c.block(), null), terrain.ground(c.x(), c.y(), c.z()),
                            Slot.BEFORE_PLANTS));
                    }
                }
                kept = keep(level, kept, chunkX, chunkZ, Slot.BEFORE_PLANTS, out);
                if (DecorationBiomes.tidalFlats(biome)) {
                    pools.place(ox, oz, level);
                }
                keep(level, kept, chunkX, chunkZ, Slot.AFTER_SHORE, out);
            }
        }
        return out;
    }

    /** Adds the level's blocks set since {@code from} that lie inside the chunk to the slot; returns the new log size. */
    private static int keep(DecorationLevel level, int from, int chunkX, int chunkZ, Slot slot, Map<Slot, List<Write>> out) {
        final List<DecorationLevel.Placed> log = level.log();
        for (int i = from; i < log.size(); i++) {
            final DecorationLevel.Placed p = log.get(i);
            if (p.inChunk(chunkX, chunkZ)) {
                out.get(slot).add(new Write(p.x(), p.y(), p.z(), p.block(), level.terrain().ground(p.x(), p.y(), p.z()), slot));
            }
        }
        return log.size();
    }

    /** The CraftEngine block of a decoration block, or null for the ones written as vanilla blocks or the palette's rock. */
    static String ceRef(DecorationLevel.Block block) {
        final String rock = block.rock() >= 0 && block.rock() < PlaceholderRocks.count() ? PlaceholderRocks.names().get(block.rock()) : null;
        return switch (block.kind()) {
            case COBBLE -> rock == null ? null : "tfp:rock/cobble/" + rock;
            case HARDENED -> rock == null ? null : "tfp:rock/hardened/" + rock;
            case COVER -> block.id();
            case SALT_WATER_COVER -> TfcTerrain.withFluid(block.id(), true);
            // PAR-DECO-23's golden bamboo takes vanilla bamboo's properties; vanilla bamboo is written as itself
            case BAMBOO -> block.id().equals(Bamboo.BAMBOO) ? null : block.id() + "[age=" + block.stalk().age() + ",leaves="
                + block.stalk().leaves() + ",stage=" + block.stalk().stage() + "]";
            case RAW, SALT_WATER, ROOTED_DIRT -> null;
        };
    }

    /** The vanilla placeholder name (a {@code Material}) of a groundcover id, for the covers CraftEngine does not know yet. */
    static String coverPlaceholder(String id) {
        return switch (id.substring(id.lastIndexOf('/') + 1).toLowerCase(Locale.ROOT)) {
            case "stick" -> "OAK_BUTTON";
            case "pinecone" -> "SPRUCE_BUTTON";
            case "bone" -> "BIRCH_BUTTON";
            case "rotten_flesh" -> "CRIMSON_BUTTON";
            case "humus" -> "BROWN_CARPET";
            case "salt_lick" -> "WHITE_CARPET";
            case "guano" -> "LIGHT_GRAY_CARPET";
            default -> "SEA_PICKLE"; // the shells and the sea urchin
        };
    }

    /**
     * Whether a block may be written where the decision saw {@code before}: a cover only where the real block is free (air or
     * water) and the decision saw air or water; the other blocks where the real block still is of the class the decision saw
     * (free over air and water, solid ground over the terrain's ground; never over a tree).
     */
    static boolean fits(DecorationLevel.Block block, TreeGround before, boolean realFree, boolean realSolidGround) {
        final boolean beforeFree = before.isAirOrEmptyFluid();
        if (block.kind() == DecorationLevel.Kind.COVER || block.kind() == DecorationLevel.Kind.SALT_WATER_COVER) {
            return beforeFree && realFree;
        }
        if (beforeFree) {
            return realFree;
        }
        return before != TreeGround.LOG && before != TreeGround.LEAVES && before != TreeGround.KRUMMHOLZ && realSolidGround;
    }
}
