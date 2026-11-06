/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/recipes/ScrapingRecipe.java @ c3b4d9536 (getRecipe, assemble, getExtraDrop)
 */
package dev.tfp.paper.scraping;

import dev.tfp.core.json.SchemaRecord;
import dev.tfp.core.recipe.output.ItemStackProvider;
import dev.tfp.core.tag.TagType;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.device.StackCodec;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.module.FeatureModule;
import dev.tfp.paper.module.ModuleContext;
import dev.tfp.paper.neighbour.NeighbourService;
import dev.tfp.paper.recipe.RecipeTypes;
import dev.tfp.paper.recipe.Recipes;
import dev.tfp.paper.scheduler.Scheduler;
import dev.tfp.paper.tags.Tags;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalInt;
import org.bukkit.DyeColor;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * PAR-PROC-05: scraping hides and paper on a log. Registers the {@link ScrapingDevice} kind with the device framework, the
 * placement listener ({@link ScrapingListener}) and the support rule ({@link ScrapingSupport}) with the neighbour runner.
 * Needs the device, tags, recipes and neighbour modules before it in the service file.
 */
public final class ScrapingModule implements FeatureModule {
    public static final String ID = "scraping";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void enable(ModuleContext context) {
        DeviceService devices = context.services().require(DeviceService.class);
        Recipes recipes = context.services().require(Recipes.class);
        Tags tags = context.services().require(Tags.class);
        CustomBlocks blocks = context.integrations().customBlocks();
        ScrapingDevice.Services services = new ScrapingDevice.Services(stack -> recipe(recipes, stack),
            stack -> tags.is(stack, ScrapingDevice.WAXES), stack -> tags.is(stack, ScrapingDevice.KNIVES),
            stack -> dye(tags, stack), new PaperScrapingEffects());
        devices.register(ScrapingDevice.kind(services));
        context.services().get(NeighbourService.class).ifPresent(n -> n.register(
            new ScrapingSupport(id -> tags.is(TagType.BLOCK, id, ScrapingDevice.SURFACE))));
        context.listen(new ScrapingListener(placer(devices, blocks, tags, recipes, context.scheduler())));
    }

    /** The scraping recipe of {@code stack} on the recipe runtime, empty when it has none. Any thread. */
    static Optional<ScrapingDevice.Recipe> recipe(Recipes recipes, ItemStack stack) {
        if (StackCodec.isEmpty(stack)) {
            return Optional.empty();
        }
        return recipes.first(RecipeTypes.SCRAPING, stack).map(entry -> {
            SchemaRecord r = entry.recipe();
            ItemStackProvider result = r.require("result", ItemStackProvider.class);
            Optional<ItemStackProvider> extra = r.get("result_item", ItemStackProvider.class);
            return new ScrapingDevice.Recipe(r.require("input_texture", String.class),
                in -> recipes.singleOutput(result, in),
                in -> extra.map(p -> recipes.singleOutput(p, in)).orElse(ItemStack.empty()));
        });
    }

    /** TFC {@code DyeColor.getColor(stack)}: the vanilla dye item of a colour, or an item in {@code c:dyes/<colour>}. */
    static OptionalInt dye(Tags tags, ItemStack stack) {
        Optional<String> id = tags.itemId(stack);
        if (id.isEmpty()) {
            return OptionalInt.empty();
        }
        for (DyeColor color : DyeColor.values()) {
            String name = color.name().toLowerCase(Locale.ROOT);
            if (id.get().equals("minecraft:" + name + "_dye") || tags.is(TagType.ITEM, id.get(), "c:dyes/" + name)) {
                return OptionalInt.of(color.ordinal());
            }
        }
        return OptionalInt.empty();
    }

    static ScrapingListener.Placer placer(DeviceService devices, CustomBlocks blocks, Tags tags, Recipes recipes,
            Scheduler scheduler) {
        return new ScrapingListener.Placer() {
            @Override
            public boolean scrapable(ItemStack item) {
                return recipe(recipes, item).isPresent();
            }

            @Override
            public boolean surface(Block block) {
                return tags.is(block, ScrapingDevice.SURFACE);
            }

            @Override
            public boolean owns(Block block) {
                return scheduler.isOwner(block.getWorld(), block.getX() >> 4, block.getZ() >> 4);
            }

            @Override
            public boolean place(Block block, BlockStateRef state) {
                return blocks.place(block, state, true);
            }

            @Override
            public void placed(Block block, Player player, EquipmentSlot hand, ItemStack one) {
                devices.placed(block, player, hand, one);
            }

            @Override
            public boolean holds(Block block) {
                return devices.inspect(block).flatMap(DeviceService.Inspection::record)
                    .map(r -> !r.slot(ScrapingDevice.SLOT).isEmpty()).orElse(false);
            }
        };
    }
}
