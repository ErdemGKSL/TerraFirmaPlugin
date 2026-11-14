/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/AbstractFirepitBlockEntity.java @ c3b4d9536 (isItemValid:
 *       Fuel.get and #tfc:firepit_fuel, HeatCapability.has)
 */
package dev.tfp.paper.firepit.gui;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import dev.tfp.core.firepit.FirepitFuels;
import dev.tfp.core.gui.ProgressFrames;
import dev.tfp.core.registry.Registries;
import dev.tfp.paper.boot.TfpCommands.Reply;
import dev.tfp.paper.gui.GuiService;
import dev.tfp.paper.heat.ItemHeatDefinitionsCache;
import dev.tfp.paper.heat.ItemHeatHooks;
import dev.tfp.paper.i18n.Messages;
import dev.tfp.paper.module.DebugTree;
import dev.tfp.paper.module.FeatureModule;
import dev.tfp.paper.module.ModuleContext;
import dev.tfp.paper.scheduler.Scheduler;
import dev.tfp.paper.tags.Tags;
import dev.tfp.paper.tooltip.TooltipStyles;
import io.papermc.paper.command.brigadier.Commands;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.bukkit.entity.Player;

/**
 * PAR-HEAT-07: the firepit screen service ({@link FirepitScreens}, {@code services().require(FirepitScreens.class)}), its
 * listener and TFC's slot rules ({@link FirepitSlotRules}, also a service, for the device link). Needs the GUI, tags and
 * heating modules before it in the service file. The firepit block opens the screen in PAR-HEAT-20; until then
 * {@code /tfp debug firepitgui [temperature]} opens it over a {@link DemoFirepitLink}.
 */
public final class FirepitGuiModule implements FeatureModule {
    public static final String ID = "firepit_gui";
    public static final String FIREPIT_FUEL = "tfc:firepit_fuel";
    static final int DEFAULT_DEMO_TEMPERATURE = 600;
    static final int MAX_DEMO_TEMPERATURE = 1600;

    private volatile FirepitScreens screens;
    private volatile FirepitSlotRules rules;
    private volatile Scheduler scheduler;
    private volatile Messages messages;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void enable(ModuleContext context) {
        GuiService gui = context.services().require(GuiService.class);
        Tags tags = context.services().require(Tags.class);
        ItemHeatDefinitionsCache heat = context.services().require(ItemHeatDefinitionsCache.class);
        Supplier<FirepitFuels> fuels = fuels(() -> context.registryService().registries());
        ItemHeatHooks.Capacities capacities = ItemHeatHooks.Capacities.byId(tags::itemId, heat);
        FirepitSlotRules r = new FirepitSlotRules(
            stack -> tags.itemId(stack).flatMap(id -> fuels.get().get(id)).isPresent() && tags.is(stack, FIREPIT_FUEL),
            // TFC HeatCapability.has: every TFC item heat definition has a positive heat capacity (smallest 0.114), so
            // capacity > 0 means "has one"; a datapack definition with heat_capacity 0 is refused here (ledger divergence)
            stack -> capacities.of(stack) > 0);
        FirepitScreens s = new FirepitScreens(gui, context.scheduler(), context.messages(), TooltipStyles.TFC_DEFAULTS.heat());
        context.listen(new FirepitGuiListener(s));
        context.services().provide(FirepitScreens.class, s);
        context.services().provide(FirepitSlotRules.class, r);
        this.scheduler = context.scheduler();
        this.messages = context.messages();
        this.rules = r;
        this.screens = s;
    }

    @Override
    public void disable() {
        screens = null;
    }

    @Override
    public void debugCommands(DebugTree tree) {
        tree.add("firepitgui", node -> node
            .executes(tree.player((p, ctx) -> demo(p, DEFAULT_DEMO_TEMPERATURE)))
            .then(Commands.argument("temperature", IntegerArgumentType.integer(0, MAX_DEMO_TEMPERATURE))
                .executes(tree.player((p, ctx) -> demo(p, IntegerArgumentType.getInteger(ctx, "temperature"))))));
    }

    /** Opens a demo firepit at {@code temperature} degrees C on the player's thread. */
    CompletableFuture<Reply> demo(Player player, int temperature) {
        FirepitScreens s = screens;
        FirepitSlotRules r = rules;
        if (s == null || r == null) {
            return CompletableFuture.completedFuture(Reply.error("debug.gui.refused"));
        }
        CompletableFuture<Reply> reply = new CompletableFuture<>();
        boolean scheduled = scheduler.entity(player, () -> {
            FirepitScreens.Opened opened = s.open(player, new Object(), new DemoFirepitLink(temperature, r),
                p -> messages.get(p, "gui.firepit.title"));
            reply.complete(opened.ok()
                ? Reply.ok("debug.firepitgui.opened", temperature, ProgressFrames.thermometer(temperature))
                : Reply.error("debug.gui.refused"));
        }, () -> reply.complete(Reply.error("debug.gui.refused")));
        if (!scheduled) {
            reply.complete(Reply.error("debug.gui.refused"));
        }
        return reply;
    }

    /** The fuel index, rebuilt when the registries change version. Thread-safe. */
    static Supplier<FirepitFuels> fuels(Supplier<Registries> registries) {
        record Entry(long version, FirepitFuels fuels) {
        }
        AtomicReference<Entry> cache = new AtomicReference<>(new Entry(0, FirepitFuels.EMPTY));
        return () -> {
            Registries reg = registries.get();
            if (reg == null) {
                return FirepitFuels.EMPTY;
            }
            long version = reg.version();
            Entry e = cache.get();
            if (e.version() == version) {
                return e.fuels();
            }
            FirepitFuels f = version == 0 ? FirepitFuels.EMPTY : FirepitFuels.fromRegistry(reg.get());
            cache.set(new Entry(version, f));
            return f;
        };
    }
}
