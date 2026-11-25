/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/devices/IngotPileBlock.java @ c3b4d9536,
 *       src/main/java/net/dries007/tfc/common/blocks/devices/DoubleIngotPileBlock.java @ c3b4d9536
 */
package dev.tfp.paper.pile;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.tfp.core.pile.PileKind;
import dev.tfp.paper.boot.TfpCommands.Reply;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.device.StackCodec;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.module.DebugTree;
import dev.tfp.paper.module.FeatureModule;
import dev.tfp.paper.module.ModuleContext;
import dev.tfp.paper.scheduler.Scheduler;
import dev.tfp.paper.tags.Tags;
import io.papermc.paper.command.brigadier.Commands;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;

/**
 * PAR-METAL-16: ingot piles and double ingot piles. Registers the device kinds {@code tfp:ingot_pile} and
 * {@code tfp:double_ingot_pile} and the {@link PileListener}. Needs the tags and device modules before it in the service file.
 *
 * <p>Debug: {@code /tfp debug pile fill <x> <y> <z> <ingot|double> <count> <metal,metal,..>} places a pile of {@code count}
 * ingots of the listed metals (cycling) in the first world, for in-game checks.
 */
public final class PileModule implements FeatureModule {
    public static final String ID = "pile";

    private volatile PileService service;
    private volatile Scheduler scheduler;
    private volatile dev.tfp.paper.integration.CustomItems items;
    private volatile java.util.logging.Logger log;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void enable(ModuleContext ctx) {
        DeviceService devs = ctx.services().require(DeviceService.class);
        Tags tags = ctx.services().require(Tags.class);
        PileService s = new PileService(ctx.integrations().customBlocks(), ctx.integrations().customItems(), () -> devs,
            ctx.scheduler(), (block, state) -> ctx.integrations().blockVisuals().blockData(state).map(d -> {
                block.setBlockData(d, false);
                return true;
            }).orElse(false), devs.stacks(), System::nanoTime);
        for (PileKind kind : PileKind.values()) {
            s.bind(kind, devs.register(PileDevice.kind(kind, s)));
        }
        ctx.listen(new PileListener(s, tags::is));
        this.service = s;
        this.scheduler = ctx.scheduler();
        this.items = ctx.integrations().customItems();
        this.log = ctx.logger();
    }

    @Override
    public void disable() {
        service = null;
    }

    @Override
    public void debugCommands(DebugTree tree) {
        var fill = Commands.literal("fill")
            .then(Commands.argument("x", IntegerArgumentType.integer())
                .then(Commands.argument("y", IntegerArgumentType.integer())
                    .then(Commands.argument("z", IntegerArgumentType.integer())
                        .then(Commands.argument("kind", StringArgumentType.word())
                            .then(Commands.argument("count", IntegerArgumentType.integer(1, 64))
                                .then(Commands.argument("metals", StringArgumentType.greedyString())
                                    .executes(tree.run(ctx -> fill(IntegerArgumentType.getInteger(ctx, "x"),
                                        IntegerArgumentType.getInteger(ctx, "y"), IntegerArgumentType.getInteger(ctx, "z"),
                                        StringArgumentType.getString(ctx, "kind"), IntegerArgumentType.getInteger(ctx, "count"),
                                        StringArgumentType.getString(ctx, "metals"))))))))));
        tree.add("pile", c -> c.then(fill));
    }

    private CompletableFuture<Reply> fill(int x, int y, int z, String kindName, int count, String metals) {
        CompletableFuture<Reply> reply = new CompletableFuture<>();
        PileService s = service;
        Scheduler sch = scheduler;
        if (s == null || sch == null) {
            reply.complete(Reply.error("debug.pile.disabled"));
            return reply;
        }
        PileKind kind = kindName.startsWith("d") ? PileKind.DOUBLE_INGOT : PileKind.INGOT;
        org.bukkit.World world = org.bukkit.Bukkit.getWorlds().getFirst();
        sch.region(world, x >> 4, z >> 4, () -> {
            try {
                reply.complete(fillNow(s, world.getBlockAt(x, y, z), kind, Math.min(count, kind.capacity()), metals));
            } catch (RuntimeException e) {
                log.log(java.util.logging.Level.WARNING, "pile debug failed", e);
                reply.complete(Reply.error("debug.pile.refused", e.getMessage()));
            }
        });
        return reply;
    }

    private Reply fillNow(PileService s, Block block, PileKind kind, int count, String metals) {
        List<ItemStack> ingots = new ArrayList<>();
        String[] names = metals.trim().split("[,\\s]+");
        String folder = kind == PileKind.INGOT ? "ingot" : "double_ingot";
        for (int i = 0; i < count; i++) {
            String id = "tfp:metal/" + folder + "/" + names[i % names.length];
            Optional<ItemStack> made = items.create(id, 1);
            if (made.isEmpty()) {
                return Reply.error("debug.pile.unknown_item", id);
            }
            ingots.add(made.get());
        }
        if (!s.blocks().place(block, BlockStateRef.of(PileService.id(kind)).with(PileService.COUNT, Integer.toString(count)), false)) {
            return Reply.error("debug.pile.refused", PileService.id(kind));
        }
        Optional<dev.tfp.core.device.DeviceRecord<Boolean>> r = s.fill(block, kind, ingots);
        if (r.isEmpty()) {
            return Reply.error("debug.pile.refused", "no record");
        }
        s.later(block, () -> s.sync(block));
        log.info("pile debug: " + count + " " + kind + " at " + block.getX() + "," + block.getY() + "," + block.getZ());
        return Reply.ok("debug.pile.filled", count, PileService.id(kind));
    }
}
