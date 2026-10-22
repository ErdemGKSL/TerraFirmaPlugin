/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen.hitbox;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.tfp.datagen.DatagenException;
import dev.tfp.datagen.Json;
import dev.tfp.datagen.hitbox.TfcShapes.Kind;
import dev.tfp.datagen.hitbox.TfcShapes.Row;
import dev.tfp.datagen.hitbox.VanillaCandidates.BlockPolicy;
import dev.tfp.datagen.hitbox.VanillaCandidates.Presence;
import dev.tfp.datagen.hitbox.VanillaCandidates.VState;
import dev.tfp.datagen.visuals.Profile;
import dev.tfp.datagen.visuals.VersionFamily;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Predicate;

/**
 * The hitbox carrier selector (PAR-INFRA-13): for one TFC block state, the vanilla state whose selection outline and
 * collision best match TFC's, among the states a vanilla client can be sent without side effects.
 *
 * <p><b>Score.</b> {@code (0.6 IoU(selection) + 0.4 IoU(collision)) x quality x penalties}, IoU exact on the shapes'
 * own coordinate grid; two empty collisions count as equal (1), empty against non-empty as 0. Selection weighs more:
 * the outline is what players see hug the model. {@code quality = min(1, fill_cand / fill_tfc) x (1 - centroid
 * offset / 16)}. Penalties: x {@value #HIDE_PENALTY} for a natural carrier that needs a hide mapping; x {@value #PENALTY}
 * each for a replaceable carrier
 * (the client predicts placing into it), an occluding full-cube carrier under a non-full or see-through TFC block
 * (x-ray of the neighbours). The score of a family is the worst over its researched versions (the client draws the
 * outline from its own shapes).
 *
 * <p><b>Hard filters</b> (listed with their reason in {@link Choice#rejected()}): states missing in a version of the
 * family, blocks the policy marks player/reserved/reference, the hard-ban list, lit states of blocks with client
 * particles, the owner's object ban list for every class except ground pieces, light-emitting states unless TFC emits
 * the same light, waterlogged states unless TFC's state is waterlogged, random-offset states, natural states with no
 * free hide target, and the geometric quality filter ({@link #geometricRejection}).
 *
 * <p><b>Ties</b>: the vanilla block of the same name as TFC's (farmland on farmland), more shared property values
 * (a facing kept), the same occlusion (an opaque full cube on the solid pool, a see-through one on the leaves pool), a CE
 * pool before an explicit state, fewer states consumed (button pairs), then state id order.
 *
 * <p><b>Class overrides</b> ({@code hitbox/class-overrides.json}) replace the ranking for a shape class by an ordered
 * owner list; an entry with a row in {@code hitbox/unusable.json} (failed in game) is skipped.
 */
public final class HitboxCarrier {
    public static final double W_SEL = 0.6;
    public static final double W_COL = 0.4;
    public static final double PENALTY = 0.9;
    /**
     * The hide-mapping penalty is milder: hiding costs only the look of real instances of one state (real 7-layer snow
     * drawn as 8 layers). Calibrated so the charcoal forge keeps snow[layers=7] (exact outline, collision 2 px low,
     * 0.943 x 0.97 = 0.915) over dirt_path (15 px, 0.904), the result PAR-METAL-07 shipped and verified in game.
     */
    public static final double HIDE_PENALTY = 0.97;
    /** Below this score a demand falls back to hitbox-only mode (rare blocks) or fails the allocation. */
    public static final double MIN_SCORE = 0.5;
    public static final double MAX_CENTROID_XZ = 3;
    public static final double MAX_CENTROID_Y = 4;
    public static final double MIN_FILL_FACTOR = 0.6;
    public static final double MIN_PART_IOU = 0.5;

    /** The TFC side of a selection: its shape row, the light it emits and whether it is waterlogged. */
    public record TfcState(Row row, int light, boolean waterlogged) {
        public static TfcState of(String key) {
            Row r = TfcShapes.load().get(key);
            if (r == null) {
                throw new DatagenException("hitbox/tfc-shapes.json has no row " + key);
            }
            return new TfcState(r, 0, false);
        }

