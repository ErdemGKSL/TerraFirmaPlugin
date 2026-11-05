/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.spike.s2;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.event.block.BlockBreakProgressUpdateEvent;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.key.Key;
import net.momirealms.craftengine.bukkit.api.CraftEngineBlocks;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageAbortEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * PAR-INFRA-17 (spike S2, not shipped): the feel of renderer-drawn blocks. The renderer cube itself is the CraftEngine
 * block {@code tfps2:metal/block/copper} from the generated {@code tfp:block/rendered_cube} template (pack copied by
 * {@code build-pack.sh}); this plugin only places it ({@code s2 place}) and measures. The overlay ore is built here:
 * {@code s2 overlay} sets vanilla granite (the host rock look) and puts one {@code item_display} quad
 * ({@code tfps2:overlay}, TFC's normal_native_gold overlay) on each exposed face, 0.01 in front of it, lit by the open
 * cell (the planned PAR-INFRA-16 geometry). While the block is mined the quads follow the server's dig progress:
 * mode {@code crack} swaps them to the cracked variant {@code tfps2:overlay_crack_<n>} (overlay + vanilla
 * destroy_stage_n, composite), mode {@code dim} halves their brightness, mode {@code none} leaves them.
 *
 * <p>Logs ({@code [S2]} lines): every stage change of a mined block and the break time in ticks from the first
 * {@code BlockDamageEvent} to the {@code BlockBreakEvent}, with the tool. Overlays whose host block is gone (break,
 * {@code /fill}, TNT) are removed within 2 ticks.
 */
public final class S2Spike extends JavaPlugin implements Listener {
    private static final BlockFace[] FACES = {BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST};
    private final Map<Location, List<ItemDisplay>> overlays = new ConcurrentHashMap<>();
    private final Map<String, Long> digStart = new ConcurrentHashMap<>();
    private final Map<Location, Integer> stages = new ConcurrentHashMap<>();
    private volatile String mode = "crack";

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        com.github.retrooper.packetevents.PacketEvents.getAPI().getEventManager().registerListener(new SoundLog(getLogger()),
            com.github.retrooper.packetevents.event.PacketListenerPriority.MONITOR);
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS,
            e -> e.registrar().register("s2", "PAR-INFRA-17 renderer cube spike", new Cmd()));
    }

    private static ItemStack model(String id) {
        ItemStack s = new ItemStack(Material.PAPER);
        s.setData(DataComponentTypes.ITEM_MODEL, Key.key(id));
        return s;
    }

    private void spawnOverlays(Block b) {
        clear(b.getLocation());
        List<ItemDisplay> list = new ArrayList<>();
        for (BlockFace f : FACES) {
            Block open = b.getRelative(f);
            if (open.getType().isOccluding()) {
                continue;
            }
            Location at = open.getLocation().add(0.5, 0.5, 0.5);
            Vector3f n = new Vector3f(f.getModX(), f.getModY(), f.getModZ());
            ItemDisplay d = b.getWorld().spawn(at, ItemDisplay.class, e -> {
                e.setPersistent(false);
                e.setItemStack(model("tfps2:overlay"));
                e.setViewRange(0.75f);
                e.setTransformation(new Transformation(new Vector3f(n).mul(-0.49f),
                    new Quaternionf().rotationTo(new Vector3f(0, 0, 1), n), new Vector3f(1, 1, 1), new Quaternionf()));
            });
            list.add(d);
        }
        overlays.put(b.getLocation(), list);
        Bukkit.getRegionScheduler().runAtFixedRate(this, b.getLocation(), t -> {
            if (!overlays.containsKey(b.getLocation())) {
                t.cancel();
            } else if (b.getType() != Material.GRANITE) {
                getLogger().info("[S2] host gone at " + key(b) + " (" + b.getType() + "), overlays removed");
                clear(b.getLocation());
                t.cancel();
            }
        }, 1, 2);
    }

    private void clear(Location l) {
        List<ItemDisplay> old = overlays.remove(l);
        if (old != null) {
            old.forEach(ItemDisplay::remove);
        }
        stages.remove(l);
    }

    private void showStage(Location l, int stage) {
        List<ItemDisplay> list = overlays.get(l);
        if (list == null) {
            return;
        }
        for (ItemDisplay d : list) {
            switch (mode) {
                case "crack" -> d.setItemStack(model(stage < 0 ? "tfps2:overlay" : "tfps2:overlay_crack_" + stage));
                case "dim" -> {
                    if (stage < 0) {
                        d.setBrightness(null);
                    } else {
                        Block open = d.getLocation().getBlock();
                        d.setBrightness(new Display.Brightness(open.getLightFromBlocks() / 2, open.getLightFromSky() / 2));
                    }
                }
                default -> {
                }
            }
        }
    }

    private static String key(Block b) {
        return b.getX() + "," + b.getY() + "," + b.getZ();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(BlockDamageEvent e) {
        digStart.put(e.getPlayer().getName() + "@" + key(e.getBlock()), (long) Bukkit.getCurrentTick());
        getLogger().info("[S2] dig start " + e.getBlock().getType() + " at " + key(e.getBlock()) + " by " + e.getPlayer().getName()
            + " tool=" + e.getItemInHand().getType() + " instabreak=" + e.getInstaBreak());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onAbort(BlockDamageAbortEvent e) {
        getLogger().info("[S2] dig abort at " + key(e.getBlock()) + " by " + e.getPlayer().getName());
        stages.remove(e.getBlock().getLocation());
        showStage(e.getBlock().getLocation(), -1);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onProgress(BlockBreakProgressUpdateEvent e) {
        int stage = e.getProgress() <= 0 ? -1 : Math.min(9, (int) (e.getProgress() * 10));
        Location l = e.getBlock().getLocation();
        Integer old = stages.put(l, stage);
        if (old == null || old != stage) {
            getLogger().info("[S2] stage " + stage + " progress " + e.getProgress() + " at " + key(e.getBlock()) + " tick "
                + Bukkit.getCurrentTick());
            showStage(l, stage);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        Long start = digStart.remove(p.getName() + "@" + key(e.getBlock()));
        String id = CraftEngineBlocks.isCustomBlock(e.getBlock())
            ? String.valueOf(CraftEngineBlocks.getCustomBlockState(e.getBlock())) : e.getBlock().getType().toString();
        getLogger().info("[S2] break " + id + " at " + key(e.getBlock()) + " by " + p.getName() + " tool="
            + p.getInventory().getItemInMainHand().getType() + " ticks=" + (start == null ? "?" : Bukkit.getCurrentTick() - start)
            + " mode=" + p.getGameMode());
    }

    final class Cmd implements BasicCommand {
        @Override
        public void execute(CommandSourceStack source, String[] args) {
            var out = source.getSender();
            World w = Bukkit.getWorlds().getFirst();
            try {
                switch (args.length == 0 ? "" : args[0]) {
                    case "place" -> {
                        Location l = new Location(w, Integer.parseInt(args[2]), Integer.parseInt(args[3]), Integer.parseInt(args[4]));
                        Bukkit.getRegionScheduler().execute(S2Spike.this, l, () -> out.sendMessage("[S2] place " + args[1] + " "
                            + CraftEngineBlocks.place(l, net.momirealms.craftengine.core.util.Key.of(args[1]), true)));
                    }
                    case "overlay" -> {
                        Location l = new Location(w, Integer.parseInt(args[1]), Integer.parseInt(args[2]), Integer.parseInt(args[3]));
                        Bukkit.getRegionScheduler().execute(S2Spike.this, l, () -> {
                            l.getBlock().setType(Material.GRANITE);
                            spawnOverlays(l.getBlock());
                            out.sendMessage("[S2] overlay ore at " + key(l.getBlock()) + " quads " + overlays.get(l).size());
                        });
                    }
                    case "mode" -> {
                        mode = args[1];
                        out.sendMessage("[S2] mode " + mode);
                    }
                    default -> out.sendMessage("s2 place <ce id> <x> <y> <z> | overlay <x> <y> <z> | mode crack|dim|none");
                }
            } catch (RuntimeException ex) {
                out.sendMessage("[S2] error " + ex);
            }
        }
    }
}
