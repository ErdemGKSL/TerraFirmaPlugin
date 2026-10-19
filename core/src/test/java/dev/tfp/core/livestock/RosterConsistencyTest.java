/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.livestock;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import dev.tfp.core.livestock.products.ProductKind;
import dev.tfp.core.livestock.products.ProductSpec;

/**
 * M5-LIV-03 roster consistency. The domestic roster is the set of {@link LivestockSpecies} values, which must be the 18
 * animals with a {@code ServerConfig} animal row at c3b4d9536 (lines 718-769). Each rule that needs a species is checked
 * against the roster, so a species added to one table and missing from another fails here.
 *
 * <p>Known, intended exceptions (TFC behaviour, not gaps):
 * <ul>
 *   <li>{@code MULE} is not breedable: {@code TFCMule.canMate} returns false. It has a gestation row in the config only.</li>
 *   <li>{@code MULE}, {@code CAT} and {@code DOG} have no biome spawner entry in the TFC spawn export. Mules are bred, and
 *       cats and dogs are pets. TFC spawns none of them from a biome list.</li>
 * </ul>
 * Turkey and peafowl are TFC wild winged prey (no {@code ServerConfig} animal row), so they are outside the roster.
 */
class RosterConsistencyTest {
    static final String SPAWN_RESOURCE = "/dev/tfp/core/spawn/spawn_rules.tsv";

    /** TFC {@code ServerConfig} animal rows: the 18 domestic species, in the order of lines 718-769. */
    static final Set<LivestockSpecies> ROSTER = EnumSet.of(LivestockSpecies.PIG, LivestockSpecies.DONKEY,
            LivestockSpecies.MULE, LivestockSpecies.HORSE, LivestockSpecies.DROMEDARY_CAMEL, LivestockSpecies.CAT,
            LivestockSpecies.DOG, LivestockSpecies.RABBIT, LivestockSpecies.COW, LivestockSpecies.GOAT,
            LivestockSpecies.YAK, LivestockSpecies.ALPACA, LivestockSpecies.SHEEP, LivestockSpecies.MUSK_OX,
            LivestockSpecies.BACTRIAN_CAMEL, LivestockSpecies.CHICKEN, LivestockSpecies.DUCK, LivestockSpecies.QUAIL);

    /** Species TFC does not breed: {@code TFCMule.canMate} returns false. */
    static final Set<LivestockSpecies> NOT_BREEDABLE = EnumSet.of(LivestockSpecies.MULE);

    /** Species with no biome spawner entry in TFC (see the class comment). */
    static final Set<LivestockSpecies> NOT_SPAWNED = EnumSet.of(LivestockSpecies.MULE, LivestockSpecies.CAT, LivestockSpecies.DOG);

    /** {@code ServerConfig} produce hours for each producing species (ProducingMammalConfig and OviparousAnimalConfig). */
    static final Map<LivestockSpecies, Integer> PRODUCE_HOURS = new EnumMap<>(LivestockSpecies.class);

    static {
        PRODUCE_HOURS.put(LivestockSpecies.COW, 24);
        PRODUCE_HOURS.put(LivestockSpecies.GOAT, 72);
        PRODUCE_HOURS.put(LivestockSpecies.YAK, 24);
        PRODUCE_HOURS.put(LivestockSpecies.ALPACA, 120);
        PRODUCE_HOURS.put(LivestockSpecies.SHEEP, 96);
        PRODUCE_HOURS.put(LivestockSpecies.MUSK_OX, 96);
        PRODUCE_HOURS.put(LivestockSpecies.BACTRIAN_CAMEL, 168);
        PRODUCE_HOURS.put(LivestockSpecies.CHICKEN, 30);
        PRODUCE_HOURS.put(LivestockSpecies.DUCK, 32);
        PRODUCE_HOURS.put(LivestockSpecies.QUAIL, 28);
    }

