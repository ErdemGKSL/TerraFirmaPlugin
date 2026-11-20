/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/capabilities/SidedHandler.java @ c3b4d9536 (on, get)
 */
package dev.tfp.core.device.automation;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * TFC {@code SidedHandler<IItemHandler>} for automation: per face, the {@link PartialSlots} a hopper on that face sees,
 * or none. As in TFC a later {@link #on} replaces what an earlier one put on the same face. Immutable;
 * {@link #on} returns a copy.
 */
public final class SideTable {
    /** No face exposes anything (automation off, or a device with no automation). */
    public static final SideTable NONE = new SideTable(new EnumMap<>(Face.class));

    private final Map<Face, PartialSlots> sides;

    private SideTable(EnumMap<Face, PartialSlots> sides) {
        this.sides = sides;
    }

    /** TFC {@code on(handler, Predicate<Direction>)}. */
    public SideTable on(PartialSlots handler, Predicate<Face> faces) {
        EnumMap<Face, PartialSlots> next = new EnumMap<>(Face.class);
        next.putAll(sides);
        for (Face f : Face.values()) {
            if (faces.test(f)) {
                next.put(f, handler);
            }
        }
        return new SideTable(next);
    }

    /** TFC {@code on(handler, Direction...)}. */
    public SideTable on(PartialSlots handler, Face... faces) {
        List<Face> list = List.of(faces);
        return on(handler, list::contains);
    }

    /** TFC {@code get(side)}: the handler on {@code face}, empty when that face exposes nothing. */
    public Optional<PartialSlots> get(Face face) {
        return Optional.ofNullable(sides.get(face));
    }

    /** Slots a hopper on {@code face} inserts into, in the order it tries them. */
    public List<Integer> insertSlots(Face face) {
        return get(face).map(PartialSlots::insert).orElse(List.of());
    }

    /** Slots a hopper on {@code face} extracts from, in the order it tries them. */
    public List<Integer> extractSlots(Face face) {
        return get(face).map(PartialSlots::extract).orElse(List.of());
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof SideTable t && t.sides.equals(sides);
    }

    @Override
    public int hashCode() {
        return sides.hashCode();
    }

    @Override
    public String toString() {
        return "SideTable" + sides;
    }
}
