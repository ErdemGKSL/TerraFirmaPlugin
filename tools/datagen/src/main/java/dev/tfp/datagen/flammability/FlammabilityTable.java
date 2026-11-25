/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/ExtendedProperties.java @ c3b4d9536 (flammable, flammableLike*),
 *       src/main/java/net/dries007/tfc/common/blocks/wood/Wood.java @ c3b4d9536 (BlockType rows),
 *       src/main/java/net/dries007/tfc/common/blocks/plant/Plant.java @ c3b4d9536 (BlockType.fire, plant types),
 *       src/main/java/net/dries007/tfc/common/blocks/plant/fruit/FruitBlocks.java @ c3b4d9536,
 *       src/main/java/net/dries007/tfc/common/blocks/crop/Crop.java @ c3b4d9536 (dead),
 *       src/main/java/net/dries007/tfc/common/blocks/GroundcoverBlock.java @ c3b4d9536 (twig),
 *       src/main/java/net/dries007/tfc/common/blocks/TFCBlocks.java @ c3b4d9536 (single registrations)
 */
package dev.tfp.datagen.flammability;

import dev.tfp.datagen.DatagenException;
import dev.tfp.datagen.TfcCheckout;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PAR-INFRA-47: TFC's block fire behaviour, read from the pinned sources (never typed by hand). A {@link Fire} is
 * {@code ExtendedProperties.flammable(flammability, fireSpreadSpeed)} plus {@code ignitedByLava}; the presets are
 * {@code flammableLikeLogs} (5, 5), {@code Planks} (20, 5), {@code Leaves} (60, 30), {@code Wool} (100, 60), all with
 * {@code ignitedByLava}. Every key is our block id ({@code tfp:wood/log/oak} for a wood family row: {@link #FAMILY}).
 *
 * <p>Derivation: {@link #wood} reads the {@code Wood.BlockType} rows (a row with no flammable call is not flammable;
 * {@code TWIG} takes {@code GroundcoverBlock.twig}'s {@code flammable(60, 30)}, applied after its preset),
 * {@link #plants} the {@code Plant.BlockType} rows that wrap their properties in {@code fire(...)} and each plant's type,
 * {@link #crops} {@code Crop.dead()}, {@link #fruit} the {@code FruitBlocks} registrations and {@link #registered} every
 * single-line {@code TFCBlocks} registration.
 */
public final class FlammabilityTable {
    /** TFC's fire values of one block. */
    public record Fire(int flammability, int fireSpreadSpeed, boolean ignitedByLava) {
    }

    static final String BLOCKS = "src/main/java/net/dries007/tfc/common/blocks/";
    static final Map<String, Fire> PRESETS = Map.of(
        "Logs", new Fire(5, 5, true), "Planks", new Fire(20, 5, true),
        "Leaves", new Fire(60, 30, true), "Wool", new Fire(100, 60, true));
    private static final Pattern CALL = Pattern.compile(
        "flammableLike(Logs|Planks|Leaves|Wool)\\(\\)|flammable\\((\\d+),\\s*(\\d+)\\)|ignitedByLava\\(\\)");

    /** The wood family path marker: ids are {@code tfp:wood/<family>/<wood>}. */
    public static final String FAMILY = "tfp:wood/";

    private FlammabilityTable() {
    }

    /** The fire of the first flammable call in {@code text} (and a later {@code ignitedByLava()}), or null for none. */
    static Fire parse(String text) {
        Matcher m = CALL.matcher(text);
        Fire fire = null;
        while (m.find()) {
            if (m.group(1) != null) {
                fire = PRESETS.get(m.group(1));
            } else if (m.group(2) != null) {
                fire = new Fire(Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)), false);
            } else if (fire != null) {
                fire = new Fire(fire.flammability(), fire.fireSpreadSpeed(), true);
            }
        }
        return fire;
    }

    /** The text of the enum rows of {@code enumName} in {@code source}: row name to row text. */
    static Map<String, String> rows(String source, String enumName) {
        int start = source.indexOf("enum " + enumName);
        if (start < 0) {
            throw new DatagenException("no enum " + enumName);
        }
        Matcher m = Pattern.compile("(?m)^\\s{8}([A-Z][A-Z0-9_]*)\\(").matcher(source);
        List<int[]> at = new ArrayList<>();
        List<String> names = new ArrayList<>();
        while (m.find(start)) {
            at.add(new int[] {m.start()});
            names.add(m.group(1));
            start = m.end();
        }
        Map<String, String> out = new LinkedHashMap<>();
        for (int i = 0; i < names.size(); i++) {
            int end = i + 1 < names.size() ? at.get(i + 1)[0] : source.length();
            out.put(names.get(i), source.substring(at.get(i)[0], end));
        }
        return out;
    }

    /** {@code wood family (lower case) -> fire}, only the flammable rows. */
    public static Map<String, Fire> wood(TfcCheckout tfc) {
        String wood = tfc.readString(BLOCKS + "wood/Wood.java");
        Fire twig = parse(afterMarker(tfc.readString(BLOCKS + "GroundcoverBlock.java"), "static GroundcoverBlock twig"));
        if (twig == null) {
            throw new DatagenException("GroundcoverBlock.twig no longer sets flammable");
        }
        Map<String, Fire> out = new LinkedHashMap<>();
        for (Map.Entry<String, String> row : rows(wood, "BlockType").entrySet()) {
            Fire f = parse(row.getValue());
            if (f == null) {
                continue;
            }
            if (row.getKey().equals("TWIG")) {
                f = new Fire(twig.flammability(), twig.fireSpreadSpeed(), f.ignitedByLava());
            }
            out.put(row.getKey().toLowerCase(java.util.Locale.ROOT), f);
        }
        if (!out.containsKey("log") || !out.containsKey("planks") || !out.containsKey("leaves")) {
            throw new DatagenException("Wood.BlockType no longer has flammable log, planks and leaves rows");
        }
        return out;
    }

    private static String afterMarker(String text, String marker) {
        int i = text.indexOf(marker);
        if (i < 0) {
            throw new DatagenException("missing " + marker);
        }
        return text.substring(i, Math.min(text.length(), i + 300));
    }

    /** {@code tfp:plant/<name> -> fire} for every plant whose {@code Plant.BlockType} uses {@code fire(...)}. */
    public static Map<String, Fire> plants(TfcCheckout tfc) {
        String plant = tfc.readString(BLOCKS + "plant/Plant.java");
        Matcher fire = Pattern.compile("(?s)private static ExtendedProperties fire\\(.*?\\{(.*?)\\}").matcher(plant);
        if (!fire.find()) {
            throw new DatagenException("Plant.fire is gone");
        }
        Fire value = parse(fire.group(1));
        if (value == null) {
            throw new DatagenException("Plant.fire no longer sets flammable");
        }
        java.util.Set<String> burning = new java.util.HashSet<>();
        for (Map.Entry<String, String> row : rows(plant, "BlockType").entrySet()) {
            if (row.getValue().contains("fire(")) {
                burning.add(row.getKey());
            }
        }
        Map<String, Fire> out = new LinkedHashMap<>();
        Matcher p = Pattern.compile("(?m)^    ([A-Z][A-Z0-9_]*)\\(BlockType\\.([A-Z0-9_]+)").matcher(plant);
        while (p.find()) {
            if (burning.contains(p.group(2))) {
                out.put("tfp:plant/" + p.group(1).toLowerCase(java.util.Locale.ROOT), value);
            }
        }
        return out;
    }

    /** {@code Crop.dead()}: shared by the crops, dead crops and wild crops. */
    public static Fire crops(TfcCheckout tfc) {
        String crop = tfc.readString(BLOCKS + "crop/Crop.java");
        Matcher m = Pattern.compile("(?s)private static ExtendedProperties dead\\(\\)\\s*\\{(.*?)\\}").matcher(crop);
        Fire f = m.find() ? parse(m.group(1)) : null;
        if (f == null) {
            throw new DatagenException("Crop.dead() no longer sets flammable");
        }
        return f;
    }

    /** The fruit and berry block fire: the stationary/spreading bushes, banana and cranberry share one preset. */
    public static Fire fruit(TfcCheckout tfc) {
        Fire f = null;
        for (String line : tfc.readString(BLOCKS + "plant/fruit/FruitBlocks.java").split("\n")) {
            if (line.contains("new StationaryBerryBushBlock(") || line.contains("new SpreadingBushBlock(")
                    || line.contains("new SpreadingCaneBlock(")) {
                Fire here = parse(line);
                if (here == null || f != null && !f.equals(here)) {
                    throw new DatagenException("FruitBlocks bushes no longer share one flammable preset");
                }
                f = here;
            }
        }
        if (f == null) {
            throw new DatagenException("FruitBlocks has no bush registrations");
        }
        return f;
    }

    /** {@code tfp:<path> -> fire} for each single-line {@code TFCBlocks} registration that sets flammable. */
    public static Map<String, Fire> registered(TfcCheckout tfc) {
        Map<String, Fire> out = new LinkedHashMap<>();
        Pattern reg = Pattern.compile("register(?:NoItem)?\\(\"([a-z0-9_/]+)\"");
        for (String line : tfc.readString(BLOCKS + "TFCBlocks.java").split("\n")) {
            Matcher m = reg.matcher(line);
            if (m.find() && line.contains("public static final")) {
                Fire f = parse(line);
                if (f != null) {
                    out.put("tfp:" + m.group(1), f);
                }
            }
        }
        return out;
    }

    /** Everything above as one map from id family or exact id; see {@link Table#fireOf}. */
    public static Table all(TfcCheckout tfc) {
        return new Table(wood(tfc), plants(tfc), crops(tfc), fruit(tfc), registered(tfc));
    }

    /** The derived tables. */
    public record Table(Map<String, Fire> wood, Map<String, Fire> plants, Fire crops, Fire fruit, Map<String, Fire> registered) {
        /** The fire of block {@code id}, or null when TFC's counterpart is not flammable. */
        public Fire fireOf(String id) {
            if (id.startsWith(FAMILY)) {
                String[] p = id.substring(FAMILY.length()).split("/");
                return p.length == 2 ? wood.get(p[0]) : registered.get(id);
            }
            if (id.startsWith("tfp:crop/") || id.startsWith("tfp:dead_crop/") || id.startsWith("tfp:wild_crop/")) {
                return crops;
            }
            switch (id) {
                case "tfp:berry_bush/dead", "tfp:spreading_bush/dead" -> {
                    return registered.get("tfp:plant/dead_berry_bush");
                }
                case "tfp:spreading_bush/dead_cane" -> {
                    return registered.get("tfp:plant/dead_cane");
                }
                default -> {
                }
            }
            if (id.startsWith("tfp:berry_bush/") || id.startsWith("tfp:spreading_bush/")) {
                return fruit;
            }
            if (id.startsWith("tfp:plant/") && plants.containsKey(id)) {
                return plants.get(id);
            }
            return registered.get(id);
        }
    }
}
