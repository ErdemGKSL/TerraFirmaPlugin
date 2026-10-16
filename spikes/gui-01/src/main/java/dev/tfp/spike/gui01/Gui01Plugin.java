package dev.tfp.spike.gui01;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientClickWindow;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import java.util.concurrent.atomic.AtomicInteger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerRecipeBookSettingsChangeEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * M0-GUI-01 throwaway. Logs every click that reaches the server (PacketEvents and Bukkit), the
 * recipe-book toggle event, and opens a textured 6-row chest GUI.
 *
 * Trigger under test: an empty-cursor click outside the player inventory window = container click
 * with slot -999 while the player's own inventory view (InventoryType.CRAFTING) is open.
 * Log prefix "[gui01]" so the server log can be grepped.
 */
public final class Gui01Plugin extends JavaPlugin implements Listener {
    /** Set by the GUI title: CE rewrites the image tag when the title is sent. */
    static final String CHEST_TITLE = "<shift:-8><image:tfpgui:gui/chest6>";
    static final AtomicInteger PACKET_CLICKS = new AtomicInteger();
    static final AtomicInteger PACKET_OUTSIDE = new AtomicInteger();

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        PacketEvents.getAPI().getEventManager().registerListener(new PacketListenerAbstract(PacketListenerPriority.MONITOR) {
            @Override
            public void onPacketReceive(PacketReceiveEvent event) {
                if (event.getPacketType() != PacketType.Play.Client.CLICK_WINDOW) {
                    return;
                }
                WrapperPlayClientClickWindow w = new WrapperPlayClientClickWindow(event);
                PACKET_CLICKS.incrementAndGet();
                if (w.getSlot() == -999) {
                    PACKET_OUTSIDE.incrementAndGet();
                }
                getLogger().info("[gui01] PACKET click windowId=" + w.getWindowId() + " slot=" + w.getSlot()
                        + " button=" + w.getButton() + " type=" + w.getWindowClickType()
                        + " carried=" + w.getCarriedItemStack().getType() + "x" + w.getCarriedItemStack().getAmount()
                        + " user=" + event.getUser().getName());
            }
        });
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS,
                e -> e.registrar().register("gui01", "M0-GUI-01 spike", new Cmd()));
        getLogger().info("[gui01] enabled on " + Bukkit.getMinecraftVersion());
    }

    /** Bukkit view of the same click. Runs on the server thread after the packet. */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onClick(InventoryClickEvent e) {
        if (e.getRawSlot() != -999) {
            return;
        }
        ItemStack cursor = e.getCursor();
        boolean emptyCursor = cursor == null || cursor.getType().isAir();
        getLogger().info("[gui01] BUKKIT outside-click view=" + e.getView().getType() + " click=" + e.getClick()
                + " slotType=" + e.getSlotType() + " rawSlot=" + e.getRawSlot() + " emptyCursor=" + emptyCursor
                + " clickedInv=" + (e.getClickedInventory() == null ? "null" : e.getClickedInventory().getType())
                + " player=" + e.getWhoClicked().getName());
        if (e.getView().getType() == InventoryType.CRAFTING && emptyCursor) {
            e.setCancelled(true);
            Player p = (Player) e.getWhoClicked();
            Bukkit.getScheduler().runTask(this, () -> p.openInventory(newChest()));
            getLogger().info("[gui01] TRIGGER fired: opening chest6 GUI for " + p.getName());
        }
    }

    @EventHandler
    public void onRecipeBook(PlayerRecipeBookSettingsChangeEvent e) {
        getLogger().info("[gui01] RECIPEBOOK player=" + e.getPlayer().getName() + " type=" + e.getRecipeBookType()
                + " open=" + e.isOpen() + " filtering=" + e.isFiltering());
    }

    static Inventory newChest() {
        Inventory inv = Bukkit.createInventory(null, 54, Component.text(CHEST_TITLE, NamedTextColor.WHITE));
        return inv;
    }

    final class Cmd implements BasicCommand {
        @Override
        public void execute(CommandSourceStack src, String[] args) {
            if (!(src.getSender() instanceof Player p)) {
                src.getSender().sendMessage("players only");
                return;
            }
            String sub = args.length > 0 ? args[0] : "";
            switch (sub) {
                case "chest" -> {
                    Inventory inv = newChest();
                    if (args.length > 1 && args[1].equals("items")) {
                        // one item per slot column so an item centre can be checked against the frame
                        for (int r = 0; r < 6; r++) {
                            for (int c = 0; c < 9; c++) {
                                inv.setItem(r * 9 + c, new ItemStack(c % 2 == 0 ? Material.STONE : Material.DIAMOND));
                            }
                        }
                    }
                    p.openInventory(inv);
                }
                case "crafting" -> p.openWorkbench(null, true);
                case "counters" -> p.sendMessage("packet clicks=" + PACKET_CLICKS.get() + " outside=" + PACKET_OUTSIDE.get());
                default -> p.sendMessage("/gui01 chest [items] | crafting | counters");
            }
        }
    }
}
