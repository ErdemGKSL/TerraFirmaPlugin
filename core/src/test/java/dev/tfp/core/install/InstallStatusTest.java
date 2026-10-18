/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.install;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.install.ConfigEdit.Outcome;
import dev.tfp.core.install.InstallStatus.EditResult;
import java.util.List;
import org.junit.jupiter.api.Test;

/** M1-INST-02: the restart banner / degraded mode decision. */
class InstallStatusTest {
    static final List<ConfigEdit> EDITS = ThirdPartyEdits.all(1000);

    static ConfigEdit edit(String key) {
        return EDITS.stream().filter(e -> e.key().equals(key)).findFirst().orElseThrow();
    }

    static EditResult result(String key, Outcome o, String before, String after) {
        return new EditResult(edit(key), o, before, after, "");
    }

    @Test
    void nothingChangedMeansNormalRun() {
        InstallStatus s = InstallStatus.decide(List.of(result("misc.delay-configuration-load", Outcome.UNCHANGED, "false", "false")),
                true, false, List.of());
        assertThat(s).isEqualTo(InstallStatus.NOTHING);
    }

    @Test
    void restartKeyChangedMeansRestartBannerAndDegraded() {
        InstallStatus s = InstallStatus.decide(List.of(result("misc.delay-configuration-load", Outcome.APPLIED, "true", "false"),
                result("default-hud", Outcome.APPLIED, "test_hud", "")), true, false, List.of());
        assertThat(s.restartRequired()).isTrue();
        assertThat(s.degraded()).isTrue();
        assertThat(s.packRebuild()).isFalse();
        assertThat(String.join("\n", s.banner())).contains("RESTART REQUIRED",
                "CraftEngine/config.yml misc.delay-configuration-load: true -> false", "degraded mode")
                .doesNotContain("default-hud");
        assertThat(s.setupIncomplete()).isFalse(); // logged as a warning: the expected first start
    }

    @Test
    void packSettingChangedNeedsOnlyARebuild() {
        InstallStatus s = InstallStatus.decide(List.of(result("resource-pack.exclude-core-shaders", Outcome.APPLIED, "true", "false")),
                true, false, List.of());
        assertThat(s.restartRequired()).isFalse();
        assertThat(s.degraded()).isFalse();
        assertThat(s.packRebuild()).isTrue();
        assertThat(s.banner()).isEmpty();
        assertThat(InstallStatus.decide(List.of(), true, true, List.of()).packRebuild()).isTrue();
    }

    @Test
    void ownerKeepingAMandatoryValueIsDegradedWithoutRestart() {
        InstallStatus s = InstallStatus.decide(List.of(result("chunk-system.cache-system", Outcome.OWNER_KEPT, "false", "false")),
                true, false, List.of());
        assertThat(s.restartRequired()).isFalse();
        assertThat(s.degraded()).isTrue();
        assertThat(String.join("\n", s.banner())).contains("SETUP INCOMPLETE", "chunk-system.cache-system is false");
        assertThat(s.setupIncomplete()).isTrue();
        // A non-mandatory key kept by the owner is only a warning (logged elsewhere), not degraded.
        assertThat(InstallStatus.decide(List.of(result("resource-pack.exclude-core-shaders", Outcome.OWNER_KEPT, "true", "true")),
                true, false, List.of()).degraded()).isFalse();
    }

    @Test
    void withoutCraftEngineItsKeysDoNotMatter() {
        assertThat(InstallStatus.decide(List.of(result("chunk-system.cache-system", Outcome.FILE_MISSING, null, null)),
                false, false, List.of()).degraded()).isFalse();
        assertThat(InstallStatus.decide(List.of(result("chunk-system.cache-system", Outcome.FILE_MISSING, null, null)),
                true, false, List.of()).degraded()).isTrue();
    }

    @Test
    void restartSettingInEffectAfterCraftEngineLoadedIsNotDegraded() {
        InstallStatus s = InstallStatus.decide(List.of(result("misc.delay-configuration-load", Outcome.APPLIED, "true", "false")),
                true, false, List.of());
        InstallStatus cleared = s.restartApplied();
        assertThat(cleared.degraded()).isFalse();
        assertThat(cleared.restartRequired()).isTrue();
        assertThat(cleared.setupIncomplete()).isFalse();
        assertThat(String.join("\n", cleared.banner())).contains("RESTART REQUIRED", "not degraded")
                .doesNotContain("Until then", "degraded mode");
        assertThat(cleared.restartApplied()).isSameAs(cleared);
    }

    @Test
    void restartAppliedKeepsRequiredSetupProblemsAndNothingToClear() {
        InstallStatus s = InstallStatus.decide(List.of(result("chunk-system.cache-system", Outcome.OWNER_KEPT, "false", "false"),
                result("misc.delay-configuration-load", Outcome.APPLIED, "true", "false")), true, false, List.of());
        assertThat(s.restartApplied()).isSameAs(s);
        assertThat(InstallStatus.NOTHING.restartApplied()).isSameAs(InstallStatus.NOTHING);
        InstallStatus failed = InstallStatus.decide(List.of(result("misc.delay-configuration-load", Outcome.APPLIED, "true", "false")),
                true, false, List.of("CraftEngine pack install failed: disk full"));
        assertThat(failed.restartApplied()).isSameAs(failed);
    }

    @Test
    void installFailureIsDegraded() {
        InstallStatus s = InstallStatus.decide(List.of(), true, false, List.of("CraftEngine pack install failed: disk full"));
        assertThat(s.degraded()).isTrue();
        assertThat(s.restartRequired()).isFalse();
        assertThat(s.banner()).anyMatch(l -> l.contains("disk full"));
    }
}
