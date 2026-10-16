package dev.tfp.spike.net01;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListener;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.component.ComponentTypes;
import com.github.retrooper.packetevents.protocol.component.builtin.item.ItemCustomModelData;
import com.github.retrooper.packetevents.protocol.component.builtin.item.ItemLore;
import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.protocol.item.type.ItemTypes;
import com.github.retrooper.packetevents.protocol.mapper.MappedEntity;
import com.github.retrooper.packetevents.protocol.nbt.NBTCompound;
import com.github.retrooper.packetevents.protocol.nbt.NBTInt;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.protocol.world.chunk.BaseChunk;
import com.github.retrooper.packetevents.protocol.world.chunk.impl.v_1_18.Chunk_v1_18;
import com.github.retrooper.packetevents.protocol.world.chunk.palette.DataPalette;
import com.github.retrooper.packetevents.resources.ResourceLocation;
import com.github.retrooper.packetevents.util.mappings.IRegistry;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientCreativeInventoryAction;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerChunkData;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetSlot;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerWindowItems;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.entity.Player;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * M0-NET-01: (1) seasonal biome remap in outgoing chunk packets, (2) display-only lore +
 * custom_model_data on outgoing items, stripped again from creative-mode returns.
 * Commands (console/RCON): /net01 season summer|autumn, /net01 give <player>, /net01 creativetest <player>,
 * /net01 pipeline <player>, /net01 report.
 */
public final class Net01Plugin extends JavaPlugin {
    static final NamespacedKey SUMMER = new NamespacedKey("tfpspike", "plains_summer");
    static final NamespacedKey AUTUMN = new NamespacedKey("tfpspike", "plains_autumn");
    static final String MARK = "tfp:display"; // custom_data key: number of lore lines we appended

    volatile boolean autumn;
    final Map<String, AtomicLong> threads = new ConcurrentHashMap<>();
    final AtomicLong chunkPackets = new AtomicLong(), chunkNanos = new AtomicLong(), chunkMaxNanos = new AtomicLong(),
            itemPackets = new AtomicLong(), itemNanos = new AtomicLong(), stripped = new AtomicLong(),
            remappedCells = new AtomicLong();
    /** per user: [summerId, autumnId] network ids from the user's synchronized biome registry */
    final Map<User, int[]> ids = new ConcurrentHashMap<>();
    final List<String> notes = new ArrayList<>();

    @Override
    public ChunkGenerator getDefaultWorldGenerator(String worldName, String id) {
        return new ChunkGenerator() {
            @Override
            public void generateSurface(WorldInfo info, Random r, int cx, int cz, ChunkData d) {
                d.setRegion(0, d.getMinHeight(), 0, 16, 60, 16, Material.STONE);
                d.setRegion(0, 60, 0, 16, 63, 16, Material.DIRT);
                d.setRegion(0, 63, 0, 16, 64, 16, Material.GRASS_BLOCK);
                for (int i = 0; i < 6; i++) { // some leaves and grass to show foliage/grass tint
                    int x = r.nextInt(16), z = r.nextInt(16);
                    d.setBlock(x, 64, z, Material.SHORT_GRASS);
                    d.setBlock(r.nextInt(16), 64, r.nextInt(16), Material.OAK_LEAVES);
                }
            }

            @Override
            public BiomeProvider getDefaultBiomeProvider(WorldInfo info) {
                return new BiomeProvider() {
                    @Override
                    public Biome getBiome(WorldInfo w, int x, int y, int z) {
                        return RegistryAccess.registryAccess().getRegistry(RegistryKey.BIOME).getOrThrow(SUMMER);
                    }

                    @Override
                    public List<Biome> getBiomes(WorldInfo w) {
                        return List.of(RegistryAccess.registryAccess().getRegistry(RegistryKey.BIOME).getOrThrow(SUMMER));
                    }
                };
            }

            @Override
            public Location getFixedSpawnLocation(World world, Random random) {
                return new Location(world, 0.5, 65, 0.5);
            }
        };
    }

