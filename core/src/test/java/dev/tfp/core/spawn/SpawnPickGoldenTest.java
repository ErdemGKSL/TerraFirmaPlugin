/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.spawn;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import dev.tfp.core.calendar.Month;

/**
 * M5-ENT-03 golden picks. Each case is one spawn attempt at a fixed site with a fixed draw ({@link FixedRandom}). The expected
 * species and group size come from an independent scratch Python implementation of TFC's rule over the same exported
 * TSV (the biome's spawner list, the weighted pick, the group size, then the {@code Fauna} gate); the scratch script is not
 * in the repo. TFC's expected entry is the pick of the first draw, so a case with "none" means the gate refused it.
 */
class SpawnPickGoldenTest {
    private static final String NONE = "none";

    /** One attempt: the site and the fixed draws, with the expected species ({@code none} if refused) and group size. */
    record Case(String name, String biome, SpawnCategory category, int y, float temperature, float rain, float base,
            float rainVariance, int forest, boolean northern, int brightness, Month month, int intDraw, float floatDraw,
            String expectedType, int expectedGroup) {
    }

    private static final List<Case> CASES = List.of(
        new Case("plains_temperate", "plains", SpawnCategory.CREATURE, 60, 20.0f, 260.0f, 0.0f, 0.0f, 2, true, 15, Month.JULY, 3, 0.9f, "tfc:crocodile", 1),
        new Case("plains_temperate_b", "plains", SpawnCategory.CREATURE, 60, 20.0f, 260.0f, 0.0f, 0.0f, 2, true, 15, Month.JULY, 7, 0.9f, "tfc:crocodile", 1),
        new Case("plains_cold_wet", "plains", SpawnCategory.CREATURE, 60, -12.5f, 200.0f, 100.0f, 0.0f, 1, true, 15, Month.DECEMBER, 11, 0.3f, NONE, 0),
        new Case("plains_southern", "plains", SpawnCategory.CREATURE, 60, 18.0f, 260.0f, 0.0f, 0.5f, 0, false, 15, Month.JANUARY, 5, 0.7f, "tfc:crocodile", 1),
        new Case("plains_hot_high", "plains", SpawnCategory.CREATURE, 120, 35.0f, 65.0f, 0.0f, 0.0f, 0, true, 15, Month.JULY, 2, 0.4f, NONE, 0),
        new Case("lake_salmon_june", "lake", SpawnCategory.WATER_AMBIENT, 60, 15.0f, 300.0f, 50.0f, 0.0f, 0, true, 15, Month.JUNE, 1, 0.2f, "tfc:bluegill", 3),
        new Case("lake_salmon_december", "lake", SpawnCategory.WATER_AMBIENT, 60, 15.0f, 300.0f, 50.0f, 0.0f, 0, true, 15, Month.DECEMBER, 1, 0.2f, NONE, 0),
        new Case("deep_ocean_dolphin_deep", "deep_ocean", SpawnCategory.WATER_CREATURE, 40, 18.0f, 250.0f, 0.0f, 0.0f, 0, true, 15, Month.JULY, 10, 0.5f, "tfc:squid", 2),
        new Case("deep_ocean_dolphin_shallow", "deep_ocean", SpawnCategory.WATER_CREATURE, 60, 18.0f, 250.0f, 0.0f, 0.0f, 0, true, 15, Month.JULY, 10, 0.5f, NONE, 0),
        new Case("deep_ocean_octo_dark", "deep_ocean", SpawnCategory.UNDERGROUND_WATER_CREATURE, 20, 10.0f, 250.0f, 0.0f, 0.0f, 0, true, 0, Month.JULY, 0, 0.5f, "tfc:octopoteuthis", 1),
        new Case("deep_ocean_octo_lit", "deep_ocean", SpawnCategory.UNDERGROUND_WATER_CREATURE, 20, 10.0f, 250.0f, 0.0f, 0.0f, 0, true, 3, Month.JULY, 0, 0.5f, NONE, 0),
        new Case("plains_fuzzy_panda", "plains", SpawnCategory.CREATURE, 60, 23.0f, 250.0f, 100.0f, 0.0f, 3, true, 15, Month.JULY, 39, 0.9f, "tfc:panda", 3),
        new Case("plains_fuzzy_panda_refused", "plains", SpawnCategory.CREATURE, 60, 23.0f, 250.0f, 100.0f, 0.0f, 3, true, 15, Month.JULY, 39, 0.1f, NONE, 0),
        new Case("plains_monster_vanilla", "plains", SpawnCategory.MONSTER, 60, 20.0f, 260.0f, 0.0f, 0.0f, 2, true, 15, Month.JULY, 0, 0.5f, "minecraft:spider", 4),
        new Case("ice_sheet_penguin", "ice_sheet_oceanic", SpawnCategory.CREATURE, 70, -15.0f, 200.0f, 0.0f, 0.0f, 0, true, 15, Month.JULY, 1, 0.5f, "tfc:penguin", 3)
    );

    static List<Case> cases() {
        return CASES;
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void attemptMatchesTheTfcRule(Case c) {
        SpawnTsv tsv = SpawnTsv.load();
        Map<String, FaunaRule> rules = tsv.rules();
        List<SpawnEntry> candidates = tsv.candidates(c.biome(), c.category());
        assertThat(candidates).isNotEmpty();
        SpawnSite site = new SpawnSite(c.y(), 63, SpawnTsv.chunk(c.temperature(), c.rain(), c.rainVariance(), c.base()),
                c.forest(), c.northern(), c.brightness(), c.month());

        Optional<SpawnPick> pick = SpawnRules.attempt(candidates, rules, site, new FixedRandom(c.intDraw(), c.floatDraw()));

        if (NONE.equals(c.expectedType())) {
            assertThat(pick).as(c.name()).isEmpty();
        } else {
            assertThat(pick).as(c.name()).isPresent();
            assertThat(pick.get().entry().type()).as(c.name()).isEqualTo(c.expectedType());
            assertThat(pick.get().groupSize()).as(c.name()).isEqualTo(c.expectedGroup());
        }
    }
}
