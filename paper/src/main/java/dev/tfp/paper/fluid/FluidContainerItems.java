/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/items/TFCItems.java @ c3b4d9536 (the FluidContainerItem registrations)
 */
package dev.tfp.paper.fluid;

import dev.tfp.core.fluid.FluidContainerKind;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * The fluid-holding items of TFC that are {@code FluidContainerItem}s (wooden, red and blue steel buckets, the ceramic
 * jug, the four glass bottles), by our item id. Their content lives in the item PDC ({@code FluidPdc}). Not here: the
 * small vessel (a {@code FluidAlloy}), the molds (their own tooltip), and TFC's per-fluid {@code bucket/<fluid>} items,
 * which are plain bucket items without a tank.
 */
public final class FluidContainerItems {
    private static final Map<String, FluidContainerKind> BY_ITEM = Map.of(
            "tfp:wooden_bucket", FluidContainerKind.WOODEN_BUCKET,
            "tfp:metal/bucket/red_steel", FluidContainerKind.RED_STEEL_BUCKET,
            "tfp:metal/bucket/blue_steel", FluidContainerKind.BLUE_STEEL_BUCKET,
            "tfp:ceramic/jug", FluidContainerKind.JUG,
            "tfp:silica_glass_bottle", FluidContainerKind.SILICA_GLASS_BOTTLE,
            "tfp:hematitic_glass_bottle", FluidContainerKind.HEMATITIC_GLASS_BOTTLE,
            "tfp:volcanic_glass_bottle", FluidContainerKind.VOLCANIC_GLASS_BOTTLE,
            "tfp:olivine_glass_bottle", FluidContainerKind.OLIVINE_GLASS_BOTTLE);

    private FluidContainerItems() {
    }

    /** The container kind of an item id, empty for any other item. */
    public static Optional<FluidContainerKind> kindOf(String itemId) {
        return itemId == null ? Optional.empty() : Optional.ofNullable(BY_ITEM.get(itemId));
    }

    /** The item ids of the containers. */
    public static Map<String, FluidContainerKind> all() {
        return BY_ITEM;
    }

    /**
     * A container named in a command: the kind's lower-case name ({@code jug}, {@code wooden_bucket},
     * {@code silica_glass_bottle}, ...), for the item kinds only.
     */
    public static Optional<FluidContainerKind> byName(String name) {
        if (name == null) {
            return Optional.empty();
        }
        String n = name.toLowerCase(Locale.ROOT);
        return BY_ITEM.values().stream().filter(k -> k.name().toLowerCase(Locale.ROOT).equals(n)).findFirst();
    }

    /** The command name of a kind. */
    public static String name(FluidContainerKind kind) {
        return kind.name().toLowerCase(Locale.ROOT);
    }
}
