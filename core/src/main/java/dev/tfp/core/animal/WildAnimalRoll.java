/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/entities/prey/WildAnimal.java @ c3b4d9536
 * (finalizeSpawn, tick, isBaby/getAge) and src/main/java/net/dries007/tfc/common/entities/TFCEntities.java (which
 * registrations are WildAnimal subclasses), same commit
 */
package dev.tfp.core.animal;

import java.util.Set;

/**
 * TFC's wild animals ({@code WildAnimal} and its subclasses: {@code Prey}, {@code WingedPrey}, {@code RammingPrey},
 * {@code Pest}, {@code Predator}, {@code FelinePredator}, {@code AmphibiousPredator}, {@code PackPredator},
 * {@code AmphibiousAnimal}): a gender and a baby flag, no genetic size.
 *
 * <p>TFC's spawn ({@code finalizeSpawn}) draws {@code nextBoolean()} for the gender, then {@code nextFloat() < 0.1f}
 * for the baby flag. A baby grows up in {@code tick()}: on every game tick with {@code gameTime % 4000 == 0}, with
 * chance {@code nextInt(2000) == 0}. That per-tick roll is replaced here by a lazy grow-up tick drawn once at spawn
 * ({@link #growTickFromUniform}): the number of 4000-tick boundaries until the first passing roll is geometric with
 * {@code p = 1/2000}, so one uniform draw gives the same distribution (not the same draws). The staggered check clears
 * the flag once that tick has passed. While a baby, vanilla draws it at half size with a halved hitbox
 * ({@code getAge() == -24000}).
 */
public final class WildAnimalRoll {
    /** TFC literal {@code 0.1f}: the chance that a spawned wild animal is a baby. */
    public static final float BABY_CHANCE = 0.1f;
    /** TFC literal {@code gameTime % 4000 == 0}: the ticks on which a baby may grow up. */
    public static final long GROW_INTERVAL_TICKS = 4000;
    /** TFC literal {@code random.nextInt(2000) == 0}: the grow-up roll on such a tick. */
    public static final int GROW_ONE_IN = 2000;

    /** TFC entity ids whose class extends {@code WildAnimal} (31 of TFCEntities' living registrations). */
    public static final Set<String> SPECIES = Set.of(
            // aquatic/AmphibiousAnimal
            "tfc:turtle", "tfc:penguin", "tfc:leopard_seal",
            // predator/Predator, FelinePredator, AmphibiousPredator, ai/predator/PackPredator
            "tfc:polar_bear", "tfc:grizzly_bear", "tfc:black_bear",
            "tfc:cougar", "tfc:panther", "tfc:lion", "tfc:sabertooth", "tfc:tiger",
            "tfc:crocodile",
            "tfc:wolf", "tfc:hyena", "tfc:direwolf",
            // prey/Prey, WingedPrey, RammingPrey, Pest
            "tfc:deer", "tfc:caribou", "tfc:bongo", "tfc:gazelle",
            "tfc:grouse", "tfc:pheasant", "tfc:turkey", "tfc:peafowl",
            "tfc:boar", "tfc:moose", "tfc:wildebeest", "tfc:bison",
            "tfc:rat", "tfc:lemming", "tfc:mongoose", "tfc:jerboa");

    /** The random draws, in the order they are made. */
    public interface Draws {
        boolean nextBoolean();

        float nextFloat();

        /** A value in [0, 1); drawn only for a baby, after TFC's draws (the lazy grow-up). */
        double nextDouble();
    }

    /**
     * A rolled wild animal.
     *
     * @param male     the gender
     * @param baby     TFC's baby flag
     * @param growTick the tick at which the baby grows up, or {@link AnimalRecord#NO_GROW_TICK} for an adult
     */
    public record Rolled(boolean male, boolean baby, long growTick) {
    }

    private WildAnimalRoll() {
    }

    /** Whether {@code id} is one of TFC's {@code WildAnimal} species. */
    public static boolean isWildAnimal(String id) {
        return SPECIES.contains(id);
    }

    /**
     * TFC {@code finalizeSpawn} at tick {@code now}: {@code nextBoolean()} (gender), {@code nextFloat() < 0.1f} (baby),
     * then for a baby one {@code nextDouble()} for the lazy grow-up tick. Both TFC draws are always made; the hint and
     * {@code male} only override their results ({@code CHILD}: a baby, {@code ADULT}/{@code OLD}: not a baby, as TFC's
     * wild animals have no old age).
     */
    public static Rolled roll(Draws draws, long now, AnimalSpawnRoll.AgeHint hint, Boolean male) {
        boolean drawnMale = draws.nextBoolean();
        boolean drawnBaby = draws.nextFloat() < BABY_CHANCE;
        boolean baby = switch (hint) {
            case RANDOM -> drawnBaby;
            case CHILD -> true;
            case ADULT, OLD -> false;
        };
        long growTick = baby ? growTickFromUniform(now, draws.nextDouble()) : AnimalRecord.NO_GROW_TICK;
        return new Rolled(male != null ? male : drawnMale, baby, growTick);
    }

    /**
     * The lazy form of TFC's grow-up roll: the tick of the first passing roll for a baby spawned at {@code spawnTick}.
     * The roll is made on the boundaries {@code b1 = (floor(spawnTick / 4000) + 1) * 4000, b1 + 4000, ...}, each with
     * chance {@code 1/2000}, so the number of boundaries up to the first hit is geometric:
     * {@code k = max(1, ceil(ln(1 - u) / ln(1999 / 2000)))} and the tick is {@code b1 + (k - 1) * 4000}.
     *
     * @param u a uniform draw in [0, 1)
     */
    public static long growTickFromUniform(long spawnTick, double u) {
        if (u < 0 || u >= 1) {
            throw new IllegalArgumentException("u must be in [0, 1), was " + u);
        }
        double q = (double) (GROW_ONE_IN - 1) / GROW_ONE_IN;
        long k = Math.max(1L, (long) Math.ceil(Math.log(1 - u) / Math.log(q)));
        return firstBoundaryAfter(spawnTick) + (k - 1) * GROW_INTERVAL_TICKS;
    }

    /** The first grow-up boundary strictly after {@code tick}. */
    public static long firstBoundaryAfter(long tick) {
        return (Math.floorDiv(tick, GROW_INTERVAL_TICKS) + 1) * GROW_INTERVAL_TICKS;
    }

    /**
     * TFC's per-tick test, kept for the lazy-vs-stepped test: {@code gameTime % 4000 == 0 && nextInt(2000) == 0}. The
     * roll is drawn only on a boundary tick, as TFC's {@code &&} does.
     *
     * @param nextInt draws a value in [0, bound)
     */
    public static boolean growsUpOnTick(long gameTime, java.util.function.IntUnaryOperator nextInt) {
        return Math.floorMod(gameTime, GROW_INTERVAL_TICKS) == 0 && nextInt.applyAsInt(GROW_ONE_IN) == 0;
    }

    /** Whether a record with this baby flag and grow tick is still a baby at {@code now}. */
    public static boolean babyAt(boolean baby, long growTick, long now) {
        return baby && now < growTick;
    }
}