        public TfcState withLight(int l) {
            return new TfcState(row, l, waterlogged);
        }
    }

    /** One scored candidate in the family's worst version. {@code hideTarget}: where real instances go, or null. */
    public record Scored(String carrier, double selIoU, double colIoU, double quality, double penalty, double score,
            String worstVersion, int statesConsumed, String hideTarget, boolean fullOccluder, List<String> notes) {
        public boolean pool() {
            return carrier.startsWith(VanillaCandidates.POOL_PREFIX);
        }

        public String block() {
            int i = carrier.indexOf('[');
            return i < 0 ? carrier : carrier.substring(0, i);
        }
    }

    /** A candidate a hard filter removed; {@code rawScore}: its score without filters or penalties (NaN when absent). */
    public record Rejected(String carrier, String reason, double rawScore) {
    }

    /**
     * The selector's answer: the chosen carrier, the owner override that chose it (null when ranked) and its index in
     * the override list (0 = primary, above 0 = a fallback that needs a ledger row), the ranking and the rejections.
     */
    public record Choice(TfcState tfc, VersionFamily family, Scored chosen, String override, int overrideIndex,
            List<Scored> ranked, List<Rejected> rejected) {
    }

    /** One owner override entry. */
    public record OverrideEntry(String carrier, String reason) {
    }

    /** One unusable.json row. */
    public record Unusable(String shapeClass, String carrier, String family, String failedCheck, String date, String verification) {
    }

    /** The inputs of the selector (the committed tables by default; tests swap the unusable rows). */
    public record Context(VanillaCandidates vanilla, Map<String, List<OverrideEntry>> overrides, List<Unusable> unusable) {
        public Context withUnusable(List<Unusable> rows) {
            return new Context(vanilla, overrides, List.copyOf(rows));
        }

        boolean isUnusable(String shapeClass, String carrier, VersionFamily family) {
            return unusable.stream().anyMatch(u -> u.shapeClass().equals(shapeClass) && u.carrier().equals(carrier)
                && u.family().equals(family.id()));
        }
    }

    private static Context standard;

    private HitboxCarrier() {
    }

    /** The committed policy, overrides and unusable rows (cached). */
    public static synchronized Context standardContext() {
        if (standard == null) {
            standard = new Context(VanillaCandidates.load(), overrides(VanillaCandidates.resource("class-overrides.json")),
                unusable(VanillaCandidates.resource("unusable.json")));
        }
        return standard;
    }

    static Map<String, List<OverrideEntry>> overrides(String text) {
        Map<String, List<OverrideEntry>> out = new TreeMap<>();
        JsonObject classes = Json.parse(text, "class-overrides.json").getAsJsonObject().getAsJsonObject("classes");
        for (Map.Entry<String, JsonElement> e : classes.entrySet()) {
            List<OverrideEntry> list = new ArrayList<>();
            for (JsonElement x : e.getValue().getAsJsonArray()) {
                JsonObject o = x.getAsJsonObject();
                list.add(new OverrideEntry(o.get("carrier").getAsString(), o.get("reason").getAsString()));
            }
            out.put(e.getKey(), List.copyOf(list));
        }
        return out;
    }

    static List<Unusable> unusable(String text) {
        List<Unusable> out = new ArrayList<>();
        for (JsonElement x : Json.parse(text, "unusable.json").getAsJsonObject().getAsJsonArray("rows")) {
            JsonObject o = x.getAsJsonObject();
            out.add(new Unusable(o.get("class").getAsString(), o.get("carrier").getAsString(), o.get("family").getAsString(),
                o.get("failed_check").getAsString(), o.get("date").getAsString(), o.get("verification").getAsString()));
        }
        return List.copyOf(out);
    }

    /** The best carrier for {@code tfc} in {@code family}, with no budget (every state free). */
    public static Choice select(TfcState tfc, Profile profile, VersionFamily family) {
        return select(tfc, profile, family, standardContext(), s -> true);
    }

