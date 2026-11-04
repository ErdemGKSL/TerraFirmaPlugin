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
import org.bukkit.ChunkSnapshot;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * PAR-WORLD-17 (spike S1, not shipped): the client cost of the ore overlay. Sends a synthetic field of packet-only
 * {@code item_display} quads (one per "exposed face", the model {@code tfpspike:overlay} from the spike pack, see
 * {@code build-pack.sh}) around a player, gated by chunk like the planned runtime (PAR-INFRA-16): the chunks within
 * {@value #RADIUS} of the player's chunk carry {@code n / 81} quads each, spawned when the chunk enters the radius and
 * removed when it leaves. Each quad sits on a real exposed face of an occluding block of its chunk (the chunk is
 * snapshotted on its region thread, the faces are picked on the async pool): the entity is in the centre of the open cell
 * next to the face and translated 0.49 back along the normal, so the quad lies flush on the face 0.01 in front of it;
 * {@code view_range} gives the 48 m cutoff. Any occluding block counts (stone, soil, ores), so the field stands in for
 * "n exposed ore faces" at the real positions faces have.
 *
 * <p>Console / RCON commands:
 * <ul>
 *   <li>{@code oreoverlay field <player> <n>}: start (or replace) the field and reset the counters;</li>
 *   <li>{@code oreoverlay clear <player>}: remove every quad;</li>
 *   <li>{@code oreoverlay stats}: packets and bytes (uncompressed) sent to each player since the field started, quads
 *       spawned/removed, exposed faces available per chunk, the largest burst per chunk crossing, and the server's
 *       average tick time.</li>
 * </ul>
 */
public final class OreOverlaySpike extends JavaPlugin {
    static final int RADIUS = 4;
    static final int CHUNKS = (2 * RADIUS + 1) * (2 * RADIUS + 1);
    /** view_range is a multiplier of 64 blocks (at 100 % entity distance): 0.75 = 48 m. */
    static final float VIEW_RANGE = 0.75f;
    static final float PULL_BACK = 0.49f;
    private static final float S = (float) Math.sqrt(0.5);
    /**
     * Normals: down, up, north, south, west, east. The quad faces +z (south) on the entity: it faces -z in the model and
     * the item_display renderer turns item models 180 degrees about y.
     */
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

    /**
     * One player's field. {@code sent}, {@code pending} and {@code want} are guarded by {@code this}: the player's task
     * adds and removes chunks, the async face picker fills them in.
     */
    final class Field {
        final Player player;
        final int perChunk;
        final Map<Long, int[]> sent = new HashMap<>();
        final Set<Long> pending = new HashSet<>();
        Set<Long> want = Set.of();
        final AtomicLong packets = new AtomicLong();
        final AtomicLong bytes = new AtomicLong();
        final AtomicLong spawned = new AtomicLong();
        final AtomicLong removed = new AtomicLong();
        final AtomicLong facesAvailable = new AtomicLong();
        final AtomicLong chunksSampled = new AtomicLong();
        final AtomicLong crossingBurst = new AtomicLong();
        final long started = System.nanoTime();
        volatile long maxBurst;
        volatile boolean closed;
        long lastChunk = Long.MIN_VALUE;
        ScheduledTask task;

        Field(Player player, int perChunk) {
            this.player = player;
            this.perChunk = perChunk;
        }

        /**
         * On the player's thread: on a chunk crossing, removes the chunks that left the radius; on every call, asks for
         * the chunks inside the radius that have no quads yet (a chunk that is not loaded is retried on the next call).
         */
        void update() {
            final Location at = player.getLocation();
            final int pcx = at.getBlockX() >> 4;
            final int pcz = at.getBlockZ() >> 4;
            final long key = key(pcx, pcz);
            final List<Long> ask = new ArrayList<>();
            synchronized (this) {
                if (key != lastChunk) {
                    lastChunk = key;
                    maxBurst = Math.max(maxBurst, crossingBurst.getAndSet(0));
                    final Set<Long> w = new HashSet<>();
                    for (int dx = -RADIUS; dx <= RADIUS; dx++) {
                        for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                            w.add(key(pcx + dx, pcz + dz));
                        }
                    }
                    want = w;
                    final List<Long> gone = new ArrayList<>();
                    for (Long k : sent.keySet()) {
                        if (!w.contains(k)) {
                            gone.add(k);
                        }
                    }
                    for (Long k : gone) {
                        final int[] ids = sent.remove(k);
                        PacketEvents.getAPI().getPlayerManager().sendPacket(player, new WrapperPlayServerDestroyEntities(ids));
                        removed.addAndGet(ids.length);
                        crossingBurst.incrementAndGet();
                    }
                }
                for (Long k : want) {
                    if (!sent.containsKey(k) && pending.add(k)) {
                        ask.add(k);
                    }
                }
            }
            for (Long k : ask) {
                request(at.getWorld(), (int) (k >> 32), (int) (long) k);
            }
        }

        /** Snapshot on the chunk's region thread, pick the faces on the async pool, send from there. */
        private void request(World world, int cx, int cz) {
            Bukkit.getRegionScheduler().run(OreOverlaySpike.this, world, cx, cz, t -> {
                if (closed || !world.isChunkLoaded(cx, cz)) {
                    synchronized (this) {
                        pending.remove(key(cx, cz));
                    }
                    return;
                }
                final ChunkSnapshot snap = world.getChunkAt(cx, cz).getChunkSnapshot(true, false, false);
                final int minY = world.getMinHeight();
                final int maxY = world.getMaxHeight();
                Bukkit.getAsyncScheduler().runNow(OreOverlaySpike.this, a -> place(snap, cx, cz, minY, maxY));
            });
        }

        /**
         * Async: every exposed face of an occluding block in the chunk (its neighbour inside the chunk is not occluding:
         * air, fluid, plants, glass, leaves; faces across the chunk edge are skipped because the snapshot does not hold
         * the neighbour chunk), then {@code perChunk} of them chosen at random (all of them when the chunk has fewer).
         */
        private void place(ChunkSnapshot snap, int cx, int cz, int minY, int maxY) {
            final List<int[]> faces = new ArrayList<>();
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    final int top = Math.min(maxY - 1, snap.getHighestBlockYAt(x, z) + 1);
                    for (int y = minY; y <= top; y++) {
                        if (!occluding(snap, x, y, z, minY, maxY)) {
                            continue;
                        }
                        for (int f = 0; f < 6; f++) {
                            final int[] n = NORMALS[f];
                            final int nx = x + n[0];
                            final int ny = y + n[1];
                            final int nz = z + n[2];
                            if (nx < 0 || nx > 15 || nz < 0 || nz > 15 || ny < minY || ny >= maxY) {
                                continue;
                            }
                            if (!occluding(snap, nx, ny, nz, minY, maxY)) {
                                faces.add(new int[] {x, y, z, f});
                            }
                        }
                    }
                }
            }
            facesAvailable.addAndGet(faces.size());
            chunksSampled.incrementAndGet();
            final SplittableRandom r = new SplittableRandom(key(cx, cz) * 0x9E3779B97F4A7C15L);
            final int count = Math.min(perChunk, faces.size());
            for (int i = 0; i < count; i++) {
                final int j = i + r.nextInt(faces.size() - i);
                final int[] tmp = faces.get(i);
                faces.set(i, faces.get(j));
                faces.set(j, tmp);
            }
            final long k = key(cx, cz);
            synchronized (this) {
                pending.remove(k);
                if (closed || !want.contains(k) || sent.containsKey(k)) {
                    return;
                }
                final int[] ids = new int[count];
                for (int i = 0; i < count; i++) {
                    final int[] c = faces.get(i);
                    final int[] n = NORMALS[c[3]];
                    final double x = (cx << 4) + c[0] + n[0] + 0.5;
                    final double y = c[1] + n[1] + 0.5;
                    final double z = (cz << 4) + c[2] + n[2] + 0.5;
                    ids[i] = nextId.getAndIncrement();
                    send(ids[i], new Vector3d(x, y, z), c[3]);
                }
                sent.put(k, ids);
                spawned.addAndGet(count);
                crossingBurst.addAndGet(2L * count);
            }
        }

        private void send(int id, Vector3d pos, int face) {
            final int[] n = NORMALS[face];
            // pos is the centre of the open cell; the block face is 0.5 back along -normal; stop 0.01 in front of it.
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
            synchronized (this) {
                closed = true;
                for (int[] ids : sent.values()) {
                    PacketEvents.getAPI().getPlayerManager().sendPacket(player, new WrapperPlayServerDestroyEntities(ids));
                    removed.addAndGet(ids.length);
                }
                sent.clear();
            }
        }

        String stats() {
            final double seconds = (System.nanoTime() - started) / 1e9;
            int live = 0;
            synchronized (this) {
                for (int[] ids : sent.values()) {
                    live += ids.length;
                }
            }
            final long sampled = chunksSampled.get();
            return String.format(java.util.Locale.ROOT,
                    "%s: field %d/chunk (%d target), live %d, spawned %d, removed %d, exposed faces per chunk %.0f,"
                            + " max burst per crossing %d packets; since start %.1f s: %d packets, %.1f KB uncompressed",
                    player.getName(), perChunk, perChunk * CHUNKS, live, spawned.get(), removed.get(),
                    sampled == 0 ? 0.0 : facesAvailable.get() / (double) sampled,
                    Math.max(maxBurst, crossingBurst.get()), seconds, packets.get(), bytes.get() / 1024.0);
        }
    }

    /** A full occluding block (stone, soil, ore...); air, fluids, plants, glass and leaves are not. */
    static boolean occluding(ChunkSnapshot snap, int x, int y, int z, int minY, int maxY) {
        if (y < minY) {
            return true;
        }
        if (y >= maxY) {
            return false;
        }
        return snap.getBlockType(x, y, z).isOccluding();
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
                        final Field f = new Field(p, Math.max(1, Math.round(n / (float) CHUNKS)));
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
