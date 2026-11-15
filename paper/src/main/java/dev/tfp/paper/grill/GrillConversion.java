/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/AbstractFirepitBlockEntity.java @ c3b4d9536 (convertTo:
 *       ejectMainInventory, copyToAndClear, setBlock with copyProperties, playPlaceSound, copyFrom; ejectInventory with the
 *       ash when the old block entity goes)
 */
package dev.tfp.paper.grill;

import dev.tfp.core.device.DeviceRecord;
import dev.tfp.core.device.ItemBlob;
import dev.tfp.core.firepit.FirepitData;
import dev.tfp.core.firepit.FirepitSim;
import dev.tfp.core.grill.GrillSim;
import dev.tfp.paper.device.DeviceKind;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.firepit.FirepitDevice;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.CustomBlocks;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.block.Block;

/**
 * TFC {@code AbstractFirepitBlockEntity.convertTo} between the firepit and the grill (PAR-HEAT-08), outside any device
 * route, on the block's region thread:
 *
 * <ol>
 *   <li>the old device is caught up and its four fuel slots are taken out (TFC {@code copyToAndClear}); its other slots and
 *       its ash stay, so the old block entity's removal drops them (TFC {@code ejectMainInventory}, then
 *       {@code ejectInventory}'s ash when the block is replaced);</li>
 *   <li>the new block is placed with the old {@code lit}, {@code smoke_level} and {@code axis} ({@code copyProperties});</li>
 *   <li>the new device gets the fuel slots and TFC's {@code copyFrom} fields ({@link GrillSim#converted}), its place
 *       sound plays, and a neighbour update lets its handler show the state and queue its burn-out callback.</li>
 * </ol>
 *
 * If the new block cannot be placed, the fuel goes back into the old device and nothing else changes.
 */
public final class GrillConversion {
    /** Places a custom block state (CraftEngine: removes the old block entity, creates the new one). */
    @FunctionalInterface
    public interface Placer {
        boolean place(Block block, BlockStateRef state);
    }

    private final DeviceService devices;
    private final CustomBlocks blocks;
    private final Placer placer;
    private final GrillEffects effects;

    public GrillConversion(DeviceService devices, CustomBlocks blocks, GrillEffects effects) {
        this(devices, blocks, (block, state) -> blocks.place(block, state, true), effects);
    }

    public GrillConversion(DeviceService devices, CustomBlocks blocks, Placer placer, GrillEffects effects) {
        this.devices = Objects.requireNonNull(devices, "devices");
        this.blocks = Objects.requireNonNull(blocks, "blocks");
        this.placer = Objects.requireNonNull(placer, "placer");
        this.effects = Objects.requireNonNull(effects, "effects");
    }

    /** The firepit at {@code block} becomes a grill. Returns whether it did. */
    public boolean toGrill(Block block) {
        return convert(block, FirepitDevice.ID, GrillDevice.ID);
    }

    /** The grill at {@code block} becomes a firepit. Returns whether it did. */
    public boolean toFirepit(Block block) {
        return convert(block, GrillDevice.ID, FirepitDevice.ID);
    }

    boolean convert(Block block, String fromId, String toId) {
        Optional<BlockStateRef> at = blocks.stateAt(block);
        Optional<DeviceKind<FirepitData>> from = kind(fromId);
        Optional<DeviceKind<FirepitData>> to = kind(toId);
        if (at.isEmpty() || !at.get().id().equals(fromId) || from.isEmpty() || to.isEmpty()) {
            return false;
        }
        AtomicReference<DeviceRecord<FirepitData>> taken = new AtomicReference<>();
        Optional<DeviceRecord<FirepitData>> left = devices.edit(block, from.get(), (record, now) -> {
            taken.set(record);
            DeviceRecord<FirepitData> out = record;
            for (int i = FirepitSim.SLOT_FUEL_CONSUME; i <= FirepitSim.SLOT_FUEL_INPUT; i++) {
                out = out.withSlot(i, ItemBlob.EMPTY);
            }
            return out;
        });
        if (left.isEmpty() || taken.get() == null) {
            return false;
        }
        DeviceRecord<FirepitData> old = taken.get();
        List<ItemBlob> fuel = new ArrayList<>(4);
        for (int i = FirepitSim.SLOT_FUEL_CONSUME; i <= FirepitSim.SLOT_FUEL_INPUT; i++) {
            fuel.add(old.slot(i));
        }
        FirepitData data = old.state();
        BlockStateRef next = BlockStateRef.of(toId).with(FirepitDevice.LIT, Boolean.toString(data.lit()))
            .with(FirepitDevice.SMOKE_LEVEL, Integer.toString(data.smokeLevel()))
            .with(FirepitDevice.AXIS, at.get().properties().getOrDefault(FirepitDevice.AXIS, "x"));
        if (!placer.place(block, next)) {
            devices.edit(block, from.get(), (record, now) -> withFuel(record, fuel));
            return false;
        }
        FirepitData moved = GrillSim.converted(data);
        devices.edit(block, to.get(), (record, now) -> withFuel(record.withState(moved), fuel));
        effects.placeSound(block);
        devices.neighbourChanged(block);
        return true;
    }

    private static DeviceRecord<FirepitData> withFuel(DeviceRecord<FirepitData> record, List<ItemBlob> fuel) {
        DeviceRecord<FirepitData> out = record;
        for (int i = 0; i < fuel.size(); i++) {
            out = out.withSlot(FirepitSim.SLOT_FUEL_CONSUME + i, fuel.get(i));
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private Optional<DeviceKind<FirepitData>> kind(String id) {
        return devices.kinds().find(id).filter(k -> k.stateCodec() == FirepitData.CODEC)
            .map(k -> (DeviceKind<FirepitData>) k);
    }
}
