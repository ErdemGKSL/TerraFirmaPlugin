/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/util/Metal.java @ c3b4d9536, common/TFCTiers.java, common/items/ToolItem.java and
 * common/component/forge/ForgingQuality.java (golden values only)
 */
package dev.tfp.core.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.tfp.core.metal.Metal;
import dev.tfp.core.metal.MetalTier;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SplittableRandom;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * Tier progression and hand-checked values for the metal tool, armor and forging rules.
 */
class MetalToolRulesTest {

    @Test
    void toolMetalsAreTheNineTiersAndTheirLevelsAreOneToSix() {
        List<Metal> tiered = Arrays.stream(Metal.values()).filter(m -> m.toolTier().isPresent()).toList();
        assertThat(tiered).hasSize(9);
        assertThat(tiered.stream().map(Metal::tier).distinct().sorted()).containsExactly(1, 2, 3, 4, 5, 6);
        for (Metal metal : Metal.values()) {
            if (metal.toolTier().isEmpty()) {
                assertThat(metal.tier()).as(metal.name()).isZero();
                assertThat(MetalToolRules.stats(metal, ToolKind.PICKAXE)).isEmpty();
            }
        }
    }

    @Test
    void durabilityAttackDamageAndMiningSpeedIncreaseWithTierLevel() {
        Map<Integer, List<ToolStats>> byLevel = tierStatsByLevel(ToolKind.PICKAXE);
        for (int level = 1; level < 6; level++) {
            List<ToolStats> lower = byLevel.get(level);
            List<ToolStats> higher = byLevel.get(level + 1);
            assertThat(higher.stream().mapToInt(ToolStats::durability).min().orElseThrow())
                .as("durability of level %d above level %d", level + 1, level)
                .isGreaterThan(lower.stream().mapToInt(ToolStats::durability).max().orElseThrow());
            assertThat(higher.stream().mapToDouble(ToolStats::miningSpeed).min().orElseThrow())
                .as("mining speed of level %d above level %d", level + 1, level)
                .isGreaterThan(lower.stream().mapToDouble(ToolStats::miningSpeed).max().orElseThrow());
            assertThat(higher.stream().mapToDouble(ToolStats::attackDamageModifier).min().orElseThrow())
                .as("attack damage of level %d above level %d", level + 1, level)
                .isGreaterThan(lower.stream().mapToDouble(ToolStats::attackDamageModifier).max().orElseThrow());
        }
    }

    @Test
    void redAndBlueSteelShareTheirToolNumbers() {
        for (ToolKind kind : ToolKind.values()) {
            assertThat(MetalToolRules.stats(Metal.BLUE_STEEL, kind)).isEqualTo(MetalToolRules.stats(Metal.RED_STEEL, kind));
        }
    }

    @Test
    void copperPickaxeHandValues() {
        ToolStats pickaxe = MetalToolRules.stats(Metal.COPPER, ToolKind.PICKAXE).orElseThrow();
        assertThat(pickaxe.durability()).isEqualTo(600);
        assertThat(pickaxe.miningSpeed()).isCloseTo(5.25f, within(1e-6f));
        assertThat(pickaxe.attackDamageModifier()).isCloseTo(0.75f * 3.25f, within(1e-6f));
        assertThat(pickaxe.attackSpeedModifier()).isCloseTo(-2.8f, within(1e-6f));
        assertThat(pickaxe.breakPower()).isEqualTo(1);
    }

    @Test
    void nonDiggersMineAtSpeedOneAndHaveNoAttributesWhenTheKindHasNone() {
        ToolStats sword = MetalToolRules.stats(Metal.STEEL, ToolKind.SWORD).orElseThrow();
        assertThat(sword.miningSpeed()).isEqualTo(1f);
        assertThat(sword.attackDamageModifier()).isCloseTo(5.75f, within(1e-6f));

        ToolStats shears = MetalToolRules.stats(Metal.STEEL, ToolKind.SHEARS).orElseThrow();
        assertThat(shears.durability()).isEqualTo(3300);
        assertThat(shears.attackDamageModifier()).isZero();
        assertThat(shears.attackSpeedModifier()).isZero();

        ToolStats tuyere = MetalToolRules.stats(Metal.STEEL, ToolKind.TUYERE).orElseThrow();
        assertThat(tuyere.durability()).isZero();
    }

