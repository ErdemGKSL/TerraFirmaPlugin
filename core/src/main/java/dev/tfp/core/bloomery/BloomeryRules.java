/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/BloomeryBlockEntity.java @ c3b4d9536 (serverTick capacity and
 *       excess trim, addItemsFromWorld recipe selection and alternating intake, updateCachedRecipe, calculateCapacity,
 *       getRemainingTicks, onCalendarUpdate, completeRecipe: produced amount, output temperature, inputs cleared),
 *       src/main/java/net/dries007/tfc/common/blockentities/BloomBlockEntity.java @ c3b4d9536 (TOTAL_LAYERS, getState
 *       layer rule), src/main/java/net/dries007/tfc/common/blocks/devices/BloomeryBlock.java @ c3b4d9536 (getChimneyLevels
 *       count, calculateCapacity's chimney multiplier), src/main/java/net/dries007/tfc/util/Helpers.java @ c3b4d9536
 *       (alternatingConsumeItemsFromEntitiesIndividually order), src/main/java/net/dries007/tfc/config/ServerConfig.java
 *       @ c3b4d9536 (bloomeryCapacity, bloomeryMaxChimneyHeight defaults)
 */
package dev.tfp.core.bloomery;

import java.util.List;

/**
 * The bloomery rules, evaluated lazily from calendar ticks and from facts the caller has already read from the world.
 * Nothing here reads blocks or items, and nothing ticks.
 *
 * <p><b>Facts in, decisions out.</b> The multiblock shape check reads blocks, so the caller supplies its result:
 * which chimney levels are formed (in order from the bottom), and whether the base is formed. The caller also supplies
 * the world items as {@link BloomeryItem}s with their melts already looked up. The rules return which recipe applies,
 * how many units to take, what the lit bloomery becomes, and the bloom it makes.
 *
 * <p><b>Lifecycle.</b> TFC completes a recipe at the tick {@code litTick + duration}, through the calendar update. A
 * lit bloomery whose structure fails a check (its capacity drops below its contents) returns to unlit with its inputs
 * kept and makes no bloom. {@link #phaseAt} gives that outcome for a span, given the first check tick that failed.
 *
 * <p><b>No randomness.</b> Nothing here draws random numbers.
 */
public final class BloomeryRules {
    /** {@code bloomeryCapacity} at the TFC default: items per level of chimney. */
    public static final int DEFAULT_CAPACITY_PER_LEVEL = 16;
    /** {@code bloomeryMaxChimneyHeight} at the TFC default. */
    public static final int DEFAULT_MAX_CHIMNEY_HEIGHT = 3;
    /** The number of visible layers of a bloom at full size ({@code BloomBlockEntity.TOTAL_LAYERS}). */
    public static final int BLOOM_TOTAL_LAYERS = 8;
    /** The tick value meaning "never" (a bloomery that never failed). */
    public static final long NEVER = Long.MAX_VALUE;

    private BloomeryRules() {
    }

    /**
     * The config values of the bloomery.
     *
     * @param capacityPerLevel  items per level of chimney ({@code bloomeryCapacity}, at least 1)
     * @param maxChimneyHeight  the most chimney levels that count ({@code bloomeryMaxChimneyHeight}, at least 1)
     */
    public record Config(int capacityPerLevel, int maxChimneyHeight) {
        public static final Config DEFAULT = new Config(DEFAULT_CAPACITY_PER_LEVEL, DEFAULT_MAX_CHIMNEY_HEIGHT);

        public Config {
            if (capacityPerLevel < 1) {
                throw new IllegalArgumentException("capacityPerLevel must be >= 1: " + capacityPerLevel);
            }
            if (maxChimneyHeight < 1) {
                throw new IllegalArgumentException("maxChimneyHeight must be >= 1: " + maxChimneyHeight);
            }
        }
    }

    /** The lit phase of a bloomery at a tick. Unlit is the caller's own fact (it was never lit, or it has finished). */
    public enum Phase {
        /** Lit and burning. */
        LIT,
        /** The burn time ended: the inputs are the bloom ({@link #produce}) and the bloomery is unlit. */
        FIRED,
        /** A check found the structure too small for its contents: unlit, inputs kept, no bloom. */
        HALTED
    }

    /**
     * The bloom a finished recipe makes.
     *
     * @param item        the bloom item id
     * @param count       the bloom's count, at least 1 (the number of batches of the recipe that were made)
     * @param temperature the temperature the bloom starts at, or {@code null} when the item has no heating recipe
     */
    public record Bloom(String item, int count, Float temperature) {
    }

    /**
     * The number of chimney levels that count: the leading formed levels, capped at {@code maxHeight}. TFC's
     * {@code getChimneyLevels} stops at the first level that does not match.
     *
     * @param levelFormed whether each chimney level, from the bottom (level 1), is formed
     * @param maxHeight  the config maximum
     */
    public static int chimneyLevels(boolean[] levelFormed, int maxHeight) {
        int limit = Math.min(levelFormed.length, maxHeight);
        for (int i = 0; i < limit; i++) {
            if (!levelFormed[i]) {
                return i;
            }
        }
        return limit;
    }

    /**
     * TFC {@code calculateCapacity}: no capacity unless the base is formed, otherwise the chimney levels times the
     * capacity per level.
     *
     * @param formed         whether the base multiblock is formed
     * @param chimneyLevels  the counted chimney levels ({@link #chimneyLevels})
     * @param capacityPerLevel the config capacity per level
     */
    public static int capacity(boolean formed, int chimneyLevels, int capacityPerLevel) {
        if (!formed) {
            return 0;
        }
        return Math.multiplyExact(chimneyLevels, capacityPerLevel);
    }

    /**
     * The number of items above a capacity, which TFC pops off the end of the contents on each check.
     *
     * @return {@code max(0, size - capacity)}
     */
    public static int excess(int size, int capacity) {
        return Math.max(0, size - capacity);
    }

    /**
     * TFC {@code updateCachedRecipe}: the first recipe that matches the first content unit with a melt, scanning the
     * contents in order and then the recipes in order.
     *
     * @return the recipe, or {@code null} when no unit's melt matches any recipe
     */
    public static BloomeryRecipe selectRecipe(List<BloomeryRecipe> recipes, List<BloomeryItem> units) {
        for (BloomeryItem unit : units) {
            if (unit.fluid() == null) {
                continue;
            }
            for (BloomeryRecipe recipe : recipes) {
                if (recipe.matchesInput(unit.fluid())) {
                    return recipe;
                }
            }
        }
        return null;
    }

    /**
     * Whether the contents must be dumped before a new recipe is looked for: they are not empty and none of them
     * matches a recipe (TFC's reload path, where the recipe changed under the contents).
     */
    public static boolean mustDump(List<BloomeryRecipe> recipes, List<BloomeryItem> contents) {
        return !contents.isEmpty() && selectRecipe(recipes, contents) == null;
    }

    /**
     * The recipe the intake works with, as TFC's {@code addItemsFromWorld}: the recipe of the contents if there is one,
     * otherwise the first recipe matched by the world items. Call {@link #mustDump} first; after a dump the contents are
     * empty.
     *
     * @return the recipe, or {@code null} when none applies
     */
    public static BloomeryRecipe intakeRecipe(List<BloomeryRecipe> recipes, List<BloomeryItem> contents, List<BloomeryItem> world) {
        BloomeryRecipe fromContents = selectRecipe(recipes, contents);
        return fromContents != null ? fromContents : selectRecipe(recipes, world);
    }

    /**
     * TFC's intake from the world. The world items are split into catalysts (matching the recipe's catalyst and not a
     * primary input) and inputs (primary inputs). Units are taken one at a time, alternating catalyst then input, from
     * each set in order, up to the free capacity. Nothing is taken unless there is at least one catalyst and one input,
     * or the bloomery already holds inputs and there is a catalyst or no input in the world.
     *
     * @param recipe   the recipe from {@link #intakeRecipe}; a {@code null} recipe takes nothing
     * @param world    the world items, one entry per stack
     * @param current  the number of units already in the bloomery
     * @param capacity the capacity from {@link #capacity}
     * @return the units taken from each world entry, in the same order as {@code world}
     */
    public static int[] intake(BloomeryRecipe recipe, List<BloomeryItem> world, int current, int capacity) {
        int[] taken = new int[world.size()];
        if (recipe == null || current >= capacity) {
            return taken;
        }
        boolean[] input = new boolean[world.size()];
        boolean[] catalyst = new boolean[world.size()];
        boolean anyCatalyst = false;
        boolean anyInput = false;
        for (int i = 0; i < world.size(); i++) {
            BloomeryItem unit = world.get(i);
            if (recipe.isPrimary(unit)) {
                input[i] = true;
                anyInput = true;
            } else if (recipe.matchesCatalyst(unit.item(), unit.count())) {
                catalyst[i] = true;
                anyCatalyst = true;
            }
        }
        boolean accept = (anyCatalyst && anyInput) || (current > 0 && (anyCatalyst || !anyInput));
        if (!accept) {
            return taken;
        }

        int[] remaining = new int[world.size()];
        int[] catalystQueue = indices(catalyst, world, remaining);
        int[] inputQueue = indices(input, world, remaining);
        int maximum = capacity - current;
        int consumed = 0;
        int c = 0;
        int n = 0;
        while (consumed < maximum && (c < catalystQueue.length || n < inputQueue.length)) {
            if (c < catalystQueue.length) {
                int idx = catalystQueue[c];
                taken[idx]++;
                remaining[idx]--;
                consumed++;
                if (remaining[idx] == 0) {
                    c++;
                }
                if (consumed == maximum) {
                    break;
                }
            }
            if (n < inputQueue.length) {
                int idx = inputQueue[n];
                taken[idx]++;
                remaining[idx]--;
                consumed++;
                if (remaining[idx] == 0) {
                    n++;
                }
            }
        }
        return taken;
    }

    /**
     * The bloom the bloomery makes when a recipe completes, as TFC's {@code completeRecipe}. Every unit is a primary
     * input (its melt adds to the fluid total) or a catalyst (any other unit counts as one catalyst, as in TFC). The bloom
     * count is the least of the batches the fluid pays for and the batches the catalysts pay for. The contents are
     * consumed whether or not a bloom results, so the caller clears them either way.
     *
     * @param recipe                 the recipe the contents were gathered for
     * @param contents               the units in the bloomery at completion
     * @param resultMeltTemperature  the melting temperature of the result item's heating recipe, or {@code null} when it
     *                               has none; the bloom starts 1 degree below it, as TFC does
     * @return the bloom, or {@code null} when the contents make no batch
     */
    public static Bloom produce(BloomeryRecipe recipe, List<BloomeryItem> contents, Float resultMeltTemperature) {
        long fluid = 0;
        long catalysts = 0;
        for (BloomeryItem unit : contents) {
            if (recipe.isPrimary(unit)) {
                fluid += unit.fluidAmount();
            } else {
                catalysts++;
            }
        }
        long batches = Math.min(fluid / recipe.inputAmount(), catalysts / recipe.catalystCount());
        if (batches <= 0) {
            return null;
        }
        Float temperature = resultMeltTemperature == null ? null : resultMeltTemperature - 1f;
        return new Bloom(recipe.result(), (int) batches, temperature);
    }

    /**
     * The layers a bloom shows for a count, as TFC's {@code BloomBlockEntity.getState}. A bloom holding at most
     * {@link #BLOOM_TOTAL_LAYERS} items shows one layer per item; a larger bloom scales its count onto the layers.
     *
     * @param count    the current count
     * @param maxCount the count the bloom was made with
     * @return the layers, {@code 0} when the count is 0 or less (air), otherwise in {@code [1, BLOOM_TOTAL_LAYERS]}
     */
    public static int bloomLayers(int count, int maxCount) {
        if (count <= 0) {
            return 0;
        }
        if (maxCount <= BLOOM_TOTAL_LAYERS) {
            return count;
        }
        long scaled = (long) BLOOM_TOTAL_LAYERS * count / maxCount;
        return (int) Math.clamp(scaled, 1, BLOOM_TOTAL_LAYERS);
    }

    /** Ticks left before the recipe completes at {@code now}, as TFC's {@code getRemainingTicks}; not clamped. */
    public static long remainingTicks(long now, long litTick, int duration) {
        return litTick + duration - now;
    }

    /**
     * The phase of a lit bloomery at a calendar tick.
     *
     * <p>A halt is the first check tick that found the structure too small (the caller aligns it to TFC's 20-tick
     * checks), or {@link #NEVER}. A halt before the finish tick wins. A halt at or after the finish tick does not stop a
     * finished recipe, which fires at {@code litTick + duration}.
     *
     * @param now        the calendar tick, at or after {@code litTick}
     * @param litTick    the tick the bloomery was lit
     * @param duration   the recipe duration
     * @param haltedAt   the first failed check tick, or {@link #NEVER}
     */
    public static Phase phaseAt(long now, long litTick, int duration, long haltedAt) {
        long finish = litTick + duration;
        if (haltedAt < finish) {
            return now >= haltedAt ? Phase.HALTED : Phase.LIT;
        }
        return now >= finish ? Phase.FIRED : Phase.LIT;
    }

    /** The indices of the flagged world entries, in order; sets {@code remaining} to each flagged entry's stack count. */
    private static int[] indices(boolean[] flags, List<BloomeryItem> world, int[] remaining) {
        int count = 0;
        for (boolean f : flags) {
            if (f) {
                count++;
            }
        }
        int[] out = new int[count];
        int k = 0;
        for (int i = 0; i < flags.length; i++) {
            if (flags[i]) {
                remaining[i] = world.get(i).count();
                out[k++] = i;
            }
        }
        return out;
    }
}
