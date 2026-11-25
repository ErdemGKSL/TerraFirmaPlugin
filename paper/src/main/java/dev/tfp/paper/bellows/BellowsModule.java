/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/devices/CharcoalForgeBlock.java @ c3b4d9536 (IBellowsConsumer.intakeAir)
 */
package dev.tfp.paper.bellows;

import dev.tfp.paper.charcoalforge.CharcoalForgeDevice;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.module.FeatureModule;
import dev.tfp.paper.module.ModuleContext;
import org.bukkit.block.Block;

/**
 * PAR-METAL-11: the bellows. Registers the {@link BellowsDevice} kind {@code tfp:bellows}, provides the
 * {@link BellowsConsumers} registry (the blast furnace registers there in PAR-METAL-15) and registers the charcoal forge as
 * a consumer. Needs the device and charcoal forge modules before it in the service file.
 */
public final class BellowsModule implements FeatureModule {
    public static final String ID = "bellows";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void enable(ModuleContext ctx) throws Exception {
        DeviceService devs = ctx.services().require(DeviceService.class);
        CustomBlocks blocks = ctx.integrations().customBlocks();
        BellowsConsumers consumers = new BellowsConsumers();
        ctx.services().get(CharcoalForgeDevice.class).ifPresent(forge -> consumers.register(CharcoalForgeDevice.ID,
            new BellowsConsumers.Consumer() {
                @Override
                public void intakeAir(Block block, int amount) {
                    forge.intakeAir(devs, block, amount);
                }
            }));
        BellowsDevice device = new BellowsDevice(consumers, b -> blocks.stateAt(b).map(BlockStateRef::id),
            new PaperBellowsEffects(ctx.scheduler()));
        devs.register(device.kind());
        ctx.services().provide(BellowsConsumers.class, consumers);
        ctx.services().provide(BellowsDevice.class, device);
    }
}
