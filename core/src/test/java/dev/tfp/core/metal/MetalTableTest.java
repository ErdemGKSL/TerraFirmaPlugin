/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/util/Metal.java @ c3b4d9536
 */
package dev.tfp.core.metal;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

/**
 * Golden tests for the metal table. {@code metals.tsv} is exported from TFC at c3b4d9536 by reading {@code util/Metal.java},
 * {@code common/TFCTiers.java}, {@code common/TFCArmorMaterials.java}, and the {@code fluid_heat/} and {@code item_heat/}
 * data. Each row is one metal; the tier columns are {@code -} for metals without a tool tier.
 */
class MetalTableTest {
    private static final String RESOURCE = "metals.tsv";
    private static final String NONE = "-";
    /** The metals of TFC's {@code Metal} enum, without the {@code UNKNOWN} fallback. The plan's count. */
    private static final int PLAN_METAL_COUNT = 27;

    private static final List<String> HEADER;
    private static final List<Map<String, String>> ROWS;
    /** The number of metals in the TSV, read from the file. */
    private static final int TSV_METAL_COUNT;

    static {
        List<String> lines = read();
        HEADER = List.of(lines.get(0).split("\t"));
        ROWS = new ArrayList<>();
        for (String line : lines.subList(1, lines.size())) {
            String[] cells = line.split("\t", -1);
            Map<String, String> row = new HashMap<>();
            for (int i = 0; i < HEADER.size(); i++) {
                row.put(HEADER.get(i), cells[i]);
            }
            ROWS.add(row);
        }
        TSV_METAL_COUNT = ROWS.size();
    }

    private static List<String> read() {
        try (InputStream in = MetalTableTest.class.getResourceAsStream(RESOURCE)) {
            assertThat(in).as("%s on the test classpath", RESOURCE).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).lines().filter(l -> !l.isBlank()).toList();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static float f(Map<String, String> row, String column) {
        return Float.parseFloat(row.get(column));
    }

    private static int i(Map<String, String> row, String column) {
        return Integer.parseInt(row.get(column));
    }

    @Test
    void tsvHasTheMetalsOfTheTable() {
        assertThat(TSV_METAL_COUNT).isEqualTo(Metal.values().length);
        assertThat(TSV_METAL_COUNT).isEqualTo(PLAN_METAL_COUNT);
    }

    @Test
    void metalsKeepTfcOrderAndIds() {
        for (int n = 0; n < TSV_METAL_COUNT; n++) {
            Map<String, String> row = ROWS.get(n);
            Metal metal = Metal.values()[n];
            assertThat(metal.name().toLowerCase(java.util.Locale.ROOT)).isEqualTo(row.get("metal"));
            assertThat(metal.id()).isEqualTo("tfc:metal/" + row.get("metal"));
            assertThat(Metal.fromId(metal.id())).contains(metal);
        }
        assertThat(Metal.fromId(Metal.UNKNOWN_ID)).isEmpty();
    }

    @TestFactory
    Stream<DynamicTest> everyMetalMatchesTfc() {
        return ROWS.stream().map(row -> DynamicTest.dynamicTest(row.get("metal"), () -> {
            Metal metal = Metal.valueOf(row.get("metal").toUpperCase(java.util.Locale.ROOT));

            assertThat(metal.weatheringResistance()).isEqualTo(f(row, "weathering_resistance"));
            assertThat(metal.weathers()).isEqualTo(f(row, "weathering_resistance") >= 0f);
            assertThat(metal.meltTemperature()).isEqualTo(f(row, "melt_temperature"));
            assertThat(metal.specificHeatCapacity()).isEqualTo(f(row, "specific_heat_capacity"));
            assertThat(metal.ingotHeatCapacity()).isEqualTo(f(row, "ingot_heat_capacity"));
            assertThat(metal.ingotForgingTemperature()).isEqualTo(f(row, "ingot_forging_temperature"));
            assertThat(metal.ingotWeldingTemperature()).isEqualTo(f(row, "ingot_welding_temperature"));

            if (NONE.equals(row.get("tool_tier"))) {
                assertThat(metal.toolTier()).isEmpty();
                assertThat(metal.tier()).isZero();
                return;
            }
            MetalTier tier = metal.toolTier().orElseThrow();
            assertThat(tier.name()).isEqualTo(row.get("tool_tier"));
            assertThat(metal.tier()).isEqualTo(i(row, "tier_level"));
            assertThat(tier.level()).isEqualTo(i(row, "tier_level"));
            assertThat(tier.durability()).isEqualTo(i(row, "tier_uses"));
            assertThat(tier.speed()).isEqualTo(f(row, "tier_speed"));
            assertThat(tier.attackDamage()).isEqualTo(f(row, "tier_damage"));
            assertThat(tier.enchantmentValue()).isEqualTo(i(row, "tier_enchantment_value"));
            assertThat(tier.incorrectForTag()).isEqualTo(row.get("tier_mineable_tag"));
            assertThat(tier.armor()).isEqualTo(new MetalTier.Armor(
                i(row, "armor_feet_durability"), i(row, "armor_leg_durability"),
                i(row, "armor_chest_durability"), i(row, "armor_head_durability"),
                i(row, "armor_feet_reduction"), i(row, "armor_leg_reduction"),
                i(row, "armor_chest_reduction"), i(row, "armor_head_reduction"),
                i(row, "armor_enchantment_value"),
                f(row, "armor_toughness"), f(row, "armor_knockback_resistance")
            ));
        }));
    }

    @Test
    void toolTiersAreTheNineOfTfc() {
        long withTier = ROWS.stream().filter(row -> !NONE.equals(row.get("tool_tier"))).count();
        assertThat(withTier).isEqualTo(MetalTier.values().length);
        assertThat(MetalTier.values()).hasSize(9);
    }
}
