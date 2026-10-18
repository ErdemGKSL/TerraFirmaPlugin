/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/items/ToolItem.java @ c3b4d9536 (golden values only)
 */
package dev.tfp.core.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * M3-TOOL-01 golden values. The text below was produced by a scratch transcription of TFC's rules at c3b4d9536 (TFCTiers, RockCategory,
 * ToolItem.mineBlock, HammerItem, TFCHoeItem, JavelinItem), with vanilla's DiggerItem durability rule for the stone axe and shovel.
 * Rows: {@code TOOL} the tool type, {@code BLOCK} the fixture block, {@code TIER} the stone tier, {@code ATTACK} and
 * {@code JAVELIN_THROW} the damage values, {@code MINE tool tier block speed correct durabilityLoss} the mining grid.
 */
class ToolRulesGoldenTest {

    // from TFC at c3b4d9536
    private static final String GOLDEN = """
# TOOL name attackFactor attackSpeed digs mineableTag consumesTag
TOOL AXE 1.5 -3.2 true minecraft:mineable/axe false
TOOL HAMMER 1.0 -3.0 true tfc:mineable/hammer true
TOOL HOE 0.5 -3.0 true tfc:mineable/hoe true
TOOL JAVELIN 0.7 -2.2 false - -
TOOL KNIFE 0.6 -2.0 true tfc:mineable/knife true
TOOL SHOVEL 0.875 -3.0 true minecraft:mineable/shovel false
# BLOCK name mineable incorrect requiresCorrectTool consumesTag destroySpeed (- for none)
BLOCK B0 SHOVEL - false false 0.6
BLOCK B1 HAMMER - true false 3.0
BLOCK B2 HAMMER WOODEN true false 3.0
BLOCK B3 - - false false -1.0
BLOCK B4 HOE - false true 0.0
BLOCK B5 AXE - false false 2.0
BLOCK B6 HOE,KNIFE STONE,WOODEN true true 5.0
BLOCK B7 AXE,SHOVEL DIAMOND,IRON,NETHERITE,STONE,WOODEN true false 2.0
BLOCK B8 - - false false 0.0
# TIERS name level uses speed attackBonus enchant incorrect
TIER IGNEOUS_EXTRUSIVE 0 70 4.7 2.0 5 WOODEN
TIER IGNEOUS_INTRUSIVE 0 60 4.7 2.0 5 WOODEN
TIER SEDIMENTARY 0 50 4.0 2.0 5 WOODEN
TIER METAMORPHIC 0 55 4.35 2.0 5 WOODEN
# ATTACK tool tier attackDamage(=factor*bonus) attackSpeed
ATTACK AXE IGNEOUS_EXTRUSIVE 3.0 -3.2
ATTACK AXE IGNEOUS_INTRUSIVE 3.0 -3.2
ATTACK AXE SEDIMENTARY 3.0 -3.2
ATTACK AXE METAMORPHIC 3.0 -3.2
ATTACK HAMMER IGNEOUS_EXTRUSIVE 2.0 -3.0
ATTACK HAMMER IGNEOUS_INTRUSIVE 2.0 -3.0
ATTACK HAMMER SEDIMENTARY 2.0 -3.0
ATTACK HAMMER METAMORPHIC 2.0 -3.0
ATTACK HOE IGNEOUS_EXTRUSIVE 1.0 -3.0
ATTACK HOE IGNEOUS_INTRUSIVE 1.0 -3.0
ATTACK HOE SEDIMENTARY 1.0 -3.0
ATTACK HOE METAMORPHIC 1.0 -3.0
ATTACK JAVELIN IGNEOUS_EXTRUSIVE 1.4 -2.2
ATTACK JAVELIN IGNEOUS_INTRUSIVE 1.4 -2.2
ATTACK JAVELIN SEDIMENTARY 1.4 -2.2
ATTACK JAVELIN METAMORPHIC 1.4 -2.2
ATTACK KNIFE IGNEOUS_EXTRUSIVE 1.2 -2.0
ATTACK KNIFE IGNEOUS_INTRUSIVE 1.2 -2.0
ATTACK KNIFE SEDIMENTARY 1.2 -2.0
ATTACK KNIFE METAMORPHIC 1.2 -2.0
ATTACK SHOVEL IGNEOUS_EXTRUSIVE 1.75 -3.0
ATTACK SHOVEL IGNEOUS_INTRUSIVE 1.75 -3.0
ATTACK SHOVEL SEDIMENTARY 1.75 -3.0
ATTACK SHOVEL METAMORPHIC 1.75 -3.0
# JAVELIN_THROW tier thrownDamage(=1.5*bonus)
JAVELIN_THROW IGNEOUS_EXTRUSIVE 3.0
JAVELIN_THROW IGNEOUS_INTRUSIVE 3.0
JAVELIN_THROW SEDIMENTARY 3.0
JAVELIN_THROW METAMORPHIC 3.0
# MINE tool tier block speed correct durabilityLoss
MINE AXE IGNEOUS_EXTRUSIVE B0 1.0 true 1
MINE AXE IGNEOUS_EXTRUSIVE B1 1.0 false 1
MINE AXE IGNEOUS_EXTRUSIVE B2 1.0 false 1
MINE AXE IGNEOUS_EXTRUSIVE B3 1.0 true 1
MINE AXE IGNEOUS_EXTRUSIVE B4 1.0 true 0
MINE AXE IGNEOUS_EXTRUSIVE B5 4.7 true 1
MINE AXE IGNEOUS_EXTRUSIVE B6 1.0 false 1
MINE AXE IGNEOUS_EXTRUSIVE B7 4.7 false 1
MINE AXE IGNEOUS_EXTRUSIVE B8 1.0 true 0
MINE AXE IGNEOUS_INTRUSIVE B0 1.0 true 1
MINE AXE IGNEOUS_INTRUSIVE B1 1.0 false 1
MINE AXE IGNEOUS_INTRUSIVE B2 1.0 false 1
MINE AXE IGNEOUS_INTRUSIVE B3 1.0 true 1
MINE AXE IGNEOUS_INTRUSIVE B4 1.0 true 0
MINE AXE IGNEOUS_INTRUSIVE B5 4.7 true 1
MINE AXE IGNEOUS_INTRUSIVE B6 1.0 false 1
MINE AXE IGNEOUS_INTRUSIVE B7 4.7 false 1
MINE AXE IGNEOUS_INTRUSIVE B8 1.0 true 0
MINE AXE SEDIMENTARY B0 1.0 true 1
MINE AXE SEDIMENTARY B1 1.0 false 1
MINE AXE SEDIMENTARY B2 1.0 false 1
MINE AXE SEDIMENTARY B3 1.0 true 1
MINE AXE SEDIMENTARY B4 1.0 true 0
MINE AXE SEDIMENTARY B5 4.0 true 1
MINE AXE SEDIMENTARY B6 1.0 false 1
MINE AXE SEDIMENTARY B7 4.0 false 1
MINE AXE SEDIMENTARY B8 1.0 true 0
MINE AXE METAMORPHIC B0 1.0 true 1
MINE AXE METAMORPHIC B1 1.0 false 1
MINE AXE METAMORPHIC B2 1.0 false 1
MINE AXE METAMORPHIC B3 1.0 true 1
MINE AXE METAMORPHIC B4 1.0 true 0
MINE AXE METAMORPHIC B5 4.35 true 1
MINE AXE METAMORPHIC B6 1.0 false 1
MINE AXE METAMORPHIC B7 4.35 false 1
MINE AXE METAMORPHIC B8 1.0 true 0
MINE HAMMER IGNEOUS_EXTRUSIVE B0 1.0 true 1
MINE HAMMER IGNEOUS_EXTRUSIVE B1 4.7 true 1
MINE HAMMER IGNEOUS_EXTRUSIVE B2 4.7 false 1
MINE HAMMER IGNEOUS_EXTRUSIVE B3 1.0 true 1
MINE HAMMER IGNEOUS_EXTRUSIVE B4 1.0 true 1
MINE HAMMER IGNEOUS_EXTRUSIVE B5 1.0 true 1
MINE HAMMER IGNEOUS_EXTRUSIVE B6 1.0 false 1
MINE HAMMER IGNEOUS_EXTRUSIVE B7 1.0 false 1
MINE HAMMER IGNEOUS_EXTRUSIVE B8 1.0 true 0
MINE HAMMER IGNEOUS_INTRUSIVE B0 1.0 true 1
MINE HAMMER IGNEOUS_INTRUSIVE B1 4.7 true 1
MINE HAMMER IGNEOUS_INTRUSIVE B2 4.7 false 1
MINE HAMMER IGNEOUS_INTRUSIVE B3 1.0 true 1
MINE HAMMER IGNEOUS_INTRUSIVE B4 1.0 true 1
MINE HAMMER IGNEOUS_INTRUSIVE B5 1.0 true 1
MINE HAMMER IGNEOUS_INTRUSIVE B6 1.0 false 1
MINE HAMMER IGNEOUS_INTRUSIVE B7 1.0 false 1
MINE HAMMER IGNEOUS_INTRUSIVE B8 1.0 true 0
MINE HAMMER SEDIMENTARY B0 1.0 true 1
MINE HAMMER SEDIMENTARY B1 4.0 true 1
MINE HAMMER SEDIMENTARY B2 4.0 false 1
MINE HAMMER SEDIMENTARY B3 1.0 true 1
MINE HAMMER SEDIMENTARY B4 1.0 true 1
MINE HAMMER SEDIMENTARY B5 1.0 true 1
MINE HAMMER SEDIMENTARY B6 1.0 false 1
MINE HAMMER SEDIMENTARY B7 1.0 false 1
MINE HAMMER SEDIMENTARY B8 1.0 true 0
MINE HAMMER METAMORPHIC B0 1.0 true 1
MINE HAMMER METAMORPHIC B1 4.35 true 1
MINE HAMMER METAMORPHIC B2 4.35 false 1
MINE HAMMER METAMORPHIC B3 1.0 true 1
MINE HAMMER METAMORPHIC B4 1.0 true 1
MINE HAMMER METAMORPHIC B5 1.0 true 1
MINE HAMMER METAMORPHIC B6 1.0 false 1
MINE HAMMER METAMORPHIC B7 1.0 false 1
MINE HAMMER METAMORPHIC B8 1.0 true 0
MINE HOE IGNEOUS_EXTRUSIVE B0 1.0 true 1
MINE HOE IGNEOUS_EXTRUSIVE B1 1.0 false 1
MINE HOE IGNEOUS_EXTRUSIVE B2 1.0 false 1
MINE HOE IGNEOUS_EXTRUSIVE B3 1.0 true 1
MINE HOE IGNEOUS_EXTRUSIVE B4 4.7 true 1
MINE HOE IGNEOUS_EXTRUSIVE B5 1.0 true 1
MINE HOE IGNEOUS_EXTRUSIVE B6 4.7 false 1
MINE HOE IGNEOUS_EXTRUSIVE B7 1.0 false 1
MINE HOE IGNEOUS_EXTRUSIVE B8 1.0 true 0
MINE HOE IGNEOUS_INTRUSIVE B0 1.0 true 1
MINE HOE IGNEOUS_INTRUSIVE B1 1.0 false 1
MINE HOE IGNEOUS_INTRUSIVE B2 1.0 false 1
MINE HOE IGNEOUS_INTRUSIVE B3 1.0 true 1
MINE HOE IGNEOUS_INTRUSIVE B4 4.7 true 1
MINE HOE IGNEOUS_INTRUSIVE B5 1.0 true 1
MINE HOE IGNEOUS_INTRUSIVE B6 4.7 false 1
MINE HOE IGNEOUS_INTRUSIVE B7 1.0 false 1
MINE HOE IGNEOUS_INTRUSIVE B8 1.0 true 0
MINE HOE SEDIMENTARY B0 1.0 true 1
MINE HOE SEDIMENTARY B1 1.0 false 1
MINE HOE SEDIMENTARY B2 1.0 false 1
MINE HOE SEDIMENTARY B3 1.0 true 1
MINE HOE SEDIMENTARY B4 4.0 true 1
MINE HOE SEDIMENTARY B5 1.0 true 1
MINE HOE SEDIMENTARY B6 4.0 false 1
MINE HOE SEDIMENTARY B7 1.0 false 1
MINE HOE SEDIMENTARY B8 1.0 true 0
MINE HOE METAMORPHIC B0 1.0 true 1
MINE HOE METAMORPHIC B1 1.0 false 1
MINE HOE METAMORPHIC B2 1.0 false 1
MINE HOE METAMORPHIC B3 1.0 true 1
MINE HOE METAMORPHIC B4 4.35 true 1
MINE HOE METAMORPHIC B5 1.0 true 1
MINE HOE METAMORPHIC B6 4.35 false 1
MINE HOE METAMORPHIC B7 1.0 false 1
MINE HOE METAMORPHIC B8 1.0 true 0
MINE KNIFE IGNEOUS_EXTRUSIVE B0 1.0 true 1
MINE KNIFE IGNEOUS_EXTRUSIVE B1 1.0 false 1
MINE KNIFE IGNEOUS_EXTRUSIVE B2 1.0 false 1
MINE KNIFE IGNEOUS_EXTRUSIVE B3 1.0 true 1
MINE KNIFE IGNEOUS_EXTRUSIVE B4 1.0 true 1
MINE KNIFE IGNEOUS_EXTRUSIVE B5 1.0 true 1
MINE KNIFE IGNEOUS_EXTRUSIVE B6 4.7 false 1
MINE KNIFE IGNEOUS_EXTRUSIVE B7 1.0 false 1
MINE KNIFE IGNEOUS_EXTRUSIVE B8 1.0 true 0
MINE KNIFE IGNEOUS_INTRUSIVE B0 1.0 true 1
MINE KNIFE IGNEOUS_INTRUSIVE B1 1.0 false 1
MINE KNIFE IGNEOUS_INTRUSIVE B2 1.0 false 1
MINE KNIFE IGNEOUS_INTRUSIVE B3 1.0 true 1
MINE KNIFE IGNEOUS_INTRUSIVE B4 1.0 true 1
MINE KNIFE IGNEOUS_INTRUSIVE B5 1.0 true 1
MINE KNIFE IGNEOUS_INTRUSIVE B6 4.7 false 1
MINE KNIFE IGNEOUS_INTRUSIVE B7 1.0 false 1
MINE KNIFE IGNEOUS_INTRUSIVE B8 1.0 true 0
MINE KNIFE SEDIMENTARY B0 1.0 true 1
MINE KNIFE SEDIMENTARY B1 1.0 false 1
MINE KNIFE SEDIMENTARY B2 1.0 false 1
MINE KNIFE SEDIMENTARY B3 1.0 true 1
MINE KNIFE SEDIMENTARY B4 1.0 true 1
MINE KNIFE SEDIMENTARY B5 1.0 true 1
MINE KNIFE SEDIMENTARY B6 4.0 false 1
MINE KNIFE SEDIMENTARY B7 1.0 false 1
MINE KNIFE SEDIMENTARY B8 1.0 true 0
MINE KNIFE METAMORPHIC B0 1.0 true 1
MINE KNIFE METAMORPHIC B1 1.0 false 1
MINE KNIFE METAMORPHIC B2 1.0 false 1
MINE KNIFE METAMORPHIC B3 1.0 true 1
MINE KNIFE METAMORPHIC B4 1.0 true 1
MINE KNIFE METAMORPHIC B5 1.0 true 1
MINE KNIFE METAMORPHIC B6 4.35 false 1
MINE KNIFE METAMORPHIC B7 1.0 false 1
MINE KNIFE METAMORPHIC B8 1.0 true 0
MINE SHOVEL IGNEOUS_EXTRUSIVE B0 4.7 true 1
MINE SHOVEL IGNEOUS_EXTRUSIVE B1 1.0 false 1
MINE SHOVEL IGNEOUS_EXTRUSIVE B2 1.0 false 1
MINE SHOVEL IGNEOUS_EXTRUSIVE B3 1.0 true 1
MINE SHOVEL IGNEOUS_EXTRUSIVE B4 1.0 true 0
MINE SHOVEL IGNEOUS_EXTRUSIVE B5 1.0 true 1
MINE SHOVEL IGNEOUS_EXTRUSIVE B6 1.0 false 1
MINE SHOVEL IGNEOUS_EXTRUSIVE B7 4.7 false 1
MINE SHOVEL IGNEOUS_EXTRUSIVE B8 1.0 true 0
MINE SHOVEL IGNEOUS_INTRUSIVE B0 4.7 true 1
MINE SHOVEL IGNEOUS_INTRUSIVE B1 1.0 false 1
MINE SHOVEL IGNEOUS_INTRUSIVE B2 1.0 false 1
MINE SHOVEL IGNEOUS_INTRUSIVE B3 1.0 true 1
MINE SHOVEL IGNEOUS_INTRUSIVE B4 1.0 true 0
MINE SHOVEL IGNEOUS_INTRUSIVE B5 1.0 true 1
MINE SHOVEL IGNEOUS_INTRUSIVE B6 1.0 false 1
MINE SHOVEL IGNEOUS_INTRUSIVE B7 4.7 false 1
MINE SHOVEL IGNEOUS_INTRUSIVE B8 1.0 true 0
MINE SHOVEL SEDIMENTARY B0 4.0 true 1
MINE SHOVEL SEDIMENTARY B1 1.0 false 1
MINE SHOVEL SEDIMENTARY B2 1.0 false 1
MINE SHOVEL SEDIMENTARY B3 1.0 true 1
MINE SHOVEL SEDIMENTARY B4 1.0 true 0
MINE SHOVEL SEDIMENTARY B5 1.0 true 1
MINE SHOVEL SEDIMENTARY B6 1.0 false 1
MINE SHOVEL SEDIMENTARY B7 4.0 false 1
MINE SHOVEL SEDIMENTARY B8 1.0 true 0
MINE SHOVEL METAMORPHIC B0 4.35 true 1
MINE SHOVEL METAMORPHIC B1 1.0 false 1
MINE SHOVEL METAMORPHIC B2 1.0 false 1
MINE SHOVEL METAMORPHIC B3 1.0 true 1
MINE SHOVEL METAMORPHIC B4 1.0 true 0
MINE SHOVEL METAMORPHIC B5 1.0 true 1
MINE SHOVEL METAMORPHIC B6 1.0 false 1
MINE SHOVEL METAMORPHIC B7 4.35 false 1
MINE SHOVEL METAMORPHIC B8 1.0 true 0
            """;

