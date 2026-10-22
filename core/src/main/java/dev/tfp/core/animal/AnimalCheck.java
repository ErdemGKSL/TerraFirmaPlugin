/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/entities/livestock/TFCAnimalProperties.java @ c3b4d9536
 * (tickAnimalData: the age part, getAgeType) and src/main/java/net/dries007/tfc/common/entities/prey/WildAnimal.java
 * (tick: the grow-up, isBaby), same commit
 */
package dev.tfp.core.animal;

import dev.tfp.core.livestock.LivestockAge;
import dev.tfp.core.livestock.LivestockRules;
import dev.tfp.core.livestock.LivestockSpecies;
import java.util.Optional;

/**
 * The per-animal check decision (M5-ENT-01), pure. TFC runs {@code tickAnimalData} every 20 game ticks and updates
 * {@code lastAge} when the stage changes. Our glue runs this on a staggered 20-tick entity task and when the model is
 * (re)created: it derives the stage lazily from the stored ticks, applies TFC's one-shot old-age mark
 * ({@link LivestockRules#markElderlyIfDue}), clears a wild baby's flag once its lazy grow-up tick has passed
 * ({@link WildAnimalRoll}), and says whether the record must be written and whether the visible stage
 * changed. Nothing is counted between checks, so a gap (unloaded chunk) changes nothing but the read time.
 */
public final class AnimalCheck {
    /** TFC's check interval, {@code gameTime % 20 == 0}. */
    public static final long PERIOD_TICKS = LivestockRules.CHECK_INTERVAL_TICKS;

    /**
     * @param record        the record after the check (the same instance when unchanged)
     * @param age           the stage at the check tick
     * @param recordChanged the record must be written back
     * @param stageChanged  {@code age} differs from the stage last applied (scale and baby flag must be re-applied)
     */
    public record Decision(AnimalRecord record, LivestockAge age, boolean recordChanged, boolean stageChanged) {
    }

    private AnimalCheck() {
    }

    /**
     * TFC {@code getAgeType()} for livestock. Animals without livestock rules (TFC's wild animals) are
     * {@link LivestockAge#CHILD} while their baby flag holds ({@code WildAnimal.isBaby}) and adult otherwise: they have
     * no old age.
     */
    public static LivestockAge ageAt(AnimalRecord record, long playerTicks) {
        Optional<LivestockSpecies> species = species(record);
        if (species.isPresent()) {
            return LivestockRules.ageAt(record.state(), species.get(), playerTicks);
        }
        return record.wildBabyAt(playerTicks) ? LivestockAge.CHILD : LivestockAge.ADULT;
    }

    /**
     * One check at {@code playerTicks}.
     *
     * @param dayDraw     the old-age offset draw in [0, {@link LivestockRules#OLD_DAY_DRAWS}), used only if the mark is due
     * @param lastApplied the stage the glue last applied, or null if none yet
     */
    public static Decision decide(AnimalRecord record, long playerTicks, int dayDraw, LivestockAge lastApplied) {
        AnimalRecord next = record;
        Optional<LivestockSpecies> species = species(record);
        if (species.isPresent()) {
            var marked = LivestockRules.markElderlyIfDue(record.state(), species.get(), playerTicks, dayDraw);
            if (!marked.equals(record.state())) {
                next = record.withState(marked);
            }
        } else if (record.baby() && !record.wildBabyAt(playerTicks)) {
            next = record.grownUp(); // TFC WildAnimal.tick: setBaby(false), at the lazily drawn tick
        }
        LivestockAge age = ageAt(next, playerTicks);
        return new Decision(next, age, next != record, age != lastApplied);
    }

    private static Optional<LivestockSpecies> species(AnimalRecord record) {
        return CarrierTable.get().find(record.species()).flatMap(Carrier::livestock);
    }
}
