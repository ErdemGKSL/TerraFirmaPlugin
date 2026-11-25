/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/entities/prey/TFCFox.java @ c3b4d9536 (TFCFoxEatBerriesGoal)
 * and net.minecraft.world.entity.animal.Fox.FoxEatBerriesGoal (vanilla 1.21.1: acceptedDistance 2.0, wait 40 ticks)
 */
package dev.tfp.core.wildraid;

/**
 * TFC's fox berry goal ({@code TFCFoxEatBerriesGoal}): a fox with an empty mouth walks to a fruiting bush of
 * {@code tfc:fox_raidable} and takes one product from it. Speed 1.2, search range 12, vertical range 1 (the super call
 * {@code super(1.2, 12, 1)}); vanilla's {@code FoxEatBerriesGoal} accepts a target within 2 blocks, waits 40 ticks there and
 * searches again 200 to 399 ticks after the last search ({@code MoveToBlockGoal.nextStartTick}).
 */
public final class FoxBerry {
    public static final double SPEED = 1.2;
    public static final int RANGE = 12;
    public static final int VERTICAL = 1;
    /** {@code FoxEatBerriesGoal.acceptedDistance()}. */
    public static final double ACCEPTED_DISTANCE = 2.0;
    /** {@code FoxEatBerriesGoal.tick}: ticks waited at the bush before it is picked. */
    public static final int WAIT_TICKS = 40;
    /** {@code MoveToBlockGoal.nextStartTick}: {@code 200 + random.nextInt(200)}. */
    public static final int SEARCH_DELAY_BASE = 200;
    public static final int SEARCH_DELAY_RANDOM = 200;

    private FoxBerry() {
    }

    /** The goal starts only with an empty mouth, awake ({@code !isSleeping()}) and a target found. */
    public static boolean canStart(boolean mouthEmpty, boolean sleeping) {
        return mouthEmpty && !sleeping;
    }

    /**
     * {@code onReachedTarget}: the fox takes a product only when mobs may grief ({@code EventHooks.canEntityGrief}), the
     * bush still stands and is fruiting, and the mouth is empty.
     */
    public static boolean mayPick(boolean griefing, boolean fruiting, boolean mouthEmpty) {
        return griefing && fruiting && mouthEmpty;
    }

    /** The delay before the next search, from a draw in {@code [0, SEARCH_DELAY_RANDOM)}. */
    public static int searchDelay(int draw) {
        if (draw < 0 || draw >= SEARCH_DELAY_RANDOM) {
            throw new IllegalArgumentException("draw " + draw);
        }
        return SEARCH_DELAY_BASE + draw;
    }
}
