/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/AnvilBlockEntity.java @ c3b4d9536 (work)
 */
package dev.tfp.paper.anvil.forge;

import com.mojang.brigadier.arguments.StringArgumentType;
import dev.tfp.core.forge.ForgeStep;
import dev.tfp.core.tool.ForgingConfig;
import dev.tfp.paper.anvil.AnvilDevice;
import dev.tfp.paper.anvil.AnvilEffects;
import dev.tfp.paper.anvil.plan.AnvilPlanScreens;
import dev.tfp.paper.anvil.plan.PlanBook;
import dev.tfp.paper.boot.TfpCommands.Reply;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.gui.GuiService;
import dev.tfp.paper.gui.GuiSession;
import dev.tfp.paper.heat.ItemHeatDefinitionsCache;
import dev.tfp.paper.heat.ItemHeatHooks;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.module.DebugTree;
import dev.tfp.paper.module.FeatureModule;
import dev.tfp.paper.module.ModuleContext;
import dev.tfp.paper.recipe.Recipes;
import dev.tfp.paper.scheduler.Scheduler;
import dev.tfp.paper.tags.Tags;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

/**
 * PAR-METAL-05: the anvil's forging screen. The plain click on an anvil opens it ({@link AnvilForgeScreens}); its plan button
 * opens the plan screen of PAR-METAL-04 and a chosen plan returns here. Needs the anvil, device, GUI, tags, recipe and heat
 * modules before it in the service file. {@code /tfp debug forge <name> open|step <step>} opens the screen of, or applies a step
 * to, the anvil looked at (the step is TFC's name, for example {@code hit_light}, {@code upset}).
 */
public final class AnvilForgeModule implements FeatureModule {
    public static final String ID = "anvil_forge";
    static final double REACH = 6.0;

    private volatile AnvilForgeScreens screens;
    private volatile CustomBlocks blocks;
    private volatile Scheduler scheduler;
    private volatile AutoCloseable metalHook;
    private volatile AutoCloseable rockHook;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void enable(ModuleContext context) {
        DeviceService devices = context.services().require(DeviceService.class);
        Tags tags = context.services().require(Tags.class);
        Recipes recipes = context.services().require(Recipes.class);
        AnvilPlanScreens plans = context.services().require(AnvilPlanScreens.class);
        AnvilEffects effects = context.services().require(AnvilEffects.class);
        ItemHeatHooks heatHooks = context.services().require(ItemHeatHooks.class);
        ItemHeatDefinitionsCache definitions = context.services().require(ItemHeatDefinitionsCache.class);
        PlanBook book = new PlanBook(recipes);
        ForgeHeat heat = ForgeHeat.of(heatHooks, context.integrations().customItems()::idOf, definitions,
            () -> context.clock().get().playerTicks());
        AnvilForger.Env env = new AnvilForger.Env(devices.stacks(), book::recipes, recipes::ingredients,
            stack -> tags.is(stack, AnvilDevice.HAMMERS), heat, context.integrations().customItems(), () -> ForgingConfig.DEFAULTS,
            () -> 0, AnvilForger.Env.realDamage());
        AnvilForgeScreens s = new AnvilForgeScreens(context.services().require(GuiService.class), context.scheduler(), devices, env,
            plans, context.integrations().customBlocks(), effects, context.messages(),
            p -> context.messages().get(p, "gui.anvil.title"), stack -> tags.is(stack, AnvilDevice.FLUXES));
        plans.onPlainClick(s::openLater);
        plans.onChosen(s::openLater);
        context.listen(new AnvilForgeListener(s));
        ScreenSync sync = new ScreenSync(s, context.scheduler());
        metalHook = devices.hook(AnvilDevice.METAL_ID, sync);
        rockHook = devices.hook(AnvilDevice.ROCK_ID, sync);
        context.services().provide(AnvilForgeScreens.class, s);
        this.screens = s;
        this.blocks = context.integrations().customBlocks();
        this.scheduler = context.scheduler();
    }

