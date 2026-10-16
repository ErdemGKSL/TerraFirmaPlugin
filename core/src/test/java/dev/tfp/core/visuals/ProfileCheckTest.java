/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.visuals;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.tfp.core.config.TfpConfig.Profile;
import dev.tfp.core.visuals.ProfileCheck.PoolUsage;
import dev.tfp.core.visuals.ProfileCheck.Verdict;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** M1-INST-03: the runtime CraftEngine solid-pool check after a reload. */
class ProfileCheckTest {
    /** Datagen's 1.21 / N/E off demand (visuals/index.json). */
    static final Map<Profile, Integer> DEMAND = demand(780, 780, 591, 339, 87);
    static final VisualSelection L2 = new VisualSelection("1.21", Profile.LEVEL_2, false);

    static Map<Profile, Integer> demand(int... d) {
        Map<Profile, Integer> m = new LinkedHashMap<>();
        for (Profile p : Profile.values()) {
            m.put(p, d[p.ordinal()]);
        }
        return m;
    }

    @Test
    void fitsWithTheReserveFree() {
        // 1289 states, other packs hold 100, we allocated all 591.
        ProfileCheck.Result r = ProfileCheck.check(L2, DEMAND, new PoolUsage(1289, 691, 591), 500);
        assertThat(r.verdict()).isEqualTo(Verdict.OK);
        assertThat(r.free()).isEqualTo(598);
        assertThat(r.othersUsed()).isEqualTo(100);
        assertThat(r.suggestion()).isEmpty();
        assertThat(r.lines()).singleElement().asString().contains("fits").contains("598");
    }

    @Test
    void doesNotFitNamesTheNextHigherProfileThatFits() {
        // Other packs took 900 first; CraftEngine ran out after 389 of our 591.
        ProfileCheck.Result r = ProfileCheck.check(L2, DEMAND, new PoolUsage(1289, 1289, 389), 500);
        assertThat(r.verdict()).isEqualTo(Verdict.FAIL);
        assertThat(r.free()).isEqualTo(-202);
        assertThat(r.suggestion()).contains(Profile.LEVEL_3A); // 900 + 339 <= 1289
        assertThat(String.join("\n", r.lines()))
                .contains("does not fit")
                .contains("202 solid state(s) missing")
                .contains("visuals.profile.\"1.21\": level-3a")
                .contains("does not change worlds");
    }

    @Test
    void doesNotFitSkipsHigherProfilesThatStillDoNotFit() {
        // Others hold 1100: level-3a (339) does not fit either, level-3b (87) does.
        ProfileCheck.Result r = ProfileCheck.check(L2, DEMAND, new PoolUsage(1289, 1289, 189), 500);
        assertThat(r.verdict()).isEqualTo(Verdict.FAIL);
        assertThat(r.suggestion()).contains(Profile.LEVEL_3B);
    }

    @Test
    void nothingFitsStillNamesTheNextHigherProfile() {
        ProfileCheck.Result r = ProfileCheck.check(L2, DEMAND, new PoolUsage(1289, 1289, 0), 500);
        assertThat(r.verdict()).isEqualTo(Verdict.FAIL);
        assertThat(r.suggestion()).contains(Profile.LEVEL_3A);
        assertThat(String.join("\n", r.lines())).contains("even that does not fit");
    }

    @Test
    void highestProfileHasNoSuggestion() {
        VisualSelection top = new VisualSelection("1.21", Profile.LEVEL_3B, false);
        ProfileCheck.Result r = ProfileCheck.check(top, DEMAND, new PoolUsage(100, 100, 50), 500);
        assertThat(r.verdict()).isEqualTo(Verdict.FAIL);
        assertThat(r.suggestion()).isEmpty();
        assertThat(String.join("\n", r.lines())).contains("highest profile");
    }

    @Test
    void belowTheReserveWarns() {
        // Others hold 400: 1289 - 400 - 591 = 298 < 500.
        ProfileCheck.Result r = ProfileCheck.check(L2, DEMAND, new PoolUsage(1289, 991, 591), 500);
        assertThat(r.verdict()).isEqualTo(Verdict.WARN);
        assertThat(r.free()).isEqualTo(298);
        assertThat(r.suggestion()).contains(Profile.LEVEL_3A);
        assertThat(r.lines()).singleElement().asString().contains("Only 298").contains("solid-reserve-warning: 500");
    }

    @Test
    void reserveZeroNeverWarns() {
        ProfileCheck.Result r = ProfileCheck.check(L2, DEMAND, new PoolUsage(1289, 1289, 591), 0);
        assertThat(r.verdict()).isEqualTo(Verdict.OK);
        assertThat(r.free()).isZero();
    }

    @Test
    void otherPacksExhaustedAfterUsIsAWarningNotAFailure() {
        // We got all 591, the pool is full: others lost blocks, ours are fine.
        ProfileCheck.Result r = ProfileCheck.check(L2, DEMAND, new PoolUsage(1289, 1289, 591), 500);
        assertThat(r.verdict()).isEqualTo(Verdict.WARN);
        assertThat(r.free()).isZero();
    }

    @Test
    void rejectsImpossibleCounts() {
        assertThatThrownBy(() -> new PoolUsage(10, 5, 6)).isInstanceOf(IllegalArgumentException.class);
    }
}
