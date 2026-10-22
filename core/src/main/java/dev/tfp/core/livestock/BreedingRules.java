/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/entities/livestock/TFCAnimalProperties.java @ c3b4d9536 (isReadyToMate, onFertilized,
 * setBabyTraits, getBreedOffspring), src/main/java/net/dries007/tfc/common/entities/livestock/MammalProperties.java
 * (onFertilized, tickAnimalData birth check, birthChildren), src/main/java/net/dries007/tfc/common/entities/livestock/
 * TFCAnimal.java (canMate), src/main/java/net/dries007/tfc/common/entities/livestock/OviparousAnimal.java (makeEgg)
 */
package dev.tfp.core.livestock;

import dev.tfp.core.calendar.CalendarTicks;
import java.util.Optional;

/**
 * TFC's mating, pregnancy, gestation and birth rules as pure functions. Gestation is measured in calendar days
 * ({@code getTotalCalendarDays}), and the familiarity and feeding conditions use the familiarity rules in
 * {@link LivestockRules}.
 *
 * <p>The birth check is a stored-state test, so it is lazy. The caller supplies the player tick at which the gestation
 * first becomes due ({@code birthPlayerTick}, via {@link #dueCalendarTick} and the calendar's day-length mapping).
 * The offspring familiarity is taken at that tick, as TFC's check would have seen it, and the mother's decay continues
 * to the read time. The stepped and lazy results agree for grid-aligned times (see {@code LazyEqualsSteppingTest}).
 */
public final class BreedingRules {

    /** Result of {@link #fertilize}: the female and male after the mating. */
    public record Pairing(AnimalState female, AnimalState male) {
    }

    /**
     * Result of a birth: the mother after the birth, the litter size, and the familiarity each offspring starts with
     * (TFC {@code setBabyTraits} / {@code birthChildren}).
     */
    public record Birth(AnimalState mother, int kids, float offspringFamiliarity) {
    }

    private BreedingRules() {
    }

    /**
     * TFC {@code isReadyToMate}: adult, familiarity at least {@link LivestockRules#READY_TO_MATE_FAMILIARITY}, not
     * fertilized, not hungry, past the mating cooldown, and not pregnant ({@code pregnantTime <= 0}).
     */
    public static boolean isReadyToMate(AnimalState s, LivestockSpecies species, long playerTicks) {
        return LivestockRules.ageAt(s, species, playerTicks) == LivestockAge.ADULT
            && LivestockRules.familiarityAt(s, playerTicks) >= LivestockRules.READY_TO_MATE_FAMILIARITY
            && !s.fertilized()
            && !LivestockRules.isHungry(s, playerTicks)
            && s.lastMateTick() + LivestockRules.MATING_COOLDOWN_TICKS <= playerTicks
            && s.pregnantDay() <= 0;
    }

    /** TFC {@code TFCAnimal.canMate}: opposite genders, both ready to mate. */
    public static boolean canMate(AnimalState a, AnimalState b, LivestockSpecies species, long playerTicks) {
        return a.male() != b.male()
            && isReadyToMate(a, species, playerTicks)
            && isReadyToMate(b, species, playerTicks);
    }

    /**
     * TFC {@code TFCAnimalProperties.onFertilized} (called from {@code getBreedOffspring} on the female) plus
     * {@code MammalProperties.onFertilized}. The female becomes fertilized and hungry (her last feed is set to
     * a day ago). A mammal also records the calendar day of fertilization as its pregnancy day. The male is made
     * hungry and loses {@link LivestockRules#MALE_USES_PER_FERTILIZATION} uses.
     *
     * <p>If the female is already fertilized, TFC does nothing, and so does this method.
     *
     * @param calendarTicks the calendar tick at which the pairing happens, used for the pregnancy day
     * @throws IllegalArgumentException if {@code female} is male or {@code male} is female
     */
    public static Pairing fertilize(AnimalState female, AnimalState male, LivestockSpecies species, long playerTicks, long calendarTicks) {
        if (female.male() || !male.male()) {
            throw new IllegalArgumentException("fertilize needs a female and a male");
        }
        if (female.fertilized()) {
            return new Pairing(female, male);
        }
        AnimalState f = LivestockRules.catchUp(female, playerTicks)
            .withFertilized(true)
            .withLastFedTick(playerTicks - LivestockRules.TICKS_IN_DAY);
        if (species.kind() == LivestockSpecies.Kind.MAMMAL) {
            f = f.withPregnantDay(CalendarTicks.totalDays(calendarTicks));
        }
        final AnimalState m = LivestockRules.catchUp(male, playerTicks)
            .withLastFedTick(playerTicks - LivestockRules.TICKS_IN_DAY)
            .withUses(male.uses() + LivestockRules.MALE_USES_PER_FERTILIZATION);
        return new Pairing(f, m);
    }

