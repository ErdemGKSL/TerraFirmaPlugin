/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.tabs;

import com.mojang.brigadier.arguments.StringArgumentType;
import dev.tfp.paper.boot.TfpCommands.Reply;
import dev.tfp.paper.hud.HudStylesHolder;
import dev.tfp.paper.install.Installer;
import dev.tfp.paper.module.DebugTree;
import dev.tfp.paper.module.FeatureModule;
import dev.tfp.paper.module.ModuleContext;
import dev.tfp.paper.tabs.TabLayout.Page;
import io.papermc.paper.command.brigadier.Commands;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/**
 * PAR-SURV-07: TFC's player tab screens (calendar, nutrition, climate) on the module SPI. A click outside the inventory
 * window opens them ({@link TabListener}); {@code /tfp debug tabs <page>} opens a page directly (screenshots, and creative
 * players, whose inventory sends no outside click).
 */
public final class PlayerTabsModule implements FeatureModule {
    public static final String ID = "player_tabs";

    private volatile TabService service;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void enable(ModuleContext context) {
        boolean glyphs = context.integrations().activePlugins().contains(Installer.CRAFTENGINE);
        TabService s = new TabService(context.plugin().getServer(), context.scheduler(), context.messages(),
            () -> context.clock().get(), id -> context.playerData().get(id), () -> HudStylesHolder.current().climate(), glyphs);
        context.listen(new TabListener(s, context.scheduler(), context.messages()));
        this.service = s;
    }

    @Override
    public void disable() {
        service = null;
    }

    @Override
    public void debugCommands(DebugTree tree) {
        tree.add("tabs", tabs -> tabs.then(Commands.argument("page", StringArgumentType.word())
            .suggests((ctx, b) -> {
                for (Page p : Page.values()) {
                    b.suggest(p.key());
                }
                return b.buildFuture();
            })
            .executes(tree.player((player, ctx) -> {
                Page page;
                try {
                    page = Page.valueOf(StringArgumentType.getString(ctx, "page").toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException e) {
                    return CompletableFuture.completedFuture(Reply.error("debug.tabs.unknown"));
                }
                TabService s = service;
                if (s == null) {
                    return CompletableFuture.completedFuture(Reply.error("debug.tabs.refused"));
                }
                return s.open(player, page).handle((ok, err) -> ok != null && ok
                    ? Reply.ok("debug.tabs.opened", page.key())
                    : Reply.error("debug.tabs.refused"));
            }))));
    }
}
