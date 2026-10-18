/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/util/Metal.java, common/TFCTiers.java, common/TFCArmorMaterials.java and
 * config/ServerConfig.java @ c3b4d9536 (golden values only)
 */
package dev.tfp.core.tool;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.metal.Metal;
import dev.tfp.core.metal.MetalTier;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Golden tests for the metal tool, armor and forging rules. The three TSV files in this package's test resources are exported from
 * TFC at c3b4d9536 by {@code export_tool_golden.py} (kept outside the repository): the tier inputs and the expected derived numbers
 * are computed from the TFC sources, not from this code. Each test loads one file, checks its row count and compares every row's
 * derived numbers with what the Java rules produce.
 */
class MetalToolGoldenTest {
    /** The metals with a tool tier: nine tiers in the metal table. */
    private static final int TOOL_TIER_COUNT = 9;
    /** Tool kinds, the tool entries of TFC's {@code Metal.ItemType}. */
    private static final int TOOL_KIND_COUNT = 17;
    private static final int TOOL_ROW_COUNT = TOOL_TIER_COUNT * TOOL_KIND_COUNT;
    private static final int FORGING_ROW_COUNT = 5;
    private static final float TOLERANCE = 1e-4f;

    @Test
    void toolRowCountIsTiersTimesKinds() {
        List<Map<String, String>> rows = read("tools.tsv");
        assertThat(rows).hasSize(TOOL_ROW_COUNT);
        assertThat(ToolKind.values()).hasSize(TOOL_KIND_COUNT);
        assertThat(rows.stream().map(r -> r.get("metal")).distinct()).hasSize(TOOL_TIER_COUNT);
    }

    @Test
    void toolRowsMatchTfcDerivedNumbers() {
        List<String> mismatches = new ArrayList<>();
        for (Map<String, String> row : read("tools.tsv")) {
            Metal metal = Metal.fromId("tfc:metal/" + row.get("metal")).orElseThrow();
            ToolKind kind = ToolKind.valueOf(row.get("tool").toUpperCase(java.util.Locale.ROOT));
            MetalTier tier = metal.toolTier().orElseThrow();
            String name = row.get("metal") + "/" + row.get("tool");

            // The tier inputs of the row are the Java tier.
            if (tier.level() != i(row, "tier_level")
                || tier.durability() != i(row, "tier_uses")
                || !close(tier.speed(), f(row, "tier_speed"))
                || !close(tier.attackDamage(), f(row, "tier_damage"))) {
                mismatches.add(name + ": tier inputs");
            }
            // The kind inputs of the row are the Java kind.
            String head = kind.headPart().orElse("-");
            if (!head.equalsIgnoreCase(row.get("head_part"))
                || kind.hasAttributeModifiers() != Boolean.parseBoolean(row.get("has_attributes"))
                || kind.digger() != Boolean.parseBoolean(row.get("digger"))
                || kind.damageable() != Boolean.parseBoolean(row.get("damageable"))
                || !close(kind.attackDamageFactor(), f(row, "attack_damage_factor"))
                || !close(kind.attackSpeed(), f(row, "attack_speed"))) {
                mismatches.add(name + ": kind inputs");
            }
            // The derived numbers.
            var stats = MetalToolRules.stats(tier, kind);
            if (stats.durability() != i(row, "expected_durability")) {
                mismatches.add(name + ": durability " + stats.durability());
            }
            if (!close(stats.miningSpeed(), f(row, "expected_mining_speed"))) {
                mismatches.add(name + ": mining speed " + stats.miningSpeed());
            }
            if (!close(stats.attackDamageModifier(), f(row, "expected_attack_damage_modifier"))) {
                mismatches.add(name + ": attack damage " + stats.attackDamageModifier());
            }
            if (!close(stats.attackSpeedModifier(), f(row, "expected_attack_speed_modifier"))) {
                mismatches.add(name + ": attack speed " + stats.attackSpeedModifier());
            }
            if (stats.breakPower() != i(row, "tier_level")) {
                mismatches.add(name + ": break power " + stats.breakPower());
            }
        }
        assertThat(mismatches).as("tool rows that differ from TFC").isEmpty();
    }

    @Test
    void armorRowCountIsTheToolTiers() {
        assertThat(read("armor.tsv")).hasSize(TOOL_TIER_COUNT);
    }

