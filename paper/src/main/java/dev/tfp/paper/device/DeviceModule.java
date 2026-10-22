/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.device;

import dev.tfp.core.device.DeviceRecord;
import dev.tfp.core.device.ItemBlob;
import dev.tfp.paper.boot.TfpCommands.Reply;
import dev.tfp.paper.module.DebugTree;
import dev.tfp.paper.module.FeatureModule;
import dev.tfp.paper.module.ModuleContext;
import dev.tfp.paper.scheduler.Scheduler;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

/**
 * PAR-INFRA-02: the device framework on the module SPI. Provides {@link DeviceService} to later modules
 * ({@code services().require(DeviceService.class)}, then {@code register(kind)}), installs it as the router of the
 * {@code tfp:device} CraftEngine behaviour ({@link DeviceBridge}), listens for left clicks and placements, registers
 * the {@link ProbeDevice} and carries {@code /tfp debug device} (the device the player looks at: kind, decoded state,
 * slots, lastUpdateTick; registered by PAR-INFRA-18, see {@link #addDebugCommand}). Must be listed before every module that registers device kinds.
 */
public final class DeviceModule implements FeatureModule {
    public static final String ID = "device";
    /** Reach of {@code /tfp debug device}, as {@code /tfp debug block}. */
    public static final int REACH = 8;

    private volatile DeviceService service;
    private volatile Scheduler scheduler;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void enable(ModuleContext context) {
        DeviceService s = new DeviceService(new DeviceKinds(), context.integrations().customBlocks(), DeviceBridge::store,
                () -> context.clock().get().playerTicks(), StackCodec.PAPER, context.logger());
        s.register(ProbeDevice.kind());
        context.services().provide(DeviceService.class, s);
        context.listen(new DeviceListener(s, context.scheduler()));
        scheduler = context.scheduler();
        service = s;
        DeviceBridge.installRouter(s);
    }

    @Override
    public void disable() {
        DeviceService s = service;
        if (s != null) {
            DeviceBridge.clearRouter(s);
        }
    }

    /**
     * Adds {@code /tfp debug device}. Not wired into {@link #debugCommands} yet: {@code BootstrapTest} pins the exact
     * debug literal set and is outside PAR-INFRA-02's files; PAR-INFRA-18 overrides {@code debugCommands} with this
     * and adds the literal to that test.
     */
    void addDebugCommand(DebugTree tree) {
        tree.add("device", device -> device.executes(tree.player((player, ctx) -> describe(player))));
    }

    /** The reply of {@code /tfp debug device} for the block {@code player} looks at. */
    CompletableFuture<Reply> describe(Player player) {
        CompletableFuture<Reply> out = new CompletableFuture<>();
        DeviceService s = service;
        Scheduler sch = scheduler;
        boolean scheduled = sch.entity(player, () -> {
            Block block = player.getTargetBlockExact(REACH);
            if (block == null) {
                out.complete(Reply.error("debug.block.none", REACH));
                return;
            }
            Location loc = block.getLocation();
            String where = loc.getWorld().getName() + " " + loc.getBlockX() + " " + loc.getBlockY() + " " + loc.getBlockZ();
            sch.region(loc, () -> {
                try {
                    Optional<DeviceService.Inspection> found = s.inspect(block);
                    out.complete(found.map(i -> Reply.ok("debug.block.header", where).withDetails(lines(i, s)))
                            .orElseGet(() -> Reply.error("debug.device.none", where)));
                } catch (RuntimeException e) {
                    out.completeExceptionally(e);
                }
            });
        }, () -> out.complete(Reply.error("command.player_only")));
        if (!scheduled) {
            out.complete(Reply.error("command.player_only"));
        }
        return out;
    }

    /** The detail lines of {@code /tfp debug device}. */
    static List<String> lines(DeviceService.Inspection i, DeviceService s) {
        List<String> lines = new ArrayList<>();
        lines.add("kind: " + i.kindId() + ", block: " + i.state());
        if (!i.problem().isEmpty()) {
            lines.add("note: " + i.problem());
        }
        if (i.record().isPresent()) {
            DeviceRecord<?> r = i.record().get();
            lines.add("state: " + r.state());
            lines.add("lastUpdateTick: " + (r.neverUpdated() ? "never" : r.lastUpdateTick())
                    + ", now: " + i.now() + (r.neverUpdated() ? "" : ", behind: " + (i.now() - r.lastUpdateTick())));
            for (int slot = 0; slot < r.slots().size(); slot++) {
                ItemBlob b = r.slot(slot);
                if (!b.isEmpty()) {
                    String item;
                    try {
                        var stack = s.stacks().decode(b);
                        item = stack.getAmount() + " x " + stack.getType().getKey();
                    } catch (RuntimeException e) {
                        item = "unreadable (" + b.size() + " bytes)";
                    }
                    lines.add("slot " + slot + ": " + item);
                }
            }
        }
        return lines;
    }
}