    private static final float EPS = 1e-6f;

    private record Golden(Map<String, ToolType> tools, Map<String, MinedBlock> blocks, Map<String, ToolTier> tiers) {}

    private static Golden load() {
        Map<String, ToolType> tools = new HashMap<>();
        Map<String, MinedBlock> blocks = new HashMap<>();
        Map<String, ToolTier> tiers = new HashMap<>();
        for (String line : GOLDEN.split("\n")) {
            if (line.isBlank() || line.startsWith("#")) continue;
            String[] t = line.trim().split(" ");
            switch (t[0]) {
                case "TOOL" -> {
                    ToolType tool = ToolType.valueOf(t[1]);
                    tools.put(t[1], tool);
                    assertThat(tool.attackDamageFactor()).as("factor " + t[1]).isEqualTo(Float.parseFloat(t[2]));
                    assertThat(tool.attackSpeed()).as("attack speed " + t[1]).isEqualTo(Float.parseFloat(t[3]));
                    assertThat(tool.digs()).as("digs " + t[1]).isEqualTo(Boolean.parseBoolean(t[4]));
                    assertThat(tool.mineableTag()).as("tag " + t[1]).isEqualTo(t[5].equals("-") ? null : t[5]);
                    if (!t[6].equals("-")) {
                        assertThat(tool.consumesDurabilityTag()).as("consumes " + t[1]).isEqualTo(Boolean.parseBoolean(t[6]));
                    }
                }
                case "BLOCK" -> blocks.put(t[1], new MinedBlock(
                    tagSet(t[2], ToolType.class),
                    tagSet(t[3], IncorrectTag.class),
                    Boolean.parseBoolean(t[4]),
                    Boolean.parseBoolean(t[5]),
                    Float.parseFloat(t[6])
                ));
                case "TIER" -> {
                    ToolTier tier = ToolTier.valueOf(t[1]);
                    tiers.put(t[1], tier);
                    assertThat(tier.level()).as("level " + t[1]).isEqualTo(Integer.parseInt(t[2]));
                    assertThat(tier.uses()).as("uses " + t[1]).isEqualTo(Integer.parseInt(t[3]));
                    assertThat(tier.speed()).as("speed " + t[1]).isEqualTo(Float.parseFloat(t[4]));
                    assertThat(tier.attackDamageBonus()).as("bonus " + t[1]).isEqualTo(Float.parseFloat(t[5]));
                    assertThat(tier.enchantmentValue()).as("enchant " + t[1]).isEqualTo(Integer.parseInt(t[6]));
                    assertThat(tier.incorrectTag()).as("incorrect " + t[1]).isEqualTo(IncorrectTag.valueOf(t[7]));
                }
                default -> { }
            }
        }
        return new Golden(tools, blocks, tiers);
    }

