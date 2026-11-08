/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/devices/SealableDeviceBlock.java @ c3b4d9536 (getSize HUGE, getWeight
 * HEAVY), src/main/java/net/dries007/tfc/config/ServerConfig.java @ c3b4d9536 (barrelCapacity)
 */
package dev.tfp.paper.barrel;

import dev.tfp.core.barrel.BarrelRules;
import dev.tfp.core.item.ItemSize;
import dev.tfp.core.item.ItemSizing;
import dev.tfp.core.item.ItemWeight;
import dev.tfp.core.tag.TagType;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.itemsize.ItemSizes;
import dev.tfp.paper.module.FeatureModule;
import dev.tfp.paper.module.ModuleContext;
import dev.tfp.paper.recipe.Recipes;
import dev.tfp.paper.tags.Tags;
import java.util.Optional;
import java.util.function.Predicate;
import org.bukkit.inventory.ItemStack;

/**
 * PAR-PROC-08: the barrel. Registers the {@link BarrelDevice} kind ({@code tfp:wood/barrel/<wood>}) with the device framework
 * and the barrel item's size (TFC {@code SealableDeviceBlock}: huge and heavy). Needs the tags, device, recipe and item size
 * modules before it in the service file. The CraftEngine blocks are written by datagen (follow-up PAR-PROC-32).
 */
public final class BarrelModule implements FeatureModule {
    public static final String ID = "barrel";
    /** The barrel block items. */
    static final String ITEM_PREFIX = BarrelDevice.ID + "/";

    private volatile AutoCloseable sizer;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void enable(ModuleContext context) {
        DeviceService devices = context.services().require(DeviceService.class);
        Recipes recipes = context.services().require(Recipes.class);
        Tags tags = context.services().require(Tags.class);
        Optional<ItemSizes> sizes = context.services().get(ItemSizes.class);
        var items = context.integrations().customItems();
        BarrelItemTanks tanks = new BarrelItemTanks(new BarrelItemTanks.Services(tags::itemId,
            (kind, fluid) -> kind.usableTag().map(tag -> tags.is(TagType.FLUID, fluid, tag)).orElse(true),
            id -> items.create(id, 1), items::exists));
        BarrelRecipeBook book = new BarrelRecipeBook(recipes, tanks, BarrelRules.DEFAULT_CAPACITY);
        Predicate<ItemStack> fits = sizes.<Predicate<ItemStack>>map(s -> s::fitsBarrel).orElse(s -> true);
        PaperBarrelEffects effects = new PaperBarrelEffects(context.scheduler(), context.integrations().customBlocks(),
            () -> context.integrations().blockVisuals());
        devices.register(BarrelDevice.kind(new BarrelDevice.Services(book, fits,
            stack -> tags.is(stack, BarrelDevice.RODS_WOODEN), effects), devices.stacks()));
        sizes.ifPresent(s -> sizer = s.register(id -> id.startsWith(ITEM_PREFIX),
            stack -> new ItemSizing(ItemSize.HUGE, ItemWeight.HEAVY)));
    }

    @Override
    public void disable() {
        AutoCloseable s = sizer;
        sizer = null;
        if (s != null) {
            try {
                s.close();
            } catch (Exception ignored) {
                // the registration is a list removal
            }
        }
    }
}
