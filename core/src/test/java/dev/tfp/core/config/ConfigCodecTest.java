/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.config;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.compat.Version;
import dev.tfp.core.config.TfpConfig.AutoInstall;
import dev.tfp.core.config.TfpConfig.CatchUp;
import dev.tfp.core.config.TfpConfig.NetherEndBlocks;
import dev.tfp.core.config.TfpConfig.OreOverlay;
import dev.tfp.core.config.TfpConfig.Profile;
import dev.tfp.core.config.TfpConfig.Visuals;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** M1-BOOT-03: map-level round trip, defaults for missing keys, warnings for unknown/bad keys. */
class ConfigCodecTest {
    static final TfpConfig CUSTOM = new TfpConfig(
            new Visuals(Map.of("1.21", Profile.LEVEL_1, "26", Profile.LEVEL_0), 120, NetherEndBlocks.TRUE),
            new OreOverlay(32, 2000, false),
            new AutoInstall(false, true, false, true, true),
            new CatchUp(40, 8, 250, 2000));

    @Test
    void roundTripsDefaultsAndCustomValues() {
        for (TfpConfig c : List.of(TfpConfig.DEFAULTS, CUSTOM,
                new TfpConfig(new Visuals(Map.of(), 0, NetherEndBlocks.FALSE), new OreOverlay(0, 0, true),
                        AutoInstall.DEFAULTS, new CatchUp(0, 0, 1, 50_000)))) {
            ConfigCodec.Parsed p = ConfigCodec.parse(ConfigCodec.toMap(c));
            assertThat(p.warnings()).isEmpty();
            assertThat(p.defaulted()).isEmpty();
            // Families absent from the input come back with their defaults.
            Map<String, Profile> expected = new LinkedHashMap<>(Visuals.DEFAULT_PROFILES);
            expected.putAll(c.visuals().profile());
            assertThat(p.config().visuals().profile()).isEqualTo(expected);
            assertThat(p.config().visuals().solidReserveWarning()).isEqualTo(c.visuals().solidReserveWarning());
            assertThat(p.config().visuals().useNetherEndBlocks()).isEqualTo(c.visuals().useNetherEndBlocks());
            assertThat(p.config().autoInstall()).isEqualTo(c.autoInstall());
            assertThat(p.config().oreOverlay()).isEqualTo(c.oreOverlay());
            assertThat(p.config().catchUp()).isEqualTo(c.catchUp());
            // Serialising again is stable.
            assertThat(ConfigCodec.parse(ConfigCodec.toMap(p.config())).config()).isEqualTo(p.config());
        }
    }

    @Test
    void emptyOrNullTreeGivesDefaults() {
        ConfigCodec.Parsed p = ConfigCodec.parse(Map.of());
        assertThat(p.config()).isEqualTo(TfpConfig.DEFAULTS);
        assertThat(p.warnings()).isEmpty();
        assertThat(p.defaulted()).hasSize(15).contains("visuals.profile", "auto-install.restore-deleted", "catchup.sweep-period-ticks",
                "catchup.commit-budget-micros",
                "ore-overlay.cutoff-blocks", "ore-overlay.max-per-player", "ore-overlay.surface-outcrops-full-range");
        assertThat(ConfigCodec.parse(null).config()).isEqualTo(TfpConfig.DEFAULTS);
    }

    @Test
    void missingNestedKeysTakeDefaultsAndAreListed() {
        Map<String, Object> root = Map.of(
                "visuals", Map.of("solid-reserve-warning", 42),
                "auto-install", Map.of("download-missing", true));
        ConfigCodec.Parsed p = ConfigCodec.parse(root);
        assertThat(p.warnings()).isEmpty();
        assertThat(p.config().visuals().solidReserveWarning()).isEqualTo(42);
        assertThat(p.config().visuals().profile()).isEqualTo(Visuals.DEFAULT_PROFILES);
        assertThat(p.config().autoInstall().downloadMissing()).isTrue();
        assertThat(p.config().autoInstall().craftengine()).isTrue();
        assertThat(p.defaulted()).contains("visuals.profile", "visuals.use-nether-end-blocks", "auto-install.craftengine");
    }

