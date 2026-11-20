/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.farm;

import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.module.FeatureModule;
import dev.tfp.paper.module.ModuleContext;
import dev.tfp.paper.tags.Tags;

/**
 * PAR-FARM-01: registers the farmland device kind and the hoe overlay. The CraftEngine farmland blocks
 * ({@code tfp:farmland/<soil>}) come from the follow-up datagen item; until they exist the kind is registered and idle, and
 * the soil listener leaves hoe clicks alone. Needs the device and tags modules before it.
 */
public final class FarmModule implements FeatureModule {
    public static final String ID = "farm";
    public static final String HOES = "minecraft:hoes";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void enable(ModuleContext context) {
        DeviceService devices = context.services().require(DeviceService.class);
        Tags tags = context.services().require(Tags.class);
        devices.register(FarmlandDevice.kind(() -> context.clock().get()));
        context.listen(new HoeOverlay(context.scheduler(), context.integrations().customBlocks(), devices, tags, context.messages(),
            () -> context.clock().get(), stack -> tags.is(stack, HOES)));
    }
}
