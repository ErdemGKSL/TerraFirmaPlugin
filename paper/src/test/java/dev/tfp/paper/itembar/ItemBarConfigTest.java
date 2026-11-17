/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.itembar;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.config.ConfigCodec;
import dev.tfp.core.config.TfpConfig;
import dev.tfp.core.itembar.HeatBar;
import dev.tfp.paper.config.ConfigService;
import dev.tfp.paper.config.Yamls;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * PAR-HEAT-15: {@code item-bars.heat} ships in config.yml with TFC's default and comment, is typed by the config service,
 * and the heat bar follows it after a {@code /tfp reload config} (the setting is read on every draw).
 */
class ItemBarConfigTest {
    static final String SHIPPED = shipped();

    @TempDir
    Path dir;
    Path file;
    ConfigService service;

    static String shipped() {
        try (InputStream in = ItemBarConfigTest.class.getClassLoader().getResourceAsStream("config.yml")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    @BeforeEach
    void setUp() {
        file = dir.resolve("config.yml");
        service = new ConfigService(file, () -> SHIPPED);
    }

    /** A heat bar over a stack that is always hot (1000 °C), gated by the live setting. */
    private static HeatBar<String> bar(BooleanSupplier enabled) {
        return new HeatBar<>(enabled, s -> Optional.of(1000f), (s, t) -> Optional.empty(), level -> 0xFFFFFF);
    }

    @Test
    void shippedKeyIsTfcDefaultWithTfcComment() {
        Map<?, ?> tree = Yamls.load(SHIPPED);
        assertThat(tree.get("item-bars")).isEqualTo(Map.of("heat", true));
        assertThat(SHIPPED).contains("If true, for items that are hot, they will show a bar on the item like a durability bar");
        ConfigCodec.Parsed p = ConfigCodec.parse(tree);
        assertThat(p.warnings()).isEmpty();
        assertThat(p.defaulted()).doesNotContain("item-bars.heat");
        assertThat(p.config().itemBars()).isEqualTo(TfpConfig.ItemBars.DEFAULTS);
    }

    @Test
    void heatBarFollowsTheConfigAcrossReloads() throws IOException {
        assertThat(service.load().warnings()).isEmpty();
        HeatBar<String> bar = bar(ItemBarModule.heatSetting(service));
        assertThat(bar.isBarVisible("ingot")).isTrue();

        Files.writeString(file, Files.readString(file).replace("  heat: true", "  heat: false"));
        ConfigService.Outcome off = service.load();
        assertThat(off.applied()).isTrue();
        assertThat(off.warnings()).isEmpty();
        assertThat(bar.isBarVisible("ingot")).as("hidden after reload, same bar instance").isFalse();

        Files.writeString(file, Files.readString(file).replace("  heat: false", "  heat: true"));
        service.load();
        assertThat(bar.isBarVisible("ingot")).isTrue();
    }

    @Test
    void badValueWarnsAndKeepsTheBarOn() throws IOException {
        service.load();
        Files.writeString(file, Files.readString(file).replace("  heat: true", "  heat: maybe"));
        ConfigService.Outcome o = service.load();
        assertThat(o.applied()).isTrue();
        assertThat(o.warnings()).singleElement().asString()
                .contains("item-bars.heat: expected true or false, got 'maybe'; using true");
        assertThat(bar(ItemBarModule.heatSetting(service)).isBarVisible("ingot")).isTrue();
    }

    @Test
    void oldConfigWithoutTheSectionGetsItAppended() throws IOException {
        Files.writeString(file, "survival:\n  keep-nutrition-after-death: false\n");
        ConfigService.Outcome o = service.load();
        assertThat(o.added()).contains("item-bars");
        assertThat(o.warnings()).noneMatch(w -> w.contains("item-bars"));
        assertThat(Files.readString(file)).contains("item-bars:\n  heat: true");
        assertThat(service.current().itemBars().heat()).isTrue();
        assertThat(service.current().survival().keepNutritionAfterDeath()).isFalse();
    }

    @Test
    void helpersReadTheTypedSetting() {
        assertThat(ItemBarModule.heatSetting(null).getAsBoolean()).isTrue();
        assertThat(ItemBarModule.heatEnabled(Map.of("item-bars", Map.of("heat", false)))).isFalse();
        assertThat(ItemBarModule.heatEnabled(Map.of("item-bars", Map.of("heat", "no")))).isTrue();
        assertThat(ItemBarModule.SECTION).isEqualTo(TfpConfig.ItemBars.SECTION);
    }
}
