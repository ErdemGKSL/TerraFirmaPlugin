/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/wood/Wood.java @ c3b4d9536 (the woods, VERTICAL_SUPPORT,
 * HORIZONTAL_SUPPORT), src/main/java/net/dries007/tfc/common/items/TFCItems.java @ c3b4d9536 (SUPPORTS),
 * src/main/java/net/dries007/tfc/common/blocks/wood/VerticalSupportBlock.java @ c3b4d9536 (NORTH, EAST, SOUTH, WEST)
 */
package dev.tfp.paper.beam;

import dev.tfp.core.beam.BeamShape;
import dev.tfp.core.world.Direction;
import dev.tfp.paper.integration.BlockStateRef;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The support beam ids in our vocabulary: per wood the vertical beam {@code tfp:wood/vertical_support/<wood>}, the horizontal
 * beam {@code tfp:wood/horizontal_support/<wood>} (both with TFC's boolean {@code north/east/south/west} connection
 * properties) and the item {@code tfp:wood/support/<wood>}. The horizontal ids are the ones TFC's support definition
 * ({@code data/tfc/tfc/support/horizontal_support_beam.json}, renamed by datagen) lists, so the collapse and landslide support
 * tables match these blocks by id.
 */
public final class BeamBlocks {
    /** TFC's {@code Wood} enum, in order. */
    public static final List<String> WOODS = List.of("acacia", "ash", "aspen", "birch", "blackwood", "chestnut",
        "douglas_fir", "hickory", "kapok", "mangrove", "maple", "oak", "palm", "pine", "rosewood", "sequoia", "spruce",
        "sycamore", "white_cedar", "willow");
    public static final String VERTICAL_PREFIX = "tfp:wood/vertical_support/";
    public static final String HORIZONTAL_PREFIX = "tfp:wood/horizontal_support/";
    public static final String ITEM_PREFIX = "tfp:wood/support/";
    /** The connection property of each horizontal side. */
    public static final Map<Direction, String> PROPERTY = Map.of(Direction.NORTH, "north", Direction.EAST, "east",
        Direction.SOUTH, "south", Direction.WEST, "west");
    /** Every vertical and horizontal beam id. */
    public static final Set<String> IDS = WOODS.stream()
        .flatMap(w -> Stream.of(VERTICAL_PREFIX + w, HORIZONTAL_PREFIX + w))
        .collect(Collectors.toUnmodifiableSet());

    private BeamBlocks() {}

    public static String verticalId(String wood) {
        return VERTICAL_PREFIX + wood;
    }

    public static String horizontalId(String wood) {
        return HORIZONTAL_PREFIX + wood;
    }

    public static String itemId(String wood) {
        return ITEM_PREFIX + wood;
    }

    /** The shape of a block id: vertical, horizontal or {@link BeamShape#NONE}. */
    public static BeamShape shape(String id) {
        if (id.startsWith(VERTICAL_PREFIX) && WOODS.contains(id.substring(VERTICAL_PREFIX.length()))) {
            return BeamShape.VERTICAL;
        }
        if (id.startsWith(HORIZONTAL_PREFIX) && WOODS.contains(id.substring(HORIZONTAL_PREFIX.length()))) {
            return BeamShape.HORIZONTAL;
        }
        return BeamShape.NONE;
    }

    /** The wood of a beam block id, or empty. */
    public static Optional<String> woodOfBlock(String id) {
        return switch (shape(id)) {
            case VERTICAL -> Optional.of(id.substring(VERTICAL_PREFIX.length()));
            case HORIZONTAL -> Optional.of(id.substring(HORIZONTAL_PREFIX.length()));
            case NONE -> Optional.empty();
        };
    }

    /** The wood of a support item id, or empty. */
    public static Optional<String> woodOfItem(String id) {
        if (id.startsWith(ITEM_PREFIX) && WOODS.contains(id.substring(ITEM_PREFIX.length()))) {
            return Optional.of(id.substring(ITEM_PREFIX.length()));
        }
        return Optional.empty();
    }

    /** The beam of {@code wood} and {@code shape} with the given connections (TFC's default state: none). */
    public static BlockStateRef state(String wood, BeamShape shape, Set<Direction> connections) {
        String id = switch (shape) {
            case VERTICAL -> verticalId(wood);
            case HORIZONTAL -> horizontalId(wood);
            case NONE -> throw new IllegalArgumentException("Not a beam shape");
        };
        Map<String, String> props = new TreeMap<>();
        PROPERTY.forEach((d, name) -> props.put(name, Boolean.toString(connections.contains(d))));
        return BlockStateRef.of(id, props);
    }

    /** {@code state} with its connections replaced. */
    public static BlockStateRef withConnections(BlockStateRef state, Set<Direction> connections) {
        BlockStateRef out = state;
        for (Map.Entry<Direction, String> e : PROPERTY.entrySet()) {
            out = out.with(e.getValue(), Boolean.toString(connections.contains(e.getKey())));
        }
        return out;
    }
}
