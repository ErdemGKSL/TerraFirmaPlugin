/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/container/CharcoalForgeContainer.java @ c3b4d9536 (addContainerSlots, moveStack),
 *       src/main/java/net/dries007/tfc/client/screen/CharcoalForgeScreen.java @ c3b4d9536 (renderBg: the temperature bar;
 *       the tooltip heatTooltipStyle.formatColored(temperature))
 */
package dev.tfp.paper.charcoalforge.gui;

import dev.tfp.core.charcoalforge.CharcoalForgeData;
import dev.tfp.core.charcoalforge.CharcoalForgeSim;
import dev.tfp.core.device.DeviceRecord;
import dev.tfp.paper.charcoalforge.CharcoalForgeDevice;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.device.StackCodec;
import dev.tfp.paper.scheduler.Scheduler;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;

/**
 * PAR-METAL-09: the {@link ForgeLink} of a placed charcoal forge, over its device record. Every call loads the record through
 * {@link DeviceService#edit} (catch-up first, so the screen shows the heat as it is now), changes it and stores it, hands the
 * heat the catch-up gave to the consumer above ({@link CharcoalForgeDevice#pushHeat}), then answers the forge as stored.
 *
 * <p>Threads: the screen calls on the viewer's thread; the record lives on the block's region. When this thread does not own
 * the block every call answers empty: the screen gives the viewer back what it held since the last commit and closes.
 */
public final class ForgeScreenLink implements ForgeLink {
    /** The screen key of a forge: its block position. */
    public record Key(UUID world, int x, int y, int z) {
        public static Key of(Block block) {
            return new Key(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
        }
    }

    /** What a link needs. */
    public record Env(DeviceService devices, CharcoalForgeDevice device, Scheduler scheduler) {
        public Env {
            Objects.requireNonNull(devices, "devices");
            Objects.requireNonNull(device, "device");
            Objects.requireNonNull(scheduler, "scheduler");
        }

        StackCodec stacks() {
            return devices.stacks();
        }
    }

    private final Env env;
    private final Block block;

    public ForgeScreenLink(Env env, Block block) {
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
    public Optional<ForgeSnapshot> snapshot() {
        return edit(record -> record);
    }

    @Override
    public Optional<ForgeSnapshot> commit(List<ItemStack> slots) {
        if (slots.size() != CharcoalForgeSim.SLOTS) {
            throw new IllegalArgumentException(slots.size() + " slots");
        }
        return edit(record -> withSlots(record, slots, env.stacks()));
    }

    private Optional<ForgeSnapshot> edit(java.util.function.UnaryOperator<DeviceRecord<CharcoalForgeData>> change) {
        if (!owned()) {
            return Optional.empty();
        }
        CharcoalForgeData[] seen = {null};
        Optional<DeviceRecord<CharcoalForgeData>> r = env.devices().edit(block, env.device().kind(), (record, now) -> {
            seen[0] = record.state();
            return change.apply(record);
        });
        env.device().pushHeat(block, seen[0]);
        return r.map(this::snapshotOf);
    }

    @Override
    public boolean isItemValid(int tfcSlot, ItemStack stack) {
        return env.device().isItemValid(tfcSlot, stack);
    }

    /** The fourteen slots stored. */
    static DeviceRecord<CharcoalForgeData> withSlots(DeviceRecord<CharcoalForgeData> record, List<ItemStack> slots,
            StackCodec stacks) {
        for (int i = 0; i < CharcoalForgeSim.SLOTS; i++) {
            record = record.withSlot(i, stacks.encode(slots.get(i)));
        }
        return record;
    }

    ForgeSnapshot snapshotOf(DeviceRecord<CharcoalForgeData> record) {
        StackCodec stacks = env.stacks();
        List<ItemStack> slots = new ArrayList<>(CharcoalForgeSim.SLOTS);
        for (int i = 0; i < CharcoalForgeSim.SLOTS; i++) {
            slots.add(stacks.decode(record.slot(i)));
        }
        return new ForgeSnapshot(record.state().temperature(), slots);
    }
}
