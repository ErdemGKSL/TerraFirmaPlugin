/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.chest;

import dev.tfp.core.chest.ChestRules;
import dev.tfp.core.chest.ChestSide;
import dev.tfp.core.chest.ChestType;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.CustomBlocks;
import java.util.Optional;
import org.bukkit.block.Block;

/**
 * PAR-DECO-04: the chest blocks as the plugin reads them: {@code tfp:wood/chest/<wood>} and
 * {@code tfp:wood/trapped_chest/<wood>} with the properties {@code facing} and {@code type} (datagen
 * {@code chest/ChestBlocks}). Any thread for the parsing, the block's region thread for {@link #at}.
 */
public final class ChestBlocks {
    public static final String CHEST = "tfp:wood/chest";
    public static final String TRAPPED_CHEST = "tfp:wood/trapped_chest";
    public static final String FACING = "facing";
    public static final String TYPE = "type";
    /** PAR-DECO-26: set while a player has the chest open; the display shows the lid turned up. */
    public static final String OPEN = "open";

    private ChestBlocks() {
    }

    /** A chest block read from the world: its full id, its facing and its type. */
    public record Found(String id, ChestRules.Chest chest, boolean open) {
        public Found(String id, ChestRules.Chest chest) {
            this(id, chest, false);
        }

        public BlockStateRef state() {
            return BlockStateRef.of(id).with(FACING, chest.facing().id()).with(TYPE, chest.type().id())
                .with(OPEN, Boolean.toString(open));
        }

        public Found withType(ChestType type) {
            return new Found(id, new ChestRules.Chest(chest.facing(), type), open);
        }

        public Found with(ChestSide facing, ChestType type) {
            return new Found(id, new ChestRules.Chest(facing, type), open);
        }

        public Found withOpen(boolean open) {
            return new Found(id, chest, open);
        }
    }

    /** Whether {@code id} is the id of a chest or trapped chest block of one wood. */
    public static boolean isChestId(String id) {
        return id != null && (id.startsWith(CHEST + "/") || id.startsWith(TRAPPED_CHEST + "/"));
    }

    /** The chest a state is, or empty for another block (a state without the properties reads as the default: north, single). */
    public static Optional<Found> parse(BlockStateRef state) {
        if (state == null || !isChestId(state.id())) {
            return Optional.empty();
        }
        ChestSide facing = ChestSide.of(state.properties().getOrDefault(FACING, "north"));
        ChestType type = ChestType.of(state.properties().getOrDefault(TYPE, "single"));
        boolean open = Boolean.parseBoolean(state.properties().getOrDefault(OPEN, "false"));
        return Optional.of(new Found(state.id(), new ChestRules.Chest(facing, type), open));
    }

    /** The chest at {@code block}, if it is one. */
    public static Optional<Found> at(CustomBlocks blocks, Block block) {
        return blocks.stateAt(block).flatMap(ChestBlocks::parse);
    }

    /** The block on {@code side} of {@code block}. */
    public static Block relative(Block block, ChestSide side) {
        return block.getRelative(side.dx(), 0, side.dz());
    }
}
