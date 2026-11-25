/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/devices/ChannelBlock.java @ c3b4d9536,
 *       src/main/java/net/dries007/tfc/common/blocks/devices/MoldTableBlock.java @ c3b4d9536,
 *       src/main/java/net/dries007/tfc/util/ChannelFlow.java @ c3b4d9536
 */
package dev.tfp.paper.channel;

import com.mojang.brigadier.arguments.StringArgumentType;
import dev.tfp.core.channel.ChannelData;
import dev.tfp.core.channel.MoldTableData;
import dev.tfp.core.channel.MoldTableRules;
import dev.tfp.core.crucible.CrucibleData;
import dev.tfp.core.device.DeviceRecord;
import dev.tfp.paper.alloy.AlloyRecipes;
import dev.tfp.paper.boot.TfpCommands.Reply;
import dev.tfp.paper.device.DeviceKind;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.mold.MoldItems;
import dev.tfp.paper.module.DebugTree;
import dev.tfp.paper.module.FeatureModule;
import dev.tfp.paper.module.ModuleContext;
import dev.tfp.paper.neighbour.NeighbourService;
import dev.tfp.paper.scheduler.Scheduler;
import io.papermc.paper.command.brigadier.Commands;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

/**
 * PAR-METAL-13: casting channels and mold tables. Registers the device kinds {@code tfp:channel} and {@code tfp:mold_table},
 * the connection {@link ConnectionRules shape rules} with the neighbour runner, the {@link PourService} and the table's
 * item displays. Needs the device, neighbour, mold, alloy and crucible modules before it in the service file.
 *
 * <p>Debug: {@code /tfp debug channel <name> activate} starts the flow of the channel the player looks at,
 * {@code info} prints the channel's or the table's state.
 */
public final class ChannelModule implements FeatureModule {
    public static final String ID = "channel";
    static final double REACH = 6.0;

    private volatile PourService pour;
    private volatile ChannelWorld world;
    private volatile Scheduler scheduler;
    private volatile dev.tfp.paper.integration.CustomItems items;
    private final List<NeighbourService.Registration> registrations = new ArrayList<>();
    private final List<AutoCloseable> hooks = new ArrayList<>();

