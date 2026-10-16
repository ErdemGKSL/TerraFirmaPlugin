package dev.tfp.spike.vis02;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import net.momirealms.craftengine.bukkit.api.CraftEngineBlocks;
import net.momirealms.craftengine.core.util.Key;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * M0-VIS-02 throwaway. A gem ore is a granite cube (same visual as the host rock). Every exposed
 * face (the neighbour cell is air) gets one item_display in that air cell: the TFC ruby overlay
 * quad, lying on the face plane and pushed {@code offset} blocks out into the air cell.
 * The display's own location is the air cell centre, so it samples the air cell's light.
 */
public final class Vis02Plugin extends JavaPlugin implements Listener {
    record Pos(int x, int y, int z) {
        Pos relative(BlockFace f) {
            return new Pos(x + f.getModX(), y + f.getModY(), z + f.getModZ());
        }
    }

    static final BlockFace[] FACES = {BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH,
            BlockFace.EAST, BlockFace.WEST};
    static final Key ROCK = Key.of("tfpvis02", "rock/granite");
    static final Key GEM = Key.of("tfpvis02", "ore/ruby_granite");
    static final NamespacedKey GEM_ITEM_MODEL = NamespacedKey.fromString("tfpvis02:gem/ruby");

    final Map<Pos, Map<BlockFace, ItemDisplay>> gems = new ConcurrentHashMap<>();
    volatile double offset = 0.01;
    final AtomicLong spawnPackets = new AtomicLong();
    final AtomicLong metaPackets = new AtomicLong();
    final AtomicLong otherPackets = new AtomicLong();

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        PacketEvents.getAPI().getEventManager().registerListener(new PacketListenerAbstract(PacketListenerPriority.MONITOR) {
            @Override
            public void onPacketSend(PacketSendEvent event) {
                if (event.getPacketType() == PacketType.Play.Server.SPAWN_ENTITY) {
                    spawnPackets.incrementAndGet();
                } else if (event.getPacketType() == PacketType.Play.Server.ENTITY_METADATA) {
                    metaPackets.incrementAndGet();
                }
            }
        });
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS,
                e -> e.registrar().register("vis02", "M0-VIS-02 spike", new Cmd()));
        getLogger().info("[vis02] enabled on " + Bukkit.getMinecraftVersion());
    }

    // ---- exposure -----------------------------------------------------------------------------

    World world() {
        return Bukkit.getWorlds().getFirst();
    }

    boolean isAir(Pos p) {
        return world().getBlockAt(p.x(), p.y(), p.z()).getType().isAir();
    }

    /** Adds or removes the overlay of each face so that it matches "neighbour is air". */
    void refreshGem(Pos gem) {
        Map<BlockFace, ItemDisplay> per = gems.computeIfAbsent(gem, g -> new EnumMap<>(BlockFace.class));
        for (BlockFace f : FACES) {
            Pos air = gem.relative(f);
            boolean exposed = isAir(air);
            ItemDisplay d = per.get(f);
            if (exposed && d == null) {
                per.put(f, spawnOverlay(air, f));
            } else if (!exposed && d != null) {
                d.remove();
                per.remove(f);
            }
        }
    }

    ItemDisplay spawnOverlay(Pos air, BlockFace towardGem) {
        // towardGem is the direction from the gem to the air cell; the overlay faces out of the gem
        Vector3f n = new Vector3f(towardGem.getModX(), towardGem.getModY(), towardGem.getModZ());
        // the entity sits at the air cell centre; move the quad from the cell centre to the face plane,
        // then out by `offset` (into the air cell)
        float back = (float) (0.5 - offset);
        Vector3f translation = new Vector3f(n).mul(-back);
        Quaternionf left = new Quaternionf().rotationTo(0f, 0f, 1f, n.x, n.y, n.z);
        Transformation t = new Transformation(translation, left, new Vector3f(1f, 1f, 1f), new Quaternionf());
        return world().spawn(new Location(world(), air.x() + 0.5, air.y() + 0.5, air.z() + 0.5), ItemDisplay.class, d -> {
            ItemStack stack = new ItemStack(Material.PAPER);
            var meta = stack.getItemMeta();
            meta.setItemModel(GEM_ITEM_MODEL);
            stack.setItemMeta(meta);
            d.setItemStack(stack);
            d.setItemDisplayTransform(org.bukkit.entity.ItemDisplay.ItemDisplayTransform.FIXED);
            d.setTransformation(t);
            d.setPersistent(false);
        });
    }

    void clearOverlays() {
        for (Map<BlockFace, ItemDisplay> per : gems.values()) {
            per.values().forEach(ItemDisplay::remove);
        }
        gems.clear();
    }

    // ---- scene --------------------------------------------------------------------------------

    static final int BASE_Y = 150;

    void setRock(int x, int y, int z) {
        CraftEngineBlocks.place(new Location(world(), x, y, z), ROCK, false);
    }

    void setGem(int x, int y, int z) {
        CraftEngineBlocks.place(new Location(world(), x, y, z), GEM, false);
    }

    void setAir(int x, int y, int z) {
        world().getBlockAt(x, y, z).setType(Material.AIR, false);
    }

    /** Scene layout, origin x=0, z=0, floor at y=149 (rock), gems at y=150 unless noted. */
    void build() {
        clearOverlays();
        // ground: rock layer y=149 for x 0..15, z 0..11
        for (int x = 0; x <= 15; x++) {
            for (int z = 0; z <= 11; z++) {
                setRock(x, BASE_Y - 1, z);
            }
        }
        // ground gems: even x and even z, one layer on the rock, air around (5 exposed faces each)
        for (int x = 0; x <= 14; x += 2) {
            for (int z = 0; z <= 6; z += 2) {
                setGem(x, BASE_Y, z);
            }
        }
        // wall at z 10..11, y 150..155; gems on the row y=152 at z=10 (1 exposed face each: toward z=9)
        for (int x = 0; x <= 15; x++) {
            for (int y = BASE_Y; y <= BASE_Y + 5; y++) {
                for (int z = 10; z <= 11; z++) {
                    setRock(x, y, z);
                }
            }
        }
        for (int x = 0; x <= 14; x += 2) {
            setGem(x, BASE_Y + 2, 10);
        }
        // cave mass x 20..31, y 149..153, z 0..9, carved corridor x 21..30, y 150..151, z 3..5 (enclosed)
        for (int x = 20; x <= 31; x++) {
            for (int y = BASE_Y - 1; y <= BASE_Y + 3; y++) {
                for (int z = 0; z <= 9; z++) {
                    setRock(x, y, z);
                }
            }
        }
        for (int x = 21; x <= 30; x++) {
            for (int y = BASE_Y; y <= BASE_Y + 1; y++) {
                for (int z = 3; z <= 5; z++) {
                    setAir(x, y, z);
                }
            }
        }
        for (int x = 21; x <= 30; x += 2) {
            setGem(x, BASE_Y, 2);           // corridor side wall, exposed toward z=3
        }
        for (int x = 21; x <= 30; x += 3) {
            setGem(x, BASE_Y + 2, 4);       // corridor ceiling, exposed toward y=151
        }
        world().getBlockAt(26, BASE_Y, 4).setType(Material.TORCH, false);
        // zero-offset comparison wall (same as the wall above, placed at z 14..15 for z-fighting checks)
        for (int x = 0; x <= 15; x++) {
            for (int y = BASE_Y; y <= BASE_Y + 5; y++) {
                for (int z = 14; z <= 15; z++) {
                    setRock(x, y, z);
                }
            }
        }
        for (int x = 0; x <= 14; x += 2) {
            setGem(x, BASE_Y + 2, 14);
        }
        for (int x = 0; x <= 15; x++) {
            setRock(x, BASE_Y - 1, 12);
            setRock(x, BASE_Y - 1, 13);
            setRock(x, BASE_Y - 1, 14);
            setRock(x, BASE_Y - 1, 15);
        }
    }

    /** All gem positions in the current scene (the layout above). */
    List<Pos> scenePositions() {
        List<Pos> out = new ArrayList<>();
        for (int x = 0; x <= 14; x += 2) for (int z = 0; z <= 6; z += 2) out.add(new Pos(x, BASE_Y, z));
        for (int x = 0; x <= 14; x += 2) out.add(new Pos(x, BASE_Y + 2, 10));
        for (int x = 21; x <= 30; x += 2) out.add(new Pos(x, BASE_Y, 2));
        for (int x = 21; x <= 30; x += 3) out.add(new Pos(x, BASE_Y + 2, 4));
        for (int x = 0; x <= 14; x += 2) out.add(new Pos(x, BASE_Y + 2, 14));
        return out;
    }

    // ---- events: keep exposure in sync for edits made in game ---------------------------------

    @EventHandler
    public void onPlace(BlockPlaceEvent e) {
        changed(e.getBlockPlaced().getX(), e.getBlockPlaced().getY(), e.getBlockPlaced().getZ());
    }

    @EventHandler
    public void onBreak(BlockBreakEvent e) {
        int x = e.getBlock().getX(), y = e.getBlock().getY(), z = e.getBlock().getZ();
        Bukkit.getScheduler().runTask(this, () -> changed(x, y, z));
    }

    void changed(int x, int y, int z) {
        Pos p = new Pos(x, y, z);
        Bukkit.getScheduler().runTask(this, () -> {
            for (BlockFace f : FACES) {
                Pos n = p.relative(f);
                if (gems.containsKey(n)) {
                    refreshGem(n);
                }
            }
            if (gems.containsKey(p) && world().getBlockAt(p.x(), p.y(), p.z()).getType().isAir()) {
                Map<BlockFace, ItemDisplay> per = gems.remove(p);
                per.values().forEach(ItemDisplay::remove);
            }
        });
    }

    // ---- commands -----------------------------------------------------------------------------

    final class Cmd implements BasicCommand {
        @Override
        public void execute(CommandSourceStack src, String[] args) {
            String sub = args.length > 0 ? args[0] : "";
            Runnable r = null;
            switch (sub) {
                case "build" -> r = () -> {
                    build();
                    for (Pos p : scenePositions()) {
                        gems.putIfAbsent(p, new EnumMap<>(BlockFace.class));
                    }
                    long t0 = System.nanoTime();
                    for (Pos p : scenePositions()) {
                        refreshGem(p);
                    }
                    long ms = (System.nanoTime() - t0) / 1_000_000;
                    int displays = gems.values().stream().mapToInt(Map::size).sum();
                    report(src, "built scene: gems=" + gems.size() + " displays=" + displays + " spawn_ms=" + ms);
                };
                case "offset" -> {
                    offset = Double.parseDouble(args[1]);
                    report(src, "offset=" + offset + " (run 'vis02 refresh')");
                }
                case "refresh" -> r = () -> {
                    List<Pos> all = new ArrayList<>(gems.keySet());
                    clearOverlaysKeepGems();
                    for (Pos p : all) {
                        gems.putIfAbsent(p, new EnumMap<>(BlockFace.class));
                    }
                    long t0 = System.nanoTime();
                    for (Pos p : all) {
                        refreshGem(p);
                    }
                    report(src, "refreshed: displays=" + gems.values().stream().mapToInt(Map::size).sum()
                            + " offset=" + offset + " spawn_ms=" + (System.nanoTime() - t0) / 1_000_000);
                };
                case "measure" -> r = () -> {
                    List<Pos> all = new ArrayList<>(gems.keySet());
                    clearOverlaysKeepGems();
                    spawnPackets.set(0);
                    metaPackets.set(0);
                    long t0 = System.nanoTime();
                    for (Pos p : all) {
                        gems.putIfAbsent(p, new EnumMap<>(BlockFace.class));
                    }
                    for (Pos p : all) {
                        refreshGem(p);
                    }
                    long spawnNanos = System.nanoTime() - t0;
                    int displays = gems.values().stream().mapToInt(Map::size).sum();
                    Bukkit.getScheduler().runTaskLater(Vis02Plugin.this, () -> {
                        report(src, "MEASURE gems=" + gems.size() + " exposed_faces=" + displays
                                + " spawn_packets=" + spawnPackets.get() + " metadata_packets=" + metaPackets.get()
                                + " packets_per_face=" + String.format("%.2f", (spawnPackets.get() + metaPackets.get()) / (double) Math.max(1, displays))
                                + " main_thread_spawn_ms=" + spawnNanos / 1_000_000
                                + " us_per_face=" + String.format("%.1f", spawnNanos / 1000.0 / Math.max(1, displays)));
                    }, 60L);
                };
                case "status" -> r = () -> {
                    int displays = gems.values().stream().mapToInt(Map::size).sum();
                    report(src, "gems=" + gems.size() + " displays=" + displays + " offset=" + offset);
                };
                case "clear" -> r = Vis02Plugin.this::clearOverlays;
                default -> report(src, "/vis02 build | offset <v> | refresh | measure | status | clear");
            }
            if (r != null) {
                Bukkit.getScheduler().runTask(Vis02Plugin.this, r);
            }
        }

        void report(CommandSourceStack src, String msg) {
            getLogger().info("[vis02] " + msg);
            src.getSender().sendMessage("[vis02] " + msg);
        }
    }

    void clearOverlaysKeepGems() {
        for (Map<BlockFace, ItemDisplay> per : gems.values()) {
            per.values().forEach(ItemDisplay::remove);
            per.clear();
        }
    }
}
