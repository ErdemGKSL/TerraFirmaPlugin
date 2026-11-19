/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/BarrelBlockEntity.java @ c3b4d9536 (serverTick: gatherAndConsumeItems),
 *       src/main/java/net/dries007/tfc/util/Helpers.java @ c3b4d9536 (gatherAndConsumeItems)
 */
package dev.tfp.paper.barrel.world;

import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.module.FeatureModule;
import dev.tfp.paper.module.ModuleContext;

/**
 * PAR-PROC-42: barrel world rules (redstone seal, item gathering). Needs the device and barrel modules before it in the
 * service file.
 */
public final class BarrelWorldModule implements FeatureModule {
    public static final String ID = "barrel_world";
    /** The barrel block id prefix. */
    public static final String PREFIX = "tfp:wood/barrel/";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void enable(ModuleContext context) {
        DeviceService devices = context.services().require(DeviceService.class);
        context.listen(new BarrelWorldListener(context.scheduler(), context.integrations().customBlocks(), devices, PREFIX));
    }
}
