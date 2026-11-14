/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/container/BarrelContainer.java @ c3b4d9536 (the three slots, onButtonPress),
 * file: src/main/java/net/dries007/tfc/common/blockentities/BarrelBlockEntity.java @ c3b4d9536 (getRecipeTooltip,
 * getSealedTick), file: src/main/java/net/dries007/tfc/client/screen/BarrelScreen.java @ c3b4d9536 (renderLabels: the
 * recipe name and getExactTimeAndDate of the seal tick)
 */
package dev.tfp.paper.barrel;

import dev.tfp.core.barrel.BarrelData;
import dev.tfp.core.clock.ClockSnapshot;
import dev.tfp.core.clock.GlobalClockHolder;
import dev.tfp.core.device.DeviceRecord;
import dev.tfp.paper.barrel.gui.BarrelLink;
import dev.tfp.paper.barrel.gui.BarrelScreenLayout;
import dev.tfp.paper.barrel.gui.BarrelSnapshot;
import dev.tfp.paper.device.DeviceKind;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.device.StackCodec;
import dev.tfp.paper.i18n.Messages;
import dev.tfp.paper.scheduler.Scheduler;
import dev.tfp.paper.tooltip.TimeDeltaStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * PAR-PROC-35: the {@link BarrelLink} of a placed barrel, over its device record. Every call loads the record through
 * {@link DeviceService#edit} (catch-up, then {@link BarrelDevice#settle}: the rain fill and the fluid IO slots), changes it
 * and stores it, then answers the barrel as stored.
 *
 * <p>Threads: the screen calls on the viewer's thread; the record lives on the block's region. A player using a barrel is
 * next to it, so on Paper and normally on Folia the viewer's thread owns the block. When it does not (Folia, the regions
 * split while the screen is open), every call answers empty: the screen gives the viewer back what it held since the last
 * commit and closes, so nothing is lost or duplicated (ledger divergence).
 */
public final class BarrelScreenLink implements BarrelLink {
    /** The screen key of a barrel: its block position. */
    public record Key(UUID world, int x, int y, int z) {
        public static Key of(Block block) {
            return new Key(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
        }
    }

    /**
     * What a link needs.
     *
     * @param devices  the device service
     * @param kind     the barrel kind registered there
     * @param services the barrel's services
     * @param stacks   the slot codec
     * @param scheduler for the ownership check
     * @param messages the viewer's texts
     * @param clock    the calendar (the seal date)
     */
    public record Env(DeviceService devices, DeviceKind<BarrelData> kind, BarrelDevice.Services services, StackCodec stacks,
            Scheduler scheduler, Messages messages, GlobalClockHolder clock) {
        public Env {
            Objects.requireNonNull(devices, "devices");
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(services, "services");
            Objects.requireNonNull(stacks, "stacks");
            Objects.requireNonNull(scheduler, "scheduler");
            Objects.requireNonNull(messages, "messages");
            Objects.requireNonNull(clock, "clock");
        }
    }

    private final Env env;
    private final Block block;
    private final String blockId;

    public BarrelScreenLink(Env env, Block block, String blockId) {
        this.env = Objects.requireNonNull(env, "env");
        this.block = Objects.requireNonNull(block, "block");
        this.blockId = Objects.requireNonNull(blockId, "blockId");
    }

    public Key key() {
        return Key.of(block);
    }

    private boolean owned() {
        return env.scheduler().isOwner(block.getWorld(), block.getX() >> 4, block.getZ() >> 4);
    }

    /** Settles the record, applies {@code change} to the settled record, stores it; the fluid surface follows. */
    private Optional<BarrelSnapshot> edit(Change change) {
        if (!owned()) {
            return Optional.empty();
        }
        BarrelDevice.Services s = env.services();
        boolean raining = s.rain().rainingAbove(block);
        BarrelData[] before = new BarrelData[1];
        Optional<DeviceRecord<BarrelData>> stored = env.devices().edit(block, env.kind(), (record, now) -> {
            before[0] = record.state();
            DeviceRecord<BarrelData> settled = BarrelDevice.settle(record, now, raining, block, s, env.stacks());
            DeviceRecord<BarrelData> next = change.apply(settled, now);
            return next == null ? settled : next;
        });
        stored.ifPresent(r -> BarrelDevice.surface(block, before[0], r.state(), s));
        return stored.map(this::snapshotOf);
    }

    @FunctionalInterface
    private interface Change {
        /** The settled record changed, or null to keep it. */
        DeviceRecord<BarrelData> apply(DeviceRecord<BarrelData> settled, long now);
    }

