/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.showcase;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * {@code /tfp debug showcase <family>} (M1-SVC-05, {@code plugin-testing} visual checklist): which block ids
 * belong to a family, and where each goes in the grid. Pure: the Paper side reads the ids from the shipped
 * {@code id_map.json}, keeps those CraftEngine actually loaded and places them on the region threads.
 *
 * <p>Layout: one <b>row per variant group</b> (the id without its last path segment, e.g.
 * {@code tfp:ore/rich_hematite}), one <b>column per member</b> (e.g. the rock {@code granite}), so ores read
 * as ore × rock and rocks as block type × rock. Rows and columns are sorted, so the grid is stable.
 */
public final class Showcase {
    /** The showcase families of M1 (the datagen v1 block families, M1-DGN-03). */
    public enum Family {
        /** {@code rock/<type>/<rock>}: raw, hardened, cobble, bricks, gravel, smooth, ... */
        ROCKS,
        /** Dirt, grass, clay, mud, farmland, ... per soil, and sand per colour. */
        SOILS,
        /** {@code ore/<grade_ore>/<rock>}: every ore and grade in every rock. */
        ORES;

        /** The command argument ({@code rocks}). */
        public String argument() {
            return name().toLowerCase(Locale.ROOT);
        }

        /** By command argument, case-insensitive. */
        public static Optional<Family> byArgument(String argument) {
            for (Family f : values()) {
                if (f.argument().equalsIgnoreCase(argument == null ? "" : argument.trim())) {
                    return Optional.of(f);
                }
            }
            return Optional.empty();
        }

        public static List<String> arguments() {
            List<String> out = new ArrayList<>();
            for (Family f : values()) {
                out.add(f.argument());
            }
            return out;
        }
    }

    /** First path segments of the soil family (TFC {@code SoilBlockType} + sand). */
    static final Set<String> SOIL_ROOTS = Set.of("dirt", "coarse_dirt", "rooted_dirt", "grass", "grass_path", "farmland",
            "clay", "clay_grass", "mud", "muddy_roots", "duff", "clay_duff", "sand");

    /** One grid position, in blocks from the origin: {@code dx} east (columns), {@code dz} south (rows). */
    public record Cell(int dx, int dz) {
    }

    private Showcase() {
    }

    /** The family of a block id, or empty. Only ids with a path of at least two segments belong to one. */
    public static Optional<Family> familyOf(String id) {
        int colon = id.indexOf(':');
        String path = colon < 0 ? id : id.substring(colon + 1);
        int slash = path.indexOf('/');
        if (slash <= 0 || slash == path.length() - 1) {
            return Optional.empty();
        }
        String root = path.substring(0, slash);
        if (root.equals("rock")) {
            return Optional.of(Family.ROCKS);
        }
        if (root.equals("ore")) {
            return Optional.of(Family.ORES);
        }
        return SOIL_ROOTS.contains(root) ? Optional.of(Family.SOILS) : Optional.empty();
    }

    /** The ids of {@code family} among {@code ids}, sorted and without duplicates. */
    public static List<String> select(Collection<String> ids, Family family) {
        TreeSet<String> out = new TreeSet<>();
        for (String id : ids) {
            if (familyOf(id).orElse(null) == family) {
                out.add(id);
            }
        }
        return List.copyOf(out);
    }

    /**
     * Our block ids from datagen's {@code id_map.json} (the values of {@code "blocks"}). This is every
     * exported TFC block, not only the ones with a CraftEngine definition yet: callers filter by what is loaded.
     */
    public static List<String> blockIds(JsonElement idMap) {
        if (idMap == null || !idMap.isJsonObject()) {
            throw new IllegalArgumentException("id_map.json: expected an object");
        }
        JsonObject blocks = idMap.getAsJsonObject().getAsJsonObject("blocks");
        if (blocks == null) {
            throw new IllegalArgumentException("id_map.json: no \"blocks\" section");
        }
        TreeSet<String> out = new TreeSet<>();
        for (Map.Entry<String, JsonElement> e : blocks.entrySet()) {
            out.add(e.getValue().getAsString());
        }
        return List.copyOf(out);
    }

    /** The variant group of an id: everything before the last {@code /} ({@code tfp:rock/raw/granite} → {@code tfp:rock/raw}). */
    static String group(String id) {
        int slash = id.lastIndexOf('/');
        return slash < 0 ? id : id.substring(0, slash);
    }

    /**
     * The grid: id → cell, one row per variant group, one column per member, {@code spacing} blocks apart
     * (≥ 1; 2 leaves a walkable gap). Iteration order is row by row.
     */
    public static Map<String, Cell> layout(Collection<String> ids, int spacing) {
        if (spacing < 1) {
            throw new IllegalArgumentException("spacing must be >= 1, got " + spacing);
        }
        TreeMap<String, TreeSet<String>> rows = new TreeMap<>();
        for (String id : ids) {
            rows.computeIfAbsent(group(id), g -> new TreeSet<>()).add(id);
        }
        Map<String, Cell> out = new LinkedHashMap<>();
        int row = 0;
        for (TreeSet<String> members : rows.values()) {
            int col = 0;
            for (String id : members) {
                out.put(id, new Cell(col * spacing, row * spacing));
                col++;
            }
            row++;
        }
        return out;
    }

    /** Width and depth (blocks) of a layout: the bounding box of its cells. */
    public static Cell extent(Map<String, Cell> layout) {
        int w = 0;
        int d = 0;
        for (Cell c : layout.values()) {
            w = Math.max(w, c.dx() + 1);
            d = Math.max(d, c.dz() + 1);
        }
        return new Cell(w, d);
    }
}
