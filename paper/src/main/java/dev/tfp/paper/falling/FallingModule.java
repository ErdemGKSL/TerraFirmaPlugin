/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.falling;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.tfp.core.falling.FallRules;
import dev.tfp.paper.boot.TfpCommands.Reply;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.loot.LootService;
import dev.tfp.paper.module.DebugTree;
import dev.tfp.paper.module.FeatureModule;
import dev.tfp.paper.module.ModuleContext;
import dev.tfp.paper.scheduler.Scheduler;
import dev.tfp.paper.tags.Tags;
import io.papermc.paper.command.brigadier.Commands;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

/**
 * PAR-PHYS-01: provides {@link FallingBlocks} (TFC's falling block on a vanilla {@code FallingBlock} carrying the custom
 * state) for collapses, landslides and later features, registers {@link FallingListener}, and adds
 * {@code /tfp debug fall <x> <y> <z> <state>} (spawns a falling state there, landslide damage) and
 * {@code /tfp debug fall stats} and {@code /tfp debug fall last} (the last landings with what is at each landing block now,
 * read without changing it, so a console or RCON check needs no crosshair target). Must come after {@code TagsModule} and {@code LootModule} in the service file.
 */
public final class FallingModule implements FeatureModule {
    public static final String ID = "falling";

    private volatile FallingBlocks falling;
    private volatile Scheduler scheduler;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void enable(ModuleContext context) {
        Tags tags = context.services().require(Tags.class);
        LootService loot = context.services().require(LootService.class);
        FallWorld world = new BukkitFallWorld(tags, context.integrations().customBlocks(),
                context.integrations().blockVisuals(), loot);
        FallingBlocks service = new FallingBlocks(world, context.scheduler());
        context.services().provide(FallingBlocks.class, service);
        context.listen(new FallingListener(service));
        scheduler = context.scheduler();
        falling = service;
    }

    @Override
    public void disable() {
        falling = null;
    }

    @Override
    public void debugCommands(DebugTree tree) {
        tree.add("fall", node -> node
                .then(Commands.literal("stats").executes(tree.run(ctx -> CompletableFuture.completedFuture(stats()))))
                .then(Commands.literal("last").executes(tree.run(ctx -> last())))
                .then(Commands.argument("x", IntegerArgumentType.integer())
                        .then(Commands.argument("y", IntegerArgumentType.integer())
                                .then(Commands.argument("z", IntegerArgumentType.integer())
                                        .then(Commands.argument("state", StringArgumentType.greedyString())
                                                .executes(tree.run(ctx -> spawn(
                                                        ctx.getSource().getSender() instanceof Player p
                                                                ? p.getWorld() : Bukkit.getWorlds().getFirst(),
                                                        IntegerArgumentType.getInteger(ctx, "x"),
                                                        IntegerArgumentType.getInteger(ctx, "y"),
                                                        IntegerArgumentType.getInteger(ctx, "z"),
                                                        StringArgumentType.getString(ctx, "state")))))))));
    }

    Reply stats() {
        FallingBlocks f = falling;
        if (f == null) {
            return Reply.error("debug.fall.off");
        }
        return Reply.ok("debug.fall.stats").withDetails(List.of("spawned " + f.spawned() + ", tracking "
                + f.tracking() + ", placed " + f.placed() + ", dropped " + f.dropped() + ", blocks broken "
                + f.broken()));
    }

    /**
     * The last {@link FallingBlocks#RECENT} landings, newest first, each with the state at its block now (read on the
     * block's region; the console on Paper owns every region and reads inline).
     */
    CompletableFuture<Reply> last() {
        FallingBlocks f = falling;
        Scheduler sch = scheduler;
        if (f == null || sch == null) {
            return CompletableFuture.completedFuture(Reply.error("debug.fall.off"));
        }
        List<FallingBlocks.Landed> recent = f.recent();
        if (recent.isEmpty()) {
            return CompletableFuture.completedFuture(Reply.ok("debug.fall.stats").withDetails(List.of("no landings yet")));
        }
        List<CompletableFuture<String>> lines = new ArrayList<>();
        for (FallingBlocks.Landed l : recent) {
            Block b = l.block();
            CompletableFuture<String> line = new CompletableFuture<>();
            Runnable read = () -> {
                try {
                    line.complete(line(l, f.stateNow(l)));
                } catch (RuntimeException e) {
                    line.completeExceptionally(e);
                }
            };
            if (Bukkit.isOwnedByCurrentRegion(b.getWorld(), b.getX() >> 4, b.getZ() >> 4)) {
                read.run();
            } else {
                sch.region(b.getWorld(), b.getX() >> 4, b.getZ() >> 4, read);
            }
            lines.add(line);
        }
        return CompletableFuture.allOf(lines.toArray(CompletableFuture[]::new)).thenApply(v -> Reply
                .ok("debug.fall.stats").withDetails(lines.stream().map(CompletableFuture::join).toList()));
    }

    /** One {@code last} line: {@code <world> x y z: <landing> carried <state>, now <state>}. */
    static String line(FallingBlocks.Landed landed, BlockStateRef now) {
        Block b = landed.block();
        return b.getWorld().getName() + " " + b.getX() + " " + b.getY() + " " + b.getZ() + ": " + landed.landing()
                + " carried " + landed.state() + ", now " + now;
    }

    CompletableFuture<Reply> spawn(World world, int x, int y, int z, String stateText) {
        FallingBlocks f = falling;
        Scheduler sch = scheduler;
        if (f == null || sch == null) {
            return CompletableFuture.completedFuture(Reply.error("debug.fall.off"));
        }
        BlockStateRef state;
        try {
            state = BlockStateRef.parse(stateText.trim());
        } catch (IllegalArgumentException e) {
            return CompletableFuture.completedFuture(Reply.error("debug.place.bad_state", stateText, e.getMessage()));
        }
        String where = world.getName() + " " + x + " " + y + " " + z;
        CompletableFuture<Reply> out = new CompletableFuture<>();
        Runnable task = () -> {
            try {
                boolean ok = f.spawn(world, x, y, z, state, FallRules.LANDSLIDE_DAMAGE_PER_BLOCK,
                        FallRules.LANDSLIDE_MAX_DAMAGE).isPresent();
                out.complete(ok ? Reply.ok("debug.fall.done", state.toString(), where)
                        : Reply.error("debug.fall.unknown", state.toString()));
            } catch (RuntimeException e) {
                out.completeExceptionally(e);
            }
        };
        // the console on Paper owns every region: run inline (a deferred task would wait behind the reply wait)
        if (Bukkit.isOwnedByCurrentRegion(world, x >> 4, z >> 4)) {
            task.run();
        } else {
            sch.region(world, x >> 4, z >> 4, task);
        }
        return out;
    }
}