    @Test
    void unknownKeysAreWarningsNotFailures() {
        Map<String, Object> root = Map.of(
                "visual", Map.of(),
                "visuals", Map.of("profiles", "x", "profile", Map.of("26", "level-1", "1.20", "level-0", "nether", "level-2")),
                "auto-install", Map.of("craftengine", false, "bettermodel", true));
        ConfigCodec.Parsed p = ConfigCodec.parse(root);
        assertThat(p.warnings()).anyMatch(w -> w.contains("'visual'"))
                .anyMatch(w -> w.contains("'visuals.profiles'"))
                .anyMatch(w -> w.contains("'auto-install.bettermodel'"))
                .anyMatch(w -> w.contains("visuals.profile.nether"));
        assertThat(p.config().visuals().profile()).containsEntry("26", Profile.LEVEL_1).containsEntry("1.20", Profile.LEVEL_0);
        assertThat(p.config().autoInstall().craftengine()).isFalse();
    }

    @Test
    void badValuesWarnAndFallBack() {
        Map<String, Object> root = Map.of(
                "visuals", Map.of("profile", Map.of("26", "level-9"), "solid-reserve-warning", -5,
                        "use-nether-end-blocks", "sometimes"),
                "auto-install", Map.of("craftengine", "yes please"));
        ConfigCodec.Parsed p = ConfigCodec.parse(root);
        assertThat(p.warnings()).hasSize(4);
        assertThat(p.config()).isEqualTo(TfpConfig.DEFAULTS);
        assertThat(ConfigCodec.parse(Map.of("visuals", "flat")).warnings()).singleElement().asString().contains("section");
    }

    @Test
    void netherEndAcceptsBooleansAndStrings() {
        assertThat(ConfigCodec.parse(Map.of("visuals", Map.of("use-nether-end-blocks", false))).config()
                .visuals().useNetherEndBlocks()).isEqualTo(NetherEndBlocks.FALSE);
        assertThat(ConfigCodec.parse(Map.of("visuals", Map.of("use-nether-end-blocks", "AUTO"))).config()
                .visuals().useNetherEndBlocks()).isEqualTo(NetherEndBlocks.AUTO);
    }

    @Test
    void profileForRunningVersion() {
        Visuals v = TfpConfig.DEFAULTS.visuals();
        assertThat(v.profileFor(Version.parse("1.21.11"))).isEqualTo(Profile.LEVEL_2);
        assertThat(v.profileFor(Version.parse("26.3"))).isEqualTo(Profile.LEVEL_2);
        assertThat(v.profileFor(Version.parse("27.1"))).isEqualTo(Profile.LEVEL_2);
        assertThat(Visuals.family(Version.parse("1.21.4"))).isEqualTo("1.21");
        assertThat(Visuals.family(Version.parse("26.1"))).isEqualTo("26");
        assertThat(CUSTOM.visuals().profileFor(Version.parse("26.2"))).isEqualTo(Profile.LEVEL_0);
    }

    @Test
    void defaultsAreLevel2OnBothFamiliesAndOnlyThreeProfilesExist() {
        assertThat(Profile.values()).extracting(Profile::id).containsExactly("level-0", "level-1", "level-2");
        assertThat(Visuals.DEFAULT_PROFILES).containsExactly(Map.entry("1.21", Profile.LEVEL_2), Map.entry("26", Profile.LEVEL_2));
        assertThat(TfpConfig.DEFAULTS.oreOverlay()).isEqualTo(new OreOverlay(48, 8000, true));
        assertThat(Profile.byId("level-3a")).isEmpty();
    }

    @Test
    void removedProfilesAreReadAsLevel2WithAWarningNamingFileAndKey() {
        for (String removed : List.of("level-3a", "level-3b", " LEVEL-3A ")) {
            ConfigCodec.Parsed p = ConfigCodec.parse(Map.of("visuals", Map.of("profile", Map.of("1.21", removed, "26", "level-1"))));
            assertThat(p.config().visuals().profile()).as(removed)
                    .containsEntry("1.21", Profile.LEVEL_2).containsEntry("26", Profile.LEVEL_1);
            assertThat(p.warnings()).as(removed).singleElement().asString()
                    .startsWith("plugins/TerraFirmaPlugin/config.yml visuals.profile.\"1.21\": " + removed.strip())
                    .contains("was removed: ores are drawn as overlays and grades no longer cost visual states; using level-2");
        }
        // Parsing never rewrites the owner's value: the same input warns again (every start until the owner edits it).
        Map<String, Object> tree = Map.of("visuals", Map.of("profile", Map.of("26", "level-3b")));
        assertThat(ConfigCodec.parse(tree).warnings()).hasSize(1);
        assertThat(ConfigCodec.parse(tree).warnings()).hasSize(1);
        assertThat(tree.get("visuals")).isEqualTo(Map.of("profile", Map.of("26", "level-3b")));
    }

