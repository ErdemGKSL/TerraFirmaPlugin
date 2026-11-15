/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/container/GrillContainer.java @ c3b4d9536 (the CallbackSlots: a slot change
 *       sets the slot and marks the cascade; the screen reads the block entity's temperature)
 */
package dev.tfp.paper.grill;

import dev.tfp.core.device.DeviceRecord;
import dev.tfp.core.firepit.FirepitData;
import dev.tfp.core.grill.GrillSim;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.grill.gui.GrillLink;
import dev.tfp.paper.grill.gui.GrillSnapshot;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;

/**
 * PAR-HEAT-08: the grill screen's link to the grill device at one block. Every read catches the grill up and stores it
 * ({@link DeviceService#edit}); a commit writes the nine slots, runs TFC's fuel cascade and redraws the items on top.
 * Runs on the viewer's thread, which must own the block's region (always on Paper; on Folia the player stands within reach
 * of the grill, so in its region): otherwise the grill reads as gone and the screen closes.
 */
public final class DeviceGrillLink implements GrillLink {
    private final DeviceService devices;
    private final GrillDevice device;
    private final Block block;
    private final Predicate<Block> owned;

    public DeviceGrillLink(DeviceService devices, GrillDevice device, Block block, Predicate<Block> owned) {
        this.devices = Objects.requireNonNull(devices, "devices");
        this.device = Objects.requireNonNull(device, "device");
        this.block = Objects.requireNonNull(block, "block");
        this.owned = Objects.requireNonNull(owned, "owned");
    }

    @Override
    public Optional<GrillSnapshot> snapshot() {
        if (!owned.test(block)) {
            return Optional.empty();
        }
        Optional<DeviceRecord<FirepitData>> now = devices.edit(block, device.kind(), (record, tick) -> record);
        now.ifPresent(r -> device.services().effects().items(block, device.grill(r))); // a catch-up may have cooked
        return now.map(this::snapshot);
    }

    @Override
    public Optional<GrillSnapshot> commit(List<ItemStack> slots) {
        if (!owned.test(block) || slots.size() != GrillSim.SLOTS) {
            return Optional.empty();
        }
        Optional<DeviceRecord<FirepitData>> after = devices.edit(block, device.kind(), (record, now) -> {
            DeviceRecord<FirepitData> out = record;
            for (int i = 0; i < GrillSim.SLOTS; i++) {
                out = out.withSlot(i, device.stacks().encode(slots.get(i)));
            }
            return GrillDevice.cascade(out);
        });
        after.ifPresent(r -> device.services().effects().items(block, device.grill(r)));
        return after.map(this::snapshot);
    }

    @Override
    public boolean isItemValid(int tfcSlot, ItemStack stack) {
        return device.isItemValid(tfcSlot, stack);
    }

    private GrillSnapshot snapshot(DeviceRecord<FirepitData> record) {
        List<ItemStack> out = new ArrayList<>(GrillSim.SLOTS);
        for (int i = 0; i < GrillSim.SLOTS; i++) {
            out.add(device.stacks().decode(record.slot(i)));
        }
        return new GrillSnapshot(record.state().temperature(), out);
    }
}
