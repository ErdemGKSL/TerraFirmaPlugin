/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.hud;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.climate.OverworldClimate;
import dev.tfp.core.climate.Solar;
import dev.tfp.paper.integration.HudSnapshot;
import org.junit.jupiter.api.Test;

/**
 * PAR-HUD-16: the HUD's hemisphere is TFC's {@code SolarCalculator.getInNorthernHemisphere(z, hemisphereScale)} with the
 * world's climate scale (20 000 blocks): bands of 40 000 blocks, north for {@code (z - 10 000) mod 80 000 > 40 000}.
 */
class HudHemisphereTest {
    private static final long SEED = 12345L;

    @Test
    void worldScaleIsTheClimatesHemisphereScale() {
        assertThat(OverworldClimate.forWorldSeed(SEED, OverworldClimate.DEFAULT_TEMPERATURE_SCALE).hemisphereScale())
                .isEqualTo(20_000f);
    }

    @Test
    void flagFollowsSolarAtTheBandEdges() {
        // Spawn (z = 0) is northern, TFC's default.
        assertThat(HudHemisphere.north(true, SEED, 0)).isTrue();
        // The southern band starts at z = 10 000 ((z - 10 000) mod 80 000 = 0, not > 40 000).
        assertThat(HudHemisphere.north(true, SEED, 9_999)).isTrue();
        assertThat(HudHemisphere.north(true, SEED, 10_000)).isFalse();
        // ...and ends at z = 50 000 inclusive (exactly 40 000 is not > 40 000).
        assertThat(HudHemisphere.north(true, SEED, 50_000)).isFalse();
        assertThat(HudHemisphere.north(true, SEED, 50_001)).isTrue();
        // Negative z: the band below spawn is southern from -30 000 down to -70 000.
        assertThat(HudHemisphere.north(true, SEED, -29_999)).isTrue();
        assertThat(HudHemisphere.north(true, SEED, -30_000)).isFalse();
        assertThat(HudHemisphere.north(true, SEED, -70_000)).isFalse();
        assertThat(HudHemisphere.north(true, SEED, -70_001)).isTrue();
        // The pattern repeats every 80 000 blocks.
        assertThat(HudHemisphere.north(true, SEED, 90_000)).isFalse();
        for (int z = -100_000; z <= 100_000; z += 997) {
            assertThat(HudHemisphere.north(true, SEED, z)).as("z=%d", z).isEqualTo(Solar.inNorthernHemisphere(z, 20_000f));
        }
    }

    @Test
    void outsideTheOverworldThePlayerIsNorthern() {
        assertThat(HudHemisphere.north(false, SEED, 20_000)).isTrue();
        assertThat(HudHemisphere.north(false, SEED, 0)).isTrue();
    }

    @Test
    void snapshotDefaultsToNorthAndKeepsTheFlagThroughUpdates() {
        assertThat(HudSnapshot.DEFAULT.northernHemisphere()).isTrue();
        HudSnapshot south = HudSnapshot.DEFAULT.withNorthernHemisphere(false);
        assertThat(south.northernHemisphere()).isFalse();
        assertThat(south.withThirst(10).withHealth(20, 50).withHunger(5).withHeldTemperature(100).withAmbientTemperature(3)
                .withAir(10).withArmor(4).withNeedsActive(false).northernHemisphere()).isFalse();
        assertThat(HudSnapshot.DEFAULT.withNorthernHemisphere(true)).isSameAs(HudSnapshot.DEFAULT);
    }
}
