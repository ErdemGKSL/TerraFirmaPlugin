/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.livestock;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * M5-LIV-01 golden constants. Every value is copied from TFC at c3b4d9536:
 * {@code ServerConfig.java} lines 718-769 (the {@code *Config = ...Config.build(...)} calls, argument order
 * name, familiarityCap, adulthoodDays, uses, eatsRottenFood, then gestationDays and childCount for mammals or
 * produceTicks, produceFamiliarity and hatchDays for oviparous; produce fields are not ported here),
 * {@code ServerConfig.java} line 772 (familiarityDecayLimit 0.3), {@code ICalendar.java} lines 36-51
 * (TICKS_IN_DAY, CALENDAR_TICKS_IN_DAY, PLAYER_TICKS_IN_DEFAULT_DAY), and {@code TFCAnimalProperties.java}
 * (READY_TO_MATE_FAMILIARITY, MATING_COOLDOWN_DEFAULT_TICKS).
 */
class SpeciesGoldenTest {

    @Test
    void speciesTableMatchesServerConfig() {
        // ServerConfig 718: pig 0.35, 80 adult days, 60 uses, rotten yes, gestation 19, litter up to 10
        assertThat(LivestockSpecies.PIG.adulthoodDays()).isEqualTo(80);
        assertThat(LivestockSpecies.PIG.usesToElderly()).isEqualTo(60);
        assertThat(LivestockSpecies.PIG.eatsRottenFood()).isTrue();
        assertThat(LivestockSpecies.PIG.gestationDays()).isEqualTo(19);
        assertThat(LivestockSpecies.PIG.childCount()).isEqualTo(10);
        // ServerConfig 742: cow 192 adult days, 128 uses, rotten yes, gestation 58, litter up to 2
        assertThat(LivestockSpecies.COW.adulthoodDays()).isEqualTo(192);
        assertThat(LivestockSpecies.COW.usesToElderly()).isEqualTo(128);
        assertThat(LivestockSpecies.COW.eatsRottenFood()).isTrue();
        assertThat(LivestockSpecies.COW.gestationDays()).isEqualTo(58);
        assertThat(LivestockSpecies.COW.childCount()).isEqualTo(2);
        // ServerConfig 754: sheep 56 adult days, 60 uses, rotten no, gestation 32, litter up to 2
        assertThat(LivestockSpecies.SHEEP.adulthoodDays()).isEqualTo(56);
        assertThat(LivestockSpecies.SHEEP.usesToElderly()).isEqualTo(60);
        assertThat(LivestockSpecies.SHEEP.eatsRottenFood()).isFalse();
        assertThat(LivestockSpecies.SHEEP.gestationDays()).isEqualTo(32);
        assertThat(LivestockSpecies.SHEEP.childCount()).isEqualTo(2);
        // ServerConfig 757: muskOx gestation 64, litter 1
        assertThat(LivestockSpecies.MUSK_OX.gestationDays()).isEqualTo(64);
        assertThat(LivestockSpecies.MUSK_OX.childCount()).isEqualTo(1);
        // ServerConfig 763: chicken 24 adult days, 100 uses, rotten yes, hatch 8 days
        assertThat(LivestockSpecies.CHICKEN.kind()).isEqualTo(LivestockSpecies.Kind.OVIPAROUS);
        assertThat(LivestockSpecies.CHICKEN.adulthoodDays()).isEqualTo(24);
        assertThat(LivestockSpecies.CHICKEN.usesToElderly()).isEqualTo(100);
        assertThat(LivestockSpecies.CHICKEN.eatsRottenFood()).isTrue();
        assertThat(LivestockSpecies.CHICKEN.hatchDays()).isEqualTo(8);
        // ServerConfig 769: quail 22 adult days, 48 uses, rotten yes, hatch 8 days
        assertThat(LivestockSpecies.QUAIL.adulthoodDays()).isEqualTo(22);
        assertThat(LivestockSpecies.QUAIL.usesToElderly()).isEqualTo(48);
        assertThat(LivestockSpecies.QUAIL.hatchDays()).isEqualTo(8);
    }

    @Test
    void everySpeciesHasTheTfcFamiliarityCapAndTheRightKind() {
        // ServerConfig 718-769: every species passes familiarityCap 0.35
        assertThat(LivestockSpecies.values()).hasSize(18);
        for (LivestockSpecies species : LivestockSpecies.values()) {
            assertThat(species.familiarityCap()).isEqualTo(0.35f);
            if (species.kind() == LivestockSpecies.Kind.OVIPAROUS) {
                assertThat(species.gestationDays()).isZero();
                assertThat(species.hatchDays()).isEqualTo(8);
            } else {
                assertThat(species.hatchDays()).isZero();
                assertThat(species.gestationDays()).isPositive();
            }
        }
    }

    @Test
    void ruleConstantsMatchTfc() {
        // ICalendar: TICKS_IN_DAY = 1000 * 24, PLAYER_TICKS_IN_DEFAULT_DAY = 60 * 20 * 24
        assertThat(LivestockRules.TICKS_IN_DAY).isEqualTo(24_000L);
        assertThat(LivestockRules.PLAYER_TICKS_IN_DEFAULT_DAY).isEqualTo(28_800L);
        // tickAnimalData runs on gameTime % 20 == 0
        assertThat(LivestockRules.CHECK_INTERVAL_TICKS).isEqualTo(20L);
        // ServerConfig.familiarityDecayLimit default 0.3; literal 0.02f decay; literal 0.06f feed gain
        assertThat(LivestockRules.FAMILIARITY_DECAY_LIMIT).isEqualTo(0.3f);
        assertThat(LivestockRules.FAMILIARITY_DECAY_RATE).isEqualTo(0.02);
        assertThat(LivestockRules.FEED_FAMILIARITY_GAIN).isEqualTo(0.06f);
        // TFCAnimalProperties: READY_TO_MATE_FAMILIARITY = 0.3f, MATING_COOLDOWN_DEFAULT_TICKS = TICKS_IN_DAY
        assertThat(LivestockRules.READY_TO_MATE_FAMILIARITY).isEqualTo(0.3f);
        assertThat(LivestockRules.MATING_COOLDOWN_TICKS).isEqualTo(24_000L);
        // MammalProperties / TFCAnimalProperties.onFertilized: male addUses(5); birth addUses(10); old 1 + nextInt(5) days
        assertThat(LivestockRules.MALE_USES_PER_FERTILIZATION).isEqualTo(5);
        assertThat(LivestockRules.USES_PER_BIRTH).isEqualTo(10);
        assertThat(LivestockRules.OLD_DAY_DRAWS).isEqualTo(5);
    }
}
