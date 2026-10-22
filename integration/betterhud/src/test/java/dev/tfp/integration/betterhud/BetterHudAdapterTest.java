/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.integration.betterhud;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.clock.ClockSnapshot;
import dev.tfp.paper.integration.HudSnapshot;
import dev.tfp.paper.integration.HudSnapshotCache;
import dev.tfp.paper.integration.IntegrationProvider;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The parts of the BetterHud adapter that run without a server (no BetterHud class is loaded). */
class BetterHudAdapterTest {
    @Test
    void placeholdersReadTheLatestSnapshotOnly() {
        HudSnapshotCache cache = new HudSnapshotCache(() -> true);
        UUID player = UUID.randomUUID();
        // Before any publish: the default snapshot.
        assertThat(HudValues.NUMBERS.get("tfp_health").applyAsDouble(cache.snapshot(player))).isEqualTo(50);
        cache.put(player, new HudSnapshot(45, 50, 30, 10, 1200));
        HudSnapshot s = cache.snapshot(player);
        assertThat(HudValues.NUMBERS.keySet())
                .containsExactly("tfp_health", "tfp_max_health", "tfp_thirst", "tfp_hunger", "tfp_held_temp", "tfp_air",
                        "tfp_armor");
        assertThat(HudValues.NUMBERS.get("tfp_health").applyAsDouble(s)).isEqualTo(45);
        assertThat(HudValues.NUMBERS.get("tfp_max_health").applyAsDouble(s)).isEqualTo(50);
        assertThat(HudValues.NUMBERS.get("tfp_thirst").applyAsDouble(s)).isEqualTo(30);
        assertThat(HudValues.NUMBERS.get("tfp_hunger").applyAsDouble(s)).isEqualTo(10);
        assertThat(HudValues.NUMBERS.get("tfp_held_temp").applyAsDouble(s)).isEqualTo(1200);
        assertThat(HudValues.LISTENERS.get("tfp_health").applyAsDouble(s)).isEqualTo(0.9);
        assertThat(HudValues.LISTENERS.get("tfp_thirst").applyAsDouble(s)).isEqualTo(0.3);
        assertThat(HudValues.LISTENERS.get("tfp_hunger").applyAsDouble(s)).isEqualTo(0.5);
    }

    /** Armour: the raw attribute as the number, 0..1 on vanilla's 20-point scale as the bar (full at 20 and above). */
    @Test
    void armourReadsTheAttributeOnVanillasTwentyPointScale() {
        HudSnapshot none = HudSnapshot.DEFAULT;
        assertThat(HudValues.NUMBERS.get("tfp_armor").applyAsDouble(none)).isZero();
        assertThat(HudValues.LISTENERS.get("tfp_armor").applyAsDouble(none)).isZero();
        HudSnapshot iron = none.withArmor(15);
        assertThat(HudValues.NUMBERS.get("tfp_armor").applyAsDouble(iron)).isEqualTo(15);
        assertThat(HudValues.LISTENERS.get("tfp_armor").applyAsDouble(iron)).isEqualTo(0.75);
        assertThat(HudValues.LISTENERS.get("tfp_armor").applyAsDouble(none.withArmor(24))).isEqualTo(1);
    }

    /** The thermometer maps -20..40 °C to 0..1 and is empty without a reading. */
    @Test
    void thermometer() {
        assertThat(HudValues.thermometer(-20)).isZero();
        assertThat(HudValues.thermometer(10)).isEqualTo(0.5);
        assertThat(HudValues.thermometer(55)).isEqualTo(1);
        assertThat(HudValues.thermometer(Double.NaN)).isZero();
    }

    /** M2-CAL-02: the clock text placeholders read the published clock only, and show a dash before the first reading. */
    @Test
    void clockTextPlaceholdersFormatThePublishedClock() {
        assertThat(HudClockValues.TEXTS.keySet()).containsExactly("tfp_date", "tfp_season");
        assertThat(HudClockValues.TEXTS.get("tfp_date").apply(ClockSnapshot.UNSET)).isEqualTo("-");
        assertThat(HudClockValues.TEXTS.get("tfp_season").apply(ClockSnapshot.UNSET)).isEqualTo("-");
        ClockSnapshot june = new ClockSnapshot(1, 0, 966_000L, 8, true);
        assertThat(HudClockValues.TEXTS.get("tfp_date").apply(june)).isEqualTo("June 1, 1000");
        assertThat(HudClockValues.TEXTS.get("tfp_season").apply(june)).isEqualTo("Summer");
        ClockSnapshot december = new ClockSnapshot(2, 0, 2_112_000L, 8, false);
        assertThat(HudClockValues.TEXTS.get("tfp_date").apply(december)).isEqualTo("December 1, 1000");
        assertThat(HudClockValues.TEXTS.get("tfp_season").apply(december)).isEqualTo("Winter");
    }

    /** M3-SURV-06: thirst and hunger come from the published snapshot, scaled by the snapshot's maxima. */
    @Test
    void thirstAndHungerReadThePublishedSnapshotWithItsMaxima() {
        HudSnapshotCache cache = new HudSnapshotCache(() -> true);
        UUID player = UUID.randomUUID();
        // No snapshot yet: the defaults (full thirst, full hunger).
        assertThat(HudValues.LISTENERS.get("tfp_thirst").applyAsDouble(cache.snapshot(player))).isEqualTo(1);
        assertThat(HudValues.LISTENERS.get("tfp_hunger").applyAsDouble(cache.snapshot(player))).isEqualTo(1);
        cache.put(player, new HudSnapshot(50, 50, 25, 5, 0));
        HudSnapshot s = cache.snapshot(player);
        assertThat(HudValues.NUMBERS.get("tfp_thirst").applyAsDouble(s)).isEqualTo(25);
        assertThat(HudValues.NUMBERS.get("tfp_hunger").applyAsDouble(s)).isEqualTo(5);
        assertThat(HudValues.LISTENERS.get("tfp_thirst").applyAsDouble(s)).isEqualTo(0.25);
        assertThat(HudValues.LISTENERS.get("tfp_hunger").applyAsDouble(s)).isEqualTo(0.25);
        assertThat(HudSnapshot.MAX_THIRST).isEqualTo(100);
        assertThat(HudSnapshot.MAX_HUNGER).isEqualTo(20);
    }

    @Test
    void listenerRatiosAreClamped() {
        assertThat(HudValues.ratio(150, 100)).isEqualTo(1);
        assertThat(HudValues.ratio(-5, 100)).isZero();
        assertThat(HudValues.ratio(5, 0)).isZero();
        assertThat(HudValues.ratio(Double.NaN, 10)).isZero();
    }

    @Test
    void packTargetAndOwnerOptOut(@TempDir Path plugins) throws Exception {
        assertThat(BetterHudPackTarget.packFolder(plugins)).isEqualTo(plugins.resolve("BetterHud/packs/tfp"));
        assertThat(BetterHudPackTarget.optedOut(plugins)).isFalse();
        Files.createDirectories(plugins.resolve("BetterHud/packs/-tfp"));
        assertThat(BetterHudPackTarget.optedOut(plugins)).isTrue();
    }

    @Test
    void providerIsDiscoverableWithoutBetterHud() {
        List<IntegrationProvider> found = new ArrayList<>();
        ServiceLoader.load(IntegrationProvider.class, getClass().getClassLoader()).forEach(found::add);
        assertThat(found).extracting(IntegrationProvider::pluginName).contains(BetterHudProvider.PLUGIN);
    }
}
