/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.oreoverlay;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.tfp.core.config.TfpConfig;
import dev.tfp.core.oreoverlay.OreOverlayTable;
import dev.tfp.core.oreoverlay.OverlayGate;
import dev.tfp.core.visuals.OverlayFace;
import dev.tfp.paper.boot.TfpCommands.Reply;
import dev.tfp.paper.install.Installer;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.integration.Integrations;
import dev.tfp.paper.module.DebugTree;
import dev.tfp.paper.module.FeatureModule;
import dev.tfp.paper.module.ModuleContext;
import dev.tfp.paper.packet.PacketHandler;
import dev.tfp.paper.packet.PacketLayers;
import dev.tfp.paper.scheduler.Scheduler;
import io.papermc.paper.command.brigadier.Commands;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.IntSupplier;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

/**
 * PAR-INFRA-16: the ore overlay runtime. Ores look like their host rock (PAR-INFRA-15); this module draws the ore texture
 * on the exposed faces as packet-only {@code item_display} overlays ({@link OreOverlayService}). Needs CraftEngine (the
 * chunk storage: the {@link OreBlockSource} service its adapter publishes through {@link Integrations}, PAR-INFRA-39) and
 * PacketEvents (the display packets, {@code dev.tfp.paper.packet.display}, the one class-name lookup left:
 * {@link #packetSide}); the module stays off with a log line when either is missing. Debug: {@code /tfp debug oreoverlay stats|reload|show <x> <y> <z>|place <x> <y> <z> <state>}.
 */
public final class OreOverlayModule implements FeatureModule {
    public static final String ID = "ore_overlay";
    static final String SENDER_CLASS = "dev.tfp.paper.packet.display.OverlayDisplaySender";
    static final String HANDLER_CLASS = "dev.tfp.paper.packet.display.OreOverlayPacketHandler";

    private volatile OreOverlayService service;
    private volatile Scheduler scheduler;
    private volatile Scheduler.TaskHandle ticker;
    private volatile CustomBlocks blocks;

    @Override
    public String id() {
        return ID;
    }

    /** The running service, or null while the module is off. */
    public OreOverlayService service() {
        return service;
    }

    @Override
    public void enable(ModuleContext context) throws Exception {
        scheduler = context.scheduler();
        blocks = context.integrations().customBlocks();
        OreOverlayData data = OreOverlayData.load(context.plugin().getClass().getClassLoader());
        if (data.ores() == 0) {
            context.logger().info("Ore overlays: no ore blocks in the content; off");
            return;
        }
        var plugins = context.plugin().getServer().getPluginManager();
        if (plugins.getPlugin(Installer.CRAFTENGINE) == null) {
            context.logger().info("Ore overlays: CraftEngine is not installed; off");
            return;
        }
        if (plugins.getPlugin(PacketLayers.PACKETEVENTS) == null) {
            context.logger().warning("Ore overlays: PacketEvents is not installed; ores look like plain rock");
            return;
        }
        Optional<OreBlockSource> found = oreSource(context.integrations(), context.logger());
        if (found.isEmpty()) {
            return;
        }
        OreBlockSource source = found.get();
        OverlaySender sender = sender(entityIds(context.logger()));
        TfpConfig.OreOverlay cfg = context.config().current().oreOverlay();
        OverlayGate gate = new OverlayGate(cfg.cutoffBlocks(), cfg.surfaceOutcropsFullRange());
        float full = OverlayGate.fullViewRange(context.plugin().getServer().getViewDistance());
        Scheduler sch = context.scheduler();
        OreOverlayService.Regions regions = new OreOverlayService.Regions() {
            @Override
            public void run(UUID world, int chunkX, int chunkZ, Runnable task) {
                World w = Bukkit.getWorld(world);
                if (w != null) {
                    sch.region(w, chunkX, chunkZ, task);
                }
            }

            @Override
            public void nextTick(UUID world, int chunkX, int chunkZ, Runnable task) {
                World w = Bukkit.getWorld(world);
                if (w != null) {
                    sch.regionDelayed(w, chunkX, chunkZ, 1L, task);
                }
            }
        };
        OreOverlayService s = new OreOverlayService(data, new BukkitCellReader(source, data), regions, sch.cpuExecutor(),
                sender, gate, cfg.maxPerPlayer(), full, id -> source.networkState(id, data::kind), context.logger());
        for (World w : Bukkit.getWorlds()) {
            s.world(w.getKey().toString(), w.getUID());
        }
        context.packets().register(handler(s, sender));
        context.listen(new OreOverlayListener(s, source, player -> {
            s.quit(player);
            forget(sender, player);
        }));
        context.services().provide(OreOverlayService.class, s);
        ticker = sch.globalRepeating(1L, 1L, s::tick);
        service = s;
        context.logger().info("Ore overlays: " + data.ores() + " ore blocks, " + data.overlays().size()
                + " overlays; cutoff " + cfg.cutoffBlocks() + " m (radius " + gate.radiusChunks() + " chunks), cap "
                + cfg.maxPerPlayer() + " per player");
    }

