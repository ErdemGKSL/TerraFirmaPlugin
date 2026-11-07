/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/component/size/ItemSizeManager.java @ c3b4d9536 (get, getDefinition)
 */
package dev.tfp.paper.itemsize;

import dev.tfp.core.item.ContainerRules;
import dev.tfp.core.item.ItemSizeDefinitions;
import dev.tfp.core.item.ItemSizeLimits;
import dev.tfp.core.item.ItemSizing;
import dev.tfp.core.item.VanillaItemClasses;
import dev.tfp.core.tag.TagLookup;
import dev.tfp.paper.recipe.ItemIngredientContext;
import dev.tfp.paper.tags.TagSnapshot;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/**
 * The size and weight of item stacks (TFC {@code ItemSizeManager.get}), the shared predicate every container check
 * uses (PAR-SURV-06; {@code services().require(ItemSizes.class)}). An item gets the first {@code item_size}
 * definition that accepts its id, else the default of its class:
 * <ul>
 *   <li>a vanilla item: tool / armour by its id ({@link VanillaItemClasses}), block when it places a block;</li>
 *   <li>a CraftEngine item: tool / armour by the vanilla item it is built on (our tools and armour sit on vanilla
 *       tools and armour), block when a custom block of the same id exists (TFC's block items share the block id),
 *       else the default class.</li>
 * </ul>
 * Results are cached per id until the definitions or the tag snapshot change. Thread-safe: reads only immutable data,
 * so it serves the Netty thread (tooltips) and every region thread.
 */
public final class ItemSizes {
    private record Cache(ItemSizeDefinitions definitions, TagLookup tags, ConcurrentHashMap<String, ItemSizing> byKey) {
    }

    private final Supplier<ItemSizeDefinitions> definitions;
    private final Supplier<TagLookup> tags;
    private final Function<ItemStack, Optional<String>> customIds;
    private final Predicate<String> customBlockExists;
    private final ItemSizeLimits limits;
    private volatile Cache cache = new Cache(ItemSizeDefinitions.EMPTY, TagLookup.EMPTY, new ConcurrentHashMap<>());

    /**
     * @param definitions       the current item size definitions
     * @param tags              the current tag lookup; the per-id cache is dropped when it returns another object
     *                          ({@link #lookups})
     * @param customIds         the CraftEngine id of a stack, empty for a vanilla item
     * @param customBlockExists whether a custom block of an id exists
     * @param limits            the configured container limits
     */
    public ItemSizes(Supplier<ItemSizeDefinitions> definitions, Supplier<TagLookup> tags,
            Function<ItemStack, Optional<String>> customIds, Predicate<String> customBlockExists, ItemSizeLimits limits) {
        this.definitions = Objects.requireNonNull(definitions, "definitions");
        this.tags = Objects.requireNonNull(tags, "tags");
        this.customIds = Objects.requireNonNull(customIds, "customIds");
        this.customBlockExists = Objects.requireNonNull(customBlockExists, "customBlockExists");
        this.limits = Objects.requireNonNull(limits, "limits");
    }

    public ItemSizeLimits limits() {
        return limits;
    }

    /** The sizing of a stack; null or air reads as TFC's default (very small, very light). */
    public ItemSizing sizing(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return ItemSizing.DEFAULT;
        }
        Optional<String> custom = customIds.apply(stack);
        return sizing(custom.orElse(null), stack.getType());
    }

    /**
     * The sizing of an item by its CraftEngine id ({@code null} for a vanilla item) and the vanilla material it is (or
     * is built on).
     */
    public ItemSizing sizing(String customId, Material material) {
        String id = customId != null ? customId : material.getKey().toString();
        ItemSizing.ItemClass itemClass = customId != null ? customClass(customId, material) : vanillaClass(material);
        Cache c = current();
        String key = itemClass.ordinal() + id;
        ItemSizing known = c.byKey().get(key);
        if (known != null) {
            return known;
        }
        ItemSizing resolved = c.definitions().resolve(id, c.tags(), itemClass);
        c.byKey().put(key, resolved);
        return resolved;
    }

    private Cache current() {
        ItemSizeDefinitions d = definitions.get();
        TagLookup t = tags.get();
        Cache c = cache;
        if (c.definitions() != d || c.tags() != t) {
            c = new Cache(d, t, new ConcurrentHashMap<>());
            cache = c;
        }
        return c;
    }

    /**
     * A tag lookup over the current tag snapshot that stays the same object while the snapshot does, so the cache
     * above lives as long as the snapshot.
     */
    public static Supplier<TagLookup> lookups(Supplier<TagSnapshot> snapshots) {
        Objects.requireNonNull(snapshots, "snapshots");
        record Pinned(TagSnapshot snapshot, TagLookup lookup) {
        }
        AtomicReference<Pinned> pinned = new AtomicReference<>();
        return () -> {
            TagSnapshot s = snapshots.get();
            Pinned p = pinned.get();
            if (p == null || p.snapshot() != s) {
                p = new Pinned(s, ItemIngredientContext.lookup(s));
                pinned.set(p);
            }
            return p.lookup();
        };
    }

    /** TFC's default class of a vanilla item. */
    static ItemSizing.ItemClass vanillaClass(Material material) {
        return VanillaItemClasses.of(material.getKey().toString(), placesBlock(material));
    }

    /** The default class of a CraftEngine item (see the class comment). */
    ItemSizing.ItemClass customClass(String id, Material base) {
        ItemSizing.ItemClass byBase = VanillaItemClasses.of(base.getKey().toString(), false);
        if (byBase != ItemSizing.ItemClass.OTHER) {
            return byBase;
        }
        return customBlockExists.test(id) ? ItemSizing.ItemClass.BLOCK : ItemSizing.ItemClass.OTHER;
    }

    /** Whether the vanilla item is a {@code BlockItem} (Paper's item type), else whether the material is a block. */
    @SuppressWarnings("UnstableApiUsage")
    static boolean placesBlock(Material material) {
        try {
            var type = material.asItemType();
            if (type != null) {
                return type.hasBlockType();
            }
        } catch (RuntimeException | LinkageError e) {
            // a test server without item types: fall back to the material
        }
        return material.isBlock();
    }

    /** TFC {@code TFCChestBlockEntity.isValid}: whether a chest (chest boat, chest minecart) accepts the stack. */
    public boolean fitsChest(ItemStack stack) {
        return ContainerRules.chest(sizing(stack), limits);
    }

    /** TFC {@code TFCBundleItem.overrideOtherStackedOnMe}: whether the stack may be put into a bundle. */
    public boolean fitsBundle(ItemStack stack) {
        return ContainerRules.bundle(sizing(stack));
    }

    /** TFC {@code VesselItem.canContainItem}: whether a small vessel accepts the stack. */
    public boolean fitsSmallVessel(ItemStack stack) {
        return ContainerRules.smallVessel(sizing(stack), limits);
    }

    /** TFC {@code LargeVesselBlockEntity.isItemValid}. */
    public boolean fitsLargeVessel(ItemStack stack) {
        return ContainerRules.largeVessel(sizing(stack));
    }

    /** TFC {@code BarrelBlockEntity.isItemValid}. */
    public boolean fitsBarrel(ItemStack stack) {
        return ContainerRules.barrel(sizing(stack));
    }
}
