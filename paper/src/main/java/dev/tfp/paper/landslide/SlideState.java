/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.landslide;

import dev.tfp.core.landslide.LandslideCell;
import dev.tfp.core.landslide.LandslideRules;
import dev.tfp.core.world.Direction;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * The landslide pipeline's block state (PAR-PHYS-03): a {@link Cell} read from the world, or a {@link Move} written back.
 * Immutable; read on the owner thread, handed to the worker pool.
 */
sealed interface SlideState permits SlideState.Cell, SlideState.Move {
    /**
     * What the landslide rules need to know about one block, read on its owner thread.
     *
     * @param kind        the block id in our vocabulary ({@code tfp:dirt/loam}, {@code minecraft:dirt}); air, water and
     *                    lava read as {@link LandslideRules#AIR} (TFC {@code isAirOrEmptyFluid})
     * @param sturdy      the faces that are sturdy ({@code isFaceSturdy(face, FULL)} of the server state)
     * @param toughness   TFC {@code getBlockToughness} ({@code #tfp:toughness_1..3}, bedrock 4)
     * @param unbreakable a destroy speed below zero (bedrock, barriers)
     * @param supports    in {@code #tfp:supports_landslide}
     * @param result      the state this block slides as (the landslide recipe's result, or the block itself for the
     *                    {@code can_landslide} fallback), or null when it cannot landslide
     */
    record Cell(String kind, Set<Direction> sturdy, int toughness, boolean unbreakable, boolean supports, String result)
            implements SlideState {
        static final Cell AIR = new Cell(LandslideRules.AIR, Set.of(), 0, false, false, null);

        public Cell {
            Objects.requireNonNull(kind, "kind");
            EnumSet<Direction> faces = EnumSet.noneOf(Direction.class);
            faces.addAll(sturdy);
            sturdy = Collections.unmodifiableSet(faces);
        }

        LandslideCell toCore() {
            return new LandslideCell(kind, sturdy);
        }
    }

    /**
     * The decided landslide of the block at the change's position: it falls in place ({@code side == null}) or slides one
     * block into {@code side}, as {@code result}.
     */
    record Move(Direction side, String result) implements SlideState {
        public Move {
            Objects.requireNonNull(result, "result");
        }
    }
}
