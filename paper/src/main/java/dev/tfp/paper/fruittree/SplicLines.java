/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/plant/fruit/FruitTreeBranchBlock.java @ c3b4d9536 (addHoeOverlayInfo),
 *       src/main/java/net/dries007/tfc/common/blocks/plant/fruit/GrowingFruitTreeBranchBlock.java @ c3b4d9536 (addExtraInfo),
 *       src/main/java/net/dries007/tfc/common/blocks/plant/fruit/FruitTreeSaplingBlock.java @ c3b4d9536 (addHoeOverlayInfo),
 *       src/main/java/net/dries007/tfc/common/blocks/plant/fruit/FruitTreeLeavesBlock.java @ c3b4d9536 (addHoeOverlayInfo),
 *       src/main/java/net/dries007/tfc/common/blocks/plant/fruit/BananaPlantBlock.java @ c3b4d9536 (addHoeOverlayInfo),
 *       src/main/java/net/dries007/tfc/common/blocks/soil/FarmlandBlock.java @ c3b4d9536 (getHydrationTooltip, getAverageTemperatureTooltip)
 */
package dev.tfp.paper.fruittree;

import dev.tfp.core.climate.ClimateRange;
import dev.tfp.core.climate.ClimateTemperatureStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

/**
 * PAR-FARM-28: the lines the hoe shows for fruit trees and the banana (TFC's {@code addHoeOverlayInfo}): the hydration at the root below the
 * stem and the average temperature at the stem, each with the verdict against the plant's {@link ClimateRange}, then the block's own hints.
 * Pure: the text comes from a lookup of message keys ({@code (key, args) -> text}).
 */
public final class SplicLines {
    private SplicLines() {
    }

    /** {@code FarmlandBlock.getHydrationTooltip(range, false, hydration)}: {@code fruit_tree.hydration} plus the verdict. */
    public static String hydration(BiFunction<String, Object[], String> text, ClimateRange range, int hydration) {
        StringBuilder out = new StringBuilder(text.apply("fruit_tree.hydration", new Object[] {hydration}));
        switch (range.checkHydration(hydration, false)) {
            case VALID -> out.append(text.apply("fruit_tree.just_right", new Object[0]));
            case LOW -> out.append(text.apply("fruit_tree.hydration_too_low", new Object[] {range.getMinHydration(false)}));
            case HIGH -> out.append(text.apply("fruit_tree.hydration_too_high", new Object[] {range.getMaxHydration(false)}));
        }
        return out.toString();
    }

    /** {@code FarmlandBlock.getAverageTemperatureTooltip}: {@code fruit_tree.temperature} plus the verdict. */
    public static String temperature(BiFunction<String, Object[], String> text, ClimateRange range, float celsius, ClimateTemperatureStyle style) {
        StringBuilder out = new StringBuilder(text.apply("fruit_tree.temperature", new Object[] {style.format(celsius)}));
        switch (range.checkTemperature(celsius, false)) {
            case VALID -> out.append(text.apply("fruit_tree.just_right", new Object[0]));
            case LOW -> out.append(text.apply("fruit_tree.temperature_too_low", new Object[] {style.format(range.getMinTemperature(false))}));
            case HIGH -> out.append(text.apply("fruit_tree.temperature_too_high", new Object[] {style.format(range.getMaxTemperature(false))}));
        }
        return out.toString();
    }

    /**
     * The lines of a fruit tree block.
     *
     * @param part          the block family
     * @param range         the tree's climate
     * @param hydration     the hydration at the root below the stem (ignored for a static branch)
     * @param celsius       the average temperature at the stem (ignored for a static branch)
     * @param spliceHint    whether {@link SplicAction#maySplice} holds for the cell the block would splice (the sapling itself, or the
     *                      cell above a branch)
     * @param activeMonth   for a sapling: whether this month lets it grow
     */
    public static List<String> tree(BiFunction<String, Object[], String> text, FruitTreeDevice.Part part, ClimateRange range, int hydration,
                                    float celsius, ClimateTemperatureStyle style, boolean spliceHint, boolean activeMonth) {
        List<String> out = new ArrayList<>();
        switch (part) {
            case SAPLING -> {
                out.add(hydration(text, range, hydration));
                out.add(temperature(text, range, celsius, style));
                out.add(text.apply(activeMonth ? "fruit_tree.growing" : "fruit_tree.sapling_wrong_month", new Object[0]));
                if (spliceHint) {
                    out.add(text.apply("fruit_tree.sapling_splice", new Object[0]));
                }
            }
            case GROWING -> {
                out.add(hydration(text, range, hydration));
                out.add(temperature(text, range, celsius, style));
                if (spliceHint) {
                    out.add(text.apply("fruit_tree.sapling_splice", new Object[0]));
                }
                out.add(text.apply("fruit_tree.growing", new Object[0]));
            }
            case BRANCH -> {
                out.add(text.apply("fruit_tree.check_leaves", new Object[0]));
                if (spliceHint) {
                    out.add(text.apply("fruit_tree.sapling_splice", new Object[0]));
                }
                out.add(text.apply("fruit_tree.done_growing", new Object[0]));
            }
            case LEAVES -> {
                out.add(hydration(text, range, hydration));
                out.add(temperature(text, range, celsius, style));
            }
        }
        return out;
    }

    /** The lines of a banana plant block ({@code BananaPlantBlock.addHoeOverlayInfo}). */
    public static List<String> banana(BiFunction<String, Object[], String> text, ClimateRange range, int hydration, float celsius,
                                      ClimateTemperatureStyle style) {
        return List.of(hydration(text, range, hydration), temperature(text, range, celsius, style));
    }
}
