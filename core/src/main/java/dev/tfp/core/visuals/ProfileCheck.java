/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.visuals;

import dev.tfp.core.config.TfpConfig.Profile;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The runtime capacity check of the CraftEngine {@code solid} pool after a CraftEngine reload
 * (ARCHITECTURE.md §9.1, M1-INST-03). The installed {@code mappings.yml} and other CE packs differ per
 * server, so datagen's numbers are only an estimate; this decides from the real counts. Pure.
 *
 * <p>Fits means: the slots other packs hold plus our profile's demand fit in the pool. When CraftEngine ran
 * out while allocating ours, {@code ours < needed} and the free count goes negative, so the same rule
 * catches it.
 */
public final class ProfileCheck {
    private ProfileCheck() {
    }

    /**
     * The real CraftEngine {@code solid} pool after a reload.
     *
     * @param capacity candidate states in the pool ({@code AutoStateGroup.SOLID.candidateCount()})
     * @param used     candidates in use by anyone
     * @param ours     candidates in use by our ({@code tfp}) blocks
     * @param stale    candidates CraftEngine still holds for our share ids that no loaded block uses any more
     *                 (its allocation cache keeps every id it ever assigned, e.g. those of the previous visual
     *                 profile; {@link #staleClaims}). They are ours, not other packs', and are released by
     *                 dropping them from the cache: free again after the next CraftEngine reload.
     */
    public record PoolUsage(int capacity, int used, int ours, int stale) {
        public PoolUsage {
            if (capacity < 0 || used < 0 || ours < 0 || stale < 0 || ours + stale > used) {
                throw new IllegalArgumentException("bad pool usage: capacity=" + capacity + " used=" + used + " ours=" + ours
                        + " stale=" + stale);
            }
        }

        public PoolUsage(int capacity, int used, int ours) {
            this(capacity, used, ours, 0);
        }
    }

    /**
     * The entries of CraftEngine's visual-state allocation cache ({@code VisualBlockStateAllocator}, persisted
     * across restarts) that are ours but no loaded block of ours uses. CraftEngine marks every cached entry as
     * used on load, requested or not, so after a profile switch the previous profile's share ids keep their pool
     * slots. Ours: a share id {@code <group>[id=<namespace>_...]} (datagen's {@code auto_state.id}) or a block
     * key {@code <namespace>:...}.
     *
     * @param cache     CraftEngine's cache: key to the visual state's registry id
     * @param inUse     registry ids our loaded blocks show
     * @param namespace our namespace ({@code tfp})
     * @return the stale keys, in cache order
     */
    public static Set<String> staleClaims(Map<String, Integer> cache, Set<Integer> inUse, String namespace) {
        Pattern ours = Pattern.compile("^(?:[a-z0-9_]+\\[id=" + Pattern.quote(namespace + "_") + "|" + Pattern.quote(namespace + ":") + ")");
        Set<String> out = new LinkedHashSet<>();
        for (Map.Entry<String, Integer> e : cache.entrySet()) {
            if (ours.matcher(e.getKey()).find() && !inUse.contains(e.getValue())) {
                out.add(e.getKey());
            }
        }
        return out;
    }

    public enum Verdict {
        /** Fits with at least the reserve left for other packs. */
        OK,
        /** Fits, but fewer than {@code visuals.solid-reserve-warning} slots stay free. */
        WARN,
        /** Our blocks do not all fit: some have no visual. */
        FAIL
    }

    /**
     * @param needed     solid slots the selected profile needs
     * @param othersUsed solid slots other packs hold
     * @param free       slots left after us (negative: missing)
     * @param suggestion the profile to switch to ({@code FAIL}: the next higher profile, preferring the first
     *                   that fits; {@code WARN}: the next higher profile), empty if there is none
     * @param lines      the log message
     */
    public record Result(Verdict verdict, int needed, int othersUsed, int free, Optional<Profile> suggestion, List<String> lines) {
        public Result {
            lines = List.copyOf(lines);
        }
    }

    /**
     * @param selection the running allocation
     * @param demand    solid demand per profile for the same family and Nether/End setting ({@link VisualIndex#solidDemand})
     * @param pool      the real pool counts
     * @param reserve   {@code visuals.solid-reserve-warning}
     */
    public static Result check(VisualSelection selection, Map<Profile, Integer> demand, PoolUsage pool, int reserve) {
        int needed = demand.getOrDefault(selection.profile(), pool.ours());
        int others = pool.used() - pool.ours() - pool.stale();
        int free = pool.capacity() - others - needed;
        String key = "visuals.profile.\"" + selection.family() + "\"";
        List<String> lines = new ArrayList<>();
        String counts = "CraftEngine solid pool: " + pool.capacity() + " states, " + others + " used by other packs, "
                + needed + " needed by " + selection.profile().id() + " (" + pool.ours() + " allocated)";
        if (pool.stale() > 0) {
            lines.add("Released " + pool.stale() + " CraftEngine solid state(s) still held for an earlier TerraFirmaPlugin "
                    + "visual profile; they are free after the next CraftEngine reload or restart.");
            if (free >= 0 && pool.ours() < needed) {
                lines.add(counts + ". " + (needed - pool.ours()) + " TerraFirmaPlugin block visual(s) are missing until then.");
                return new Result(Verdict.WARN, needed, others, free, Optional.empty(), lines);
            }
        }
        if (free < 0) {
            Optional<Profile> fitting = higher(selection.profile()).stream()
                    .filter(p -> demand.containsKey(p) && pool.capacity() - others - demand.get(p) >= 0)
                    .findFirst();
            Optional<Profile> next = fitting.or(() -> higher(selection.profile()).stream().findFirst());
            lines.add("Visual profile " + selection.profile().id() + " (" + selection.id() + ") does not fit: "
                    + (-free) + " solid state(s) missing, so some TerraFirmaPlugin blocks have no visual.");
            lines.add(counts + ".");
            if (next.isPresent()) {
                Profile p = next.get();
                lines.add("Set " + key + ": " + p.id() + " in plugins/TerraFirmaPlugin/config.yml and restart"
                        + (demand.containsKey(p) ? " (needs " + demand.get(p) + ")" : "")
                        + (fitting.isEmpty() ? "; even that does not fit: remove blocks of other CraftEngine packs" : "")
                        + ". Switching profiles does not change worlds.");
            } else {
                lines.add(selection.profile().id() + " is the highest profile: remove blocks of other CraftEngine packs.");
            }
            return new Result(Verdict.FAIL, needed, others, free, next, lines);
        }
        if (free < reserve) {
            Optional<Profile> next = higher(selection.profile()).stream().findFirst();
            lines.add("Only " + free + " CraftEngine solid state(s) stay free for other packs (visuals.solid-reserve-warning: "
                    + reserve + "). " + counts + "."
                    + next.map(p -> " To free more, set " + key + ": " + p.id() + " and restart.").orElse(""));
            return new Result(Verdict.WARN, needed, others, free, next, lines);
        }
        lines.add("Visual profile " + selection.profile().id() + " (" + selection.id() + ") fits: " + free
                + " solid state(s) free for other packs. " + counts + ".");
        return new Result(Verdict.OK, needed, others, free, Optional.empty(), lines);
    }

    /** Profiles above {@code p}, nearest first. */
    static List<Profile> higher(Profile p) {
        Profile[] all = Profile.values();
        return List.of(all).subList(p.ordinal() + 1, all.length);
    }
}