    /**
     * The ore block source the custom-block adapter publishes ({@code CraftEngineProvider}), or empty with a log line when
     * no adapter provides it (CraftEngine missing or its adapter disabled): the module then stays off.
     */
    public static Optional<OreBlockSource> oreSource(Integrations integrations, java.util.logging.Logger log) {
        Optional<OreBlockSource> source = integrations.service(OreBlockSource.class);
        if (source.isEmpty()) {
            log.info("Ore overlays: no ore block source (the CraftEngine adapter is not running); off");
        }
        return source;
    }

    /**
     * The PacketEvents side ({@code dev.tfp.paper.packet.display}), looked up by name on purpose: it is the one place the
     * module links PacketEvents classes, which exist only when PacketEvents is installed (checked just before), the same
     * rule as {@code PacketLayers} and {@code BlockSoundModule}. The names are constants of this class; a rename fails
     * {@code OreOverlayModule} start-up loudly, and the self-test {@code ore.overlay} covers it.
     */
    static OverlaySender sender(IntSupplier ids) throws ReflectiveOperationException {
        return (OverlaySender) packetSide(SENDER_CLASS).getConstructor(IntSupplier.class).newInstance(ids);
    }

    /** The packet handler of {@link #sender}'s type, see {@link #packetSide}. */
    static PacketHandler handler(OreOverlayService s, OverlaySender sender) throws ReflectiveOperationException {
        return (PacketHandler) packetSide(HANDLER_CLASS).getConstructor(OreOverlayService.class, sender.getClass())
                .newInstance(s, sender);
    }

    /** The documented class-name lookup of the PacketEvents-side classes ({@link #sender}, {@link #handler}). */
    private static Class<?> packetSide(String name) throws ClassNotFoundException {
        return Class.forName(name, true, OreOverlayModule.class.getClassLoader());
    }

    /** First id of our own range when the server has no shared counter: far above any real entity id. */
    static final int OWN_ID_BASE = 0x70000000;