    @Override
    public void onEnable() {
        PacketEvents.getAPI().getEventManager().registerListener(new Listener(), PacketListenerPriority.NORMAL);
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS,
                e -> e.registrar().register("net01", "M0-NET-01 spike", new Cmd()));
    }

    void thread(String what) {
        threads.computeIfAbsent(what + "@" + Thread.currentThread().getName().replaceAll("\\d+", "N"),
                k -> new AtomicLong()).incrementAndGet();
    }

    int[] idsFor(User user) {
        return ids.computeIfAbsent(user, u -> {
            IRegistry<?> reg = u.getRegistry(ResourceLocation.minecraft("worldgen/biome"), u.getClientVersion());
            MappedEntity s = reg.getByName(SUMMER.toString()), a = reg.getByName(AUTUMN.toString());
            int[] r = {s == null ? -1 : s.getId(u.getClientVersion()), a == null ? -1 : a.getId(u.getClientVersion())};
            notes.add("biome ids for " + u.getName() + ": summer=" + r[0] + " autumn=" + r[1] + " registrySize=" + reg.size());
            return r;
        });
    }

    final class Listener implements PacketListener {
        @Override
        public void onPacketSend(PacketSendEvent e) {
            if (e.getPacketType() == PacketType.Play.Server.CHUNK_DATA) {
                thread("chunk");
                long s = System.nanoTime();
                int[] id = idsFor(e.getUser());
                if (autumn && id[0] >= 0 && id[1] >= 0) {
                    WrapperPlayServerChunkData w = new WrapperPlayServerChunkData(e);
                    long cells = 0;
                    for (BaseChunk c : w.getColumn().getChunks()) {
                        if (c instanceof Chunk_v1_18 c18) {
                            DataPalette p = c18.getBiomeData();
                            for (int x = 0; x < 4; x++) for (int y = 0; y < 4; y++) for (int z = 0; z < 4; z++) {
                                if (p.get(x, y, z) == id[0]) {
                                    p.set(x, y, z, id[1]);
                                    cells++;
                                }
                            }
                        }
                    }
                    remappedCells.addAndGet(cells);
                    e.markForReEncode(true);
                }
                long d = System.nanoTime() - s;
                chunkPackets.incrementAndGet();
                chunkNanos.addAndGet(d);
                chunkMaxNanos.accumulateAndGet(d, Math::max);
            } else if (e.getPacketType() == PacketType.Play.Server.SET_SLOT) {
                thread("set_slot");
                long s = System.nanoTime();
                WrapperPlayServerSetSlot w = new WrapperPlayServerSetSlot(e);
                if (decorate(w.getItem())) {
                    e.markForReEncode(true);
                }
                itemNanos.addAndGet(System.nanoTime() - s);
                itemPackets.incrementAndGet();
            } else if (e.getPacketType() == PacketType.Play.Server.WINDOW_ITEMS) {
                thread("window_items");
                long s = System.nanoTime();
                WrapperPlayServerWindowItems w = new WrapperPlayServerWindowItems(e);
                boolean any = false;
                for (ItemStack it : w.getItems()) {
                    any |= decorate(it);
                }
                if (any) {
                    e.markForReEncode(true);
                }
                itemNanos.addAndGet(System.nanoTime() - s);
                itemPackets.incrementAndGet();
            }
        }

        @Override
        public void onPacketReceive(PacketReceiveEvent e) {
            if (e.getPacketType() == PacketType.Play.Client.CREATIVE_INVENTORY_ACTION) {
                thread("creative");
                WrapperPlayClientCreativeInventoryAction w = new WrapperPlayClientCreativeInventoryAction(e);
                if (strip(w.getItemStack())) {
                    stripped.incrementAndGet();
                    e.markForReEncode(true);
                }
            }
        }
    }

    /** Test rule: iron ingots get a heat line + cmd float. Mutates the packet's copy only. */
    boolean decorate(ItemStack it) {
        if (it == null || it.isEmpty() || it.getType() != ItemTypes.IRON_INGOT) {
            return false;
        }
        NBTCompound data = it.getComponentOr(ComponentTypes.CUSTOM_DATA, new NBTCompound());
        if (data.getTagOrNull(MARK) != null) {
            return false;
        }
        ItemLore lore = it.getComponentOr(ComponentTypes.LORE, new ItemLore(new ArrayList<>()));
        List<Component> lines = new ArrayList<>(lore.getLines());
        lines.add(Component.text("Heat: 742°C (bright red)", NamedTextColor.RED));
        it.setComponent(ComponentTypes.LORE, new ItemLore(lines));
        it.setComponent(ComponentTypes.CUSTOM_MODEL_DATA_LISTS,
                new ItemCustomModelData(List.of(0.742f), List.of(), List.of(), List.of()));
        data.setTag(MARK, new NBTInt(1));
        it.setComponent(ComponentTypes.CUSTOM_DATA, data);
        return true;
    }

    boolean strip(ItemStack it) {
        if (it == null || it.isEmpty()) {
            return false;
        }
        NBTCompound data = it.getComponentOr(ComponentTypes.CUSTOM_DATA, null);
        if (data == null || data.getTagOrNull(MARK) == null) {
            return false;
        }
        int n = ((NBTInt) data.getTagOrNull(MARK)).getAsInt();
        ItemLore lore = it.getComponentOr(ComponentTypes.LORE, new ItemLore(new ArrayList<>()));
        List<Component> lines = new ArrayList<>(lore.getLines());
        for (int i = 0; i < n && !lines.isEmpty(); i++) {
            lines.removeLast();
        }
        if (lines.isEmpty()) {
            it.unsetComponent(ComponentTypes.LORE);
        } else {
            it.setComponent(ComponentTypes.LORE, new ItemLore(lines));
        }
        it.unsetComponent(ComponentTypes.CUSTOM_MODEL_DATA_LISTS);
        data.removeTag(MARK);
        if (data.isEmpty()) {
            it.unsetComponent(ComponentTypes.CUSTOM_DATA);
        } else {
            it.setComponent(ComponentTypes.CUSTOM_DATA, data);
        }
        return true;
    }

    final class Cmd implements BasicCommand {
        @Override
        public void execute(CommandSourceStack src, String[] a) {
            var out = src.getSender();
            switch (a.length == 0 ? "" : a[0]) {
                case "season" -> {
                    autumn = a.length > 1 && a[1].equals("autumn");
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        p.getScheduler().run(Net01Plugin.this, t -> {
                            int r = 6, cx = p.getLocation().getBlockX() >> 4, cz = p.getLocation().getBlockZ() >> 4;
                            for (int x = cx - r; x <= cx + r; x++) {
                                for (int z = cz - r; z <= cz + r; z++) {
                                    final int fx = x, fz = z;
                                    Bukkit.getRegionScheduler().run(Net01Plugin.this, p.getWorld(), fx, fz,
                                            tt -> p.getWorld().refreshChunk(fx, fz));
                                }
                            }
                        }, null);
                    }
                    out.sendMessage("season autumn=" + autumn);
                }
                case "give" -> {
                    Player p = Bukkit.getPlayerExact(a[1]);
                    p.getScheduler().run(Net01Plugin.this, t -> {
                        p.getInventory().setItem(0, new org.bukkit.inventory.ItemStack(Material.IRON_INGOT, 3));
                        p.getInventory().setHeldItemSlot(0);
                    }, null);
                    out.sendMessage("gave iron ingots");
                }
                case "creativetest" -> {
                    Player p = Bukkit.getPlayerExact(a[1]);
                    // what a creative client sends back: the decorated item it was shown
                    ItemStack shown = ItemStack.builder().type(ItemTypes.IRON_INGOT).amount(5).build();
                    decorate(shown);
                    PacketEvents.getAPI().getPlayerManager().receivePacket(p,
                            new WrapperPlayClientCreativeInventoryAction(36 + 1, shown)); // hotbar slot 1
                    p.getScheduler().runDelayed(Net01Plugin.this, t -> {
                        var stack = p.getInventory().getItem(1);
                        String r = "creativetest: server stack=" + (stack == null ? "null" : stack.getType() + " x"
                                + stack.getAmount() + " lore=" + stack.lore() + " cmd=" + stack.getData(
                                io.papermc.paper.datacomponent.DataComponentTypes.CUSTOM_MODEL_DATA)
                                + " pdcKeys=" + stack.getPersistentDataContainer().getKeys())
                                + " stripped=" + stripped;
                        notes.add(r);
                        getLogger().info("[net01] " + r);
                    }, null, 10);
                    out.sendMessage("injected creative action");
                }
                case "pipeline" -> {
                    Player p = Bukkit.getPlayerExact(a[1]);
                    Object ch = PacketEvents.getAPI().getPlayerManager().getChannel(p);
                    try {
                        Object pipe = ch.getClass().getMethod("pipeline").invoke(ch);
                        Object names = pipe.getClass().getMethod("names").invoke(pipe);
                        notes.add("pipeline: " + names);
                        out.sendMessage("pipeline: " + names);
                    } catch (ReflectiveOperationException ex) {
                        out.sendMessage("pipeline failed " + ex);
                    }
                }
                case "report" -> {
                    List<String> r = new ArrayList<>(notes);
                    long n = Math.max(1, chunkPackets.get()), m = Math.max(1, itemPackets.get());
                    r.add("chunk packets=" + chunkPackets + " avg=" + chunkNanos.get() / n / 1000 + "us max="
                            + chunkMaxNanos.get() / 1000 + "us remappedCells=" + remappedCells);
                    r.add("item packets=" + itemPackets + " avg=" + itemNanos.get() / m / 1000 + "us; creative strips=" + stripped);
                    r.add("listener threads: " + threads);
                    r.add("mc=" + Bukkit.getMinecraftVersion() + " " + Bukkit.getName());
                    try {
                        Files.write(Path.of("net01-report.txt"), r);
                    } catch (IOException ignored) {
                    }
                    r.forEach(out::sendMessage);
                }
                default -> out.sendMessage("/net01 season|give|creativetest|pipeline|report");
            }
        }
    }
}
