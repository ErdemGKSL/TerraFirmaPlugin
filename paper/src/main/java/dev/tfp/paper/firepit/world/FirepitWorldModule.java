/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.firepit.world;

import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.firepit.FirepitDevice;
import dev.tfp.paper.grill.GrillDevice;
import dev.tfp.paper.module.FeatureModule;
import dev.tfp.paper.module.ModuleContext;
import dev.tfp.paper.pot.PotDevice;
import java.util.Set;

/**
 * PAR-HEAT-30: fuel gathering and the hot floor of the firepit, grill and pot (see {@link FirepitWorldListener}). Needs
 * the device, firepit, grill and pot modules before it in the service file.
 */
public final class FirepitWorldModule implements FeatureModule {
    public static final String ID = "firepit_world";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void enable(ModuleContext context) {
        DeviceService devices = context.services().require(DeviceService.class);
        context.listen(new FirepitWorldListener(context.scheduler(), context.integrations().customBlocks(), devices,
            Set.of(FirepitDevice.ID, GrillDevice.ID, PotDevice.ID)));
    }
}
