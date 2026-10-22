/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen.hitbox;

import dev.tfp.datagen.DatagenException;
import dev.tfp.datagen.hitbox.HitboxCarrier.Choice;
import dev.tfp.datagen.hitbox.HitboxCarrier.Context;
import dev.tfp.datagen.hitbox.HitboxCarrier.Scored;
import dev.tfp.datagen.hitbox.HitboxCarrier.TfcState;
import dev.tfp.datagen.visuals.Profile;
import dev.tfp.datagen.visuals.VersionFamily;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * The budget pass of the hitbox carriers: every demand of every generator, per profile x version family, in one pass,
 * so two blocks never put different looks on one vanilla state (CraftEngine aborts a block whose explicit state already
 * carries another model; byte-identical looks may share).
 *
 * <p>Demands are served by weight (higher in-world count first), then id. Each takes its best free carrier
 * ({@link HitboxCarrier#select} with the budget as the free test); a natural carrier also needs a free hide target,
 * which is then reserved (one-hop mappings: a hide target is never a carrier). When nothing free scores
 * {@value HitboxCarrier#MIN_SCORE} or more, a rare demand falls back to {@link Mode#HITBOX_ONLY} (the best-shape free
 * carrier, drawn empty, the look from entity_renderer); any other demand fails the allocation (overflow is never
 * silent: ask the owner). CE pools ({@code auto_state}) are VisualAllocator's full-cube pass and only hold appearances
 * that bring a model.
 */
public final class HitboxAllocation {
    /** Look keys of a demand: a model id ({@code tfp:block/...}), or one of these. */
    public static final String VANILLA_LOOK = "vanilla";
    public static final String TRANSPARENT = "transparent";

    /** How the carrier is drawn. */
    public enum Mode {
        /** Our block model on the carrier state. */
        MODEL,
        /** The carrier's own vanilla look (a placeholder until the block's model exists). */
        VANILLA,
        /** The carrier drawn empty ({@code transparent: true}); the look comes from entity_renderer. */
        HITBOX_ONLY,
        /** A CE auto_state pool state with our model (VisualAllocator's pools). */
        POOL
    }

    /**
     * One appearance that needs a carrier.
     *
     * @param blockId    our block id ({@code tfp:firepit})
     * @param appearance the appearance name, or a pattern the generator resolves ({@code heat_*})
     * @param tfcKey     the row of {@code hitbox/tfc-shapes.json}
     * @param light      the light TFC's state emits
     * @param look       a model id, {@link #VANILLA_LOOK} or {@link #TRANSPARENT}
     * @param rare       whether hitbox-only mode is allowed when no carrier scores high enough (devices, piles, lamps)
     * @param weight     in-world frequency rank; higher is served first
     */
    public record Demand(String blockId, String appearance, String tfcKey, int light, String look, boolean rare, int weight) {
        public String id() {
            return blockId + "#" + appearance;
        }
    }

    /** The carrier of one demand. {@code hideTarget}: where real instances of the carrier are sent, or null. */
    public record Assignment(Demand demand, Choice choice, Scored carrier, Mode mode, String hideTarget) {
        public String state() {
            return carrier.carrier();
        }

        public boolean pool() {
            return carrier.pool();
        }
    }

    /** One allocation: the assignments in demand order, the hide mappings and the look of every used state. */
    public record Result(Profile profile, VersionFamily family, List<Assignment> assignments, Map<String, String> hideMappings,
            Map<String, String> lookByState) {
        public Assignment get(String blockId, String appearance) {
            for (Assignment a : assignments) {
                if (a.demand().blockId().equals(blockId) && a.demand().appearance().equals(appearance)) {
                    return a;
                }
            }
            throw new DatagenException("no hitbox demand " + blockId + "#" + appearance + " (add it to HitboxDemands)");
        }

        /** Used states per vanilla block (pools excluded). */
        public Map<String, Set<String>> statesByBlock() {
            Map<String, Set<String>> m = new TreeMap<>();
            for (String s : lookByState.keySet()) {
                int i = s.indexOf('[');
                m.computeIfAbsent(i < 0 ? s : s.substring(0, i), k -> new TreeSet<>()).add(s);
            }
            return m;
        }
    }

    private HitboxAllocation() {
    }

    /** Allocates {@code demands} for one profile and family. Throws on overflow or a look conflict. */
    public static Result allocate(List<Demand> demands, Profile profile, VersionFamily family, Context ctx) {
        List<Demand> order = new ArrayList<>(demands);
        order.sort(Comparator.comparingInt(Demand::weight).reversed().thenComparing(Demand::id));
        Map<String, String> taken = new TreeMap<>();
        Map<String, String> hide = new TreeMap<>();
        Map<String, Assignment> byId = new LinkedHashMap<>();
        for (Demand d : order) {
            if (byId.containsKey(d.id())) {
                throw new DatagenException("duplicate hitbox demand " + d.id());
            }
            TfcState tfc = TfcState.of(d.tfcKey()).withLight(d.light());
            boolean model = !d.look().equals(VANILLA_LOOK) && !d.look().equals(TRANSPARENT);
            Choice choice = HitboxCarrier.select(tfc, profile, family, ctx, s -> free(s, d.look(), model, taken, hide));
            Mode mode;
            Scored chosen = choice.chosen();
            if (chosen != null && (choice.override() != null || chosen.score() >= HitboxCarrier.MIN_SCORE)) {
                mode = chosen.pool() ? Mode.POOL : model ? Mode.MODEL : d.look().equals(TRANSPARENT) ? Mode.HITBOX_ONLY : Mode.VANILLA;
            } else if (d.rare()) {
                chosen = null;
                for (Scored s : choice.ranked()) {
                    if (!s.pool() && free(s.carrier(), TRANSPARENT, false, taken, hide)) {
                        chosen = s;
                        break;
                    }
                }
                if (chosen == null) {
                    throw new DatagenException("hitbox overflow on " + family.id() + ": no free carrier for " + d.id());
                }
                mode = Mode.HITBOX_ONLY;
            } else {
                throw new DatagenException("hitbox overflow on " + family.id() + ": " + d.id() + " has no free carrier scoring "
                    + HitboxCarrier.MIN_SCORE + " or more and is not rare (ask the owner; never degrade silently)");
            }
            String look = mode == Mode.HITBOX_ONLY ? TRANSPARENT : d.look();
            // A vanilla look changes nothing for real instances of the state, so they need no hiding.
            String hideTarget = mode == Mode.VANILLA ? null
                : hide.containsKey(chosen.carrier()) ? hide.get(chosen.carrier()) : chosen.hideTarget();
            if (!chosen.pool()) {
                String prev = taken.putIfAbsent(chosen.carrier(), look);
                if (prev != null && !prev.equals(look)) {
                    throw new DatagenException("hitbox look conflict on " + chosen.carrier() + ": " + prev + " vs " + look);
                }
                if (hideTarget != null) {
                    if (taken.containsKey(hideTarget)) {
                        throw new DatagenException("hide target " + hideTarget + " of " + chosen.carrier() + " is a carrier");
                    }
                    String old = hide.putIfAbsent(chosen.carrier(), hideTarget);
                    if (old != null && !old.equals(hideTarget)) {
                        throw new DatagenException(chosen.carrier() + " hidden as both " + old + " and " + hideTarget);
                    }
                }
            }
            byId.put(d.id(), new Assignment(d, choice, chosen, mode, hide.get(chosen.carrier())));
        }
        List<Assignment> inDemandOrder = new ArrayList<>();
        for (Demand d : demands) {
            inDemandOrder.add(byId.get(d.id()));
        }
        return new Result(profile, family, List.copyOf(inDemandOrder), Map.copyOf(hide), Map.copyOf(taken));
    }

    private static boolean free(String state, String look, boolean model, Map<String, String> taken, Map<String, String> hide) {
        if (state.startsWith(VanillaCandidates.POOL_PREFIX)) {
            return model;
        }
        if (hide.containsValue(state)) {
            return false;
        }
        String owner = taken.get(state);
        return owner == null || owner.equals(look);
    }

    /** Every profile x family allocation of {@link HitboxDemands#all()} (cached). */
    public static synchronized Map<String, Result> standard() {
        if (standard == null) {
            Map<String, Result> m = new LinkedHashMap<>();
            for (VersionFamily f : VersionFamily.values()) {
                for (Profile p : Profile.values()) {
                    m.put(f.id() + "/" + p.id(), allocate(HitboxDemands.all(), p, f, HitboxCarrier.standardContext()));
                }
            }
            standard = m;
        }
        return standard;
    }

    private static Map<String, Result> standard;

    /**
     * The carrier of a demand whose generator writes one file for every profile and family: all allocations must agree
     * on the state, the mode and the hide target, else the generator would need one output per family.
     */
    public static Assignment agreed(String blockId, String appearance) {
        Assignment first = null;
        for (Map.Entry<String, Result> e : standard().entrySet()) {
            Assignment a = e.getValue().get(blockId, appearance);
            if (first == null) {
                first = a;
            } else if (!first.state().equals(a.state()) || first.mode() != a.mode()
                || !java.util.Objects.equals(first.hideTarget(), a.hideTarget())) {
                throw new DatagenException("hitbox carrier of " + blockId + "#" + appearance + " differs on " + e.getKey()
                    + " (" + a.state() + " vs " + first.state() + "): write one output per version family");
            }
        }
        return first;
    }
}
