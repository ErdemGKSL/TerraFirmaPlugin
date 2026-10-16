/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen.visuals;

import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Visual budget profiles (ARCHITECTURE.md §9.1). Each level adds demand reductions to the previous one; a
 * reduction makes an appearance reuse the look (vanilla state + model) of another appearance. All levels are
 * implemented; the owner picks one per MC version family ({@code visuals.profile}).
 *
 * <p>Rules that touch families datagen does not emit yet (wood, deposits, crates, workbenches) are coded where a
 * plain look substitution expresses them and simply match nothing today; rules that need a renderer or a new
 * texture are listed in {@link #pendingRules()} and reported, not silently dropped.
 */
public enum Profile {
    LEVEL_0("level-0"),
    LEVEL_1("level-1"),
    LEVEL_2("level-2"),
    LEVEL_3A("level-3a"),
    LEVEL_3B("level-3b");

    /** TFC gem ores that from level-2 look like their host rock until exposed (overlay display entity). */
    public static final Set<String> GEM_ORES = Set.of(
        "amethyst", "diamond", "emerald", "lapis_lazuli", "opal", "pyrite", "ruby", "sapphire", "topaz");
    public static final int SOLID_RESERVE_WARNING = 500;

    private static final Pattern ORE = Pattern.compile("ore/(poor|normal|rich)_([a-z_]+)/([a-z_]+)");
    private static final Pattern GEM = Pattern.compile("ore/([a-z_]+)/([a-z_]+)");
    private static final Pattern WOOD_BLOCK = Pattern.compile("wood/(wood|stripped_wood)/[a-z_]+");

    private final String id;

    Profile(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    /** Defaults decided in ARCHITECTURE.md §9.1: level-3a on 1.21.x, level-2 on 26.x. */
    public static Profile defaultFor(VersionFamily family) {
        return family == VersionFamily.V1_21 ? LEVEL_3A : LEVEL_2;
    }

    public static Profile byId(String id) {
        for (Profile p : values()) {
            if (p.id.equals(id)) {
                return p;
            }
        }
        throw new IllegalArgumentException("unknown visual profile " + id);
    }

    private boolean atLeast(Profile p) {
        return ordinal() >= p.ordinal();
    }

    /**
     * The appearance whose look {@code d} uses under this profile: {@code d} itself, or another existing
     * appearance. One hop; the target is never itself substituted.
     */
    public VisualDemand lookOf(VisualDemand d, Map<String, VisualDemand> byKey) {
        String target = null;
        if (atLeast(LEVEL_1) && WOOD_BLOCK.matcher(d.blockId()).matches() && d.appearance().startsWith("axis_")) {
            target = d.blockId() + "#axis_y"; // bark on every face: one visual for all axes
        }
        Matcher ore = ORE.matcher(d.blockId());
        if (ore.matches() && d.appearance().isEmpty()) {
            boolean share = switch (ore.group(1)) {
                case "poor" -> atLeast(LEVEL_3A);
                case "rich" -> atLeast(LEVEL_3B);
                default -> false;
            };
            if (share) {
                target = "ore/normal_" + ore.group(2) + "/" + ore.group(3);
            }
        }
        Matcher gem = GEM.matcher(d.blockId());
        if (atLeast(LEVEL_2) && gem.matches() && GEM_ORES.contains(gem.group(1)) && d.appearance().isEmpty()) {
            target = "rock/raw/" + gem.group(2) + "#axis_y"; // host rock; the exposed overlay is a renderer
        }
        if (target == null) {
            return d;
        }
        VisualDemand t = byKey.get(target);
        return t == null ? d : t;
    }

    /** Parts of this profile that need content datagen does not emit yet (shown in the allocation report). */
    public java.util.List<String> pendingRules() {
        java.util.List<String> out = new java.util.ArrayList<>();
        if (atLeast(LEVEL_1)) {
            out.add("level-1: crate/workbench drawn with entity_renderer (families not emitted yet; take no budget)");
            out.add("level-1: wood/stripped_wood axes share one visual (rule coded; wood family not emitted yet)");
        }
        if (atLeast(LEVEL_2)) {
            out.add("level-2: deposits rock-agnostic (deposit family and its gravel+specks texture not emitted yet)");
            out.add("level-2: gem ores use the host raw rock visual (applied); the exposed-only overlay renderer "
                + "is not implemented yet, so gem ores currently look exactly like raw rock");
        }
        return out;
    }
}
