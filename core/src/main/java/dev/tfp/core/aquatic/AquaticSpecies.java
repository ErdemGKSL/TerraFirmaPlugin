/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/entities/aquatic/Fish.java, AquaticCritter.java, TFCCod.java, TFCTropicalFish.java,
 * TFCPufferfish.java, Jellyfish.java, TFCDolphin.java, Manatee.java, TFCSquid.java, Octopoteuthis.java, TFCTurtle.java, Penguin.java,
 * LeopardSeal.java; src/main/java/net/dries007/tfc/common/entities/Faunas.java (registerFish, registerAnimal, the spawn gate);
 * src/main/java/net/dries007/tfc/common/items/TFCItems.java (the mob buckets) @ c3b4d9536
 */
package dev.tfp.core.aquatic;

import java.util.Locale;
import java.util.Optional;

/**
 * The 32 TFC aquatic species: 16 freshwater fish, 8 marine fish and critters, 5 large aquatic mobs (dolphin, orca, manatee, squid,
 * octopoteuthis) and 3 amphibious mobs (turtle, penguin, leopard seal). Frog is left out: TFC registers it as a land animal
 * ({@code registerAnimal}), not as an aquatic mob.
 *
 * <p>Per species, the facts are: the spawn placement (in water or on the ground), the fluid habitat of {@code canSpawnIn}
 * (absent for the three amphibious mobs, which are not {@code AquaticMob} and skip the fluid check), and the mob bucket's fluid
 * (absent for the species without a bucket). The depth, the climate and the other fauna gates are in {@code spawn}.
 *
 * <p>The spawn order is TFC's {@code Faunas} placement: the chance draw, then this fluid gate, then the depth rule, the climate,
 * solid ground and the months. The fluid gate is {@link #passesFluidGate}.
 */
public enum AquaticSpecies {
    BLUEGILL(true, Habitat.FRESH_WATER_ONLY, AquaticFluid.VANILLA_WATER),
    CRAPPIE(true, Habitat.FRESH_WATER_ONLY, AquaticFluid.VANILLA_WATER),
    LAKE_TROUT(true, Habitat.FRESH_WATER_ONLY, AquaticFluid.VANILLA_WATER),
    LARGEMOUTH_BASS(true, Habitat.FRESH_WATER_ONLY, AquaticFluid.VANILLA_WATER),
    RAINBOW_TROUT(true, Habitat.FRESH_WATER_ONLY, AquaticFluid.VANILLA_WATER),
    SALMON(true, Habitat.FRESH_WATER_ONLY, AquaticFluid.VANILLA_WATER),
    SMALLMOUTH_BASS(true, Habitat.FRESH_WATER_ONLY, AquaticFluid.VANILLA_WATER),
    NORTHERN_PIKE(true, Habitat.FRESH_WATER_ONLY, AquaticFluid.VANILLA_WATER),
    BURBOT(true, Habitat.FRESH_WATER_ONLY, AquaticFluid.VANILLA_WATER),
    ARCTIC_CHAR(true, Habitat.FRESH_WATER_ONLY, AquaticFluid.VANILLA_WATER),
    MUKSUN(true, Habitat.FRESH_WATER_ONLY, AquaticFluid.VANILLA_WATER),
    TILAPIA(true, Habitat.FRESH_WATER_ONLY, AquaticFluid.VANILLA_WATER),
    SPOTTED_GUDGEON(true, Habitat.FRESH_WATER_ONLY, AquaticFluid.VANILLA_WATER),
    PEACOCK_BASS(true, Habitat.FRESH_WATER_ONLY, AquaticFluid.VANILLA_WATER),
    PACU(true, Habitat.FRESH_WATER_ONLY, AquaticFluid.VANILLA_WATER),
    RED_PIRANHA(true, Habitat.FRESH_WATER_ONLY, AquaticFluid.VANILLA_WATER),
    COD(true, Habitat.SALT_WATER_ONLY, AquaticFluid.SALT_WATER),
    TROPICAL_FISH(true, Habitat.SALT_WATER_ONLY, AquaticFluid.SALT_WATER),
    PUFFERFISH(true, Habitat.SALT_WATER_ONLY, AquaticFluid.SALT_WATER),
    JELLYFISH(true, Habitat.SALT_WATER_ONLY, AquaticFluid.SALT_WATER),
    ISOPOD(true, Habitat.SALT_WATER_ONLY, null),
    LOBSTER(true, Habitat.SALT_WATER_ONLY, null),
    CRAYFISH(true, Habitat.FRESH_WATER_ONLY, null),
    HORSESHOE_CRAB(true, Habitat.SALT_WATER_ONLY, null),
    DOLPHIN(true, Habitat.SALT_WATER_ONLY, null),
    ORCA(true, Habitat.SALT_WATER_ONLY, null),
    MANATEE(true, Habitat.FRESH_WATER_ONLY, null),
    SQUID(true, Habitat.ANY_INFINITE_WATER, null),
    OCTOPOTEUTHIS(true, Habitat.ANY_INFINITE_WATER, null),
    TURTLE(false, null, null),
    PENGUIN(false, null, null),
    LEOPARD_SEAL(false, null, null);

    private final boolean inWaterPlacement;
    private final Habitat habitat;
    private final AquaticFluid bucketFluid;

    AquaticSpecies(boolean inWaterPlacement, Habitat habitat, AquaticFluid bucketFluid) {
        this.inWaterPlacement = inWaterPlacement;
        this.habitat = habitat;
        this.bucketFluid = bucketFluid;
    }

    /** The entity type, {@code tfc:} and the lower-case constant name, such as {@code tfc:largemouth_bass}. */
    public String entityType() {
        return "tfc:" + name().toLowerCase(Locale.ROOT);
    }

    /**
     * The species of an entity type, or empty if it is not one of the 32 species.
     *
     * @param entityType a namespaced entity type, such as {@code tfc:cod}
     * @return the species, or empty
     */
    public static Optional<AquaticSpecies> ofEntityType(String entityType) {
        for (AquaticSpecies species : values()) {
            if (species.entityType().equals(entityType)) {
                return Optional.of(species);
            }
        }
        return Optional.empty();
    }

    /**
     * Whether TFC places the species in water ({@code Faunas.registerFish}). The other three, the amphibious mobs, use the ground
     * placement ({@code registerAnimal}).
     */
    public boolean inWaterPlacement() {
        return inWaterPlacement;
    }

    /** The fluid habitat, or empty for a species that is not an {@code AquaticMob} and so has no fluid gate. */
    public Optional<Habitat> habitat() {
        return Optional.ofNullable(habitat);
    }

    /**
     * The fluid gate of the spawn placement: true if the fluid at the position is one the species accepts. A species with no
     * fluid gate always passes, as {@code Faunas} skips the check for a mob that is not an {@code AquaticMob}.
     *
     * @param fluid the fluid at the spawn position
     * @return true if the species may spawn there
     */
    public boolean passesFluidGate(AquaticFluid fluid) {
        return habitat == null || habitat.contains(fluid);
    }

    /** The fluid the species' mob bucket holds, or empty for a species with no mob bucket. */
    public Optional<AquaticFluid> bucketFluid() {
        return Optional.ofNullable(bucketFluid);
    }
}
