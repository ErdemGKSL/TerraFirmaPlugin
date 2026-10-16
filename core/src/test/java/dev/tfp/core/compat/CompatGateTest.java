/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.compat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.tfp.core.compat.CompatGate.Environment;
import dev.tfp.core.compat.CompatGate.Finding;
import dev.tfp.core.compat.CompatGate.Report;
import dev.tfp.core.compat.CompatGate.Severity;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The M1-BOOT-02 decision table, on a spec shaped like content/compat.yml. */
class CompatGateTest {
    static final CompatSpec SPEC = CompatSpec.fromMap(specMap());

    static Map<String, Object> specMap() {
        Map<String, Object> plugins = new LinkedHashMap<>();
        plugins.put("CraftEngine", Map.of("min", "26.9.2", "tested", List.of("26.9.2"), "provides", "custom blocks"));
        plugins.put("BetterModel", Map.of("min", "3.5.1", "tested", List.of("3.5.1")));
        plugins.put("BetterHud", Map.of("min", "2.2.0-SNAPSHOT-449", "tested", List.of("2.2.0-SNAPSHOT-449")));
        return Map.of(
                "java", 25,
                "minecraft", Map.of(
                        "supported", List.of(Map.of("min", "1.21.4", "max", "1.21.11"), Map.of("min", "26.1", "max", "26.3")),
                        "tested", List.of("1.21.11", "26.2", "26.3")),
                "plugins", plugins);
    }

    static Map<String, String> allPlugins() {
        return new HashMap<>(Map.of("CraftEngine", "26.9.2", "BetterModel", "3.5.1", "BetterHud", "2.2.0-SNAPSHOT-449"));
    }

    static Report run(String mc, int java, Map<String, String> plugins) {
        return CompatGate.evaluate(SPEC, new Environment(mc, java, plugins));
    }

    static Finding about(Report r, String subject) {
        return r.findings().stream().filter(f -> f.subject().equals(subject)).findFirst().orElseThrow();
    }

    @Test
    void testedSetupPassesWithoutWarnings() {
        for (String mc : List.of("1.21.11", "26.2", "26.3")) {
            Report r = run(mc, 25, allPlugins());
            assertThat(r.refused()).as(mc).isFalse();
            assertThat(r.at(Severity.WARN)).as(mc).isEmpty();
            assertThat(r.missing()).isEmpty();
        }
    }

    @Test
    void supportedButUntestedMinecraftIsInfo() {
        Report r = run("1.21.4", 25, allPlugins());
        assertThat(r.refused()).isFalse();
        assertThat(about(r, "Minecraft").severity()).isEqualTo(Severity.INFO);
        assertThat(about(r, "Minecraft").message()).contains("not tested");
        assertThat(run("26.1", 25, allPlugins()).at(Severity.WARN)).isEmpty();
    }

    @Test
    void tooOldMinecraftRefusesWithTheSupportedRanges() {
        Report r = run("1.21.3", 25, allPlugins());
        assertThat(r.refused()).isTrue();
        assertThat(about(r, "Minecraft").message()).contains("1.21.3", "1.21.4-1.21.11", "26.1-26.3");
        assertThat(run("1.20.6", 25, allPlugins()).refused()).isTrue();
    }

    @Test
    void newerMinecraftWarnsAndContinues() {
        Report r = run("26.4", 25, allPlugins());
        assertThat(r.refused()).isFalse();
        assertThat(about(r, "Minecraft").severity()).isEqualTo(Severity.WARN);
        assertThat(about(r, "Minecraft").message()).contains("newer");
        // In the gap between the two families: not refused, flagged as untested.
        assertThat(about(run("1.21.12", 25, allPlugins()), "Minecraft").severity()).isEqualTo(Severity.WARN);
    }

    @Test
    void unreadableMinecraftVersionWarns() {
        Report r = run("unknown", 25, allPlugins());
        assertThat(r.refused()).isFalse();
        assertThat(about(r, "Minecraft").severity()).isEqualTo(Severity.WARN);
    }

    @Test
    void javaBelow25Refuses() {
        Report r = run("1.21.11", 21, allPlugins());
        assertThat(r.refused()).isTrue();
        assertThat(about(r, "Java").message()).contains("Java 21", "25");
        assertThat(run("1.21.11", 26, allPlugins()).refused()).isFalse();
    }

    @Test
    void tooOldPluginRefusesNamingPluginAndMinimum() {
        Map<String, String> p = allPlugins();
        p.put("CraftEngine", "26.9.1");
        Report r = run("1.21.11", 25, p);
        assertThat(r.refused()).isTrue();
        assertThat(r.at(Severity.REFUSE)).singleElement().satisfies(f -> {
            assertThat(f.subject()).isEqualTo("CraftEngine");
            assertThat(f.message()).contains("26.9.1", "26.9.2", "Update CraftEngine");
        });
        p = allPlugins();
        p.put("BetterHud", "2.1.0");
        assertThat(run("1.21.11", 25, p).refused()).isTrue();
    }

    @Test
    void newerPluginWarns() {
        Map<String, String> p = allPlugins();
        p.put("BetterModel", "3.6.0");
        p.put("BetterHud", "2.2.0");
        Report r = run("1.21.11", 25, p);
        assertThat(r.refused()).isFalse();
        assertThat(r.at(Severity.WARN)).extracting(Finding::subject).containsExactly("BetterModel", "BetterHud");
    }

    @Test
    void missingPluginDegradesInsteadOfRefusing() {
        Map<String, String> p = allPlugins();
        p.remove("CraftEngine");
        p.remove("BetterHud");
        Report r = run("1.21.11", 25, p);
        assertThat(r.refused()).isFalse();
        assertThat(r.missing()).containsExactly("CraftEngine", "BetterHud");
        assertThat(about(r, "CraftEngine").message()).contains("not installed", "custom blocks", "26.9.2");
    }

    @Test
    void unreadablePluginVersionWarns() {
        Map<String, String> p = allPlugins();
        p.put("BetterModel", "dev-build");
        Report r = run("1.21.11", 25, p);
        assertThat(r.refused()).isFalse();
        assertThat(about(r, "BetterModel").severity()).isEqualTo(Severity.WARN);
    }

    @Test
    void severalProblemsAreAllReported() {
        Map<String, String> p = allPlugins();
        p.put("CraftEngine", "25.0");
        Report r = run("1.21.1", 21, p);
        assertThat(r.at(Severity.REFUSE)).extracting(Finding::subject).containsExactly("Java", "Minecraft", "CraftEngine");
    }

    @Test
    void malformedSpecNamesTheKey() {
        Map<String, Object> m = new HashMap<>(specMap());
        m.put("java", "twenty-five");
        assertThatThrownBy(() -> CompatSpec.fromMap(m)).hasMessageContaining("'java'");
        Map<String, Object> m2 = new HashMap<>(specMap());
        m2.put("minecraft", Map.of("supported", List.of(Map.of("min", "1.21.4")), "tested", List.of()));
        assertThatThrownBy(() -> CompatSpec.fromMap(m2)).hasMessageContaining("minecraft.supported[].max");
        Map<String, Object> m3 = new HashMap<>(specMap());
        m3.put("minecraft", Map.of("supported", List.of(Map.of("min", "26.3", "max", "26.1")), "tested", List.of()));
        assertThatThrownBy(() -> CompatSpec.fromMap(m3)).hasMessageContaining("below min");
    }
}