    /**
     * The best carrier whose state satisfies {@code free} (the allocation's budget); a hide target must also be free.
     * The profile does not change carriers today (profiles only change full-cube looks); it is part of the signature
     * so a profile rule can be added without touching every caller.
     */
    public static Choice select(TfcState tfc, Profile profile, VersionFamily family, Context ctx, Predicate<String> free) {
        VanillaCandidates v = ctx.vanilla();
        Row row = tfc.row();
        List<OverrideEntry> override = ctx.overrides().get(row.shapeClass());
        List<Scored> ranked = new ArrayList<>();
        List<Rejected> rejected = new ArrayList<>();
        Set<String> all = v.allStates();
        for (String state : all) {
            String reason = filter(tfc, family, v, state);
            if (reason != null) {
                rejected.add(new Rejected(state, reason, rawScore(tfc, family, v, state)));
                continue;
            }
            Scored s = score(tfc, family, v, state, free);
            if (s == null) {
                rejected.add(new Rejected(state, "natural block with no free hide target", rawScore(tfc, family, v, state)));
            } else {
                ranked.add(s);
            }
        }
        ranked.sort(order(row));
        if (override != null) {
            for (int i = 0; i < override.size(); i++) {
                OverrideEntry e = override.get(i);
                if (!v.inFamily(e.carrier(), family) || ctx.isUnusable(row.shapeClass(), e.carrier(), family)
                    || !free.test(e.carrier())) {
                    continue;
                }
                Scored s = score(tfc, family, v, e.carrier(), free);
                if (s == null) {
                    continue;
                }
                return new Choice(tfc, family, s, e.reason(), i, ranked, rejected);
            }
            throw new DatagenException("hitbox class " + row.shapeClass() + ": no override carrier is available, free and "
                + "usable on " + family.id() + " (ask the owner; never degrade silently)");
        }
        for (Scored s : ranked) {
            if (free.test(s.carrier())) {
                return new Choice(tfc, family, s, null, -1, ranked, rejected);
            }
        }
        return new Choice(tfc, family, null, null, -1, ranked, rejected);
    }

    /** The first hard filter that rejects {@code state} for {@code tfc}, or null. */
    static String filter(TfcState tfc, VersionFamily family, VanillaCandidates v, String state) {
        if (!v.inFamily(state, family)) {
            return "not in every " + family.id() + " version";
        }
        if (v.isPool(state)) {
            return null;
        }
        String block = state.substring(0, state.contains("[") ? state.indexOf('[') : state.length());
        String ban = v.hardBan(block);
        if (ban != null) {
            return "banned: " + ban;
        }
        BlockPolicy p = v.policy(block);
        if (p.presence() != Presence.ABSENT && p.presence() != Presence.NATURAL) {
            return p.presence().name().toLowerCase(java.util.Locale.ROOT) + ": " + p.note();
        }
        String excluded = v.excludedState(state);
        if (excluded != null) {
            return "excluded: " + excluded;
        }
        if (v.litParticles(block) && state.contains("lit=true")) {
            return "lit: client particles";
        }
        if (tfc.row().kind() != Kind.GROUND && v.objectBanned(block)) {
            return "object ban: " + v.objectBanReason();
        }
        for (String version : family.researchedVersions()) {
            VState s = v.get(version, state);
            if (s.light() != 0 && s.light() != tfc.light()) {
                return "emits light " + s.light() + " (TFC " + tfc.light() + ")";
            }
            if (s.flag("fluid") && !tfc.waterlogged()) {
                return "waterlogged (TFC is not)";
            }
            if (s.flag("random_offset")) {
                return "random offset: the outline moves per position";
            }
            String geo = geometricRejection(tfc.row().selection(), s.selection());
            if (geo != null) {
                return geo + " (" + version + ")";
            }
        }
        return null;
    }