    /**
     * Entity ids for the overlays: the server's counter ({@code UnsafeValues#nextEntityId}, Paper 1.21.x) when it exists,
     * else our own counter from {@value #OWN_ID_BASE} (26.x removed the method; a real entity would need 1.8 billion
     * spawns to reach the range). Looked up reflectively so the jar links on both.
     */
    @SuppressWarnings("deprecation")
    static IntSupplier entityIds(java.util.logging.Logger log) {
        try {
            Object unsafe = Bukkit.getUnsafe();
            java.lang.reflect.Method next = org.bukkit.UnsafeValues.class.getMethod("nextEntityId");
            if ((int) next.invoke(unsafe) > 0) {
                return () -> {
                    try {
                        return (int) next.invoke(unsafe);
                    } catch (ReflectiveOperationException e) {
                        throw new IllegalStateException(e);
                    }
                };
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            log.info("Ore overlays: the server has no shared entity id counter (" + e.getClass().getSimpleName()
                    + "); overlay ids come from 0x" + Integer.toHexString(OWN_ID_BASE) + " up");
        }
        return ownIds();
    }

    /** Our own id range ({@link #entityIds} fallback). */
    static IntSupplier ownIds() {
        java.util.concurrent.atomic.AtomicInteger own = new java.util.concurrent.atomic.AtomicInteger(OWN_ID_BASE);
        return () -> {
            int id = own.incrementAndGet();
            if (id < OWN_ID_BASE) {
                own.set(OWN_ID_BASE);
                id = own.incrementAndGet();
            }
            return id;
        };
    }

    private static void forget(OverlaySender sender, UUID player) {
        try {
            sender.getClass().getMethod("forget", UUID.class).invoke(sender, player);
        } catch (ReflectiveOperationException e) {
            // not a PacketEvents sender: nothing to forget
        }
    }

    @Override
    public void disable() {
        Scheduler.TaskHandle t = ticker;
        if (t != null) {
            t.cancel();
        }
        service = null;
    }

    @Override
    public void debugCommands(DebugTree tree) {
        tree.add("oreoverlay", node -> node
                .then(Commands.literal("stats").executes(tree.run(ctx -> CompletableFuture.completedFuture(
                        stats(ctx.getSource().getSender() instanceof Player p ? p.getUniqueId() : null)))))
                .then(Commands.literal("reload").executes(tree.player((player, ctx) -> reload(player))))
                .then(Commands.literal("place")
                        .then(Commands.argument("x", IntegerArgumentType.integer())
                                .then(Commands.argument("y", IntegerArgumentType.integer())
                                        .then(Commands.argument("z", IntegerArgumentType.integer())
                                                .then(Commands.argument("state", StringArgumentType.greedyString())
                                                        .executes(tree.run(ctx -> place(
                                                                ctx.getSource().getSender() instanceof Player p
                                                                        ? p.getWorld() : Bukkit.getWorlds().getFirst(),
                                                                IntegerArgumentType.getInteger(ctx, "x"),
                                                                IntegerArgumentType.getInteger(ctx, "y"),
                                                                IntegerArgumentType.getInteger(ctx, "z"),
                                                                StringArgumentType.getString(ctx, "state")))))))))
                .then(Commands.literal("show")
                        .then(Commands.argument("x", IntegerArgumentType.integer())
                                .then(Commands.argument("y", IntegerArgumentType.integer())
                                        .then(Commands.argument("z", IntegerArgumentType.integer())
                                                .executes(tree.run(ctx -> CompletableFuture.completedFuture(
                                                        show((ctx.getSource().getSender() instanceof Player p
                                                                ? p.getWorld() : Bukkit.getWorlds().getFirst()).getUID(),
                                                                IntegerArgumentType.getInteger(ctx, "x"),
                                                                IntegerArgumentType.getInteger(ctx, "y"),
                                                                IntegerArgumentType.getInteger(ctx, "z")))))))))
        );
    }

    Reply stats(UUID viewer) {
        OreOverlayService s = service;
        if (s == null) {
            return Reply.error("debug.oreoverlay.off");
        }
        OreOverlayService.Stats st = s.stats(viewer);
        List<String> lines = new ArrayList<>();
        lines.add("tables " + st.tables() + " (chunks sent to viewers), ores " + st.tableOres() + ", exposed faces "
                + st.tableFaces() + " (in those tables)");
        lines.add("builds " + st.builds() + ", avg " + String.format("%.3f", st.builds() == 0 ? 0.0
                : st.buildNanos() / 1e6 / st.builds()) + " ms, max " + String.format("%.3f", st.maxBuildNanos() / 1e6)
                + " ms (CPU time per chunk); region-thread copy avg " + String.format("%.3f", st.builds() == 0 ? 0.0
                : st.snapshotNanos() / 1e6 / st.builds()) + " ms; patches " + st.patches() + ", fast-path changes " + st.fastPathChanges());
        lines.add("viewers " + st.viewers() + (viewer == null ? "; all viewers: entities " : "; you: entities ") + st.viewerSent() + " / cap " + st.viewerCap()
                + "; viewer chunks loaded " + st.viewerLoaded() + "; overlay radius " + st.radiusChunks() + " chunks; deferred "
                + st.viewerDeferred());
        return Reply.ok("debug.oreoverlay.stats").withDetails(lines);
    }

    CompletableFuture<Reply> reload(Player player) {
        OreOverlayService s = service;
        Scheduler sch = scheduler;
        if (s == null || sch == null) {
            return CompletableFuture.completedFuture(Reply.error("debug.oreoverlay.off"));
        }
        CompletableFuture<Reply> out = new CompletableFuture<>();
        boolean scheduled = sch.entity(player, () -> {
            UUID world = player.getWorld().getUID();
            int cx = player.getLocation().getBlockX() >> 4;
            int cz = player.getLocation().getBlockZ() >> 4;
            s.rebuild(world, cx, cz).whenComplete((t, e) -> out.complete(e != null ? Reply.error("debug.oreoverlay.off")
                    : Reply.ok("debug.oreoverlay.reload", cx, cz, t.size(), t.faceCount())));
        }, () -> out.complete(Reply.error("command.player_only")));
        if (!scheduled) {
            out.complete(Reply.error("command.player_only"));
        }
        return out;
    }

    /** Places a custom block at a position (in-game steps run from the console, where {@code debug place} cannot aim). */
    CompletableFuture<Reply> place(World world, int x, int y, int z, String stateText) {
        CustomBlocks b = blocks;
        Scheduler sch = scheduler;
        BlockStateRef state;
        try {
            state = BlockStateRef.parse(stateText.trim());
        } catch (IllegalArgumentException e) {
            return CompletableFuture.completedFuture(Reply.error("debug.place.bad_state", stateText, e.getMessage()));
        }
        if (b == null || sch == null || !b.exists(state.id())) {
            return CompletableFuture.completedFuture(Reply.error("debug.place.unknown", state.id()));
        }
        String where = world.getName() + " " + x + " " + y + " " + z;
        CompletableFuture<Reply> out = new CompletableFuture<>();
        Runnable task = () -> {
            try {
                Block block = world.getBlockAt(x, y, z);
                boolean placed = b.place(block, state, false);
                // Read the cell back, so a console-driven step sees what is there now (a later fill can still overwrite
                // it) and that stats/show only count ores of chunks already sent to a viewer within the overlay radius.
                List<String> now = List.of("now: " + b.stateAt(block).map(BlockStateRef::toString)
                                .orElse("vanilla " + block.getType().getKey()),
                        "overlay tables exist only for chunks sent to a viewer within the overlay radius: check with "
                                + "`tfp debug oreoverlay show " + x + " " + y + " " + z + "` while a player is near");
                out.complete((placed ? Reply.ok("debug.place.done", stateText.trim(), where)
                        : Reply.error("debug.place.refused", stateText.trim(), where)).withDetails(now));
            } catch (RuntimeException e) {
                out.completeExceptionally(e);
            }
        };
        // The console on Paper already runs on the thread that owns every region: a deferred task could only run after the
        // command's reply wait gives up, stalling that thread (the Watchdog report of the in-game run). Place inline then.
        if (Bukkit.isOwnedByCurrentRegion(world, x >> 4, z >> 4)) {
            task.run();
        } else {
            sch.region(world, x >> 4, z >> 4, task);
        }
        return out;
    }

    Reply show(UUID world, int x, int y, int z) {
        OreOverlayService s = service;
        if (s == null) {
            return Reply.error("debug.oreoverlay.off");
        }
        OreOverlayTable t = s.table(world, x >> 4, z >> 4);
        int i = t == null ? -1 : t.find(x, y, z);
        String where = x + " " + y + " " + z;
        if (i < 0) {
            return Reply.ok("debug.oreoverlay.show", where, t == null
                    ? "no table (chunk not sent to a viewer within the overlay radius)" : "no ore");
        }
        List<String> faces = new ArrayList<>();
        for (OverlayFace f : OverlayFace.values()) {
            if (t.exposed(i, f)) {
                faces.add(f.name().toLowerCase(java.util.Locale.ROOT)
                        + ((t.outcrop(i) & (1 << f.ordinal())) != 0 ? " (outcrop)" : ""));
            }
        }
        List<String> details = new ArrayList<>();
        details.add("exposed faces: " + (faces.isEmpty() ? "none (buried)" : String.join(", ", faces)));
        details.addAll(sentLines(s.sentFaces(world, x, y, z), id -> {
            Player p = Bukkit.getPlayer(id);
            return p == null ? id.toString() : p.getName();
        }));
        return Reply.ok("debug.oreoverlay.show", where, s.data().overlays().get(t.kind(i))).withDetails(details);
    }

    /**
     * One line per viewer of the world: the face overlay entities its client was sent for the block, so a capture can
     * tell "the server's table has the face" from "this client got the entity" (and the entity id to look for).
     */
    static List<String> sentLines(Map<UUID, List<String>> sent, Function<UUID, String> names) {
        if (sent.isEmpty()) {
            return List.of("sent to: no viewer in this world");
        }
        List<String> out = new ArrayList<>();
        sent.forEach((id, faces) -> out.add("sent to " + names.apply(id) + ": "
                + (faces.isEmpty() ? "nothing (out of the overlay radius, over the cap or not yet sent)"
                        : String.join(", ", faces))));
        return out;
    }
}
