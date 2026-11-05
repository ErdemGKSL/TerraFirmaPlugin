/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/resources/data/minecraft/loot_table/blocks/** @ c3b4d9536 (TFC's overrides of vanilla block tables, copied)
 *       src/generated/resources/data/minecraft/loot_table/entities/** @ c3b4d9536 (zombie and drowned, copied)
 */
package dev.tfp.datagen;

import com.google.gson.JsonElement;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PAR-INFRA-30: TFC's replacements of vanilla loot tables ({@code data/minecraft/loot_table/**}: glass, the stained
 * glasses and panes, snow, snow block, zombie, drowned) ship in a datapack under their {@code minecraft:} ids, so the
 * server's own loot (block breaks, explosions, mob deaths) uses TFC's tables instead of vanilla's.
 *
 * <p>The tables go into the bootstrap datapack {@code tfp_seasonal_biomes} ({@link SeasonalBiomes#OUT}), the one
 * generated datapack {@code TfpBootstrap} registers; a pack of their own would need a new registration there.
 *
 * <p>{@link #SKIPPED}: {@code chest} and {@code trapped_chest} drop TFC's oak chests ({@code tfc:wood/chest/oak},
 * {@code tfc:wood/trapped_chest/oak}), which are not vanilla items; a vanilla table naming them would fail to load, so
 * they are not shipped and vanilla chests keep the vanilla drop (ledger divergence). Every shipped table must name only
 * {@code minecraft:} ids after the usual rename.
 */
public final class VanillaLootOverrides {
    static final String BLOCK_SOURCE = TfcCheckout.MAIN + "data/minecraft/loot_table/blocks/";
    static final String ENTITY_SOURCE = TfcCheckout.GENERATED + "data/minecraft/loot_table/entities/";
    static final String TARGET = SeasonalBiomes.OUT + "data/minecraft/loot_table/";
    /** TFC's block tables that name a TFC item (its oak chests), left out (vanilla drop kept). */
    static final Set<String> SKIPPED = Set.of("blocks/chest", "blocks/trapped_chest");
    /** At the pinned commit: 39 block tables (2 of them skipped) and 2 entity tables. */
    static final int PINNED_BLOCKS = 39;
    static final int PINNED_ENTITIES = 2;
    private static final Pattern MOD_ID = Pattern.compile("(?<![A-Za-z0-9_.\\-/])(" + Json.FROM_NS + "|" + Json.TO_NS
        + "):[a-z0-9_./\\-]+");

    private VanillaLootOverrides() {
    }

    /** Counts of TFC's overrides found and of the shipped tables. */
    public record Counts(int blocks, int entities, int shipped) {
    }

    /**
     * Copies TFC's overrides (renamed) into the datapack. Fails when a full checkout's counts differ from
     * {@value #PINNED_BLOCKS} / {@value #PINNED_ENTITIES} or when a shipped table names a non-vanilla id.
     */
    public static Counts run(TfcCheckout tfc, OutputTree out) {
        int blocks = 0;
        int entities = 0;
        int shipped = 0;
        for (String dir : List.of(BLOCK_SOURCE, ENTITY_SOURCE)) {
            String kind = dir.equals(BLOCK_SOURCE) ? "blocks/" : "entities/";
            for (String src : tfc.list(dir)) {
                if (!src.endsWith(".json")) {
                    continue;
                }
                String rel = kind + src.substring(dir.length(), src.length() - ".json".length());
                if (kind.equals("blocks/")) {
                    blocks++;
                } else {
                    entities++;
                }
                if (SKIPPED.contains(rel)) {
                    continue;
                }
                JsonElement table = Json.rename(Json.parse(tfc.readString(src), src));
                check(rel, table);
                out.put(TARGET + rel + ".json", Json.write(table));
                shipped++;
            }
        }
        // a full checkout (the small test fixtures have no forge source, as for ForgeDevices) must give the pinned counts
        if (tfc.exists(ForgeDevices.FORGE_SOURCE) && (blocks != PINNED_BLOCKS || entities != PINNED_ENTITIES)) {
            throw new DatagenException("TFC's vanilla loot overrides: " + blocks + " block and " + entities
                + " entity tables, expected " + PINNED_BLOCKS + " and " + PINNED_ENTITIES + " at the pinned commit");
        }
        return new Counts(blocks, entities, shipped);
    }

    /** A shipped table: a block or entity table that names no {@code tfc:} or {@code tfp:} id (a vanilla table cannot). */
    static void check(String rel, JsonElement table) {
        String text = Json.write(table);
        Matcher m = MOD_ID.matcher(text);
        List<String> ids = new ArrayList<>();
        while (m.find()) {
            ids.add(m.group());
        }
        if (!ids.isEmpty()) {
            throw new DatagenException("vanilla loot override minecraft:" + rel + " names non-vanilla ids " + ids
                + " (a vanilla table cannot drop them)");
        }
        String type = table.getAsJsonObject().has("type") ? table.getAsJsonObject().get("type").getAsString() : "";
        String expected = rel.startsWith("blocks/") ? "minecraft:block" : "minecraft:entity";
        if (!type.equals(expected)) {
            throw new DatagenException("vanilla loot override minecraft:" + rel + " has type " + type + ", expected " + expected);
        }
    }
}
