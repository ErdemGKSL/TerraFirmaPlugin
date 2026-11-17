/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/items/TorchItem.java @ c3b4d9536 (onEntityItemUpdate)
 */
package dev.tfp.paper.light;

import dev.tfp.core.light.TorchRules;
import java.util.Objects;
import java.util.random.RandomGenerator;

/**
 * TFC {@code TorchItem.onEntityItemUpdate}, replayed lazily over a span of the item entity's ticks. Pure; any thread.
 *
 * <p>TFC runs it on every tick of a dropped torch item:
 * <ul>
 *   <li>In a fire-extinguishing fluid ({@link Place#FLUID}): {@link TorchRules#extinguished} torches go out (each rolls the
 *       torch loot with {@code burnt_out}), the extinguish sound plays, and the stack shrinks; an empty stack is
 *       discarded.</li>
 *   <li>On a {@code #tfc:lit_by_dropped_torch} block (the item's own block, {@link Place#LIT_INSIDE}, or the block below
 *       when its own block is empty, {@link Place#LIT_BELOW}): past the age requirement (160 inside, 20 above) a
 *       {@code nextFloat() < 0.01} draw starts a strong fire there and kills the item. Otherwise the server still draws
 *       {@code nextDouble() <= 0.1} for the lava particle (three more {@code nextDouble} draws when it would spawn one); we
 *       keep those draws so the order of draws is TFC's, but spawn no particle (particles only on explicit actions).</li>
 * </ul>
 */
public final class DroppedTorches {
    /** Where the item is, as the rule sees it. */
    public enum Place {
        /** In a fluid that can extinguish fire (water, waterlogged blocks; not lava). */
        FLUID,
        /** Inside a {@code #tfc:lit_by_dropped_torch} block. */
        LIT_INSIDE,
        /** In an empty block above a {@code #tfc:lit_by_dropped_torch} block. */
        LIT_BELOW,
        /** Anywhere else: nothing happens. */
        OTHER;

        /** TFC's {@code ageRequirement} for the lit places. */
        int ageRequirement() {
            return TorchRules.droppedAgeRequirement(this == LIT_BELOW);
        }
    }

    /**
     * What the replayed ticks did.
     *
     * @param extinguished torches that went out (each one rolls the burnt-out loot once)
     * @param batches      ticks on which torches went out (TFC plays the extinguish sound on each)
     * @param left         torches left in the stack (0: the item is discarded)
     * @param lit          a strong fire was started and the item killed
     */
    public record Outcome(int extinguished, int batches, int left, boolean lit) {
        /** Nothing happened. */
        public boolean none() {
            return extinguished == 0 && !lit;
        }

        /** The item entity is gone after the replay. */
        public boolean removed() {
            return left == 0 || lit;
        }
    }

    private DroppedTorches() {
    }

    /**
     * Replays the ticks at item ages {@code fromAge + 1 .. toAge} with the item at {@code place}.
     *
     * @param count the stack size before the span
     */
    public static Outcome replay(int count, Place place, int fromAge, int toAge, RandomGenerator random) {
        Objects.requireNonNull(place, "place");
        Objects.requireNonNull(random, "random");
        if (count < 1) {
            throw new IllegalArgumentException("count must be >= 1: " + count);
        }
        int left = count;
        int out = 0;
        int batches = 0;
        for (int age = fromAge + 1; age <= toAge; age++) {
            switch (place) {
                case FLUID -> {
                    int amount = TorchRules.extinguished(left, random::nextInt);
                    left -= amount;
                    out += amount;
                    batches++;
                    if (left == 0) {
                        return new Outcome(out, batches, 0, false);
                    }
                }
                case LIT_INSIDE, LIT_BELOW -> {
                    // TFC: itemEntity.getAge() > ageRequirement && level.random.nextFloat() < 0.01f (short-circuit)
                    if (age > place.ageRequirement() && random.nextFloat() < TorchRules.DROPPED_LIGHT_CHANCE) {
                        return new Outcome(0, 0, left, true);
                    }
                    if (random.nextDouble() <= 0.1) {
                        random.nextDouble();
                        random.nextDouble();
                        random.nextDouble();
                    }
                }
                case OTHER -> {
                    return new Outcome(0, 0, left, false);
                }
            }
        }
        return new Outcome(out, batches, left, false);
    }
}
