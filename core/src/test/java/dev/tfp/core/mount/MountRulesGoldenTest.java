/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.mount;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.tfp.core.livestock.LivestockAge;
import dev.tfp.core.livestock.LivestockSpecies;
import org.junit.jupiter.api.Test;

/**
 * Golden values for the mount gates and camel constants at c3b4d9536.
 *
 * <p>Sources: {@code HorseProperties.TAMED_FAMILIARITY = 0.15f} and {@code TFCChestedHorse.isTamed} (familiarity strictly
 * greater), {@code HorseProperties.OLD_AGE_MODIFIER} (-0.5, {@code ADD_MULTIPLIED_TOTAL}, so an old mount's speed is halved),
 * {@code TFCHorse.mobInteract} (a baby is passed over, so not mounted), {@code DromedaryCamel.createAttributes}
 * (max health 32, movement 0.1F, jump 0.42F, step height 1.5), and {@code ServerConfig} lines 718 to 730, the mount rows of
 * {@code MammalConfig.build(...)}: horse, donkey and mule (adulthood 80, uses 60, gestation 19, child count 1), dromedary
 * camel (the same), and bactrian camel (adulthood 80, uses 80, gestation 24, child count 1).
 */
class MountRulesGoldenTest {

    @Test
    void tamedIsStrictlyAboveFifteenPercent() {
        assertThat(MountRules.TAMED_FAMILIARITY).isEqualTo(0.15f);
        assertThat(MountRules.isTamed(0.15f)).isFalse();
        assertThat(MountRules.isTamed(0.1500001f)).isTrue();
        assertThat(MountRules.isTamed(0.0f)).isFalse();
    }

    @Test
    void babiesAreNotMountedAndAdultsAndOldAreMounted() {
        assertThat(MountRules.canMount(LivestockAge.CHILD)).isFalse();
        assertThat(MountRules.canMount(LivestockAge.ADULT)).isTrue();
        assertThat(MountRules.canMount(LivestockAge.OLD)).isTrue();
    }

    @Test
    void oldMountsMoveAtHalfSpeed() {
        // TFC: speed * (1 + -0.5) = half. The horse speed range top, 0.3375, gives 0.16875 when old.
        assertThat(MountRules.movementSpeedAt(0.3375, LivestockAge.OLD)).isCloseTo(0.16875, within(1e-12));
        assertThat(MountRules.movementSpeedAt(0.3375, LivestockAge.ADULT)).isEqualTo(0.3375);
        assertThat(MountRules.movementSpeedAt(0.3375, LivestockAge.CHILD)).isEqualTo(0.3375);
    }

    @Test
    void dromedaryAttributesAreTheCreateAttributesValues() {
        assertThat(MountRules.DROMEDARY_MAX_HEALTH).isEqualTo(32.0);
        assertThat(MountRules.DROMEDARY_MOVEMENT_SPEED).isCloseTo(0.1, within(1e-8));
        assertThat(MountRules.DROMEDARY_JUMP_STRENGTH).isCloseTo(0.42, within(1e-7));
        assertThat(MountRules.DROMEDARY_STEP_HEIGHT).isEqualTo(1.5);
    }

    @Test
    void mountSpeciesAgeAndBreedingConfigMatchServerConfig() {
        // ServerConfig: horse, donkey and mule share (80, 60, false, 19, 1). Dromedary the same. Bactrian (80, 80, false, 24, 1).
        for (LivestockSpecies s : new LivestockSpecies[] {LivestockSpecies.HORSE, LivestockSpecies.DONKEY, LivestockSpecies.MULE, LivestockSpecies.DROMEDARY_CAMEL}) {
            assertThat(s.adulthoodDays()).as(s.name()).isEqualTo(80);
            assertThat(s.usesToElderly()).as(s.name()).isEqualTo(60);
            assertThat(s.gestationDays()).as(s.name()).isEqualTo(19);
            assertThat(s.childCount()).as(s.name()).isEqualTo(1);
        }
        assertThat(LivestockSpecies.BACTRIAN_CAMEL.adulthoodDays()).isEqualTo(80);
        assertThat(LivestockSpecies.BACTRIAN_CAMEL.usesToElderly()).isEqualTo(80);
        assertThat(LivestockSpecies.BACTRIAN_CAMEL.gestationDays()).isEqualTo(24);
        assertThat(LivestockSpecies.BACTRIAN_CAMEL.childCount()).isEqualTo(1);
    }

    @Test
    void horseColourRangesAreTheVanillaEnumSizes() {
        // Vanilla Horse.Variant has 7 values and Horse.Markings has 5 (not in the TFC tree; from Minecraft 1.21.1).
        assertThat(HorseColour.VARIANT_COUNT).isEqualTo(7);
        assertThat(HorseColour.MARKINGS_COUNT).isEqualTo(5);
    }
}
