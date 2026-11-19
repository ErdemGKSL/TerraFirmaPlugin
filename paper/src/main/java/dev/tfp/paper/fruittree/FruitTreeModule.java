/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/plant/fruit/GrowingFruitTreeBranchBlock.java @ c3b4d9536,
 *       src/main/java/net/dries007/tfc/config/ServerConfig.java @ c3b4d9536 (fruitBranchGrowthTicks, fruitPickBloomDelayTicks)
 */
package dev.tfp.paper.fruittree;

import dev.tfp.core.berry.BerryRules;
import dev.tfp.core.berry.FruitTreeState;
import dev.tfp.core.world.BlockPos;
import dev.tfp.paper.catchup.CatchUpService;
import dev.tfp.paper.device.DeviceKind;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.module.FeatureModule;
import dev.tfp.paper.module.ModuleContext;
import dev.tfp.paper.tags.Tags;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.World;
import org.bukkit.block.Block;

/**
 * PAR-FARM-08: the fruit trees. Registers the device kind {@link FruitTreeDevice#ID} and the catch-up kind of the same id. The CraftEngine
 * blocks are the generated content of the follow-up item PAR-FARM-16; until they exist the kinds are registered and idle. Must be listed
 * after the device, tags and catchup modules.
 */
public final class FruitTreeModule implements FeatureModule {
    public static final String ID = "fruit-tree";

    private final Map<String, DeviceKind<FruitTreeState>> kinds = new ConcurrentHashMap<>();

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void enable(ModuleContext context) {
        DeviceService devices = context.services().require(DeviceService.class);
        Tags tags = context.services().require(Tags.class);
        CustomBlocks blocks = context.integrations().customBlocks();
        PaperFruitTreeEffects effects = new PaperFruitTreeEffects(context.scheduler(), blocks, context.integrations().customItems(),
            () -> context.integrations().blockVisuals(), tags, () -> devices, kinds::get, () -> context.services().get(CatchUpService.class));
        FruitTreeDevice.Services services = new FruitTreeDevice.Services(effects, () -> context.clock().get(), BerryRules.Settings.DEFAULT);
        for (DeviceKind<FruitTreeState> k : FruitTreeDevice.kinds(services)) {
            kinds.put(k.id(), devices.register(k));
        }
        context.services().get(CatchUpService.class).ifPresent(c -> c.kinds().registerDirect(FruitTreeDevice.CATCH_UP_KIND,
            (world, pos) -> catchUp(services, devices, blocks, world, pos)));
    }

    /** The catch-up of one listed block; false when the block is no longer one of ours (its index entry is then dropped). */
    boolean catchUp(FruitTreeDevice.Services services, DeviceService devices, CustomBlocks blocks, World world, BlockPos pos) {
        Block block = world.getBlockAt(pos.x(), pos.y(), pos.z());
        Optional<BlockStateRef> state = blocks.stateAt(block);
        Optional<FruitTreeDevice.Parsed> parsed = state.flatMap(FruitTreeDevice::parse);
        if (parsed.isEmpty() || parsed.get().part() == FruitTreeDevice.Part.BRANCH) {
            return false;
        }
        DeviceKind<FruitTreeState> kind = kinds.get(state.get().id());
        if (kind == null) {
            return false;
        }
        Optional<?> stored = devices.edit(block, kind, (record, now) -> {
            Optional<FruitTreeState> next = FruitTreeDevice.tick(services, block, parsed.get(), record.state(), now, 0);
            return next.map(record::withState).orElse(record);
        });
        return stored.isPresent();
    }
}
