/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/entities/livestock/horse/HorseProperties.java (createGenes, applyGenes),
 * src/main/java/net/dries007/tfc/common/entities/livestock/horse/TFCHorse.java (createGenes isMule and markings,
 * applyGenes, getEntityTypeForBaby), src/main/java/net/dries007/tfc/common/entities/livestock/horse/TFCDonkey.java
 * (createGenes isMule, getEntityTypeForBaby) @ c3b4d9536
 */
package dev.tfp.core.mount;

import dev.tfp.core.livestock.LivestockSpecies;
import java.util.Objects;
import java.util.Optional;

/**
 * The genes a horse-family female records when she is fertilized (TFC {@code onFertilized} then {@code createGenes}), and
 * how the foal's species and stats are drawn from them at birth (TFC {@code applyGenes}).
 *
 * <p>Foal species: a horse with a donkey gives a mule, and a donkey with a horse gives a mule. Horse with horse gives a
 * horse, and donkey with donkey gives a donkey. A mule never mates, so it is never a parent here. TFC decides the
 * species from the genes, so a foal with no genes (a spawn, never fertilized) follows its mother's class instead.
 * Stats inherit as {@link MountStats#inheritAttribute} from the two parents' base values. The colour inherits only for
 * a horse foal, which needs both parents to have a colour.
 *
 * <p>TFC keeps the genes in NBT and applies them at birth. Camels have no stat genes (their TFC {@code createGenes}
 * and {@code applyGenes} only add the mammal's size and runt genes), so they are not modelled here.
 *
 * @param mother the female who carries the genes
 * @param father the male that fertilized her
 */
public record MountGenes(Parent mother, Parent father) {

    /**
     * One parent's genetic inputs: its species, its base stats and, for a horse, its colour.
     *
     * @param species the parent's species, {@code HORSE} or {@code DONKEY}
     * @param stats   the parent's attribute base values
     * @param colour  the parent's colour; required when both parents are horses, otherwise empty
     */
    public record Parent(LivestockSpecies species, MountStats stats, Optional<HorseColour> colour) {

        /** Validates the species and the stats. */
        public Parent {
            Objects.requireNonNull(species, "species");
            Objects.requireNonNull(stats, "stats");
            Objects.requireNonNull(colour, "colour");
            if (!MountBreeding.isHorseFamily(species)) {
                throw new IllegalArgumentException("not a horse-family species: " + species);
            }
        }
    }

    /**
     * A foal's drawn values.
     *
     * @param species the foal's species, {@code HORSE}, {@code DONKEY} or {@code MULE}
     * @param stats   the foal's attribute base values
     * @param colour  the foal's colour, present only for a horse foal
     */
    public record Foal(LivestockSpecies species, MountStats stats, Optional<HorseColour> colour) {
    }

    /**
     * Validates that the parents can breed and carry what the foal needs.
     *
     * @throws IllegalArgumentException if the colour a horse foal needs is missing
     */
    public MountGenes {
        Objects.requireNonNull(mother, "mother");
        Objects.requireNonNull(father, "father");
        if (foalSpecies(mother.species(), father.species()) == LivestockSpecies.HORSE
            && (mother.colour().isEmpty() || father.colour().isEmpty())) {
            throw new IllegalArgumentException("a horse foal needs both parents' colours");
        }
    }

    /**
     * The species a foal of this mother and father is (TFC {@code isMule} in {@code createGenes}, read by
     * {@code getEntityTypeForBaby}). The rule is the same whichever parent is the female.
     *
     * @throws IllegalArgumentException if either species is not {@code HORSE} or {@code DONKEY}
     */
    public static LivestockSpecies foalSpecies(LivestockSpecies mother, LivestockSpecies father) {
        if (!MountBreeding.isHorseFamily(mother) || !MountBreeding.isHorseFamily(father)) {
            throw new IllegalArgumentException("only horses and donkeys breed across species: " + mother + " and " + father);
        }
        if (mother == LivestockSpecies.HORSE) {
            return father == LivestockSpecies.DONKEY ? LivestockSpecies.MULE : LivestockSpecies.HORSE;
        }
        return father == LivestockSpecies.HORSE ? LivestockSpecies.MULE : LivestockSpecies.DONKEY;
    }

    /** The species of the foal these genes produce. */
    public LivestockSpecies foal() {
        return foalSpecies(mother.species(), father.species());
    }

    /**
     * TFC {@code applyGenes} at birth. The draws are taken in TFC's order: max health (three {@code nextDouble}, with the
     * father as the first value), then jump strength, then movement speed, then, for a horse foal, the colour.
     *
     * @return the foal's species, stats and colour
     */
    public Foal inherit(MountRandom random) {
        final MountStats m = mother.stats();
        final MountStats f = father.stats();
        final double health = MountStats.inheritAttribute(f.maxHealth(), m.maxHealth(), MountStats.MIN_HEALTH, MountStats.MAX_HEALTH, random);
        final double jump = MountStats.inheritAttribute(f.jumpStrength(), m.jumpStrength(), MountStats.MIN_JUMP_STRENGTH, MountStats.MAX_JUMP_STRENGTH, random);
        final double speed = MountStats.inheritAttribute(f.movementSpeed(), m.movementSpeed(), MountStats.MIN_MOVEMENT_SPEED, MountStats.MAX_MOVEMENT_SPEED, random);
        final LivestockSpecies foal = foal();
        final Optional<HorseColour> colour = foal == LivestockSpecies.HORSE
            ? Optional.of(HorseColour.inherit(mother.colour().orElseThrow(), father.colour().orElseThrow(), random))
            : Optional.empty();
        return new Foal(foal, new MountStats(health, jump, speed), colour);
    }
}
