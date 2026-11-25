/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/BloomBlockEntity.java @ c3b4d9536 (setBloom, dropBloom, getState),
 *       src/main/java/net/dries007/tfc/common/blocks/BloomBlock.java @ c3b4d9536 (onDestroyedByPlayer)
 */
package dev.tfp.paper.bloomery;

import dev.tfp.core.bloomery.BloomData;
import dev.tfp.core.bloomery.BloomeryStructure;
import dev.tfp.core.device.DeviceRecord;
import dev.tfp.paper.device.DeviceKind;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.device.StackCodec;
import java.util.Objects;
import java.util.Optional;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;

/**
 * PAR-METAL-14: the bloom block as a device kind ({@code tfp:bloom}). Its record holds the bloom item (slot 0) and
 * {@link BloomData}: the count left and the count it was made with. Each break by a player in survival drops one item and
 * takes one off the count (TFC {@code dropBloom}); the block follows the count in layers and goes at zero.
 */
public final class BloomDevice {
    public static final String ID = "tfp:bloom";

    private final StackCodec stacks;
    private final BloomeryEffects effects;
    private final BloomeryProbe probe;
    private final DeviceKind<BloomData> kind;

    public BloomDevice(StackCodec stacks, BloomeryEffects effects, BloomeryProbe probe) {
        this.stacks = Objects.requireNonNull(stacks, "stacks");
        this.effects = Objects.requireNonNull(effects, "effects");
        this.probe = Objects.requireNonNull(probe, "probe");
        this.kind = DeviceKind.builder(ID, BloomData.CODEC, BloomData.FRESH)
            .slots(1)
            .slotLimit(1)
            .filter((slot, stack) -> false)
            .removal(DeviceKind.RemoveBehaviour.NOOP) // TFC: breaking the block by other means drops nothing
            .build();
    }

    public DeviceKind<BloomData> kind() {
        return kind;
    }

    /** TFC {@code setBloom}: the freshly placed bloom block holds {@code count} of {@code item}. Region thread of {@code block}. */
    public boolean seed(DeviceService devices, Block block, ItemStack item, int count) {
        ItemStack one = item.clone();
        one.setAmount(1);
        return devices.edit(block, kind, (record, now) ->
            record.withSlot(0, stacks.encode(one)).withState(BloomData.made(count))).isPresent();
    }

    /**
     * TFC {@code dropBloom}: one item is dropped, at an adjacent bloomery if there is one, else at the bloom. Region thread of
     * {@code block}.
     *
     * @return false when the block holds no bloom (no record): the caller lets the break happen
     */
    public boolean dropOne(DeviceService devices, Block block) {
        boolean[] dropped = {false};
        devices.edit(block, kind, (record, now) -> {
            BloomData data = record.state();
            ItemStack item = stacks.decode(record.slot(0));
            if (data.count() <= 0 || StackCodec.isEmpty(item)) {
                return record;
            }
            dropped[0] = true;
            item.setAmount(1);
            effects.spawn(dropBlock(block), item);
            BloomData next = data.dropped();
            if (next.count() <= 0) {
                effects.removeBloom(block);
            } else {
                effects.showBloom(block, next.layers());
            }
            return record.withState(next);
        });
        return dropped[0];
    }

    /** The first horizontal neighbour that is a bloomery, else the bloom's own block (TFC {@code dropBloom}). */
    Block dropBlock(Block bloom) {
        for (String d : new String[] {"north", "east", "south", "west"}) {
            int[] v = BloomeryStructure.delta(d);
            Block at = bloom.getRelative(v[0], 0, v[1]);
            if (probe.readable(at) && probe.blockId(at).filter(BloomeryDevice.ID::equals).isPresent()) {
                return at;
            }
        }
        return bloom;
    }

    /** The record of a bloom block, for tests and debug. */
    public Optional<DeviceRecord<BloomData>> read(DeviceService devices, Block block) {
        return devices.edit(block, kind, (record, now) -> record);
    }
}
