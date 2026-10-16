/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.config;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.compat.Version;
import dev.tfp.core.config.TfpConfig.AutoInstall;
import dev.tfp.core.config.TfpConfig.NetherEndBlocks;
import dev.tfp.core.config.TfpConfig.Profile;
import dev.tfp.core.config.TfpConfig.Visuals;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** M1-BOOT-03: map-level round trip, defaults for missing keys, warnings for unknown/bad keys. */
class ConfigCodecTest {
    static final TfpConfig CUSTOM = new TfpConfig(
            new Visuals(Map.of("1.21", Profile.LEVEL_3B, "26", Profile.LEVEL_0), 120, NetherEndBlocks.TRUE),
            new AutoInstall(false, true, false, true, true));

    @Test
    void roundTripsDefaultsAndCustomValues() {
        for (TfpConfig c : List.of(TfpConfig.DEFAULTS, CUSTOM,
                new TfpConfig(new Visuals(Map.of(), 0, NetherEndBlocks.FALSE), AutoInstall.DEFAULTS))) {
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
            // Serialising again is stable.
            assertThat(ConfigCodec.parse(ConfigCodec.toMap(p.config())).config()).isEqualTo(p.config());
        }
    }

    @Test
    void emptyOrNullTreeGivesDefaults() {
        ConfigCodec.Parsed p = ConfigCodec.parse(Map.of());
        assertThat(p.config()).isEqualTo(TfpConfig.DEFAULTS);
        assertThat(p.warnings()).isEmpty();
        assertThat(p.defaulted()).hasSize(8).contains("visuals.profile", "auto-install.restore-deleted");
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
        assertThat(v.profileFor(Version.parse("1.21.11"))).isEqualTo(Profile.LEVEL_3A);
        assertThat(v.profileFor(Version.parse("26.3"))).isEqualTo(Profile.LEVEL_2);
        assertThat(v.profileFor(Version.parse("27.1"))).isEqualTo(Profile.LEVEL_2);
        assertThat(Visuals.family(Version.parse("1.21.4"))).isEqualTo("1.21");
        assertThat(Visuals.family(Version.parse("26.1"))).isEqualTo("26");
        assertThat(CUSTOM.visuals().profileFor(Version.parse("26.2"))).isEqualTo(Profile.LEVEL_0);
    }
}
