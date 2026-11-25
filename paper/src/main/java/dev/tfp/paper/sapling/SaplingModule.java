/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.sapling;

import dev.tfp.core.sapling.SaplingRules;
import dev.tfp.core.sapling.SaplingState;
import dev.tfp.core.world.BlockPos;
import dev.tfp.core.worldgen.tree.Wood;
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
 * PAR-FARM-10: sapling growth into trees. Registers the device kinds of {@link SaplingDevice} and the catch-up kind of the same id.
 * Must be listed after the device, tags and catchup modules.
 */
public final class SaplingModule implements FeatureModule {
    public static final String ID = "sapling";

    private final Map<String, DeviceKind<SaplingState>> kinds = new ConcurrentHashMap<>();

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void enable(ModuleContext context) {
        DeviceService devices = context.services().require(DeviceService.class);
        Tags tags = context.services().require(Tags.class);
        CustomBlocks blocks = context.integrations().customBlocks();
        SaplingFeatures features = new SaplingFeatures(context.plugin().getClass().getClassLoader());
        PaperSaplingEffects effects = new PaperSaplingEffects(context.scheduler(), blocks, tags, features,
            () -> context.services().get(CatchUpService.class));
        SaplingDevice.Services services = new SaplingDevice.Services(effects, () -> context.clock().get());
        for (DeviceKind<SaplingState> k : SaplingDevice.kinds(services)) {
            kinds.put(k.id(), devices.register(k));
        }
        context.services().get(CatchUpService.class).ifPresent(c -> c.kinds().registerDirect(SaplingDevice.CATCH_UP_KIND,
            (world, pos) -> catchUp(services, devices, blocks, world, pos)));
    }

    /** The catch-up of one listed block; false when the block is no longer a sapling (grown, broken: its index entry is dropped). */
    boolean catchUp(SaplingDevice.Services services, DeviceService devices, CustomBlocks blocks, World world, BlockPos pos) {
        Block block = world.getBlockAt(pos.x(), pos.y(), pos.z());
        Optional<BlockStateRef> state = blocks.stateAt(block);
        Wood wood = state.map(s -> SaplingRules.woodOf(s.id())).orElse(null);
        DeviceKind<SaplingState> kind = state.map(s -> kinds.get(s.id())).orElse(null);
        if (wood == null || kind == null) {
            return false;
        }
        var grown = new java.util.concurrent.atomic.AtomicReference<dev.tfp.core.worldgen.tree.Tree>();
        Optional<?> stored = devices.edit(block, kind, (record, now) -> {
            SaplingDevice.Outcome outcome = SaplingDevice.settle(services, block, wood, record.state());
            outcome.tree().ifPresent(grown::set);
            return outcome.state().equals(record.state()) ? record : record.withState(outcome.state());
        });
        if (grown.get() != null) {
            services.effects().grow(block, grown.get());
            return false;
        }
        return stored.isPresent();
    }
}