    @Test
    void armorRowsMatchTfcDerivedNumbers() {
        List<String> mismatches = new ArrayList<>();
        for (Map<String, String> row : read("armor.tsv")) {
            Metal metal = Metal.fromId("tfc:metal/" + row.get("metal")).orElseThrow();
            MetalTier.Armor armor = metal.toolTier().orElseThrow().armor();
            String name = row.get("metal");
            if (metal.toolTier().orElseThrow().level() != i(row, "tier_level")) {
                mismatches.add(name + ": tier level");
            }
            check(mismatches, name, "durability head", ArmorRules.durability(armor, ArmorSlot.HEAD), i(row, "durability_head"));
            check(mismatches, name, "durability chest", ArmorRules.durability(armor, ArmorSlot.CHEST), i(row, "durability_chest"));
            check(mismatches, name, "durability legs", ArmorRules.durability(armor, ArmorSlot.LEGS), i(row, "durability_legs"));
            check(mismatches, name, "durability feet", ArmorRules.durability(armor, ArmorSlot.FEET), i(row, "durability_feet"));
            check(mismatches, name, "durability body", ArmorRules.durability(armor, ArmorSlot.BODY), i(row, "durability_body"));
            check(mismatches, name, "defense head", ArmorRules.defense(armor, ArmorSlot.HEAD), i(row, "defense_head"));
            check(mismatches, name, "defense chest", ArmorRules.defense(armor, ArmorSlot.CHEST), i(row, "defense_chest"));
            check(mismatches, name, "defense legs", ArmorRules.defense(armor, ArmorSlot.LEGS), i(row, "defense_legs"));
            check(mismatches, name, "defense feet", ArmorRules.defense(armor, ArmorSlot.FEET), i(row, "defense_feet"));
            check(mismatches, name, "defense body", ArmorRules.defense(armor, ArmorSlot.BODY), i(row, "defense_body"));
            check(mismatches, name, "total defense", ArmorRules.totalDefense(armor), i(row, "total_defense"));
            check(mismatches, name, "enchantment", armor.enchantmentValue(), i(row, "enchantment_value"));
            if (!close(armor.toughness(), f(row, "toughness")) || !close(armor.knockbackResistance(), f(row, "knockback_resistance"))) {
                mismatches.add(name + ": toughness or knockback");
            }
        }
        assertThat(mismatches).as("armor rows that differ from TFC").isEmpty();
    }

    @Test
    void forgingRowCountIsTheFiveBonuses() {
        assertThat(read("forging.tsv")).hasSize(FORGING_ROW_COUNT);
        assertThat(ForgingQuality.values()).hasSize(FORGING_ROW_COUNT);
    }

    @Test
    void forgingRowsMatchTfcDerivedNumbers() {
        List<String> mismatches = new ArrayList<>();
        ForgingConfig config = ForgingConfig.DEFAULTS;
        for (Map<String, String> row : read("forging.tsv")) {
            ForgingQuality bonus = ForgingQuality.valueOf(row.get("bonus").toUpperCase(java.util.Locale.ROOT));
            String name = row.get("bonus");
            if (bonus.ordinal() != i(row, "ordinal")) {
                mismatches.add(name + ": ordinal");
            }
            double minRatio = row.get("min_ratio").equals("inf") ? Double.POSITIVE_INFINITY : Double.parseDouble(row.get("min_ratio"));
            if (bonus.minRatio(config) != minRatio) {
                mismatches.add(name + ": min ratio " + bonus.minRatio(config));
            }
            if (!close(bonus.efficiency(config), f(row, "expected_efficiency"))) {
                mismatches.add(name + ": efficiency " + bonus.efficiency(config));
            }
            if (!close(bonus.durability(config), f(row, "expected_durability"))) {
                mismatches.add(name + ": durability " + bonus.durability(config));
            }
            if (!close(bonus.damage(config), f(row, "expected_damage"))) {
                mismatches.add(name + ": damage " + bonus.damage(config));
            }
        }
        assertThat(mismatches).as("forging rows that differ from TFC").isEmpty();
    }

    private static void check(List<String> mismatches, String metal, String what, int actual, int expected) {
        if (actual != expected) {
            mismatches.add(metal + ": " + what + " " + actual + " != " + expected);
        }
    }

    private static boolean close(float actual, float expected) {
        return Math.abs(actual - expected) <= TOLERANCE * Math.max(1f, Math.abs(expected));
    }

    private static float f(Map<String, String> row, String column) {
        return Float.parseFloat(row.get(column));
    }

    private static int i(Map<String, String> row, String column) {
        return Integer.parseInt(row.get(column));
    }

    private static List<Map<String, String>> read(String resource) {
        List<String> lines;
        try (InputStream in = MetalToolGoldenTest.class.getResourceAsStream(resource)) {
            assertThat(in).as("%s on the test classpath", resource).isNotNull();
            lines = new String(in.readAllBytes(), StandardCharsets.UTF_8).lines().filter(l -> !l.isBlank()).toList();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        String[] header = lines.get(0).split("\t", -1);
        List<Map<String, String>> rows = new ArrayList<>();
        for (String line : lines.subList(1, lines.size())) {
            String[] cells = line.split("\t", -1);
            Map<String, String> row = new HashMap<>();
            for (int c = 0; c < header.length; c++) {
                row.put(header[c], cells[c]);
            }
            rows.add(row);
        }
        return rows;
    }
}
