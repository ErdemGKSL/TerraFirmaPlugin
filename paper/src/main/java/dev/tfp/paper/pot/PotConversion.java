/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/AbstractFirepitBlockEntity.java @ c3b4d9536 (convertTo:
 *       ejectMainInventory, the inventory copied, copyProperties of the state, playPlaceSound, copyFrom: burnTicks,
 *       airTicks, burnTemperature, temperature; ejectInventory with the ash when the old block entity goes),
 *       src/main/java/net/dries007/tfc/common/blocks/devices/FirepitBlock.java @ c3b4d9536 (useItemOn: a ceramic pot turns
 *       the firepit into a pot)
 */
package dev.tfp.paper.pot;

import dev.tfp.core.cooking.FirepitState;
import dev.tfp.core.device.DeviceRecord;
import dev.tfp.core.device.ItemBlob;
import dev.tfp.core.firepit.FirepitData;
import dev.tfp.core.firepit.FirepitSim;
import dev.tfp.core.pot.PotData;
import dev.tfp.core.pot.PotSim;
import dev.tfp.paper.device.DeviceKind;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.device.StackCodec;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.CustomBlocks;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.bukkit.Location;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;

/**
 * TFC {@code AbstractFirepitBlockEntity.convertTo} between the firepit ({@code tfp:firepit}) and the pot
 * ({@code tfp:pot}): the items that are not fuel drop at the block ({@code ejectMainInventory}), the block is replaced
 * keeping {@code lit}, {@code smoke_level} and {@code axis}, the four fuel slots move over and the new device takes the
 * old one's burn ticks, bellows air, burn temperature and device temperature ({@code copyFrom}); the rest (ash, dirtiness,
 * the pot's tank and output) is not carried over, as in TFC.
 *
 * <p>The old device's slots are emptied before the swap (they are ejected here or moved over), so its removal (the kind's
 * drop) drops none of them twice. Its ash stays on the old record: TFC's {@code setBlock} runs the old block's
 * {@code onRemove} -> {@code ejectInventory}, which spawns the ash as wood ash, so the old device's {@code removed} drops
 * it at the block (as {@code GrillConversion} does). Region thread of the block.
 */
public final class PotConversion {
    /** Places a custom block state (CraftEngine: removes the old block entity, creates the new one). */
    @FunctionalInterface
    public interface Placer {
        boolean place(Block block, BlockStateRef state);
    }

    /** TFC's firepit-family place sound ({@code SoundType} of the pot: nether wart). */
    static final String PLACE_SOUND = "minecraft:item.nether_wart.plant";

    private final DeviceService devices;
    private final CustomBlocks blocks;
    private final DeviceKind<PotData> pot;
    private final Placer placer;

    public PotConversion(DeviceService devices, CustomBlocks blocks, DeviceKind<PotData> pot) {
        this(devices, blocks, pot, (block, state) -> blocks.place(block, state, true));
    }

    public PotConversion(DeviceService devices, CustomBlocks blocks, DeviceKind<PotData> pot, Placer placer) {
        this.devices = Objects.requireNonNull(devices, "devices");
        this.blocks = Objects.requireNonNull(blocks, "blocks");
        this.pot = Objects.requireNonNull(pot, "pot");
        this.placer = Objects.requireNonNull(placer, "placer");
    }

    /** The firepit kind registered with the device service, if the firepit module runs. */
    @SuppressWarnings("unchecked")
    Optional<DeviceKind<FirepitData>> firepitKind() {
        return devices.kinds().find(PotModule.FIREPIT_ID)
            .filter(k -> k.stateCodec() == FirepitData.CODEC)
            .map(k -> (DeviceKind<FirepitData>) k);
    }

    /** Whether {@code block} is a firepit. */
    public boolean isFirepit(Block block) {
        return blocks.stateAt(block).map(s -> s.id().equals(PotModule.FIREPIT_ID)).orElse(false);
    }

    /** Whether {@code block} is a pot. */
    public boolean isPot(Block block) {
        return blocks.stateAt(block).map(s -> s.id().equals(PotDevice.ID)).orElse(false);
    }

