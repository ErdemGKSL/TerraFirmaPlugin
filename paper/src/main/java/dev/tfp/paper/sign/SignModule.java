/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/wood/TFCStandingSignBlock.java @ c3b4d9536
 */
package dev.tfp.paper.sign;

import dev.tfp.paper.integration.BlockVisuals;
import dev.tfp.paper.module.FeatureModule;
import dev.tfp.paper.module.ModuleContext;

/** PAR-DECO-25: the sign runtime; so far the placement rotation ({@link SignPlacementListener}). */
public final class SignModule implements FeatureModule {
    public static final String ID = "sign";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void enable(ModuleContext context) {
        var integrations = context.integrations();
        context.listen(new SignPlacementListener(integrations.customBlocks(), context.scheduler(), (block, next) -> {
            BlockVisuals visuals = integrations.blockVisuals();
            visuals.blockData(next).ifPresent(d -> block.setBlockData(d, false));
        }));
    }
}
