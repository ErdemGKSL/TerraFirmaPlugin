/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/client/ClientForgeEventHandler.java @ c3b4d9536 (onItemTooltip: the position of
 *       the glass and egg lines),
 *       src/main/java/net/dries007/tfc/common/blocks/devices/{SealableDeviceBlock,BarrelBlock,CrucibleBlock}.java,
 *       src/main/java/net/dries007/tfc/common/items/{JavelinItem,PanItem,FlowerCuttingItem,LampBlockItem,MoldItem}.java
 *       @ c3b4d9536 (which items carry which appendHoverText)
 */
package dev.tfp.paper.tooltip.feature;

import dev.tfp.core.calendar.CalendarTicks;
import dev.tfp.core.item.feature.FeatureLine;
import dev.tfp.core.item.feature.FeatureTooltips;
import dev.tfp.core.item.feature.ItemCount;
import dev.tfp.core.vessel.MoldKind;
import dev.tfp.paper.tooltip.TooltipContext;
import dev.tfp.paper.tooltip.TooltipContributor;
import dev.tfp.paper.tooltip.TooltipItem;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Which items get which feature lines (PAR-INFRA-50), as {@link FeatureLinesContributor.Source}s over the packet
 * copy, and where they go among the other contributors. TFC adds the block and item lines in
 * {@code appendHoverText}, which a client runs before {@code ItemTooltipEvent}, so they come right under the name
 * ({@link #ORDER_HOVER}); the glass and egg lines are part of the event, after forging and after food
 * ({@link #ORDER_GLASS}, {@link #ORDER_EGG}).
 */
public final class FeatureSources {
    /** {@code appendHoverText}: after the static lore (50), before the size line (100). */
    public static final int ORDER_HOVER = 60;
    /** {@code GlassWorking.addTooltipInfo}: after forging (200), before food (300). */
    public static final int ORDER_GLASS = TooltipContributor.ORDER_FORGING + 10;
    /** {@code EggComponent.addTooltipInfo}: after food and ingredients, before heat (500). */
    public static final int ORDER_EGG = TooltipContributor.ORDER_CONTENTS + 50;

    static final String BARREL_PREFIX = "tfp:wood/barrel/";
    static final String CRUCIBLE = "tfp:crucible";
    static final String LAMP_PREFIX = "tfp:metal/lamp/";
    static final String MOLD_PREFIX = "tfp:ceramic/";
    static final String MOLD_SUFFIX = "_mold";

    /** Whether the glass on an item is hot enough to show TFC's danger line. */
    @FunctionalInterface
    public interface GlassDanger {
        boolean test(TooltipItem item, TooltipContext context);

        GlassDanger NEVER = (item, context) -> false;
    }

    private FeatureSources() {
    }

    /**
     * The item list lines: a barrel's (merged stacks and its fluid), a crucible's (header, stacks, metal), or a sealed
     * device's (large vessel, powderkeg: header and the first five stacks).
     */
    public static List<FeatureLine> contents(TooltipItem item, TooltipContext context) {
        String id = item.id();
        Optional<List<ItemCount>> contents = FeatureData.contents(item.values());
        if (id.startsWith(BARREL_PREFIX)) {
            return FeatureTooltips.barrel(contents.orElse(List.of()), FeatureData.fluid(item.values()));
        }
        if (id.equals(CRUCIBLE)) {
            return FeatureTooltips.crucible(contents.orElse(List.of()), FeatureData.fluid(item.values()));
        }
        return contents.map(FeatureTooltips::sealedDevice).orElse(List.of());
    }

    public static List<FeatureLine> javelin(TooltipItem item, TooltipContext context) {
        return FeatureTooltips.javelin(item.customId());
    }

    public static List<FeatureLine> pan(TooltipItem item, TooltipContext context) {
        return FeatureTooltips.pan(FeatureData.itemRef(item.values(), FeatureData.DEPOSIT).orElse(null));
    }

    public static List<FeatureLine> flowerCutting(TooltipItem item, TooltipContext context) {
        return FeatureTooltips.flowerCutting(FeatureData.itemRef(item.values(), FeatureData.PLANT).orElse(null));
    }

    public static List<FeatureLine> lamp(TooltipItem item, TooltipContext context) {
        if (item.customId() == null || !item.customId().startsWith(LAMP_PREFIX)) {
            return List.of();
        }
        return FeatureTooltips.lamp(FeatureData.fluid(item.values()));
    }

    public static List<FeatureLine> mold(TooltipItem item, TooltipContext context) {
        return moldKind(item.customId())
                .map(kind -> FeatureTooltips.mold(FeatureData.fluid(item.values()), kind.defaultCapacity()))
                .orElse(List.of());
    }

    /** The mold kind of {@code tfp:ceramic/<kind>_mold}. */
    static Optional<MoldKind> moldKind(String itemId) {
        if (itemId == null || !itemId.startsWith(MOLD_PREFIX) || !itemId.endsWith(MOLD_SUFFIX)) {
            return Optional.empty();
        }
        String kind = itemId.substring(MOLD_PREFIX.length(), itemId.length() - MOLD_SUFFIX.length());
        for (MoldKind k : MoldKind.values()) {
            if (k.name().toLowerCase(Locale.ROOT).equals(kind)) {
                return Optional.of(k);
            }
        }
        return Optional.empty();
    }

    /** {@code GlassWorking.addTooltipInfo} with the item's danger test. */
    public static FeatureLinesContributor.Source glass(GlassDanger danger) {
        return (item, context) -> FeatureData.glass(item.values())
                .map(g -> FeatureTooltips.glass(g.batch(), g.steps(), danger.test(item, context)))
                .orElse(List.of());
    }

    /** {@code EggComponent.addTooltipInfo} against the packet's calendar day. */
    public static List<FeatureLine> egg(TooltipItem item, TooltipContext context) {
        return FeatureData.egg(item.values())
                .map(e -> FeatureTooltips.egg(e.fertilized(), e.hatchDay(), today(context)))
                .orElse(List.of());
    }

    /** TFC {@code Calendars.CLIENT.getTotalCalendarDays()} from the packet's clock reading. */
    static long today(TooltipContext context) {
        return CalendarTicks.totalDays(Math.max(0, context.clock().calendarTicks()));
    }
}
