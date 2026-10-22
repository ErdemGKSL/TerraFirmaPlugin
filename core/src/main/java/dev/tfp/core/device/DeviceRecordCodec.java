/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.device;

import dev.tfp.core.persist.DataMap;
import dev.tfp.core.persist.PersistException;
import dev.tfp.core.persist.VersionedCodec;
import java.util.ArrayList;
import java.util.List;

/**
 * The persisted layout of a {@link DeviceRecord} (PAR-INFRA-02). The envelope has its own version; the kind's state is
 * a nested map written by the kind's own {@link VersionedCodec}, so a kind migrates its state without touching the
 * envelope and the other way round.
 *
 * <pre>
 * { v: 1, last: long (lastUpdateTick), slots: [ { b: bytes } | {} (empty slot) ], state: { v: n, ...kind fields } }
 * </pre>
 *
 * <p>Slots: a record with fewer stored slots than the kind declares is padded with empty slots (the kind grew); extra
 * stored slots are kept, so nothing a player put in is lost when a kind shrinks (they still drop on break). At most
 * {@link #MAX_SLOTS} slots are read.
 *
 * <h2>Version history</h2>
 * <ol>
 *   <li>state, slots, last (PAR-INFRA-02)</li>
 * </ol>
 */
public final class DeviceRecordCodec {
    public static final int VERSION = 1;
    /** The most slots one device stores (TFC's largest device inventory is far below). */
    public static final int MAX_SLOTS = 256;

    private DeviceRecordCodec() {
    }

    /**
     * @param name      the codec name in error messages ({@code device/<kind>})
     * @param state     the kind's state codec
     * @param slotCount the kind's slot count; shorter stored lists are padded to it
     */
    public static <S> VersionedCodec<DeviceRecord<S>> of(String name, VersionedCodec<S> state, int slotCount) {
        if (slotCount < 0 || slotCount > MAX_SLOTS) {
            throw new IllegalArgumentException(name + ": slotCount outside 0.." + MAX_SLOTS);
        }
        return VersionedCodec.<DeviceRecord<S>>builder(name, VERSION).build((rec, out) -> {
            out.putLong("last", rec.lastUpdateTick());
            List<DataMap> slots = new ArrayList<>(rec.slots().size());
            for (ItemBlob b : rec.slots()) {
                DataMap slot = new DataMap();
                if (!b.isEmpty()) {
                    slot.putBytes("b", b.bytes());
                }
                slots.add(slot);
            }
            out.putList("slots", slots);
            out.putMap("state", state.encode(rec.state()));
        }, in -> {
            long last = in.requireLong("last");
            List<DataMap> stored = in.getList("slots");
            if (stored.size() > MAX_SLOTS) {
                throw new PersistException("slots: " + stored.size() + " > " + MAX_SLOTS);
            }
            List<ItemBlob> slots = new ArrayList<>(Math.max(slotCount, stored.size()));
            for (DataMap slot : stored) {
                slots.add(ItemBlob.of(slot.getBytes("b", null)));
            }
            while (slots.size() < slotCount) {
                slots.add(ItemBlob.EMPTY);
            }
            S decoded = state.decode(in.requireMap("state"));
            return new DeviceRecord<>(decoded, slots, last);
        });
    }
}
