/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/TFCBlocks.java @ c3b4d9536 (the registrations covered, read through
 * their blockstate files under src/main/resources/assets/tfc/blockstates)
 */
package dev.tfp.datagen.coverage;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.tfp.datagen.DatagenException;
import dev.tfp.datagen.Json;
import dev.tfp.datagen.OutputTree;
import dev.tfp.datagen.TfcCheckout;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PAR-INFRA-49 coverage gate for blocks: every TFC blockstate is a loaded CE block, a generated-but-unloaded family or
 * device-spec block, a block owned by a named parity item ({@link #OWNERS}) or a NOT-PARITY entry ({@link #NOT_PARITY}).
 * Writes {@code reports/tfc-block-coverage.json} and fails the run when a TFC block is none of these ("unowned"), so a new
 * TFC block or a dropped generator shows up here. The block twin of {@code material/ItemCoverage}.
 *
 * <p>Classification order: generated (a {@code blocks:} key of a {@code configuration/**.yml} file of the emitted tree),
 * family (a {@code tfc:} line of {@code families/*.yml} or a {@code blocks:} key of {@code devices/*.yml}: emitted, not
 * loaded by CraftEngine), owned (first matching {@link #OWNERS} rule), not_parity (first matching {@link #NOT_PARITY} rule),
 * else unowned. Generated always wins, so a rule never shadows a loaded block.
 *
 * <p>Runs after every generator that writes CE blocks. This gate proves ownership, not parity: a loaded CE block can still
 * be a placeholder (vanilla stand-in visual or missing behaviour); its owner's done criteria prove parity.
 */
public final class BlockCoverage {
    public static final String REPORT = "reports/tfc-block-coverage.json";
    static final String SOURCE = "src/main/java/net/dries007/tfc/common/blocks/TFCBlocks.java";
    static final String BLOCKSTATES = TfcCheckout.ASSETS + "blockstates/";
    /** The CraftEngine pack folder of the output tree (AssetPipeline.PACK). */
    static final String PACK = "craftengine/tfp/";
    static final String CONFIG = PACK + "configuration/";
    static final String FAMILIES = PACK + "families/";
    static final String DEVICES = PACK + "devices/";

    private static final Pattern BLOCK_KEY = Pattern.compile("^ {2}([a-z0-9_]+):([a-z0-9_/.\\-]+):\\s*$");
    private static final Pattern FAMILY_TFC = Pattern.compile("^\\s+tfc: " + Json.FROM_NS + ":([a-z0-9_/.\\-]+)\\s*$");

    /** The class of one TFC block, in classification order. */
    public enum Kind {
        GENERATED("generated"), FAMILY("family"), OWNED("owned"), NOT_PARITY("not_parity"), UNOWNED("unowned");

        final String key;

        Kind(String key) {
            this.key = key;
        }
    }

    /** One rule: TFC block paths matching {@code pattern} belong to {@code owner} (a parity item id or an NP id). */
    public record Owner(Pattern pattern, String owner, String what) {
        static Owner of(String regex, String owner, String what) {
            return new Owner(Pattern.compile(regex), owner, what);
        }
    }

    /**
     * The parity items that own the TFC blocks no CE file defines yet, matched in order (checked against
     * plans/parity/items by BlockCoverageTest). Built from the item files' titles, plans and tfc_refs on 2026-11-14.
     */
    public static final List<Owner> OWNERS = List.of(
        // farming
        Owner.of("crop/.+|dead_crop/.+", "PAR-FARM-03", "crop blocks and dead crops"),
        Owner.of("wild_crop/.+|melon|pumpkin|rotten_melon|rotten_pumpkin", "PAR-FARM-05", "wild crops, spreading-crop fruit blocks, rotten fruit"),
        Owner.of("farmland/.+", "PAR-FARM-01", "farmland per soil"),
        Owner.of("plant/(banana_plant|banana_sapling|dead_banana_plant)", "PAR-FARM-09", "banana plant and sapling"),
        Owner.of("plant/((snowberry|bunchberry|gooseberry|cloudberry|strawberry|wintergreen_berry)_bush|dead_berry_bush)",
            "PAR-FARM-06", "stationary berry bushes"),
        Owner.of("plant/((blackberry|raspberry|blueberry|elderberry)_bush(_cane)?|cranberry_bush|dead_cane)", "PAR-FARM-07",
            "spreading bushes, canes and cranberries"),
        Owner.of("plant/potted/.+|plant/.+_krummholz", "PAR-DECO-13", "potted plants and saplings, krummholz"),
        Owner.of("plant/.+", "PAR-WORLD-12", "plant blocks (the Plant enum shapes)"),
        Owner.of("coral/.+|sea_pickle", "PAR-WORLD-25", "corals, coral fans and sea pickle"),
        // soils and natural blocks
        Owner.of("(grass|clay_grass|duff|clay_duff|coarse_dirt|grass_path|mud|muddy_roots|rooted_dirt)/.+|peat|peat_grass|"
            + "kaolin_clay_grass|(pink|red|white)_kaolin_clay", "PAR-WORLD-04", "soil block set per soil variant, peat, kaolin"),
        Owner.of("deposit/.+|groundcover/.+|ore/small_.+", "PAR-WORLD-09", "ore deposits, groundcover, small ores"),
        Owner.of("(raw|smooth|cut)_sandstone/.+|hardened_clay|halite|calcite|bituminous_coal|lignite", "PAR-DECO-08",
            "sandstone families and misc natural blocks"),
        Owner.of("snow_pile|ice_pile|icicle|sea_ice", "PAR-CLIM-04", "snow piles, ice piles, icicles, sea ice"),
        // building blocks
        Owner.of("drying_bricks/.+", "PAR-POT-08", "drying mud bricks"),
        Owner.of("smooth_mud_bricks|reinforced_fire_bricks|fire_brick_shelf|fireproof_door|fire_clay_block", "PAR-DECO-16",
            "fire brick extras and smooth mud bricks"),
        Owner.of("fire_bricks|aggregate|alabaster/.+|wattle(/.+)?|thatch", "PAR-DECO-07",
            "fire bricks, aggregate, alabaster, wattle, thatch"),
        Owner.of("thatch_bed", "PAR-DECO-14", "thatch bed"),
        Owner.of("wood/planks/palm_mosaic(_stairs|_slab)?|golden_bamboo_block|cake|buoy/.+|tree_roots|light|wood/crate/.+",
            "PAR-DECO-12", "misc blocks: palm mosaic, golden bamboo block, cake, buoys, tree roots, light, crates"),
        Owner.of("wood/workbench/.+", "PAR-DECO-17", "wood workbenches"),
        Owner.of("wood/planks/.+", "PAR-DECO-01", "wood planks (the stairs and slabs are in families/wood.yml)"),
        Owner.of("wood/(log|stripped_log|wood|stripped_wood|leaves|sapling|potted_sapling|fallen_leaves|twig)/.+",
            "PAR-WORLD-10", "wood blocks: logs, woods, leaves, saplings, fallen leaves, twigs"),
        Owner.of("wood/(horizontal_support|vertical_support)/.+", "PAR-PHYS-04", "support beams"),
        Owner.of("rope|hanging_rope|steel_rope_anchor", "PAR-DECO-10", "ropes and rope anchors"),
        // mechanical power
        Owner.of("wood/(axle|encased_axle|gear_box|clutch)/.+|creative_motor", "PAR-MECH-01", "rotation network blocks"),
        Owner.of("wood/bladed_axle/.+", "PAR-MECH-10", "bladed axles"),
        Owner.of("wood/water_wheel/.+", "PAR-MECH-03", "water wheels"),
        Owner.of("wood/windmill/.+", "PAR-MECH-04", "windmills"),
        Owner.of("crankshaft", "PAR-MECH-05", "crankshaft"),
        Owner.of("trip_hammer", "PAR-MECH-07", "trip hammer"),
        Owner.of("power_loom", "PAR-MECH-08", "power loom"),
        Owner.of("steel_pipe|steel_pump", "PAR-MECH-09", "pump and pipes"),
        // devices
        Owner.of("barrel_rack", "PAR-PROC-38", "barrel rack"),
        Owner.of("log_pile|burning_log_pile|charcoal_pile", "PAR-PROC-37", "log pile, burning log pile, charcoal pile"),
        Owner.of("powderkeg", "PAR-PROC-18", "powderkeg"),
        Owner.of("([a-z_]+_)?poured_glass|glass_basin", "PAR-PROC-17", "glass basin, hot and coloured poured glass"),
        Owner.of("placed_item", "PAR-POT-01", "placed items"),
        Owner.of("pit_kiln", "PAR-POT-03", "pit kiln"),
        Owner.of("ceramic/large_vessel(/.+)?", "PAR-POT-06", "large vessels and glazed large vessels"),
        Owner.of("ceramic/bowl|wooden_bowl", "PAR-POT-09", "bowl blocks"),
        Owner.of("metal/anvil/.+", "PAR-METAL-03", "metal anvils"),
        Owner.of("crucible", "PAR-METAL-12", "crucible"),
        Owner.of("channel|mold_table", "PAR-METAL-13", "channels and mold tables"),
        Owner.of("bloomery|bloom|molten", "PAR-METAL-14", "bloomery, bloom and the molten stack block"),
        Owner.of("blast_furnace", "PAR-METAL-15", "blast furnace"),
        Owner.of("ingot_pile|double_ingot_pile", "PAR-METAL-16", "ingot and double-ingot piles"),
        Owner.of("bronze_bell|brass_bell", "PAR-METAL-18", "bronze and brass bells"),
        Owner.of("candle(/.+)?|candle_cake(/.+)?|jack_o_lantern", "PAR-HEAT-11", "candles, candle cakes, jack-o'-lanterns"),
        Owner.of("firebox|stove|stove_pot", "PAR-HEAT-12", "firebox, stove and stove pot"),
        Owner.of("torch|wall_torch|dead_torch|dead_wall_torch", "PAR-HEAT-21", "torches and dead torches"),
        Owner.of("thermometer|anemometer|vane|calendar_clock", "PAR-CLIM-09", "climate instruments"),
        Owner.of("nest_box", "PAR-FAUNA-07", "nest box"),
        Owner.of("lobster_trap", "PAR-FAUNA-19", "lobster trap"));

    /** TFC blocks that are not parity targets: the NOT-PARITY.md entry that replaces them (checked by the test). */
    public static final List<Owner> NOT_PARITY = List.of(
        Owner.of("fluid/.+|(freshwater|saltwater|spring_water)_bubble_column", "NP-FLUID-01",
            "TFC world fluids and their bubble columns (vanilla water and lava in the world)"));

    /** The coverage of one TFC block: its class and the file (generated, family) or owner/NP id (owned, not_parity). */
    public record Coverage(String id, Kind kind, String where) {
        public boolean unowned() {
            return kind == Kind.UNOWNED;
        }

        String value() {
            return switch (kind) {
                case GENERATED -> "ce:" + where;
                case FAMILY -> "family:" + where;
                case OWNED -> where;
                case NOT_PARITY -> "np:" + where;
                case UNOWNED -> "unowned";
            };
        }
    }

    private BlockCoverage() {
    }

    /**
     * True on a real checkout. The fixture checkouts of the tests carry a stub TFCBlocks and a few blockstates, but no
     * TFCItems registry, so this gate runs exactly where the item coverage gate runs.
     */
    public static boolean available(TfcCheckout tfc) {
        return tfc.exists(SOURCE) && tfc.isDirectory(BLOCKSTATES) && dev.tfp.datagen.material.MaterialItems.available(tfc);
    }

    /** Every TFC block id (path without namespace): one per blockstate file of the checkout, sorted. */
    public static List<String> tfcIds(TfcCheckout tfc) {
        List<String> ids = new ArrayList<>();
        for (String f : tfc.list(BLOCKSTATES)) {
            if (f.endsWith(".json")) {
                ids.add(f.substring(BLOCKSTATES.length(), f.length() - ".json".length()));
            }
        }
        return ids;
    }

    /** The coverage of each id against the CE files of {@code out}. */
    public static List<Coverage> coverage(List<String> ids, OutputTree out) {
        Map<String, String> loaded = blockKeys(out, CONFIG);
        Map<String, String> family = familyIds(out);
        List<Coverage> rows = new ArrayList<>();
        for (String id : ids) {
            String file = loaded.get(Json.TO_NS + ":" + id);
            if (file != null) {
                rows.add(new Coverage(id, Kind.GENERATED, file));
            } else if (family.containsKey(id)) {
                rows.add(new Coverage(id, Kind.FAMILY, family.get(id)));
            } else {
                Optional<Owner> owner = match(OWNERS, id);
                Optional<Owner> np = owner.isPresent() ? Optional.empty() : match(NOT_PARITY, id);
                if (owner.isPresent()) {
                    rows.add(new Coverage(id, Kind.OWNED, owner.get().owner()));
                } else if (np.isPresent()) {
                    rows.add(new Coverage(id, Kind.NOT_PARITY, np.get().owner()));
                } else {
                    rows.add(new Coverage(id, Kind.UNOWNED, ""));
                }
            }
        }
        return rows;
    }

    static Optional<Owner> match(List<Owner> rules, String id) {
        return rules.stream().filter(o -> o.pattern().matcher(id).matches()).findFirst();
    }

    /** CE block id ({@code tfp:quern}) to its file (relative to the pack), for every {@code blocks:} key below {@code dir}. */
    static Map<String, String> blockKeys(OutputTree out, String dir) {
        Map<String, String> ids = new TreeMap<>();
        for (Map.Entry<String, byte[]> f : out.files().entrySet()) {
            String path = f.getKey();
            if (!path.startsWith(dir) || !path.endsWith(".yml")) {
                continue;
            }
            boolean inBlocks = false;
            for (String line : new String(f.getValue(), StandardCharsets.UTF_8).split("\n")) {
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                if (!line.startsWith(" ")) {
                    inBlocks = line.equals("blocks:");
                    continue;
                }
                Matcher m = BLOCK_KEY.matcher(line);
                if (inBlocks && m.matches()) {
                    ids.putIfAbsent(m.group(1) + ":" + m.group(2), path.substring(PACK.length()));
                }
            }
        }
        return ids;
    }

    /** TFC block path to the unloaded file that already carries it: families/*.yml {@code tfc:} lines, devices/*.yml keys. */
    static Map<String, String> familyIds(OutputTree out) {
        Map<String, String> ids = new TreeMap<>();
        for (Map.Entry<String, byte[]> f : out.files().entrySet()) {
            String path = f.getKey();
            if (!path.startsWith(FAMILIES) || !path.endsWith(".yml")) {
                continue;
            }
            for (String line : new String(f.getValue(), StandardCharsets.UTF_8).split("\n")) {
                Matcher m = FAMILY_TFC.matcher(line);
                if (m.matches()) {
                    ids.putIfAbsent(m.group(1), path.substring(PACK.length()));
                }
            }
        }
        blockKeys(out, DEVICES).forEach((id, file) -> {
            if (id.startsWith(Json.TO_NS + ":")) {
                ids.putIfAbsent(id.substring(Json.TO_NS.length() + 1), file);
            }
        });
        return ids;
    }

    /** Classifies the checkout's blocks, writes the report and fails on any unowned TFC block. Returns the rows. */
    public static List<Coverage> run(TfcCheckout tfc, OutputTree out) {
        return run(tfcIds(tfc), out);
    }

    /** As {@link #run(TfcCheckout, OutputTree)} over explicit ids (tests add synthetic ones). */
    public static List<Coverage> run(List<String> ids, OutputTree out) {
        List<Coverage> rows = coverage(ids, out);
        JsonObject root = new JsonObject();
        root.addProperty("comment", "Generated by tools/datagen (PAR-INFRA-49). Every TFC blockstate: ce:<file> (a loaded "
            + "CraftEngine block), family:<file> (emitted in families/ or devices/, not loaded yet), the parity item that "
            + "owns it, or np:<id> (a NOT-PARITY.md entry). This proves ownership, not parity: a loaded CE block can still be "
            + "a placeholder (vanilla stand-in visual or missing behaviour); the owners' done criteria prove parity. Do not edit.");
        Map<Kind, Integer> byKind = new LinkedHashMap<>();
        for (Kind k : Kind.values()) {
            byKind.put(k, 0);
        }
        Map<String, Integer> byFile = new TreeMap<>();
        Map<String, Integer> byFamily = new TreeMap<>();
        Map<String, Integer> byOwner = new TreeMap<>();
        Map<String, Integer> byNp = new TreeMap<>();
        JsonArray unowned = new JsonArray();
        JsonObject blocks = new JsonObject();
        for (Coverage c : rows) {
            byKind.merge(c.kind(), 1, Integer::sum);
            switch (c.kind()) {
                case GENERATED -> byFile.merge(c.where(), 1, Integer::sum);
                case FAMILY -> byFamily.merge(c.where(), 1, Integer::sum);
                case OWNED -> byOwner.merge(c.where(), 1, Integer::sum);
                case NOT_PARITY -> byNp.merge(c.where(), 1, Integer::sum);
                case UNOWNED -> unowned.add(Json.FROM_NS + ":" + c.id());
            }
            blocks.addProperty(Json.FROM_NS + ":" + c.id(), c.value());
        }
        root.addProperty("total", rows.size());
        JsonObject counts = new JsonObject();
        byKind.forEach((k, v) -> counts.addProperty(k.key, v));
        root.add("counts", counts);
        root.add("unowned", unowned);
        root.add("generated_by_file", toJson(byFile));
        root.add("family_by_file", toJson(byFamily));
        root.add("owned_by_item", toJson(byOwner));
        root.add("not_parity_by_entry", toJson(byNp));
        root.add("blocks", blocks);
        out.put(REPORT, Json.write(root));
        if (!unowned.isEmpty()) {
            throw new DatagenException("TFC blocks with no CE block, no family entry, no owner and no NOT-PARITY entry ("
                + unowned.size() + "): " + unowned + " (generate them or add an owner rule in BlockCoverage.OWNERS)");
        }
        return rows;
    }

    private static JsonObject toJson(Map<String, Integer> counts) {
        JsonObject o = new JsonObject();
        counts.forEach(o::addProperty);
        return o;
    }

    /** Ids per owner (owned rows only), for tests. */
    static Map<String, List<String>> byOwner(List<Coverage> rows) {
        Map<String, List<String>> out = new LinkedHashMap<>();
        for (Coverage c : rows) {
            if (c.kind() == Kind.OWNED) {
                out.computeIfAbsent(c.where(), k -> new ArrayList<>()).add(c.id());
            }
        }
        return out;
    }
}
