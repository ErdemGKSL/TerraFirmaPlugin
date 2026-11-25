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
    private volatile java.util.logging.Logger log;
    private volatile MoldItems moldItems;
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
        this.log = ctx.logger();
        this.moldItems = molds;
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
        if (p.hasMoltenMold(record.get())) {
            p.settle(block);
        }
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
        var place = Commands.literal("place")
            .then(Commands.argument("x", com.mojang.brigadier.arguments.IntegerArgumentType.integer())
                .then(Commands.argument("y", com.mojang.brigadier.arguments.IntegerArgumentType.integer())
                    .then(Commands.argument("z", com.mojang.brigadier.arguments.IntegerArgumentType.integer())
                        .then(Commands.argument("state", StringArgumentType.greedyString())
                            .executes(tree.run(ctx -> place(com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "x"),
                                com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "y"),
                                com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "z"),
                                StringArgumentType.getString(ctx, "state"))))))));
        var putAt = Commands.literal("putat")
            .then(Commands.argument("x", com.mojang.brigadier.arguments.IntegerArgumentType.integer())
                .then(Commands.argument("y", com.mojang.brigadier.arguments.IntegerArgumentType.integer())
                    .then(Commands.argument("z", com.mojang.brigadier.arguments.IntegerArgumentType.integer())
                        .then(Commands.argument("item", StringArgumentType.greedyString())
                            .executes(tree.run(ctx -> putAt(com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "x"),
                                com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "y"),
                                com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "z"),
                                StringArgumentType.getString(ctx, "item"))))))));
        var infoAt = Commands.literal("infoat")
            .then(Commands.argument("x", com.mojang.brigadier.arguments.IntegerArgumentType.integer())
                .then(Commands.argument("y", com.mojang.brigadier.arguments.IntegerArgumentType.integer())
                    .then(Commands.argument("z", com.mojang.brigadier.arguments.IntegerArgumentType.integer())
                        .executes(tree.run(ctx -> infoAt(com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "x"),
                            com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "y"),
                            com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "z")))))));
        var activateAt = Commands.literal("activateat")
            .then(Commands.argument("x", com.mojang.brigadier.arguments.IntegerArgumentType.integer())
                .then(Commands.argument("y", com.mojang.brigadier.arguments.IntegerArgumentType.integer())
                    .then(Commands.argument("z", com.mojang.brigadier.arguments.IntegerArgumentType.integer())
                        .executes(tree.run(ctx -> activateAt(com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "x"),
                            com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "y"),
                            com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "z")))))));
        tree.add("channel", c -> c.then(place).then(putAt).then(infoAt).then(activateAt)
            .then(Commands.argument("name", StringArgumentType.word()).then(activate).then(info).then(put)));
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
        PourService.Outcome outcome = p.activate(b);
        log.info("channel debug: activate -> " + outcome);
        return Reply.ok("debug.channel.activated", outcome.name());
    }

    /** {@code place <x> <y> <z> <state>}: places a custom block state in the first world (an in-game check aid). */
    private CompletableFuture<Reply> place(int x, int y, int z, String text) {
        CompletableFuture<Reply> reply = new CompletableFuture<>();
        ChannelWorld w = world;
        Scheduler sch = scheduler;
        if (w == null || sch == null) {
            reply.complete(Reply.error("debug.channel.no_channel"));
            return reply;
        }
        org.bukkit.World bw = org.bukkit.Bukkit.getWorlds().getFirst();
        sch.region(bw, x >> 4, z >> 4, () -> {
            try {
                boolean ok = w.blocks().place(bw.getBlockAt(x, y, z), dev.tfp.paper.integration.BlockStateRef.parse(text.trim()), false);
                reply.complete(ok ? Reply.ok("debug.channel.placed", text.trim()) : Reply.error("debug.channel.refused", text.trim()));
            } catch (RuntimeException e) {
                reply.complete(Reply.error("debug.channel.refused", e.getMessage()));
            }
        });
        return reply;
    }

    /** {@code infoat <x> <y> <z>} and {@code activateat}: the same as {@code info} and {@code activate} for a block at coordinates. */
    private CompletableFuture<Reply> infoAt(int x, int y, int z) {
        return atBlock(x, y, z, (w, p, b) -> {
            Optional<String> crucible = p.crucibleInfo(b);
            if (crucible.isPresent()) {
                log.info("channel debug: crucible " + crucible.get());
                return Reply.ok("debug.channel.info_channel", "crucible " + crucible.get());
            }
            Optional<ChannelData> channel = w.channel(b);
            if (channel.isPresent()) {
                log.info("channel debug: channel " + PourService.describe(channel.get()) + " " + w.state(b).map(Object::toString).orElse(""));
                return Reply.ok("debug.channel.info_channel", PourService.describe(channel.get()));
            }
            Optional<DeviceRecord<MoldTableData>> table = w.tableRecord(b);
            if (table.isPresent()) {
                MoldTableData d = table.get().state();
                log.info("channel debug: table " + (d.hasSource() ? "source " + d.source() + " " + d.fluid() : "no flow") + ", mold "
                    + !table.get().slot(MoldTableRules.MOLD_SLOT).isEmpty() + ", output "
                    + !table.get().slot(MoldTableRules.OUTPUT_SLOT).isEmpty() + ", pouring " + p.activeTables() + ", content "
                    + moldContent(w, table.get()));
                return Reply.ok("debug.channel.info_channel", "table");
            }
            return Reply.error("debug.channel.no_channel");
        });
    }

    private String moldContent(ChannelWorld w, DeviceRecord<MoldTableData> record) {
        org.bukkit.inventory.ItemStack mold = w.devices().stacks().decode(record.slot(MoldTableRules.MOLD_SLOT));
        if (dev.tfp.paper.device.StackCodec.isEmpty(mold)) {
            return "no mold";
        }
        return moldItems.content(mold) + " at " + Math.round(moldItems.temperature(mold)) + " C";
    }

    private CompletableFuture<Reply> activateAt(int x, int y, int z) {
        return atBlock(x, y, z, (w, p, b) -> {
            PourService.Outcome outcome = w.is(b, ChannelWorld.CHANNEL) ? p.activate(b) : PourService.Outcome.NO_CRUCIBLE;
            log.info("channel debug: activate at " + x + "," + y + "," + z + " -> " + outcome);
            return Reply.ok("debug.channel.activated", outcome.name());
        });
    }

    private interface AtBlock {
        Reply apply(ChannelWorld w, PourService p, Block b);
    }

    private CompletableFuture<Reply> atBlock(int x, int y, int z, AtBlock action) {
        CompletableFuture<Reply> reply = new CompletableFuture<>();
        ChannelWorld w = world;
        PourService p = pour;
        Scheduler sch = scheduler;
        if (w == null || p == null || sch == null) {
            reply.complete(Reply.error("debug.channel.no_channel"));
            return reply;
        }
        org.bukkit.World bw = org.bukkit.Bukkit.getWorlds().getFirst();
        sch.region(bw, x >> 4, z >> 4, () -> {
            try {
                reply.complete(action.apply(w, p, bw.getBlockAt(x, y, z)));
            } catch (RuntimeException e) {
                log.log(java.util.logging.Level.WARNING, "channel debug failed", e);
                reply.complete(Reply.error("debug.channel.refused", e.getMessage()));
            }
        });
        return reply;
    }

    /** {@code putat <x> <y> <z> <item id>}: as {@code put}, for the table at the coordinates in the first world. */
    private CompletableFuture<Reply> putAt(int x, int y, int z, String id) {
        CompletableFuture<Reply> reply = new CompletableFuture<>();
        ChannelWorld w = world;
        Scheduler sch = scheduler;
        if (w == null || sch == null) {
            reply.complete(Reply.error("debug.channel.no_table"));
            return reply;
        }
        org.bukkit.World bw = org.bukkit.Bukkit.getWorlds().getFirst();
        sch.region(bw, x >> 4, z >> 4, () -> {
            Block b = bw.getBlockAt(x, y, z);
            Optional<org.bukkit.inventory.ItemStack> made = items.create(id, 1);
            if (!w.is(b, ChannelWorld.TABLE) || made.isEmpty()) {
                reply.complete(Reply.error("debug.channel.no_table"));
                return;
            }
            w.devices().edit(b, w.tableKind(), (record, now) -> record.withSlot(MoldTableRules.MOLD_SLOT,
                w.devices().stacks().encode(made.get())));
            tableChanged(b);
            log.info("channel debug: put " + id + " at " + x + "," + y + "," + z);
            reply.complete(Reply.ok("debug.channel.put", id));
        });
        return reply;
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
        log.info("channel debug: put " + id);
        return Reply.ok("debug.channel.put", id);
    }

    private Reply info(Player player) {
        ChannelWorld w = world;
        PourService p = pour;
        Block b = player.getTargetBlockExact((int) REACH);
        if (w == null || p == null || b == null) {
            return Reply.error("debug.channel.no_channel");
        }
        Optional<String> crucible = p.crucibleInfo(b);
        if (crucible.isPresent()) {
            log.info("channel debug: crucible " + crucible.get());
            return Reply.ok("debug.channel.info_channel", "crucible " + crucible.get());
        }
        Optional<ChannelData> channel = w.channel(b);
        if (channel.isPresent()) {
            log.info("channel debug: " + PourService.describe(channel.get()));
            return Reply.ok("debug.channel.info_channel", PourService.describe(channel.get()));
        }
        Optional<DeviceRecord<MoldTableData>> table = w.tableRecord(b);
        if (table.isPresent()) {
            MoldTableData d = table.get().state();
            log.info("channel debug: table " + (d.hasSource() ? "source " + d.source() + " " + d.fluid() : "no flow") + ", mold "
                + !table.get().slot(MoldTableRules.MOLD_SLOT).isEmpty() + ", output "
                + !table.get().slot(MoldTableRules.OUTPUT_SLOT).isEmpty() + ", pouring " + p.activeTables());
            return Reply.ok("debug.channel.info_table", d.hasSource() ? "source " + d.source() + ", " + d.fluid() : "no flow",
                !table.get().slot(MoldTableRules.MOLD_SLOT).isEmpty(), !table.get().slot(MoldTableRules.OUTPUT_SLOT).isEmpty(),
                p.activeTables());
        }
        return Reply.error("debug.channel.no_channel");
    }
}