    BarrelSnapshot snapshotOf(DeviceRecord<BarrelData> record) {
        List<ItemStack> slots = new ArrayList<>(BarrelScreenLayout.TFC_SLOTS);
        slots.add(env.stacks().decode(record.slot(BarrelDevice.SLOT_FLUID_CONTAINER_IN)));
        slots.add(env.stacks().decode(record.slot(BarrelDevice.SLOT_FLUID_CONTAINER_OUT)));
        slots.add(env.stacks().decode(record.slot(BarrelDevice.SLOT_ITEM)));
        BarrelData d = record.state();
        return new BarrelSnapshot(d.sealed(), d.fluid(), env.services().book().capacity(), slots);
    }

    @Override
    public Optional<BarrelSnapshot> snapshot() {
        return edit((settled, now) -> null);
    }

    @Override
    public Optional<BarrelSnapshot> commit(List<ItemStack> slots) {
        if (slots.size() != BarrelScreenLayout.TFC_SLOTS) {
            throw new IllegalArgumentException(slots.size() + " slots");
        }
        boolean[] refused = new boolean[1];
        Optional<BarrelSnapshot> after = edit((settled, now) -> {
            if (settled.state().sealed()) {
                // sealed by another path since the screen last saw it: the viewer's copy cannot go in
                refused[0] = true;
                return null;
            }
            return BarrelDevice.withSlots(settled, slots.get(BarrelScreenLayout.TFC_FLUID_IN),
                slots.get(BarrelScreenLayout.TFC_FLUID_OUT), slots.get(BarrelScreenLayout.TFC_ITEM), block,
                env.services(), env.stacks());
        });
        if (refused[0] && after.isPresent() && !BarrelSnapshot.sameSlots(after.get().slots(), slots)) {
            return Optional.empty(); // the screen gives the viewer back what differs, and closes
        }
        return after;
    }

    @Override
    public Optional<BarrelSnapshot> toggleSeal(Player viewer) {
        return edit((settled, now) -> BarrelDevice.toggleSeal(settled, now, block, blockId, env.services(), env.stacks()));
    }

    @Override
    public boolean acceptsContainer(ItemStack stack) {
        return env.services().book().tanks().tank(stack).isPresent();
    }

    @Override
    public boolean acceptsItem(ItemStack stack) {
        return env.services().fits().test(stack);
    }

    /** TFC {@code BarrelScreen.renderLabels} while sealed: the recipe's name (if any), then the seal date. */
    @Override
    public List<String> sealedLines(Player viewer, BarrelSnapshot barrel) {
        if (!owned()) {
            return List.of();
        }
        Optional<DeviceRecord<BarrelData>> record = env.devices().edit(block, env.kind(), (r, now) -> r);
        if (record.isEmpty() || !record.get().state().sealed()) {
            return List.of();
        }
        List<String> lines = new ArrayList<>(2);
        BarrelRecipeView recipe = env.services().book().session()
            .select(BarrelDevice.contents(record.get(), env.stacks()));
        String locale = Messages.code(viewer);
        if (recipe != null) {
            lines.add(recipeName(recipe.id(), locale));
        }
        ClockSnapshot clock = env.clock().get();
        long calendar = clock.calendarTicks() + (record.get().state().sealedTick() - clock.playerTicks());
        lines.add(TimeDeltaStyle.timeAndDate(calendar, clock.daysInMonth(), locale, env.messages()::get));
        return lines;
    }

    /**
     * TFC {@code getRecipeTooltip}: the key {@code tfc.recipe.barrel.<namespace>.<path with dots>}; TFC's own English
     * names are client lang the server does not ship, so a missing key shows the recipe path in words.
     */
    String recipeName(String recipeId, String locale) {
        int colon = recipeId.indexOf(':');
        String ns = colon < 0 ? "minecraft" : recipeId.substring(0, colon);
        String path = colon < 0 ? recipeId : recipeId.substring(colon + 1);
        String key = "tfc.recipe.barrel." + ns + "." + path.replace('/', '.');
        String name = env.messages().get(locale, key);
        if (!name.equals(key)) {
            return name;
        }
        String last = path.substring(path.lastIndexOf('/') + 1);
        StringBuilder b = new StringBuilder();
        for (String w : last.split("_")) {
            if (!w.isEmpty()) {
                if (!b.isEmpty()) {
                    b.append(' ');
                }
                b.append(w.substring(0, 1).toUpperCase(Locale.ROOT)).append(w.substring(1));
            }
        }
        return b.toString();
    }
}
