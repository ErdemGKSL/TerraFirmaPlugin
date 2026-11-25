/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 */
package dev.tfp.paper.crop;

import dev.tfp.core.crop.CropPlant;
import dev.tfp.core.world.BlockPos;
import dev.tfp.paper.catchup.CatchUpService;
import dev.tfp.paper.crop.wild.SpreadingFruit;
import dev.tfp.paper.device.DeviceKind;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.loot.LootService;
import dev.tfp.paper.module.FeatureModule;
import dev.tfp.paper.module.ModuleContext;
import dev.tfp.paper.tags.Tags;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.World;
import org.bukkit.block.Block;

/**
 * PAR-FARM-04: the living crops. Registers the device kind {@link CropDevice#ID}, the catch-up kind of the same id (a listed crop is brought
 * up to the calendar on chunk load and in the visible-area sweep) and the plant/break listener. Must be listed after the device, tags,
 * loot, catch-up and farm modules.
 */
public final class CropModule implements FeatureModule {
    public static final String ID = "crop";

    private volatile DeviceKind<CropPlant> kind;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void enable(ModuleContext context) {
        DeviceService devices = context.services().require(DeviceService.class);
        Tags tags = context.services().require(Tags.class);
        CustomBlocks blocks = context.integrations().customBlocks();
        CropWorld world = new CropWorld(context.scheduler(), blocks, context.integrations().customItems(),
            () -> context.integrations().blockVisuals(), tags, () -> devices, () -> context.services().get(CatchUpService.class));
        // the crop_wild module (listed before this one) provides the spreading crops' fruit service
        CropDevice.Services services = new CropDevice.Services(world, () -> context.clock().get(), () -> devices,
            ThreadLocalRandom.current(), () -> context.services().get(SpreadingFruit.class).orElse(null));
        kind = devices.register(CropDevice.kind(services));
        context.services().get(CatchUpService.class).ifPresent(c -> c.kinds().registerDirect(CropDevice.CATCH_UP_KIND,
            (w, pos) -> catchUp(services, devices, blocks, w, pos)));
        context.listen(new CropListener(world, () -> context.services().require(LootService.class), () -> devices, () -> kind));
    }

    /** The catch-up of one listed crop; false when the block is no longer a living bottom crop (its index entry is dropped). */
    boolean catchUp(CropDevice.Services services, DeviceService devices, CustomBlocks blocks, World world, BlockPos pos) {
        Block block = world.getBlockAt(pos.x(), pos.y(), pos.z());
        Optional<BlockStateRef> state = blocks.stateAt(block);
        if (state.isEmpty() || CropIds.type(state.get().id()).isEmpty() || CropIds.isTop(state.get())) {
            return false;
        }
        var type = CropIds.type(state.get().id()).get();
        AtomicReference<CropDevice.Settled> settled = new AtomicReference<>();
        Optional<?> stored = devices.edit(block, kind, (record, now) -> {
            CropDevice.Settled s = CropDevice.settle(services, block, state.get(), type, record.state());
            settled.set(s);
            return s.result().dead() ? record : record.withState(s.result().plant());
        });
        if (stored.isEmpty()) {
            return false;
        }
        CropDevice.Settled s = settled.get();
        if (s != null) {
            CropDevice.apply(services, block, s);
        }
        return !s.result().dead();
    }
}
