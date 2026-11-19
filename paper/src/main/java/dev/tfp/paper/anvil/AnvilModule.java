/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/AnvilBlockEntity.java @ c3b4d9536 (isItemValid: the hammer and
 * flux tags)
 */
package dev.tfp.paper.anvil;

import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.module.FeatureModule;
import dev.tfp.paper.module.ModuleContext;
import dev.tfp.paper.tags.Tags;

/**
 * PAR-METAL-03: the anvil blocks. Registers the device kinds of the metal and rock anvils with the device framework (the
 * blocks are written by datagen, {@code configuration/anvil/anvil.yml}) and the {@link AnvilListener}. Needs the tags and
 * device modules before it in the service file. The forging screens (PAR-METAL-04, PAR-METAL-05) and welding
 * (PAR-METAL-06) build on the same kinds.
 */
public final class AnvilModule implements FeatureModule {
    public static final String ID = "anvil";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void enable(ModuleContext context) {
        DeviceService devices = context.services().require(DeviceService.class);
        Tags tags = context.services().require(Tags.class);
        PaperAnvilEffects effects = new PaperAnvilEffects(context.scheduler(), context.integrations().customBlocks(),
            () -> context.integrations().blockVisuals());
        AnvilDevice.Services services = new AnvilDevice.Services(stack -> tags.is(stack, AnvilDevice.HAMMERS),
            stack -> tags.is(stack, AnvilDevice.FLUXES), effects);
        devices.register(AnvilDevice.kind(AnvilDevice.METAL_ID, services));
        devices.register(AnvilDevice.kind(AnvilDevice.ROCK_ID, services));
        context.listen(new AnvilListener(context.integrations().customBlocks(), devices,
            stack -> tags.is(stack, AnvilDevice.HAMMERS), stack -> tags.is(stack, AnvilListener.CHISELS), context.scheduler(),
            effects));
    }
}
