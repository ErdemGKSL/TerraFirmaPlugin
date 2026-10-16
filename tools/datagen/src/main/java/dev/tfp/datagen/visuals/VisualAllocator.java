/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen.visuals;

import dev.tfp.datagen.CeBlockStates.ModelRef;
import dev.tfp.datagen.DatagenException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.Function;

/**
 * M1-DGN-04 visual allocator: assigns every emitted CE appearance a vanilla visual state, per
 * {@link Profile} × {@link VersionFamily} × Nether/End setting. Deterministic.
 *
 * <ol>
 *   <li>Demand: every appearance; the profile may make it reuse another appearance's look
 *       ({@link Profile#lookOf}). Appearances whose models are byte-identical (same model JSON, rotation,
 *       uvlock, weight) form one <em>unit</em> and share one vanilla state (hardened rock = raw rock).</li>
 *   <li>Supply, in order: identity duplicates → Nether/End (only when enabled) → absent-in-TFC, clean before
 *       minor-caveat in each ({@link VanillaSupply}), then CE's {@code auto_state: solid} pool. Non-pool sources
 *       go first so the solid pool stays free for other CraftEngine packs (ARCHITECTURE.md §9.1).</li>
 *   <li>Overflow of the solid pool throws (the build fails).</li>
 *   <li>Every claimed explicit state gets a {@code block_state_mappings} entry ({@link HideTargets}); states CE
 *       maps itself are never in the supply, so no CE mapping is repeated.</li>
 * </ol>
 */
public final class VisualAllocator {
    private VisualAllocator() {
    }

    /** One shared look: the models drawn, the appearances using it, and the visual assigned to it. */
    public record Unit(String id, List<ModelRef> models, List<VisualDemand> members, VanillaSupply.State state,
                       String solidId) {
        /** The CE visual part of an appearance: {@code state: ...} or {@code auto_state: {type: solid, id: ...}}. */
        public Map<String, Object> visual() {
            Map<String, Object> v = new LinkedHashMap<>();
            if (state != null) {
                v.put("state", state.state());
            } else {
                Map<String, Object> auto = new LinkedHashMap<>();
                auto.put("type", "solid");
                auto.put("id", solidId);
                v.put("auto_state", auto);
            }
            return v;
        }
    }

    public record Allocation(Profile profile, VersionFamily family, boolean netherEnd, List<Unit> units,
                             Map<String, Unit> unitByDemand, List<VisualDemand> demand,
                             SortedMap<String, String> mappings, Map<String, Integer> capacity,
                             Map<String, Integer> used, int sharedByProfile) {
        public int solidUsed() {
            return used.getOrDefault("solid", 0);
        }

        public int solidFree() {
            return family.solidPool() - solidUsed();
        }

        public int explicitUsed() {
            return units.size() - solidUsed();
        }

        /** Directory of this allocation's files in the content tree, e.g. {@code visuals/1.21/level-2/nether-end-off}. */
        public String dir() {
            return dir(family, profile, netherEnd);
        }

        public static String dir(VersionFamily family, Profile profile, boolean netherEnd) {
            return "visuals/" + family.id() + "/" + profile.id() + "/nether-end-" + (netherEnd ? "on" : "off");
        }
    }

    /** Pool labels in report order. */
    public static final List<String> POOLS = List.of("identity", "nether_end", "absent_clean", "absent_minor", "solid");

    /**
     * @param modelContent model id (e.g. {@code tfp:block/rock/raw/granite}) → its JSON text, used to find
     *                     appearances that look the same through different model files
     */
    public static Allocation allocate(List<VisualDemand> demand, Function<String, String> modelContent,
                                      Profile profile, VersionFamily family, boolean netherEnd, VanillaSupply supply) {
        Map<String, VisualDemand> byKey = new LinkedHashMap<>();
        for (VisualDemand d : demand) {
            if (byKey.put(d.key(), d) != null) {
                throw new DatagenException("duplicate appearance " + d.key());
            }
        }
        // 1. units
        Map<List<String>, List<VisualDemand>> groups = new LinkedHashMap<>();
        Map<List<String>, List<ModelRef>> groupModels = new LinkedHashMap<>();
        int shared = 0;
        for (VisualDemand d : demand) {
            VisualDemand look = profile.lookOf(d, byKey);
            if (look != d) {
                shared++;
            }
            List<String> key = new ArrayList<>();
            for (ModelRef m : look.models()) {
                key.add(modelContent.apply(m.path()) + "|" + m.x() + "|" + m.y() + "|" + m.uvlock() + "|" + m.weight());
            }
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(d);
            groupModels.putIfAbsent(key, look.models());
        }
        // 2. supply
        List<VanillaSupply.State> candidates = supply.candidates(family, netherEnd, HideTargets.RESERVED);
        Map<String, Integer> capacity = new LinkedHashMap<>();
        Map<String, Integer> used = new LinkedHashMap<>();
        for (String p : POOLS) {
            capacity.put(p, 0);
            used.put(p, 0);
        }
        candidates.forEach(s -> capacity.merge(s.pool(), 1, Integer::sum));
        capacity.put("solid", family.solidPool());
        int needed = groups.size();
        int available = candidates.size() + family.solidPool();
        if (needed > available) {
            throw new DatagenException("visual profile " + profile.id() + " overflows on MC " + family.id()
                + (netherEnd ? " (Nether/End on)" : " (Nether/End off)") + ": " + needed + " visuals needed, "
                + candidates.size() + " explicit states + " + family.solidPool() + " solid-pool states available. "
                + "Use a higher profile or reduce demand (see reports/visual-allocation-*.md).");
        }
        // 3. assign
        List<Unit> units = new ArrayList<>();
        Map<String, Unit> byDemand = new LinkedHashMap<>();
        int i = 0;
        for (Map.Entry<List<String>, List<VisualDemand>> g : groups.entrySet()) {
            VisualDemand first = g.getValue().getFirst();
            String id = first.key();
            VanillaSupply.State s = i < candidates.size() ? candidates.get(i) : null;
            Unit u = new Unit(id, groupModels.get(g.getKey()), List.copyOf(g.getValue()), s,
                s == null ? solidId(id) : null);
            used.merge(s == null ? "solid" : s.pool(), 1, Integer::sum);
            units.add(u);
            g.getValue().forEach(d -> byDemand.put(d.key(), u));
            i++;
        }
        // 4. mappings
        Set<String> allocated = new HashSet<>();
        units.stream().filter(u -> u.state() != null).forEach(u -> allocated.add(u.state().state()));
        SortedMap<String, String> mappings = new TreeMap<>();
        for (Unit u : units) {
            if (u.state() != null) {
                mappings.put(u.state().state(), HideTargets.target(u.state(), allocated, supply, family));
            }
        }
        return new Allocation(profile, family, netherEnd, List.copyOf(units), byDemand, List.copyOf(demand),
            mappings, capacity, used, shared);
    }

    /** Stable {@code auto_state} share id of a unit (CE caches allocations by this name). */
    static String solidId(String unitId) {
        return "tfp_" + unitId.replace('/', '_').replace('#', '_');
    }
}
