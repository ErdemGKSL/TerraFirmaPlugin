/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/plant/fruit/SpreadingBushBlock.java @ c3b4d9536,
 *       src/main/java/net/dries007/tfc/config/ServerConfig.java @ c3b4d9536 (berryBushGrowthTicks, fruitPickBloomDelayTicks)
 */
package dev.tfp.paper.berry.spread;

import dev.tfp.core.berry.BerryRules;
import dev.tfp.core.berry.BerryState;
import dev.tfp.core.world.BlockPos;
import dev.tfp.paper.catchup.CatchUpService;
import dev.tfp.paper.device.DeviceKind;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.module.FeatureModule;
import dev.tfp.paper.module.ModuleContext;
import dev.tfp.paper.tags.Tags;
import java.util.Optional;
import org.bukkit.World;
import org.bukkit.block.Block;

/**
 * PAR-FARM-07: the spreading bushes, canes and cranberries. Registers the device kind {@link SpreadingBushDevice#ID} and the catch-up kind
 * of the same id. The CraftEngine blocks are the generated content of the follow-up item PAR-FARM-15; until they exist the kinds are
 * registered and idle. Must be listed after the device, tags and catchup modules.
 */
public final class SpreadModule implements FeatureModule {
    public static final String ID = "berry-spread";

    private volatile DeviceKind<BerryState> kind;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void enable(ModuleContext context) {
        DeviceService devices = context.services().require(DeviceService.class);
        Tags tags = context.services().require(Tags.class);
        CustomBlocks blocks = context.integrations().customBlocks();
        PaperSpreadEffects effects = new PaperSpreadEffects(context.scheduler(), blocks, context.integrations().customItems(),
            () -> context.integrations().blockVisuals(), tags, () -> devices, () -> kind, () -> context.services().get(CatchUpService.class));
        SpreadingBushDevice.Services services = new SpreadingBushDevice.Services(effects, () -> context.clock().get(),
            BerryRules.Settings.DEFAULT);
        kind = devices.register(SpreadingBushDevice.kind(services));
        context.services().get(CatchUpService.class).ifPresent(c -> c.kinds().registerDirect(SpreadingBushDevice.CATCH_UP_KIND,
            (world, pos) -> catchUp(services, devices, blocks, world, pos)));
    }

    /** The catch-up of one listed block; false when the block is no longer one of ours (its index entry is then dropped). */
    boolean catchUp(SpreadingBushDevice.Services services, DeviceService devices, CustomBlocks blocks, World world, BlockPos pos) {
        Block block = world.getBlockAt(pos.x(), pos.y(), pos.z());
        Optional<BlockStateRef> state = blocks.stateAt(block);
        Optional<SpreadingBushDevice.Parsed> parsed = state.flatMap(SpreadingBushDevice::parse);
        if (parsed.isEmpty()) {
            return false;
        }
        Optional<?> stored = devices.edit(block, kind, (record, now) -> {
            Optional<BerryState> next = SpreadingBushDevice.tick(services, block, parsed.get(), record.state(), now, 0);
            return next.map(record::withState).orElse(record);
        });
        return stored.isPresent();
    }
}
