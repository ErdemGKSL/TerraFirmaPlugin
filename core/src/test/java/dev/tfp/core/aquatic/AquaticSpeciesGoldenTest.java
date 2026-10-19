/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/entities/Faunas.java, src/main/java/net/dries007/tfc/common/entities/aquatic/*.java,
 * src/main/java/net/dries007/tfc/common/items/TFCItems.java (golden values only) @ c3b4d9536
 */
package dev.tfp.core.aquatic;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Golden tests for the aquatic species table. {@code aquatic_species.tsv} in this package's test resources is exported from the
 * fauna JSON and entity_type tags of the 32 aquatic species at c3b4d9536 by a scratch script kept outside the repository. The
 * habitat, placement and bucket columns are Java-side facts read from the TFC source, and each test names the TFC line it checks.
 */
class AquaticSpeciesGoldenTest {
    /** The aquatic species count, one fauna JSON file each. */
    private static final int SPECIES = 32;
    private static final String RESOURCE = "/dev/tfp/core/aquatic/aquatic_species.tsv";

    private static List<String[]> tsvRows() throws IOException {
        List<String[]> rows = new ArrayList<>();
        try (InputStream in = AquaticSpeciesGoldenTest.class.getResourceAsStream(RESOURCE)) {
            assertThat(in).as(RESOURCE).isNotNull();
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            boolean header = true;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("#") || line.isBlank()) {
                    continue;
                }
                if (header) {
                    assertThat(line).startsWith("species\tentity\tfauna_file");
                    header = false;
                    continue;
                }
                rows.add(line.split("\t", -1));
            }
        }
        return rows;
    }

    @Test
    void rowAndFileCountsMatchTfc() throws IOException {
        // 32 fauna JSON files under tfc/fauna for the aquatic species; the TSV has one row for each
        List<String[]> rows = tsvRows();
        assertThat(rows).hasSize(SPECIES);
        assertThat(rows.stream().map(r -> r[2]).collect(Collectors.toSet())).containsExactly("true");
        assertThat(Arrays.stream(AquaticSpecies.values()).map(AquaticSpecies::entityType).collect(Collectors.toSet()))
                .hasSize(SPECIES)
                .containsExactlyInAnyOrderElementsOf(rows.stream().map(r -> r[1]).collect(Collectors.toSet()));
    }

    @Test
    void everyRowIsInAWaterTagOfTfc() throws IOException {
        // data/tfc/tags/entity_type: small_fish, water_ambient or water_creatures lists each species
        for (String[] row : tsvRows()) {
            String tags = row[row.length - 1];
            assertThat(tags).as(row[0]).containsAnyOf("small_fish", "water_ambient", "water_creatures");
        }
    }

    @Test
    void habitatCountsMatchTfcClasses() {
        // freshwater: 16 Fish + manatee + crayfish; salt: cod, tropical, pufferfish, jellyfish, isopod, lobster, horseshoe crab,
        // dolphin, orca; infinite: squid, octopoteuthis; no gate: turtle, penguin, leopard seal
        assertThat(species().filter(s -> s.habitat().equals(Optional.of(Habitat.FRESH_WATER_ONLY))).count()).isEqualTo(18);
        assertThat(species().filter(s -> s.habitat().equals(Optional.of(Habitat.SALT_WATER_ONLY))).count()).isEqualTo(9);
        assertThat(species().filter(s -> s.habitat().equals(Optional.of(Habitat.ANY_INFINITE_WATER))).count()).isEqualTo(2);
        assertThat(species().filter(s -> s.habitat().isEmpty()).count()).isEqualTo(3);
        assertThat(species().filter(AquaticSpecies::inWaterPlacement).count()).isEqualTo(29);
    }

    private static Stream<AquaticSpecies> species() {
        return Arrays.stream(AquaticSpecies.values());
    }

    @Test
    void freshwaterFishSpawnOnlyInVanillaWater() {
        // FreshwaterFish.canSpawnIn (line 88-90): fluid.isSame(Fluids.WATER)
        for (AquaticSpecies fish : Arrays.stream(AquaticSpecies.values()).filter(s -> s.habitat().equals(Optional.of(Habitat.FRESH_WATER_ONLY)))
                .filter(s -> s.bucketFluid().isPresent()).toList()) {
            assertThat(fish.passesFluidGate(AquaticFluid.VANILLA_WATER)).as(fish.name()).isTrue();
            assertThat(fish.passesFluidGate(AquaticFluid.RIVER_WATER)).as(fish.name()).isFalse();
            assertThat(fish.passesFluidGate(AquaticFluid.SPRING_WATER)).as(fish.name()).isFalse();
            assertThat(fish.passesFluidGate(AquaticFluid.SALT_WATER)).as(fish.name()).isFalse();
        }
        assertThat(species().filter(s -> s.bucketFluid().isPresent()).count()).isEqualTo(20);
    }

    @Test
    void saltSpeciesRefuseFreshWater() {
        // TFCCod.canSpawnIn (line 58-60), TFCTropicalFish (58-60), TFCPufferfish (58-60), Jellyfish (156-158),
        // TFCDolphin (31-33): fluid.isSame(TFCFluids.SALT_WATER.getSource())
        for (AquaticSpecies salt : List.of(AquaticSpecies.COD, AquaticSpecies.TROPICAL_FISH, AquaticSpecies.PUFFERFISH,
                AquaticSpecies.JELLYFISH, AquaticSpecies.DOLPHIN, AquaticSpecies.ORCA)) {
            assertThat(salt.passesFluidGate(AquaticFluid.SALT_WATER)).as(salt.name()).isTrue();
            assertThat(salt.passesFluidGate(AquaticFluid.VANILLA_WATER)).as(salt.name()).isFalse();
            assertThat(salt.passesFluidGate(AquaticFluid.SPRING_WATER)).as(salt.name()).isFalse();
        }
    }

    @Test
    void critterFlagPicksSaltOrFresh() {
        // AquaticCritter.java line 51-54: salty ? SALT_WATER.getSource() : Fluids.WATER; canSpawnIn line 81-83
        assertThat(AquaticSpecies.CRAYFISH.habitat()).contains(Habitat.FRESH_WATER_ONLY);
        assertThat(AquaticSpecies.LOBSTER.habitat()).contains(Habitat.SALT_WATER_ONLY);
        assertThat(AquaticSpecies.ISOPOD.habitat()).contains(Habitat.SALT_WATER_ONLY);
        assertThat(AquaticSpecies.HORSESHOE_CRAB.habitat()).contains(Habitat.SALT_WATER_ONLY);
    }

    @Test
    void manateeUsesVanillaWaterOnly() {
        // Manatee.canSpawnIn (line 62-64): fluid.isSame(Fluids.WATER)
        assertThat(AquaticSpecies.MANATEE.passesFluidGate(AquaticFluid.VANILLA_WATER)).isTrue();
        assertThat(AquaticSpecies.MANATEE.passesFluidGate(AquaticFluid.SALT_WATER)).isFalse();
    }

    @Test
    void squidAndOctopoteuthisAcceptAllWaterButNotAir() {
        // TFCSquid.canSpawnIn (line 52-54): Helpers.isFluid(fluid, ANY_INFINITE_WATER); the tag holds any fresh water, salt and spring
        for (AquaticSpecies squid : List.of(AquaticSpecies.SQUID, AquaticSpecies.OCTOPOTEUTHIS)) {
            assertThat(squid.passesFluidGate(AquaticFluid.VANILLA_WATER)).isTrue();
            assertThat(squid.passesFluidGate(AquaticFluid.RIVER_WATER)).isTrue();
            assertThat(squid.passesFluidGate(AquaticFluid.SALT_WATER)).isTrue();
            assertThat(squid.passesFluidGate(AquaticFluid.SPRING_WATER)).isTrue();
            assertThat(squid.passesFluidGate(AquaticFluid.NOT_WATER)).isFalse();
        }
    }

    @Test
    void amphibiousMobsSkipTheFluidGate() {
        // Faunas.java line 227: `mob instanceof AquaticMob && !canSpawnIn(...)`; turtle, penguin and leopard seal are not
        // AquaticMob and are registered with registerAnimal (ON_GROUND) at lines 80-82
        for (AquaticSpecies amphibious : List.of(AquaticSpecies.TURTLE, AquaticSpecies.PENGUIN, AquaticSpecies.LEOPARD_SEAL)) {
            assertThat(amphibious.habitat()).isEmpty();
            assertThat(amphibious.inWaterPlacement()).as(amphibious.name()).isFalse();
            assertThat(amphibious.passesFluidGate(AquaticFluid.NOT_WATER)).isTrue();
        }
    }

    @Test
    void mobBucketsHoldTheirFluid() {
        // TFCItems.java lines 422-425: COD, PUFFERFISH, JELLYFISH, TROPICAL_FISH buckets hold SALT_WATER.getSource();
        // lines 427-428: each FRESHWATER_FISH bucket holds Fluids.WATER. No other mob has a bucket.
        Set<AquaticSpecies> salt = Set.of(AquaticSpecies.COD, AquaticSpecies.PUFFERFISH, AquaticSpecies.JELLYFISH, AquaticSpecies.TROPICAL_FISH);
        for (AquaticSpecies species : AquaticSpecies.values()) {
            if (salt.contains(species)) {
                assertThat(species.bucketFluid()).contains(AquaticFluid.SALT_WATER);
            } else if (species.habitat().equals(Optional.of(Habitat.FRESH_WATER_ONLY)) && species.inWaterPlacement()
                    && species != AquaticSpecies.CRAYFISH && species != AquaticSpecies.MANATEE) {
                assertThat(species.bucketFluid()).as(species.name()).contains(AquaticFluid.VANILLA_WATER);
            } else {
                assertThat(species.bucketFluid()).as(species.name()).isEmpty();
            }
        }
    }

    @Test
    void bucketPickupNeedsTheSameFluidAndALiveMob() {
        // EntityHelpers.bucketMobPickup (lines 64-89): same fluid as the mob's bucket, and entity.isAlive()
        assertThat(AquaticBuckets.canPickUp(AquaticSpecies.LARGEMOUTH_BASS, AquaticFluid.VANILLA_WATER, true)).isTrue();
        assertThat(AquaticBuckets.canPickUp(AquaticSpecies.LARGEMOUTH_BASS, AquaticFluid.SALT_WATER, true)).isFalse();
        assertThat(AquaticBuckets.canPickUp(AquaticSpecies.LARGEMOUTH_BASS, AquaticFluid.VANILLA_WATER, false)).isFalse();
        assertThat(AquaticBuckets.canPickUp(AquaticSpecies.COD, AquaticFluid.SALT_WATER, true)).isTrue();
        assertThat(AquaticBuckets.canPickUp(AquaticSpecies.COD, AquaticFluid.VANILLA_WATER, true)).isFalse();
        assertThat(AquaticBuckets.canPickUp(AquaticSpecies.COD, AquaticFluid.NOT_WATER, true)).isFalse();
        // lobster has no mob bucket (TFCItems has none for it), so no held bucket picks it up
        assertThat(AquaticBuckets.canPickUp(AquaticSpecies.LOBSTER, AquaticFluid.SALT_WATER, true)).isFalse();
    }

    @Test
    void entityTypeLookupFindsEverySpeciesAndRejectsOthers() {
        for (AquaticSpecies species : AquaticSpecies.values()) {
            assertThat(AquaticSpecies.ofEntityType(species.entityType())).contains(species);
        }
        assertThat(AquaticSpecies.ofEntityType("tfc:frog")).isEmpty();
        assertThat(AquaticSpecies.ofEntityType("minecraft:cod")).isEmpty();
        assertThat(AquaticSpecies.ofEntityType("tfc:octopoteuthis")).contains(AquaticSpecies.OCTOPOTEUTHIS);
    }

    @Test
    void tsvDepthColumnsAgreeWithTheFaunaJsonKnownValues() throws IOException {
        // fauna JSON: squid distance_below_sea_level 15, octopoteuthis 33 (max_brightness 0), lobster 1, horseshoe crab 1, dolphin chance 10
        Map<String, String[]> byName = new TreeMap<>();
        for (String[] row : tsvRows()) {
            byName.put(row[0], row);
        }
        assertThat(byName.get("squid")[4]).isEqualTo("15");
        assertThat(byName.get("octopoteuthis")[4]).isEqualTo("33");
        assertThat(byName.get("octopoteuthis")[9]).isEqualTo("0");
        assertThat(byName.get("lobster")[4]).isEqualTo("1");
        assertThat(byName.get("horseshoe_crab")[4]).isEqualTo("1");
        assertThat(byName.get("dolphin")[3]).isEqualTo("10");
    }
}
