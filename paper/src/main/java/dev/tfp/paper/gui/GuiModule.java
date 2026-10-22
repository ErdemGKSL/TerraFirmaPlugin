/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.gui;

import dev.tfp.paper.boot.TfpCommands.Reply;
import dev.tfp.paper.i18n.Messages;
import dev.tfp.paper.install.Installer;
import dev.tfp.paper.module.DebugTree;
import dev.tfp.paper.module.FeatureModule;
import dev.tfp.paper.module.ModuleContext;
import dev.tfp.paper.scheduler.Scheduler;
import io.papermc.paper.command.brigadier.Commands;
import java.util.concurrent.CompletableFuture;

/**
 * PAR-INFRA-03: the GUI framework on the module SPI. Provides {@link GuiService} to later modules, routes clicks
 * ({@link GuiListener}) and adds {@code /tfp debug gui sample}. Title glyphs are on when CraftEngine runs.
 */
public final class GuiModule implements FeatureModule {
    public static final String ID = "gui";

    private volatile GuiService service;
    private volatile Messages messages;
    private volatile Scheduler scheduler;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void enable(ModuleContext context) {
        boolean glyphs = context.integrations().activePlugins().contains(Installer.CRAFTENGINE);
        GuiService s = new GuiService(context.plugin().getServer(), context.scheduler(), System::currentTimeMillis, glyphs);
        context.listen(new GuiListener(s));
        context.services().provide(GuiService.class, s);
        this.messages = context.messages();
        this.scheduler = context.scheduler();
        this.service = s;
        if (!glyphs) {
            context.logger().info("GUI screens use plain titles: CraftEngine is not running");
        }
    }

    @Override
    public void disable() {
        service = null;
    }

    @Override
    public void debugCommands(DebugTree tree) {
        tree.add("gui", gui -> gui.then(Commands.literal("sample").executes(tree.player((player, ctx) -> {
            GuiService s = service;
            CompletableFuture<Reply> reply = new CompletableFuture<>();
            boolean scheduled = scheduler.entity(player, () -> {
                GuiSession session = s.open(player, SampleGui.create(s, messages));
                reply.complete(session == null ? Reply.error("debug.gui.refused")
                    : Reply.ok("debug.gui.sample.opened", s.glyphs() ? "on" : "off"));
            }, () -> reply.complete(Reply.error("debug.gui.refused")));
            if (!scheduled) {
                reply.complete(Reply.error("debug.gui.refused"));
            }
            return reply;
        }))));
    }
}
