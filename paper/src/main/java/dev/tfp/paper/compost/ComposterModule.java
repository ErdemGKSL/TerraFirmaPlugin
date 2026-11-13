/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/ComposterBlockEntity.java @ c3b4d9536 (getCompost: the tag
 * order and FoodCapability.isRotten; getReadyTicks: Climate.getAverageRainfall and the horizontal composter neighbours),
 * file: src/main/java/net/dries007/tfc/common/TFCTags.java @ c3b4d9536 (COMPOST_POISONS, COMPOST_BROWNS_*, COMPOST_GREENS_*),
 * file: src/main/java/net/dries007/tfc/config/ServerConfig.java @ c3b4d9536 (composterTicks 288000,
 * composterEnableAutomation true)
 */
package dev.tfp.paper.compost;

import dev.tfp.core.compost.CompostAddition;
import dev.tfp.core.compost.CompostRules;
import dev.tfp.paper.climate.ChunkClimateSource;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.food.ItemFoodHooks;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.integration.CustomItems;
import dev.tfp.paper.module.FeatureModule;
import dev.tfp.paper.module.ModuleContext;
import dev.tfp.paper.tags.Tags;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.logging.Level;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.inventory.ItemStack;

/**
 * PAR-PROC-11: the composter. Registers the {@link ComposterDevice} kind {@code tfp:composter} with the device framework.
 * The CraftEngine block (properties {@code stage} 0..8 and {@code type}) is written by datagen in PAR-PROC-34; until it
 * exists the kind is registered and idle. Needs the tags and device modules before it in the service file (food is
 * optional: without it no stack counts as rotten).
 */
public final class ComposterModule implements FeatureModule {
    public static final String ID = "composter";

    /** TFC's composter tags, in TFC's {@code getCompost} order. */
    static final Map<CompostAddition.Tier, String> TAGS = Map.of(
        CompostAddition.Tier.POISON, "tfc:compost_poisons",
        CompostAddition.Tier.BROWN_LOW, "tfc:compost_browns/low",
        CompostAddition.Tier.BROWN_MEDIUM, "tfc:compost_browns/medium",
        CompostAddition.Tier.BROWN_HIGH, "tfc:compost_browns/high",
        CompostAddition.Tier.GREEN_LOW, "tfc:compost_greens/low",
        CompostAddition.Tier.GREEN_MEDIUM, "tfc:compost_greens/medium",
        CompostAddition.Tier.GREEN_HIGH, "tfc:compost_greens/high");

    static final List<BlockFace> HORIZONTAL = List.of(BlockFace.NORTH, BlockFace.SOUTH, BlockFace.WEST, BlockFace.EAST);

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void enable(ModuleContext context) {
        DeviceService devices = context.services().require(DeviceService.class);
        Tags tags = context.services().require(Tags.class);
        Optional<ItemFoodHooks> food = context.services().get(ItemFoodHooks.class);
        Predicate<ItemStack> rotten = food.<Predicate<ItemStack>>map(f -> f::isRotten).orElse(s -> false);
        ComposterLang lang = ComposterLang.fromJar(context.plugin().getClass().getClassLoader());
        context.scheduler().io(() -> {
            try {
                lang.load(ComposterLang.FALLBACK);
            } catch (RuntimeException e) {
                context.logger().log(Level.WARNING, "composter: cannot load the composter messages", e);
            }
        });
        CustomBlocks blocks = context.integrations().customBlocks();
        CustomItems items = context.integrations().customItems();
        PaperComposterEffects effects = new PaperComposterEffects(context.scheduler(), blocks,
            () -> context.integrations().blockVisuals(), devices::neighbourChanged, lang);
        devices.register(ComposterDevice.kind(new ComposterDevice.Services(
            stack -> addition(stack, tags::is, rotten),
            block -> readyTicks(block, blocks),
            n -> items.create(ComposterDevice.COMPOST, n).orElse(ItemStack.empty()),
            n -> items.create(ComposterDevice.ROTTEN_COMPOST, n).orElse(ItemStack.empty()),
            true, effects)));
    }

    /** TFC {@code getCompost}: the tiers the stack is in, then {@link CompostAddition#classify}. Any thread. */
    static CompostAddition addition(ItemStack stack, BiPredicate<ItemStack, String> inTag, Predicate<ItemStack> rotten) {
        Set<CompostAddition.Tier> tiers = EnumSet.noneOf(CompostAddition.Tier.class);
        for (CompostAddition.Tier t : CompostAddition.Tier.values()) {
            if (inTag.test(stack, TAGS.get(t))) {
                tiers.add(t);
            }
        }
        return tiers.isEmpty() ? CompostAddition.NOTHING : CompostAddition.classify(tiers, rotten.test(stack));
    }

    /** TFC {@code getReadyTicks} at {@code block}: the level's average rainfall there and the horizontal composters. */
    static long readyTicks(Block block, CustomBlocks blocks) {
        float rainfall = ChunkClimateSource.climateAt(block.getWorld().getSeed(), block.getX(), block.getZ()).averageRainfall();
        return CompostRules.readyTicks(CompostRules.DEFAULT_READY_TICKS, rainfall, neighbours(block, blocks));
    }

    /** The composters on the four horizontal sides of {@code block}. Region thread. */
    static int neighbours(Block block, CustomBlocks blocks) {
        int n = 0;
        for (BlockFace f : HORIZONTAL) {
            if (blocks.stateAt(block.getRelative(f)).map(s -> s.id().equals(ComposterDevice.ID)).orElse(false)) {
                n++;
            }
        }
        return n;
    }
}
