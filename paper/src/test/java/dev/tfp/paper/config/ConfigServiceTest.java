/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.config;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.config.ConfigCodec;
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
        profiles.put("26", Profile.LEVEL_3B);
        TfpConfig custom = new TfpConfig(new Visuals(profiles, 7, NetherEndBlocks.FALSE),
                new AutoInstall(false, false, true, true, false));
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
                    "26": level-3b # more room for other packs
                  extra: 1
                """;
        Files.createDirectories(file.getParent());
        Files.writeString(file, owner);
        ConfigService.Outcome o = service.load();
        assertThat(o.created()).isFalse();
        assertThat(o.added()).containsExactly("auto-install");
        assertThat(o.warnings()).singleElement().asString().contains("visuals.extra");
        String text = Files.readString(file);
        assertThat(text).startsWith(owner).contains("# Automatic setup of CraftEngine").contains("download-missing: false");
        assertThat(service.current().visuals().profile()).containsEntry("26", Profile.LEVEL_3B)
                .containsEntry("1.21", Profile.LEVEL_3A);
        assertThat(service.current().visuals().solidReserveWarning()).isEqualTo(500);
        // Second start: nothing more to add, file untouched.
        ConfigService.Outcome again = service.load();
        assertThat(again.added()).isEmpty();
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
}
