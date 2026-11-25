/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/devices/StoveBlock.java @ c3b4d9536,
 *       src/main/java/net/dries007/tfc/common/blocks/devices/StovePotBlock.java @ c3b4d9536
 */
package dev.tfp.paper.stove;

import dev.tfp.paper.module.FeatureModule;
import dev.tfp.paper.module.ModuleContext;

/**
 * PAR-HEAT-31: the stove's own rules (a grill is refused). The stove and the stove pot are the firepit and pot devices
 * registered for {@code tfp:stove} and {@code tfp:stove_pot} by {@code FirepitModule} and {@code PotModule}.
 */
public final class StoveModule implements FeatureModule {
    public static final String ID = "stove";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void enable(ModuleContext context) {
        context.listen(new StoveListener(context.integrations().customBlocks(), context.integrations().customItems(),
            context.scheduler()));
    }
}
