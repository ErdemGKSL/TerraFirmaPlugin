/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.barrel.gui;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import dev.tfp.core.barrel.BarrelRules;
import dev.tfp.core.fluid.TfcFluid;
import dev.tfp.core.fluid.TfcFluids;
import dev.tfp.core.vessel.FluidAmount;
import dev.tfp.paper.boot.TfpCommands.Reply;
import dev.tfp.paper.gui.GuiService;
import dev.tfp.paper.i18n.Messages;
import dev.tfp.paper.module.DebugTree;
import dev.tfp.paper.module.FeatureModule;
import dev.tfp.paper.module.ModuleContext;
import dev.tfp.paper.scheduler.Scheduler;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.bukkit.entity.Player;

/**
 * PAR-PROC-09: the barrel screen service ({@link BarrelScreens}, {@code services().require(BarrelScreens.class)}) and its
 * click listener. Needs the GUI module before it in the service file. The barrel device opens the screen in PAR-PROC-35;
 * until then {@code /tfp debug barrelgui [mB] [fluid]} opens it over a {@link DemoBarrelLink}.
 */
public final class BarrelGuiModule implements FeatureModule {
    public static final String ID = "barrel_gui";
    static final int DEFAULT_DEMO_AMOUNT = BarrelRules.DEFAULT_CAPACITY / 2;

    private volatile BarrelScreens screens;
    private volatile Scheduler scheduler;
    private volatile Messages messages;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void enable(ModuleContext context) {
        GuiService gui = context.services().require(GuiService.class);
        BarrelScreens s = new BarrelScreens(gui, context.scheduler(), context.messages());
        context.listen(new BarrelGuiListener(s));
        context.services().provide(BarrelScreens.class, s);
        this.scheduler = context.scheduler();
        this.messages = context.messages();
        this.screens = s;
    }

    @Override
    public void disable() {
        screens = null;
    }

    @Override
    public void debugCommands(DebugTree tree) {
        tree.add("barrelgui", node -> node
            .executes(tree.player((p, ctx) -> demo(p, DEFAULT_DEMO_AMOUNT, TfcFluids.WATER)))
            .then(Commands.argument("mB", IntegerArgumentType.integer(0, BarrelRules.DEFAULT_CAPACITY))
                .executes(tree.player((p, ctx) -> demo(p, IntegerArgumentType.getInteger(ctx, "mB"), TfcFluids.WATER)))
                .then(Commands.argument("fluid", StringArgumentType.word())
                    .executes(tree.player((p, ctx) -> demo(p, IntegerArgumentType.getInteger(ctx, "mB"), fluid(ctx)))))));
    }

    private static String fluid(CommandContext<CommandSourceStack> ctx) {
        return StringArgumentType.getString(ctx, "fluid");
    }

    /** Opens a demo barrel holding {@code amount} mB of {@code fluidName} on the player's thread. */
    CompletableFuture<Reply> demo(Player player, int amount, String fluidName) {
        BarrelScreens s = screens;
        if (s == null) {
            return CompletableFuture.completedFuture(Reply.error("debug.gui.refused"));
        }
        Optional<TfcFluid> fluid = TfcFluids.resolve(fluidName);
        if (fluid.isEmpty()) {
            return CompletableFuture.completedFuture(Reply.error("debug.fluid.unknown_fluid", fluidName));
        }
        FluidAmount tank = amount <= 0 ? FluidAmount.EMPTY : new FluidAmount(fluid.get().id(), amount);
        CompletableFuture<Reply> reply = new CompletableFuture<>();
        boolean scheduled = scheduler.entity(player, () -> {
            BarrelScreens.Opened opened = s.open(player, new Object(),
                new DemoBarrelLink(tank, BarrelRules.DEFAULT_CAPACITY), p -> messages.get(p, "gui.barrel.title"));
            reply.complete(opened.ok()
                ? Reply.ok("debug.barrelgui.opened", tank.amount(), tank.isEmpty() ? "-" : tank.fluid(),
                    BarrelTank.fillPixels(tank.amount(), BarrelRules.DEFAULT_CAPACITY))
                : Reply.error("debug.gui.refused"));
        }, () -> reply.complete(Reply.error("debug.gui.refused")));
        if (!scheduled) {
            reply.complete(Reply.error("debug.gui.refused"));
        }
        return reply;
    }
}