    /** TFC {@code ICalendar.PLAYER_TICKS_IN_DEFAULT_HOUR}, 60 * 20. */
    static final int PLAYER_TICKS_PER_HOUR = 60 * 20;

    @Test
    void rosterIsTheEighteenServerConfigAnimals() {
        assertThat(LivestockSpecies.values()).hasSize(18);
        assertThat(ROSTER).containsExactlyInAnyOrder(LivestockSpecies.values());
    }

    @Test
    void everyRosterSpeciesHasAnAgeRule() {
        for (LivestockSpecies s : ROSTER) {
            assertThat(s.adulthoodDays()).as("adulthoodDays of %s", s).isPositive();
            assertThat(s.usesToElderly()).as("usesToElderly of %s", s).isPositive();
        }
    }

    @Test
    void everyBreedableSpeciesHasAGestationOrEggRule() {
        for (LivestockSpecies s : EnumSet.complementOf(EnumSet.copyOf(NOT_BREEDABLE))) {
            if (s.kind() == LivestockSpecies.Kind.MAMMAL) {
                assertThat(s.gestationDays()).as("gestationDays of %s", s).isPositive();
                assertThat(s.childCount()).as("childCount of %s", s).isPositive();
            } else {
                assertThat(s.hatchDays()).as("hatchDays of %s", s).isPositive();
            }
        }
        assertThat(NOT_BREEDABLE).allSatisfy(s -> assertThat(ROSTER).contains(s));
    }

    @Test
    void producingSpeciesMatchTheServerConfigCooldown() {
        assertThat(PRODUCE_HOURS.keySet()).hasSize(10);
        for (LivestockSpecies s : ROSTER) {
            var spec = ProductSpec.defaults(s);
            if (!PRODUCE_HOURS.containsKey(s)) {
                assertThat(spec).as("%s has no product in TFC", s).isEmpty();
                continue;
            }
            assertThat(spec).as("%s produces", s).isPresent();
            assertThat(spec.get().produceTicks()).as("produceTicks of %s", s)
                    .isEqualTo(PRODUCE_HOURS.get(s) * PLAYER_TICKS_PER_HOUR);
            assertThat(spec.get().produceFamiliarity()).as("produceFamiliarity of %s", s).isEqualTo(0.15);
            assertThat(spec.get().kind()).as("product kind of %s", s).isEqualTo(expectedKind(s));
        }
    }

    @Test
    void everySpawnableSpeciesHasASpawnEntry() {
        Set<String> spawnTypes = spawnTypes();
        assertThat(spawnTypes).hasSize(76);
        for (LivestockSpecies s : ROSTER) {
            String type = "tfc:" + s.name().toLowerCase(Locale.ROOT);
            if (NOT_SPAWNED.contains(s)) {
                assertThat(spawnTypes).as("%s has no biome spawn in TFC", type).doesNotContain(type);
            } else {
                assertThat(spawnTypes).as("spawn entry for %s", type).contains(type);
            }
        }
    }

    private static ProductKind expectedKind(LivestockSpecies s) {
        return switch (s) {
            case COW, GOAT, YAK -> ProductKind.MILK;
            case ALPACA, SHEEP, MUSK_OX, BACTRIAN_CAMEL -> ProductKind.SHEAR;
            case CHICKEN, DUCK, QUAIL -> ProductKind.EGG;
            default -> throw new AssertionError("no product expected for " + s);
        };
    }

    /** The {@code type} column of every biome spawner row in the TFC golden, by namespaced id. */
    static Set<String> spawnTypes() {
        Set<String> types = new HashSet<>();
        try (InputStream in = RosterConsistencyTest.class.getResourceAsStream(SPAWN_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("missing " + SPAWN_RESOURCE);
            }
            for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
                if (line.isBlank() || line.startsWith("#") || line.startsWith("biome\t")) {
                    continue;
                }
                String[] cells = line.split("\t", -1);
                types.add(cells[3]);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        types.removeIf(t -> !t.startsWith("tfc:"));
        return types;
    }
}
