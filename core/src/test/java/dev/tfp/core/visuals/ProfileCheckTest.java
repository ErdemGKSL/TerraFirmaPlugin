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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** M1-INST-03: the runtime CraftEngine solid-pool check after a reload. */
class ProfileCheckTest {
    /** Datagen's 1.21 / N/E off demand per profile (visuals/index.json, illustrative numbers). */
    static final Map<Profile, Integer> DEMAND = demand(780, 591, 339);
    static final VisualSelection L1 = new VisualSelection("1.21", Profile.LEVEL_1, false);

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
        ProfileCheck.Result r = ProfileCheck.check(L1, DEMAND, new PoolUsage(1289, 691, 591), 500);
        assertThat(r.verdict()).isEqualTo(Verdict.OK);
        assertThat(r.free()).isEqualTo(598);
        assertThat(r.othersUsed()).isEqualTo(100);
        assertThat(r.suggestion()).isEmpty();
        assertThat(r.lines()).singleElement().asString().contains("fits").contains("598");
    }

    @Test
    void doesNotFitNamesTheNextHigherProfileThatFits() {
        // Other packs took 900 first; CraftEngine ran out after 389 of our 591.
        ProfileCheck.Result r = ProfileCheck.check(L1, DEMAND, new PoolUsage(1289, 1289, 389), 500);
        assertThat(r.verdict()).isEqualTo(Verdict.FAIL);
        assertThat(r.free()).isEqualTo(-202);
        assertThat(r.suggestion()).contains(Profile.LEVEL_2); // 900 + 339 <= 1289
        assertThat(String.join("\n", r.lines()))
                .contains("does not fit")
                .contains("202 solid state(s) missing")
                .contains("visuals.profile.\"1.21\": level-2")
                .contains("does not change worlds");
    }

    @Test
    void doesNotFitSkipsHigherProfilesThatStillDoNotFit() {
        // Others hold 900: level-1 (591) does not fit either, level-2 (339) does.
        VisualSelection l0 = new VisualSelection("1.21", Profile.LEVEL_0, false);
        ProfileCheck.Result r = ProfileCheck.check(l0, DEMAND, new PoolUsage(1289, 1289, 389), 500);
        assertThat(r.verdict()).isEqualTo(Verdict.FAIL);
        assertThat(r.suggestion()).contains(Profile.LEVEL_2);
    }

    @Test
    void nothingFitsStillNamesTheNextHigherProfile() {
        VisualSelection l0 = new VisualSelection("1.21", Profile.LEVEL_0, false);
        ProfileCheck.Result r = ProfileCheck.check(l0, DEMAND, new PoolUsage(1289, 1289, 0), 500);
        assertThat(r.verdict()).isEqualTo(Verdict.FAIL);
        assertThat(r.suggestion()).contains(Profile.LEVEL_1);
        assertThat(String.join("\n", r.lines())).contains("even that does not fit");
    }

    /** PAR-INFRA-15: level-2 is the highest profile; not fitting fails with the shortfall, no suggestion and the options. */
    @Test
    void level2NotFittingFailsWithTheShortfallAndTheOptions() {
        VisualSelection top = new VisualSelection("1.21", Profile.LEVEL_2, false);
        // Other packs hold 1100 of 1289; level-2 needs 339: 150 missing.
        ProfileCheck.Result r = ProfileCheck.check(top, DEMAND, new PoolUsage(1289, 1289, 189), 500);
        assertThat(r.verdict()).isEqualTo(Verdict.FAIL);
        assertThat(r.free()).isEqualTo(-150);
        assertThat(r.suggestion()).isEmpty();
        String text = String.join("\n", r.lines());
        assertThat(text)
                .contains("150 solid state(s) missing")
                .contains("Shortfall per pool: solid 150 (capacity 1289, other packs 1100, level-2 needs 339)")
                .contains("highest profile: no higher profile exists")
                .contains("other CraftEngine packs")
                .contains("visuals.use-nether-end-blocks")
                .contains("/ce clean-cache visual_block_states")
                .doesNotContain("level-3");
    }

    @Test
    void belowTheReserveWarns() {
        // Others hold 400: 1289 - 400 - 591 = 298 < 500.
        ProfileCheck.Result r = ProfileCheck.check(L1, DEMAND, new PoolUsage(1289, 991, 591), 500);
        assertThat(r.verdict()).isEqualTo(Verdict.WARN);
        assertThat(r.free()).isEqualTo(298);
        assertThat(r.suggestion()).contains(Profile.LEVEL_2);
        assertThat(r.lines()).singleElement().asString().contains("Only 298").contains("solid-reserve-warning: 500");
    }

    @Test
    void belowTheReserveAtLevel2WarnsWithoutASuggestion() {
        VisualSelection top = new VisualSelection("1.21", Profile.LEVEL_2, false);
        ProfileCheck.Result r = ProfileCheck.check(top, DEMAND, new PoolUsage(1289, 1139, 339), 500);
        assertThat(r.verdict()).isEqualTo(Verdict.WARN);
        assertThat(r.suggestion()).isEmpty();
    }

    @Test
    void reserveZeroNeverWarns() {
        ProfileCheck.Result r = ProfileCheck.check(L1, DEMAND, new PoolUsage(1289, 1289, 591), 0);
        assertThat(r.verdict()).isEqualTo(Verdict.OK);
        assertThat(r.free()).isZero();
    }

    @Test
    void otherPacksExhaustedAfterUsIsAWarningNotAFailure() {
        // We got all 591, the pool is full: others lost blocks, ours are fine.
        ProfileCheck.Result r = ProfileCheck.check(L1, DEMAND, new PoolUsage(1289, 1289, 591), 500);
        assertThat(r.verdict()).isEqualTo(Verdict.WARN);
        assertThat(r.free()).isZero();
    }

    @Test
    void rejectsImpossibleCounts() {
        assertThatThrownBy(() -> new PoolUsage(10, 5, 6)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsStaleBeyondUsed() {
        assertThatThrownBy(() -> new PoolUsage(10, 5, 3, 3)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void staleClaimsAreOurUnusedCacheEntriesOnly() {
        Map<String, Integer> cache = new LinkedHashMap<>();
        cache.put("solid[id=tfp_clay_alfisol]", 1);   // ours, shown
        cache.put("solid[id=tfp_ore_rich_x]", 2);     // ours, not shown: stale
        cache.put("tfp:old_block[appearance=a]", 3);  // ours (block key), not shown: stale
        cache.put("solid[id=other_pack]", 4);         // foreign
        cache.put("solid[id=tfpx_lookalike]", 5);     // foreign (other namespace)
        cache.put("othermod:block", 6);               // foreign
        assertThat(ProfileCheck.staleClaims(cache, Set.of(1), "tfp"))
                .containsExactly("solid[id=tfp_ore_rich_x]", "tfp:old_block[appearance=a]");
    }

    /**
     * Regression (checks-1.21.11 server run, then with the removed level-3a; the same figures now for level-2): level-2
     * -> level-0 -> level-2 reported 476 slots "used by other packs" instead of 35, because CraftEngine's allocation cache
     * keeps (and marks used) the 441 share ids level-0 added. The check after the switch back must equal a fresh start.
     */
    @Test
    void profileSwitchBackGivesTheFreshForeignCount() {
        Map<Profile, Integer> demand = demand(780, 591, 339);
        VisualSelection l2 = new VisualSelection("1.21", Profile.LEVEL_2, false);
        VisualSelection l0 = new VisualSelection("1.21", Profile.LEVEL_0, false);
        List<String> ids2 = shareIds(0, 339);
        List<String> ids0 = new ArrayList<>(ids2);
        ids0.addAll(shareIds(339, 780));

        ProfileCheck.Result fresh = new FakeCeCache().start(ids2).check(l2, demand);
        assertThat(fresh.othersUsed()).isEqualTo(35);
        assertThat(fresh.needed()).isEqualTo(339);
        assertThat(fresh.free()).isEqualTo(915);

        FakeCeCache ce = new FakeCeCache();
        ce.start(ids2).check(l2, demand);
        ProfileCheck.Result level0 = ce.start(ids0).check(l0, demand);
        assertThat(level0.othersUsed()).isEqualTo(35);
        assertThat(level0.needed()).isEqualTo(780);
        assertThat(level0.free()).isEqualTo(474);
        FakeCeCache.Run back = ce.start(ids2);
        // The defect: counting from the pool alone, the stale level-0 ids look foreign.
        assertThat(ProfileCheck.check(l2, demand, new PoolUsage(back.capacity, back.used, back.ours), 500).othersUsed())
                .isEqualTo(476);
        ProfileCheck.Result switched = back.check(l2, demand);
        assertThat(switched.othersUsed()).isEqualTo(fresh.othersUsed());
        assertThat(switched.needed()).isEqualTo(fresh.needed());
        assertThat(switched.free()).isEqualTo(fresh.free());
        assertThat(switched.verdict()).isEqualTo(fresh.verdict());
        assertThat(switched.lines()).first().asString().contains("Released 441");
        // The stale ids were dropped from the cache: the next CraftEngine reload is identical to a fresh start.
        FakeCeCache.Run reload = ce.start(ids2);
        assertThat(reload.used).isEqualTo(35 + 339);
        assertThat(reload.check(l2, demand).lines()).isEqualTo(fresh.lines());
    }

    private static List<String> shareIds(int from, int to) {
        List<String> out = new ArrayList<>();
        for (int i = from; i < to; i++) {
            out.add("tfp_v" + i);
        }
        return out;
    }

    /**
     * CraftEngine's {@code VisualBlockStateAllocator} as read from its bytecode (CE 26.9.2): the cache survives
     * restarts, every cached entry is marked used on load whether requested or not, new requests take the next free
     * candidate. The 35 foreign slots are CraftEngine's and other packs' own.
     */
    private static final class FakeCeCache {
        static final int CAPACITY = 1289;
        final Map<String, Integer> cache = new LinkedHashMap<>();

        FakeCeCache() {
            for (int i = 0; i < 35; i++) {
                cache.put("solid[id=other_" + i + "]", i);
            }
        }

        Run start(List<String> ourIds) {
            boolean[] used = new boolean[CAPACITY];
            cache.values().forEach(v -> used[v] = true);
            Set<Integer> shown = new HashSet<>();
            int next = 0;
            for (String id : ourIds) {
                String key = "solid[id=" + id + "]";
                Integer slot = cache.get(key);
                if (slot == null) {
                    while (used[next]) {
                        next++;
                    }
                    slot = next;
                    used[slot] = true;
                    cache.put(key, slot);
                }
                shown.add(slot);
            }
            int usedCount = 0;
            for (boolean u : used) {
                usedCount += u ? 1 : 0;
            }
            // What CraftEnginePackAdapter.solid() does: count and release our stale entries.
            Set<String> stale = ProfileCheck.staleClaims(cache, shown, "tfp");
            stale.forEach(cache::remove);
            return new Run(CAPACITY, usedCount, shown.size(), stale.size());
        }

        record Run(int capacity, int used, int ours, int stale) {
            ProfileCheck.Result check(VisualSelection selection, Map<Profile, Integer> demand) {
                return ProfileCheck.check(selection, demand, new PoolUsage(capacity, used, ours, stale), 500);
            }
        }
    }
}
