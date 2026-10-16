/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.boot;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.compat.CompatGate;
import dev.tfp.core.compat.CompatSpec;
import dev.tfp.core.compat.Version;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;

/** M1-BOOT-02, Bukkit side: the shipped compat.yml, environment reading, and the refusal banner. */
class CompatCheckTest {
    static final Map<String, String> PINNED = Map.of(
            "CraftEngine", "26.9.2", "BetterModel", "3.5.1", "BetterHud", "2.2.0-SNAPSHOT-449");

    static final class Capture extends Handler {
        final List<LogRecord> records = new ArrayList<>();

        @Override
        public void publish(LogRecord r) {
            records.add(r);
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }

        List<String> at(Level level) {
            return records.stream().filter(r -> r.getLevel() == level).map(LogRecord::getMessage).toList();
        }
    }

    static Logger logger(Capture c) {
        Logger log = Logger.getAnonymousLogger();
        log.setUseParentHandlers(false);
        log.addHandler(c);
        return log;
    }

    @Test
    void shippedCompatYmlMatchesThePlan() {
        CompatSpec spec = CompatCheck.loadSpec();
        assertThat(spec.java()).isEqualTo(25);
        // Only versions run on a real server count as supported (api-version is 1.21.11, see paper-plugin.yml).
        assertThat(spec.lowestMinecraft()).isEqualTo(Version.parse("1.21.11"));
        assertThat(spec.minecraft()).extracting(CompatSpec.Range::toString).containsExactly("1.21.11-1.21.11", "26.3-26.3");
        assertThat(spec.tested()).extracting(Version::toString).containsExactly("1.21.11", "26.3");
        assertThat(spec.plugins()).containsOnlyKeys("CraftEngine", "BetterModel", "BetterHud");
        // Minimums are at least the plan's floor (CE 26.9.1, BM 3.5.0, BH 2.0.0) and never above what the spikes pinned.
        assertThat(spec.plugins().get("CraftEngine").min()).isGreaterThanOrEqualTo(Version.parse("26.9.1"));
        assertThat(spec.plugins().get("BetterModel").min()).isGreaterThanOrEqualTo(Version.parse("3.5.0"));
        assertThat(spec.plugins().get("BetterHud").min()).isGreaterThanOrEqualTo(Version.parse("2.0.0"));
        PINNED.forEach((name, v) -> assertThat(spec.plugins().get(name).min()).as(name)
                .isLessThanOrEqualTo(Version.parse(v)));
    }

    @Test
    void pinnedSetupOnTestedVersionsPassesQuietly() {
        for (String mc : List.of("1.21.11", "26.3")) {
            Capture c = new Capture();
            CompatGate.Report r = CompatCheck.run(Fakes.server(mc, PINNED), logger(c));
            assertThat(r.refused()).as(mc).isFalse();
            assertThat(c.at(Level.WARNING)).as(mc).isEmpty();
            assertThat(c.at(Level.SEVERE)).as(mc).isEmpty();
        }
    }

    @Test
    void tooOldDependencyFailsCleanlyWithOneBanner() {
        Map<String, String> plugins = new HashMap<>(PINNED);
        plugins.put("BetterModel", "3.4.0");
        Capture c = new Capture();
        CompatGate.Report r = CompatCheck.run(Fakes.server("1.21.11", plugins), logger(c));
        assertThat(r.refused()).isTrue();
        assertThat(c.at(Level.SEVERE)).singleElement().asString()
                .contains("will NOT enable", "BetterModel 3.4.0 is too old", "3.5.1");
    }

    @Test
    void missingPluginsLogDegradedMode() {
        Capture c = new Capture();
        CompatGate.Report r = CompatCheck.run(Fakes.server("26.3", Map.of("CraftEngine", "26.9.2")), logger(c));
        assertThat(r.refused()).isFalse();
        assertThat(r.missing()).containsExactly("BetterModel", "BetterHud");
        assertThat(c.at(Level.WARNING)).anyMatch(m -> m.startsWith("[compat] Degraded mode: BetterModel, BetterHud"));
    }

    @Test
    void untestedNewerMinecraftWarns() {
        Capture c = new Capture();
        CompatGate.Report r = CompatCheck.run(Fakes.server("26.4", PINNED), logger(c));
        assertThat(r.refused()).isFalse();
        assertThat(c.at(Level.WARNING)).singleElement().asString().contains("Minecraft 26.4 is newer");
    }

    @Test
    void environmentReadsInstalledVersionsOnly() {
        CompatGate.Environment env = CompatCheck.environment(
                Fakes.server("1.21.11", Map.of("BetterHud", "2.2.0", "Other", "1.0")), CompatCheck.loadSpec());
        assertThat(env.minecraft()).isEqualTo("1.21.11");
        assertThat(env.plugins()).containsExactly(Map.entry("BetterHud", "2.2.0"));
        assertThat(env.javaFeature()).isGreaterThanOrEqualTo(25);
    }
}