    @Override
    public void disable() {
        close(metalHook);
        close(rockHook);
        screens = null;
        blocks = null;
        scheduler = null;
    }

    private static void close(AutoCloseable c) {
        try {
            if (c != null) {
                c.close();
            }
        } catch (Exception e) {
            // nothing to release
        }
    }

    /**
     * Keeps an open screen in step with the other writers of the slots (a sneaking hand click, the removal): commits its copy
     * before, reloads it after, on the viewer's thread (inline when this thread owns the viewer, else the reload is queued).
     */
    record ScreenSync(AnvilForgeScreens screens, Scheduler scheduler) implements DeviceService.AccessHook {
        @Override
        public void before(Block block) {
            withViewer(block, screens::edit, false);
        }

        @Override
        public void after(Block block) {
            withViewer(block, screens::reload, true);
        }

        private void withViewer(Block block, java.util.function.Consumer<Block> action, boolean queue) {
            Optional<GuiSession> session = screens.viewer(block);
            if (session.isEmpty()) {
                return;
            }
            Player viewer = session.get().viewer();
            Location at = viewer.getLocation();
            if (scheduler.isOwner(at.getWorld(), at.getBlockX() >> 4, at.getBlockZ() >> 4)) {
                action.accept(block);
            } else if (queue) {
                scheduler.entity(viewer, () -> action.accept(block), () -> { });
            }
        }
    }

    // ---- debug ----------------------------------------------------------------------------------------------

    @Override
    public void debugCommands(DebugTree tree) {
        var open = Commands.literal("open").executes(tree.run(ctx -> onPlayer(ctx, this::open)));
        var step = Commands.literal("step").then(Commands.argument("step", StringArgumentType.word()).executes(tree.run(
            ctx -> onPlayer(ctx, p -> step(p, StringArgumentType.getString(ctx, "step"))))));
        tree.add("forge", c -> c.then(Commands.argument("name", StringArgumentType.word()).then(open).then(step)));
    }

    private CompletableFuture<Reply> onPlayer(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx,
            Function<Player, Reply> action) {
        Player player = org.bukkit.Bukkit.getPlayerExact(StringArgumentType.getString(ctx, "name"));
        CompletableFuture<Reply> reply = new CompletableFuture<>();
        Scheduler sch = scheduler;
        if (player == null || sch == null || !sch.entity(player, () -> reply.complete(action.apply(player)),
            () -> reply.complete(Reply.error("debug.anvil.no_player")))) {
            reply.complete(Reply.error("debug.anvil.no_player"));
        }
        return reply;
    }

    private Optional<Block> target(Player player) {
        Block b = player.getTargetBlockExact((int) REACH);
        CustomBlocks cb = blocks;
        if (b == null || cb == null || !cb.stateAt(b).map(BlockStateRef::id)
            .filter(id -> id.startsWith(AnvilDevice.METAL_ID + "/") || id.startsWith(AnvilDevice.ROCK_ID + "/")).isPresent()) {
            return Optional.empty();
        }
        return Optional.of(b);
    }

    private Reply open(Player player) {
        Optional<Block> b = target(player);
        AnvilForgeScreens s = screens;
        if (b.isEmpty() || s == null) {
            return Reply.error("debug.anvil.no_anvil");
        }
        return s.open(player, b.get()).isPresent() ? Reply.ok("debug.forge.opened") : Reply.error("debug.forge.refused");
    }

    private Reply step(Player player, String name) {
        Optional<Block> b = target(player);
        AnvilForgeScreens s = screens;
        if (b.isEmpty() || s == null) {
            return Reply.error("debug.anvil.no_anvil");
        }
        ForgeStep step;
        try {
            step = ForgeStep.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return Reply.error("debug.forge.bad_step", name);
        }
        Optional<GuiSession> session = s.viewer(b.get());
        if (session.isEmpty() || session.get().viewer() != player) {
            return Reply.error("debug.forge.not_open");
        }
        s.press(session.get(), step);
        return Reply.ok("debug.forge.stepped", step.name());
    }
}
