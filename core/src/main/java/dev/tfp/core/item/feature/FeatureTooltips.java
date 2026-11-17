/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/util/tooltip/Tooltips.java @ c3b4d9536 (contents, countOfItem, fluidUnitsOf,
 *       fluidUnitsAndCapacityOf),
 *       src/main/java/net/dries007/tfc/util/Helpers.java @ c3b4d9536 (addInventoryTooltipInfo),
 *       src/main/java/net/dries007/tfc/common/component/item/ItemListComponent.java @ c3b4d9536 (addTooltipInfo),
 *       src/main/java/net/dries007/tfc/common/blocks/devices/SealableDeviceBlock.java @ c3b4d9536 (appendHoverText),
 *       src/main/java/net/dries007/tfc/common/blocks/devices/BarrelBlock.java @ c3b4d9536 (appendHoverText),
 *       src/main/java/net/dries007/tfc/common/blocks/devices/CrucibleBlock.java @ c3b4d9536 (appendHoverText),
 *       src/main/java/net/dries007/tfc/common/component/EggComponent.java @ c3b4d9536 (addTooltipInfo),
 *       src/main/java/net/dries007/tfc/common/items/JavelinItem.java @ c3b4d9536 (appendHoverText),
 *       src/main/java/net/dries007/tfc/common/items/PanItem.java @ c3b4d9536 (appendHoverText),
 *       src/main/java/net/dries007/tfc/common/items/FlowerCuttingItem.java @ c3b4d9536 (appendHoverText),
 *       src/main/java/net/dries007/tfc/common/items/LampBlockItem.java @ c3b4d9536 (appendHoverText),
 *       src/main/java/net/dries007/tfc/common/items/MoldItem.java @ c3b4d9536 (appendHoverText),
 *       src/main/java/net/dries007/tfc/common/component/glass/GlassWorking.java @ c3b4d9536 (addTooltipInfo)
 */
package dev.tfp.core.item.feature;

import dev.tfp.core.hunt.HuntJavelin;
import dev.tfp.core.item.feature.FeatureLine.LineColor;
import dev.tfp.core.vessel.FluidAmount;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The per-feature tooltip lines of TFC that no item feature draws itself (PAR-INFRA-50), as pure builders over
 * immutable inputs: TFC's keys, arguments, colours and order. The platform side reads the item data and renders the
 * returned {@link FeatureLine}s in the viewer's language. Every method returns an empty list where TFC adds nothing.
 * Any thread.
 */
public final class FeatureTooltips {
    public static final String CONTENTS = "tfc.tooltip.contents";
    public static final String SMALL_VESSEL_CONTENTS = "tfc.tooltip.small_vessel.contents";
    /** Vanilla's key; the client translates it. */
    public static final String MORE = "container.shulkerBox.more";
    public static final String FLUID_UNITS_OF = "tfc.tooltip.fluid_units_of";
    public static final String FLUID_UNITS_AND_CAPACITY_OF = "tfc.tooltip.fluid_units_and_capacity_of";
    public static final String FERTILIZED = "tfc.tooltip.fertilized";
    public static final String EGG_HATCH = "tfc.tooltip.egg_hatch";
    public static final String EGG_HATCH_TODAY = "tfc.tooltip.egg_hatch_today";
    public static final String THROWN_DAMAGE = "tfc.tooltip.javelin.thrown_damage";
    public static final String PAN_CONTENTS = "tfc.tooltip.pan.contents";
    public static final String FLOWER_CUTTING = "tfc.tooltip.flower_cutting";
    public static final String GLASS_TITLE = "tfc.tooltip.glass.title";
    public static final String DANGER = "tfc.tooltip.danger";
    /** {@code Util.makeDescriptionId("glass_operation", id)} of a {@code tfc:} operation. */
    public static final String GLASS_OPERATION_PREFIX = "glass_operation.tfc.";

    /** {@code Helpers.addInventoryTooltipInfo}: {@code maximumItems <= 4} lets five stacks through. */
    static final int INVENTORY_LINES = 5;
    /** {@code GlassWorking}: the danger line shows above the recipe temperature times this. */
    static final float GLASS_DANGER_FACTOR = 0.8f;

    private FeatureTooltips() {
    }

    /** {@code Tooltips.countOfItem}: {@code "<count> x <name>"}. */
    public static FeatureLine countOfItem(String itemId, int count) {
        return FeatureLine.of(LinePart.literal(count), new LinePart.Literal(" x "), new LinePart.ItemName(itemId));
    }

