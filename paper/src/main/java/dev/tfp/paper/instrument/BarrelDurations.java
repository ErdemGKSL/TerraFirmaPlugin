/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/BarrelBlockEntity.java @ c3b4d9536 (getRecipe: the first sealed
 *       recipe that matches, none while an excess is held; getRecipeDuration)
 */
package dev.tfp.paper.instrument;

import dev.tfp.core.barrel.BarrelData;
import dev.tfp.core.barrel.BarrelRules;
import dev.tfp.core.tag.TagType;
import dev.tfp.paper.barrel.BarrelContents;
import dev.tfp.paper.barrel.BarrelDevice;
import dev.tfp.paper.barrel.BarrelItemTanks;
import dev.tfp.paper.barrel.BarrelRecipeBook;
import dev.tfp.paper.barrel.BarrelRecipeView;
import dev.tfp.core.device.DeviceRecord;
import dev.tfp.paper.device.StackCodec;
import dev.tfp.paper.integration.CustomItems;
import dev.tfp.paper.recipe.Recipes;
import dev.tfp.paper.tags.Tags;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import org.bukkit.inventory.ItemStack;

/**
 * PAR-CLIM-29: the duration of the sealed barrel recipe that matches a barrel record, for the clock's timer mode. It reads
 * the barrel's recipes through its own {@link BarrelRecipeBook} (built on first use over the shared recipe runtime, so no
 * barrel file is touched and the module load order does not matter). Region thread of the instrument.
 */
final class BarrelDurations implements Function<DeviceRecord<BarrelData>, Optional<Integer>> {
    private final Recipes recipes;
    private final Tags tags;
    private final CustomItems items;
    private final StackCodec stacks;
    private BarrelRecipeBook book;

    BarrelDurations(Recipes recipes, Tags tags, CustomItems items, StackCodec stacks) {
        this.recipes = recipes;
        this.tags = tags;
        this.items = items;
        this.stacks = stacks;
    }

    private BarrelRecipeBook book() {
        if (book == null) {
            BarrelItemTanks tanks = new BarrelItemTanks(new BarrelItemTanks.Services(tags::itemId,
                (kind, fluid) -> kind.usableTag().map(tag -> tags.is(TagType.FLUID, fluid, tag)).orElse(true),
                id -> items.create(id, 1), items::exists));
            book = new BarrelRecipeBook(recipes, tanks, BarrelRules.DEFAULT_CAPACITY);
        }
        return book;
    }

    @Override
    public Optional<Integer> apply(DeviceRecord<BarrelData> record) {
        List<ItemStack> excess = new ArrayList<>();
        for (int i = 0; i < BarrelDevice.EXCESS_SLOTS; i++) {
            ItemStack s = stacks.decode(record.slot(BarrelDevice.FIRST_EXCESS + i));
            if (!StackCodec.isEmpty(s)) {
                excess.add(s);
            }
        }
        BarrelContents contents = new BarrelContents(record.state().fluid(), stacks.decode(record.slot(BarrelDevice.SLOT_ITEM)),
            excess, stacks.decode(record.slot(BarrelDevice.SLOT_FLUID_CONTAINER_IN)),
            stacks.decode(record.slot(BarrelDevice.SLOT_FLUID_CONTAINER_OUT)));
        BarrelRecipeView recipe = book().session().select(contents);
        return recipe == null ? Optional.empty() : Optional.of(recipe.duration());
    }
}
