/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * files: src/main/java/net/dries007/tfc/common/entities/livestock/TFCAnimalProperties.java, Mammal.java,
 * MammalProperties.java @ c3b4d9536
 */
package dev.tfp.paper.livestock;

import dev.tfp.core.animal.AnimalRecord;
import dev.tfp.core.animal.Carrier;
import dev.tfp.core.animal.CarrierTable;
import dev.tfp.core.clock.ClockSnapshot;
import dev.tfp.core.livestock.AnimalState;
import dev.tfp.core.livestock.BreedingRules;
import dev.tfp.core.livestock.LivestockSpecies;
import dev.tfp.paper.animal.AnimalPdc;
import dev.tfp.paper.animal.AnimalService;
import dev.tfp.paper.animal.SpeciesBehaviour;
import dev.tfp.paper.scheduler.Scheduler;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;

/**
 * The livestock rules on every TFC livestock carrier (PAR-FAUNA-05): on each staggered check an adult female that has
 * a ready male nearby is fertilized (TFC's mating goal, minus the walk to each other, which the livestock AI of
 * PAR-FAUNA-08 adds), and a pregnant mammal whose gestation is due gives birth. The decisions are TFC's
 * ({@link BreedingRules}); this class only reads and writes the records on the animals' own threads.
 *
 * <p>Vanilla goals stay (they are replaced by PAR-FAUNA-08). Vanilla breeding is already cancelled by the service.
 */
final class LivestockBehaviour implements SpeciesBehaviour {
    /** How far a ready pair may be apart and still mate (TFC's breed goal looks 8 blocks around). */
    static final double MATE_RANGE = 8.0;

    private final AnimalService animals;
    private final Scheduler scheduler;
    private final Supplier<ClockSnapshot> clock;

    LivestockBehaviour(AnimalService animals, Scheduler scheduler, Supplier<ClockSnapshot> clock) {
        this.animals = animals;
        this.scheduler = scheduler;
        this.clock = clock;
    }

    @Override
    public Set<String> species() {
        return CarrierTable.get().all().stream().filter(c -> c.livestock().isPresent()).map(Carrier::id)
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public boolean keepVanillaGoals() {
        return true;
    }

    @Override
    public void onCheck(Check check) {
        Optional<LivestockSpecies> species = animals.table().find(check.record().species()).flatMap(Carrier::livestock);
        ClockSnapshot now = clock.get();
        if (species.isEmpty() || !now.isSet()) {
            return;
        }
        AnimalState state = check.record().state();
        LivestockSpecies sp = species.get();
        if (!state.male() && state.fertilized() && sp.kind() == LivestockSpecies.Kind.MAMMAL) {
            giveBirth(check, sp, now);
        } else if (!state.male() && BreedingRules.isReadyToMate(state, sp, now.playerTicks())) {
            mate(check, sp, now);
        }
    }

    /** TFC {@code MammalProperties.tickAnimalData}: the birth check of a pregnant mammal. */
    private void giveBirth(Check check, LivestockSpecies sp, ClockSnapshot now) {
        int draw = ThreadLocalRandom.current().nextInt(sp.childCount());
        Optional<BreedingRules.Birth> birth = BreedingRules.birth(check.record().state(), sp, now.playerTicks(),
                now.calendarTicks(), now.playerTicks(), draw);
        if (birth.isEmpty()) {
            return;
        }
        Entity mother = check.entity();
        AnimalRecord record = check.record();
        AnimalPdc.write(mother.getPersistentDataContainer(), record.withState(birth.get().mother()));
        Carrier carrier = animals.table().find(record.species()).orElseThrow();
        for (int i = 0; i < birth.get().kids(); i++) {
            animals.spawnOffspring(mother.getLocation(), carrier, birth.get().offspringFamiliarity());
        }
    }

    /** TFC's mating: a ready female and a ready male of one species within range are paired; the female is fertilized. */
    private void mate(Check check, LivestockSpecies sp, ClockSnapshot now) {
        Entity female = check.entity();
        for (Entity other : female.getNearbyEntities(MATE_RANGE, MATE_RANGE / 2, MATE_RANGE)) {
            if (other == female || !Bukkit.isOwnedByCurrentRegion(other)) {
                continue;
            }
            Optional<AnimalRecord> maybe = animals.record(other);
            if (maybe.isEmpty() || !maybe.get().species().equals(check.record().species())) {
                continue;
            }
            AnimalState male = maybe.get().state();
            if (!male.male() || !BreedingRules.canMate(check.record().state(), male, sp, now.playerTicks())) {
                continue;
            }
            BreedingRules.Pairing pairing = BreedingRules.fertilize(check.record().state(), male, sp, now.playerTicks(),
                    now.calendarTicks());
            AnimalPdc.write(female.getPersistentDataContainer(), check.record().withState(pairing.female()));
            AnimalPdc.write(other.getPersistentDataContainer(), maybe.get().withState(pairing.male()));
            return;
        }
        // not paired yet: walk towards the nearest ready male is PAR-FAUNA-08's goal
    }
}
