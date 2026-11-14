/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/CharcoalPileBlock.java @ c3b4d9536 (LAYERS, onDestroyedByPlayer,
 *       updateShape, canSurvive),
 *       src/main/java/net/dries007/tfc/util/InteractionManager.java @ c3b4d9536 (charcoal placement)
 */
package dev.tfp.core.charcoal;

/**
 * The layer rules of the charcoal pile block ({@code layers 1..8}, two pixels each). Pure: the caller reads the world.
 */
public final class CharcoalPile {
    /** {@code LAYERS}: the most layers, a full block. */
    public static final int MAX_LAYERS = CharcoalPit.MAX_LAYERS;

    private CharcoalPile() {
    }

    /** What remains of the two piles after {@code updateShape} facing down. */
    public record Merge(int under, int at) {
    }

    /** Whether {@code layers} is a valid pile, {@code 1..8}. */
    public static boolean isValid(int layers) {
        return layers >= 1 && layers <= MAX_LAYERS;
    }

    /** InteractionManager: a charcoal item adds a layer to a pile that has fewer than eight. */
    public static boolean canAddLayer(int layers) {
        return isValid(layers) && layers < MAX_LAYERS;
    }

    /**
     * TFC {@code onDestroyedByPlayer}: a pile of more than one layer loses one layer (outside creative); otherwise the block
     * is destroyed. Returns the layers left, {@code 0} for a destroyed block. Each break drops the loot table's one charcoal.
     */
    public static int afterPlayerBreak(int layers, boolean creative) {
        if (!isValid(layers)) {
            throw new IllegalArgumentException("layers must be in [1, " + MAX_LAYERS + "]: " + layers);
        }
        return layers > 1 && !creative ? layers - 1 : 0;
    }

    /**
     * TFC {@code updateShape} facing down onto another charcoal pile with fewer than eight layers: the upper pile pours into
     * it. Returns {@code null} when nothing moves (the pile below is full). {@code at == 0} means the upper pile is gone.
     */
    public static Merge mergeDown(int at, int under) {
        if (!isValid(at) || !isValid(under)) {
            throw new IllegalArgumentException("layers must be in [1, " + MAX_LAYERS + "]: " + at + ", " + under);
        }
        if (under >= MAX_LAYERS) {
            return null;
        }
        if (under + at <= MAX_LAYERS) {
            return new Merge(at + under, 0);
        }
        return new Merge(MAX_LAYERS, at + under - MAX_LAYERS);
    }

    /**
     * TFC {@code canSurvive}: the block below has a full top face, or is a full (eight-layer) charcoal pile.
     *
     * @param belowFullTop  whether the block below has a full top collision face
     * @param belowLayers   the layers of a charcoal pile below, or {@code 0} when it is none
     */
    public static boolean canSurvive(boolean belowFullTop, int belowLayers) {
        return belowFullTop || belowLayers == MAX_LAYERS;
    }
}
