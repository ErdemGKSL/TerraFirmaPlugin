/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.packet.plantstage;

import com.github.retrooper.packetevents.protocol.world.chunk.palette.DataPalette;
import com.github.retrooper.packetevents.protocol.world.chunk.palette.GlobalPalette;
import com.github.retrooper.packetevents.protocol.world.chunk.palette.ListPalette;
import com.github.retrooper.packetevents.protocol.world.chunk.palette.MapPalette;
import com.github.retrooper.packetevents.protocol.world.chunk.palette.Palette;
import com.github.retrooper.packetevents.protocol.world.chunk.palette.SingletonPalette;
import com.github.retrooper.packetevents.protocol.world.chunk.storage.BaseStorage;
import com.github.retrooper.packetevents.protocol.world.chunk.storage.BitStorage;
import dev.tfp.paper.plant.lifecycle.PlantStageRemap;

/**
 * PAR-WORLD-63: rewrites the block states of one chunk section ({@link DataPalette} of a {@code CHUNK_DATA} packet)
 * through {@link PlantStageRemap#section}. A section is decoded only when its palette names an id the remap rewrites
 * (a global palette is scanned). A changed section is written back with a fresh palette sized like vanilla's block
 * strategy (singleton, 4-bit list, 5 to 8-bit map); a section that was global stays global with its own width (the server's
 * registry width, which PacketEvents does not know). A list or map section that would need more than 8 bits is left
 * as it is (never happens in practice: a dormant look adds at most one id per plant state in the section).
 */
final class PlantSectionRewrite {
    static final int CELLS = 4096;
    /** Vanilla's block palette: a list palette is always 4 bits wide, a map palette 5 to 8. */
    static final int LIST_BITS = 4;
    static final int MAX_MAP_BITS = 8;

    private PlantSectionRewrite() {
    }

    /** Whether the section may hold an id the remap rewrites (palette check; true for any global palette). */
    static boolean mayHold(DataPalette data, PlantStageRemap remap) {
        Palette palette = data.palette;
        if (palette instanceof GlobalPalette) {
            return true;
        }
        int size = palette instanceof SingletonPalette ? 1 : palette.size();
        for (int i = 0; i < size; i++) {
            if (remap.rewrites(palette.idToState(i))) {
                return true;
            }
        }
        return false;
    }

    /** The section's 4096 state ids in vanilla order ({@code (y * 16 + z) * 16 + x}). */
    static int[] decode(DataPalette data) {
        int[] cells = new int[CELLS];
        Palette palette = data.palette;
        BaseStorage storage = data.storage;
        if (palette instanceof SingletonPalette || storage == null) {
            java.util.Arrays.fill(cells, palette.idToState(0));
            return cells;
        }
        for (int i = 0; i < CELLS; i++) {
            cells[i] = palette.idToState(storage.get(i));
        }
        return cells;
    }

    /**
     * Rewrites the section at block origin {@code (originX, originY, originZ)}.
     *
     * @return whether the section changed (then the packet must be re-encoded)
     */
    static boolean rewrite(DataPalette data, int originX, int originY, int originZ, PlantStageRemap remap,
            PlantStageRemap.ColumnClimate climate, PlantStageRemap.View view) {
        if (!mayHold(data, remap)) {
            return false;
        }
        int[] cells = decode(data);
        if (!remap.section(cells, originX, originY, originZ, climate, view)) {
            return false;
        }
        return encode(data, cells);
    }

    /** Writes {@code cells} back into {@code data}; false (and {@code data} unchanged) when it cannot be encoded. */
    static boolean encode(DataPalette data, int[] cells) {
        if (data.palette instanceof GlobalPalette && data.storage != null) {
            BaseStorage storage = data.storage;
            long max = (1L << storage.getBitsPerEntry()) - 1;
            for (int id : cells) {
                if (id > max) {
                    return false;
                }
            }
            for (int i = 0; i < CELLS; i++) {
                storage.set(i, cells[i]);
            }
            return true;
        }
        int distinct = distinct(cells);
        if (distinct == 1) {
            data.palette = new SingletonPalette(cells[0]);
            data.storage = null;
            return true;
        }
        int bits = Math.max(LIST_BITS, ceilLog2(distinct));
        if (bits > MAX_MAP_BITS) {
            return false;
        }
        Palette palette = bits == LIST_BITS ? new ListPalette(LIST_BITS) : new MapPalette(bits);
        BitStorage storage = new BitStorage(bits, CELLS);
        for (int i = 0; i < CELLS; i++) {
            storage.set(i, palette.stateToId(cells[i]));
        }
        data.palette = palette;
        data.storage = storage;
        return true;
    }

    static int distinct(int[] cells) {
        int[] sorted = cells.clone();
        java.util.Arrays.sort(sorted);
        int n = 1;
        for (int i = 1; i < sorted.length; i++) {
            if (sorted[i] != sorted[i - 1]) {
                n++;
            }
        }
        return n;
    }

    static int ceilLog2(int n) {
        return n <= 1 ? 0 : 32 - Integer.numberOfLeadingZeros(n - 1);
    }
}
