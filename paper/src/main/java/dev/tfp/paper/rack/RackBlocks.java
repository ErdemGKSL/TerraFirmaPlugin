/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.rack;

import dev.tfp.core.chest.ChestSide;
import dev.tfp.paper.integration.BlockStateRef;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * PAR-DECO-05: the rack blocks as the plugin reads them: {@code tfp:wood/tool_rack/<wood>}, {@code .../shelf/<wood>},
 * {@code .../bookshelf/<wood>} and {@code .../lectern/<wood>} with the property {@code facing} and, for the bookshelf,
 * {@code slot_<n>_occupied} (datagen {@code rack/RackBlocks}). Any thread.
 */
public final class RackBlocks {
    public static final String TOOL_RACK = "tfp:wood/tool_rack";
    public static final String SHELF = "tfp:wood/shelf";
    public static final String BOOKSHELF = "tfp:wood/bookshelf";
    public static final String LECTERN = "tfp:wood/lectern";
    public static final String FACING = "facing";
    /** TFC's {@code Wood} enum, in order. */
    public static final List<String> WOODS = List.of("acacia", "ash", "aspen", "birch", "blackwood", "chestnut",
        "douglas_fir", "hickory", "kapok", "mangrove", "maple", "oak", "palm", "pine", "rosewood", "sequoia", "spruce",
        "sycamore", "white_cedar", "willow");

    private RackBlocks() {
    }

    /** The block ids of one family: {@code tfp:wood/tool_rack/acacia} and so on. */
    public static Set<String> ids(String family) {
        List<String> ids = new ArrayList<>();
        for (String wood : WOODS) {
            ids.add(family + "/" + wood);
        }
        return Set.copyOf(ids);
    }

    /** Whether {@code id} is a block of {@code family}. */
    public static boolean isOf(String family, String id) {
        return id != null && id.startsWith(family + "/");
    }

    /** The name of {@code slot_<n>_occupied}. */
    public static String slotProperty(int slot) {
        return "slot_" + slot + "_occupied";
    }

    /** A rack block read from the world: its full id, its facing and (bookshelf) which slots are occupied. */
    public record Found(String id, ChestSide facing, boolean[] occupied) {
        public Found {
            occupied = occupied.clone();
        }

        @Override
        public boolean[] occupied() {
            return occupied.clone();
        }

        public boolean is(String family) {
            return isOf(family, id);
        }

        public BlockStateRef state() {
            BlockStateRef s = BlockStateRef.of(id).with(FACING, facing.id());
            if (is(BOOKSHELF)) {
                for (int i = 0; i < occupied.length; i++) {
                    s = s.with(slotProperty(i), Boolean.toString(occupied[i]));
                }
            }
            return s;
        }

        public Found withFacing(ChestSide next) {
            return new Found(id, next, occupied);
        }

        public Found withOccupied(boolean[] next) {
            return new Found(id, facing, next);
        }
    }

    /** The rack block a state is, or empty for another block (missing properties read as the default: north, empty). */
    public static Optional<Found> parse(BlockStateRef state) {
        if (state == null) {
            return Optional.empty();
        }
        String id = state.id();
        if (!(isOf(TOOL_RACK, id) || isOf(SHELF, id) || isOf(BOOKSHELF, id) || isOf(LECTERN, id))) {
            return Optional.empty();
        }
        ChestSide facing = ChestSide.of(state.properties().getOrDefault(FACING, "north"));
        boolean[] occupied = new boolean[6];
        for (int i = 0; i < occupied.length; i++) {
            occupied[i] = "true".equals(state.properties().get(slotProperty(i)));
        }
        return Optional.of(new Found(id, facing, occupied));
    }
}
