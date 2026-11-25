/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.fruittree;

import dev.tfp.core.berry.FruitTreeState;
import dev.tfp.paper.banana.PaperBananaEffects;
import dev.tfp.paper.catchup.CatchUpService;
import dev.tfp.paper.device.DeviceKind;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.module.FeatureModule;
import dev.tfp.paper.module.ModuleContext;
import dev.tfp.paper.tags.Tags;
import java.util.function.Function;

/**
 * PAR-FARM-28: knife splicing of fruit tree saplings ({@link SplicListener}) and the hoe overlay lines of the fruit trees and the banana
 * ({@link SplicOverlay}). Must be listed after the fruit tree, banana, device, tags and catchup modules.
 */
public final class SplicModule implements FeatureModule {
    public static final String ID = "fruit-splice";
    static final String HOES = "minecraft:hoes";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void enable(ModuleContext ctx) {
        DeviceService devices = ctx.services().require(DeviceService.class);
        Tags tags = ctx.services().require(Tags.class);
        CustomBlocks blocks = ctx.integrations().customBlocks();
        Function<String, DeviceKind<FruitTreeState>> kinds = id -> {
            @SuppressWarnings("unchecked")
            DeviceKind<FruitTreeState> k = devices.kinds().find(id).map(x -> (DeviceKind<FruitTreeState>) x).orElse(null);
            return k;
        };
        PaperFruitTreeEffects treeFx = new PaperFruitTreeEffects(ctx.scheduler(), blocks, ctx.integrations().customItems(),
            () -> ctx.integrations().blockVisuals(), tags, () -> devices, kinds, () -> ctx.services().get(CatchUpService.class));
        PaperBananaEffects bananaFx = new PaperBananaEffects(ctx.scheduler(), blocks, ctx.integrations().customItems(),
            () -> ctx.integrations().blockVisuals(), tags, () -> devices, kinds, () -> ctx.services().get(CatchUpService.class));
        ctx.listen(new SplicListener(treeFx, ctx.integrations().customItems(), stack -> tags.is(stack, SplicListener.KNIVES)));
        ctx.listen(new SplicOverlay(ctx.scheduler(), blocks, devices, treeFx, bananaFx, ctx.messages(), () -> ctx.clock().get(),
            stack -> tags.is(stack, HOES)));
    }
}
