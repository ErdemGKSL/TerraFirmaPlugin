/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/Lore.java @ c3b4d9536
 * (and the registrations that use it: common/blocks/TFCBlocks.java ROCK_BLOCKS, ROCK_DECORATIONS, ROCK_ANVILS,
 * MAGMA_BLOCKS; common/items/TFCItems.java ROCK_TOOLS, BRICKS, *_GLASS_BATCH, *_GLASS_BOTTLE;
 * common/blocks/rock/Rock.java createItemProperties and BlockType; common/blocks/rock/RockCategory.java ItemType)
 */
package dev.tfp.core.item.info;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * The fixed {@code minecraft:lore} line TFC gives some items at registration, as a rule on the item id: the rock display
 * category on rock block items, their slabs, stairs and walls, rock anvils, magma blocks and bricks (grey italic); the
 * rock category on the stone tool heads (grey italic; the finished stone tools have none); the glass type on the four
 * glass batches and bottles (aqua, red, green, dark purple, italic). Pure and thread-safe.
 */
public final class StaticLore {
    private StaticLore() {}

    /** The colours TFC's lore lines use ({@code ChatFormatting}). */
    public enum LoreColor {
        GRAY, AQUA, RED, GREEN, DARK_PURPLE
    }

    /**
     * One lore line: a message key and its colour; TFC's lore lines are all italic.
     *
     * @param key   the message key ({@code tooltip.rock_display_category.sedimentary}, {@code tooltip.glass.silica}...)
     * @param color the line's colour
     */
    public record Line(String key, LoreColor color) {
        public Line {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(color, "color");
        }
    }

    public static final String NAMESPACE = "tfp:";
    public static final String ROCK_DISPLAY_KEY = "tooltip.rock_display_category.";
    public static final String ROCK_CATEGORY_KEY = "tooltip.rock_category.";
    public static final String GLASS_KEY = "tooltip.glass.";

    /** TFC {@code Rock.BlockType} values that get a block item ({@code needsItem}: all but {@code rope_anchor}). */
    static final Set<String> ROCK_BLOCK_ITEMS = Set.of("raw", "hardened", "smooth", "cobble", "bricks", "gravel",
            "spike", "cracked_bricks", "mossy_bricks", "mossy_cobble", "chiseled", "loose", "mossy_loose",
            "pressure_plate", "button", "aqueduct");

    /** Block types with slab, stairs and wall ({@code hasVariants()} plus mossy cobble and mossy bricks). */
    static final Set<String> ROCK_DECORATED = Set.of("raw", "smooth", "cobble", "bricks", "cracked_bricks",
            "mossy_bricks", "mossy_cobble");

    /** {@code RockCategory.ItemType} values built with the default factory (the heads), which carries the lore. */
    static final Set<String> STONE_HEADS = Set.of("axe_head", "hammer_head", "hoe_head", "javelin_head", "knife_head",
            "shovel_head");

    /** TFC {@code Lore.SILICA/HEMATITIC/OLIVINE/VOLCANIC}. */
    static final Map<String, LoreColor> GLASS = Map.of(
            "silica", LoreColor.AQUA,
            "hematitic", LoreColor.RED,
            "olivine", LoreColor.GREEN,
            "volcanic", LoreColor.DARK_PURPLE);

    /** The lore line of the item {@code itemId}, empty when TFC registers it without one. */
    public static Optional<Line> of(String itemId) {
        if (itemId == null || !itemId.startsWith(NAMESPACE)) {
            return Optional.empty();
        }
        String path = itemId.substring(NAMESPACE.length());
        String[] parts = path.split("/", -1);
        if (parts.length == 3 && parts[0].equals("rock")) {
            return rock(parts[1], parts[2]);
        }
        if (parts.length == 2 && parts[0].equals("brick")) {
            return display(parts[1]);
        }
        if (parts.length == 3 && parts[0].equals("stone") && STONE_HEADS.contains(parts[1])) {
            return RockCategory.byName(parts[2])
                    .map(c -> new Line(ROCK_CATEGORY_KEY + c.serializedName(), LoreColor.GRAY));
        }
        if (parts.length == 1) {
            return glass(path);
        }
        return Optional.empty();
    }

    private static Optional<Line> rock(String type, String rockAndSuffix) {
        if (ROCK_BLOCK_ITEMS.contains(type)) {
            Optional<Line> plain = display(rockAndSuffix);
            if (plain.isPresent()) {
                return plain;
            }
        }
        if (type.equals("anvil") || type.equals("magma")) {
            // TFC registers rock anvils and magma blocks for the igneous rocks only
            return RockDisplayCategory.ofRock(rockAndSuffix)
                    .filter(c -> c.category() == RockCategory.IGNEOUS_EXTRUSIVE
                            || c.category() == RockCategory.IGNEOUS_INTRUSIVE)
                    .map(StaticLore::displayLine);
        }
        if (ROCK_DECORATED.contains(type)) {
            for (String suffix : new String[] {"_slab", "_stairs", "_wall"}) {
                if (rockAndSuffix.endsWith(suffix)) {
                    return display(rockAndSuffix.substring(0, rockAndSuffix.length() - suffix.length()));
                }
            }
        }
        return Optional.empty();
    }

    private static Optional<Line> display(String rock) {
        return RockDisplayCategory.ofRock(rock).map(StaticLore::displayLine);
    }

    private static Line displayLine(RockDisplayCategory c) {
        return new Line(ROCK_DISPLAY_KEY + c.serializedName(), LoreColor.GRAY);
    }

    private static Optional<Line> glass(String path) {
        for (String kind : new String[] {"_glass_batch", "_glass_bottle"}) {
            if (path.endsWith(kind)) {
                String type = path.substring(0, path.length() - kind.length());
                LoreColor color = GLASS.get(type);
                return color == null ? Optional.empty() : Optional.of(new Line(GLASS_KEY + type, color));
            }
        }
        return Optional.empty();
    }
}
