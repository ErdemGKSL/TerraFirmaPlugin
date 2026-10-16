/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.visuals;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.config.TfpConfig.Profile;
import dev.tfp.core.visuals.PackBuild.Fingerprint;
import dev.tfp.core.visuals.PackBuild.Plan;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** M1-INST-03: when the CraftEngine pack is rebuilt, and that a profile switch never touches worlds. */
class PackBuildTest {
    static final VisualSelection L3A = new VisualSelection("1.21", Profile.LEVEL_3A, false);
    static final VisualSelection L2 = new VisualSelection("1.21", Profile.LEVEL_2, false);
    static final Map<String, String> FILES = Map.of("pack.yml", "aa", "configuration/rock/granite.yml", "bb");

    @Test
    void firstInstallRebuilds() {
        Plan p = PackBuild.decide(Optional.empty(), Fingerprint.of(FILES, L3A));
        assertThat(p.rebuild()).isTrue();
        assertThat(p.migrateWorldData()).isFalse();
    }

    @Test
    void sameContentAndProfileDoesNothing() {
        Fingerprint f = Fingerprint.of(FILES, L3A);
        assertThat(PackBuild.decide(Optional.of(Fingerprint.of(Map.copyOf(FILES), L3A)), f)).isEqualTo(Plan.NOTHING);
    }

    @Test
    void profileSwitchBetweenRestartsRebuildsAndLeavesWorldsAlone() {
        // Start 1 built with level-3a; the owner switches to level-2 and restarts; then back again.
        Fingerprint built = Fingerprint.of(FILES, L3A);
        Map<String, String> level2Files = Map.of("pack.yml", "aa", "configuration/rock/granite.yml", "bb",
                "configuration/visuals/visual_templates.yml", "cc");
        Plan toL2 = PackBuild.decide(Optional.of(built), Fingerprint.of(level2Files, L2));
        assertThat(toL2.rebuild()).isTrue();
        assertThat(toL2.migrateWorldData()).isFalse();
        assertThat(toL2.reason()).contains("1.21/level-3a/nether-end-off -> 1.21/level-2/nether-end-off")
                .contains("worlds are not changed");

        Plan back = PackBuild.decide(Optional.of(Fingerprint.of(level2Files, L2)), built);
        assertThat(back.rebuild()).isTrue();
        assertThat(back.migrateWorldData()).isFalse();
    }

    @Test
    void netherEndSwitchIsAProfileChange() {
        Plan p = PackBuild.decide(Optional.of(Fingerprint.of(FILES, L3A)),
                Fingerprint.of(FILES, new VisualSelection("1.21", Profile.LEVEL_3A, true)));
        assertThat(p.rebuild()).isTrue();
        assertThat(p.migrateWorldData()).isFalse();
    }

    @Test
    void contentChangeRebuilds() {
        Plan p = PackBuild.decide(Optional.of(Fingerprint.of(FILES, L3A)),
                Fingerprint.of(Map.of("pack.yml", "aa", "configuration/rock/granite.yml", "XX"), L3A));
        assertThat(p.rebuild()).isTrue();
        assertThat(p.reason()).contains("content changed");
    }

    @Test
    void recordRoundTripsAndBadRecordsAreUnknown() {
        Fingerprint f = Fingerprint.of(FILES, L2);
        assertThat(Fingerprint.fromJson(f.toJson())).contains(f);
        Fingerprint noVisuals = Fingerprint.of(FILES, null);
        assertThat(Fingerprint.fromJson(noVisuals.toJson())).contains(noVisuals);
        assertThat(Fingerprint.fromJson("{not json")).isEmpty();
        assertThat(Fingerprint.fromJson("{\"content\":\"x\",\"family\":\"1.21\",\"profile\":\"level-9\",\"nether_end\":false}")).isEmpty();
    }

    @Test
    void fingerprintIgnoresMapOrder() {
        java.util.LinkedHashMap<String, String> a = new java.util.LinkedHashMap<>();
        a.put("b", "2");
        a.put("a", "1");
        assertThat(Fingerprint.of(a, L2)).isEqualTo(Fingerprint.of(Map.of("a", "1", "b", "2"), L2));
    }
}