    private static <T extends Enum<T>> Set<T> tagSet(String text, Class<T> type) {
        EnumSet<T> out = EnumSet.noneOf(type);
        if (!text.equals("-")) {
            for (String name : text.split(",")) out.add(Enum.valueOf(type, name));
        }
        return out;
    }

    @Test
    void tierAndToolTablesMatchTfc() {
        Golden g = load();
        assertThat(g.tiers()).hasSize(4);
        assertThat(g.tools()).hasSize(6);
        assertThat(g.blocks()).hasSize(9);
        assertThat(ToolTier.values()).hasSize(4);
        assertThat(ToolType.values()).hasSize(6);
    }

    @Test
    void attackAndJavelinDamageMatchTfc() {
        Golden g = load();
        int rows = 0;
        for (String line : GOLDEN.split("\n")) {
            String[] t = line.trim().split(" ");
            if (t[0].equals("ATTACK")) {
                rows++;
                float expected = Float.parseFloat(t[3]);
                assertThat(ToolRules.attackDamage(g.tools().get(t[1]), g.tiers().get(t[2])))
                    .as(line).isCloseTo(expected, within(EPS));
            } else if (t[0].equals("JAVELIN_THROW")) {
                rows++;
                assertThat(ToolRules.javelinThrownDamage(g.tiers().get(t[1])))
                    .as(line).isCloseTo(Float.parseFloat(t[2]), within(EPS));
            }
        }
        assertThat(rows).isEqualTo(24 + 4);
    }

    @Test
    void miningGridMatchesTfc() {
        Golden g = load();
        int rows = 0;
        Map<String, Integer> seen = new HashMap<>();
        for (String line : GOLDEN.split("\n")) {
            String[] t = line.trim().split(" ");
            if (!t[0].equals("MINE")) continue;
            rows++;
            ToolType tool = g.tools().get(t[1]);
            ToolTier tier = g.tiers().get(t[2]);
            MinedBlock block = g.blocks().get(t[3]);
            assertThat(ToolRules.miningSpeed(tool, tier, block)).as(line).isCloseTo(Float.parseFloat(t[4]), within(EPS));
            assertThat(ToolRules.correctForDrops(tool, tier, block)).as(line).isEqualTo(Boolean.parseBoolean(t[5]));
            assertThat(ToolRules.durabilityLoss(tool, block)).as(line).isEqualTo(Integer.parseInt(t[6]));
            seen.merge(t[1] + "/" + t[2], 1, Integer::sum);
        }
        assertThat(rows).isEqualTo(180);
        assertThat(seen).hasSize(4 * 5);
    }
}