    /**
     * TFC {@code MammalProperties.tickAnimalData} birth check: a fertilized female whose pregnancy day is positive
     * gives birth once {@code totalCalendarDays >= pregnantDay + gestationDays}.
     *
     * <p><b>Upstream quirk, kept on purpose.</b> The check is {@code pregnantTime > 0}, so a female fertilized on
     * calendar day 0 never gives birth. Her fertilized flag stays set, so the state is not reset.
     */
    public static boolean gestationDue(AnimalState s, LivestockSpecies species, long calendarTicks) {
        return !s.male()
            && s.fertilized()
            && s.pregnantDay() > 0
            && CalendarTicks.totalDays(calendarTicks) >= s.pregnantDay() + species.gestationDays();
    }

    /**
     * The calendar tick at which a fertilized mammal's gestation becomes due: the start of day
     * {@code pregnantDay + gestationDays}. The glue maps this to the player tick of the first check at or after it
     * (through the calendar's day-length mapping) and passes that tick to {@link #birth} as {@code birthPlayerTick}.
     */
    public static long dueCalendarTick(AnimalState s, LivestockSpecies species) {
        return (s.pregnantDay() + species.gestationDays()) * (long) CalendarTicks.CALENDAR_TICKS_IN_DAY;
    }

    /**
     * Applies the TFC {@code MammalProperties.tickAnimalData} birth check at {@code playerTicks}. TFC runs the decay
     * before the birth in {@code tickAnimalData}, and the offspring inherit the familiarity as it was at the birth
     * check, not at the later read. So the offspring familiarity is taken at {@code birthPlayerTick}, while the mother's
     * decay continues to {@code playerTicks}.
     * If the birth is due, the litter is {@code 1 + childDraw} and the mother is cleared (not fertilized, not
     * pregnant) and gains {@link LivestockRules#USES_PER_BIRTH} uses. Offspring familiarity follows
     * {@link #offspringFamiliarity}.
     *
     * @param birthPlayerTick the player tick of the first check at which the gestation is due, {@code <= playerTicks}
     * @param childDraw       a value in [0, childCount) from the entity's random source, giving litter size 1 to childCount
     * @return the birth, or empty if it is not due at {@code playerTicks}
     */
    public static Optional<Birth> birth(AnimalState mother, LivestockSpecies species, long playerTicks, long calendarTicks,
                                        long birthPlayerTick, int childDraw) {
        if (birthPlayerTick > playerTicks) {
            throw new IllegalArgumentException("birthPlayerTick " + birthPlayerTick + " is after playerTicks " + playerTicks);
        }
        final AnimalState s = LivestockRules.catchUp(mother, playerTicks);
        if (!gestationDue(s, species, calendarTicks)) {
            return Optional.empty();
        }
        if (childDraw < 0 || childDraw >= species.childCount()) {
            throw new IllegalArgumentException("childDraw outside [0, " + species.childCount() + "): " + childDraw);
        }
        final float offspring = offspringFamiliarity(LivestockRules.familiarityAt(mother, birthPlayerTick));
        final AnimalState after = s.withFertilized(false)
            .withPregnantDay(AnimalState.NOT_PREGNANT)
            .withUses(s.uses() + LivestockRules.USES_PER_BIRTH);
        return Optional.of(new Birth(after, 1 + childDraw, offspring));
    }

    /**
     * TFC {@code f < 0.9F ? f / 2.0F : f * 0.9F}, used for offspring of both mammals and eggs. The comparison is done
     * in {@code float}, as TFC does, so 0.9 exactly as a float is not less than 0.9.
     */
    public static float offspringFamiliarity(float parent) {
        return parent < 0.9f ? parent / 2f : parent * 0.9f;
    }

    /**
     * TFC {@code OviparousAnimal.makeEgg}: a fertilized egg hatches {@link LivestockSpecies#hatchDays()} calendar days
     * after it is laid, so the hatch day is {@code totalCalendarDays + hatchDays}.
     */
    public static long eggHatchDay(long calendarTicks, LivestockSpecies species) {
        return CalendarTicks.totalDays(calendarTicks) + species.hatchDays();
    }
}
