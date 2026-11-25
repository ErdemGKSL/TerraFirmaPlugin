/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.worldgen.plant;

/**
 * The air carving mask of the cave carvers, for the {@code tfc:carving_mask} placement (TFC's cave vines). A view passed to
 * {@link Plants} that does not implement it has no carved positions, so those features place nothing.
 */
public interface CarvingMaskView {
    /** Whether the cave carvers carved the position to air ({@code GenerationStep.Carving.AIR}). */
    boolean carved(int x, int y, int z);
}
