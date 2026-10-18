/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** The stone tool rules give the same answer for fixed inputs, whatever order they are asked in and however often. */
class ToolRulesDeterminismTest {

    private static final List<MinedBlock> BLOCKS = List.of(
        new MinedBlock(EnumSet.of(ToolType.SHOVEL), EnumSet.noneOf(IncorrectTag.class), false, false, 0.6f),
        new MinedBlock(EnumSet.of(ToolType.HAMMER), EnumSet.noneOf(IncorrectTag.class), true, false, 3.0f),
        new MinedBlock(EnumSet.of(ToolType.HAMMER), EnumSet.of(IncorrectTag.WOODEN), true, false, 3.0f),
        new MinedBlock(EnumSet.of(ToolType.HOE), EnumSet.noneOf(IncorrectTag.class), false, true, 0.0f),
        new MinedBlock(EnumSet.of(ToolType.AXE, ToolType.SHOVEL), EnumSet.allOf(IncorrectTag.class), true, false, 2.0f),
        new MinedBlock(EnumSet.noneOf(ToolType.class), EnumSet.noneOf(IncorrectTag.class), false, false, -1.0f)
    );

    /** Every (tool, tier, block) answer, keyed by its coordinates. */
    private static Map<String, String> grid(List<ToolType> tools, List<ToolTier> tiers, List<MinedBlock> blocks) {
        Map<String, String> out = new LinkedHashMap<>();
        for (ToolType tool : tools) {
            if (!tool.digs()) continue;
            for (ToolTier tier : tiers) {
                for (int b = 0; b < blocks.size(); b++) {
                    MinedBlock block = blocks.get(b);
                    out.put(tool + "/" + tier + "/" + b, ToolRules.miningSpeed(tool, tier, block) + " "
                        + ToolRules.correctForDrops(tool, tier, block) + " " + ToolRules.durabilityLoss(tool, block));
                }
            }
        }
        return out;
    }

    @Test
    void answersDoNotDependOnOrderOrRepetition() {
        List<ToolType> tools = List.of(ToolType.values());
        List<ToolTier> tiers = List.of(ToolTier.values());
        Map<String, String> sorted = grid(tools, tiers, BLOCKS);
        assertThat(sorted).hasSize(5 * 4 * BLOCKS.size());

        assertThat(grid(tools, tiers, BLOCKS)).isEqualTo(sorted);
        for (long seed = 1; seed <= 20; seed++) {
            List<ToolType> shuffledTools = new ArrayList<>(tools);
            List<ToolTier> shuffledTiers = new ArrayList<>(tiers);
            List<MinedBlock> shuffledBlocks = new ArrayList<>(BLOCKS);
            Collections.shuffle(shuffledTools, new Random(seed));
            Collections.shuffle(shuffledTiers, new Random(seed + 100));
            Collections.shuffle(shuffledBlocks, new Random(seed + 200));
            Map<String, String> shuffled = grid(shuffledTools, shuffledTiers, shuffledBlocks);
            // Keys use the block index, so compare by block identity instead of position.
            for (Map.Entry<String, String> e : sorted.entrySet()) {
                String[] parts = e.getKey().split("/");
                MinedBlock block = BLOCKS.get(Integer.parseInt(parts[2]));
                int shuffledIndex = shuffledBlocks.indexOf(block);
                String key = parts[0] + "/" + parts[1] + "/" + shuffledIndex;
                assertThat(shuffled).containsEntry(key, e.getValue());
            }
        }
    }

    @Test
    void durabilityLossDoesNotDependOnTier() {
        for (ToolType tool : ToolType.values()) {
            if (!tool.digs()) continue;
            for (MinedBlock block : BLOCKS) {
                int loss = ToolRules.durabilityLoss(tool, block);
                for (ToolTier tier : ToolTier.values()) {
                    assertThat(ToolRules.durabilityLoss(tool, block)).isEqualTo(loss);
                    assertThat(ToolRules.damageAfterMining(0, tool, block)).isEqualTo(loss);
                }
            }
        }
    }

    @Test
    void javelinDoesNotMine() {
        assertThatThrownBy(() -> ToolRules.durabilityLoss(ToolType.JAVELIN, BLOCKS.get(0)))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ToolRules.miningSpeed(ToolType.JAVELIN, ToolTier.SEDIMENTARY, BLOCKS.get(0)))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