    @Override
    public String id() {
        return ID;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void enable(ModuleContext ctx) {
        DeviceService devs = ctx.services().require(DeviceService.class);
        MoldItems molds = ctx.services().require(MoldItems.class);
        AlloyRecipes alloys = ctx.services().require(AlloyRecipes.class);
        Scheduler sch = ctx.scheduler();
        Optional<DeviceKind<?>> crucible = devs.kinds().find(ChannelWorld.CRUCIBLE);
        if (crucible.isEmpty()) {
            ctx.logger().warning("channel: the crucible device kind is not registered; channels are not enabled");
            return;
        }
        ChannelWorld w = new ChannelWorld(ctx.integrations().customBlocks(), devs, sch, () -> ctx.integrations().blockVisuals());
        PourService p = new PourService(w, molds, alloys, (DeviceKind<CrucibleData>) crucible.get(), devs.stacks(),
            () -> ctx.clock().get().playerTicks(), ctx.logger());
        ChannelDevice channelDevice = new ChannelDevice(w, p);
        MoldTableDevice tableDevice = new MoldTableDevice(w, p, molds, devs.stacks());
        w.bind(channelDevice.kind(), tableDevice.kind());
        devs.register(channelDevice.kind());
        devs.register(tableDevice.kind());
        hooks.add(devs.hook(MoldTableDevice.ID, new DeviceService.AccessHook() {
            @Override
            public void after(Block block) {
                tableChanged(block);
            }
        }));
        ctx.services().get(NeighbourService.class).ifPresent(n -> {
            registrations.add(n.register(ConnectionRules.channel()));
            registrations.add(n.register(ConnectionRules.table()));
        });
        this.pour = p;
        this.world = w;
        this.scheduler = sch;
        this.items = ctx.integrations().customItems();
        ctx.services().provide(PourService.class, p);
    }

    /** The table's record was stored or the table is gone: its displays follow, and a pour is resumed. Region thread. */
    private void tableChanged(Block block) {
        ChannelWorld w = world;
        PourService p = pour;
        if (w == null || p == null) {
            return;
        }
        Optional<DeviceRecord<MoldTableData>> record = w.tableRecord(block);
        if (record.isEmpty()) {
            MoldTableDisplays.clear(block);
            return;
        }
        MoldTableDisplays.sync(block, w.devices().stacks().decode(record.get().slot(MoldTableRules.MOLD_SLOT)),
            w.devices().stacks().decode(record.get().slot(MoldTableRules.OUTPUT_SLOT)));
    }

    @Override
    public void disable() {
        PourService p = pour;
        pour = null;
        world = null;
        if (p != null) {
            p.stop();
        }
        registrations.forEach(NeighbourService.Registration::unregister);
        registrations.clear();
        for (AutoCloseable h : hooks) {
            try {
                h.close();
            } catch (Exception ignored) {
                // a map removal
            }
        }
        hooks.clear();
    }

    // ---- debug --------------------------------------------------------------------------------------------

    @Override
    public void debugCommands(DebugTree tree) {
        var activate = Commands.literal("activate").executes(tree.run(ctx -> onPlayer(ctx, this::activate)));
        var info = Commands.literal("info").executes(tree.run(ctx -> onPlayer(ctx, this::info)));
        var put = Commands.literal("put").then(Commands.argument("item", StringArgumentType.greedyString())
            .executes(tree.run(ctx -> onPlayer(ctx, p -> put(p, StringArgumentType.getString(ctx, "item"))))));
        tree.add("channel", c -> c.then(Commands.argument("name", StringArgumentType.word()).then(activate).then(info).then(put)));
    }

    private CompletableFuture<Reply> onPlayer(com.mojang.brigadier.context.CommandContext<io.papermc.paper.command.brigadier.CommandSourceStack> ctx,
            java.util.function.Function<Player, Reply> action) {
        Player player = org.bukkit.Bukkit.getPlayerExact(StringArgumentType.getString(ctx, "name"));
        CompletableFuture<Reply> reply = new CompletableFuture<>();
        Scheduler sch = scheduler;
        if (player == null || sch == null || !sch.entity(player, () -> reply.complete(action.apply(player)),
            () -> reply.complete(Reply.error("debug.channel.no_player")))) {
            reply.complete(Reply.error("debug.channel.no_player"));
        }
        return reply;
    }

    private Reply activate(Player player) {
        PourService p = pour;
        ChannelWorld w = world;
        Block b = player.getTargetBlockExact((int) REACH);
        if (p == null || w == null || b == null || !w.is(b, ChannelWorld.CHANNEL)) {
            return Reply.error("debug.channel.no_channel");
        }
        return Reply.ok("debug.channel.activated", p.activate(b).name());
    }

    /** {@code put <item id>}: the item goes into the mold slot of the mold table the player looks at (an in-game check aid). */
    private Reply put(Player player, String id) {
        ChannelWorld w = world;
        Block b = player.getTargetBlockExact((int) REACH);
        if (w == null || b == null || !w.is(b, ChannelWorld.TABLE)) {
            return Reply.error("debug.channel.no_table");
        }
        Optional<org.bukkit.inventory.ItemStack> made = items.create(id, 1);
        if (made.isEmpty()) {
            return Reply.error("debug.channel.unknown_item", id);
        }
        org.bukkit.inventory.ItemStack one = made.get();
        Optional<DeviceRecord<MoldTableData>> r = w.devices().edit(b, w.tableKind(),
            (record, now) -> record.withSlot(MoldTableRules.MOLD_SLOT, w.devices().stacks().encode(one)));
        if (r.isEmpty()) {
            return Reply.error("debug.channel.no_table");
        }
        tableChanged(b);
        return Reply.ok("debug.channel.put", id);
    }

    private Reply info(Player player) {
        ChannelWorld w = world;
        PourService p = pour;
        Block b = player.getTargetBlockExact((int) REACH);
        if (w == null || p == null || b == null) {
            return Reply.error("debug.channel.no_channel");
        }
        Optional<ChannelData> channel = w.channel(b);
        if (channel.isPresent()) {
            return Reply.ok("debug.channel.info_channel", PourService.describe(channel.get()));
        }
        Optional<DeviceRecord<MoldTableData>> table = w.tableRecord(b);
        if (table.isPresent()) {
            MoldTableData d = table.get().state();
            return Reply.ok("debug.channel.info_table", d.hasSource() ? "source " + d.source() + ", " + d.fluid() : "no flow",
                !table.get().slot(MoldTableRules.MOLD_SLOT).isEmpty(), !table.get().slot(MoldTableRules.OUTPUT_SLOT).isEmpty(),
                p.activeTables());
        }
        return Reply.error("debug.channel.no_channel");
    }
}