    /** {@code Tooltips.fluidUnitsOf}: {@code "<amount> mB of <fluid>"}. */
    public static FeatureLine fluidUnitsOf(FluidAmount fluid) {
        return FeatureLine.of(LinePart.key(FLUID_UNITS_OF, LinePart.literal(fluid.amount()),
                new LinePart.FluidName(fluid.fluid())));
    }

    /** {@code Helpers.addInventoryTooltipInfo}: the first five stacks, then {@code "and %s more..."} in italics. */
    public static List<FeatureLine> inventory(List<ItemCount> contents) {
        List<FeatureLine> out = new ArrayList<>();
        int shown = 0;
        int total = 0;
        for (ItemCount stack : contents) {
            ++total;
            if (shown < INVENTORY_LINES) {
                ++shown;
                out.add(countOfItem(stack.itemId(), stack.count()));
            }
        }
        if (total - shown > 0) {
            out.add(FeatureLine.of(LinePart.key(MORE, LinePart.literal(total - shown))).inItalics());
        }
        return out;
    }

    /** {@code ItemListComponent.addTooltipInfo} (TFC's {@code INGREDIENTS}): one grey count line per stack. */
    public static List<FeatureLine> ingredients(List<ItemCount> contents) {
        List<FeatureLine> out = new ArrayList<>(contents.size());
        for (ItemCount stack : contents) {
            out.add(countOfItem(stack.itemId(), stack.count()).colored(LineColor.GRAY));
        }
        return out;
    }

    /** {@code SealableDeviceBlock.appendHoverText} (large vessel, powderkeg): the dark green header and the inventory. */
    public static List<FeatureLine> sealedDevice(List<ItemCount> contents) {
        if (contents.isEmpty()) {
            return List.of();
        }
        List<FeatureLine> out = new ArrayList<>();
        out.add(FeatureLine.of(LinePart.key(CONTENTS)).colored(LineColor.DARK_GREEN));
        out.addAll(inventory(contents));
        return out;
    }

    /**
     * {@code BarrelBlock.appendHoverText}: the header, the stacks merged by item identity in first-seen order (so
     * {@code "154 x Mortar"} for an excess of several stacks), then the fluid.
     */
    public static List<FeatureLine> barrel(List<ItemCount> contents, FluidAmount fluid) {
        boolean noFluid = fluid == null || fluid.isEmpty();
        if (contents.isEmpty() && noFluid) {
            return List.of();
        }
        List<FeatureLine> out = new ArrayList<>();
        out.add(FeatureLine.of(LinePart.key(CONTENTS)).colored(LineColor.DARK_GREEN));
        Map<String, int[]> merged = new LinkedHashMap<>();
        Map<String, String> ids = new LinkedHashMap<>();
        for (ItemCount stack : contents) {
            merged.computeIfAbsent(stack.identity(), k -> new int[1])[0] += stack.count();
            ids.putIfAbsent(stack.identity(), stack.itemId());
        }
        merged.forEach((identity, count) -> out.add(countOfItem(ids.get(identity), count[0])));
        if (!noFluid) {
            out.add(fluidUnitsOf(fluid));
        }
        return out;
    }

    /** {@code CrucibleBlock.appendHoverText}: the dark green header and the inventory when it holds items, then the metal. */
    public static List<FeatureLine> crucible(List<ItemCount> contents, FluidAmount metal) {
        boolean noMetal = metal == null || metal.isEmpty();
        if (contents.isEmpty() && noMetal) {
            return List.of();
        }
        List<FeatureLine> out = new ArrayList<>();
        // TFC writes the header whenever the component is not empty, even with no items
        out.add(FeatureLine.of(LinePart.key(SMALL_VESSEL_CONTENTS)).colored(LineColor.DARK_GREEN));
        out.addAll(inventory(contents));
        if (!noMetal) {
            out.add(fluidUnitsOf(metal));
        }
        return out;
    }

    /**
     * {@code EggComponent.addTooltipInfo}: {@code "Fertilized"} (gold in TFC's lang) then the days until
     * {@code hatchDay}, or {@code "Will hatch today!"} when it is due.
     *
     * @param today TFC {@code Calendars.CLIENT.getTotalCalendarDays()}
     */
    public static List<FeatureLine> egg(boolean fertilized, long hatchDay, long today) {
        if (!fertilized) {
            return List.of();
        }
        long remainingDays = hatchDay - today;
        FeatureLine second = remainingDays > 0
                ? FeatureLine.of(LinePart.key(EGG_HATCH, LinePart.literal(remainingDays)))
                : FeatureLine.of(LinePart.key(EGG_HATCH_TODAY));
        return List.of(FeatureLine.of(LinePart.key(FERTILIZED)), second);
    }

