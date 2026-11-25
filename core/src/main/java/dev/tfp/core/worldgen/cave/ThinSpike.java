/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/world/feature/cave/ThinSpikeFeature.java @ c3b4d9536
 *       src/main/java/net/dries007/tfc/world/feature/cave/ThinSpikeConfig.java @ c3b4d9536
 *       src/main/java/net/dries007/tfc/common/blocks/ThinSpikeBlock.java @ c3b4d9536 (canSurvive)
 *       src/main/java/net/dries007/tfc/common/fluids/FluidHelpers.java @ c3b4d9536 (fillWithFluid, isAirOrEmptyFluid)
 */
package dev.tfp.core.worldgen.cave;

import dev.tfp.core.worldgen.random.XoroshiroRandomSource;

/**
 * PAR-WORLD-20: TFC's {@code tfc:thin_spike} feature (calcite, mega calcite, icicles): {@code tries} hanging spikes around the
 * origin, each grown down from a ceiling, the last block a tip. TFC's draws in TFC's order. Pure apart from the level.
 *
 * @param kind            {@link CaveCell.Kind#CALCITE} or {@link CaveCell.Kind#ICICLE}
 * @param allowUnderwater whether the spike grows in water ({@code FLUID = TFCBlockStateProperties.WATER}: water or salt water)
 */
public record ThinSpike(CaveCell.Kind kind, int radius, int tries, int minHeight, int maxHeight, boolean allowUnderwater) {
    /** {@code configured_feature/calcite.json}. */
    public static final ThinSpike CALCITE = new ThinSpike(CaveCell.Kind.CALCITE, 5, 20, 2, 5, true);
    /** {@code configured_feature/mega_calcite.json}. */
    public static final ThinSpike MEGA_CALCITE = new ThinSpike(CaveCell.Kind.CALCITE, 12, 70, 3, 9, true);
    /** {@code configured_feature/icicle.json}. */
    public static final ThinSpike ICICLE = new ThinSpike(CaveCell.Kind.ICICLE, 10, 50, 2, 5, false);

    /** {@code ThinSpikeConfig.getHeight}. */
    int height(XoroshiroRandomSource random) {
        return minHeight == maxHeight ? minHeight : minHeight + random.nextInt(maxHeight - minHeight);
    }

    /** {@code ThinSpikeFeature.place}; whether any spike was placed. */
    public boolean place(CaveLevel level, int px, int py, int pz, XoroshiroRandomSource rand) {
        boolean placedAny = false;
        for (int attempt = 0; attempt < tries; attempt++) {
            // Java evaluates the offsets left to right: x, y, z
            final int dx = rand.nextInt(radius) - rand.nextInt(radius);
            final int dy = rand.nextInt(radius - rand.nextInt(radius));
            final int dz = rand.nextInt(radius) - rand.nextInt(radius);
            final int x = px + dx;
            final int z = pz + dz;
            int y = py + dy;
            // Move upwards to find a suitable spot
            for (int i = 0; i < 7; i++) {
                y++;
                if (empty(level.get(x, y, z))) {
                    y--;
                    break;
                }
            }
            placedAny |= spike(level, x, y, z, rand);
        }
        return placedAny;
    }

    private boolean spike(CaveLevel level, int x, int y, int z, XoroshiroRandomSource random) {
        // Not appending to an existing spike, and no non-icicle under ice
        final CaveCell above = level.get(x, y + 1, z);
        if (above.kind() == kind || (!allowUnderwater && above.kind() == CaveCell.Kind.ICE)) {
            return false;
        }
        if (!block(level, x, y, z)) {
            return false;
        }
        final int height = height(random);
        for (int i = 0; i < height; i++) {
            y--;
            if (!block(level, x, y, z)) {
                y++;
                break;
            }
        }
        final CaveCell last = level.get(x, y, z);
        if (last.kind() == kind) {
            level.set(x, y, z, CaveCell.thinSpike(kind, last.fluid(), true));
            return true;
        }
        return false;
    }

    /** {@code placeSpikeBlock}: on an empty block that the spike survives on, holding the block's fluid when it can. */
    private boolean block(CaveLevel level, int x, int y, int z) {
        final CaveCell state = level.get(x, y, z);
        if (empty(state) && survives(level, x, y, z)) {
            final CaveCell.Fluid fluid = state.kind() == CaveCell.Kind.FLUID ? state.fluid() : CaveCell.Fluid.EMPTY;
            // fillWithFluid: the spike's fluid property holds water and salt water only
            if (fluid == CaveCell.Fluid.EMPTY || fluid == CaveCell.Fluid.WATER || fluid == CaveCell.Fluid.SALT_WATER) {
                level.set(x, y, z, CaveCell.thinSpike(kind, fluid, false));
                return true;
            }
        }
        return false;
    }

    /** {@code ThinSpikeBlock.canSurvive}: below the same spike or a sturdy bottom face. */
    private boolean survives(CaveLevel level, int x, int y, int z) {
        final CaveCell above = level.get(x, y + 1, z);
        return above.kind() == kind || above.sturdy();
    }

    private boolean empty(CaveCell state) {
        return allowUnderwater ? state.airOrEmptyFluid() : state.isAir();
    }
}
