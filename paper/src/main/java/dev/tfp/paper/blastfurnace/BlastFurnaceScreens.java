/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/container/BlastFurnaceContainer.java @ c3b4d9536 (one slot, the tuyere),
 *       src/main/java/net/dries007/tfc/client/screen/BlastFurnaceScreen.java @ c3b4d9536 (the temperature, the fuel and
 *       input counts, the two tanks)
 */
package dev.tfp.paper.blastfurnace;

import dev.tfp.core.blastfurnace.BlastFurnaceData;
import dev.tfp.core.blastfurnace.BlastFurnaceRules;
import dev.tfp.core.blastfurnace.BlastFurnaceRules.Tank;
import dev.tfp.core.device.DeviceRecord;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.device.StackCodec;
import dev.tfp.paper.i18n.Messages;
import dev.tfp.paper.scheduler.Scheduler;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

/**
 * PAR-METAL-15: the blast furnace screen (TFC {@code BlastFurnaceScreen} + {@code BlastFurnaceContainer}): one slot for the
 * tuyere and a read-out of the temperature, the fuel, input and flux counts, the air and the two tanks. It is a plain
 * three-row chest menu (ledger divergence: no textured background and no bars; follow-up PAR-METAL-27). Every slot but the
 * tuyere refuses clicks. The furnace stays the authority: a click is committed by a region task of the furnace block and the
 * menu is reloaded from it, and an open menu reloads every {@link #RELOAD_TICKS} ticks so the viewer watches the heat.
 *
 * <p>Threads: inventory events run on the clicking player's thread, device edits on the furnace's region; the two hand over
 * through {@link Scheduler#entity} and {@link Scheduler#region}.
 */
public final class BlastFurnaceScreens implements Listener {
    public static final String TITLE_KEY = "gui.blast_furnace.title";
    /** How often an open screen reloads the furnace. */
    public static final long RELOAD_TICKS = 10;
    static final int ROWS = 3;
    /** The tuyere slot of the chest (top middle). */
    public static final int TUYERE_SLOT = 4;
    static final int TEMPERATURE = 10;
    static final int FUEL = 11;
    static final int INPUT = 12;
    static final int FLUX = 13;
    static final int MOLTEN = 14;
    static final int OUTPUT = 15;
    static final int AIR = 16;

    /** What the screen shows, read on the furnace's region. */
    record Snapshot(boolean present, ItemStack tuyere, BlastFurnaceData data, int fuel, int inputs, int catalysts, int capacity) {
    }

    /** The chest holder: which furnace the menu is for. */
    static final class Holder implements InventoryHolder {
        final Block furnace;
        Inventory inventory;
        volatile boolean closed;

        Holder(Block furnace) {
            this.furnace = furnace;
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }
    }

    private final BlastFurnaceDevice device;
    private final DeviceService devices;
    private final Scheduler scheduler;
    private final Messages messages;

    public BlastFurnaceScreens(BlastFurnaceDevice device, DeviceService devices, Scheduler scheduler, Messages messages) {
        this.device = Objects.requireNonNull(device, "device");
        this.devices = Objects.requireNonNull(devices, "devices");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    // ---- opening ---------------------------------------------------------------------------------------------

    /** Opens the screen for {@code player}: reads the furnace on its region, then opens the chest on the player's thread. */
    public void open(Player player, Block furnace) {
        scheduler.region(furnace.getWorld(), furnace.getX() >> 4, furnace.getZ() >> 4, () -> {
            Snapshot s = snapshot(furnace);
            if (!s.present()) {
                return;
            }
            scheduler.entity(player, () -> {
                if (!player.isOnline()) {
                    return;
                }
                Holder holder = new Holder(furnace);
                Inventory inv = Bukkit.createInventory(holder, ROWS * 9,
                    LegacyComponentSerializer.legacySection().deserialize(messages.get(player, TITLE_KEY)));
                holder.inventory = inv;
                render(player, inv, s);
                player.openInventory(inv);
                scheduler.entityRepeating(player, RELOAD_TICKS, RELOAD_TICKS, () -> reload(player, holder), () -> { });
            }, () -> { });
        });
    }

    /** The furnace caught up to now. Region thread of {@code furnace}. */
    Snapshot snapshot(Block furnace) {
        Optional<DeviceRecord<BlastFurnaceData>> rec = devices.edit(furnace, device.kind(), (record, now) -> record);
        if (rec.isEmpty()) {
            return new Snapshot(false, ItemStack.empty(), BlastFurnaceData.FRESH, 0, 0, 0, 0);
        }
        DeviceRecord<BlastFurnaceData> r = rec.get();
        int cap = BlastFurnaceRules.capacity(device.levels(furnace), device.services().config().get().capacityPerLevel());
        return new Snapshot(true, device.tuyere(r), r.state(), device.group(r, BlastFurnaceDevice.FUEL).size(),
            device.group(r, BlastFurnaceDevice.INPUT).size(), device.group(r, BlastFurnaceDevice.CATALYST).size(), cap);
    }

    private void reload(Player player, Holder holder) {
        scheduler.region(holder.furnace.getWorld(), holder.furnace.getX() >> 4, holder.furnace.getZ() >> 4, () -> {
            Snapshot s = snapshot(holder.furnace);
            scheduler.entity(player, () -> {
                if (!(player.getOpenInventory().getTopInventory().getHolder() == holder)) {
                    return;
                }
                if (!s.present()) {
                    player.closeInventory();
                    return;
                }
                render(player, holder.inventory, s);
            }, () -> { });
        });
    }

    // ---- rendering ---------------------------------------------------------------------------------------------

    private ItemStack icon(Player p, Material type, String key, Object... args) {
        ItemStack item = new ItemStack(type);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(LegacyComponentSerializer.legacySection().deserialize(messages.get(p, key, args))
            .decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }

    private static String fluid(Tank t) {
        return t.isEmpty() ? "-" : t.fluid();
    }

    /** Fills the chest from {@code s}. Player thread. */
    void render(Player p, Inventory inv, Snapshot s) {
        ItemStack pane = icon(p, Material.GRAY_STAINED_GLASS_PANE, "gui.blast_furnace.blank");
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, pane);
        }
        inv.setItem(TUYERE_SLOT, StackCodec.isEmpty(s.tuyere()) ? null : s.tuyere());
        BlastFurnaceData d = s.data();
        inv.setItem(TEMPERATURE, icon(p, Material.BLAZE_POWDER, "gui.blast_furnace.temperature",
            Math.round(d.state().temperature())));
        inv.setItem(FUEL, icon(p, Material.CHARCOAL, "gui.blast_furnace.fuel", s.fuel(), s.capacity()));
        inv.setItem(INPUT, icon(p, Material.IRON_INGOT, "gui.blast_furnace.input", s.inputs(), s.capacity()));
        inv.setItem(FLUX, icon(p, Material.GUNPOWDER, "gui.blast_furnace.flux", s.catalysts(), s.capacity()));
        inv.setItem(MOLTEN, icon(p, Material.LAVA_BUCKET, "gui.blast_furnace.molten", fluid(d.input()), d.input().amount()));
        inv.setItem(OUTPUT, icon(p, Material.BUCKET, "gui.blast_furnace.output", fluid(d.output()), d.output().amount(),
            device.services().config().get().fluidCapacity()));
        inv.setItem(AIR, icon(p, Material.WIND_CHARGE, "gui.blast_furnace.air", d.state().airTicks()));
    }

