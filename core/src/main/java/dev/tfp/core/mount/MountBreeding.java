/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/entities/livestock/horse/TFCHorse.java (canMate, checkExtraBreedConditions),
 * src/main/java/net/dries007/tfc/common/entities/livestock/horse/TFCDonkey.java (canMate, checkExtraBreedConditions),
 * src/main/java/net/dries007/tfc/common/entities/livestock/horse/TFCMule.java (canMate, isReadyToMate),
 * src/main/java/net/dries007/tfc/common/entities/livestock/horse/TFCChestedHorse.java (vanillaParentingCheck, canMate),
 * src/main/java/net/dries007/tfc/common/entities/livestock/camel/DromedaryCamel.java and BactrianCamel.java (canMate,
 * checkExtraBreedConditions) @ c3b4d9536
 */
package dev.tfp.core.mount;

import dev.tfp.core.livestock.AnimalState;
import dev.tfp.core.livestock.BreedingRules;
import dev.tfp.core.livestock.LivestockAge;
import dev.tfp.core.livestock.LivestockRules;
import dev.tfp.core.livestock.LivestockSpecies;
import java.util.Objects;

/**
 * TFC's mating eligibility for the mounts. Horses, donkeys and mules share one family, and dromedary and bactrian camels
 * each mate only with their own kind. A mule never mates. Camels do not need a tame animal, only one that is not
 * mounted. The generic gender, adulthood, familiarity, hunger and cooldown checks are {@link BreedingRules}.
 */
public final class MountBreeding {

    /**
     * One side of a mating.
     *
     * @param state   the animal's stored state, caught up or not (the rules catch it up)
     * @param species the animal's species
     * @param ridden  true when the animal is a vehicle or a passenger (TFC {@code vanillaParentingCheck})
     */
    public record Mate(AnimalState state, LivestockSpecies species, boolean ridden) {

        /** Validates the parts. */
        public Mate {
            Objects.requireNonNull(state, "state");
            Objects.requireNonNull(species, "species");
        }
    }

    private MountBreeding() {
    }

    /** True for the species that breed across: {@code HORSE} and {@code DONKEY}. A mule is not in this set. */
    public static boolean isHorseFamily(LivestockSpecies species) {
        return species == LivestockSpecies.HORSE || species == LivestockSpecies.DONKEY;
    }

    /** True for the two camels, which mate only with their own species. */
    public static boolean isCamel(LivestockSpecies species) {
        return species == LivestockSpecies.DROMEDARY_CAMEL || species == LivestockSpecies.BACTRIAN_CAMEL;
    }

    /**
     * TFC {@code isReadyToMate}: the generic rules, except that a mule is never ready (TFCMule overrides it to false).
     */
    public static boolean isReadyToMate(AnimalState state, LivestockSpecies species, long playerTicks) {
        return species != LivestockSpecies.MULE && BreedingRules.isReadyToMate(state, species, playerTicks);
    }

    /**
     * TFC {@code canMate} for the mounts. Both sides must be of opposite gender and ready to mate. Then:
     * <ul>
     *   <li>a horse-family pair (horse with horse, horse with donkey, donkey with donkey) needs both animals to be
     *       unridden, tamed (familiarity above {@link MountRules#TAMED_FAMILIARITY}) and not a child;</li>
     *   <li>a camel pair needs the same camel species and both animals unridden.</li>
     * </ul>
     * Any other pair (a mule, horse with camel) is refused.
     */
    public static boolean canMate(Mate a, Mate b, long playerTicks) {
        if (a.state().male() == b.state().male()) {
            return false;
        }
        if (!isReadyToMate(a.state(), a.species(), playerTicks) || !isReadyToMate(b.state(), b.species(), playerTicks)) {
            return false;
        }
        if (isHorseFamily(a.species()) && isHorseFamily(b.species())) {
            return horseParenting(a, playerTicks) && horseParenting(b, playerTicks);
        }
        return isCamel(a.species()) && a.species() == b.species() && !a.ridden() && !b.ridden();
    }

    private static boolean horseParenting(Mate m, long playerTicks) {
        return !m.ridden()
            && MountRules.isTamed(LivestockRules.familiarityAt(m.state(), playerTicks))
            && LivestockRules.ageAt(m.state(), m.species(), playerTicks) != LivestockAge.CHILD;
    }
}
