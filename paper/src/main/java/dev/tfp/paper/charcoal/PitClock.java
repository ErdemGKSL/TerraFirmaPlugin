/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/BurningLogPileBlockEntity.java @ c3b4d9536 (serverTick: the
 *       conversion test every tick),
 *       src/main/java/net/dries007/tfc/common/blocks/devices/BurningLogPileBlock.java @ c3b4d9536 (randomTick)
 */
package dev.tfp.paper.charcoal;

import dev.tfp.core.charcoal.CharcoalPit;
import dev.tfp.core.charcoal.CharcoalSpread;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntUnaryOperator;
import java.util.function.ToIntFunction;
import org.bukkit.World;
import org.bukkit.block.Block;

/**
 * The loaded burning piles and when each one needs its device callback, instead of TFC's per-tick {@code serverTick} and
 * per-pile random ticks. A burning pile lists itself here at every callback with the calendar tick it converts at (or
 * {@link CharcoalPit#NEVER} while a pile sits on it); one global task ({@link #PERIOD_TICKS}) compares that with the
 * calendar, so a time skip ({@code /tfp time add}) converts the pile at the next period, and draws the pile's random ticks
 * for the period at vanilla's rate ({@link CharcoalSpread#randomTicks}). Only piles in loaded chunks are listed; a chunk
 * load lists its piles again (their callback catches up). Thread-safe: listing runs on region threads, {@link #due} on the
 * global thread.
 */
public final class PitClock {
    /** Server ticks between two checks. */
    public static final int PERIOD_TICKS = 20;

    /** A pile's position. */
    record Pos(UUID world, int x, int y, int z) {
        static Pos of(Block b) {
            return new Pos(b.getWorld().getUID(), b.getX(), b.getY(), b.getZ());
        }
    }

    private record Entry(Block block, long convertTick) {
    }

    /**
     * What a pile needs this period.
     *
     * @param block       the pile
     * @param randomTicks its random ticks in the period (each one a neighbour sweep)
     * @param convert     whether its conversion tick has passed
     */
    public record Due(Block block, int randomTicks, boolean convert) {
    }

    private final Map<Pos, Entry> piles = new ConcurrentHashMap<>();

    /** Lists the burning pile at {@code block} with its conversion tick ({@link CharcoalPit#NEVER} for none). */
    public void track(Block block, long convertTick) {
        piles.put(Pos.of(block), new Entry(block, convertTick));
    }

    /** Forgets the pile at {@code block} (it was converted, broken or replaced). */
    public void untrack(Block block) {
        piles.remove(Pos.of(block));
    }

    /** Forgets the piles of an unloading chunk. */
    public void untrackChunk(World world, int chunkX, int chunkZ) {
        UUID id = world.getUID();
        piles.keySet().removeIf(p -> p.world().equals(id) && p.x() >> 4 == chunkX && p.z() >> 4 == chunkZ);
    }

    /** The listed piles (tests and debug). */
    public int size() {
        return piles.size();
    }

    /** The conversion tick listed for {@code block}, or empty. */
    public java.util.OptionalLong convertTick(Block block) {
        Entry e = piles.get(Pos.of(block));
        return e == null ? java.util.OptionalLong.empty() : java.util.OptionalLong.of(e.convertTick());
    }

    /**
     * One period: the piles that need a callback at calendar tick {@code now}. A pile due for conversion is listed again
     * by its callback.
     *
     * @param randomTickSpeed the {@code randomTickSpeed} game rule of a world
     * @param nextInt         the random draws ({@code nextInt(4096)})
     */
    public List<Due> due(long now, ToIntFunction<World> randomTickSpeed, IntUnaryOperator nextInt) {
        Objects.requireNonNull(randomTickSpeed, "randomTickSpeed");
        List<Due> out = new ArrayList<>();
        for (Entry e : piles.values()) {
            int speed = Math.max(0, randomTickSpeed.applyAsInt(e.block().getWorld()));
            int hits = CharcoalSpread.randomTicks(PERIOD_TICKS, speed, nextInt);
            boolean convert = e.convertTick() != CharcoalPit.NEVER && now >= e.convertTick();
            if (hits > 0 || convert) {
                out.add(new Due(e.block(), hits, convert));
            }
        }
        return out;
    }
}
