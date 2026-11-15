/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/container/PotContainer.java @ c3b4d9536 (the nine slots),
 * file: src/main/java/net/dries007/tfc/common/blockentities/AbstractFirepitBlockEntity.java @ c3b4d9536
 *       (setAndUpdateSlots, cascadeFuelSlots),
 * file: src/main/java/net/dries007/tfc/common/blockentities/PotBlockEntity.java @ c3b4d9536 (getBoilingTicks,
 *       hasRecipeStarted, getOutput)
 */
package dev.tfp.paper.pot;

import dev.tfp.core.device.DeviceRecord;
import dev.tfp.core.device.ItemBlob;
import dev.tfp.core.firepit.FirepitSim;
import dev.tfp.core.pot.PotData;
import dev.tfp.core.pot.PotOutput;
import dev.tfp.core.pot.PotSim;
import dev.tfp.paper.device.DeviceKind;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.device.StackCodec;
import dev.tfp.paper.pot.gui.PotLink;
import dev.tfp.paper.pot.gui.PotSnapshot;
import dev.tfp.paper.scheduler.Scheduler;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;

/**
 * PAR-HEAT-09: the {@link PotLink} of a placed pot, over its device record. Every call loads the record through
 * {@link DeviceService#edit} (catch-up first), changes it and stores it, then answers the pot as stored.
 *
 * <p>Threads: the screen calls on the viewer's thread; the record lives on the block's region. When this thread does not
 * own the block (Folia, the regions split while the screen is open) every call answers empty: the screen gives the viewer
 * back what it held since the last commit and closes, as the barrel screen.
 */
public final class PotScreenLink implements PotLink {
    /** The screen key of a pot: its block position. */
    public record Key(UUID world, int x, int y, int z) {
        public static Key of(Block block) {
            return new Key(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
        }
    }

    /** What a link needs. */
    public record Env(DeviceService devices, PotDevice device, Scheduler scheduler) {
        public Env {
            Objects.requireNonNull(devices, "devices");
            Objects.requireNonNull(device, "device");
            Objects.requireNonNull(scheduler, "scheduler");
        }

        DeviceKind<PotData> kind() {
            return device.kind();
        }

        StackCodec stacks() {
            return devices.stacks();
        }
    }

    private final Env env;
    private final Block block;

    public PotScreenLink(Env env, Block block) {
        this.env = Objects.requireNonNull(env, "env");
        this.block = Objects.requireNonNull(block, "block");
    }

    public Key key() {
        return Key.of(block);
    }

    private boolean owned() {
        return env.scheduler().isOwner(block.getWorld(), block.getX() >> 4, block.getZ() >> 4);
    }

    @Override
    public Optional<PotSnapshot> snapshot() {
        if (!owned()) {
            return Optional.empty();
        }
        return env.devices().edit(block, env.kind(), (record, now) -> record).map(this::snapshotOf);
    }

    @Override
    public Optional<PotSnapshot> commit(List<ItemStack> slots) {
        if (slots.size() != PotSim.SLOTS) {
            throw new IllegalArgumentException(slots.size() + " slots");
        }
        if (!owned()) {
            return Optional.empty();
        }
        return env.devices().edit(block, env.kind(), (record, now) -> withSlots(record, slots, env.stacks()))
            .map(this::snapshotOf);
    }

    @Override
    public boolean isItemValid(int tfcSlot, ItemStack stack) {
        return env.device().isItemValid(tfcSlot, stack);
    }

    /** The nine slots stored, then TFC {@code cascadeFuelSlots}. */
    static DeviceRecord<PotData> withSlots(DeviceRecord<PotData> record, List<ItemStack> slots, StackCodec stacks) {
        for (int i = 0; i < PotSim.SLOTS; i++) {
            record = record.withSlot(i, stacks.encode(slots.get(i)));
        }
        return cascade(record);
    }

    /** TFC {@code cascadeFuelSlots}: the non-empty fuel slots move down to the lowest open slots, in order. */
    static DeviceRecord<PotData> cascade(DeviceRecord<PotData> record) {
        List<ItemBlob> fuel = new ArrayList<>(4);
        for (int i = FirepitSim.SLOT_FUEL_CONSUME; i <= FirepitSim.SLOT_FUEL_INPUT; i++) {
            if (!record.slot(i).isEmpty()) {
                fuel.add(record.slot(i));
            }
        }
        for (int i = FirepitSim.SLOT_FUEL_CONSUME; i <= FirepitSim.SLOT_FUEL_INPUT; i++) {
            record = record.withSlot(i, i < fuel.size() ? fuel.get(i) : ItemBlob.EMPTY);
        }
        return record;
    }

    PotSnapshot snapshotOf(DeviceRecord<PotData> record) {
        StackCodec stacks = env.stacks();
        List<ItemStack> slots = new ArrayList<>(PotSim.SLOTS);
        for (int i = 0; i < PotSim.SLOTS; i++) {
            slots.add(stacks.decode(record.slot(i)));
        }
        PotData d = record.state();
        boolean started = PotSim.hasRecipeStarted(d, env.device().recipe(record).map(PotRecipeView::boil).orElse(null));
        return new PotSnapshot(d.temperature(), d.counters().boilingTicks(), started, d.fluid(), outputStack(d.output(), stacks),
            slots);
    }

    /** TFC's output tooltip stack: the soup, or the sealed jars. */
    static ItemStack outputStack(PotOutput output, StackCodec stacks) {
        return switch (output) {
            case PotOutput.Soup s -> stacks.decode(s.stack());
            case PotOutput.Jam j -> stacks.decode(j.sealed());
            case PotOutput.None n -> ItemStack.empty();
        };
    }
}
