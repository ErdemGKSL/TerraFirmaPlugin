/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.lookinfo;

import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.module.FeatureModule;
import dev.tfp.paper.module.ModuleContext;

/** PAR-DATA-12: registers the sneak-look block readout on the action bar. Needs the device module before it. */
public final class LookInfoModule implements FeatureModule {
    public static final String ID = "lookinfo";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void enable(ModuleContext context) {
        DeviceService devices = context.services().require(DeviceService.class);
        LookInfoOverlay overlay = new LookInfoOverlay(context.scheduler(), context.integrations().customBlocks(), devices,
            context.messages(), () -> context.clock().get().playerTicks());
        context.services().provide(LookInfoOverlay.class, overlay);
        context.listen(overlay);
    }
}