    @Test
    void heatingRecipeFluidScalesWithRemainingDurability() {
        assertThat(MetalToolRules.fluidAfterDamage(1000, 0, 10)).isEqualTo(1000);
        assertThat(MetalToolRules.fluidAfterDamage(1000, 5, 10)).isEqualTo(500);
        assertThat(MetalToolRules.fluidAfterDamage(1000, 10, 10)).isZero();
        assertThat(MetalToolRules.fluidAfterDamage(7, 1, 2)).isEqualTo(3);
        assertThat(MetalToolRules.fluidAfterDamage(1000, 4, 0)).as("no max damage keeps the amount").isEqualTo(1000);
    }

    @Test
    void armorSlotsMapToTheirPiecesAndBodyUsesChest() {
        MetalTier.Armor steel = Metal.STEEL.toolTier().orElseThrow().armor();
        assertThat(ArmorRules.durability(steel, ArmorSlot.HEAD)).isEqualTo(440);
        assertThat(ArmorRules.durability(steel, ArmorSlot.LEGS)).isEqualTo(600);
        assertThat(ArmorRules.durability(steel, ArmorSlot.BODY)).isEqualTo(ArmorRules.durability(steel, ArmorSlot.CHEST));
        assertThat(ArmorRules.defense(steel, ArmorSlot.BODY)).isEqualTo(ArmorRules.defense(steel, ArmorSlot.CHEST));
        assertThat(ArmorRules.totalDefense(steel)).isEqualTo(2 + 6 + 5 + 2);
        assertThat(steel.toughness()).isEqualTo(1f);
    }

    @Test
    void forgeRatioPicksTheHighestBonusStrictlyAboveTheRatio() {
        ForgingConfig c = ForgingConfig.DEFAULTS;
        assertThat(ForgingQuality.byRatio(1.0f, c)).isEqualTo(ForgingQuality.PERFECT);
        assertThat(ForgingQuality.byRatio(1.49f, c)).isEqualTo(ForgingQuality.PERFECT);
        assertThat(ForgingQuality.byRatio(1.5f, c)).isEqualTo(ForgingQuality.EXPERT);
        assertThat(ForgingQuality.byRatio(1.99f, c)).isEqualTo(ForgingQuality.EXPERT);
        assertThat(ForgingQuality.byRatio(2.0f, c)).isEqualTo(ForgingQuality.WELL);
        assertThat(ForgingQuality.byRatio(5.0f, c)).isEqualTo(ForgingQuality.MODEST);
        assertThat(ForgingQuality.byRatio(9.99f, c)).isEqualTo(ForgingQuality.MODEST);
        assertThat(ForgingQuality.byRatio(10.0f, c)).isEqualTo(ForgingQuality.NONE);
    }

    @Test
    void forgingUnbreakingNeverAddsDamageAndNoneIgnoresNothing() {
        SplittableRandom random = new SplittableRandom(7L);
        assertThat(ForgingQuality.NONE.applyLikeUnbreaking(100, ForgingConfig.DEFAULTS, random)).isEqualTo(100);
        assertThat(ForgingQuality.PERFECT.applyLikeUnbreaking(0, ForgingConfig.DEFAULTS, random)).isZero();
        for (int trial = 0; trial < 200; trial++) {
            int result = ForgingQuality.PERFECT.applyLikeUnbreaking(50, ForgingConfig.DEFAULTS, random);
            assertThat(result).isBetween(0, 50);
        }
    }

    @Test
    void armorDurabilityAndDefenseFollowTheSlotTable() {
        MetalTier.Armor copper = Metal.COPPER.toolTier().orElseThrow().armor();
        assertThat(ArmorRules.durability(copper, ArmorSlot.HEAD)).isEqualTo(150);
        assertThat(ArmorRules.durability(copper, ArmorSlot.CHEST)).isEqualTo(215);
        assertThat(ArmorRules.defense(copper, ArmorSlot.LEGS)).isEqualTo(3);
        assertThat(ArmorRules.totalDefense(copper)).isEqualTo(9);
    }

    private static Map<Integer, List<ToolStats>> tierStatsByLevel(ToolKind kind) {
        return Arrays.stream(Metal.values())
            .map(m -> MetalToolRules.stats(m, kind))
            .flatMap(Optional::stream)
            .collect(Collectors.groupingBy(ToolStats::breakPower));
    }
}
