/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/plant/fruit/StationaryBerryBushBlock.java @ c3b4d9536,
 *       src/main/java/net/dries007/tfc/config/ServerConfig.java @ c3b4d9536 (berryBushGrowthTicks 96000 = 4 default days,
 *       fruitPickBloomDelayTicks 240000 = 10 default days)
 */
package dev.tfp.paper.berry;

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
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.World;
import org.bukkit.block.Block;

/**
 * PAR-FARM-06: the stationary berry bushes. Registers the device kind {@link BerryBushDevice#ID} and the catch-up kind of the same id, which
 * brings a listed bush up to date on chunk load and in the visible-area sweep. The CraftEngine blocks ({@code tfp:berry_bush/<species>},
 * {@code tfp:berry_bush/dead}) are the generated content of the follow-up item PAR-FARM-14; until they exist the kinds are registered and idle.
 * Must be listed after the device, tags and catchup modules.
 */
public final class BerryModule implements FeatureModule {
    public static final String ID = "berry";

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
        PaperBerryEffects effects = new PaperBerryEffects(context.scheduler(), blocks, context.integrations().customItems(),
            () -> context.integrations().blockVisuals(), tags, () -> devices, () -> kind,
            () -> context.services().get(CatchUpService.class));
        BerryBushDevice.Services services = new BerryBushDevice.Services(effects, () -> context.clock().get(), BerryRules.Settings.DEFAULT);
        kind = devices.register(BerryBushDevice.kind(services));
        context.services().get(CatchUpService.class).ifPresent(c -> c.kinds().registerDirect(BerryBushDevice.CATCH_UP_KIND,
            (world, pos) -> catchUp(services, devices, blocks, world, pos)));
    }

    /** The catch-up of one listed bush; false when the block is no longer a bush (its index entry is then dropped). */
    boolean catchUp(BerryBushDevice.Services services, DeviceService devices, CustomBlocks blocks, World world, BlockPos pos) {
        Block block = world.getBlockAt(pos.x(), pos.y(), pos.z());
        Optional<BlockStateRef> state = blocks.stateAt(block);
        if (state.isEmpty() || !state.get().id().startsWith(BerryBushDevice.ID)) {
            return false;
        }
        AtomicReference<BerryBushDevice.Settled> settled = new AtomicReference<>();
        Optional<?> stored = devices.edit(block, kind, (record, now) -> {
            var species = BerryBushDevice.species(state.get().id());
            if (species.isEmpty() && !state.get().id().equals(BerryBushDevice.DEAD_ID)) {
                return record;
            }
            // A dead bush keeps its species in no block id: any species settles it the same way (only its removal matters).
            var sp = species.orElse(dev.tfp.core.berry.BushSpecies.SNOWBERRY);
            BerryBushDevice.Settled s = BerryBushDevice.settle(services, block, sp, record.state(), now);
            settled.set(s);
            return record.withState(s.state());
        });
        if (stored.isEmpty()) {
            return false;
        }
        BerryBushDevice.Settled s = settled.get();
        if (s != null) {
            BerryBushDevice.apply(services, block, s);
        }
        return true;
    }
}
