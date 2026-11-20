/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/crop/{WildCropBlock,SpreadingCropBlock,TFCPumpkinBlock}.java @ c3b4d9536
 */
package dev.tfp.paper.crop.wild;

import dev.tfp.core.food.FoodConfig;
import dev.tfp.core.world.BlockPos;
import dev.tfp.paper.catchup.CatchUpService;
import dev.tfp.paper.device.DeviceKind;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.food.FoodDefinitionsCache;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.module.FeatureModule;
import dev.tfp.paper.module.ModuleContext;
import dev.tfp.paper.tags.Tags;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.World;
import org.bukkit.block.Block;

/**
 * PAR-FARM-05: wild crops (mature by month), the pumpkin and melon fruit blocks that rot, and {@link SpreadingFruit} for the living
 * spreading crops. Registers the device kinds {@link WildCropDevice#ID} and {@link FruitDevice#ID} with a catch-up kind each, and provides
 * {@link SpreadingFruit}. The CraftEngine blocks are the generated content of the follow-up item PAR-FARM-21; until they exist the kinds are
 * registered and idle. Must be listed after the device, tags, food and catchup modules.
 */
public final class WildModule implements FeatureModule {
    public static final String ID = "crop-wild";

    private volatile DeviceKind<Long> wildKind;
    private volatile DeviceKind<FruitDevice.FruitState> fruitKind;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void enable(ModuleContext context) {
        DeviceService devices = context.services().require(DeviceService.class);
        Tags tags = context.services().require(Tags.class);
        CustomBlocks blocks = context.integrations().customBlocks();
        WildWorld world = new WildWorld(blocks, () -> context.integrations().blockVisuals(), tags,
            () -> context.services().get(CatchUpService.class));
        WildCropDevice.Services wild = new WildCropDevice.Services(world, () -> context.clock().get());
        FoodDefinitionsCache definitions = context.services().require(FoodDefinitionsCache.class);
        FruitDevice.Services fruit = new FruitDevice.Services(world, () -> context.clock().get(), definitions, tags, FoodConfig.DEFAULT);
        wildKind = devices.register(WildCropDevice.kind(wild));
        fruitKind = devices.register(FruitDevice.kind(fruit));
        context.services().provide(SpreadingFruit.class, new SpreadingFruit(fruit, () -> devices, () -> fruitKind,
            ThreadLocalRandom.current()));
        context.services().get(CatchUpService.class).ifPresent(c -> {
            c.kinds().registerDirect(WildCropDevice.CATCH_UP_KIND, (w, pos) -> catchUpWild(wild, devices, blocks, w, pos));
            c.kinds().registerDirect(FruitDevice.CATCH_UP_KIND, (w, pos) -> catchUpFruit(fruit, devices, blocks, w, pos));
        });
    }

    /** The catch-up of one listed wild crop; false when the block is gone (its index entry is dropped). */
    boolean catchUpWild(WildCropDevice.Services services, DeviceService devices, CustomBlocks blocks, World world, BlockPos pos) {
        Block block = world.getBlockAt(pos.x(), pos.y(), pos.z());
        Optional<BlockStateRef> state = blocks.stateAt(block);
        if (state.isEmpty() || WildCropDevice.type(state.get().id()).isEmpty()) {
            return false;
        }
        return WildCropDevice.settle(services, block, state.get());
    }

    /** The catch-up of one listed fruit block; false when it is no longer a fresh fruit. */
    boolean catchUpFruit(FruitDevice.Services services, DeviceService devices, CustomBlocks blocks, World world, BlockPos pos) {
        Block block = world.getBlockAt(pos.x(), pos.y(), pos.z());
        Optional<BlockStateRef> state = blocks.stateAt(block);
        if (state.isEmpty() || FruitDevice.type(state.get().id()).isEmpty() || FruitDevice.isRottenBlock(state.get().id())) {
            return false;
        }
        boolean[] fresh = {false};
        Optional<?> stored = devices.edit(block, fruitKind, (record, now) -> {
            fresh[0] = FruitDevice.settle(services, block, state.get(), record.state(), now);
            return record;
        });
        return stored.isPresent() && fresh[0];
    }
}
