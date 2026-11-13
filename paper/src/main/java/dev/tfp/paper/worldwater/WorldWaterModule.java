/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.worldwater;

import com.mojang.brigadier.arguments.StringArgumentType;
import dev.tfp.core.worldgen.biome.TfcBiomes;
import dev.tfp.core.worldgen.river.Flow;
import dev.tfp.core.worldwater.ChunkWater;
import dev.tfp.core.worldwater.WaterKind;
import dev.tfp.paper.boot.TfpCommands.Reply;
import dev.tfp.paper.module.DebugTree;
import dev.tfp.paper.module.FeatureModule;
import dev.tfp.paper.module.ModuleContext;
import dev.tfp.paper.module.ModuleServices;
import dev.tfp.paper.scheduler.Scheduler;
import dev.tfp.paper.scheduler.ThreadAsserts;
import dev.tfp.paper.worldwater.hot.SpringWater;
import io.papermc.paper.command.brigadier.Commands;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;

/**
 * PAR-FLUID-03: salt water, spring water and river water in the world (NP-FLUID-01). The world holds vanilla water; this
 * module provides {@link WorldWaterService} (which TFC water a block stands for, from the seed) and runs the river current
 * ({@link RiverCurrentTracker}). {@code /tfp debug water <name>} tells the water at a player's feet; inside a hot spring
 * pool it names spring water through the {@link SpringWater} service of the hotspring module when that runs (PAR-FLUID-14).
 *
 * <p>Threads: the scan is started from the global scheduler and runs on each player's thread; pushes run on each entity's
 * thread; chunk water is computed on the CPU pool.
 */
public final class WorldWaterModule implements FeatureModule, Listener {
    public static final String ID = "worldwater";

    private volatile Scheduler scheduler;
    private volatile WorldWaterService water;
    private volatile RiverCurrentTracker current;
    private volatile Scheduler.TaskHandle scanTask;
    private volatile ModuleServices services;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void enable(ModuleContext context) {
        scheduler = context.scheduler();
        services = context.services();
        water = new WorldWaterService(scheduler.cpuExecutor(), context.logger());
        current = new RiverCurrentTracker(scheduler, water);
        context.services().provide(WorldWaterService.class, water);
        for (World world : Bukkit.getWorlds()) {
            water.register(world);
        }
        context.listen(this);
        scanTask = scheduler.globalRepeating(RiverCurrentTracker.SCAN_PERIOD, RiverCurrentTracker.SCAN_PERIOD,
                this::scanAll);
    }

    @Override
    public void disable() {
        Scheduler.TaskHandle t = scanTask;
        if (t != null) {
            t.cancel();
        }
    }

    /** Global thread: hands each player's scan to the player's thread. */
    private void scanAll() {
        RiverCurrentTracker c = current;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (water.isTfcWorld(player.getWorld())) {
                scheduler.entity(player, () -> c.scan(player), null);
            }
        }
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        water.register(event.getWorld());
    }

    @EventHandler
    public void onWorldUnload(WorldUnloadEvent event) {
        water.unregister(event.getWorld());
    }

    @Override
    public void debugCommands(DebugTree tree) {
        tree.add("water", node -> node
                .then(Commands.argument("name", StringArgumentType.word())
                        .executes(tree.run(ctx -> {
                            String name = StringArgumentType.getString(ctx, "name");
                            Player target = Bukkit.getPlayerExact(name);
                            if (target == null) {
                                return CompletableFuture.completedFuture(Reply.error("command.player_not_found", name));
                            }
                            return describeOnThread(target);
                        }))));
    }

    /** Any thread: the debug answer, read on the player's thread. */
    private CompletableFuture<Reply> describeOnThread(Player player) {
        CompletableFuture<Reply> out = new CompletableFuture<>();
        scheduler.entity(player, () -> {
            try {
                ModuleServices s = services;
                SpringWater springs = s == null ? null : s.get(SpringWater.class).orElse(null);
                out.complete(describe(player, water, springs));
            } catch (RuntimeException e) {
                out.completeExceptionally(e);
            }
        }, () -> out.complete(Reply.error("command.player_not_found", player.getName())));
        return out;
    }

    /** Player thread: the water at the player's feet, without spring pools. */
    static Reply describe(Player player, WorldWaterService water) {
        return describe(player, water, null);
    }

    /**
     * Player thread: the water at the player's feet. In a TFC world, water that {@code springs} (when given) calls spring
     * water is named {@code spring}; otherwise the chunk's classification stands.
     */
    static Reply describe(Player player, WorldWaterService water, SpringWater springs) {
        ThreadAsserts.owned(player);
        Location at = player.getLocation();
        World world = player.getWorld();
        int x = at.getBlockX();
        int y = at.getBlockY();
        int z = at.getBlockZ();
        if (!water.isTfcWorld(world)) {
            WaterKind kind = water.kindOf(world.getBlockAt(x, y, z));
            return Reply.ok("debug.water.vanilla", player.getName(), x, y, z, name(kind));
        }
        Optional<ChunkWater> chunk = water.chunk(world, x >> 4, z >> 4);
        if (chunk.isEmpty()) {
            return Reply.error("debug.water.pending", player.getName());
        }
        ChunkWater c = chunk.get();
        WaterKind kind = c.kind(x & 15, y, z & 15);
        if (springs != null && springs.isSpringWater(world.getBlockAt(x, y, z))) {
            kind = WaterKind.SPRING;
        }
        Flow flow = c.flow(x & 15, z & 15);
        return Reply.ok("debug.water.done", player.getName(), x, y, z, name(kind), flow.serializedName(),
                TfcBiomes.name(c.biome(x & 15, z & 15)).toLowerCase(Locale.ROOT), c.surfaceHeight(x & 15, z & 15));
    }

    private static String name(WaterKind kind) {
        return kind.name().toLowerCase(Locale.ROOT);
    }
}
