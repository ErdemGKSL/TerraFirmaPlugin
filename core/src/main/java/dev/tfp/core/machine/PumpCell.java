/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/rotation/PumpBlockEntity.java @ c3b4d9536 (the block predicates the
 *       pump reads: isPipe, the air-or-empty-fluid test, the fluid type, isSource, isFaceSturdy, the aqueduct check)
 */
package dev.tfp.core.machine;

import dev.tfp.core.world.Direction;
import java.util.EnumSet;
import java.util.Set;

/**
 * One block as the pump's flow rules see it: the few facts of a block state that the rules read. The world reads the real states
 * into this record; nothing here knows about blocks.
 *
 * <p>Fluid types are strings. A source and its flowing form of one fluid are two types of one family: {@code "water"} and
 * {@code "flowing_water"} (see {@link PumpRules#sameFamily}).
 *
 * @param air          the block is air
 * @param fluid        the fluid type of the block's fluid state, or {@code null} for none
 * @param fluidSource  the fluid state is a source
 * @param plainFluid   the block is the legacy block of its fluid, so TFC's {@code isAirOrEmptyFluid} holds
 * @param pipe         the block is a steel pipe; its {@code pipeOpen} faces are its connections
 * @param pipeOpen     the connected faces of a pipe, empty for anything else
 * @param aqueduct     the block is an aqueduct, which a pump never fills
 * @param loggable     the block can take the pump's fluid as a fluid-logged state (TFC's {@code IFluidLoggable.canContain})
 * @param sturdy       the faces that are sturdy (TFC's {@code isFaceSturdy})
 */
public record PumpCell(boolean air, String fluid, boolean fluidSource, boolean plainFluid, boolean pipe, Set<Direction> pipeOpen,
                       boolean aqueduct, boolean loggable, Set<Direction> sturdy) {

    /** Air: no fluid, nothing sturdy. */
    public static final PumpCell AIR = new PumpCell(true, null, false, false, false, Set.of(), false, false, Set.of());

    public PumpCell {
        if (fluid != null && fluid.isEmpty()) {
            throw new IllegalArgumentException("empty fluid type");
        }
        if (air && (fluid != null || plainFluid)) {
            throw new IllegalArgumentException("air holds no fluid");
        }
        if (fluidSource && fluid == null) {
            throw new IllegalArgumentException("a source needs a fluid");
        }
        if (plainFluid && fluid == null) {
            throw new IllegalArgumentException("a plain fluid block needs a fluid");
        }
        if (!pipe && !pipeOpen.isEmpty()) {
            throw new IllegalArgumentException("only a pipe has connections");
        }
        pipeOpen = Set.copyOf(pipeOpen);
        sturdy = Set.copyOf(sturdy);
    }

    /** A solid block with the given sturdy faces and no fluid. */
    public static PumpCell solid(Set<Direction> sturdy) {
        return new PumpCell(false, null, false, false, false, Set.of(), false, false, sturdy);
    }

    /** A solid block with every face sturdy and no fluid. */
    public static PumpCell solid() {
        return solid(EnumSet.allOf(Direction.class));
    }

    /** A source block of a fluid, the legacy block of the fluid. */
    public static PumpCell source(String fluid) {
        return new PumpCell(false, fluid, true, true, false, Set.of(), false, false, Set.of());
    }

    /** A flowing block of a fluid, the legacy block of the fluid but not a source. */
    public static PumpCell flowing(String fluid) {
        return new PumpCell(false, fluid, false, true, false, Set.of(), false, false, Set.of());
    }

    /**
     * A steel pipe with its connected faces. A pipe can hold fluid, so it is loggable; a {@code null} fluid is a dry pipe.
     */
    public static PumpCell steelPipe(Set<Direction> open, String fluid) {
        return new PumpCell(false, fluid, fluid != null, false, true, open, false, true, Set.of());
    }

    /** An aqueduct: a rock block that the pump never fills. It is sturdy on every face. */
    public static PumpCell aqueductBlock() {
        return new PumpCell(false, null, false, false, false, Set.of(), true, false, EnumSet.allOf(Direction.class));
    }

    /** TFC's {@code FluidHelpers.isAirOrEmptyFluid}: air, or the legacy block of a fluid. */
    public boolean airOrEmptyFluid() {
        return air || plainFluid;
    }
}
