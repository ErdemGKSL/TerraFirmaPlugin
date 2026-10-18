/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/entities/livestock/OviparousAnimal.java (makeEgg, hasProduct),
 * src/main/java/net/dries007/tfc/common/component/EggComponent.java (fields, canHatch) and
 * src/main/java/net/dries007/tfc/config/animals/OviparousAnimalConfig.java (hatchDays) @ c3b4d9536
 */
package dev.tfp.core.livestock.products;

import dev.tfp.core.calendar.CalendarTicks;
import dev.tfp.core.livestock.AnimalState;
import dev.tfp.core.livestock.BreedingRules;
import dev.tfp.core.livestock.LivestockRules;
import dev.tfp.core.livestock.LivestockSpecies;

import java.util.Optional;

/**
 * The egg as TFC stores it and the hen's part in laying it, as pure values. Nothing here is ticked.
 *
 * <p>A <b>fertile</b> egg carries an {@code EggComponent}: the hatch day and the entity NBT of the chick, which is an
 * {@link AnimalState} born at the lay tick with the offspring familiarity. A <b>plain</b> egg has no component.
 */
public final class EggRules {
    private EggRules() {
    }

    /**
     * A fertile egg: {@code EggComponent} with {@code fertilized == true}.
     *
     * @param hatchDay  the calendar day it hatches, {@code totalCalendarDays at lay + hatchDays}
     * @param species   the species of the chick
     * @param hatchling the chick's stored state, born at the lay tick
     */
    public record FertileEgg(long hatchDay, LivestockSpecies species, AnimalState hatchling) {
        public FertileEgg {
            if (species == null || hatchling == null) {
                throw new IllegalArgumentException("species and hatchling are required");
            }
        }
    }

    /**
     * The result of {@link #makeEgg}.
     *
     * @param egg the egg made: a fertile egg if the hen was fertilized, otherwise empty (a plain egg)
     * @param hen the hen after the lay: caught up to the lay tick, with one more use. The caller clears the
     *            fertilized flag and starts the product cooldown ({@link NestRules} does both).
     */
    public record Laying(Optional<FertileEgg> egg, AnimalState hen) {
    }

    /**
     * TFC {@code OviparousAnimal.makeEgg}. A fertilized hen makes a fertile egg: the chick is born at {@code playerTicks}
     * with the offspring familiarity ({@link BreedingRules#offspringFamiliarity}) of the hen's familiarity at that tick,
     * and the hatch day is {@link BreedingRules#eggHatchDay}. The hen gets one use either way.
     *
     * @param playerTicks   the lay tick in player ticks
     * @param calendarTicks the calendar tick at the lay, for the hatch day
     * @param chickMale     the gender of the chick, drawn by the caller only when {@code hen.fertilized()} (TFC draws the
     *                      gender only for a fertilized egg)
     * @throws IllegalArgumentException if the species is not oviparous
     */
    public static Laying makeEgg(LivestockSpecies species, AnimalState hen, long playerTicks, long calendarTicks,
                                 boolean chickMale) {
        if (species.kind() != LivestockSpecies.Kind.OVIPAROUS) {
            throw new IllegalArgumentException(species + " is not oviparous");
        }
        final AnimalState caught = LivestockRules.catchUp(hen, playerTicks);
        final AnimalState after = caught.withUses(caught.uses() + ProductRules.USES_PER_HARVEST);
        if (!caught.fertilized()) {
            return new Laying(Optional.empty(), after);
        }
        final float familiarity = (float) LivestockRules.familiarityAt(hen, playerTicks);
        final AnimalState chick = AnimalState.offspring(chickMale, BreedingRules.offspringFamiliarity(familiarity), playerTicks);
        final FertileEgg egg = new FertileEgg(BreedingRules.eggHatchDay(calendarTicks, species), species, chick);
        return new Laying(Optional.of(egg), after);
    }

    /**
     * TFC {@code EggComponent.canHatch}: fertile (always true here), the hatch day has come, and the egg is not rotten.
     * A rotten egg never hatches, even after its day.
     *
     * @param calendarTicks the calendar tick now
     * @param rotten        whether the egg item is rotten now (the glue reads {@code FoodCapability.isRotten})
     */
    public static boolean canHatch(FertileEgg egg, long calendarTicks, boolean rotten) {
        return !rotten && egg.hatchDay() <= CalendarTicks.totalDays(calendarTicks);
    }

    /**
     * The calendar tick at which the egg first becomes hatchable: the start of its hatch day. The glue maps it to the
     * player tick of the nest check at or after it, as for births.
     */
    public static long hatchCalendarTick(FertileEgg egg) {
        return egg.hatchDay() * (long) CalendarTicks.CALENDAR_TICKS_IN_DAY;
    }
}
