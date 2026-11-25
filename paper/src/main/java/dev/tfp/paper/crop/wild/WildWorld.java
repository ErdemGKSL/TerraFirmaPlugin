/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/crop/{WildCropBlock,WildSpreadingCropBlock,SpreadingCropBlock}.java @ c3b4d9536
 *       (the world reads and writes of the wild and spreading crops)
 */
package dev.tfp.paper.crop.wild;

import dev.tfp.core.berry.BerryClock;
import dev.tfp.core.berry.SpreadRules.Side;
import dev.tfp.core.calendar.Month;
import dev.tfp.core.climate.OverworldClimate;
import dev.tfp.core.climate.Solar;
import dev.tfp.core.clock.ClockSnapshot;
import dev.tfp.paper.catchup.CatchUpService;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.BlockVisuals;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.tags.Tags;
import dev.tfp.paper.worldgen.WorldSettingsLookup;
import java.util.Optional;
import java.util.function.Supplier;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;

/** What the wild crops, the spreading crops and the fruit blocks read from and write to the world. Region thread of the block. */
public final class WildWorld {
    public static final String WILD_CROP_GROWS_ON = "tfc:wild_crop_grows_on";
    public static final String SPREADING_FRUIT_GROWS_ON = "tfc:spreading_fruit_grows_on";
    public static final String[] SIDES = {"north", "east", "south", "west"};

    private final CustomBlocks blocks;
    private final Supplier<BlockVisuals> visuals;
    private final Tags tags;
    private final Supplier<Optional<CatchUpService>> catchUp;

    public WildWorld(CustomBlocks blocks, Supplier<BlockVisuals> visuals, Tags tags, Supplier<Optional<CatchUpService>> catchUp) {
        this.blocks = blocks;
        this.visuals = visuals;
        this.tags = tags;
        this.catchUp = catchUp;
    }

    public CustomBlocks blocks() {
        return blocks;
    }

    /** Whether the world is northern at the block (TFC {@code SolarCalculator.getInNorthernHemisphere}; other dimensions count as northern). */
    public static boolean northern(Block block) {
        World world = block.getWorld();
        OverworldClimate climate = WorldSettingsLookup.climate(world);
        return world.getEnvironment() != World.Environment.NORMAL || Solar.inNorthernHemisphere(block.getZ(), climate.hemisphereScale());
    }

    /** TFC {@code getHemispheralCalendarMonthOfYear} at the block, from the clock. */
    public static Month month(Block block, ClockSnapshot clock) {
        BerryClock c = new BerryClock(clock.playerTicks(), clock.calendarTicks(), clock.daysInMonth(), northern(block));
        return c.hemisphericMonth(clock.calendarTicks());
    }

    /** TFC {@code WILD_CROP_GROWS_ON}: whether the block below carries a wild crop. */
    public boolean wildGrowsOn(Block below) {
        return tags.is(below, WILD_CROP_GROWS_ON);
    }

    /** TFC {@code SPREADING_FRUIT_GROWS_ON}: whether the block below may carry a fruit block. */
    public boolean fruitGrowsOn(Block below) {
        return tags.is(below, SPREADING_FRUIT_GROWS_ON);
    }

    public Optional<BlockStateRef> state(Block block) {
        return blocks.stateAt(block);
    }

    /** TFC {@code BlockState.canBeReplaced()} for the cell a fruit goes to: air or a plain replaceable vanilla block, never a custom block. */
    public boolean replaceable(Block block) {
        return blocks.stateAt(block).isEmpty() && (block.getType().isAir() || block.isReplaceable());
    }

    public static Block side(Block block, Side side) {
        return switch (side) {
            case NORTH -> block.getRelative(BlockFace.NORTH);
            case EAST -> block.getRelative(BlockFace.EAST);
            case SOUTH -> block.getRelative(BlockFace.SOUTH);
            case WEST -> block.getRelative(BlockFace.WEST);
        };
    }

    /** The number of horizontal neighbours holding the custom block {@code id}. */
    public int countAround(Block block, String id) {
        int n = 0;
        for (Side s : Side.values()) {
            if (blocks.stateAt(side(block, s)).map(b -> b.id().equals(id)).orElse(false)) {
                n++;
            }
        }
        return n;
    }

    /**
     * TFC {@code updateShape} of a spreading crop: the four side properties show whether that neighbour is the fruit block. Writes the visual
     * state only when it changes.
     */
    public void updateSides(Block block, BlockStateRef current, String fruitId) {
        BlockStateRef wanted = current;
        for (Side s : Side.values()) {
            boolean fruit = blocks.stateAt(side(block, s)).map(b -> b.id().equals(fruitId)).orElse(false);
            wanted = wanted.with(SIDES[s.ordinal()], Boolean.toString(fruit));
        }
        if (!wanted.equals(current)) {
            show(block, wanted);
        }
    }

    /** Sets the visual state of a block that keeps its id (CraftEngine keeps the block entity), without physics. */
    public void show(Block block, BlockStateRef wanted) {
        visuals.get().blockData(wanted).ifPresent((BlockData d) -> block.setBlockData(d, false));
    }

    public boolean place(Block block, BlockStateRef state, boolean physics) {
        return blocks.place(block, state, physics);
    }

    public boolean remove(Block block) {
        return blocks.remove(block);
    }

    public void track(Block block, String kind) {
        catchUp.get().ifPresent(c -> c.track(block, kind));
    }

    public void untrack(Block block) {
        catchUp.get().ifPresent(c -> c.untrack(block));
    }
}
