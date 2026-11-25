/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/BlastFurnaceBlockEntity.java @ c3b4d9536 (serverTick: transferExact
 *       into the container below, provideHeatTo)
 */
package dev.tfp.paper.blastfurnace;

import dev.tfp.core.alloy.FluidAlloy;
import dev.tfp.core.crucible.CrucibleData;
import dev.tfp.core.crucible.CrucibleRules;
import dev.tfp.paper.crucible.CrucibleDevice;
import dev.tfp.paper.device.DeviceKind;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.firebox.HeatConsumers;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.scheduler.Scheduler;
import java.util.Objects;
import java.util.Optional;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

/**
 * {@link BlastFurnaceBelow} for the crucible (the only container TFC's tuyere furnace pours into that this plugin has): the
 * fluid goes into its alloy and the furnace heats it on every tick it poured. Both writes are one region task of the
 * crucible's block, after the furnace's own record is stored.
 */
public final class PaperBlastFurnaceBelow implements BlastFurnaceBelow {
    private final DeviceService devices;
    private final CustomBlocks blocks;
    private final HeatConsumers consumers;
    private final Scheduler scheduler;

    public PaperBlastFurnaceBelow(DeviceService devices, CustomBlocks blocks, HeatConsumers consumers, Scheduler scheduler) {
        this.devices = Objects.requireNonNull(devices, "devices");
        this.blocks = Objects.requireNonNull(blocks, "blocks");
        this.consumers = Objects.requireNonNull(consumers, "consumers");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    private boolean crucible(Block below) {
        return blocks.stateAt(below).map(BlockStateRef::id).filter(CrucibleDevice.ID::equals).isPresent();
    }

    @Override
    public int accepts(Block furnace, String fluid) {
        Block below = furnace.getRelative(BlockFace.DOWN);
        if (!crucible(below)) {
            return 0;
        }
        Optional<DeviceService.Inspection> seen = devices.inspect(below);
        if (seen.isEmpty() || seen.get().record().isEmpty() || !(seen.get().record().get().state() instanceof CrucibleData data)) {
            return 0;
        }
        FluidAlloy.Fill fill = CrucibleRules.fill(data.alloy(), fluid, CrucibleRules.DEFAULT_CAPACITY, CrucibleRules.DEFAULT_CAPACITY);
        return fill.accepted();
    }

    @Override
    @SuppressWarnings("unchecked")
    public void give(Block furnace, String fluid, int amount, float temperature, long now) {
        Block below = furnace.getRelative(BlockFace.DOWN);
        scheduler.region(below.getWorld(), below.getX() >> 4, below.getZ() >> 4, () -> {
            if (!crucible(below)) {
                return;
            }
            Optional<DeviceKind<?>> kind = devices.kinds().find(CrucibleDevice.ID);
            if (kind.isEmpty()) {
                return;
            }
            DeviceKind<CrucibleData> crucible = (DeviceKind<CrucibleData>) kind.get();
            devices.edit(below, crucible, (record, tick) -> {
                CrucibleData data = record.state();
                FluidAlloy.Fill fill = CrucibleRules.fill(data.alloy(), fluid, amount, CrucibleRules.DEFAULT_CAPACITY);
                return fill.accepted() > 0 ? record.withState(data.withAlloy(fill.alloy())) : record;
            });
            consumers.find(CrucibleDevice.ID).ifPresent(c -> c.heat(below, Math.max(1, now - amount + 1), now, temperature));
        });
    }
}