    /**
     * The owner's shape-quality filter on the selection outline: more 6-neighbour parts than TFC's (unless TFC is itself
     * multi-part and every candidate part overlaps a TFC part with IoU >= 0.5), a bounding-box fill below 0.6 x TFC's,
     * or a centroid more than 3 px (XZ) or 4 px (Y) from TFC's. Null when accepted (an empty TFC outline accepts all).
     */
    public static String geometricRejection(Shape tfc, Shape cand) {
        if (tfc.isEmpty()) {
            return null;
        }
        if (cand.isEmpty()) {
            return "no outline";
        }
        List<Shape> tfcParts = tfc.components();
        List<Shape> parts = cand.components();
        if (parts.size() > tfcParts.size()) {
            boolean aligned = tfcParts.size() > 1;
            for (Shape part : parts) {
                if (tfcParts.stream().noneMatch(t -> Shape.iou(t, part) >= MIN_PART_IOU)) {
                    aligned = false;
                    break;
                }
            }
            if (!aligned) {
                return "components: " + parts.size() + " parts vs TFC " + tfcParts.size();
            }
        }
        double fillT = tfc.fill();
        double fillC = cand.fill();
        if (fillC < MIN_FILL_FACTOR * fillT) {
            return String.format(java.util.Locale.ROOT, "fill %.2f below 0.6 x TFC %.2f", fillC, fillT);
        }
        double[] ct = tfc.centroid();
        double[] cc = cand.centroid();
        double dxz = Math.hypot(cc[0] - ct[0], cc[2] - ct[2]);
        double dy = Math.abs(cc[1] - ct[1]);
        if (dxz > MAX_CENTROID_XZ || dy > MAX_CENTROID_Y) {
            return String.format(java.util.Locale.ROOT, "centroid off by %.1f px XZ, %.1f px Y", dxz, dy);
        }
        return null;
    }

    /** {@code min(1, fill_cand / fill_tfc) x (1 - centroid offset / 16)}; 1 when either outline is empty. */
    public static double quality(Shape tfc, Shape cand) {
        if (tfc.isEmpty() || cand.isEmpty()) {
            return 1;
        }
        double[] ct = tfc.centroid();
        double[] cc = cand.centroid();
        double d = Math.sqrt(Math.pow(cc[0] - ct[0], 2) + Math.pow(cc[1] - ct[1], 2) + Math.pow(cc[2] - ct[2], 2));
        return Math.min(1, cand.fill() / tfc.fill()) * Math.max(0, 1 - d / 16);
    }

    /** {@code (0.6 IoU_sel + 0.4 IoU_col) x quality} in the family's worst version, NaN when a version lacks the state. */
    static double rawScore(TfcState tfc, VersionFamily family, VanillaCandidates v, String state) {
        if (!v.inFamily(state, family)) {
            return Double.NaN;
        }
        double worst = Double.MAX_VALUE;
        for (String version : family.researchedVersions()) {
            VState s = v.get(version, state);
            Row row = tfc.row();
            double score = (W_SEL * Shape.iou(row.selection(), s.selection()) + W_COL * Shape.iou(row.collision(), s.collision()))
                * quality(row.selection(), s.selection());
            worst = Math.min(worst, score);
        }
        return worst;
    }

    /** The worst-version score of an accepted state, or null when a natural state has no free hide target. */
    static Scored score(TfcState tfc, VersionFamily family, VanillaCandidates v, String state, Predicate<String> free) {
        Row row = tfc.row();
        String hide = null;
        int consumed = 1;
        List<String> notes = new ArrayList<>();
        double penalty = 1;
        if (!v.isPool(state)) {
            BlockPolicy p = v.policy(state.substring(0, state.contains("[") ? state.indexOf('[') : state.length()));
            if (p != null) {
                consumed = p.statesConsumed();
                if (p.presence() == Presence.NATURAL) {
                    hide = hideTarget(v, family, state, p.hide(), free);
                    if (hide == null) {
                        return null;
                    }
                    penalty *= HIDE_PENALTY;
                    notes.add("hide " + hide);
                }
            }
        }
        Scored worst = null;
        for (String version : family.researchedVersions()) {
            VState s = v.get(version, state);
            double pen = penalty;
            List<String> n = new ArrayList<>(notes);
            if (v.replaceable(s)) {
                pen *= PENALTY;
                n.add("replaceable");
            }
            boolean tfcFull = row.selection().equals(Shape.FULL) && !row.seeThrough();
            boolean fullOccluder = s.flag("occludes") && s.selection().equals(Shape.FULL);
            if (fullOccluder && !tfcFull) {
                pen *= PENALTY;
                n.add("occludes neighbours");
            }
            double sel = Shape.iou(row.selection(), s.selection());
            double col = Shape.iou(row.collision(), s.collision());
            double q = quality(row.selection(), s.selection());
            double score = (W_SEL * sel + W_COL * col) * q * pen;
            Scored sc = new Scored(state, sel, col, q, pen, score, version, consumed, hide, fullOccluder, List.copyOf(n));
            if (worst == null || sc.score() < worst.score()) {
                worst = sc;
            }
        }
        return worst;
    }