    @Test
    void unknownProfileFallsBackToTheDefaultAndListsTheThreeLevels() {
        ConfigCodec.Parsed p = ConfigCodec.parse(Map.of("visuals", Map.of("profile", Map.of("1.21", "level-4"))));
        assertThat(p.config().visuals().profile()).containsEntry("1.21", Profile.LEVEL_2);
        assertThat(p.warnings()).singleElement().asString()
                .contains("unknown profile 'level-4'").contains("expected level-0, level-1 or level-2").contains("using level-2")
                .doesNotContain("level-3");
    }

    @Test
    void oreOverlayBadValuesWarnAndFallBack() {
        ConfigCodec.Parsed p = ConfigCodec.parse(Map.of("ore-overlay",
                Map.of("cutoff-blocks", -1, "max-per-player", "many", "surface-outcrops-full-range", "no", "radius", 3)));
        assertThat(p.config().oreOverlay()).isEqualTo(OreOverlay.DEFAULTS);
        assertThat(p.warnings()).hasSize(4).anyMatch(w -> w.contains("'ore-overlay.radius'"));
    }

    @Test
    void catchUpDefaultsAndMissingKeys() {
        assertThat(TfpConfig.DEFAULTS.catchUp()).isEqualTo(new CatchUp(600, 4, 1000, 1000));
        ConfigCodec.Parsed p = ConfigCodec.parse(Map.of("catchup", Map.of("sweep-radius-chunks", 6)));
        assertThat(p.warnings()).isEmpty();
        assertThat(p.config().catchUp()).isEqualTo(new CatchUp(600, 6, 1000, 1000));
        assertThat(p.defaulted()).contains("catchup.sweep-period-ticks", "catchup.feed-budget-micros",
                "catchup.commit-budget-micros").doesNotContain("catchup.sweep-radius-chunks");
        assertThat(ConfigCodec.toMap(TfpConfig.DEFAULTS)).containsKey("catchup")
                .extractingByKey("catchup").isEqualTo(Map.of("sweep-period-ticks", 600, "sweep-radius-chunks", 4,
                        "feed-budget-micros", 1000, "commit-budget-micros", 1000));
    }

    @Test
    void catchUpOutOfRangeValuesWarnAndFallBack() {
        ConfigCodec.Parsed p = ConfigCodec.parse(Map.of("catchup", Map.of("sweep-period-ticks", -1,
                "sweep-radius-chunks", 33, "feed-budget-micros", 0, "commit-budget-micros", 50_001, "period", 5)));
        assertThat(p.config().catchUp()).isEqualTo(CatchUp.DEFAULTS);
        assertThat(p.warnings()).hasSize(5)
                .anyMatch(w -> w.contains("catchup.sweep-period-ticks: expected a whole number >= 0, got '-1'; using 600"))
                .anyMatch(w -> w.contains("catchup.sweep-radius-chunks: expected a whole number in 0..32, got '33'; using 4"))
                .anyMatch(w -> w.contains("catchup.feed-budget-micros: expected a whole number in 1..50000"))
                .anyMatch(w -> w.contains("catchup.commit-budget-micros: expected a whole number in 1..50000"))
                .anyMatch(w -> w.contains("'catchup.period'"));
        ConfigCodec.Parsed bad = ConfigCodec.parse(Map.of("catchup", Map.of("sweep-period-ticks", "often",
                "feed-budget-micros", 2.5)));
        assertThat(bad.config().catchUp()).isEqualTo(CatchUp.DEFAULTS);
        assertThat(bad.warnings()).hasSize(2);
        assertThat(ConfigCodec.parse(Map.of("catchup", 3)).warnings()).singleElement().asString().contains("section");
        // Edges are accepted.
        assertThat(ConfigCodec.parse(Map.of("catchup", Map.of("sweep-period-ticks", 0, "sweep-radius-chunks", 32,
                "feed-budget-micros", 1, "commit-budget-micros", 50_000))).config().catchUp())
                .isEqualTo(new CatchUp(0, 32, 1, 50_000));
    }
}
