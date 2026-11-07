/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.config;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.climate.ClimateTemperatureStyle;
import dev.tfp.core.config.ConfigCodec;
import dev.tfp.core.health.HealthDisplayStyle;
import dev.tfp.paper.hud.HudStyles;
import dev.tfp.paper.hud.HudStylesHolder;
import dev.tfp.core.config.TfpConfig;
import dev.tfp.core.config.TfpConfig.AutoInstall;
import dev.tfp.core.config.TfpConfig.NetherEndBlocks;
import dev.tfp.core.config.TfpConfig.Profile;
import dev.tfp.core.config.TfpConfig.Visuals;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** M1-BOOT-03 with the real shipped config.yml and real YAML text on disk. */
class ConfigServiceTest {
    @TempDir
    Path dir;
    Path file;
    ConfigService service;
    static final String SHIPPED = shipped();

    static String shipped() {
        try (InputStream in = ConfigServiceTest.class.getClassLoader().getResourceAsStream("config.yml")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    @BeforeEach
    void setUp() {
        file = dir.resolve("sub/config.yml");
        service = new ConfigService(file, () -> SHIPPED);
    }

    @Test
    void shippedDefaultsParseToTheRecordDefaultsWithNothingMissing() {
        ConfigCodec.Parsed p = ConfigCodec.parse(Yamls.load(SHIPPED));
        assertThat(p.config()).isEqualTo(TfpConfig.DEFAULTS);
        assertThat(p.warnings()).isEmpty();
        assertThat(p.defaulted()).isEmpty();
    }

    @Test
    void yamlRoundTrip() {
        Map<String, Profile> profiles = new java.util.LinkedHashMap<>();
        profiles.put("1.21", Profile.LEVEL_1);
        profiles.put("26", Profile.LEVEL_0);
        TfpConfig custom = new TfpConfig(new Visuals(profiles, 7, NetherEndBlocks.FALSE),
                new TfpConfig.OreOverlay(32, 100, false),
                new AutoInstall(false, false, true, true, false), new TfpConfig.CatchUp(40, 0, 5, 50_000));
        for (TfpConfig c : new TfpConfig[] {TfpConfig.DEFAULTS, custom}) {
            String yaml = Yamls.dump(ConfigCodec.toMap(c));
            ConfigCodec.Parsed p = ConfigCodec.parse(Yamls.load(yaml));
            assertThat(p.warnings()).as(yaml).isEmpty();
            assertThat(p.config()).as(yaml).isEqualTo(c);
            assertThat(Yamls.dump(ConfigCodec.toMap(p.config()))).isEqualTo(yaml);
        }
    }

    @Test
    void absentFileIsCreatedFromDefaults() throws IOException {
        ConfigService.Outcome o = service.load();
        assertThat(o.created()).isTrue();
        assertThat(o.applied()).isTrue();
        assertThat(o.warnings()).isEmpty();
        assertThat(Files.readString(file)).isEqualTo(SHIPPED);
        assertThat(service.current()).isEqualTo(TfpConfig.DEFAULTS);
        assertThat(dir.resolve("sub")).isDirectoryNotContaining("glob:**.tmp");
    }

    @Test
    void ownerFileKeepsCommentsAndGetsMissingSections() throws IOException {
        String owner = """
                # my notes stay
                visuals:
                  profile:
                    "26": level-3b # an old config: read as level-2 with a warning
                  extra: 1
                """;
        Files.createDirectories(file.getParent());
        Files.writeString(file, owner);
        ConfigService.Outcome o = service.load();
        assertThat(o.created()).isFalse();
        assertThat(o.added()).containsExactly("ore-overlay", "auto-install", "catchup", "hud");
        assertThat(o.warnings()).hasSize(2).anyMatch(w -> w.contains("visuals.extra"))
                .anyMatch(w -> w.contains("config.yml visuals.profile.\"26\": level-3b was removed") && w.contains("using level-2"));
        String text = Files.readString(file);
        assertThat(text).startsWith(owner).contains("# Automatic setup of CraftEngine").contains("download-missing: false");
        assertThat(service.current().visuals().profile()).containsEntry("26", Profile.LEVEL_2)
                .containsEntry("1.21", Profile.LEVEL_2);
        assertThat(service.current().oreOverlay()).isEqualTo(TfpConfig.OreOverlay.DEFAULTS);
        assertThat(service.current().catchUp()).isEqualTo(TfpConfig.CatchUp.DEFAULTS);
        assertThat(text).contains("# Catch-up of time-based blocks").contains("sweep-period-ticks: 600");
        assertThat(service.current().visuals().solidReserveWarning()).isEqualTo(500);
        // Second start: nothing more to add, file untouched (the owner's level-3b stays), the warning repeats.
        ConfigService.Outcome again = service.load();
        assertThat(again.added()).isEmpty();
        assertThat(again.warnings()).anyMatch(w -> w.contains("level-3b was removed"));
        assertThat(Files.readString(file)).isEqualTo(text);
    }

    @Test
    void reloadPicksUpEdits() throws IOException {
        service.load();
        Files.writeString(file, Files.readString(file).replace("solid-reserve-warning: 500", "solid-reserve-warning: 64"));
        ConfigService.Outcome o = service.load();
        assertThat(o.applied()).isTrue();
        assertThat(service.current().visuals().solidReserveWarning()).isEqualTo(64);
    }

    @Test
    void brokenYamlKeepsPreviousConfigAndDoesNotTouchTheFile() throws IOException {
        service.load();
        Files.writeString(file, Files.readString(file).replace("solid-reserve-warning: 500", "solid-reserve-warning: 64"));
        service.load();
        String broken = "visuals: [unclosed\n";
        Files.writeString(file, broken);
        ConfigService.Outcome o = service.load();
        assertThat(o.applied()).isFalse();
        assertThat(o.warnings()).singleElement().asString().contains("keeping the previous settings");
        assertThat(service.current().visuals().solidReserveWarning()).isEqualTo(64);
        assertThat(Files.readString(file)).isEqualTo(broken);
    }

    @Test
    void dottedFamilyKeysSurvive() {
        // Bukkit's YamlConfiguration would split "1.21" into 1 -> 21; plain SnakeYAML must not.
        Map<?, ?> tree = Yamls.load("visuals:\n  profile:\n    \"1.21\": level-0\n");
        assertThat(ConfigCodec.parse(tree).config().visuals().profile()).containsEntry("1.21", Profile.LEVEL_0);
    }

    @Test
    void shippedCatchUpSectionAndEdits() throws IOException {
        assertThat(Yamls.load(SHIPPED).containsKey("catchup")).isTrue();
        service.load();
        Files.writeString(file, Files.readString(file).replace("sweep-period-ticks: 600", "sweep-period-ticks: 40")
                .replace("sweep-radius-chunks: 4", "sweep-radius-chunks: 99"));
        ConfigService.Outcome o = service.load();
        assertThat(o.applied()).isTrue();
        assertThat(o.warnings()).singleElement().asString().contains("catchup.sweep-radius-chunks");
        assertThat(service.current().catchUp()).isEqualTo(new TfpConfig.CatchUp(40, 4, 1000, 1000));
    }

    /** PAR-HUD-17: the shipped hud section is TFC's defaults, with no warning and nothing missing. */
    @Test
    void shippedHudSectionIsTfcDefaults() {
        assertThat(Yamls.load(SHIPPED).get("hud")).isEqualTo(TfpConfig.Hud.DEFAULTS.section());
        HudStyles.Parsed p = HudStyles.parse(ConfigCodec.parse(Yamls.load(SHIPPED)).config().hud().section());
        assertThat(p.warnings()).isEmpty();
        assertThat(p.styles()).isEqualTo(HudStyles.DEFAULTS);
        assertThat(SHIPPED).contains("TFC - e.g. 750/1000", "FAHRENHEIT = Exact degrees Fahrenheit");
    }

    /** PAR-HUD-17: /tfp reload (another load) swaps the published styles; bad values warn and keep TFC's defaults. */
    @Test
    void reloadSwapsTheHudStyles() throws IOException {
        try {
            ConfigService.Outcome first = service.load();
            assertThat(first.warnings()).isEmpty();
            assertThat(service.hudStyles()).isEqualTo(HudStyles.DEFAULTS);
            assertThat(HudStylesHolder.current()).isEqualTo(HudStyles.DEFAULTS);

            Files.writeString(file, Files.readString(file).replace("health-display-style: TFC", "health-display-style: VANILLA")
                    .replace("climate-temperature-style: CELSIUS", "climate-temperature-style: FAHRENHEIT"));
            ConfigService.Outcome edited = service.load();
            assertThat(edited.warnings()).isEmpty();
            HudStyles styles = new HudStyles(HealthDisplayStyle.VANILLA, ClimateTemperatureStyle.FAHRENHEIT, true,
                    HudStyles.DisabledExperienceBarStyle.HOVER);
            assertThat(service.hudStyles()).isEqualTo(styles);
            assertThat(HudStylesHolder.current()).isEqualTo(styles);
            assertThat(HudStylesHolder.current().temperatureText(20)).isEqualTo("68.0°F");

            Files.writeString(file, Files.readString(file).replace("health-display-style: VANILLA", "health-display-style: HUGE")
                    .replace("climate-temperature-style: FAHRENHEIT", "climate-temperature-style: CELSIUS"));
            ConfigService.Outcome bad = service.load();
            assertThat(bad.applied()).isTrue();
            assertThat(bad.warnings()).singleElement().asString().contains("hud.health-display-style", "HUGE", "using TFC");
            assertThat(HudStylesHolder.current()).isEqualTo(HudStyles.DEFAULTS);

            // A broken file keeps the previous styles.
            Files.writeString(file, Files.readString(file).replace("health-display-style: HUGE", "health-display-style: VANILLA"));
            service.load();
            Files.writeString(file, "hud: [unclosed\n");
            assertThat(service.load().applied()).isFalse();
            assertThat(HudStylesHolder.current().health()).isEqualTo(HealthDisplayStyle.VANILLA);
        } finally {
            HudStylesHolder.publish(HudStyles.DEFAULTS);
        }
    }
}
