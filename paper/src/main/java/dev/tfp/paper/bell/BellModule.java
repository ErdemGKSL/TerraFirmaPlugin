/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/TFCBellBlock.java @ c3b4d9536 (attemptToRing: pitch, hammer, volume 2)
 */
package dev.tfp.paper.bell;

import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.module.FeatureModule;
import dev.tfp.paper.module.ModuleContext;
import dev.tfp.paper.pump.BlockWriter;
import dev.tfp.paper.tags.Tags;

/**
 * PAR-METAL-18: the bronze and brass bells. Registers the two {@link BellDevices} kinds and the placement and projectile
 * listeners. Needs the device and tags modules before it in the service file.
 */
public final class BellModule implements FeatureModule {
    public static final String ID = "bell";
    /** TFC {@code TFCTags.Items.TOOLS_HAMMER} ({@code c:tools/hammer}). */
    public static final String HAMMERS = "c:tools/hammer";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void enable(ModuleContext ctx) {
        DeviceService devs = ctx.services().require(DeviceService.class);
        Tags tags = ctx.services().require(Tags.class);
        BellDevices bells = new BellDevices(ctx.scheduler(), ctx.integrations().customBlocks(),
            BlockWriter.visuals(() -> ctx.integrations().blockVisuals()), new PaperBellEffects(),
            stack -> tags.is(stack, HAMMERS));
        bells.kinds().forEach(devs::register);
        ctx.listen(bells.new Placement());
        ctx.listen(bells.new Projectiles());
    }
}
