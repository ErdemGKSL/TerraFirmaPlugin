/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/mixin/PistonBlockEntityMixin.java @ c3b4d9536
 * (removeFluidInFinalTick, removeFluidInTick: every block a piston moves arrives through emptyFluidFrom), and
 * src/main/java/net/dries007/tfc/common/fluids/FluidHelpers.java @ c3b4d9536
 * (emptyFluidFrom(BlockState), lines 534-554; isAirOrEmptyFluid; isMeltableIce).
 */
package dev.tfp.core.piston;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * PAR-DATA-16: the state a piston-moved block arrives in. TFC routes the landing state of both piston paths through
 * {@code FluidHelpers.emptyFluidFrom}: the normal path ({@code PistonMovingBlockEntity.tick}, where vanilla already
 * clears {@code waterlogged}) and the 1-tick path ({@code finalTick}, vanilla bug MC-130183, which keeps the water). So
 * a moved block never carries a fluid source:
 *
 * <ol>
 *   <li>{@code waterlogged=true} becomes {@code false};</li>
 *   <li>a TFC fluid-logged block ({@code fluid} property, TFC's {@code IFluidLoggable}) gets {@code fluid=empty};</li>
 *   <li>a liquid block arrives as air ({@code isAirOrEmptyFluid});</li>
 *   <li>meltable ice ({@code minecraft:ice}, TFC's sea ice and ice pile) arrives as air ({@code isMeltableIce}).</li>
 * </ol>
 *
 * Pure and thread-safe; works on block ids and property maps, so it covers vanilla block data and our CraftEngine
 * states alike.
 */
public final class PistonFluidRule {
    /** Vanilla's {@code BlockStateProperties.WATERLOGGED}. */
    public static final String WATERLOGGED = "waterlogged";
    /** TFC's {@code FluidProperty} name on fluid-logged blocks ({@code TFCBlockStateProperties}). */
    public static final String FLUID = "fluid";
    /** The value of {@link #FLUID} for no fluid ({@code FluidProperty.keyFor(Fluids.EMPTY)}). */
    public static final String EMPTY = "empty";
    /** The block a moving piston leaves at the destination until the move lands. */
    public static final String MOVING_PISTON = "minecraft:moving_piston";
    public static final String AIR = "minecraft:air";

    /** {@code FluidHelpers.isMeltableIce}: vanilla ice, TFC's sea ice and ice pile (renamed to our namespace). */
    public static final Set<String> MELTABLE_ICE = Set.of("minecraft:ice", "tfp:sea_ice", "tfp:ice_pile");

    private PistonFluidRule() {
    }

    /** A block state as id + properties. Immutable; properties sorted so equal states are equal. */
    public record State(String id, SortedMap<String, String> properties) {
        public State {
            Objects.requireNonNull(id, "id");
            properties = java.util.Collections.unmodifiableSortedMap(new TreeMap<>(properties));
        }

        public static State of(String id, Map<String, String> properties) {
            return new State(id, new TreeMap<>(properties));
        }

        public static State of(String id) {
            return new State(id, new TreeMap<>());
        }

        State with(String key, String value) {
            TreeMap<String, String> p = new TreeMap<>(properties);
            p.put(key, value);
            return new State(id, p);
        }
    }

    /**
     * True if a block in {@code state} would arrive changed: the cheap filter the piston event applies to every moved
     * block before it watches the destination.
     */
    public static boolean changesOnArrival(State state) {
        return arrive(state).isPresent();
    }

    /**
     * {@code emptyFluidFrom}: the state the moved block must have after landing, or empty when it lands as it is.
     */
    public static Optional<State> arrive(State state) {
        State s = state;
        if ("true".equals(s.properties().get(WATERLOGGED))) {
            s = s.with(WATERLOGGED, "false");
        }
        String fluid = s.properties().get(FLUID);
        if (fluid != null && !EMPTY.equals(fluid)) {
            s = s.with(FLUID, EMPTY);
        }
        if (isLiquid(s.id()) || MELTABLE_ICE.contains(s.id())) {
            s = State.of(AIR);
        }
        return s.equals(state) ? Optional.empty() : Optional.of(s);
    }

    /**
     * The decision for a watched destination once the move has landed: {@code live} is the block there now, {@code
     * movedId} the id of the block the piston moved there. A different block (the move was undone, or something else
     * took the place) or a block still moving is left alone.
     */
    public static Optional<State> decide(String movedId, State live) {
        if (live == null || !live.id().equals(movedId)) {
            return Optional.empty();
        }
        return arrive(live);
    }

    /** {@code isAirOrEmptyFluid} for the non-air half: the block is a liquid's own block. */
    static boolean isLiquid(String id) {
        return id.equals("minecraft:water") || id.equals("minecraft:lava") || id.startsWith("tfp:fluid/");
    }
}