    /**
     * {@code nearest_free_value:<property>}: the state of the same block with the nearest other value of the property
     * that is free and in the family (the higher one on a tie), e.g. snow[layers=7] to snow[layers=8].
     */
    static String hideTarget(VanillaCandidates v, VersionFamily family, String state, String rule, Predicate<String> free) {
        if (rule == null || !rule.startsWith("nearest_free_value:")) {
            throw new DatagenException("unknown hide rule " + rule + " for " + state);
        }
        String prop = rule.substring("nearest_free_value:".length());
        Map<String, String> props = props(state);
        String current = props.get(prop);
        if (current == null) {
            throw new DatagenException("hide rule " + rule + ": " + state + " has no " + prop);
        }
        int value = Integer.parseInt(current);
        String best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (int candidate = value + 64; candidate >= value - 64; candidate--) {
            if (candidate == value) {
                continue;
            }
            Map<String, String> p = new LinkedHashMap<>(props);
            p.put(prop, Integer.toString(candidate));
            String target = withProps(state, p);
            int d = Math.abs(candidate - value);
            if (d < bestDistance && v.inFamily(target, family) && free.test(target)) {
                best = target;
                bestDistance = d;
            }
        }
        return best;
    }

    static Map<String, String> props(String state) {
        Map<String, String> m = new LinkedHashMap<>();
        int i = state.indexOf('[');
        if (i >= 0) {
            for (String kv : state.substring(i + 1, state.length() - 1).split(",")) {
                String[] p = kv.split("=", 2);
                m.put(p[0], p[1]);
            }
        }
        return m;
    }

    static String withProps(String state, Map<String, String> props) {
        int i = state.indexOf('[');
        String block = i < 0 ? state : state.substring(0, i);
        List<String> parts = new ArrayList<>();
        props.forEach((k, val) -> parts.add(k + "=" + val));
        return parts.isEmpty() ? block : block + "[" + String.join(",", parts) + "]";
    }

    private static Comparator<Scored> order(Row row) {
        String tfcName = row.block().substring(row.block().lastIndexOf('/') + 1).replace("tfc:", "");
        Map<String, String> tfcProps = row.properties();
        boolean tfcOccludes = row.selection().equals(Shape.FULL) && !row.seeThrough();
        Comparator<Scored> byScore = Comparator.comparingDouble(s -> -round(s.score()));
        return byScore
            .thenComparing(s -> !s.block().equals("minecraft:" + tfcName))
            .thenComparing(s -> -shared(tfcProps, props(s.carrier())))
            .thenComparing(s -> s.fullOccluder() != tfcOccludes)
            .thenComparing(s -> !s.pool())
            .thenComparingInt(Scored::statesConsumed)
            .thenComparing(Scored::carrier);
    }

    /** Scores equal to 1e-9 count as ties (floating-point noise must not decide). */
    private static double round(double d) {
        return Math.round(d * 1e9) / 1e9;
    }

    private static int shared(Map<String, String> a, Map<String, String> b) {
        int n = 0;
        for (Map.Entry<String, String> e : a.entrySet()) {
            if (e.getValue().equals(b.get(e.getKey()))) {
                n++;
            }
        }
        return n;
    }
}