    // ---- clicks ------------------------------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof Holder holder) || !(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        boolean inTop = event.getClickedInventory() == top;
        if (inTop && event.getSlot() != TUYERE_SLOT) {
            event.setCancelled(true);
            return;
        }
        if (inTop) { // the tuyere slot: we do the move ourselves
            event.setCancelled(true);
            ItemStack cursor = event.getCursor();
            ItemStack slot = top.getItem(TUYERE_SLOT);
            if (!StackCodec.isEmpty(slot)) {
                commit(player, holder, null, true); // take it out (to the player)
            } else if (!StackCodec.isEmpty(cursor) && device.services().tuyereTag().test(cursor)) {
                ItemStack one = cursor.clone();
                one.setAmount(1);
                cursor.setAmount(cursor.getAmount() - 1);
                event.getView().setCursor(cursor.getAmount() > 0 ? cursor : null);
                commit(player, holder, one, false);
            }
            return;
        }
        if (event.isShiftClick() && inTopHasRoom(top)) { // a shift click from the player inventory
            event.setCancelled(true);
            ItemStack clicked = event.getCurrentItem();
            if (!StackCodec.isEmpty(clicked) && device.services().tuyereTag().test(clicked)) {
                ItemStack one = clicked.clone();
                one.setAmount(1);
                clicked.setAmount(clicked.getAmount() - 1);
                event.setCurrentItem(clicked.getAmount() > 0 ? clicked : null);
                commit(player, holder, one, false);
            }
            return;
        }
        if (event.getAction().name().startsWith("COLLECT") || event.isShiftClick()) {
            event.setCancelled(true);
        }
    }

    private static boolean inTopHasRoom(Inventory top) {
        return StackCodec.isEmpty(top.getItem(TUYERE_SLOT));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder) {
            for (int raw : event.getRawSlots()) {
                if (raw < ROWS * 9) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
    }

    @EventHandler
    public void onClose(org.bukkit.event.inventory.InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof Holder h) {
            h.closed = true;
        }
    }

    /**
     * Puts {@code put} into the tuyere slot, or (when {@code take}) takes the tuyere out to the player. The furnace's region
     * does it; an item that could not go in comes back to the player.
     */
    private void commit(Player player, Holder holder, ItemStack put, boolean take) {
        Block furnace = holder.furnace;
        scheduler.region(furnace.getWorld(), furnace.getX() >> 4, furnace.getZ() >> 4, () -> {
            ItemStack[] out = {null};
            boolean[] accepted = {false};
            devices.edit(furnace, device.kind(), (record, now) -> {
                if (take) {
                    out[0] = device.tuyere(record);
                    return record.withSlot(BlastFurnaceDevice.TUYERE, dev.tfp.core.device.ItemBlob.EMPTY);
                }
                if (record.slot(BlastFurnaceDevice.TUYERE).isEmpty()) {
                    accepted[0] = true;
                    return record.withSlot(BlastFurnaceDevice.TUYERE, devices.stacks().encode(put));
                }
                return record;
            });
            Snapshot s = snapshot(furnace);
            scheduler.entity(player, () -> {
                ItemStack back = take ? out[0] : accepted[0] ? null : put;
                if (!StackCodec.isEmpty(back)) {
                    giveBack(player, back);
                }
                if (holder.inventory != null && player.getOpenInventory().getTopInventory().getHolder() == holder && s.present()) {
                    render(player, holder.inventory, s);
                }
            }, () -> { });
        });
    }

    private static void giveBack(Player player, ItemStack stack) {
        List<ItemStack> left = new ArrayList<>(player.getInventory().addItem(stack).values());
        for (ItemStack l : left) {
            player.getWorld().dropItem(player.getLocation(), l);
        }
    }
}