    /** TFC {@code FirepitBlock.useItemOn} with a ceramic pot: the firepit becomes a pot. Returns whether it did. */
    public boolean toPot(Block block) {
        Optional<BlockStateRef> state = blocks.stateAt(block);
        Optional<DeviceKind<FirepitData>> kind = firepitKind();
        if (state.isEmpty() || !state.get().id().equals(PotModule.FIREPIT_ID) || kind.isEmpty()) {
            return false;
        }
        Object[] old = new Object[1];
        Optional<DeviceRecord<FirepitData>> emptied = devices.edit(block, kind.get(), (record, now) -> {
            old[0] = record;
            return record.cleared();
        });
        if (emptied.isEmpty()) {
            return false;
        }
        @SuppressWarnings("unchecked")
        DeviceRecord<FirepitData> before = (DeviceRecord<FirepitData>) old[0];
        BlockStateRef next = copyProperties(BlockStateRef.of(PotDevice.ID), state.get());
        if (!placer.place(block, next)) {
            devices.edit(block, kind.get(), (record, now) -> before.withLastUpdateTick(record.lastUpdateTick()));
            return false;
        }
        FirepitData f = before.state();
        devices.edit(block, pot, (record, now) -> {
            DeviceRecord<PotData> r = record;
            for (int i = FirepitSim.SLOT_FUEL_CONSUME; i <= FirepitSim.SLOT_FUEL_INPUT; i++) {
                r = r.withSlot(i, before.slot(i));
            }
            return r.withState(PotData.FRESH.withFirepit(copyFrom(f, PotData.FRESH.firepit())));
        });
        eject(block, before.slots(), FirepitSim.SLOT_ITEM_INPUT);
        playPlaceSound(block);
        return true;
    }

    /** TFC {@code PotBlock.useItemOn}, sneaking with an empty hand: the pot becomes a firepit. Returns whether it did. */
    public boolean toFirepit(Block block) {
        Optional<BlockStateRef> state = blocks.stateAt(block);
        Optional<DeviceKind<FirepitData>> kind = firepitKind();
        if (state.isEmpty() || !state.get().id().equals(PotDevice.ID) || kind.isEmpty()) {
            return false;
        }
        Object[] old = new Object[1];
        Optional<DeviceRecord<PotData>> emptied = devices.edit(block, pot, (record, now) -> {
            old[0] = record;
            return record.cleared();
        });
        if (emptied.isEmpty()) {
            return false;
        }
        @SuppressWarnings("unchecked")
        DeviceRecord<PotData> before = (DeviceRecord<PotData>) old[0];
        BlockStateRef next = copyProperties(BlockStateRef.of(PotModule.FIREPIT_ID), state.get());
        if (!placer.place(block, next)) {
            devices.edit(block, pot, (record, now) -> before.withLastUpdateTick(record.lastUpdateTick()));
            return false;
        }
        FirepitData f = before.state().firepit();
        devices.edit(block, kind.get(), (record, now) -> {
            DeviceRecord<FirepitData> r = record;
            for (int i = FirepitSim.SLOT_FUEL_CONSUME; i <= FirepitSim.SLOT_FUEL_INPUT; i++) {
                r = r.withSlot(i, before.slot(i));
            }
            return r.withState(copyFrom(f, FirepitData.FRESH));
        });
        eject(block, before.slots(), PotSim.SLOT_INPUT_START);
        playPlaceSound(block);
        return true;
    }

    /**
     * TFC {@code copyFrom}: burn ticks, air ticks, burn temperature and temperature from {@code from}; {@code lit} and the
     * smoke level follow the copied block state ({@code copyProperties}); the rest is the new device's own.
     */
    static FirepitData copyFrom(FirepitData from, FirepitData fresh) {
        FirepitState h = from.heat();
        return new FirepitData(new FirepitState(h.burnTicks(), h.burnTemperature(), h.temperature(), h.airTicks()),
            from.lit(), from.smokeLevel(), fresh.dirtiness(), fresh.ash(), fresh.lastMaxBurnTicks(), from.raining());
    }

    /** TFC {@code Helpers.copyProperties}: {@code lit}, {@code smoke_level} and {@code axis} onto the new block. */
    static BlockStateRef copyProperties(BlockStateRef target, BlockStateRef from) {
        BlockStateRef out = target;
        for (String p : List.of(PotDevice.LIT, PotDevice.SMOKE_LEVEL, PotDevice.AXIS)) {
            String v = from.properties().get(p);
            if (v != null) {
                out = out.with(p, v);
            }
        }
        return out;
    }

    /** TFC {@code ejectMainInventory}: the slots from {@code start} on spawn at the block. */
    private void eject(Block block, List<ItemBlob> slots, int start) {
        StackCodec stacks = devices.stacks();
        List<ItemStack> out = new ArrayList<>();
        for (int i = start; i < slots.size(); i++) {
            ItemStack s = stacks.decode(slots.get(i));
            if (!StackCodec.isEmpty(s)) {
                out.add(s);
            }
        }
        Location centre = new Location(block.getWorld(), block.getX() + 0.5, block.getY() + 0.7, block.getZ() + 0.5);
        for (ItemStack s : out) {
            block.getWorld().dropItem(centre, s, e -> e.setPickupDelay(0));
        }
    }

    private static void playPlaceSound(Block block) {
        Location centre = new Location(block.getWorld(), block.getX() + 0.5, block.getY() + 0.5, block.getZ() + 0.5);
        block.getWorld().playSound(centre, PLACE_SOUND, SoundCategory.BLOCKS, 1f, 0.8f);
    }
}
