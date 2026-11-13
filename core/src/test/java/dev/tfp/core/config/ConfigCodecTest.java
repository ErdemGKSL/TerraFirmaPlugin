/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.config;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.bucket.BucketConfig;
import dev.tfp.core.bucket.BucketKind;
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
        assertThat(p.defaulted()).hasSize(24).contains("survival.keep-nutrition-after-death",
                "survival.peaceful-passive-regeneration", "survival.natural-regeneration-modifier",
                "buckets.wooden-bucket-can-place-sources",
                "buckets.metal-bucket-can-place-sources", "hud.health-display-style", "hud.disabled-experience-bar-style",
"visuals.profile", "auto-install.restore-deleted", "catchup.sweep-period-ticks",
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

    /** PAR-HUD-17: the hud section is kept as written; missing keys take TFC's defaults and are listed. */
    @Test
    void hudSectionIsKeptAsWrittenWithDefaultsFilledIn() {
        Map<String, Object> written = new LinkedHashMap<>();
        written.put("health-display-style", "VANILLA");
        written.put("climate-temperature-style", "fahrenheit");
        ConfigCodec.Parsed p = ConfigCodec.parse(Map.of("hud", written));
        assertThat(p.warnings()).isEmpty();
        assertThat(p.config().hud().section()).containsExactly(Map.entry("health-display-style", "VANILLA"),
                Map.entry("climate-temperature-style", "fahrenheit"), Map.entry("enable-experience-bar", true),
                Map.entry("disabled-experience-bar-style", "HOVER"));
        assertThat(p.defaulted()).contains("hud.enable-experience-bar", "hud.disabled-experience-bar-style")
                .doesNotContain("hud.health-display-style", "hud.climate-temperature-style");
        // Round trip.
        assertThat(ConfigCodec.parse(ConfigCodec.toMap(p.config())).config()).isEqualTo(p.config());
        assertThat(ConfigCodec.toMap(TfpConfig.DEFAULTS).get("hud")).isEqualTo(TfpConfig.Hud.DEFAULTS.section());
    }

    /** Values and unknown keys are checked by the paper layer's HudStyles; core keeps them for it and only checks the shape. */
    @Test
    void hudValuesAreLeftToThePaperLayerButTheShapeIsChecked() {
        ConfigCodec.Parsed odd = ConfigCodec.parse(Map.of("hud", Map.of("health-display-style", "BIG", "colour", 3)));
        assertThat(odd.warnings()).isEmpty();
        assertThat(odd.config().hud().section()).containsEntry("health-display-style", "BIG").containsEntry("colour", 3);
        ConfigCodec.Parsed notASection = ConfigCodec.parse(Map.of("hud", "TFC"));
        assertThat(notASection.warnings()).singleElement().asString().contains("hud: expected a section");
        assertThat(notASection.config().hud()).isEqualTo(TfpConfig.Hud.DEFAULTS);
        assertThat(ConfigCodec.parse(Map.of("hud", Map.of())).config()).isEqualTo(TfpConfig.DEFAULTS);
    }

    /** PAR-FLUID-12: TFC's bucket switches (ServerConfig "buckets"), defaulted when absent, round-tripped, warned when bad. */
    @Test
    void bucketKeysAreReadWrittenAndDefaulted() {
        assertThat(TfpConfig.DEFAULTS.buckets()).isEqualTo(new BucketConfig(false, false));
        assertThat(ConfigCodec.toMap(TfpConfig.DEFAULTS).get("buckets")).isEqualTo(Map.of(
                "wooden-bucket-can-place-sources", false, "metal-bucket-can-place-sources", false));
        for (BucketConfig b : List.of(new BucketConfig(true, false), new BucketConfig(false, true),
                new BucketConfig(true, true))) {
            TfpConfig c = new TfpConfig(Visuals.DEFAULTS, OreOverlay.DEFAULTS, AutoInstall.DEFAULTS, CatchUp.DEFAULTS,
                    TfpConfig.Hud.DEFAULTS, b);
            Map<String, Object> tree = ConfigCodec.toMap(c);
            ConfigCodec.Parsed p = ConfigCodec.parse(tree);
            assertThat(p.warnings()).isEmpty();
            assertThat(p.defaulted()).isEmpty();
            assertThat(p.config()).isEqualTo(c);
            assertThat(p.config().buckets()).isEqualTo(BucketConfig.fromTree(tree));
        }
        ConfigCodec.Parsed one = ConfigCodec.parse(Map.of("buckets", Map.of("wooden-bucket-can-place-sources", true)));
        assertThat(one.warnings()).isEmpty();
        assertThat(one.config().buckets()).isEqualTo(new BucketConfig(true, false));
        assertThat(one.config().buckets().canPlaceSources(BucketKind.WOODEN)).isTrue();
        assertThat(one.config().buckets().canPlaceSources(BucketKind.RED_STEEL)).isFalse();
        assertThat(one.defaulted()).contains("buckets.metal-bucket-can-place-sources")
                .doesNotContain("buckets.wooden-bucket-can-place-sources");
        // The 4- and 5-argument constructors keep TFC's bucket defaults.
        assertThat(new TfpConfig(Visuals.DEFAULTS, OreOverlay.DEFAULTS, AutoInstall.DEFAULTS, CatchUp.DEFAULTS).buckets())
                .isEqualTo(BucketConfig.DEFAULTS);
    }

    /** PAR-SURV-14: TFC's survival server options, defaulted to TFC's values, round-tripped. */
    @Test
    void survivalKeysAreReadWrittenAndDefaulted() {
        assertThat(TfpConfig.DEFAULTS.survival()).isEqualTo(new TfpConfig.Survival(true, false, 1d));
        assertThat(ConfigCodec.toMap(TfpConfig.DEFAULTS).get("survival")).isEqualTo(Map.of(
                "keep-nutrition-after-death", true, "peaceful-passive-regeneration", false,
                "natural-regeneration-modifier", 1d));
        for (TfpConfig.Survival sv : List.of(new TfpConfig.Survival(false, true, 0d),
                new TfpConfig.Survival(true, true, 2.5d), new TfpConfig.Survival(false, false, 100d))) {
            TfpConfig c = new TfpConfig(Visuals.DEFAULTS, OreOverlay.DEFAULTS, AutoInstall.DEFAULTS, CatchUp.DEFAULTS,
                    TfpConfig.Hud.DEFAULTS, BucketConfig.DEFAULTS, sv);
            ConfigCodec.Parsed p = ConfigCodec.parse(ConfigCodec.toMap(c));
            assertThat(p.warnings()).isEmpty();
            assertThat(p.defaulted()).isEmpty();
            assertThat(p.config()).isEqualTo(c);
        }
        // A whole number is read as the modifier too.
        ConfigCodec.Parsed one = ConfigCodec.parse(Map.of("survival", Map.of("keep-nutrition-after-death", false,
                "natural-regeneration-modifier", 3)));
        assertThat(one.warnings()).isEmpty();
        assertThat(one.config().survival()).isEqualTo(new TfpConfig.Survival(false, false, 3d));
        assertThat(one.defaulted()).contains("survival.peaceful-passive-regeneration")
                .doesNotContain("survival.keep-nutrition-after-death", "survival.natural-regeneration-modifier");
        // The 6-argument constructor keeps TFC's survival defaults.
        assertThat(new TfpConfig(Visuals.DEFAULTS, OreOverlay.DEFAULTS, AutoInstall.DEFAULTS, CatchUp.DEFAULTS,
                TfpConfig.Hud.DEFAULTS, BucketConfig.DEFAULTS).survival()).isEqualTo(TfpConfig.Survival.DEFAULTS);
    }

    @Test
    void survivalOutOfRangeModifierBadValuesAndUnknownKeysWarn() {
        ConfigCodec.Parsed high = ConfigCodec.parse(Map.of("survival", Map.of("natural-regeneration-modifier", 100.5)));
        assertThat(high.config().survival()).isEqualTo(TfpConfig.Survival.DEFAULTS);
        assertThat(high.warnings()).singleElement().asString().isEqualTo(
                "survival.natural-regeneration-modifier: expected a number in 0..100, got '100.5'; using 1");
        ConfigCodec.Parsed low = ConfigCodec.parse(Map.of("survival", Map.of("natural-regeneration-modifier", -1)));
        assertThat(low.config().survival().naturalRegenerationModifier()).isEqualTo(1d);
        assertThat(low.warnings()).singleElement().asString().contains("got '-1'");
        ConfigCodec.Parsed nan = ConfigCodec.parse(Map.of("survival", Map.of("natural-regeneration-modifier", Double.NaN)));
        assertThat(nan.config().survival()).isEqualTo(TfpConfig.Survival.DEFAULTS);
        assertThat(nan.warnings()).hasSize(1);
        ConfigCodec.Parsed bad = ConfigCodec.parse(Map.of("survival", Map.of("keep-nutrition-after-death", "no",
                "natural-regeneration-modifier", "fast", "thirst-modifier", 2)));
        assertThat(bad.config().survival()).isEqualTo(TfpConfig.Survival.DEFAULTS);
        assertThat(bad.warnings()).hasSize(3)
                .anyMatch(w -> w.contains("survival.keep-nutrition-after-death: expected true or false, got 'no'"))
                .anyMatch(w -> w.contains("survival.natural-regeneration-modifier: expected a number in 0..100, got 'fast'"))
                .anyMatch(w -> w.contains("unknown key 'survival.thirst-modifier'"));
        assertThat(ConfigCodec.parse(Map.of("survival", true)).warnings()).singleElement().asString()
                .contains("survival: expected a section");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new TfpConfig.Survival(true, false, 101d))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void bucketBadValuesAndUnknownKeysWarn() {
        Map<String, Object> tree = Map.of("buckets", Map.of("wooden-bucket-can-place-sources", "yes",
                "metal-bucket-can-place-sources", 1, "wooden-bucket-capacity", 2000));
        ConfigCodec.Parsed p = ConfigCodec.parse(tree);
        assertThat(p.config().buckets()).isEqualTo(BucketConfig.DEFAULTS).isEqualTo(BucketConfig.fromTree(tree));
        assertThat(p.warnings()).hasSize(3)
                .anyMatch(w -> w.contains("buckets.wooden-bucket-can-place-sources: expected true or false, got 'yes'; using false"))
                .anyMatch(w -> w.contains("buckets.metal-bucket-can-place-sources: expected true or false"))
                .anyMatch(w -> w.contains("unknown key 'buckets.wooden-bucket-capacity'"));
        assertThat(ConfigCodec.parse(Map.of("buckets", "on")).warnings()).singleElement().asString()
                .contains("buckets: expected a section");
        // The section itself is known: no top-level unknown-key warning.
        assertThat(ConfigCodec.parse(Map.of("buckets", Map.of())).warnings()).isEmpty();
    }
}
