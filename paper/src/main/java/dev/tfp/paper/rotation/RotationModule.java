/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.rotation;

import dev.tfp.core.rotation.RotationWorld;
import dev.tfp.core.world.BlockPos;
import dev.tfp.paper.boot.TfpCommands.Reply;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.module.DebugTree;
import dev.tfp.paper.module.FeatureModule;
import dev.tfp.paper.module.ModuleContext;
import dev.tfp.paper.rotation.spin.SpinShafts;
import dev.tfp.paper.rotation.spin.SpinVisuals;
import dev.tfp.paper.scheduler.Scheduler;
import dev.tfp.paper.tags.Tags;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

/**
 * PAR-MECH-01: the rotation network blocks (axles, encased axles, clutches, gearboxes, the creative motor). Registers their
 * device kinds, runs the per-world networks ({@link RotationService}), re-adds a chunk's blocks when it loads and removes them
 * when it unloads, and adds {@code /tfp debug rotation}: the speeds of the network of the block the player looks at. The axles
 * are drawn by the spinning shafts ({@link SpinVisuals}, PAR-MECH-12).
 * Needs the device and tags modules before it in the service file.
 */
public final class RotationModule implements FeatureModule {
    public static final String ID = "rotation";
    /** How far {@code /tfp debug rotation} looks. */
    static final int REACH = 8;

    private Scheduler scheduler;
    private CustomBlocks blocks;
    private RotationService networks;
    private RotationDevices devices;
    private PaperRotationWorld world;
    private volatile SpinShafts spin;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void enable(ModuleContext context) {
        DeviceService deviceService = context.services().require(DeviceService.class);
        Tags tags = context.services().require(Tags.class);
        scheduler = context.scheduler();
        blocks = context.integrations().customBlocks();
        world = new PaperRotationWorld(scheduler, blocks, () -> context.integrations().blockVisuals(),
            context.integrations().customItems(), uid -> context.plugin().getServer().getWorld(uid));
        networks = new RotationService(scheduler.cpuExecutor(), world, context.logger());
        RotationChunkIndex index = new RotationChunkIndex(context.chunkData());
        devices = new RotationDevices(networks, world, index, stack -> tags.is(stack, RotationDevices.HAMMERS));
        world.bind(networks, deviceService);
        devices.kinds().forEach(deviceService::register);
        context.services().provide(RotationService.class, networks);
        blocks.onChunkLoad(chunk -> world.chunkLoaded(chunk, index, devices));
        blocks.onChunkUnload(chunk -> world.chunkUnloaded(chunk, index));
        // PAR-MECH-12: the spinning shafts (packet-only displays fed by the network actors), before the re-add so it reports them.
        try {
            spin = SpinVisuals.start(context, networks, world::axleItem).orElse(null);
        } catch (ReflectiveOperationException | RuntimeException e) {
            context.logger().warning("Rotation: the spinning shafts failed to start (" + e + "); axles are not drawn");
        }
        // PAR-MECH-11: chunks loaded before this module enabled get no chunk load event; re-add their blocks once the server
        // is up (CraftEngine has restored them by then).
        scheduler.global(() -> context.plugin().getServer().getWorlds().forEach(w -> world.reAddLoaded(w, index, devices)));
    }

    /** PAR-MECH-12: the spinning shafts, or null when they are off (no PacketEvents). */
    public SpinShafts spin() {
        return spin;
    }

    @Override
    public void debugCommands(DebugTree tree) {
        tree.add("rotation", r -> r.executes(tree.player((player, ctx) -> {
            CompletableFuture<Reply> out = new CompletableFuture<>();
            boolean scheduled = scheduler.entity(player, () -> {
                try {
                    report(player).whenComplete((reply, err) -> out.complete(err != null
                        ? Reply.error("debug.rotation.failed", String.valueOf(err.getMessage())) : reply));
                } catch (RuntimeException e) {
                    out.completeExceptionally(e);
                }
            }, () -> out.complete(Reply.error("command.player_not_found", player.getName())));
            if (!scheduled) {
                out.complete(Reply.error("command.player_not_found", player.getName()));
            }
            return out;
        })));
    }

    /** Player thread (which owns the blocks within reach): refreshes the looked-at block and asks its network. */
    CompletableFuture<Reply> report(Player player) {
        Block target = player.getTargetBlockExact(REACH);
        Optional<BlockStateRef> state = target == null ? Optional.empty() : blocks.stateAt(target);
        if (state.isEmpty() || RotationBlockStates.block(state.get().id()).isEmpty()) {
            return CompletableFuture.completedFuture(Reply.error("debug.rotation.none", REACH));
        }
        devices.submit(target, state.get(), world.step(target));
        BlockPos pos = RotationDevices.pos(target);
        return networks.report(target.getWorld().getUID(), pos).thenApply(r -> r.map(RotationModule::reply)
            .orElse(Reply.error("debug.rotation.none", REACH)));
    }

    static Reply reply(RotationService.Report report) {
        RotationWorld.Readout t = report.target();
        List<String> lines = new ArrayList<>();
        for (RotationWorld.Readout r : report.network()) {
            lines.add(line(r));
        }
        String network = t.network().isPresent() ? Long.toString(t.network().getAsLong()) : "-";
        return Reply.ok("debug.rotation.done", t.pos().x(), t.pos().y(), t.pos().z(), name(t), network,
            String.format(Locale.ROOT, "%.2f", t.rpm()), report.network().size(), report.networks()).withDetails(lines);
    }

    /** One untranslated detail line: position, block, rpm, hand, source face. */
    static String line(RotationWorld.Readout r) {
        String hand = r.incoming().map(i -> i.direction().name().toLowerCase(Locale.ROOT)).orElse("-");
        String parent = r.parent().map(d -> d.name().toLowerCase(Locale.ROOT)).orElse("-");
        return String.format(Locale.ROOT, "%d %d %d %s: %.2f rpm (%.4f rad/t), hand %s, from %s%s", r.pos().x(), r.pos().y(),
            r.pos().z(), name(r), r.rpm(), r.incoming().map(i -> i.speed()).orElse(0f), hand, parent,
            r.invalid() ? ", invalid (breaks)" : "");
    }

    static String name(RotationWorld.Readout r) {
        return r.block().name().toLowerCase(Locale.ROOT);
    }
}
