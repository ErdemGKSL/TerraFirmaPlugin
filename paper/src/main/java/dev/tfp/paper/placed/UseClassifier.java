/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.placed;

import dev.tfp.core.placed.PlacePolicy;
import dev.tfp.paper.device.StackCodec;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;

/**
 * Reads the {@link PlacePolicy.Item} flags of a held stack and a clicked block ("action first, hold otherwise"). Region
 * thread of the clicked block (it reads the block).
 */
public final class UseClassifier {
    /** Item tags whose right-click acts on blocks (TFC tools and buckets). */
    static final List<String> WORLD_TOOL_TAGS = List.of("minecraft:hoes", "minecraft:shovels", "minecraft:axes",
        "c:tools/knife", "c:tools/chisel", "c:tools/igniter", "c:tools/propick", "c:buckets");
    /** TFC loose rocks ({@code c:stones/loose}): a right-click places a loose rock block. */
    static final String ROCKS_TAG = "c:stones/loose";
    /** Item tags of use-duration items the client stops repeating for (javelins, shields, bows). */
    static final List<String> USE_DURATION_TAGS = List.of("c:tools/spear", "c:tools/shield", "c:tools/ranged_weapon");

    /** Vanilla items with a right-click action on a block (besides block items and tagged tools). */
    static final Set<Material> VANILLA_WORLD_ITEMS = EnumSet.of(Material.FLINT_AND_STEEL, Material.FIRE_CHARGE,
        Material.BONE_MEAL, Material.ARMOR_STAND, Material.ITEM_FRAME, Material.GLOW_ITEM_FRAME, Material.PAINTING,
        Material.LEAD, Material.END_CRYSTAL, Material.ENDER_EYE, Material.HONEYCOMB, Material.SHEARS, Material.BRUSH,
        Material.FIREWORK_ROCKET, Material.WIND_CHARGE, Material.SNOWBALL, Material.EGG, Material.ENDER_PEARL,
        Material.SPLASH_POTION, Material.LINGERING_POTION, Material.EXPERIENCE_BOTTLE, Material.FISHING_ROD);
    /** Vanilla use-duration items. */
    static final Set<Material> VANILLA_USE_DURATION = EnumSet.of(Material.BOW, Material.CROSSBOW, Material.SHIELD,
        Material.SPYGLASS, Material.TRIDENT, Material.GOAT_HORN, Material.POTION, Material.HONEY_BOTTLE,
        Material.MILK_BUCKET, Material.OMINOUS_BOTTLE);

    private final Function<ItemStack, Optional<String>> customId;
    private final Predicate<String> customBlock;
    private final BiPredicate<ItemStack, String> tagged;
    private final Predicate<Block> device;

    /**
     * @param customId    the CraftEngine id of a stack, empty for a vanilla one
     * @param customBlock whether an id names a CraftEngine block (a CraftEngine item of that id places it)
     * @param tagged      whether a stack is in an item tag
     * @param device      whether a block is a device of another kind (it takes the click)
     */
    public UseClassifier(Function<ItemStack, Optional<String>> customId, Predicate<String> customBlock,
            BiPredicate<ItemStack, String> tagged, Predicate<Block> device) {
        this.customId = Objects.requireNonNull(customId, "customId");
        this.customBlock = Objects.requireNonNull(customBlock, "customBlock");
        this.tagged = Objects.requireNonNull(tagged, "tagged");
        this.device = Objects.requireNonNull(device, "device");
    }

    @SuppressWarnings("deprecation") // Material.isInteractable, as the other right-click listeners
    public PlacePolicy.Item classify(ItemStack held, Block target) {
        boolean interactable = target != null && (target.getType().isInteractable() || device.test(target));
        if (StackCodec.isEmpty(held)) {
            return new PlacePolicy.Item(false, false, false, false, interactable);
        }
        Optional<String> id = customId.apply(held);
        Material type = held.getType();
        boolean blockItem = id.isPresent() ? customBlock.test(id.get()) : type.isBlock();
        boolean rock = tagged.test(held, ROCKS_TAG);
        boolean worldTool = WORLD_TOOL_TAGS.stream().anyMatch(t -> tagged.test(held, t))
            || (id.isEmpty() && (VANILLA_WORLD_ITEMS.contains(type) || vanillaWorldName(type.name())));
        boolean useDuration = USE_DURATION_TAGS.stream().anyMatch(t -> tagged.test(held, t))
            || (id.isEmpty() && VANILLA_USE_DURATION.contains(type)) || type.isEdible() || consumable(held);
        return new PlacePolicy.Item(blockItem, rock, worldTool, useDuration, interactable);
    }

    /** Vanilla tools, buckets, spawn eggs, boats and minecarts by name (their right-click acts on the clicked block). */
    static boolean vanillaWorldName(String name) {
        return name.endsWith("_HOE") || name.endsWith("_SHOVEL") || name.endsWith("_AXE") || name.endsWith("BUCKET")
            || name.endsWith("_SPAWN_EGG") || name.endsWith("_BOAT") || name.endsWith("_RAFT") || name.endsWith("MINECART");
    }

    /** A stack with the {@code consumable} component (CraftEngine foods, drinkable jugs): used over time. */
    @SuppressWarnings("UnstableApiUsage")
    static boolean consumable(ItemStack held) {
        try {
            return held.hasData(io.papermc.paper.datacomponent.DataComponentTypes.CONSUMABLE);
        } catch (RuntimeException | LinkageError e) {
            return false; // no data component API (test server): only the other checks apply
        }
    }
}
