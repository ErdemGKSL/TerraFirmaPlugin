/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/BellowsBlockEntity.java @ c3b4d9536 (getExtensionLength: the
 *       stroke the pressed levels follow)
 */
package dev.tfp.paper.bellows;

import dev.tfp.core.bellows.BellowsMotion;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.scheduler.Scheduler;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;

/**
 * PAR-METAL-26: the pressed look of the bellows. The block {@code tfp:bellows} has a {@code pressed} level 0 to 4 (level
 * {@code n} draws the extension {@code MIN + n / 4 * (MAX - MIN)}, see {@code BellowsAppearance} in datagen). After a push
 * the block is morphed through the levels of {@link BellowsMotion#extension} at the ticks where the level changes
 * ({@link #schedule}), ending at level 0 when the stroke ends. Each morph is one block state write (one metadata packet per
 * viewer of the item display); nothing runs between pushes.
 */
public final class BellowsVisuals {
    public static final String PROPERTY = "pressed";
    public static final int LEVELS = 5;
    public static final int MAX_LEVEL = LEVELS - 1;

    /** One morph: {@code delay} ticks after the push the block shows {@code level}. */
    public record Step(int delay, int level) {
    }

    private static final List<Step> SCHEDULE = computeSchedule();

    private final Scheduler scheduler;
    private final Function<Block, Optional<BlockStateRef>> stateAt;
    private final Function<BlockStateRef, Optional<BlockData>> blockData;

    public BellowsVisuals(Scheduler scheduler, Function<Block, Optional<BlockStateRef>> stateAt,
            Function<BlockStateRef, Optional<BlockData>> blockData) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.stateAt = Objects.requireNonNull(stateAt, "stateAt");
        this.blockData = Objects.requireNonNull(blockData, "blockData");
    }

    /** The pressed level that shows {@code extension}: the nearest of the {@link #LEVELS} evenly spaced extensions. */
    public static int level(float extension) {
        double step = (BellowsMotion.MAX_EXTENSION - BellowsMotion.MIN_EXTENSION) / MAX_LEVEL;
        int level = (int) Math.round((extension - BellowsMotion.MIN_EXTENSION) / step);
        return Math.max(0, Math.min(MAX_LEVEL, level));
    }

    /** The morphs of one push: the ticks (1 to 20) at which {@link #level} of the stroke changes, the last one to 0. */
    public static List<Step> schedule() {
        return SCHEDULE;
    }

    private static List<Step> computeSchedule() {
        List<Step> steps = new ArrayList<>();
        int shown = 0;
        for (int t = 1; t <= BellowsMotion.STROKE_TICKS; t++) {
            int level = level(BellowsMotion.extension(t, 0));
            if (level != shown) {
                steps.add(new Step(t, level));
                shown = level;
            }
        }
        return List.copyOf(steps);
    }

    /** The properties of {@code state} with the level written; empty states stay as they are. */
    static BlockStateRef withLevel(BlockStateRef state, int level) {
        return state.with(PROPERTY, Integer.toString(level));
    }

    /** Schedules the morphs of a push at {@code bellows}. Region thread of the bellows. */
    public void press(Block bellows) {
        int cx = bellows.getX() >> 4;
        int cz = bellows.getZ() >> 4;
        for (Step step : SCHEDULE) {
            scheduler.regionDelayed(bellows.getWorld(), cx, cz, step.delay(), () -> show(bellows, step.level()));
        }
    }

    /** Writes {@code level} when the block is still a bellows with another level. Region thread. */
    void show(Block block, int level) {
        Optional<BlockStateRef> now = stateAt.apply(block);
        if (now.isEmpty() || !now.get().id().equals(BellowsDevice.ID) || !now.get().properties().containsKey(PROPERTY)) {
            return;
        }
        BlockStateRef next = withLevel(now.get(), level);
        if (next.equals(now.get())) {
            return;
        }
        blockData.apply(next).ifPresent(data -> block.setBlockData(data, false));
    }
}
