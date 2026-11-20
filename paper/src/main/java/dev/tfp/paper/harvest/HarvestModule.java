/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.harvest;

import dev.tfp.core.gather.HeldTool;
import dev.tfp.core.gather.ToolTag;
import dev.tfp.core.tag.TagType;
import dev.tfp.paper.loot.LootService;
import dev.tfp.paper.module.FeatureModule;
import dev.tfp.paper.module.ModuleContext;
import dev.tfp.paper.tags.Tags;
import java.util.EnumSet;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;

/** PAR-GATHER-02: harvest loot of plants, leaves, twigs and fallen leaves. Needs {@code TagsModule} (and optionally LootModule) before it. */
public final class HarvestModule implements FeatureModule {
    public static final String ID = "harvest";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void enable(ModuleContext context) {
        Tags tags = context.services().require(Tags.class);
        context.listen(new HarvestListener(context.integrations().customBlocks(), context.integrations().customItems(),
                stack -> toolOf(tags, stack),
                id -> context.services().get(LootService.class).map(s -> s.handlesBlock(id)).orElse(false),
                () -> ThreadLocalRandom.current().nextDouble()));
    }

    /** The loot tool tags of a held stack: {@code #tfc:tools/sharp}, {@code #c:tools/shear}, {@code #minecraft:axes}, silk touch. */
    static HeldTool toolOf(Tags tags, ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return HeldTool.NONE;
        }
        EnumSet<ToolTag> set = EnumSet.noneOf(ToolTag.class);
        String id = tags.itemId(stack).orElse(null);
        if (id != null) {
            if (tags.is(TagType.ITEM, id, "tfc:tools/sharp")) {
                set.add(ToolTag.SHARP);
            }
            if (tags.is(TagType.ITEM, id, "c:tools/shear")) {
                set.add(ToolTag.SHEAR);
            }
            if (tags.is(TagType.ITEM, id, "minecraft:axes")) {
                set.add(ToolTag.AXE);
            }
        }
        if (stack.getEnchantmentLevel(Enchantment.SILK_TOUCH) > 0) {
            set.add(ToolTag.SILK_TOUCH);
        }
        return new HeldTool(set);
    }
}
