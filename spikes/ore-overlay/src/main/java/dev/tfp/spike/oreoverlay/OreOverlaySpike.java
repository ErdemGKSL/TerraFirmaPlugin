/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.spike.oreoverlay;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListener;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.netty.buffer.ByteBufHelper;
import com.github.retrooper.packetevents.protocol.component.ComponentTypes;
import com.github.retrooper.packetevents.protocol.component.builtin.item.ItemModel;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.protocol.item.type.ItemTypes;
import com.github.retrooper.packetevents.resources.ResourceLocation;
import com.github.retrooper.packetevents.util.Quaternion4f;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.util.Vector3f;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * PAR-WORLD-17 (spike S1, not shipped): the client cost of the ore overlay. Sends a synthetic field of packet-only
 * {@code item_display} quads (one per "exposed face", the model {@code tfpspike:overlay} from the spike pack, see
 * {@code build-pack.sh}) around a player, gated by chunk like the planned runtime (PAR-INFRA-16): the chunks within
 * {@value #RADIUS} of the player's chunk carry {@code n / 81} quads each, spawned when the chunk enters the radius and
 * removed when it leaves. Quads are placed in the air cell of a random face at the player's height band, pulled back 0.49
 * along the normal, with {@code view_range} for a 48 m cutoff.
 *
 * <p>Console / RCON commands:
 * <ul>
 *   <li>{@code oreoverlay field <player> <n>}: start (or replace) the field and reset the counters;</li>
 *   <li>{@code oreoverlay clear <player>}: remove every quad;</li>
 *   <li>{@code oreoverlay stats}: packets and bytes (uncompressed) sent to each player since the field started, quads
 *       spawned/removed, the largest per-check burst, and the server's average tick time.</li>
 * </ul>
 */
public final class OreOverlaySpike extends JavaPlugin {
    static final int RADIUS = 4;
    static final int CHUNKS = (2 * RADIUS + 1) * (2 * RADIUS + 1);
    /** view_range is a multiplier of 64 blocks (at 100 % entity distance): 0.75 = 48 m. */
    static final float VIEW_RANGE = 0.75f;
    static final float PULL_BACK = 0.49f;
    private static final float S = (float) Math.sqrt(0.5);
    /** Normals: down, up, north, south, west, east. The model's quad faces +z (south). */
    private static final int[][] NORMALS = {{0, -1, 0}, {0, 1, 0}, {0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0}};
    private static final Quaternion4f[] ROTATIONS = {
        new Quaternion4f(S, 0, 0, S), new Quaternion4f(-S, 0, 0, S), new Quaternion4f(0, 1, 0, 0),
        new Quaternion4f(0, 0, 0, 1), new Quaternion4f(0, -S, 0, S), new Quaternion4f(0, S, 0, S)};

    private final Map<UUID, Field> fields = new ConcurrentHashMap<>();
    private final AtomicInteger nextId = new AtomicInteger(1_900_000_000);

    @Override
    public void onEnable() {
        PacketEvents.getAPI().getEventManager().registerListener(new Counter(), PacketListenerPriority.MONITOR);
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS,
                e -> e.registrar().register("oreoverlay", "PAR-WORLD-17 overlay field spike", new Cmd()));
    }

    /** One player's field: per chunk the entity ids sent; touched only by the player's scheduler task. */
    final class Field {
        final Player player;
        final int perChunk;
        final int baseY;
        final Map<Long, int[]> sent = new HashMap<>();
        final AtomicLong packets = new AtomicLong();
        final AtomicLong bytes = new AtomicLong();
        final long started = System.nanoTime();
        long spawned;
        long removed;
        int maxBurst;
        long lastChunk = Long.MIN_VALUE;
        ScheduledTask task;

        Field(Player player, int perChunk, int baseY) {
            this.player = player;
            this.perChunk = perChunk;
            this.baseY = baseY;
        }

        /** On the player's thread: sends the chunks that entered the radius, removes those that left. */
        void update() {
            final Location at = player.getLocation();
            final int pcx = at.getBlockX() >> 4;
            final int pcz = at.getBlockZ() >> 4;
            final long key = key(pcx, pcz);
            if (key == lastChunk) {
                return;
            }
            lastChunk = key;
            final Set<Long> want = new HashSet<>();
            for (int dx = -RADIUS; dx <= RADIUS; dx++) {
                for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                    want.add(key(pcx + dx, pcz + dz));
                }
            }
            int burst = 0;
            final List<Long> gone = new ArrayList<>();
            for (Long k : sent.keySet()) {
                if (!want.contains(k)) {
                    gone.add(k);
                }
            }
            for (Long k : gone) {
                final int[] ids = sent.remove(k);
                PacketEvents.getAPI().getPlayerManager().sendPacket(player, new WrapperPlayServerDestroyEntities(ids));
                removed += ids.length;
                burst += 1;
            }
            for (Long k : want) {
                if (!sent.containsKey(k)) {
                    final int[] ids = spawnChunk((int) (k >> 32), (int) (long) k);
                    sent.put(k, ids);
                    spawned += ids.length;
                    burst += 2 * ids.length;
                }
            }
            maxBurst = Math.max(maxBurst, burst);
        }

        private int[] spawnChunk(int cx, int cz) {
            final SplittableRandom r = new SplittableRandom(key(cx, cz) * 0x9E3779B97F4A7C15L);
            final int[] ids = new int[perChunk];
            for (int i = 0; i < perChunk; i++) {
                final int face = r.nextInt(6);
                final double x = (cx << 4) + r.nextInt(16) + 0.5;
                final double y = baseY + r.nextInt(-8, 24) + 0.5;
                final double z = (cz << 4) + r.nextInt(16) + 0.5;
                final int id = nextId.getAndIncrement();
                ids[i] = id;
                send(id, new Vector3d(x, y, z), face);
            }
            return ids;
        }

        private void send(int id, Vector3d pos, int face) {
            final int[] n = NORMALS[face];
            // The air cell is pos; the ore face is 0.5 back along -normal; stop 0.01 in front of it.
            final Vector3f translation = new Vector3f(-n[0] * PULL_BACK, -n[1] * PULL_BACK, -n[2] * PULL_BACK);
            final ItemStack item = ItemStack.builder().type(ItemTypes.PAPER)
                    .component(ComponentTypes.ITEM_MODEL, new ItemModel(new ResourceLocation("tfpspike", "overlay")))
                    .build();
            final List<EntityData<?>> data = List.of(
                    new EntityData<>(11, EntityDataTypes.VECTOR3F, translation),
                    new EntityData<>(13, EntityDataTypes.QUATERNION, ROTATIONS[face]),
                    new EntityData<>(17, EntityDataTypes.FLOAT, VIEW_RANGE),
                    new EntityData<>(23, EntityDataTypes.ITEMSTACK, item),
                    new EntityData<>(24, EntityDataTypes.BYTE, (byte) 0));
            PacketEvents.getAPI().getPlayerManager().sendPacket(player, new WrapperPlayServerSpawnEntity(id,
                    Optional.of(UUID.randomUUID()), EntityTypes.ITEM_DISPLAY, pos, 0f, 0f, 0f, 0, Optional.empty()));
            PacketEvents.getAPI().getPlayerManager().sendPacket(player, new WrapperPlayServerEntityMetadata(id, data));
        }

        /** On the player's thread. */
        void clear() {
            for (int[] ids : sent.values()) {
                PacketEvents.getAPI().getPlayerManager().sendPacket(player, new WrapperPlayServerDestroyEntities(ids));
                removed += ids.length;
            }
            sent.clear();
        }

        String stats() {
            final double seconds = (System.nanoTime() - started) / 1e9;
            int live = 0;
            for (int[] ids : sent.values()) {
                live += ids.length;
            }
            return String.format(java.util.Locale.ROOT,
                    "%s: field %d/chunk (%d target), live %d, spawned %d, removed %d, max burst %d packets; since start %.1f s:"
                            + " %d packets, %.1f KB uncompressed", player.getName(), perChunk, perChunk * CHUNKS, live, spawned,
                    removed, maxBurst, seconds, packets.get(), bytes.get() / 1024.0);
        }
    }

    static long key(int cx, int cz) {
        return (long) cx << 32 | (cz & 0xFFFFFFFFL);
    }

    /** Counts every packet and its uncompressed size sent to a player with a field (Netty threads). */
    final class Counter implements PacketListener {
        @Override
        public void onPacketSend(PacketSendEvent event) {
            final UUID uuid = event.getUser().getUUID();
            final Field f = uuid == null ? null : fields.get(uuid);
            if (f != null) {
                f.packets.incrementAndGet();
                f.bytes.addAndGet(ByteBufHelper.readableBytes(event.getByteBuf()));
            }
        }
    }

    final class Cmd implements BasicCommand {
        @Override
        public void execute(CommandSourceStack source, String[] args) {
            final var out = source.getSender();
            if (args.length == 0) {
                out.sendMessage("oreoverlay field <player> <n> | clear <player> | stats");
                return;
            }
            switch (args[0]) {
                case "field" -> {
                    final Player p = Bukkit.getPlayerExact(args[1]);
                    final int n = Integer.parseInt(args[2]);
                    if (p == null) {
                        out.sendMessage("no player " + args[1]);
                        return;
                    }
                    p.getScheduler().run(OreOverlaySpike.this, t -> {
                        final Field old = fields.remove(p.getUniqueId());
                        if (old != null) {
                            old.task.cancel();
                            old.clear();
                        }
                        final Field f = new Field(p, Math.max(1, Math.round(n / (float) CHUNKS)), p.getLocation().getBlockY());
                        fields.put(p.getUniqueId(), f);
                        f.update();
                        f.task = p.getScheduler().runAtFixedRate(OreOverlaySpike.this, u -> f.update(), null, 2, 2);
                        getLogger().info("field started: " + f.stats());
                    }, null);
                    out.sendMessage("field " + n + " for " + p.getName() + " scheduled");
                }
                case "clear" -> {
                    final Player p = Bukkit.getPlayerExact(args[1]);
                    if (p == null) {
                        out.sendMessage("no player " + args[1]);
                        return;
                    }
                    p.getScheduler().run(OreOverlaySpike.this, t -> {
                        final Field f = fields.remove(p.getUniqueId());
                        if (f != null) {
                            f.task.cancel();
                            f.clear();
                            getLogger().info("field cleared: " + f.stats());
                        }
                    }, null);
                    out.sendMessage("clear scheduled");
                }
                case "stats" -> {
                    for (Field f : fields.values()) {
                        out.sendMessage(f.stats());
                        getLogger().info("stats: " + f.stats());
                    }
                    try {
                        final String mspt = String.format(java.util.Locale.ROOT, "%.2f", Bukkit.getAverageTickTime());
                        out.sendMessage("average tick time " + mspt + " ms");
                        getLogger().info("stats: average tick time " + mspt + " ms");
                    } catch (UnsupportedOperationException e) {
                        out.sendMessage("average tick time not available on this server");
                    }
                }
                default -> out.sendMessage("unknown subcommand " + args[0]);
            }
        }
    }
}
