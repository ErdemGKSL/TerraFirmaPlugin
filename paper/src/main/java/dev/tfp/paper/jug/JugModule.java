/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.jug;

import dev.tfp.core.bucket.WorldFluid;
import dev.tfp.core.tag.TagType;
import dev.tfp.paper.module.FeatureModule;
import dev.tfp.paper.module.ModuleContext;
import dev.tfp.paper.player.ThirstModule;
import dev.tfp.paper.tags.Tags;
import dev.tfp.paper.worldwater.WorldWaterService;
import dev.tfp.paper.worldwater.hot.SpringWater;
import java.util.function.Function;
import java.util.function.Predicate;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;

/**
 * PAR-POT-07: the jug and glass bottle interactions ({@link JugListener}). Listed after {@code tags}, {@code worldwater}
 * and the hot spring module, whose services it uses when present.
 */
public final class JugModule implements FeatureModule {
    public static final String ID = "jug";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void enable(ModuleContext context) {
        Tags tags = context.services().require(Tags.class);
        Function<Block, String> water = context.services().get(WorldWaterService.class)
            .<Function<Block, String>>map(w -> block -> w.kindOf(block).containerFluid())
            .orElse(JugModule::biomeWaterFluid);
        Predicate<Block> spring = block -> context.services().get(SpringWater.class)
            .map(s -> s.isSpringWater(block)).orElse(false);
        context.listen(new JugListener(tags::itemId,
            (kind, fluid) -> kind.usableTag().map(tag -> tags.is(TagType.FLUID, fluid, tag)).orElse(true),
            water, spring, context.playerData(), () -> context.clock().get().playerTicks(), context.hudCache(),
            ThirstModule::drinkables, player -> { }));
    }

    /** The fallback water rule without the worldwater module: salt water in a vanilla ocean biome. */
    @SuppressWarnings("deprecation")
    static String biomeWaterFluid(Block block) {
        Biome biome = block.getBiome();
        return WorldFluid.WATER.pickedUp(biome != null && biome.getKey().getKey().contains("ocean"));
    }
}
