/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/PitKilnBlockEntity.java @ c3b4d9536 (isLit, litTick, the
 *       strawItems / logItems lists, loadAdditional / saveAdditional),
 *       src/main/java/net/dries007/tfc/common/blockentities/PlacedItemBlockEntity.java @ c3b4d9536 (isHoldingLargeItem,
 *       rotations, copied by convertPlacedItemToPitKiln and convertPitKilnToPlacedItem)
 */
package dev.tfp.core.kiln;

import dev.tfp.core.persist.VersionedCodec;
import dev.tfp.core.placed.PlacedState;
import java.util.Objects;

/**
 * The stored state of a pit kiln device: the placed-item state it took over (large flag and rotations, copied back when it
 * becomes a placed item again), whether it is lit and the calendar tick it was lit at. The items live in the device slots
 * ({@link #SLOTS}): four contents, eight straw, eight logs, one item each, as TFC's {@code inventory}, {@code strawItems}
 * and {@code logItems}.
 *
 * @param placed  the placed-item state of the four content slots
 * @param lit     TFC {@code isLit}
 * @param litTick TFC {@code litTick}, the calendar (player) tick it was lit at
 */
public record PitKilnState(PlacedState placed, boolean lit, long litTick) {
    /** Content slots (TFC {@code PlacedItemBlockEntity} inventory). */
    public static final int CONTENT_SLOTS = 4;
    /** TFC {@code STRAW_NEEDED}. */
    public static final int STRAW_SLOTS = 8;
    /** TFC {@code WOOD_NEEDED}. */
    public static final int LOG_SLOTS = 8;
    /** The first straw slot. */
    public static final int FIRST_STRAW = CONTENT_SLOTS;
    /** The first log slot. */
    public static final int FIRST_LOG = FIRST_STRAW + STRAW_SLOTS;
    /** All device slots. */
    public static final int SLOTS = FIRST_LOG + LOG_SLOTS;

    /** A kiln that was never stored: no rotations, unlit. */
    public static final PitKilnState FRESH = new PitKilnState(PlacedState.EMPTY, false, 0L);

    /** TFC NBT keys {@code isLit}, {@code litTick}, {@code isHoldingLargeItem}, {@code rotation1..4}. */
    public static final VersionedCodec<PitKilnState> CODEC = VersionedCodec.<PitKilnState>builder("pit_kiln", 1)
        .build((s, out) -> out.putBoolean("isLit", s.lit()).putLong("litTick", s.litTick())
                .putBoolean("large", s.placed().large()).putFloat("rotation1", s.placed().rotation0())
                .putFloat("rotation2", s.placed().rotation1()).putFloat("rotation3", s.placed().rotation2())
                .putFloat("rotation4", s.placed().rotation3()),
            in -> new PitKilnState(new PlacedState(in.getBoolean("large", false), in.getFloat("rotation1", 0f),
                in.getFloat("rotation2", 0f), in.getFloat("rotation3", 0f), in.getFloat("rotation4", 0f)),
                in.getBoolean("isLit", false), in.getLong("litTick", 0L)));

    public PitKilnState {
        Objects.requireNonNull(placed, "placed");
    }

    /** A kiln made from a placed item block (TFC {@code convertPlacedItemToPitKiln}): its state, unlit. */
    public static PitKilnState fromPlaced(PlacedState placed) {
        return new PitKilnState(placed, false, 0L);
    }

    /** TFC {@code light()}: lit at {@code tick}. */
    public PitKilnState light(long tick) {
        return new PitKilnState(placed, true, tick);
    }

    /** The device slot of straw slot {@code index} ({@code 0..7}). */
    public static int strawSlot(int index) {
        if (index < 0 || index >= STRAW_SLOTS) {
            throw new IllegalArgumentException("straw slot must be 0.." + (STRAW_SLOTS - 1) + ": " + index);
        }
        return FIRST_STRAW + index;
    }

    /** The device slot of log slot {@code index} ({@code 0..7}). */
    public static int logSlot(int index) {
        if (index < 0 || index >= LOG_SLOTS) {
            throw new IllegalArgumentException("log slot must be 0.." + (LOG_SLOTS - 1) + ": " + index);
        }
        return FIRST_LOG + index;
    }
}