    /** {@code JavelinItem.appendHoverText}: dark green {@code "<damage> Thrown Damage"}, the damage as {@code %.0f}. */
    public static List<FeatureLine> javelin(float attackDamageBonus) {
        String damage = String.format(Locale.ROOT, "%.0f", HuntJavelin.thrownDamage(attackDamageBonus));
        return List.of(FeatureLine.of(LinePart.key(THROWN_DAMAGE, new LinePart.Literal(damage)))
                .colored(LineColor.DARK_GREEN));
    }

    /** As {@link #javelin(float)} for the javelin item {@code itemId}; empty for any other item. */
    public static List<FeatureLine> javelin(String itemId) {
        return JavelinTiers.attackDamageBonus(itemId).map(FeatureTooltips::javelin).orElse(List.of());
    }

    /** {@code PanItem.appendHoverText}: {@code "Contains "} followed by the deposit's name. */
    public static List<FeatureLine> pan(String depositItemId) {
        if (depositItemId == null || depositItemId.isEmpty()) {
            return List.of();
        }
        return List.of(FeatureLine.of(LinePart.key(PAN_CONTENTS), new LinePart.ItemName(depositItemId)));
    }

    /** {@code FlowerCuttingItem.appendHoverText}: {@code "Cutting of <plant>"}. */
    public static List<FeatureLine> flowerCutting(String plantItemId) {
        if (plantItemId == null || plantItemId.isEmpty()) {
            return List.of();
        }
        return List.of(FeatureLine.of(LinePart.key(FLOWER_CUTTING, new LinePart.ItemName(plantItemId))));
    }

    /** {@code LampBlockItem.appendHoverText}: the fuel as {@code "<amount> mB of <fluid>"}. */
    public static List<FeatureLine> lamp(FluidAmount fuel) {
        return fuel == null || fuel.isEmpty() ? List.of() : List.of(fluidUnitsOf(fuel));
    }

    /** {@code MoldItem.appendHoverText}: {@code "<amount> / <capacity> mB of <metal>"}. */
    public static List<FeatureLine> mold(FluidAmount metal, int capacity) {
        if (metal == null || metal.isEmpty()) {
            return List.of();
        }
        return List.of(FeatureLine.of(LinePart.key(FLUID_UNITS_AND_CAPACITY_OF, LinePart.literal(metal.amount()),
                LinePart.literal(capacity), new LinePart.FluidName(metal.fluid()))));
    }

    /**
     * {@code GlassWorking.addTooltipInfo}: the batch's name, the aqua {@code "Glass Operations:"} title and
     * {@code "- <operation>"} per step when there are steps, then {@code " - Danger!!"} when {@code danger}.
     *
     * @param batchItemId the batch item, such as {@code tfp:silica_glass_batch}; {@code null} or empty = no glass data
     * @param steps       the operation paths in order, such as {@code blow}
     * @param danger      {@link #glassDanger}
     */
    public static List<FeatureLine> glass(String batchItemId, List<String> steps, boolean danger) {
        if (batchItemId == null || batchItemId.isEmpty()) {
            return List.of();
        }
        List<FeatureLine> out = new ArrayList<>();
        out.add(FeatureLine.of(new LinePart.ItemName(batchItemId)));
        if (!steps.isEmpty()) {
            out.add(FeatureLine.of(LinePart.key(GLASS_TITLE)).colored(LineColor.AQUA));
            for (String step : steps) {
                out.add(FeatureLine.of(new LinePart.Literal("- "), LinePart.key(GLASS_OPERATION_PREFIX + step)));
            }
        }
        if (danger) {
            out.add(FeatureLine.of(LinePart.key(DANGER)));
        }
        return out;
    }

    /**
     * {@code GlassWorking}: the danger line shows when the item has a heating recipe and
     * {@code temperature / 0.8 > recipe temperature}.
     *
     * @param recipeTemperature the heating recipe's temperature, or {@code NaN} without a recipe
     */
    public static boolean glassDanger(float temperature, float recipeTemperature) {
        return !Float.isNaN(recipeTemperature) && temperature / GLASS_DANGER_FACTOR > recipeTemperature;
    }
}
