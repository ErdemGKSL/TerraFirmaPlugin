/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/ForgeEventHandler.java @ c3b4d9536 (onProjectileImpact, onFireStop),
 *       src/main/java/net/dries007/tfc/util/events/DouseFireEvent.java @ c3b4d9536,
 *       src/main/java/net/dries007/tfc/config/ServerConfig.java @ c3b4d9536 (enableFireArrowSpreading)
 */
package dev.tfp.paper.fire.spread;

import dev.tfp.paper.fire.FireStarting;
import dev.tfp.paper.module.FeatureModule;
import dev.tfp.paper.module.ModuleContext;
import org.bukkit.inventory.ItemStack;

/**
 * PAR-HEAT-13: fire arrows and fireballs start fires, water potions douse. The switch is TFC's
 * {@code enableFireArrowSpreading} (default on), read here from the JVM property {@code tfp.fire.arrowSpreading}
 * (ledger divergence: no config.yml key yet). Needs the fire module before it in the service file.
 */
public final class FireSpreadModule implements FeatureModule {
    public static final String ID = "fire-spread";
    /** JVM property of {@code enableFireArrowSpreading}. */
    public static final String ARROW_PROPERTY = "tfp.fire.arrowSpreading";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void enable(ModuleContext context) {
        FireStarting fire = context.services().require(FireStarting.class);
        context.listen(new FireSpreadListener(context.scheduler(),
            () -> Boolean.parseBoolean(System.getProperty(ARROW_PROPERTY, "true")),
            (block, face) -> fire.start(block, face, null, ItemStack.empty(),
                FireStarting.Starter.fixed(dev.tfp.core.fuel.FireStart.Strength.STRONG, 1.0), r -> { })));
    }
}
