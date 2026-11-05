/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen.visuals;

import dev.tfp.datagen.DatagenException;
import dev.tfp.datagen.oreoverlay.OreLook;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Visual budget profiles (ARCHITECTURE.md §9.1). Each level adds demand reductions to the previous one; a
 * reduction makes an appearance reuse the look (vanilla state + model) of another appearance. The owner picks one
 * per MC version family ({@code visuals.profile}). {@code level-2} is the highest: {@code level-3a}/{@code level-3b}
 * only merged ore grades and were removed when ores became host-rock visuals with face overlays (PAR-INFRA-15).
 *
 * <p>Always-on rule (every profile): with {@link OreLook#HOST_ROCK} every ore appearance ({@code ore/<ore>/<rock>})
 * uses its host rock's raw unit ({@code rock/raw/<rock>#axis_y}, including TFC's random mirrored/rotated variants;
 * shale and claystone keep their {@code cube_column} look), so ores cost no visual state; the ore itself is drawn by
 * the face overlays (PAR-INFRA-16). {@link OreLook#FULL_CUBE} keeps the flattened ore cube of each ore (the shipped
 * look until the overlay runtime exists).
 *
 * <p>Rules that touch families datagen does not emit yet (wood, deposits, crates, workbenches) are coded where a
 * plain look substitution expresses them and simply match nothing today; rules that need a renderer or a new
 * texture are listed in {@link #pendingRules()} and reported, not silently dropped.
 */
public enum Profile {
    LEVEL_0("level-0"),
    LEVEL_1("level-1"),
    LEVEL_2("level-2");

    public static final int SOLID_RESERVE_WARNING = 500;

    /** Every TFC ore block: {@code ore/<ore>/<rock>} and {@code ore/<grade>_<ore>/<rock>} (small ores have no rock). */
    public static final Pattern ORE = Pattern.compile("ore/([a-z_]+)/([a-z_]+)");
    private static final Pattern WOOD_BLOCK = Pattern.compile("wood/(wood|stripped_wood)/[a-z_]+");

    private final String id;

    Profile(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    /** Defaults decided in ARCHITECTURE.md §9.1 (PAR-INFRA-15): level-2 on 1.21.x and 26.x. */
    public static Profile defaultFor(VersionFamily family) {
        return LEVEL_2;
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

    /** The host rock appearance of an ore appearance key ({@code rock/raw/<rock>#axis_y}), or null if {@code d} is no ore. */
    public static String hostRock(VisualDemand d) {
        Matcher ore = ORE.matcher(d.blockId());
        return ore.matches() && d.appearance().isEmpty() ? "rock/raw/" + ore.group(2) + "#axis_y" : null;
    }

    /**
     * The appearance whose look {@code d} uses under this profile and ore look: {@code d} itself, or another existing
     * appearance. One hop; the target is never itself substituted.
     *
     * @throws DatagenException if {@code oreLook} is {@link OreLook#HOST_ROCK} and an ore's host rock is not emitted
     */
    public VisualDemand lookOf(VisualDemand d, Map<String, VisualDemand> byKey, OreLook oreLook) {
        String target = null;
        if (atLeast(LEVEL_1) && WOOD_BLOCK.matcher(d.blockId()).matches() && d.appearance().startsWith("axis_")) {
            target = d.blockId() + "#axis_y"; // bark on every face: one visual for all axes
        }
        String host = hostRock(d);
        if (host != null && oreLook == OreLook.HOST_ROCK) {
            VisualDemand rock = byKey.get(host);
            if (rock == null) {
                throw new DatagenException("ore " + d.key() + " has no emitted host rock appearance " + host);
            }
            return rock;
        }
        if (target == null) {
            return d;
        }
        VisualDemand t = byKey.get(target);
        return t == null ? d : t;
    }

    /** Parts of this profile that need content datagen does not emit yet (shown in the allocation report). */
    public List<String> pendingRules() {
        List<String> out = new ArrayList<>();
        if (atLeast(LEVEL_1)) {
            out.add("level-1: crate/workbench drawn with entity_renderer (families not emitted yet; take no budget)");
            out.add("level-1: wood/stripped_wood axes share one visual (rule coded; wood family not emitted yet)");
        }
        if (atLeast(LEVEL_2)) {
            out.add("level-2: deposits rock-agnostic (deposit family and its gravel+specks texture not emitted yet)");
        }
        return out;
    }
}
